package com.example.demo.controllers;
import com.example.demo.services.NegociacaoService;
import com.example.demo.services.PainelService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Um painel por perfil; o SecurityConfig libera cada rota so para o perfil dela. */
@RestController
public class PainelController {
    private final PainelService service;
    public PainelController(PainelService service) { this.service = service; }
    @GetMapping("/painel") public Map<String, Object> painel() { return service.admin(); }
    @GetMapping("/painel/corretor") public Map<String, Object> corretor(Authentication a) {
        return service.corretor(NegociacaoService.uid(a));
    }
    @GetMapping("/painel/cliente") public Map<String, Object> cliente(Authentication a) {
        return service.cliente(NegociacaoService.uid(a));
    }
}
