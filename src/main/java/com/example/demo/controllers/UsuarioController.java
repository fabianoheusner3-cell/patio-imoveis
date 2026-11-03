package com.example.demo.controllers;

import java.net.URI;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import com.example.demo.models.UsuarioModel;
import com.example.demo.services.UsuarioService;
import com.example.demo.services.UsuarioService.DadosUsuario;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {
    private final UsuarioService service;
    public UsuarioController(UsuarioService service) { this.service = service; }
    @GetMapping public List<UsuarioModel> listar() { return service.listar(); }
    @GetMapping("/{id}") public UsuarioModel buscar(@PathVariable Long id) { return service.buscar(id); }
    @PostMapping public ResponseEntity<UsuarioModel> cadastrar(@RequestBody DadosUsuario dados) {
        var usuario = service.salvar(null, dados);
        return ResponseEntity.created(URI.create("/usuarios/" + usuario.getId())).body(usuario);
    }
    @PutMapping("/{id}") public UsuarioModel alterar(@PathVariable Long id, @RequestBody DadosUsuario dados) {
        return service.salvar(id, dados);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> excluir(@PathVariable Long id, org.springframework.security.core.Authentication auth) {
        if (id.toString().equals(auth.getName())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Um administrador não pode excluir a si mesmo.");
        service.excluir(id); return ResponseEntity.noContent().build();
    }
}
