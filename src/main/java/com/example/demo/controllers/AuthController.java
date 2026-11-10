package com.example.demo.controllers;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import com.example.demo.repositories.UsuarioRepository;
import com.example.demo.services.UsuarioService;
import com.fasterxml.jackson.annotation.JsonAlias;

@RestController
public class AuthController {
    /** "password" e o nome usado no exemplo do professor; "senha" continua aceito. */
    public record Login(String email, @JsonAlias("password") String senha) {}
    /** tipo traz o perfil (ADMIN ou USER), como na tabela do cronograma; perfil continua para o React. */
    public record Token(String token, String tipo, long expiraEm, String perfil, String nome) {}
    private final UsuarioRepository usuarios;
    @org.springframework.beans.factory.annotation.Autowired private UsuarioService usuarioService;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final long validade;
    private final String dummyHash;
    public AuthController(UsuarioRepository usuarios, PasswordEncoder passwords, JwtEncoder encoder,
            @Value("${app.jwt.expiration-seconds:3600}") long validade) {
        this.usuarios = usuarios; this.passwords = passwords; this.encoder = encoder; this.validade = validade;
        if (validade <= 0) throw new IllegalArgumentException("Validade do JWT deve ser positiva.");
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }
    /** Cadastro feito pelo proprio cliente na tela do React. */
    public record Cadastro(String nome, String email, @JsonAlias("password") String senha) {}
    // Publico, mas sempre cria perfil USER: um visitante nunca consegue se cadastrar como ADMIN.
    @PostMapping("/public/cadastro")
    public org.springframework.http.ResponseEntity<com.example.demo.models.UsuarioModel> cadastrar(@RequestBody Cadastro dados) {
        var usuario = usuarioService.salvar(null, new UsuarioService.DadosUsuario(
            dados.nome(), dados.email(), dados.senha(), com.example.demo.models.UsuarioModel.Perfil.USER));
        return org.springframework.http.ResponseEntity.created(java.net.URI.create("/usuarios/" + usuario.getId())).body(usuario);
    }
    @GetMapping("/auth/me") public com.example.demo.models.UsuarioModel me(org.springframework.security.core.Authentication auth) {
        return usuarios.findById(Long.valueOf(auth.getName())).orElseThrow();
    }
    // /public/login segue a tabela do cronograma; /auth/login continua sendo usado pelo React.
    @PostMapping({"/auth/login", "/public/login"}) public Token login(@RequestBody Login dados) {
        var usuario = usuarios.findByEmail(UsuarioService.normalizarEmail(dados.email()));
        boolean senhaValida = dados.senha() != null && passwords.matches(dados.senha(),
            usuario.map(u -> u.getSenha()).orElse(dummyHash));
        if (usuario.isEmpty() || !senhaValida)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou senha invalidos.");
        var u = usuario.get();
        String perfil = u.getPerfil().name();
        var agora = Instant.now();
        // role define as permissoes; tipo repete o perfil para leitura no jwt.io, como pede o cronograma.
        var claims = JwtClaimsSet.builder().issuer("bairro-pageable").subject(u.getId().toString())
            .issuedAt(agora).expiresAt(agora.plusSeconds(validade)).id(UUID.randomUUID().toString())
            .claim("role", perfil).claim("tipo", perfil).build();
        String token = encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new Token(token, perfil, validade, perfil, u.getNome());
    }
}
