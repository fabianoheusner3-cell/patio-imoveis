package com.example.demo.controllers;

import java.net.URI;
import java.util.List;
import java.util.Map;
import jakarta.mail.MessagingException;
import com.example.demo.services.EmailService;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/emails")
@ConditionalOnProperty(name = "app.mail.test-enabled", havingValue = "true")
public class EmailController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailController.class);
    private final EmailService service;

    public EmailController(EmailService service) {
        this.service = service;
    }

    public record EmailTexto(String destinatario, String assunto, String texto) {}

    @PostMapping("/texto")
    public ResponseEntity<Void> enviarTexto(@RequestBody EmailTexto email) {
        if (email.destinatario() == null || email.destinatario().isBlank()
                || email.assunto() == null || email.assunto().isBlank()
                || email.texto() == null || email.texto().isBlank()
                || email.destinatario().contains("\r") || email.destinatario().contains("\n")
                || email.assunto().contains("\r") || email.assunto().contains("\n")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe destinatario, assunto e texto validos.");
        }
        try {
            new InternetAddress(email.destinatario(), true).validate();
        } catch (AddressException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destinatario invalido.");
        }
        try {
            service.enviarTexto(email.destinatario(), email.assunto(), email.texto());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configuracao de email ausente ou invalida.");
        } catch (MailException ex) {
            throw falhaDeEnvio(ex);
        }
        return ResponseEntity.noContent().build();
    }

    public record EmailTemplate(String destinatario, String assunto, String nome,
            List<String> itens, String link) {}

    @PostMapping("/template")
    public ResponseEntity<Void> enviarTemplate(@RequestBody EmailTemplate email) {
        if (email.destinatario() == null || email.destinatario().isBlank()
                || email.assunto() == null || email.assunto().isBlank()
                || email.nome() == null || email.nome().isBlank()
                || email.itens() == null || email.itens().stream().anyMatch(item -> item == null || item.isBlank())
                || email.link() == null || email.link().isBlank()
                || email.destinatario().contains("\r") || email.destinatario().contains("\n")
                || email.assunto().contains("\r") || email.assunto().contains("\n")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe destinatario, assunto, nome, itens e link validos.");
        }
        try {
            new InternetAddress(email.destinatario(), true).validate();
            URI link = URI.create(email.link());
            if (!("https".equalsIgnoreCase(link.getScheme()) || "http".equalsIgnoreCase(link.getScheme()))
                    || link.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (AddressException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destinatario ou link invalido.");
        }
        try {
            service.enviarEmailComTemplate(email.destinatario(), email.assunto(), "email/template",
                    Map.of("nome", email.nome(), "itens", email.itens(), "link", email.link()));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configuracao de email ausente ou invalida.");
        } catch (MessagingException | MailException ex) {
            throw falhaDeEnvio(ex);
        }
        return ResponseEntity.noContent().build();
    }

    private ResponseStatusException falhaDeEnvio(Exception erro) {
        String diagnostico = "Falha no provedor de email.";
        Throwable causa = erro;
        for (int nivel = 0; causa != null && nivel < 20; nivel++, causa = causa.getCause()) {
            if (causa instanceof org.springframework.mail.MailAuthenticationException
                    || causa instanceof jakarta.mail.AuthenticationFailedException) {
                diagnostico = "Autenticacao SMTP recusada. Confira o usuario e a senha completos de My Sandbox.";
                break;
            }
            if (causa instanceof javax.net.ssl.SSLException) {
                diagnostico = "Falha TLS ao conectar ao SMTP. Confira certificados e inspecao de conexoes no antivirus.";
                break;
            }
            if (causa instanceof java.net.SocketTimeoutException) {
                diagnostico = "Tempo de conexao SMTP esgotado. Confira rede, firewall e porta SMTP.";
                break;
            }
            if (causa instanceof java.net.ConnectException || causa instanceof java.net.UnknownHostException) {
                diagnostico = "Nao foi possivel conectar ao SMTP. Confira rede, host e porta.";
                break;
            }
        }
        // Somente tipos e diagnostico: nao registrar senha, conteudo ou resposta do provedor.
        log.warn("EMAIL_DIAGNOSTICO: {} Tipo: {}", diagnostico, erro.getClass().getSimpleName());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, diagnostico);
    }
}