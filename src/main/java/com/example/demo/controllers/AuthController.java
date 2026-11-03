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

@RestController
@RequestMapping("/auth")
public class AuthController {
    public record Login(String email, String senha) {}
    public record Token(String token, String tipo, long expiraEm, String perfil, String nome) {}
    private final UsuarioRepository usuarios;
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
    @GetMapping("/me") public com.example.demo.models.UsuarioModel me(org.springframework.security.core.Authentication auth) {
        return usuarios.findById(Long.valueOf(auth.getName())).orElseThrow();
    }
    @PostMapping("/login") public Token login(@RequestBody Login dados) {
        var usuario = usuarios.findByEmail(UsuarioService.normalizarEmail(dados.email()));
        boolean senhaValida = dados.senha() != null && passwords.matches(dados.senha(),
            usuario.map(u -> u.getSenha()).orElse(dummyHash));
        if (usuario.isEmpty() || !senhaValida)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou senha invalidos.");
        var u = usuario.get();
        var agora = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("bairro-pageable").subject(u.getId().toString())
            .issuedAt(agora).expiresAt(agora.plusSeconds(validade)).id(UUID.randomUUID().toString())
            .claim("role", u.getPerfil().name()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new Token(token, "Bearer", validade, u.getPerfil().name(), u.getNome());
    }
}
