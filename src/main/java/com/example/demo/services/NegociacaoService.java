package com.example.demo.services;

import com.example.demo.models.*;
import com.example.demo.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regras da negociacao. Fluxo da Aula 3:
 * cliente envia a oferta -> corretor aceita e prepara o contrato -> administrador aprova -> cliente assina.
 * Toda mudanca de etapa bloqueia o imovel (lock pessimista): duas decisoes sobre o mesmo imovel
 * nunca acontecem ao mesmo tempo.
 */
@Service @Transactional
public class NegociacaoService {
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    public static final List<OfertaModel.Status> OFERTAS_ABERTAS = List.of(OfertaModel.Status.PENDENTE, OfertaModel.Status.EM_ESPERA);
    private static final int MAX_POR_PAGINA = 50;

    @PersistenceContext private EntityManager em;
    private final ImovelRepository imoveis;
    private final UsuarioRepository usuarios;
    private final OfertaRepository ofertas;
    private final ContratoRepository contratos;
    private final FavoritoRepository favoritos;

    public NegociacaoService(ImovelRepository imoveis, UsuarioRepository usuarios, OfertaRepository ofertas,
            ContratoRepository contratos, FavoritoRepository favoritos) {
        this.imoveis = imoveis; this.usuarios = usuarios; this.ofertas = ofertas;
        this.contratos = contratos; this.favoritos = favoritos;
    }

    // ---------- Perfis (vem da claim "role" do JWT) ----------
    public static boolean admin(Authentication a) { return temPerfil(a, "ROLE_ADMIN"); }
    public static boolean corretor(Authentication a) { return temPerfil(a, "ROLE_CORRETOR"); }
    public static boolean cliente(Authentication a) { return temPerfil(a, "ROLE_USER"); }
    private static boolean temPerfil(Authentication a, String perfil) {
        return a.getAuthorities().stream().anyMatch(r -> r.getAuthority().equals(perfil));
    }
    public static Long uid(Authentication a) { return Long.valueOf(a.getName()); }

    public static String normalizar(String valor) {
        return Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }
    public static boolean disponivel(ImovelModel i) { return normalizar(i.getStatus()).equals("DISPONIVEL"); }

