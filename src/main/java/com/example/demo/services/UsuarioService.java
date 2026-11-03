package com.example.demo.services;

import java.util.Locale;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import com.example.demo.models.UsuarioModel;
import com.example.demo.repositories.UsuarioRepository;

@Service
public class UsuarioService {
    public record DadosUsuario(String nome, String email, String senha, UsuarioModel.Perfil perfil) {}
    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    public UsuarioService(UsuarioRepository repository, PasswordEncoder encoder) {
        this.repository = repository; this.encoder = encoder;
    }
    public static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
    public List<UsuarioModel> listar() { return repository.findAll(); }
    public UsuarioModel buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario inexistente."));
    }
    public UsuarioModel salvar(Long id, DadosUsuario dados) {
        var usuario = id == null ? new UsuarioModel() : buscar(id);
        String email = normalizarEmail(dados.email());
        if (dados.nome() == null || dados.nome().isBlank() || dados.nome().length() > 150
                || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || dados.perfil() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe nome, email e perfil ADMIN ou USER validos.");
        if (id == null || dados.senha() != null) {
            if (dados.senha() == null || dados.senha().length() < 8
                    || dados.senha().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve ter ao menos 8 caracteres e no maximo 72 bytes.");
            usuario.setSenha(encoder.encode(dados.senha()));
        }
        var existente = repository.findByEmail(email);
        if (existente.isPresent() && !existente.get().getId().equals(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado.");
        usuario.setNome(dados.nome().trim()); usuario.setEmail(email); usuario.setPerfil(dados.perfil());
        try { return repository.saveAndFlush(usuario); }
        catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado.");
        }
    }
    @Transactional public void excluir(Long id) { repository.delete(buscar(id)); }
}
