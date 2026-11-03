package com.example.demo.services;

import java.util.Map;
import jakarta.mail.MessagingException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class EmailService {
    private final TemplateEngine templateEngine;
    private final JavaMailSender mailSender;
    private final RestClient client;
    private final String remetente;
    private final String transporte;
    private final String apiKey;
    private final String dominio;

    public EmailService(JavaMailSender mailSender, RestClient.Builder builder, TemplateEngine templateEngine,
            @Value("${app.mail.from}") String remetente,
            @Value("${app.mail.transport:smtp}") String transporte,
            @Value("${app.mail.api-key:}") String apiKey,
            @Value("${app.mail.domain:}") String dominio,
            @Value("${app.mail.api-base-url:https://api.mailgun.net}") String baseUrl) {
        this.templateEngine = templateEngine;
        this.mailSender = mailSender;
        this.remetente = remetente;
        this.transporte = transporte;
        this.apiKey = apiKey;
        this.dominio = dominio;
        this.client = builder.baseUrl(baseUrl).build();
    }

    public void enviarTexto(String destinatario, String assunto, String texto) {
        if (remetente.isBlank()) {
            throw new IllegalStateException("Configure MAILGUN_FROM para enviar emails.");
        }
        if ("api".equalsIgnoreCase(transporte)) {
            enviarPelaApi(destinatario, assunto, "text", texto);
        } else if ("smtp".equalsIgnoreCase(transporte)) {
            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setFrom(remetente);
            mensagem.setTo(destinatario);
            mensagem.setSubject(assunto);
            mensagem.setText(texto);
            mailSender.send(mensagem);
        } else {
            throw new IllegalStateException("MAIL_TRANSPORT deve ser api ou smtp.");
        }
    }

    public void enviarEmailComTemplate(String para, String assunto, String template,
            Map<String, Object> variaveis) throws MessagingException {
        if (remetente.isBlank()) {
            throw new IllegalStateException("Configure MAILGUN_FROM para enviar emails.");
        }
        Context context = new Context(java.util.Locale.forLanguageTag("pt-BR"));
        context.setVariables(variaveis);
        String html = templateEngine.process(template, context);
        if ("api".equalsIgnoreCase(transporte)) {
            enviarPelaApi(para, assunto, "html", html);
        } else if ("smtp".equalsIgnoreCase(transporte)) {
            var mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(para);
            helper.setSubject(assunto);
            helper.setText(html, true);
            mailSender.send(mime);
        } else {
            throw new IllegalStateException("MAIL_TRANSPORT deve ser api ou smtp.");
        }
    }

    private void enviarPelaApi(String destinatario, String assunto, String tipo, String conteudo) {
        if (apiKey.isBlank() || dominio.isBlank()) {
            throw new IllegalStateException("Configure MAILGUN_API_KEY e MAILGUN_DOMAIN.");
        }
        var formulario = new LinkedMultiValueMap<String, Object>();
        formulario.add("from", remetente);
        formulario.add("to", destinatario);
        formulario.add("subject", assunto);
        formulario.add(tipo, conteudo);
        try {
            client.post().uri("/v3/{domain}/messages", dominio)
                    .headers(headers -> headers.setBasicAuth("api", apiKey))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(formulario)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            // Nao exponha resposta do provedor ou credenciais ao cliente.
            throw new MailSendException("Falha ao enviar email pela API Mailgun.");
        }
    }
}