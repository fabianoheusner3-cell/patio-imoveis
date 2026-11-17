package com.example.demo;

import com.example.demo.models.*;
import com.example.demo.repositories.*;
import com.example.demo.services.*;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;

/** Perfil CORRETOR, listas paginadas de ofertas e contratos e o painel de cada perfil. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:perfis-paineis", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never", "app.admin.email=admin@example.com", "app.admin.password=AdminTeste123!"})
class PerfisPaineisApiTest {
    static final String SENHA = "AdminTeste123!";
    @LocalServerPort int port;
    @Autowired UsuarioService usuarios;
    @Autowired ImovelRepository imoveis;
    @Autowired BairroRepository bairros;
    @Autowired TipoImovelRepository tipos;
    @MockitoBean EmailService email;
    final HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> req(String method, String path, String body, String token) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        return client.send(b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    static void status(int esperado, HttpResponse<String> r) { assertEquals(esperado, r.statusCode(), r.body()); }
    static String json(HttpResponse<String> r, String caminho) { return String.valueOf((Object) JsonPath.read(r.body(), caminho)); }
    static int quantos(HttpResponse<String> r, String caminho) { return ((List<?>) JsonPath.read(r.body(), caminho)).size(); }
    static String q(String texto) { return URLEncoder.encode(texto, StandardCharsets.UTF_8); }

    String login(String mail, String senha) throws Exception {
        var r = req("POST", "/auth/login", "{\"email\":\"" + mail + "\",\"senha\":\"" + senha + "\"}", null);
        status(200, r);
        return JsonPath.read(r.body(), "$.token");
    }
    String usuario(String nome, String mail, UsuarioModel.Perfil perfil) throws Exception {
        usuarios.salvar(null, new UsuarioService.DadosUsuario(nome, mail, SENHA, perfil));
        return login(mail, SENHA);
    }
    // Com bairro e tipo, como no cadastro real: a lista paginada precisa enviar os dois no JSON.
    int imovel(String titulo, BairroModel bairro, TipoImovelModel tipo) {
        var i = new ImovelModel();
        i.setTitulo(titulo);
        i.setStatus("Disponível");
        i.setPrecoVenda(new BigDecimal("300000"));
        i.setBairro(bairro);
        i.setTipoImovel(tipo);
        return imoveis.save(i).getId();
    }
    String oferta(int imovel, String token) throws Exception {
        var r = req("POST", "/ofertas", "{\"imovelId\":" + imovel + ",\"tipo\":\"VENDA\",\"valorProposto\":290000,\"mensagem\":\"Proposta\"}", token);
        status(201, r);
        return json(r, "$.id");
    }

    @Test void corretorCriadoComOsCamposDaTabelaDoCronograma() throws Exception {
        String admin = login("admin@example.com", SENHA);
        var criado = req("POST", "/users",
            "{\"nome\":\"Corretora Tabela\",\"email\":\"corretora@example.com\",\"password\":\"Corretor123!\",\"tipo\":\"CORRETOR\"}", admin);
        status(201, criado);
        assertEquals("CORRETOR", json(criado, "$.perfil"));
        var login = req("POST", "/public/login", "{\"email\":\"corretora@example.com\",\"password\":\"Corretor123!\"}", null);
        status(200, login);
        assertEquals("CORRETOR", json(login, "$.tipo"));
        String corretor = JsonPath.read(login.body(), "$.token");
        assertTrue(CronogramaApiTest.payload(corretor).contains("\"tipo\":\"CORRETOR\""));

        // O corretor atende ofertas, mas nao administra cadastros nem age como cliente
        status(200, req("GET", "/auth/me", null, corretor));
        status(200, req("GET", "/ofertas", null, corretor));
        status(403, req("GET", "/usuarios", null, corretor));
        status(403, req("POST", "/imoveis", "{}", corretor));
        status(403, req("POST", "/bairros", "{\"nome\":\"Bairro do corretor\"}", corretor));
        status(403, req("POST", "/ofertas", "{\"imovelId\":1,\"tipo\":\"VENDA\",\"valorProposto\":1,\"mensagem\":\"x\"}", corretor));
        status(403, req("POST", "/favoritos/1", null, corretor));
        // Cadastro publico continua criando apenas clientes
        var publico = req("POST", "/public/cadastro", "{\"nome\":\"Visitante\",\"email\":\"visitante@example.com\",\"senha\":\"Visitante123!\",\"perfil\":\"CORRETOR\"}", null);
        status(201, publico);
        assertEquals("USER", json(publico, "$.perfil"));
    }

    @Test void listasPaginadasEPainelDeCadaPerfil() throws Exception {
        String admin = login("admin@example.com", SENHA);
        String cliente = usuario("Cliente Painel", "cliente-painel@example.com", UsuarioModel.Perfil.USER);
        String outro = usuario("Outro Cliente", "outro@example.com", UsuarioModel.Perfil.USER);
        String corretor = usuario("Corretor Painel", "corretor-painel@example.com", UsuarioModel.Perfil.CORRETOR);
        String colega = usuario("Colega Corretor", "colega@example.com", UsuarioModel.Perfil.CORRETOR);
        var centro = new BairroModel();
        centro.setNome("Centro");
        centro = bairros.save(centro);
        var casa = tipos.save(new TipoImovelModel(null, "Casa"));
        int azul = imovel("Casa Azul", centro, casa), verde = imovel("Casa Verde", centro, casa),
            sala = imovel("Sala Central", centro, casa);
        String aceita = oferta(azul, cliente);
        oferta(verde, cliente);
        oferta(sala, cliente);
        oferta(azul, outro);

        // Ofertas paginadas no servidor: cliente so ve as proprias, corretor ve todas
        var pagina = req("GET", "/ofertas/ofertas-page?page=0&size=2", null, cliente);
        status(200, pagina);
        assertTrue(pagina.body().contains("\"totalElements\":3"), pagina.body());
        assertEquals(2, quantos(pagina, "$.content"));
        assertEquals("Centro", json(pagina, "$.content[0].imovel.bairro.nome"));
        assertEquals("Casa", json(pagina, "$.content[0].imovel.tipoImovel.nome"));
        assertEquals(1, quantos(req("GET", "/ofertas/ofertas-page?page=1&size=2", null, cliente), "$.content"));
        assertTrue(req("GET", "/ofertas/ofertas-page", null, corretor).body().contains("\"totalElements\":4"));
        assertEquals(2, quantos(req("GET", "/ofertas/ofertas-page?busca=" + q("casa azul"), null, corretor), "$.content"));
        assertEquals(1, quantos(req("GET", "/ofertas/ofertas-page?busca=" + q("outro@example"), null, corretor), "$.content"));
        status(400, req("GET", "/ofertas/ofertas-page?status=INVENTADA", null, corretor));

        status(200, req("POST", "/ofertas/" + aceita + "/aceitar", null, corretor));
        assertEquals(3, quantos(req("GET", "/ofertas/ofertas-page?status=PENDENTE,EM_ESPERA", null, corretor), "$.content"));
        assertEquals("EM_ESPERA", json(req("GET", "/ofertas/ofertas-page?status=EM_ESPERA", null, corretor), "$.content[0].status"));
        assertEquals(1, quantos(req("GET", "/ofertas/ofertas-page?status=ACEITA", null, cliente), "$.content"));

        // Contratos paginados: cada perfil ve so o que e seu
        assertEquals(1, quantos(req("GET", "/contratos/contratos-page", null, corretor), "$.content"));
        assertEquals(0, quantos(req("GET", "/contratos/contratos-page", null, colega), "$.content"));
        assertEquals(1, quantos(req("GET", "/contratos/contratos-page", null, cliente), "$.content"));
        assertEquals(0, quantos(req("GET", "/contratos/contratos-page", null, outro), "$.content"));
        assertEquals(1, quantos(req("GET", "/contratos/contratos-page?status=EM_PREPARACAO", null, admin), "$.content"));
        assertEquals(0, quantos(req("GET", "/contratos/contratos-page?status=ASSINADO", null, admin), "$.content"));
        assertEquals(1, quantos(req("GET", "/contratos/contratos-page?busca=" + q("corretor painel"), null, admin), "$.content"));

        // Cada painel so abre para o seu perfil
        for (String token : List.of(corretor, cliente)) status(403, req("GET", "/painel", null, token));
        for (String token : List.of(admin, cliente)) status(403, req("GET", "/painel/corretor", null, token));
        for (String token : List.of(admin, corretor)) status(403, req("GET", "/painel/cliente", null, token));

        var doCorretor = req("GET", "/painel/corretor", null, corretor);
        status(200, doCorretor);
        assertEquals("3", json(doCorretor, "$.fila"));
        assertEquals("1", json(doCorretor, "$.emPreparacao"));
        assertEquals("EM_PREPARACAO", json(doCorretor, "$.contratosPorEtapa[0].nome"));
        assertEquals("1", json(doCorretor, "$.contratosPorEtapa[0].total"));
        assertEquals(6, quantos(doCorretor, "$.porMes"));
        assertEquals("1", json(doCorretor, "$.porMes[5].aceitas"));
        assertEquals(3, quantos(doCorretor, "$.proximas"));
        assertEquals("Centro", json(doCorretor, "$.filaPorBairro[0].nome"));
        assertEquals("3", json(doCorretor, "$.filaPorBairro[0].total"));
        assertEquals("0", json(req("GET", "/painel/corretor", null, colega), "$.emPreparacao"));

        var doCliente = req("GET", "/painel/cliente", null, cliente);
        status(200, doCliente);
        assertEquals("2", json(doCliente, "$.ofertasAbertas"));
        assertEquals("0", json(doCliente, "$.paraAssinar"));
        assertEquals("EM_PREPARACAO", json(doCliente, "$.emAndamento[0].status"));

        var doAdmin = req("GET", "/painel", null, admin);
        status(200, doAdmin);
        assertEquals("2", json(doAdmin, "$.pendentes"));
        assertEquals("Corretor Painel", json(doAdmin, "$.porCorretor[0].nome"));
        assertEquals("4", json(doAdmin, "$.porMes[5].ofertas"));

        // Depois de preparar e aprovar, os paineis acompanham a etapa
        String c = "/contratos/" + json(req("GET", "/contratos", null, corretor), "$[0].id");
        String inicio = java.time.LocalDate.now(NegociacaoService.FUSO).plusDays(10).toString();
        status(200, req("POST", c + "/preparar", "{\"valorFinal\":295000,\"dataInicio\":\"" + inicio + "\",\"condicoes\":\"À vista.\"}", corretor));
        assertEquals("1", json(req("GET", "/painel", null, admin), "$.aguardandoAprovacao"));
        assertEquals(1, quantos(req("GET", "/painel", null, admin), "$.paraAprovar"));
        status(200, req("POST", c + "/aprovar", null, admin));
        assertEquals("1", json(req("GET", "/painel/cliente", null, cliente), "$.paraAssinar"));
        assertEquals("1", json(req("GET", "/painel/corretor", null, corretor), "$.aguardandoAssinatura"));
        status(200, req("POST", c + "/assinar", "{\"nome\":\"Cliente Painel\",\"concordo\":true}", cliente));
        var final_ = req("GET", "/painel", null, admin);
        assertEquals("1", json(final_, "$.assinados"));
        assertEquals(0, new BigDecimal(json(final_, "$.valorNegociado")).compareTo(new BigDecimal("295000")));
        assertEquals("1", json(final_, "$.porMes[5].assinados"));

        // Se o contrato assinado for cancelado, ele sai dos numeros de assinados e vai para cancelados
        status(200, req("POST", c + "/cancelar", null, admin));
        var cancelado = req("GET", "/painel", null, admin);
        assertEquals("0", json(cancelado, "$.assinados"));
        assertEquals("0", json(cancelado, "$.porMes[5].assinados"));
        assertEquals("1", json(cancelado, "$.cancelados"));
    }
}
