package com.example.demo.controllers;
import com.example.demo.services.*;
import com.example.demo.models.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
import java.io.*;
import java.text.Normalizer;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;

@RestController
public class NegociacaoController {
    private final NegociacaoService service;
    private final EmailService email;
    private final String frontend;
    public NegociacaoController(NegociacaoService service,EmailService email,@Value("${app.frontend-url:http://localhost:5173}")String frontend){this.service=service;this.email=email;this.frontend=frontend;}
    @GetMapping("/ofertas") public List<OfertaModel> ofertas(Authentication a,@RequestParam(required=false)String status){return service.listarOfertas(a,status);}
    @PostMapping("/ofertas") @ResponseStatus(HttpStatus.CREATED) public OfertaModel criar(@RequestBody NegociacaoService.NovaOferta dados,Authentication a){return service.criar(dados,a);}
    @PostMapping("/ofertas/{id}/{acao}") public OfertaModel decidir(@PathVariable Long id,@PathVariable String acao){return service.decidir(id,acao);}
    @GetMapping("/contratos") public List<ContratoModel> contratos(Authentication a){return service.listarContratos(a);}
    @GetMapping("/contratos/{id}") public ContratoModel contrato(@PathVariable Long id,Authentication a){return service.contrato(id,a);}
    @PostMapping("/contratos/{id}/aprovar") public ContratoModel aprovar(@PathVariable Long id,Authentication a){return service.aprovar(id,a);}
    @PostMapping({"/contratos/{id}/cancelar","/contratos/{id}/recusar"}) public ContratoModel cancelar(@PathVariable Long id,Authentication a){return service.cancelar(id,a);}
    public record Assinatura(String nome,Boolean concordo){}
    @PostMapping("/contratos/{id}/assinar") public ContratoModel assinar(@PathVariable Long id,@RequestBody Assinatura dados,Authentication a){
        if(!Boolean.TRUE.equals(dados.concordo()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Confirme que leu e aceita o contrato.");
        return service.assinar(id,dados.nome(),a);
    }
    @PostMapping("/contratos/{id}/enviar") public Map<String,String> enviar(@PathVariable Long id,Authentication a){
        var c=service.contrato(id,a);
        if(c.getDataAprovacao()==null||c.getStatus()!=ContratoModel.Status.AGUARDANDO_ASSINATURA)throw new ResponseStatusException(HttpStatus.CONFLICT,"Aprove o contrato antes de enviar ao cliente.");
        try {email.enviarTexto(c.getOferta().getUsuario().getEmail(),"Contrato #"+id+" disponível para assinatura",
            "Olá, "+c.getOferta().getUsuario().getNome()+". Seu contrato do imóvel #"+c.getOferta().getImovel().getId()+" foi aprovado. Acesse sua área de contratos para baixar o PDF e assinar: "+frontend+"/cliente/contratos");}
        catch(RuntimeException ex){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Não foi possível enviar o email. Verifique a configuração SMTP/Mailgun e tente novamente. O contrato continua aprovado.");}
        service.marcarEnviado(id,a);return Map.of("mensagem","Contrato enviado e cliente notificado por email.");
    }
    @GetMapping("/contratos/{id}/pdf") public ResponseEntity<byte[]> pdf(@PathVariable Long id,Authentication a) throws IOException {
        var c=service.contrato(id,a);var o=c.getOferta();var i=o.getImovel();
        try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()) {
            var page=new PDPage();doc.addPage(page);
            try(var content=new PDPageContentStream(doc,page)) {
                content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),11);content.setLeading(22);content.newLineAtOffset(45,745);
                var linhas=List.of("PATIO - CONTRATO DE "+o.getTipo(),"Contrato #"+id+" | Oferta #"+o.getId(),
                    "Cliente: "+o.getUsuario().getNome(),"Email: "+o.getUsuario().getEmail(),"Imovel #"+i.getId()+": "+i.getTitulo(),
                    "Endereco: "+Objects.toString(i.getEndereco(),"")+", "+Objects.toString(i.getNumero(),""),
                    "Valor acordado: R$ "+o.getValorProposto(),"Situacao: "+c.getStatus(),"Aprovacao: "+Objects.toString(c.getDataAprovacao(),"Aguardando"),
                    "Assinatura: "+Objects.toString(c.getDataAssinatura(),"Aguardando"),"Assinante: "+Objects.toString(c.getAssinante(),"Aguardando"),
                    "As partes confirmam o interesse na negociacao descrita acima.","Documento demonstrativo da disciplina Programacao Web IV.","Assinatura por confirmacao autenticada; sem certificado digital.");
                for(String linha:linhas){String texto=Normalizer.normalize(linha,Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^\\x20-\\x7E]"," ");
                    while(texto.length()>82){content.showText(texto.substring(0,82));content.newLine();texto=texto.substring(82);}content.showText(texto);content.newLine();}
                content.endText();
            }
            doc.save(bytes);return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Content-Disposition","attachment; filename=contrato-"+id+".pdf").body(bytes.toByteArray());
        }
    }
    @GetMapping("/favoritos") public List<FavoritoModel> favoritos(Authentication a){return service.favoritos(a);}
    @PostMapping("/favoritos/{id}") public FavoritoModel favoritar(@PathVariable Integer id,Authentication a){return service.favoritar(id,a);}
    @DeleteMapping("/favoritos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void desfavoritar(@PathVariable Integer id,Authentication a){service.desfavoritar(id,a);}
}
