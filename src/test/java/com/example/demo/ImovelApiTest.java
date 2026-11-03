package com.example.demo;
import java.net.URI;
import java.net.http.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
 "spring.datasource.url=jdbc:h2:mem:imoveis-test",
 "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
class ImovelApiTest extends AdminApiSupport {
 @LocalServerPort private int port;
 private final HttpClient client = HttpClient.newHttpClient();
 private HttpResponse<String> request(String method, String path, String body) throws Exception {
  return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
   .header("Authorization", "Bearer " + adminToken()).header("Content-Type","application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
 }
 private String createdPath(HttpResponse<String> response) {
  assertEquals(201,response.statusCode(),response.body());
  return URI.create(response.headers().firstValue("Location").orElseThrow()).getPath();
 }
 @Test void cicloCompletoComRelacionamentosEPaginacao() throws Exception {
  String tipo = createdPath(request("POST","/tipos-imoveis","{\"nome\":\"Casa\"}"));
  String bairro = createdPath(request("POST","/bairros","{\"nome\":\"Centro\"}"));
  int tipoId = Integer.parseInt(tipo.substring(tipo.lastIndexOf('/')+1));
  int bairroId = Integer.parseInt(bairro.substring(bairro.lastIndexOf('/')+1));
  String body = """
   {"titulo":"Casa ampla com piscina","descricao":"Casa de teste","precoVenda":850000.00,
   "precoAluguel":3500.00,"finalidade":"Residencial","status":"Disponível","dormitorios":3,
   "banheiros":2,"garagem":2,"areaTotal":300.50,"areaConstruida":180.75,
   "endereco":"Rua das Flores","numero":"123","complemento":"Casa 02","cep":"12345-678",
   "caracteristicas":"Piscina, Churrasqueira, Jardim","destaque":true,
   "tipoImovel":{"id":%d},"bairro":{"id":%d}}
   """.formatted(tipoId,bairroId);
  assertEquals(400,request("POST","/imoveis",body.replace("\"titulo\":\"Casa ampla com piscina\"","\"titulo\":\"\"")).statusCode());
  assertEquals(400,request("POST","/imoveis",body.replace("\"bairro\":{\"id\":"+bairroId+"}","\"bairro\":{\"id\":999999}")).statusCode());
  assertEquals(400,request("POST","/imoveis",body.replace("850000.00","-1.00")).statusCode());
  String path = createdPath(request("POST","/imoveis",body));
  var found = request("GET",path,null);
  assertEquals(200,found.statusCode());
  assertEquals("Centro",JsonPath.read(found.body(),"$.bairro.nome"));
  assertEquals("Casa",JsonPath.read(found.body(),"$.tipoImovel.nome"));
  assertEquals(850000.0,((Number)JsonPath.read(found.body(),"$.precoVenda")).doubleValue());
  assertEquals(true,JsonPath.read(found.body(),"$.destaque"));
  var paged = request("GET","/imoveis/imoveis-page?page=0&size=1&sort=id,DESC",null);
  assertEquals(200,paged.statusCode());
  assertEquals(1,(int)JsonPath.read(paged.body(),"$.totalElements"));
  assertEquals("Centro",JsonPath.read(paged.body(),"$.content[0].bairro.nome"));
  assertEquals("Casa",JsonPath.read(paged.body(),"$.content[0].tipoImovel.nome"));
  var all = request("GET","/imoveis",null);
  assertEquals(200,all.statusCode());
  assertEquals("Centro",JsonPath.read(all.body(),"$[0].bairro.nome"));
  assertEquals("Casa",JsonPath.read(all.body(),"$[0].tipoImovel.nome"));
  for (String json : java.util.List.of(found.body(), paged.body(), all.body())) {
   assertFalse(json.contains("hibernateLazyInitializer"));
   assertFalse(json.contains("\"handler\""));
  }
  assertEquals(204,request("PUT",path,body.replace("Casa ampla com piscina","Casa atualizada")).statusCode());
  assertEquals("Casa atualizada",JsonPath.read(request("GET",path,null).body(),"$.titulo"));
  assertEquals(204,request("DELETE",path,null).statusCode());
  assertEquals(404,request("GET",path,null).statusCode());
  assertEquals(404,request("PUT",path,body).statusCode());
  assertEquals(404,request("DELETE",path,null).statusCode());
  assertEquals(200,request("GET",tipo,null).statusCode());
  assertEquals(1,((java.util.List<?>)JsonPath.read(request("GET","/bairros",null).body(),"$")).size());
 }
}
