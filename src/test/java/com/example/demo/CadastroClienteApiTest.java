package com.example.demo;

import java.net.URI;
import java.net.http.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

/** Cadastro publico do cliente pela tela do React (POST /public/cadastro). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:cadastro-test", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never", "app.admin.email=admin@example.com", "app.admin.password=AdminTeste123!"
})
class CadastroClienteApiTest {
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> post(String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test void clienteSeCadastraSemLoginESempreComoUser() throws Exception {
        // Mesmo pedindo ADMIN, o cadastro publico cria um cliente
        var criado = post("/public/cadastro",
            "{\"nome\":\"Maria\",\"email\":\"Maria@Example.com\",\"senha\":\"Senha123!\",\"tipo\":\"ADMIN\"}", null);
        assertEquals(201, criado.statusCode(), criado.body());
        assertEquals("USER", JsonPath.read(criado.body(), "$.perfil"));
        assertFalse(criado.body().contains("Senha123!"));

        // Entra logo depois e continua sem permissao de admin
        var login = post("/auth/login", "{\"email\":\"maria@example.com\",\"senha\":\"Senha123!\"}", null);
        assertEquals(200, login.statusCode(), login.body());
        assertEquals("USER", JsonPath.read(login.body(), "$.tipo"));
        String token = JsonPath.read(login.body(), "$.token");
        assertEquals(403, post("/bairros", "{\"nome\":\"Centro\"}", token).statusCode());

        // Email repetido e senha curta sao recusados
        var repetido = post("/public/cadastro", "{\"nome\":\"Outra\",\"email\":\"maria@example.com\",\"senha\":\"Senha123!\"}", null);
        assertEquals(409, repetido.statusCode(), repetido.body());
        assertEquals(400, post("/public/cadastro", "{\"nome\":\"Ana\",\"email\":\"ana@example.com\",\"senha\":\"123\"}", null).statusCode());
        assertEquals(400, post("/public/cadastro", "{\"nome\":\"\",\"email\":\"ana@example.com\",\"senha\":\"Senha123!\"}", null).statusCode());
    }
}
