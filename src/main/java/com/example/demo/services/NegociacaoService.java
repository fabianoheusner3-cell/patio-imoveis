package com.example.demo.services;
import com.example.demo.models.*;
import com.example.demo.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service @Transactional
public class NegociacaoService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    private final ImovelRepository imoveis;
    private final UsuarioRepository usuarios;
    private final OfertaRepository ofertas;
    private final ContratoRepository contratos;
    private final FavoritoRepository favoritos;
    public NegociacaoService(ImovelRepository i,UsuarioRepository u,OfertaRepository o,ContratoRepository c,FavoritoRepository f) {
        imoveis=i;usuarios=u;ofertas=o;contratos=c;favoritos=f;
    }
    public static boolean admin(Authentication a) {return a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_ADMIN"));}
    public static Long uid(Authentication a) {return Long.valueOf(a.getName());}
    public static String normalizar(String valor) {return java.text.Normalizer.normalize(valor==null?"":valor,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toUpperCase(Locale.ROOT);}
    public static boolean disponivel(ImovelModel i) {return normalizar(i.getStatus()).equals("DISPONIVEL");}
    private ResponseStatusException conflito(String s) {return new ResponseStatusException(HttpStatus.CONFLICT,s);}
    private ImovelModel lock(Integer id) {var i=imoveis.bloquearPorId(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Imóvel não encontrado."));em.refresh(i);return i;}
    public record NovaOferta(Integer imovelId,OfertaModel.Tipo tipo,BigDecimal valorProposto,String mensagem) {}
    public OfertaModel criar(NovaOferta d,Authentication a) {
        if(d.imovelId()==null||d.tipo()==null||d.valorProposto()==null||d.valorProposto().signum()<=0||d.valorProposto().scale()>2||d.valorProposto().precision()>17||d.mensagem()==null||d.mensagem().isBlank()||d.mensagem().length()>4000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe imóvel, tipo, valor positivo com até duas casas e mensagem de até 4000 caracteres.");
        var i=lock(d.imovelId());
        if(!disponivel(i))throw conflito("Somente imóveis disponíveis recebem ofertas.");
        BigDecimal preco=d.tipo()==OfertaModel.Tipo.ALUGUEL?i.getPrecoAluguel():i.getPrecoVenda();
        if(preco==null||preco.signum()<=0)throw conflito("O imóvel não está disponível para este tipo de oferta.");
        if(ofertas.existsByUsuarioIdAndImovelIdAndStatus(uid(a),i.getId(),OfertaModel.Status.PENDENTE))throw conflito("Você já tem uma oferta pendente para este imóvel.");
        var o=new OfertaModel();o.setImovel(i);o.setUsuario(usuarios.findById(uid(a)).orElseThrow());o.setTipo(d.tipo());o.setValorProposto(d.valorProposto());o.setMensagem(d.mensagem().trim());return ofertas.save(o);
    }
    public List<OfertaModel> listarOfertas(Authentication a,String status) {
        return (admin(a)?ofertas.findAll():ofertas.findByUsuarioIdOrderByDataCriacaoDesc(uid(a))).stream()
            .filter(o->status==null||status.isBlank()||o.getStatus().name().equals(status)).sorted(Comparator.comparing(OfertaModel::getDataCriacao).reversed()).toList();
    }
    public OfertaModel decidir(Long id,String acao) {
        var o=ofertas.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Oferta não encontrada."));
        var i=lock(o.getImovel().getId());
        em.refresh(o);
        if(o.getStatus()!=OfertaModel.Status.PENDENTE&&o.getStatus()!=OfertaModel.Status.EM_ESPERA)throw conflito("Esta oferta já foi decidida.");
        if("recusar".equals(acao)){o.setStatus(OfertaModel.Status.RECUSADA);return o;}
        if(!"aceitar".equals(acao))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ação inválida.");
        if(!disponivel(i)||contratos.existsByOfertaImovelIdAndStatusNot(i.getId(),ContratoModel.Status.CANCELADO))throw conflito("O imóvel já tem uma negociação ativa.");
        o.setStatus(OfertaModel.Status.ACEITA);i.setStatus("Reservado");
        for(var outra:ofertas.findByImovelId(i.getId()))if(!outra.getId().equals(id)&&outra.getStatus()==OfertaModel.Status.PENDENTE)outra.setStatus(OfertaModel.Status.EM_ESPERA);
        var c=new ContratoModel();c.setOferta(o);c=contratos.save(c);c.setPdf("/contratos/"+c.getId()+"/pdf");return o;
    }
    public ContratoModel contrato(Long id,Authentication a) {
        var c=contratos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Contrato não encontrado."));
        if(!admin(a)&&!c.getOferta().getUsuario().getId().equals(uid(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Este contrato pertence a outro cliente.");return c;
    }
    public List<ContratoModel> listarContratos(Authentication a) {return (admin(a)?contratos.findAll():contratos.findByOfertaUsuarioId(uid(a))).stream().sorted(Comparator.comparing(ContratoModel::getId).reversed()).toList();}
    public ContratoModel aprovar(Long id,Authentication a) {
        var c=contrato(id,a);lock(c.getOferta().getImovel().getId());em.refresh(c);
        if(c.getStatus()!=ContratoModel.Status.AGUARDANDO_ASSINATURA)throw conflito("Somente contratos aguardando assinatura podem ser aprovados.");
        c.setDataAprovacao(Instant.now());return c;
    }
    public ContratoModel assinar(Long id,String nome,Authentication a) {
        var c=contrato(id,a);var i=lock(c.getOferta().getImovel().getId());em.refresh(c);
        if(!c.getOferta().getUsuario().getId().equals(uid(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Somente o titular pode assinar.");
        if(c.getStatus()!=ContratoModel.Status.AGUARDANDO_ASSINATURA||c.getDataAprovacao()==null||c.getDataEnvio()==null)throw conflito("O contrato precisa estar aprovado e enviado para assinatura.");
        if(nome==null||!nome.trim().equalsIgnoreCase(c.getOferta().getUsuario().getNome().trim()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Digite seu nome completo conforme o cadastro para confirmar a assinatura.");
        c.setAssinante(nome.trim());c.setDataAssinatura(Instant.now());c.setStatus(ContratoModel.Status.ASSINADO);
        i.setStatus(c.getOferta().getTipo()==OfertaModel.Tipo.ALUGUEL?"Alugado":"Vendido");
        for(var outra:ofertas.findByImovelId(i.getId()))if(!outra.getId().equals(c.getOferta().getId())&&(outra.getStatus()==OfertaModel.Status.PENDENTE||outra.getStatus()==OfertaModel.Status.EM_ESPERA))outra.setStatus(OfertaModel.Status.RECUSADA);
        return c;
    }
    public ContratoModel cancelar(Long id,Authentication a) {
        var c=contrato(id,a);var i=lock(c.getOferta().getImovel().getId());em.refresh(c);
        if(c.getStatus()==ContratoModel.Status.CANCELADO)throw conflito("O contrato já está cancelado.");
        c.setStatus(ContratoModel.Status.CANCELADO);c.getOferta().setStatus(OfertaModel.Status.CANCELADA);i.setStatus("Disponível");return c;
    }
    public void marcarEnviado(Long id,Authentication a) {contrato(id,a).setDataEnvio(Instant.now());}
    public List<FavoritoModel> favoritos(Authentication a) {return favoritos.findByUsuarioId(uid(a));}
    public FavoritoModel favoritar(Integer id,Authentication a) {
        var i=lock(id);return favoritos.findByUsuarioIdAndImovelId(uid(a),id).orElseGet(()->{var f=new FavoritoModel();f.setImovel(i);f.setUsuario(usuarios.findById(uid(a)).orElseThrow());return favoritos.save(f);});
    }
    public void desfavoritar(Integer id,Authentication a) {favoritos.findByUsuarioIdAndImovelId(uid(a),id).ifPresent(favoritos::delete);}
}
