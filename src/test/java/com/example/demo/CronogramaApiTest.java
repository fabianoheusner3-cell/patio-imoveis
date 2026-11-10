package com.example.demo;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

/** Tabela de verificacao do cronograma, com as rotas e os campos usados pelo professor. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:cronograma-test", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never", "app.admin.email=admin@example.com", "app.admin.password=AdminTeste123!"
})
class CronogramaApiTest {
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> req(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** O que o jwt.io mostra: o payload do token, apenas decodificado. */
    static String payload(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    @Test void tabelaDeVerificacaoDoCronograma() throws Exception {
        String bairro = "{\"nome\":\"Centro\"}";

        // 1. A vitrine e publica
        assertEquals(200, req("GET", "/imoveis", null, null).statusCode());

        // 2. Login do admin responde token e tipo
        var login = req("POST", "/public/login", "{\"email\":\"admin@example.com\",\"password\":\"AdminTeste123!\"}", null);
        assertEquals(200, login.statusCode(), login.body());
        assertEquals("ADMIN", JsonPath.read(login.body(), "$.tipo"));
        String admin = JsonPath.read(login.body(), "$.token");

        // 3. O token e legivel: sub, tipo e exp
        String claims = payload(admin);
        assertNotNull(JsonPath.read(claims, "$.sub"));
        assertEquals("ADMIN", JsonPath.read(claims, "$.tipo"));
        assertNotNull(JsonPath.read(claims, "$.exp"));

        // 4. Rota protegida sem login
        assertEquals(401, req("POST", "/bairros", bairro, null).statusCode());

        // 5. Admin tem permissao
        assertEquals(201, req("POST", "/bairros", bairro, admin).statusCode());

        // 6. So o admin cadastra usuarios
        String cliente = "{\"nome\":\"Cliente\",\"email\":\"cliente@example.com\",\"password\":\"ClienteTeste123!\",\"tipo\":\"USER\"}";
        assertEquals(401, req("POST", "/users", cliente, null).statusCode());
        var criado = req("POST", "/users", cliente, admin);
        assertEquals(201, criado.statusCode(), criado.body());
        assertEquals("USER", JsonPath.read(criado.body(), "$.perfil"));
        assertFalse(criado.body().contains("ClienteTeste123!"));

        // 7. Login com o cliente criado
        var loginCliente = req("POST", "/public/login", "{\"email\":\"cliente@example.com\",\"password\":\"ClienteTeste123!\"}", null);
        assertEquals(200, loginCliente.statusCode(), loginCliente.body());
        assertEquals("USER", JsonPath.read(loginCliente.body(), "$.tipo"));
        String user = JsonPath.read(loginCliente.body(), "$.token");

        // 8. Logado, mas sem permissao
        assertEquals(403, req("POST", "/bairros", bairro, user).statusCode());
        assertEquals(403, req("POST", "/users", cliente.replace("cliente@", "outro@"), user).statusCode());

        // 9. Token alterado no jwt.io (USER -> ADMIN) mantem a assinatura antiga: assinatura invalida
        String[] partes = user.split("\\.");
        String alterado = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload(user).replace("\"USER\"", "\"ADMIN\"").getBytes(StandardCharsets.UTF_8));
        assertEquals(401, req("POST", "/bairros", bairro, partes[0] + "." + alterado + "." + partes[2]).statusCode());
    }

    @Test void rotasUsadasPeloReactContinuamIguais() throws Exception {
        var login = req("POST", "/auth/login", "{\"email\":\"admin@example.com\",\"senha\":\"AdminTeste123!\"}", null);
        assertEquals(200, login.statusCode(), login.body());
        assertEquals("ADMIN", JsonPath.read(login.body(), "$.perfil"));
        assertEquals("ADMIN", JsonPath.read(login.body(), "$.tipo"));
        String admin = JsonPath.read(login.body(), "$.token");
        assertEquals(200, req("GET", "/auth/me", null, admin).statusCode());
        assertEquals(200, req("GET", "/usuarios", null, admin).statusCode());
        assertEquals(200, req("GET", "/users", null, admin).statusCode());
        assertEquals(401, req("POST", "/public/login", "{\"email\":\"admin@example.com\",\"password\":\"errada\"}", null).statusCode());
    }
}
