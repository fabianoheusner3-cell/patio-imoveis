package com.example.demo.controllers;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.example.demo.services.*;
import java.util.Map;
@RestController
public class ContatoController {
    private final ImovelService imoveis;private final EmailService email;private final String destinatario;
    public ContatoController(ImovelService i,EmailService e,@Value("${app.mail.contato:}")String d){imoveis=i;email=e;destinatario=d;}
    public record Interesse(String nome,String email,String assunto,String mensagem){}
    @PostMapping("/contato/{id}") public Map<String,String> contato(@PathVariable Integer id,@RequestBody Interesse d){
        var i=imoveis.find(id);
        if(d.nome()==null||d.nome().isBlank()||d.nome().length()>150||d.email()==null||d.email().length()>254||!d.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||d.assunto()==null||d.assunto().isBlank()||d.assunto().length()>200||d.assunto().matches("(?s).*[\\r\\n].*")||d.mensagem()==null||d.mensagem().isBlank()||d.mensagem().length()>4000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe nome, email, assunto e mensagem válidos.");
        if(destinatario.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"O email da imobiliária ainda não está configurado. Entre em contato por telefone.");
        try{email.enviarTexto(destinatario,d.assunto(),"Interesse no imóvel #"+i.getId()+" - "+i.getTitulo()+"\nTipo: "+i.getTipoImovel().getNome()+"\nBairro: "+i.getBairro().getNome()+"\nVenda: "+i.getPrecoVenda()+" | Aluguel: "+i.getPrecoAluguel()+"\nCliente: "+d.nome()+" <"+d.email()+">\n\n"+d.mensagem());}
        catch(RuntimeException ex){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Não foi possível enviar seu interesse. Tente novamente mais tarde.");}
        return Map.of("mensagem","Interesse enviado à imobiliária.");
    }
}
