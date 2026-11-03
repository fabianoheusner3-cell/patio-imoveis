package com.example.demo.services;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.example.demo.models.ImovelModel;
import com.example.demo.repositories.*;

@Service
@Transactional
public class ImovelService {
    @org.springframework.beans.factory.annotation.Autowired private ContratoRepository contratos;
    @org.springframework.beans.factory.annotation.Autowired private FavoritoRepository favoritos;
    @org.springframework.beans.factory.annotation.Autowired private OfertaRepository ofertas;
    private final ImovelRepository repository;
    private final TipoImovelRepository tipos;
    private final BairroRepository bairros;
    private final FotoImovelService fotos;

    public ImovelService(ImovelRepository repository, TipoImovelRepository tipos, BairroRepository bairros, FotoImovelService fotos) {
        this.repository = repository;
        this.tipos = tipos;
        this.bairros = bairros;
        this.fotos = fotos;
    }
    public List<ImovelModel> getAll() { return repository.findAll(); }
    public Page<ImovelModel> getAll(Pageable pageable) { return repository.findAll(pageable); }
    public ImovelModel find(Integer id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Imóvel não encontrado."));
    }
    private void validate(ImovelModel model) {
        if (model.getTitulo() == null || model.getTitulo().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o título.");
        if (model.getTipoImovel() == null || model.getTipoImovel().getId() == null || model.getBairro() == null || model.getBairro().getId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe tipo de imóvel e bairro.");
        model.setTipoImovel(tipos.findById(model.getTipoImovel().getId()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de imóvel inexistente.")));
        model.setBairro(bairros.findById(model.getBairro().getId()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bairro inexistente.")));
        for (var value : new java.math.BigDecimal[] { model.getPrecoVenda(), model.getPrecoAluguel(), model.getAreaTotal(), model.getAreaConstruida() })
            if (value != null && value.signum() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valores e áreas não podem ser negativos.");
        for (var value : new Integer[] { model.getDormitorios(), model.getBanheiros(), model.getGaragem() })
            if (value != null && value < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidades não podem ser negativas.");
    }
    public ImovelModel insert(ImovelModel model) {
        model.setId(null);
        validate(model);
        return repository.save(model);
    }
    public ImovelModel update(ImovelModel model) {
        var atual=repository.bloquearPorId(model.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Imóvel não encontrado."));
        if(contratos.existsByOfertaImovelIdAndStatusNot(model.getId(),com.example.demo.models.ContratoModel.Status.CANCELADO)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Este imóvel tem um contrato ativo. Cancele o contrato antes de editar o imóvel.");
        validate(model);
        return repository.save(model);
    }
    public void alterarStatus(Integer id, String status) {
        if (status == null || status.isBlank() || status.length() > 255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um status de ate 255 caracteres.");
        ImovelModel imovel = repository.bloquearPorId(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Imóvel não encontrado."));
        if(contratos.existsByOfertaImovelIdAndStatusNot(id,com.example.demo.models.ContratoModel.Status.CANCELADO)) throw new ResponseStatusException(HttpStatus.CONFLICT,"O status deste imóvel é controlado pelo contrato ativo.");
        imovel.setStatus(status.trim());
        repository.save(imovel);
    }
    public void delete(Integer id) {
        ImovelModel imovel = find(id);
        if(ofertas.existsByImovelId(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Este imóvel tem histórico de ofertas e contratos. Mantenha o cadastro para preservar o histórico.");
        favoritos.deleteByImovelId(id);
        for (var foto : fotos.listarPorImovel(id)) fotos.deletar(foto.getId());
        repository.delete(imovel);
    }
}
