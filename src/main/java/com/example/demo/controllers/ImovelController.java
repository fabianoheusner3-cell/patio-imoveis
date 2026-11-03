package com.example.demo.controllers;

import java.util.List;
import java.util.Map;
import com.example.demo.dto.SaberMaisRequest;
import com.example.demo.services.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.example.demo.models.ImovelModel;
import com.example.demo.services.ImovelService;

@RestController
@RequestMapping("/imoveis")
public class ImovelController {
    private final ImovelService service;
    private final EmailService emailService;
    private final String nomeImobiliaria;
    private final String mensagemImobiliaria;

    public ImovelController(ImovelService service, EmailService emailService,
            @Value("${app.imobiliaria.nome:Imobiliaria Bairro}") String nomeImobiliaria,
            @Value("${app.imobiliaria.mensagem:Obrigado pelo interesse! Nossa equipe entrara em contato para esclarecer suas duvidas e combinar uma visita.}") String mensagemImobiliaria) {
        this.service = service;
        this.emailService = emailService;
        this.nomeImobiliaria = nomeImobiliaria;
        this.mensagemImobiliaria = mensagemImobiliaria;
    }

    @PostMapping("/sabermais/{id}")
    public ResponseEntity<Map<String, String>> saberMais(@PathVariable Integer id,
            @RequestBody SaberMaisRequest cliente) {
        validarCliente(cliente, id);
        ImovelModel imovel = service.find(id);
        try {
            // Conforme a aula: o controller de imoveis chama o servico de email.
            emailService.enviarEmailComTemplate(cliente.email().trim(),
                    "Informações sobre o imóvel #" + imovel.getId(),
                    "email/saber-mais", Map.of(
                            "cliente", cliente, "imovel", imovel,
                            "imobiliaria", nomeImobiliaria,
                            "mensagemImobiliaria", mensagemImobiliaria));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Envio de email não configurado.");
        } catch (MessagingException | MailException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("mensagem", diagnosticoEmail(ex)));
        }
        return ResponseEntity.ok(Map.of("mensagem",
                "Solicitação recebida. O email com as informações do imóvel foi enviado."));
    }

    private void validarCliente(SaberMaisRequest cliente, Integer id) {
        if (id == null || id <= 0
                || !campoValido(cliente.nome(), 150)
                || !campoValido(cliente.telefone(), 30)
                || !campoValido(cliente.email(), 254)
                || !campoValido(cliente.mensagem(), 4000)
                || cliente.nome().contains("\r") || cliente.nome().contains("\n")
                || cliente.email().contains("\r") || cliente.email().contains("\n")
                || !cliente.telefone().matches("[+()0-9.\\s-]{6,30}")
                || (cliente.imovelId() != null && !cliente.imovelId().equals(id))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe nome, telefone, email e mensagem válidos. O ID deve coincidir com a URL.");
        }
        try {
            InternetAddress endereco = new InternetAddress(cliente.email().trim(), true);
            endereco.validate();
            if (endereco.getPersonal() != null || !endereco.getAddress().equals(cliente.email().trim())) {
                throw new AddressException();
            }
        } catch (AddressException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um email válido.");
        }
    }

    private boolean campoValido(String valor, int tamanhoMaximo) {
        return valor != null && !valor.isBlank() && valor.length() <= tamanhoMaximo;
    }
    @GetMapping
    public List<ImovelModel> getAll() { return service.getAll(); }
    @GetMapping("/imoveis-page")
    public Page<ImovelModel> getPage(Pageable pageable) { return service.getAll(pageable); }
    @GetMapping("/{id}")
    public ImovelModel find(@PathVariable Integer id) { return service.find(id); }
    @PostMapping
    public ResponseEntity<Void> insert(@RequestBody ImovelModel model) {
        model = service.insert(model);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(model.getId()).toUri()).build();
    }
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@RequestBody ImovelModel model, @PathVariable Integer id) {
        model.setId(id);
        service.update(model);
        return ResponseEntity.noContent().build();
    }
    public record AlteracaoStatus(String status) {}

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> alterarStatus(@PathVariable Integer id, @RequestBody AlteracaoStatus dados) {
        service.alterarStatus(id, dados.status());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private String diagnosticoEmail(Exception erro) {
        var pendentes = new java.util.ArrayDeque<Throwable>();
        var visitados = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        var tipos = new java.util.ArrayList<String>();
        pendentes.add(erro);
        while (!pendentes.isEmpty() && visitados.size() < 30) {
            Throwable causa = pendentes.removeFirst();
            if (!visitados.add(causa)) continue;
            tipos.add(causa.getClass().getSimpleName());
            if (causa instanceof org.springframework.mail.MailAuthenticationException
                    || causa instanceof jakarta.mail.AuthenticationFailedException) {
                return "Autenticação SMTP recusada. Confira Username e Password completos de My Sandbox.";
            }
            if (causa instanceof javax.net.ssl.SSLException
                    || causa instanceof java.security.cert.CertificateException) {
                return "Falha de certificado/TLS na conexão SMTP. Verifique a configuração de certificados do Java.";
            }
            if (causa instanceof java.net.SocketTimeoutException) {
                return "Conexão SMTP excedeu o tempo limite. Verifique a rede e tente a porta SMTP 2525.";
            }
            if (causa instanceof java.net.ConnectException || causa instanceof java.net.UnknownHostException) {
                return "Não foi possível conectar ao SMTP. Verifique o host, a rede e a porta.";
            }
            if (causa.getCause() != null) pendentes.add(causa.getCause());
            if (causa instanceof jakarta.mail.MessagingException mensagem
                    && mensagem.getNextException() != null) {
                pendentes.add(mensagem.getNextException());
            }
            if (causa instanceof org.springframework.mail.MailSendException envio) {
                for (Exception falha : envio.getFailedMessages().values()) pendentes.add(falha);
            }
        }
        // Apenas nomes de classes, sem mensagens brutas, credenciais ou dados do cliente.
        return "Falha no envio SMTP (" + String.join(", ", tipos) + ").";
    }
}
