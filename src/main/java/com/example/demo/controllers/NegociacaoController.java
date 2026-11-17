package com.example.demo.controllers;

import com.example.demo.models.*;
import com.example.demo.services.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Ofertas, contratos e favoritos. As permissoes de cada perfil estao no SecurityConfig. */
@RestController
public class NegociacaoController {
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(NegociacaoService.FUSO);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final NegociacaoService service;
    private final EmailService email;
    private final String frontend;

    public NegociacaoController(NegociacaoService service, EmailService email,
            @Value("${app.frontend-url:http://localhost:5173}") String frontend) {
        this.service = service; this.email = email; this.frontend = frontend;
    }

    // ---------- Ofertas ----------
    @GetMapping("/ofertas") public List<OfertaModel> ofertas(Authentication a, @RequestParam(required = false) String status) {
        return service.listarOfertas(a, status);
    }
    // Paginada como /imoveis/imoveis-page: ?status=PENDENTE,EM_ESPERA&busca=casa&page=0&size=5
    @GetMapping("/ofertas/ofertas-page") public Page<OfertaModel> paginaOfertas(Authentication a,
            @RequestParam(required = false) String status, @RequestParam(required = false) String busca, Pageable pageable) {
        return service.paginaOfertas(a, status, busca, pageable);
    }
    @PostMapping("/ofertas") @ResponseStatus(HttpStatus.CREATED)
    public OfertaModel criar(@RequestBody NegociacaoService.NovaOferta dados, Authentication a) { return service.criar(dados, a); }
    @PostMapping("/ofertas/{id}/aceitar") public OfertaModel aceitar(@PathVariable Long id, Authentication a) {
        return service.aceitar(id, a);
    }
    @PostMapping("/ofertas/{id}/recusar") public OfertaModel recusar(@PathVariable Long id,
            @RequestBody(required = false) NegociacaoService.Resposta dados, Authentication a) {
        return service.recusar(id, dados == null ? null : dados.motivo(), a);
    }
    @PostMapping("/ofertas/{id}/cancelar") public OfertaModel cancelarOferta(@PathVariable Long id, Authentication a) {
        return service.cancelarOferta(id, a);
    }

    // ---------- Contratos ----------
    @GetMapping("/contratos") public List<ContratoModel> contratos(Authentication a) { return service.listarContratos(a); }
    @GetMapping("/contratos/contratos-page") public Page<ContratoModel> paginaContratos(Authentication a,
            @RequestParam(required = false) String status, @RequestParam(required = false) String busca, Pageable pageable) {
        return service.paginaContratos(a, status, busca, pageable);
    }
    @GetMapping("/contratos/{id}") public ContratoModel contrato(@PathVariable Long id, Authentication a) { return service.contrato(id, a); }

    @PostMapping("/contratos/{id}/preparar") public ContratoModel preparar(@PathVariable Long id,
            @RequestBody NegociacaoService.Preparo dados, Authentication a) {
        return service.preparar(id, dados, a);
    }

