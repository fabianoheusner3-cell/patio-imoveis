package com.example.demo.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import com.example.demo.repositories.UsuarioRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean SecretKey jwtKey(@Value("${app.jwt.secret:}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
        } else if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET precisa ter ao menos 32 bytes.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean JwtDecoder jwtDecoder(SecretKey key, UsuarioRepository usuarios) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> userValidator = jwt -> {
            try {
                var usuario = usuarios.findById(Long.valueOf(jwt.getSubject()));
                if (usuario.isPresent() && usuario.get().getPerfil().name().equals(jwt.getClaimAsString("role")))
                    return OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException ignored) { }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Usuario ou perfil invalido.", null));
        };
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator("bairro-pageable"), requiredClaims, userValidator));
        return decoder;
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint unauthorized = (req, res, ex) -> {
            res.setStatus(401);
            res.setHeader("WWW-Authenticate", "Bearer");
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"status\":401,\"erro\":\"Unauthorized\",\"mensagem\":\"Token ausente, invalido ou expirado.\"}");
        };
        AccessDeniedHandler forbidden = (req, res, ex) -> {
            res.setStatus(403);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"status\":403,\"erro\":\"Forbidden\",\"mensagem\":\"Usuario sem permissao para esta acao.\"}");
        };
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> List.of(
            new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/login", "/public/login", "/public/cadastro").permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/*.html", "/*.js", "/*.css", "/favicon.ico",
                    "/imoveis", "/imoveis/**", "/tipos-imoveis", "/tipos-imoveis/**",
                    "/bairros", "/bairros/**", "/fotos/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/me", "/ofertas", "/contratos", "/contratos/*", "/contratos/*/pdf", "/favoritos").authenticated()
                .requestMatchers(HttpMethod.POST, "/ofertas", "/contratos/*/assinar", "/favoritos/*").hasRole("USER")
                .requestMatchers(HttpMethod.DELETE, "/favoritos/*").hasRole("USER")
                .requestMatchers("/painel", "/ofertas/**", "/contratos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/imoveis/*/status").authenticated()
                .requestMatchers(HttpMethod.POST, "/imoveis/sabermais/*", "/contato/*").permitAll()
                .requestMatchers("/usuarios", "/usuarios/**", "/users", "/users/**", "/imoveis", "/imoveis/**",
                    "/tipos-imoveis", "/tipos-imoveis/**", "/bairros", "/bairros/**", "/fotos", "/fotos/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
            .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
            .build();
    }
}
