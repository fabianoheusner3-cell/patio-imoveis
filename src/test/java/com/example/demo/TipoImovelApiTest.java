package com.example.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.example.demo.repositories.TipoImovelRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:tipos-imoveis-test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always"
})
class TipoImovelApiTest extends AdminApiSupport {
    @LocalServerPort
    private int port;

    @Autowired
    private TipoImovelRepository repository;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + adminToken()).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void paginacaoRespeitaTamanhoOrdemETotal() throws Exception {
        var response = request("GET", "/tipos-imoveis/tipos-imoveis-page?page=0&size=5&sort=id,DESC", null);
        assertEquals(200, response.statusCode());
        assertEquals(12, (int) JsonPath.read(response.body(), "$.totalElements"));
        assertEquals(3, (int) JsonPath.read(response.body(), "$.totalPages"));
        assertEquals(List.of(12, 11, 10, 9, 8), JsonPath.read(response.body(), "$.content[*].id"));

        var last = request("GET", "/tipos-imoveis/tipos-imoveis-page?page=2&size=5&sort=id,DESC", null);
        assertEquals(List.of(2, 1), JsonPath.read(last.body(), "$.content[*].id"));
        var beyond = request("GET", "/tipos-imoveis/tipos-imoveis-page?page=3&size=5&sort=id,DESC", null);
        assertEquals(List.of(), JsonPath.read(beyond.body(), "$.content"));
        var ascending = request("GET", "/tipos-imoveis/tipos-imoveis-page?page=0&size=5&sort=id,ASC", null);
        assertEquals(List.of(1, 2, 3, 4, 5), JsonPath.read(ascending.body(), "$.content[*].id"));
    }

    @Test
    void cadastroConsultaAtualizacaoEExclusao() throws Exception {
        String body = """
                {"nome":"Teste"}
                """;
        var created = request("POST", "/tipos-imoveis", body);
        assertEquals(201, created.statusCode());
        String path = URI.create(created.headers().firstValue("Location").orElseThrow()).getPath();
        assertNotEquals("/tipos-imoveis/1", path);
        try {
            var found = request("GET", path, null);
            assertEquals(200, found.statusCode());
            assertEquals("Teste", JsonPath.read(found.body(), "$.nome"));
            assertEquals(204, request("PUT", path, body.replace("Teste", "Atualizado")).statusCode());
            assertEquals("Atualizado", JsonPath.read(request("GET", path, null).body(), "$.nome"));
            assertEquals(204, request("DELETE", path, null).statusCode());
            var missing = request("GET", path, null);
            assertEquals(404, missing.statusCode());
            assertEquals(404, request("PUT", path, body).statusCode());
            assertFalse(repository.existsById(Integer.valueOf(path.substring(path.lastIndexOf('/') + 1))));
            assertEquals(404, request("DELETE", path, null).statusCode());
            var all = request("GET", "/tipos-imoveis", null);
            assertEquals(200, all.statusCode());
            assertEquals(12, ((List<?>) JsonPath.read(all.body(), "$" )).size());
        } finally {
            repository.deleteById(Integer.valueOf(path.substring(path.lastIndexOf('/') + 1)));
        }
    }
}