    private static ResponseStatusException conflito(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
    private static ResponseStatusException invalido(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException proibido(String m) { return new ResponseStatusException(HttpStatus.FORBIDDEN, m); }

    private ImovelModel bloquear(Integer id) {
        var i = imoveis.bloquearPorId(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Imóvel não encontrado."));
        em.refresh(i);
        return i;
    }
    private UsuarioModel usuario(Authentication a) { return usuarios.findById(uid(a)).orElseThrow(); }
    private static boolean valorValido(BigDecimal v) {
        return v != null && v.signum() > 0 && v.scale() <= 2 && v.precision() <= 17;
    }

    /** Filtro de situacao: vazio = todas; aceita varias separadas por virgula (PENDENTE,EM_ESPERA). */
    private static <E extends Enum<E>> List<E> situacoes(Class<E> tipo, String filtro) {
        if (filtro == null || filtro.isBlank()) return List.of(tipo.getEnumConstants());
        var lista = new ArrayList<E>();
        for (var nome : filtro.split(",")) {
            try { lista.add(Enum.valueOf(tipo, nome.trim().toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ex) {
                throw invalido("Situação inválida: " + nome.trim().substring(0, Math.min(nome.trim().length(), 30)) + ".");
            }
        }
        return lista;
    }
    private static String texto(String busca) {
        if (busca == null) return "";
        var t = busca.trim();
        return t.length() > 100 ? t.substring(0, 100) : t;
    }
    /** Mantem pagina e tamanho do Pageable, com limite de tamanho e a ordem definida aqui. */
    private static PageRequest pagina(Pageable p, Sort ordem) {
        int tamanho = Math.min(Math.max(p.getPageSize(), 1), MAX_POR_PAGINA);
        return PageRequest.of(p.getPageNumber(), tamanho, ordem);
    }

    // ---------- Ofertas ----------
    public record NovaOferta(Integer imovelId, OfertaModel.Tipo tipo, BigDecimal valorProposto, String mensagem) {}
    public record Resposta(String motivo) {}

    public OfertaModel criar(NovaOferta d, Authentication a) {
        if (d.imovelId() == null || d.tipo() == null || !valorValido(d.valorProposto())
                || d.mensagem() == null || d.mensagem().isBlank() || d.mensagem().length() > 4000)
            throw invalido("Informe imóvel, tipo, valor positivo com até duas casas e mensagem de até 4000 caracteres.");
        var i = bloquear(d.imovelId());
        if (!disponivel(i)) throw conflito("Somente imóveis disponíveis recebem ofertas.");
        BigDecimal preco = d.tipo() == OfertaModel.Tipo.ALUGUEL ? i.getPrecoAluguel() : i.getPrecoVenda();
        if (preco == null || preco.signum() <= 0) throw conflito("O imóvel não está disponível para este tipo de oferta.");
        if (ofertas.existsByUsuarioIdAndImovelIdAndStatusIn(uid(a), i.getId(), OFERTAS_ABERTAS))
            throw conflito("Você já tem uma oferta em aberto para este imóvel.");
        var o = new OfertaModel();
        o.setImovel(i); o.setUsuario(usuario(a)); o.setTipo(d.tipo());
        o.setValorProposto(d.valorProposto()); o.setMensagem(d.mensagem().trim());
        return ofertas.save(o);
    }

    /** Cliente ve as proprias ofertas; corretor e administrador veem todas. */
    public List<OfertaModel> listarOfertas(Authentication a, String status) {
        var filtro = situacoes(OfertaModel.Status.class, status);
        var lista = cliente(a) ? ofertas.findByUsuarioIdOrderByDataCriacaoDesc(uid(a)) : ofertas.findAll();
        return lista.stream().filter(o -> filtro.contains(o.getStatus()))
            .sorted(Comparator.comparing(OfertaModel::getDataCriacao).reversed()).toList();
    }

    public Page<OfertaModel> paginaOfertas(Authentication a, String status, String busca, Pageable pageable) {
        var ordem = Sort.by(Sort.Direction.DESC, "dataCriacao").and(Sort.by(Sort.Direction.DESC, "id"));
        return ofertas.buscar(cliente(a) ? uid(a) : null, situacoes(OfertaModel.Status.class, status),
            texto(busca), pagina(pageable, ordem));
    }

    private OfertaModel buscarOferta(Long id) {
        return ofertas.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta não encontrada."));
    }

    /** Corretor aceita: reserva o imovel, coloca as outras ofertas em espera e abre o contrato em preparacao. */
    public OfertaModel aceitar(Long id, Authentication a) {
        var o = buscarOferta(id);
        var i = bloquear(o.getImovel().getId());
        em.refresh(o);
        if (!OFERTAS_ABERTAS.contains(o.getStatus())) throw conflito("Esta oferta já foi decidida.");
        if (!disponivel(i) || contratos.existsByOfertaImovelIdAndStatusNot(i.getId(), ContratoModel.Status.CANCELADO))
            throw conflito("O imóvel já tem uma negociação ativa.");
        var agora = Instant.now();
        o.setStatus(OfertaModel.Status.ACEITA);
        o.setDataResposta(agora);
        i.setStatus("Reservado");
        for (var outra : ofertas.findByImovelId(i.getId()))
            if (!outra.getId().equals(id) && outra.getStatus() == OfertaModel.Status.PENDENTE)
                outra.setStatus(OfertaModel.Status.EM_ESPERA);
        var c = new ContratoModel();
        c.setOferta(o);
        c.setCorretor(usuario(a));
        c.setValorFinal(o.getValorProposto());
        c.setDataCriacao(agora);
        c = contratos.save(c);
        c.setPdf("/contratos/" + c.getId() + "/pdf");
        return o;
    }

    /** Corretor recusa, com uma mensagem opcional que o cliente ve. */
    public OfertaModel recusar(Long id, String motivo, Authentication a) {
        if (motivo != null && motivo.length() > 1000)
            throw invalido("A mensagem para o cliente pode ter até 1000 caracteres.");
        var o = buscarOferta(id);
        bloquear(o.getImovel().getId());
        em.refresh(o);
        if (!OFERTAS_ABERTAS.contains(o.getStatus())) throw conflito("Esta oferta já foi decidida.");
        o.setStatus(OfertaModel.Status.RECUSADA);
        o.setDataResposta(Instant.now());
        o.setResposta(motivo == null || motivo.isBlank() ? null : motivo.trim());
        return o;
    }

    /** O cliente desiste da propria oferta enquanto ela ainda esta em aberto. */
    public OfertaModel cancelarOferta(Long id, Authentication a) {
        var o = buscarOferta(id);
        if (!o.getUsuario().getId().equals(uid(a))) throw proibido("Esta oferta pertence a outro cliente.");
        bloquear(o.getImovel().getId());
        em.refresh(o);
        if (!OFERTAS_ABERTAS.contains(o.getStatus()))
            throw conflito("Só é possível cancelar ofertas pendentes ou em espera. Se ela já foi aceita, fale com o corretor.");
        o.setStatus(OfertaModel.Status.CANCELADA);
        o.setDataResposta(Instant.now());
        return o;
    }

    // ---------- Contratos ----------
    /** So o titular (cliente), o corretor responsavel ou o administrador acessam o contrato. */
    public ContratoModel contrato(Long id, Authentication a) {
        var c = contratos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado."));
        if (admin(a)) return c;
        if (corretor(a)) {
            if (c.getCorretor() != null && c.getCorretor().getId().equals(uid(a))) return c;
            throw proibido("Este contrato está com outro corretor.");
        }
        if (c.getOferta().getUsuario().getId().equals(uid(a))) return c;
        throw proibido("Este contrato pertence a outro cliente.");
    }

    public List<ContratoModel> listarContratos(Authentication a) {
        var lista = admin(a) ? contratos.findAll()
            : corretor(a) ? contratos.findByCorretorId(uid(a))
            : contratos.findByOfertaUsuarioId(uid(a));
        return lista.stream().sorted(Comparator.comparing(ContratoModel::getId).reversed()).toList();
    }

    public Page<ContratoModel> paginaContratos(Authentication a, String status, String busca, Pageable pageable) {
        return contratos.buscar(cliente(a) ? uid(a) : null, corretor(a) ? uid(a) : null,
            situacoes(ContratoModel.Status.class, status), texto(busca),
            pagina(pageable, Sort.by(Sort.Direction.DESC, "id")));
    }

    /** Termos que o corretor preenche antes de enviar o contrato para aprovacao. */
    public record Preparo(BigDecimal valorFinal, LocalDate dataInicio, Integer prazoMeses, String condicoes) {}

    public ContratoModel preparar(Long id, Preparo d, Authentication a) {
        var c = contrato(id, a);
        bloquear(c.getOferta().getImovel().getId());
        em.refresh(c);
        if (c.getStatus() != ContratoModel.Status.EM_PREPARACAO)
            throw conflito("Somente contratos em preparação podem ser enviados para aprovação.");
        boolean aluguel = c.getOferta().getTipo() == OfertaModel.Tipo.ALUGUEL;
        var hoje = LocalDate.now(FUSO);
        if (d == null || !valorValido(d.valorFinal()))
            throw invalido("Informe o valor final, positivo e com até duas casas decimais.");
        if (d.dataInicio() == null || d.dataInicio().isBefore(hoje) || d.dataInicio().isAfter(hoje.plusYears(2)))
            throw invalido("Informe a data de início entre hoje e os próximos dois anos.");
        if (aluguel && (d.prazoMeses() == null || d.prazoMeses() < 1 || d.prazoMeses() > 120))
            throw invalido("Informe o prazo do aluguel entre 1 e 120 meses.");
        if (d.condicoes() == null || d.condicoes().isBlank() || d.condicoes().length() > 4000)
            throw invalido("Descreva as condições do contrato em até 4000 caracteres.");
        c.setValorFinal(d.valorFinal());
        c.setDataInicio(d.dataInicio());
        c.setPrazoMeses(aluguel ? d.prazoMeses() : null);
        c.setCondicoes(d.condicoes().trim());
        c.setStatus(ContratoModel.Status.AGUARDANDO_APROVACAO);
        c.setDataPreparacao(Instant.now());
        return c;
    }

    /** Administrador aprova o que o corretor preparou: o contrato fica liberado para o cliente assinar. */
    public ContratoModel aprovar(Long id, Authentication a) {
        var c = contrato(id, a);
        bloquear(c.getOferta().getImovel().getId());
        em.refresh(c);
        if (c.getStatus() != ContratoModel.Status.AGUARDANDO_APROVACAO)
            throw conflito("Somente contratos aguardando aprovação podem ser aprovados.");
        c.setStatus(ContratoModel.Status.AGUARDANDO_ASSINATURA);
        c.setDataAprovacao(Instant.now());
        return c;
    }

    /** Administrador devolve ao corretor, explicando o que ajustar. */
    public ContratoModel devolver(Long id, String motivo, Authentication a) {
        if (motivo == null || motivo.isBlank() || motivo.length() > 1000)
            throw invalido("Explique ao corretor o que precisa ser ajustado, em até 1000 caracteres.");
        var c = contrato(id, a);
        bloquear(c.getOferta().getImovel().getId());
        em.refresh(c);
        if (c.getStatus() != ContratoModel.Status.AGUARDANDO_APROVACAO)
            throw conflito("Somente contratos aguardando aprovação podem ser devolvidos ao corretor.");
        c.setStatus(ContratoModel.Status.EM_PREPARACAO);
        c.setMotivoDevolucao(motivo.trim());
        c.setDataDevolucao(Instant.now());
        return c;
    }

    public ContratoModel assinar(Long id, String nome, Authentication a) {
        var c = contrato(id, a);
        var i = bloquear(c.getOferta().getImovel().getId());
        em.refresh(c);
        if (!c.getOferta().getUsuario().getId().equals(uid(a))) throw proibido("Somente o titular pode assinar.");
        if (c.getStatus() != ContratoModel.Status.AGUARDANDO_ASSINATURA || c.getDataAprovacao() == null)
            throw conflito("O contrato precisa ser aprovado pela imobiliária antes da assinatura.");
        if (nome == null || !nome.trim().equalsIgnoreCase(c.getOferta().getUsuario().getNome().trim()))
            throw invalido("Digite seu nome completo conforme o cadastro para confirmar a assinatura.");
        var agora = Instant.now();
        c.setAssinante(nome.trim());
        c.setDataAssinatura(agora);
        c.setStatus(ContratoModel.Status.ASSINADO);
        i.setStatus(c.getOferta().getTipo() == OfertaModel.Tipo.ALUGUEL ? "Alugado" : "Vendido");
        for (var outra : ofertas.findByImovelId(i.getId()))
            if (!outra.getId().equals(c.getOferta().getId()) && OFERTAS_ABERTAS.contains(outra.getStatus())) {
                outra.setStatus(OfertaModel.Status.RECUSADA);
                outra.setDataResposta(agora);
                outra.setResposta("O imóvel foi negociado com outra proposta.");
            }
        return c;
    }

    /** Cancelar encerra a negociacao: a oferta e cancelada e o imovel volta a ficar disponivel. */
    public ContratoModel cancelar(Long id, Authentication a) {
        var c = contrato(id, a);
        var i = bloquear(c.getOferta().getImovel().getId());
        em.refresh(c);
        if (c.getStatus() == ContratoModel.Status.CANCELADO) throw conflito("O contrato já está cancelado.");
        c.setStatus(ContratoModel.Status.CANCELADO);
        c.setDataCancelamento(Instant.now());
        c.getOferta().setStatus(OfertaModel.Status.CANCELADA);
        i.setStatus("Disponível");
        return c;
    }

    public ContratoModel marcarEnviado(Long id, Authentication a) {
        var c = contrato(id, a);
        c.setDataEnvio(Instant.now());
        return c;
    }

    // ---------- Favoritos ----------
    public List<FavoritoModel> favoritos(Authentication a) { return favoritos.findByUsuarioId(uid(a)); }
    public FavoritoModel favoritar(Integer id, Authentication a) {
        var i = bloquear(id);
        return favoritos.findByUsuarioIdAndImovelId(uid(a), id).orElseGet(() -> {
            var f = new FavoritoModel();
            f.setImovel(i);
            f.setUsuario(usuario(a));
            return favoritos.save(f);
        });
    }
    public void desfavoritar(Integer id, Authentication a) {
        favoritos.findByUsuarioIdAndImovelId(uid(a), id).ifPresent(favoritos::delete);
    }
}
