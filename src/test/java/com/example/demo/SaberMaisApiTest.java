package com.example.demo;

import com.example.demo.models.*;
import com.example.demo.repositories.*;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:sabermais-test",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "app.mail.transport=smtp", "app.mail.from=imobiliaria@example.com",
        "app.imobiliaria.nome=Imobiliaria Aula 10",
        "app.imobiliaria.mensagem=Vamos ajudar voce a conhecer este imovel!"
})
class SaberMaisApiTest {
    @LocalServerPort int port;
    @MockitoBean JavaMailSender sender;
    @Autowired ImovelRepository imoveis;
    @Autowired TipoImovelRepository tipos;
    @Autowired BairroRepository bairros;
    private int id;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void preparar() {
        reset(sender);
        when(sender.createMimeMessage()).thenAnswer(invocation ->
                new MimeMessage(Session.getInstance(new Properties())));
        TipoImovelModel tipo = new TipoImovelModel();
        tipo.setNome("Casa");
        tipo = tipos.save(tipo);
        BairroModel bairro = new BairroModel();
        bairro.setNome("Centro");
        bairro = bairros.save(bairro);
        ImovelModel imovel = new ImovelModel();
        imovel.setTitulo("Casa <script>alert(1)</script>");
        imovel.setDescricao("Casa com jardim e varanda.");
        imovel.setTipoImovel(tipo);
        imovel.setBairro(bairro);
        imovel.setPrecoVenda(new BigDecimal("850000.00"));
        imovel.setPrecoAluguel(new BigDecimal("3500.00"));
        imovel.setDormitorios(3);
        imovel.setEndereco("Rua das Flores");
        imovel.setNumero("123");
        imovel.setAreaTotal(new BigDecimal("300.50"));
        id = imoveis.save(imovel).getId();
    }

    private String json(Integer imovelId) {
        return """
                {"nome":"Fabiano <b>Heusner</b>","telefone":"(55) 99999-1234",
                 "email":"cliente@example.com","mensagem":"Quero saber mais e marcar uma visita.",
                 "imovelId":%s}
                """.formatted(imovelId == null ? "null" : imovelId);
    }

    private HttpResponse<String> post(int codigo, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/imoveis/sabermais/" + codigo))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String html(Part part) throws Exception {
        if (part.isMimeType("text/html")) return (String) part.getContent();
        if (part.isMimeType("multipart/*")) {
            Multipart multi = (Multipart) part.getContent();
            for (int indice = 0; indice < multi.getCount(); indice++) {
                String valor = html(multi.getBodyPart(indice));
                if (valor != null) return valor;
            }
        }
        return null;
    }

    @Test
    void postEnviaHtmlAoClienteComDadosDoImovelEMensagemPersonalizada() throws Exception {
        var response = post(id, json(id));
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("Solicitação recebida"));
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage mensagem = captor.getValue();
        mensagem.saveChanges();
        assertEquals("cliente@example.com", mensagem.getAllRecipients()[0].toString());
        assertEquals("imobiliaria@example.com", mensagem.getFrom()[0].toString());
        assertEquals("Informações sobre o imóvel #" + id, mensagem.getSubject());
        String html = html(mensagem);
        assertNotNull(html);
        for (String esperado : new String[] {"Fabiano &lt;b&gt;Heusner&lt;/b&gt;",
                "Casa &lt;script&gt;alert(1)&lt;/script&gt;", "Centro", "Rua das Flores", "123",
                "850.000,00", "3.500,00", "300,50", "Casa com jardim e varanda.",
                "Imobiliaria Aula 10", "Vamos ajudar voce", "(55) 99999-1234",
                "cliente@example.com", "Quero saber mais e marcar uma visita.", "Não informado"}) {
            assertTrue(html.contains(esperado), esperado);
        }
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("th:text"));
    }

    @Test
    void identificadorNaUrlESuficiente() throws Exception {
        assertEquals(200, post(id, json(null)).statusCode());
        verify(sender).send(any(MimeMessage.class));
    }

    @Test
    void rejeitaDadosInvalidosSemEnviarEmail() throws Exception {
        for (String body : new String[] {
                json(id).replace("Fabiano <b>Heusner</b>", ""),
                json(id).replace("(55) 99999-1234", "abcdef"),
                json(id).replace("cliente@example.com", "email-invalido"),
                json(id).replace("Quero saber mais e marcar uma visita.", ""),
                json(id).replace("cliente@example.com", "cliente@example.com\\r\\nBcc:outro@example.com"),
                json(id + 1), "{}", "null"}) {
            assertEquals(400, post(id, body).statusCode(), body);
        }
        verifyNoInteractions(sender);
    }

    @Test
    void imovelInexistenteRetorna404SemEnviarEmail() throws Exception {
        assertEquals(404, post(999999, json(null)).statusCode());
        verifyNoInteractions(sender);
    }

    @Test
    void falhaSmtpRetorna502SemSucessoFalsoOuExporSenha() throws Exception {
        doThrow(new MailAuthenticationException("detalhe-confidencial"))
                .when(sender).send(any(MimeMessage.class));
        var resposta = post(id, json(id));
        assertEquals(502, resposta.statusCode());
        assertFalse(resposta.body().contains("detalhe-confidencial"));
    }
}