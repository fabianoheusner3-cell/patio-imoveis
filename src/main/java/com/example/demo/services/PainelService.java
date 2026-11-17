package com.example.demo.services;

import com.example.demo.models.*;
import com.example.demo.repositories.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Numeros dos paineis de cada perfil (administrador, corretor e cliente). */
@Service
@Transactional(readOnly = true)
public class PainelService {
    /** Etapas do contrato na ordem do fluxo; CANCELADO fica fora do funil e e contado a parte. */
    public static final List<ContratoModel.Status> ETAPAS = List.of(ContratoModel.Status.EM_PREPARACAO,
        ContratoModel.Status.AGUARDANDO_APROVACAO, ContratoModel.Status.AGUARDANDO_ASSINATURA, ContratoModel.Status.ASSINADO);
    private static final List<OfertaModel.Status> SITUACOES_OFERTA = List.of(OfertaModel.Status.PENDENTE,
        OfertaModel.Status.EM_ESPERA, OfertaModel.Status.ACEITA, OfertaModel.Status.RECUSADA, OfertaModel.Status.CANCELADA);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final ImovelRepository imoveis;
    private final OfertaRepository ofertas;
    private final ContratoRepository contratos;
    private final FavoritoRepository favoritos;

    public PainelService(ImovelRepository imoveis, OfertaRepository ofertas, ContratoRepository contratos,
            FavoritoRepository favoritos) {
        this.imoveis = imoveis; this.ofertas = ofertas; this.contratos = contratos; this.favoritos = favoritos;
    }

    public Map<String, Object> admin() {
        var todosImoveis = imoveis.findAll();
        var todasOfertas = ofertas.findAll();
        var todosContratos = contratos.findAll();
        var dados = new LinkedHashMap<String, Object>();
        dados.put("disponiveis", todosImoveis.stream().filter(NegociacaoService::disponivel).count());
        dados.put("pendentes", contar(todasOfertas, OfertaModel::getStatus, OfertaModel.Status.PENDENTE));
        dados.put("emPreparacao", contar(todosContratos, ContratoModel::getStatus, ContratoModel.Status.EM_PREPARACAO));
        dados.put("aguardandoAprovacao", contar(todosContratos, ContratoModel::getStatus, ContratoModel.Status.AGUARDANDO_APROVACAO));
        dados.put("aguardando", contar(todosContratos, ContratoModel::getStatus, ContratoModel.Status.AGUARDANDO_ASSINATURA));
        dados.put("assinados", contar(todosContratos, ContratoModel::getStatus, ContratoModel.Status.ASSINADO));
        dados.put("valorNegociado", valorAssinado(todosContratos));
        dados.put("contratosPorEtapa", naOrdem(todosContratos, ContratoModel::getStatus, ETAPAS));
        dados.put("cancelados", contar(todosContratos, ContratoModel::getStatus, ContratoModel.Status.CANCELADO));
        dados.put("porSituacao", naOrdem(todasOfertas, OfertaModel::getStatus, SITUACOES_OFERTA));
        dados.put("porMes", porMes(todasOfertas, todosContratos));
        dados.put("porCorretor", ranking(todosContratos.stream().filter(c -> c.getStatus() != ContratoModel.Status.CANCELADO).toList(),
            c -> c.getCorretor() == null ? "Sem corretor" : c.getCorretor().getNome()));
        dados.put("porBairro", ranking(todosImoveis, i -> i.getBairro() == null ? "Sem bairro" : i.getBairro().getNome()));
        dados.put("porFinalidade", ranking(todosImoveis, i -> i.getFinalidade() == null ? "Não informada" : i.getFinalidade()));
        dados.put("paraAprovar", resumo(todosContratos.stream()
            .filter(c -> c.getStatus() == ContratoModel.Status.AGUARDANDO_APROVACAO)
            .sorted(Comparator.comparing(ContratoModel::getId)).limit(4).toList()));
        return dados;
    }

    public Map<String, Object> corretor(Long corretorId) {
        var fila = ofertas.findAll().stream().filter(o -> NegociacaoService.OFERTAS_ABERTAS.contains(o.getStatus()))
            .sorted(Comparator.comparing(OfertaModel::getDataCriacao)).toList();
        var meus = contratos.findByCorretorId(corretorId);
        var dados = new LinkedHashMap<String, Object>();
        dados.put("fila", fila.size());
        dados.put("emPreparacao", contar(meus, ContratoModel::getStatus, ContratoModel.Status.EM_PREPARACAO));
        dados.put("aguardandoAprovacao", contar(meus, ContratoModel::getStatus, ContratoModel.Status.AGUARDANDO_APROVACAO));
        dados.put("aguardandoAssinatura", contar(meus, ContratoModel::getStatus, ContratoModel.Status.AGUARDANDO_ASSINATURA));
        dados.put("assinados", contar(meus, ContratoModel::getStatus, ContratoModel.Status.ASSINADO));
        dados.put("valorNegociado", valorAssinado(meus));
        dados.put("contratosPorEtapa", naOrdem(meus, ContratoModel::getStatus, ETAPAS));
        dados.put("cancelados", contar(meus, ContratoModel::getStatus, ContratoModel.Status.CANCELADO));
        dados.put("filaPorBairro", ranking(fila, o -> o.getImovel().getBairro() == null ? "Sem bairro" : o.getImovel().getBairro().getNome()));
        var meses = ultimosMeses();
        dados.put("porMes", meses.stream().map(m -> mes(m, Map.of(
            "aceitas", meus.stream().filter(c -> noMes(c.getDataCriacao(), m)).count(),
            "assinados", meus.stream().filter(c -> assinadoNoMes(c, m)).count()))).toList());
        dados.put("proximas", fila.stream().limit(4).map(o -> {
            var item = new LinkedHashMap<String, Object>();
            item.put("id", o.getId());
            item.put("imovel", o.getImovel().getTitulo());
            item.put("cliente", o.getUsuario().getNome());
            item.put("tipo", o.getTipo().name());
            item.put("valor", o.getValorProposto());
            item.put("status", o.getStatus().name());
            item.put("data", o.getDataCriacao());
            return item;
        }).toList());
        dados.put("paraPreparar", resumo(meus.stream().filter(c -> c.getStatus() == ContratoModel.Status.EM_PREPARACAO)
            .sorted(Comparator.comparing(ContratoModel::getId)).limit(4).toList()));
        return dados;
    }

