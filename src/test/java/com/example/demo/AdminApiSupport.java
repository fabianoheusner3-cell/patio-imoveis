package com.example.demo;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import com.example.demo.models.UsuarioModel;
import com.example.demo.repositories.UsuarioRepository;

abstract class AdminApiSupport {
    @Autowired private UsuarioRepository usuarios;
    @Autowired private JwtEncoder encoder;
    protected String adminToken() {
        var usuario = usuarios.findByEmail("regression-admin@example.com").orElseGet(() -> {
            var u = new UsuarioModel(); u.setNome("Admin testes"); u.setEmail("regression-admin@example.com");
            u.setSenha("unused"); u.setPerfil(UsuarioModel.Perfil.ADMIN); return usuarios.save(u);
        });
        var now = Instant.now();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer("bairro-pageable").subject(usuario.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("role", "ADMIN").build())).getTokenValue();
    }
}
