package com.example.demo.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import com.example.demo.models.UsuarioModel;
import com.example.demo.repositories.UsuarioRepository;
import com.example.demo.services.UsuarioService;

@Configuration
public class AdminInicial {
    @Bean CommandLineRunner criarAdmin(UsuarioRepository repository, UsuarioService service,
            @Value("${app.admin.email:}") String email, @Value("${app.admin.password:}") String senha) {
        return args -> {
            if (!email.isBlank() && !senha.isBlank() && repository.count() == 0)
                service.salvar(null, new UsuarioService.DadosUsuario("Administrador", email, senha, UsuarioModel.Perfil.ADMIN));
        };
    }
}
