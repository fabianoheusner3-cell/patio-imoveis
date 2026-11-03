package com.example.demo.services;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.example.demo.models.TipoImovelModel;
import com.example.demo.repositories.TipoImovelRepository;
@Service
public class TipoImovelService {
 private final TipoImovelRepository repository;
 public TipoImovelService(TipoImovelRepository repository){this.repository=repository;}
 public List<TipoImovelModel> getAll(){return repository.findAll();}
 public Page<TipoImovelModel> getAll(Pageable pageable){return repository.findAll(pageable);}
 public TipoImovelModel find(Integer id){return repository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tipo de imovel nao encontrado."));}
 private void validar(TipoImovelModel m){if(m.getNome()==null||m.getNome().isBlank()||m.getNome().length()>255)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe um nome de ate 255 caracteres.");m.setNome(m.getNome().trim());}
 public TipoImovelModel insert(TipoImovelModel m){validar(m);m.setId(null);return repository.save(m);}
 public TipoImovelModel update(TipoImovelModel m){find(m.getId());validar(m);return repository.save(m);}
 public void delete(Integer id){repository.delete(find(id));}
}