    /** Aprovar libera a assinatura. O email ao cliente e um aviso: se falhar, ele assina pela area de contratos. */
    @PostMapping("/contratos/{id}/aprovar") public ContratoModel aprovar(@PathVariable Long id, Authentication a) {
        var c = service.aprovar(id, a);
        return notificarCliente(c) ? service.marcarEnviado(id, a) : c;
    }
    @PostMapping("/contratos/{id}/devolver") public ContratoModel devolver(@PathVariable Long id,
            @RequestBody(required = false) NegociacaoService.Resposta dados, Authentication a) {
        return service.devolver(id, dados == null ? null : dados.motivo(), a);
    }
    /** Reenvia o aviso por email de um contrato ja aprovado. */
    @PostMapping("/contratos/{id}/enviar") public Map<String, String> enviar(@PathVariable Long id, Authentication a) {
        var c = service.contrato(id, a);
        if (c.getStatus() != ContratoModel.Status.AGUARDANDO_ASSINATURA)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aprove o contrato antes de avisar o cliente.");
        if (!notificarCliente(c))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível enviar o email. Verifique a configuração "
                + "de email e tente novamente. O contrato continua aprovado e o cliente já pode assinar pela área de contratos.");
        service.marcarEnviado(id, a);
        return Map.of("mensagem", "Email enviado: o cliente foi avisado de que o contrato está pronto para assinatura.");
    }
    @PostMapping({"/contratos/{id}/cancelar", "/contratos/{id}/recusar"})
    public ContratoModel cancelar(@PathVariable Long id, Authentication a) { return service.cancelar(id, a); }

    public record Assinatura(String nome, Boolean concordo) {}
    @PostMapping("/contratos/{id}/assinar") public ContratoModel assinar(@PathVariable Long id, @RequestBody Assinatura dados, Authentication a) {
        if (!Boolean.TRUE.equals(dados.concordo()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirme que leu e aceita o contrato.");
        return service.assinar(id, dados.nome(), a);
    }

    private boolean notificarCliente(ContratoModel c) {
        var cliente = c.getOferta().getUsuario();
        try {
            email.enviarTexto(cliente.getEmail(), "Contrato #" + c.getId() + " disponível para assinatura",
                "Olá, " + cliente.getNome() + ". Seu contrato do imóvel #" + c.getOferta().getImovel().getId()
                + " foi aprovado. Acesse sua área de contratos para baixar o PDF e assinar: " + frontend + "/cliente/contratos");
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // ---------- PDF do contrato ----------
    @GetMapping("/contratos/{id}/pdf") public ResponseEntity<byte[]> pdf(@PathVariable Long id, Authentication a) throws IOException {
        var c = service.contrato(id, a);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "attachment; filename=contrato-" + id + ".pdf").body(gerarPdf(c));
    }

    private static byte[] gerarPdf(ContratoModel c) throws IOException {
        var o = c.getOferta();
        var i = o.getImovel();
        boolean aluguel = o.getTipo() == OfertaModel.Tipo.ALUGUEL;
        String porMes = aluguel ? " por mes" : "";
        var reais = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
        var linhas = new ArrayList<String>();
        linhas.add("PATIO IMOVEIS - CONTRATO DE " + (aluguel ? "LOCACAO" : "COMPRA E VENDA"));
        linhas.add("Contrato #" + c.getId() + " | Oferta #" + o.getId() + " | Situacao: " + etapa(c.getStatus()));
        linhas.add("");
        linhas.add("Cliente: " + o.getUsuario().getNome() + " (" + o.getUsuario().getEmail() + ")");
        linhas.add("Corretor responsavel: " + (c.getCorretor() == null ? "-" : c.getCorretor().getNome()));
        linhas.add("Imovel #" + i.getId() + ": " + i.getTitulo());
        linhas.add("Endereco: " + Objects.toString(i.getEndereco(), "") + ", " + Objects.toString(i.getNumero(), "")
            + (i.getCidade() == null ? "" : " - " + i.getCidade()));
        linhas.add("");
        linhas.add("Valor proposto pelo cliente: " + reais.format(o.getValorProposto()) + porMes);
        linhas.add("Valor final acordado: " + (c.getValorFinal() == null ? "a definir" : reais.format(c.getValorFinal()) + porMes));
        linhas.add((aluguel ? "Inicio da locacao: " : "Entrega das chaves: ")
            + (c.getDataInicio() == null ? "a definir" : DATA.format(c.getDataInicio()))
            + (aluguel ? " | Prazo: " + (c.getPrazoMeses() == null ? "a definir" : c.getPrazoMeses() + " meses") : ""));
        linhas.add("");
        linhas.add("CONDICOES");
        if (c.getCondicoes() == null) linhas.add("A definir pelo corretor responsavel.");
        else linhas.addAll(Arrays.asList(c.getCondicoes().split("\\R")));
        linhas.add("");
        linhas.add("Preparado pelo corretor: " + quando(c.getDataPreparacao()));
        linhas.add("Aprovado pela administracao: " + quando(c.getDataAprovacao()));
        linhas.add("Assinado pelo cliente: " + quando(c.getDataAssinatura())
            + (c.getAssinante() == null ? "" : " - " + c.getAssinante()));
        if (c.getDataCancelamento() != null) linhas.add("Cancelado em: " + quando(c.getDataCancelamento()));
        linhas.add("");
        linhas.add("As partes confirmam o interesse na negociacao descrita acima.");
        linhas.add("Documento demonstrativo da disciplina Programacao Web IV.");
        linhas.add("Assinatura por confirmacao autenticada; sem certificado digital.");
        return escreverPdf(linhas);
    }

    private static String etapa(ContratoModel.Status s) {
        return switch (s) {
            case EM_PREPARACAO -> "Em preparacao";
            case AGUARDANDO_APROVACAO -> "Aguardando aprovacao";
            case AGUARDANDO_ASSINATURA -> "Aguardando assinatura";
            case ASSINADO -> "Assinado";
            case CANCELADO -> "Cancelado";
        };
    }
    private static String quando(Instant t) { return t == null ? "aguardando" : DATA_HORA.format(t); }

    private static final float TAMANHO = 11, ENTRELINHA = 17, LARGURA = 512;
    private static final int LINHAS_POR_PAGINA = 40;

    /** Escreve as linhas em paginas A4/Carta, quebrando por largura real do texto na fonte. */
    private static byte[] escreverPdf(List<String> linhas) throws IOException {
        var fonte = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        var quebradas = new ArrayList<String>();
        for (String linha : linhas) quebradas.addAll(quebrar(textoPdf(linha), fonte));
        try (var doc = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            for (int inicio = 0; inicio < quebradas.size(); inicio += LINHAS_POR_PAGINA) {
                var page = new PDPage();
                doc.addPage(page);
                try (var content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(fonte, TAMANHO);
                    content.setLeading(ENTRELINHA);
                    content.newLineAtOffset(50, 740);
                    for (String l : quebradas.subList(inicio, Math.min(inicio + LINHAS_POR_PAGINA, quebradas.size()))) {
                        content.showText(l);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            doc.save(bytes);
            return bytes.toByteArray();
        }
    }
    /** A fonte padrao do PDF so tem ASCII: remove acentos e troca o resto por espaco. */
    private static String textoPdf(String linha) {
        return Normalizer.normalize(linha, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^\\x20-\\x7E]", " ");
    }
    private static List<String> quebrar(String texto, PDType1Font fonte) throws IOException {
        var linhas = new ArrayList<String>();
        var atual = new StringBuilder();
        for (String palavra : texto.split(" ")) {
            while (largura(palavra, fonte) > LARGURA) {          // palavra enorme (ex.: URL): corta em pedacos
                if (!atual.isEmpty()) { linhas.add(atual.toString()); atual.setLength(0); }
                int corte = Math.min(palavra.length(), 80);
                linhas.add(palavra.substring(0, corte));
                palavra = palavra.substring(corte);
            }
            String tentativa = atual.isEmpty() ? palavra : atual + " " + palavra;
            if (atual.isEmpty() || largura(tentativa, fonte) <= LARGURA) { atual.setLength(0); atual.append(tentativa); }
            else { linhas.add(atual.toString()); atual.setLength(0); atual.append(palavra); }
        }
        linhas.add(atual.toString());
        return linhas;
    }
    private static float largura(String texto, PDType1Font fonte) throws IOException {
        return fonte.getStringWidth(texto) / 1000 * TAMANHO;
    }

    // ---------- Favoritos ----------
    @GetMapping("/favoritos") public List<FavoritoModel> favoritos(Authentication a) { return service.favoritos(a); }
    @PostMapping("/favoritos/{id}") public FavoritoModel favoritar(@PathVariable Integer id, Authentication a) { return service.favoritar(id, a); }
    @DeleteMapping("/favoritos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desfavoritar(@PathVariable Integer id, Authentication a) { service.desfavoritar(id, a); }
}
