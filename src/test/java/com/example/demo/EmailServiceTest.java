package com.example.demo;

import com.example.demo.services.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EmailServiceTest {
    private org.thymeleaf.TemplateEngine templateEngine() {
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode(org.thymeleaf.templatemode.TemplateMode.HTML);
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
    private EmailService service(JavaMailSender sender, RestClient.Builder builder, String transporte, String key) {
        return new EmailService(sender, builder, templateEngine(), "postmaster@sandbox.mailgun.org",
                transporte, key, "sandbox.mailgun.org", "https://api.mailgun.net");
    }

    @Test
    void smtpContinuaEnviandoTexto() {
        JavaMailSender sender = mock(JavaMailSender.class);
        service(sender, RestClient.builder(), "smtp", "").enviarTexto("destinatario@example.com", "Teste", "Texto");
        var mensagem = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mensagem.capture());
        assertEquals("postmaster@sandbox.mailgun.org", mensagem.getValue().getFrom());
        assertArrayEquals(new String[]{"destinatario@example.com"}, mensagem.getValue().getTo());
        assertEquals("Teste", mensagem.getValue().getSubject());
        assertEquals("Texto", mensagem.getValue().getText());
    }

    @Test
    void apiEnviaFormularioComAutenticacaoETextoUtf8() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sender = mock(JavaMailSender.class);
        var service = service(sender, builder, "api", "chave-ficticia");
        server.expect(requestTo("https://api.mailgun.net/v3/sandbox.mailgun.org/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString("api:chave-ficticia".getBytes(StandardCharsets.UTF_8))))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"from\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("postmaster@sandbox.mailgun.org")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("destinatario@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Olá Fabiano!")))
                .andRespond(withSuccess("{\"message\":\"Queued\"}", MediaType.APPLICATION_JSON));
        service.enviarTexto("destinatario@example.com", "Teste", "Olá Fabiano!");
        server.verify();
        verifyNoInteractions(sender);
    }

    @Test
    void apiNaoEnviaSemChave() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sender = mock(JavaMailSender.class);
        assertThrows(IllegalStateException.class,
                () -> service(sender, builder, "api", "").enviarTexto("destinatario@example.com", "Teste", "Texto"));
        server.verify();
        verifyNoInteractions(sender);
    }

    @Test
    void apiTrataContaDesativadaSemExporRespostaDoProvedor() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = service(mock(JavaMailSender.class), builder, "api", "chave-ficticia");
        server.expect(requestTo("https://api.mailgun.net/v3/sandbox.mailgun.org/messages"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("Account disabled"));
        var erro = assertThrows(MailSendException.class,
                () -> service.enviarTexto("destinatario@example.com", "Teste", "Texto"));
        assertEquals("Falha ao enviar email pela API Mailgun.", erro.getMessage());
        server.verify();
    }

    @Test
    void smtpEnviaTemplateHtmlUtf8ComVariaveisEscapadas() throws Exception {
        var sender = mock(JavaMailSender.class);
        var mime = new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);
        service(sender, RestClient.builder(), "smtp", "").enviarEmailComTemplate(
                "destinatario@example.com", "Bem-vindo!", "email/template",
                java.util.Map.of("nome", "<script>Maria</script>", "itens",
                        java.util.List.of("Plano Pro", "Suporte 24h"), "link", "https://example.com"));
        verify(sender).send(mime);
        mime.saveChanges();
        assertEquals("Bem-vindo!", mime.getSubject());
        assertEquals("postmaster@sandbox.mailgun.org", ((jakarta.mail.internet.InternetAddress) mime.getFrom()[0]).getAddress());
        assertEquals("destinatario@example.com", ((jakarta.mail.internet.InternetAddress) mime.getAllRecipients()[0]).getAddress());
        var mixed = (jakarta.mail.Multipart) mime.getContent();
        var related = (jakarta.mail.Multipart) mixed.getBodyPart(0).getContent();
        var htmlPart = related.getBodyPart(0);
        assertTrue(htmlPart.getContentType().toLowerCase().contains("text/html"));
        assertTrue(htmlPart.getContentType().toLowerCase().contains("utf-8"));
        String html = (String) htmlPart.getContent();
        assertTrue(html.contains("Olá"));
        assertTrue(html.contains("&lt;script&gt;Maria&lt;/script&gt;"));
        assertTrue(html.contains("Plano Pro"));
        assertTrue(html.contains("Suporte 24h"));
        assertTrue(html.contains("href=\"https://example.com\""));
        assertFalse(html.contains("th:text"));
    }

    @Test
    void apiEnviaTemplateNoCampoHtml() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sender = mock(JavaMailSender.class);
        var service = service(sender, builder, "api", "chave-ficticia");
        server.expect(requestTo("https://api.mailgun.net/v3/sandbox.mailgun.org/messages"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"html\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Maria")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Plano Pro")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"text\""))))
                .andRespond(withSuccess());
        service.enviarEmailComTemplate("destinatario@example.com", "Bem-vindo!", "email/template",
                java.util.Map.of("nome", "Maria", "itens", java.util.List.of("Plano Pro"), "link", "https://example.com"));
        server.verify();
        verifyNoInteractions(sender);
    }
}