package com.example.demo;
import com.example.demo.models.*;
import com.example.demo.repositories.*;
import com.example.demo.services.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:negociacao","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","app.admin.email=admin@example.com","app.admin.password=AdminTeste123!","app.mail.contato=agencia@example.com"})
class NegociacaoApiTest {
    @LocalServerPort int port;
    @Autowired UsuarioService usuarios;
    @Autowired ImovelRepository imoveis;
    @MockitoBean EmailService email;
    HttpClient client=HttpClient.newHttpClient();
    HttpResponse<String> req(String method,String path,String body,String token)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(token!=null)b.header("Authorization","Bearer "+token);
        return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String login(String mail)throws Exception {var r=req("POST","/auth/login","{\"email\":\""+mail+"\",\"senha\":\"AdminTeste123!\"}",null);assertEquals(200,r.statusCode(),r.body());return JsonPath.read(r.body(),"$.token");}
    String oferta(Integer id,String token)throws Exception {var r=req("POST","/ofertas","{\"imovelId\":"+id+",\"tipo\":\"VENDA\",\"valorProposto\":400000,\"mensagem\":\"Minha proposta\"}",token);assertEquals(201,r.statusCode(),r.body());return ((Number)JsonPath.read(r.body(),"$.id")).toString();}
    @Test void fluxoCompletoComPrivacidadeEmailPdfECancelamento()throws Exception {
        var u=usuarios.salvar(null,new UsuarioService.DadosUsuario("Cliente Um","um@example.com","AdminTeste123!",UsuarioModel.Perfil.USER));
        usuarios.salvar(null,new UsuarioService.DadosUsuario("Cliente Dois","dois@example.com","AdminTeste123!",UsuarioModel.Perfil.USER));
        String a=login("admin@example.com"),um=login("um@example.com"),dois=login("dois@example.com");
        var i=new ImovelModel();i.setTitulo("Casa de negociação");i.setStatus("Disponível");i.setPrecoVenda(new java.math.BigDecimal("450000"));i=imoveis.save(i);int id=i.getId();
        assertEquals(401,req("GET","/ofertas",null,null).statusCode());
        assertEquals(403,req("GET","/painel",null,um).statusCode());
        assertEquals(409,req("DELETE","/usuarios/1",null,a).statusCode());
        String o1=oferta(id,um),o2=oferta(id,dois);
        assertEquals(409,req("POST","/ofertas","{\"imovelId\":"+id+",\"tipo\":\"VENDA\",\"valorProposto\":400000,\"mensagem\":\"Duplicada\"}",um).statusCode());
        assertEquals(1,((java.util.List<?>)JsonPath.read(req("GET","/ofertas",null,um).body(),"$")).size());
        assertEquals(200,req("POST","/favoritos/"+id,null,um).statusCode());
        assertEquals(200,req("POST","/favoritos/"+id,null,um).statusCode());
        assertEquals(1,((java.util.List<?>)JsonPath.read(req("GET","/favoritos",null,um).body(),"$")).size());
        assertEquals(0,((java.util.List<?>)JsonPath.read(req("GET","/favoritos",null,dois).body(),"$")).size());
        assertEquals(403,req("POST","/ofertas/"+o1+"/aceitar",null,um).statusCode());
        assertEquals(200,req("POST","/ofertas/"+o1+"/aceitar",null,a).statusCode());
        assertEquals("Reservado",imoveis.findById(id).orElseThrow().getStatus());
        assertEquals("EM_ESPERA",JsonPath.read(req("GET","/ofertas",null,dois).body(),"$[0].status"));
        assertEquals(409,req("POST","/ofertas/"+o2+"/aceitar",null,a).statusCode());
        String contrato=((Number)JsonPath.read(req("GET","/contratos",null,um).body(),"$[0].id")).toString(),c="/contratos/"+contrato;
        assertEquals(403,req("GET",c,null,dois).statusCode());assertEquals(403,req("GET",c+"/pdf",null,dois).statusCode());
        var pdf=req("GET",c+"/pdf",null,um);assertEquals(200,pdf.statusCode(),pdf.body());assertTrue(pdf.body().startsWith("%PDF"));
        String sign="{\"nome\":\"Cliente Um\",\"concordo\":true}";
        assertEquals(409,req("POST",c+"/assinar",sign,um).statusCode());
        assertEquals(200,req("POST",c+"/aprovar",null,a).statusCode());
        doThrow(new IllegalStateException()).when(email).enviarTexto(anyString(),anyString(),anyString());
        assertEquals(502,req("POST",c+"/enviar",null,a).statusCode());
        assertNull(JsonPath.read(req("GET",c,null,um).body(),"$.dataEnvio"));
        reset(email);assertEquals(200,req("POST",c+"/enviar",null,a).statusCode());
        verify(email).enviarTexto(eq("um@example.com"),contains("Contrato"),contains("/cliente/contratos"));
        assertEquals(403,req("POST",c+"/assinar",sign,dois).statusCode());
        assertEquals(400,req("POST",c+"/assinar","{\"nome\":\"Outro\",\"concordo\":true}",um).statusCode());
        assertEquals(200,req("POST",c+"/assinar",sign,um).statusCode());
        assertEquals("Vendido",imoveis.findById(id).orElseThrow().getStatus());
        assertEquals("RECUSADA",JsonPath.read(req("GET","/ofertas",null,dois).body(),"$[0].status"));
        assertEquals(409,req("PATCH","/imoveis/"+id+"/status","{\"status\":\"Disponível\"}",um).statusCode());
        assertEquals(200,req("POST",c+"/cancelar",null,a).statusCode());
        assertEquals("Disponível",imoveis.findById(id).orElseThrow().getStatus());
        oferta(id,dois);
        assertEquals(204,req("DELETE","/favoritos/"+id,null,um).statusCode());
        assertEquals(200,req("GET","/painel",null,a).statusCode());
    }
    @Test void aceiteConcorrenteReservaApenasUmaOferta()throws Exception {
        usuarios.salvar(null,new UsuarioService.DadosUsuario("Concorrente A","concorrente-a@example.com","AdminTeste123!",UsuarioModel.Perfil.USER));
        usuarios.salvar(null,new UsuarioService.DadosUsuario("Concorrente B","concorrente-b@example.com","AdminTeste123!",UsuarioModel.Perfil.USER));
        String admin=login("admin@example.com"),a=login("concorrente-a@example.com"),b=login("concorrente-b@example.com");
        var i=new ImovelModel();i.setTitulo("Concorrência");i.setStatus("Disponível");i.setPrecoVenda(new java.math.BigDecimal("500000"));i=imoveis.save(i);
        String oa=oferta(i.getId(),a),ob=oferta(i.getId(),b);
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var primeiro=pool.submit(()->{start.await();return req("POST","/ofertas/"+oa+"/aceitar",null,admin).statusCode();});
            var segundo=pool.submit(()->{start.await();return req("POST","/ofertas/"+ob+"/aceitar",null,admin).statusCode();});start.countDown();
            var resultados=new java.util.ArrayList<>(java.util.List.of(primeiro.get(),segundo.get()));java.util.Collections.sort(resultados);
            assertEquals(java.util.List.of(200,409),resultados);
        }
    }
}
