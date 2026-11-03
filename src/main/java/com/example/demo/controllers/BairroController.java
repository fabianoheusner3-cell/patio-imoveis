package com.example.demo.controllers;

import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.example.demo.models.BairroModel;
import com.example.demo.repositories.BairroRepository;

@RestController
@RequestMapping("/bairros")
public class BairroController {
    private final BairroRepository repository;
    public BairroController(BairroRepository repository) { this.repository = repository; }
    @GetMapping
    public List<BairroModel> getAll() { return repository.findAll(); }
    @GetMapping("/{id}") public BairroModel buscar(@PathVariable Integer id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Bairro não encontrado."));
    }
    @PutMapping("/{id}") public BairroModel alterar(@PathVariable Integer id,@RequestBody BairroModel dados) {
        var bairro=buscar(id);
        if(dados.getNome()==null||dados.getNome().isBlank()||dados.getNome().length()>255)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe um nome de até 255 caracteres.");
        bairro.setNome(dados.getNome().trim());return repository.save(bairro);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void excluir(@PathVariable Integer id) {repository.delete(buscar(id));}
    @PostMapping
    public ResponseEntity<Void> insert(@RequestBody BairroModel model) {
        if (model.getNome() == null || model.getNome().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome do bairro.");
        model.setId(null);
        model = repository.save(model);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(model.getId()).toUri()).build();
    }
}
