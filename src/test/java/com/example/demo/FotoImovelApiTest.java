package com.example.demo;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDirFactory;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import java.io.IOException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:fotos-test",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
class FotoImovelApiTest extends AdminApiSupport {
    public static class ProjectTempDirFactory implements TempDirFactory {
        @Override
        public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context)
                throws IOException {
            Path target = Path.of("target").toAbsolutePath();
            Files.createDirectories(target);
            return Files.createTempDirectory(target, "foto-test-");
        }
    }

    @TempDir(factory = ProjectTempDirFactory.class) static Path uploads;
    @LocalServerPort private int port;
    private final HttpClient client = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void uploadDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> uploads.toString());
    }

    private HttpResponse<String> request(String method, String path, String body, String contentType) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + adminToken()).header("Content-Type", contentType)
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, contentType.startsWith("multipart/") ? java.nio.charset.StandardCharsets.ISO_8859_1 : java.nio.charset.StandardCharsets.UTF_8))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    private int create(String path, String body) throws Exception {
        var response = request("POST", path, body, "application/json");
        assertEquals(201, response.statusCode(), response.body());
        String location = response.headers().firstValue("Location").orElseThrow();
        return Integer.parseInt(location.substring(location.lastIndexOf('/') + 1));
    }

    private HttpResponse<String> upload(int imovelId, String content) throws Exception {
        return upload(imovelId, content, "POST", "/fotos");
    }

    private HttpResponse<String> upload(int imovelId, String content, String method, String path) throws Exception {
        String body = "--foto-teste\r\nContent-Disposition: form-data; name=\"ordem\"\r\n\r\n0\r\n"
            + "--foto-teste\r\nContent-Disposition: form-data; name=\"imovelId\"\r\n\r\n"
            + imovelId + "\r\n--foto-teste\r\n"
            + "Content-Disposition: form-data; name=\"arquivo\"; filename=\"foto.png\"\r\n"
            + "Content-Type: image/png\r\n\r\n" + content + "\r\n--foto-teste--\r\n";
        return request(method, path, body, "multipart/form-data; boundary=foto-teste");
    }

    @Test
    void enviaListaEApagaArquivoDoImovel() throws Exception {
        int tipo = create("/tipos-imoveis", "{\"nome\":\"Casa\"}");
        int bairro = create("/bairros", "{\"nome\":\"Centro\"}");
        int imovel = create("/imoveis",
            "{\"titulo\":\"Casa com foto\",\"tipoImovel\":{\"id\":" + tipo
            + "},\"bairro\":{\"id\":" + bairro + "}}");
        assertEquals(400, upload(imovel, "").statusCode());
        assertEquals(404, upload(999999, "foto de teste").statusCode());
        assertEquals(400, upload(imovel, "arquivo inválido").statusCode());
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
            java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        String png = output.toString(java.nio.charset.StandardCharsets.ISO_8859_1);
        var saved = upload(imovel, png);
        assertEquals(201, saved.statusCode(), saved.body());
        int foto = JsonPath.read(saved.body(), "$.id");
        Path file = Path.of((String) JsonPath.read(saved.body(), "$.caminho"));
        assertTrue(file.startsWith(uploads));
        assertArrayEquals(output.toByteArray(), Files.readAllBytes(file));
        var image = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
            + "/fotos/" + foto + "/arquivo")).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, image.statusCode());
        assertEquals("image/png", image.headers().firstValue("Content-Type").orElseThrow());
        assertArrayEquals(output.toByteArray(), image.body());
        assertEquals(imovel, (int) JsonPath.read(saved.body(), "$.imovel.id"));
        var list = request("GET", "/fotos/imovel/" + imovel, null, "application/json");
        assertEquals(200, list.statusCode(), list.body());
        assertEquals(1, ((List<?>) JsonPath.read(list.body(), "$")).size());
        assertEquals(0, ((List<?>) JsonPath.read(request("GET", "/fotos/imovel/999999", null,
            "application/json").body(), "$")).size());
        var second = upload(imovel, png);
        int segunda = JsonPath.read(second.body(), "$.id");
        int outroImovel = create("/imoveis", "{\"titulo\":\"Outro imóvel\",\"tipoImovel\":{\"id\":"
            + tipo + "},\"bairro\":{\"id\":" + bairro + "}}");
        int terceira = JsonPath.read(upload(outroImovel, png).body(), "$.id");
        assertEquals(200, request("PUT", "/fotos/" + terceira + "/capa", null, "application/json").statusCode());
        assertEquals(200, request("PUT", "/fotos/" + foto + "/capa", null, "application/json").statusCode());
        assertEquals(200, request("PUT", "/fotos/" + segunda + "/capa", null, "application/json").statusCode());
        assertEquals(List.of(segunda), (List<?>) JsonPath.read(request("GET", "/fotos/imovel/" + imovel,
            null, "application/json").body(), "$[?(@.capa == true)].id"));
        assertEquals(List.of(terceira), (List<?>) JsonPath.read(request("GET", "/fotos/imovel/" + outroImovel,
            null, "application/json").body(), "$[?(@.capa == true)].id"));
        assertEquals(404, request("PUT", "/fotos/999999/capa", null, "application/json").statusCode());
        assertEquals(400, request("PUT", "/fotos/" + foto, "{\"ordem\":-1}", "application/json").statusCode());
        assertEquals(400, request("PUT", "/fotos/" + foto, "{}", "application/json").statusCode());
        assertEquals(404, request("PUT", "/fotos/999999", "{\"ordem\":1}", "application/json").statusCode());
        assertEquals(200, request("PUT", "/fotos/" + foto, "{\"ordem\":5}", "application/json").statusCode());
        assertEquals(200, request("PUT", "/fotos/" + segunda, "{\"ordem\":0}", "application/json").statusCode());
        assertEquals(segunda, (int) JsonPath.read(request("GET", "/fotos/imovel/" + imovel,
            null, "application/json").body(), "$[0].id"));
        Path antigo = Path.of((String) JsonPath.read(second.body(), "$.caminho"));
        assertEquals(400, upload(imovel, "inválida", "PUT", "/fotos/" + segunda).statusCode());
        assertTrue(Files.exists(antigo));
        var editada = upload(imovel, png, "PUT", "/fotos/" + segunda);
        assertEquals(200, editada.statusCode(), editada.body());
        assertEquals(segunda, (int) JsonPath.read(editada.body(), "$.id"));
        assertEquals(true, JsonPath.read(editada.body(), "$.capa"));
        assertEquals(0, (int) JsonPath.read(editada.body(), "$.ordem"));
        assertFalse(Files.exists(antigo));
        Path novo = Path.of((String) JsonPath.read(editada.body(), "$.caminho"));
        assertArrayEquals(output.toByteArray(), Files.readAllBytes(novo));
        assertEquals(204, request("DELETE", "/fotos/" + segunda, null, "application/json").statusCode());
        assertFalse(Files.exists(novo));
        assertEquals(204, request("DELETE", "/imoveis/" + outroImovel, null, "application/json").statusCode());
        assertEquals(0, ((List<?>) JsonPath.read(request("GET", "/fotos/imovel/" + outroImovel,
            null, "application/json").body(), "$")).size());
        assertEquals(204, request("DELETE", "/fotos/" + foto, null, "application/json").statusCode());
        assertFalse(Files.exists(file));
        assertEquals(404, request("GET", "/fotos/" + foto + "/arquivo", null, "application/json").statusCode());
        assertEquals(0, ((List<?>) JsonPath.read(request("GET", "/fotos/imovel/" + imovel, null,
            "application/json").body(), "$")).size());
        assertEquals(404, request("DELETE", "/fotos/" + foto, null, "application/json").statusCode());
    }
}
