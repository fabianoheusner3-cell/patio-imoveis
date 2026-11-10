package com.example.demo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

/** Vitrine do React: paginacao, filtros e ordenacao feitos no servidor (/imoveis/imoveis-page). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:vitrine-test", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never", "app.admin.email=admin@example.com", "app.admin.password=AdminTeste123!"
})
class VitrinePaginadaApiTest {
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> req(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    int id(HttpResponse<String> criado) {
        String location = criado.headers().firstValue("Location").orElseThrow();
        return Integer.parseInt(location.substring(location.lastIndexOf('/') + 1));
    }

    String imovel(String titulo, String status, Integer venda, Integer aluguel, int bairro, int tipo) {
        return "{\"titulo\":\"" + titulo + "\",\"status\":\"" + status + "\",\"cidade\":\"Panambi\","
            + "\"precoVenda\":" + venda + ",\"precoAluguel\":" + aluguel
            + ",\"bairro\":{\"id\":" + bairro + "},\"tipoImovel\":{\"id\":" + tipo + "}}";
    }

    List<String> titulos(String query) throws Exception {
        var r = req("GET", "/imoveis/imoveis-page?vitrine=true&" + query, null, null);
        assertEquals(200, r.statusCode(), r.body());
        return JsonPath.read(r.body(), "$.content[*].titulo");
    }

    @Test void vitrinePaginadaComFiltros() throws Exception {
        var login = req("POST", "/auth/login", "{\"email\":\"admin@example.com\",\"senha\":\"AdminTeste123!\"}", null);
        String admin = JsonPath.read(login.body(), "$.token");
        int centro = id(req("POST", "/bairros", "{\"nome\":\"Centro\"}", admin));
        int planalto = id(req("POST", "/bairros", "{\"nome\":\"Planalto\"}", admin));
        int casa = id(req("POST", "/tipos-imoveis", "{\"nome\":\"Casa\"}", admin));

        for (String body : List.of(
                imovel("Casa barata", "Disponível", 200000, null, centro, casa),
                imovel("Casa cara", "Disponível", 900000, null, planalto, casa),
                imovel("Apto aluguel", "Disponível", null, 1500, centro, casa),
                imovel("Venda e aluguel", "Disponível", 500000, 2500, planalto, casa),
                imovel("Ja vendida", "Vendido", 300000, null, centro, casa))) {
            assertEquals(201, req("POST", "/imoveis", body, admin).statusCode());
        }

        // Publico, so disponiveis, mais recentes primeiro, com os totais do Pageable
        var pagina = req("GET", "/imoveis/imoveis-page?vitrine=true&page=0&size=3", null, null);
        assertEquals(200, pagina.statusCode(), pagina.body());
        assertEquals(List.of("Venda e aluguel", "Apto aluguel", "Casa cara"), JsonPath.read(pagina.body(), "$.content[*].titulo"));
        assertTrue(pagina.body().contains("\"totalElements\":4"), pagina.body());
        assertTrue(pagina.body().contains("\"totalPages\":2"), pagina.body());
        assertEquals(List.of("Casa barata"), titulos("page=1&size=3"));

        // Filtros
        assertEquals(List.of("Apto aluguel", "Casa barata"), titulos("bairro=" + centro));
        assertEquals(List.of("Venda e aluguel", "Apto aluguel"), titulos("finalidade=ALUGUEL"));
        assertEquals(List.of("Casa cara"), titulos("busca=" + URLEncoder.encode("CASA CARA", StandardCharsets.UTF_8)));
        assertEquals(List.of("Venda e aluguel", "Casa barata"), titulos("finalidade=VENDA&min=100000&max=600000"));
        // Sem finalidade, o preco vale para venda ou aluguel
        assertEquals(List.of("Venda e aluguel", "Apto aluguel"), titulos("max=3000"));

        // Ordenacao pelo preco (sem finalidade: venda, ou aluguel quando so e alugado)
        assertEquals(List.of("Apto aluguel", "Casa barata", "Venda e aluguel", "Casa cara"), titulos("sort=preco,asc"));
        assertEquals(List.of("Venda e aluguel", "Apto aluguel"), titulos("finalidade=ALUGUEL&sort=preco,desc"));

        // A rota original da aula continua igual: todos os imoveis, inclusive os vendidos
        var original = req("GET", "/imoveis/imoveis-page?page=0&size=10", null, null);
        assertTrue(original.body().contains("Ja vendida"), original.body());
    }
}
