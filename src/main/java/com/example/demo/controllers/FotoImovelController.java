package com.example.demo.controllers;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.demo.models.FotoImovelModel;
import com.example.demo.services.FotoImovelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

@RestController
@RequestMapping("/fotos")
public class FotoImovelController {
    private final FotoImovelService service;

    public FotoImovelController(FotoImovelService service) {
        this.service = service;
    }

    @GetMapping("/imovel/{id}")
    public List<FotoImovelModel> listarPorImovel(@PathVariable Integer id) {
        return service.listarPorImovel(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FotoImovelModel> salvar(@RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("imovelId") Integer imovelId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.salvar(arquivo, imovelId));
    }

    public record EdicaoFoto(Integer ordem) {}

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public FotoImovelModel editar(@PathVariable Integer id, @RequestBody EdicaoFoto dados) {
        return service.editar(id, dados.ordem(), null);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FotoImovelModel editarArquivo(@PathVariable Integer id,
            @RequestParam("ordem") Integer ordem,
            @RequestParam(value = "arquivo", required = false) MultipartFile arquivo) {
        return service.editar(id, ordem, arquivo);
    }

    @PutMapping("/{id}/capa")
    public FotoImovelModel tornarCapa(@PathVariable Integer id) {
        return service.tornarCapa(id);
    }

    @PostMapping("/debug")
    public String debug(HttpServletRequest request) throws Exception {
        return request.getContentType() + " | partes: "
                + request.getParts().stream().map(Part::getName).toList();
    }

    @GetMapping("/{id}/arquivo")
    public ResponseEntity<org.springframework.core.io.Resource> arquivo(@PathVariable Integer id)
            throws java.io.IOException {
        var caminho = service.arquivo(id);
        String tipo = java.nio.file.Files.probeContentType(caminho);
        if (tipo == null) tipo = "application/octet-stream";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(tipo))
            .header("X-Content-Type-Options", "nosniff")
            .body(new org.springframework.core.io.FileSystemResource(caminho));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Integer id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
