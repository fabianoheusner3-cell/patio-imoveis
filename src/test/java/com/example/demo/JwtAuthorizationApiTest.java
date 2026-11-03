package com.example.demo;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.List;
import com.jayway.jsonpath.JsonPath;
import com.example.demo.models.*;
import com.example.demo.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:jwt-test", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never", "app.admin.email=admin@example.com", "app.admin.password=AdminTeste123!"
})
class JwtAuthorizationApiTest {
    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarios;
    @Autowired ImovelRepository imoveis;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    final HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> req(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    String login(String email, String senha) throws Exception {
        var response = req("POST", "/auth/login", "{\"email\":\""+email+"\",\"senha\":\""+senha+"\"}", null);
        assertEquals(200, response.statusCode(), response.body());
        assertFalse(response.headers().firstValue("Set-Cookie").isPresent());
        return JsonPath.read(response.body(), "$.token");
    }
    @Test void regrasDaAtividadeETokens() throws Exception {
        assertEquals(200, req("GET", "/imoveis", null, null).statusCode());
        assertEquals(200, req("GET", "/imoveis/imoveis-page", null, null).statusCode());
        assertEquals(200, req("GET", "/login.html", null, null).statusCode());
        assertEquals(401, req("POST", "/auth/login", "{\"email\":\"admin@example.com\",\"senha\":\"errada\"}", null).statusCode());
        var admin = login("ADMIN@example.com", "AdminTeste123!");
        String novo = "{\"nome\":\"Usuario teste\",\"email\":\"user@example.com\",\"senha\":\"UserTeste123!\",\"perfil\":\"USER\"}";
        var criado = req("POST", "/usuarios", novo, admin);
        assertEquals(201, criado.statusCode(), criado.body());
        assertFalse(criado.body().contains("senha"));
        assertFalse(criado.body().contains("$2a$"));
        var usuario = usuarios.findByEmail("user@example.com").orElseThrow();
        assertNotEquals("UserTeste123!", usuario.getSenha());
        assertTrue(passwords.matches("UserTeste123!", usuario.getSenha()));
        var user = login("user@example.com", "UserTeste123!");
        assertEquals(409, req("POST", "/usuarios", novo, admin).statusCode());
        var imovel = new ImovelModel(); imovel.setTitulo("Casa de teste"); imovel.setStatus("Disponivel");
        imovel = imoveis.save(imovel);
        String path = "/imoveis/" + imovel.getId();
        assertEquals(200, req("GET", path, null, null).statusCode());
        for (String token : new String[] {null, "invalido"}) {
            assertEquals(401, req("POST", "/imoveis", "{}", token).statusCode());
            assertEquals(401, req("PATCH", path+"/status", "{\"status\":\"Vendido\"}", token).statusCode());
            assertEquals(401, req("GET", "/usuarios", null, token).statusCode());
        }
        var expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer("bairro-pageable").subject(usuario.getId().toString())
                .issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().minusSeconds(1))
                .claim("role", "USER").build())).getTokenValue();
        assertEquals(401, req("PATCH", path+"/status", "{\"status\":\"Vendido\"}", expired).statusCode());
        String[] partes = user.split("\\.");
        String assinatura = (partes[2].startsWith("A") ? "B" : "A") + partes[2].substring(1);
        assertEquals(401, req("GET", "/usuarios", null, partes[0]+"."+partes[1]+"."+assinatura).statusCode());
        for (String method : List.of("POST", "PUT", "DELETE")) {
            assertEquals(403, req(method, method.equals("POST") ? "/imoveis" : path, "{}", user).statusCode());
            assertEquals(403, req(method, method.equals("POST") ? "/usuarios" : "/usuarios/"+usuario.getId(), novo, user).statusCode());
        }
        assertEquals(403, req("GET", "/usuarios", null, user).statusCode());
        assertEquals(204, req("PATCH", path+"/status", "{\"status\":\"Reservado\",\"titulo\":\"invasao\"}", user).statusCode());
        var salvo = imoveis.findById(imovel.getId()).orElseThrow();
        assertEquals("Casa de teste", salvo.getTitulo()); assertEquals("Reservado", salvo.getStatus());
        assertEquals(400, req("PATCH", path+"/status", "{\"status\":\" \"}", user).statusCode());
        assertEquals(204, req("PATCH", path+"/status", "{\"status\":\"Vendido\"}", admin).statusCode());
        assertEquals(200, req("GET", "/usuarios", null, admin).statusCode());
        assertEquals(200, req("PUT", "/usuarios/"+usuario.getId(), novo.replace("USER", "ADMIN"), admin).statusCode());
        assertEquals(401, req("GET", "/usuarios", null, user).statusCode());
        String promoted = login("user@example.com", "UserTeste123!");
        assertEquals(200, req("GET", "/usuarios", null, promoted).statusCode());
        assertEquals(204, req("DELETE", "/usuarios/"+usuario.getId(), null, admin).statusCode());
        assertEquals(401, req("GET", "/usuarios", null, promoted).statusCode());
        assertEquals(204, req("DELETE", path, null, admin).statusCode());
    }
}
