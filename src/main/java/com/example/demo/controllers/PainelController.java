package com.example.demo.controllers;
import com.example.demo.repositories.*;
import com.example.demo.models.*;
import com.example.demo.services.NegociacaoService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
@RestController
public class PainelController {
    private final ImovelRepository imoveis;private final OfertaRepository ofertas;private final ContratoRepository contratos;
    public PainelController(ImovelRepository i,OfertaRepository o,ContratoRepository c){imoveis=i;ofertas=o;contratos=c;}
    private <T> List<Map<String,Object>> grupos(List<T> itens,Function<T,String> key){return itens.stream().collect(Collectors.groupingBy(key,TreeMap::new,Collectors.counting())).entrySet().stream().map(e->Map.<String,Object>of("nome",e.getKey(),"total",e.getValue())).toList();}
    @GetMapping("/painel") public Map<String,Object> painel(){var i=imoveis.findAll();var o=ofertas.findAll();var c=contratos.findAll();return Map.of(
        "disponiveis",i.stream().filter(NegociacaoService::disponivel).count(),"pendentes",o.stream().filter(x->x.getStatus()==OfertaModel.Status.PENDENTE).count(),
        "aguardando",c.stream().filter(x->x.getStatus()==ContratoModel.Status.AGUARDANDO_ASSINATURA).count(),
        "porBairro",grupos(i,x->x.getBairro()==null?"Sem bairro":x.getBairro().getNome()),"porSituacao",grupos(o,x->x.getStatus().name()),
        "porFinalidade",grupos(i,x->x.getFinalidade()==null?"Não informada":x.getFinalidade()));}
}