    public Map<String, Object> cliente(Long usuarioId) {
        var minhas = ofertas.findByUsuarioId(usuarioId);
        var meus = contratos.findByOfertaUsuarioId(usuarioId);
        var dados = new LinkedHashMap<String, Object>();
        dados.put("ofertasAbertas", minhas.stream().filter(o -> NegociacaoService.OFERTAS_ABERTAS.contains(o.getStatus())).count());
        dados.put("paraAssinar", contar(meus, ContratoModel::getStatus, ContratoModel.Status.AGUARDANDO_ASSINATURA));
        dados.put("assinados", contar(meus, ContratoModel::getStatus, ContratoModel.Status.ASSINADO));
        dados.put("favoritos", favoritos.countByUsuarioId(usuarioId));
        dados.put("porSituacao", naOrdem(minhas, OfertaModel::getStatus, SITUACOES_OFERTA));
        dados.put("emAndamento", resumo(meus.stream()
            .filter(c -> c.getStatus() != ContratoModel.Status.ASSINADO && c.getStatus() != ContratoModel.Status.CANCELADO)
            .sorted(Comparator.comparing(ContratoModel::getId)).toList()));
        return dados;
    }

    // ---------- auxiliares ----------
    private static <T, S> long contar(List<T> itens, Function<T, S> campo, S valor) {
        return itens.stream().filter(x -> valor.equals(campo.apply(x))).count();
    }
    /** Todas as categorias na ordem dada, inclusive as que tem zero. */
    private static <T, S extends Enum<S>> List<Map<String, Object>> naOrdem(List<T> itens, Function<T, S> campo, List<S> ordem) {
        return ordem.stream().map(s -> Map.<String, Object>of("nome", s.name(), "total", contar(itens, campo, s))).toList();
    }
    /** Categorias sem ordem natural (bairros, corretores): da maior para a menor. */
    private static <T> List<Map<String, Object>> ranking(List<T> itens, Function<T, String> chave) {
        return itens.stream().collect(Collectors.groupingBy(chave, Collectors.counting())).entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .map(e -> Map.<String, Object>of("nome", e.getKey(), "total", e.getValue())).toList();
    }
    private static BigDecimal valorAssinado(List<ContratoModel> lista) {
        return lista.stream().filter(c -> c.getStatus() == ContratoModel.Status.ASSINADO)
            .map(c -> c.getValorFinal() != null ? c.getValorFinal() : c.getOferta().getValorProposto())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static List<YearMonth> ultimosMeses() {
        var atual = YearMonth.now(NegociacaoService.FUSO);
        return IntStream.rangeClosed(0, 5).mapToObj(k -> atual.minusMonths(5 - k)).toList();
    }
    private static boolean noMes(Instant instante, YearMonth mes) {
        return instante != null && YearMonth.from(instante.atZone(NegociacaoService.FUSO)).equals(mes);
    }
    private static Map<String, Object> mes(YearMonth m, Map<String, Long> valores) {
        var item = new LinkedHashMap<String, Object>();
        item.put("mes", m.toString());
        item.put("nome", m.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "") + "/" + (m.getYear() % 100));
        item.putAll(valores);
        return item;
    }
    private static List<Map<String, Object>> porMes(List<OfertaModel> todasOfertas, List<ContratoModel> todosContratos) {
        return ultimosMeses().stream().map(m -> mes(m, Map.of(
            "ofertas", todasOfertas.stream().filter(o -> noMes(o.getDataCriacao(), m)).count(),
            "assinados", todosContratos.stream().filter(c -> assinadoNoMes(c, m)).count()))).toList();
    }
    /** Conta so os contratos que continuam assinados (se um deles for cancelado depois, sai da conta). */
    private static boolean assinadoNoMes(ContratoModel c, YearMonth m) {
        return c.getStatus() == ContratoModel.Status.ASSINADO && noMes(c.getDataAssinatura(), m);
    }
    private static List<Map<String, Object>> resumo(List<ContratoModel> lista) {
        return lista.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("imovel", c.getOferta().getImovel().getTitulo());
            item.put("cliente", c.getOferta().getUsuario().getNome());
            item.put("corretor", c.getCorretor() == null ? null : c.getCorretor().getNome());
            item.put("tipo", c.getOferta().getTipo().name());
            item.put("valor", c.getValorFinal() != null ? c.getValorFinal() : c.getOferta().getValorProposto());
            item.put("status", c.getStatus().name());
            return item;
        }).toList();
    }
}
