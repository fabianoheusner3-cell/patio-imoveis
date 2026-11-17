package com.example.demo;

import com.example.demo.models.*;
import com.example.demo.repositories.*;
import com.example.demo.services.*;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Checkpoint da Aula 3 e as regras de negocio de cada perfil:
 * cliente envia oferta -> corretor aceita e prepara o contrato -> administrador aprova -> cliente assina.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:negociacao", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never", "app.admin.email=admin@example.com", "app.admin.password=AdminTeste123!",
    "app.mail.contato=agencia@example.com"})
class NegociacaoApiTest {
    static final String SENHA = "AdminTeste123!";
    @LocalServerPort int port;
    @Autowired UsuarioService usuarios;
    @Autowired ImovelRepository imoveis;
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
    static List<?> lista(HttpResponse<String> r) { return JsonPath.read(r.body(), "$"); }
    static LocalDate hoje() { return LocalDate.now(NegociacaoService.FUSO); }

    String login(String mail) throws Exception {
        var r = req("POST", "/auth/login", "{\"email\":\"" + mail + "\",\"senha\":\"" + SENHA + "\"}", null);
        status(200, r);
        return JsonPath.read(r.body(), "$.token");
    }
    String usuario(String nome, String mail, UsuarioModel.Perfil perfil) throws Exception {
        usuarios.salvar(null, new UsuarioService.DadosUsuario(nome, mail, SENHA, perfil));
        return login(mail);
    }
    int imovel(String titulo, Integer venda, Integer aluguel) {
        var i = new ImovelModel();
        i.setTitulo(titulo);
        i.setStatus("Disponível");
        if (venda != null) i.setPrecoVenda(new BigDecimal(venda));
        if (aluguel != null) i.setPrecoAluguel(new BigDecimal(aluguel));
        return imoveis.save(i).getId();
    }
    String statusImovel(int id) { return imoveis.findById(id).orElseThrow().getStatus(); }
    static String novaOferta(int imovel, String tipo, Object valor) {
        return "{\"imovelId\":" + imovel + ",\"tipo\":\"" + tipo + "\",\"valorProposto\":" + valor + ",\"mensagem\":\"Minha proposta\"}";
    }
    String oferta(int imovel, String tipo, Object valor, String token) throws Exception {
        var r = req("POST", "/ofertas", novaOferta(imovel, tipo, valor), token);
        status(201, r);
        return json(r, "$.id");
    }
    static String preparo(Object valor, LocalDate inicio, Integer prazo, String condicoes) {
        return "{\"valorFinal\":" + valor + ",\"dataInicio\":" + (inicio == null ? "null" : "\"" + inicio + "\"")
            + ",\"prazoMeses\":" + prazo + ",\"condicoes\":" + (condicoes == null ? "null" : "\"" + condicoes + "\"") + "}";
    }

    @Test void fluxoDoCheckpointComAsRegrasDeCadaPerfil() throws Exception {
        String um = usuario("Cliente Um", "um@example.com", UsuarioModel.Perfil.USER);
        String dois = usuario("Cliente Dois", "dois@example.com", UsuarioModel.Perfil.USER);
        String corretor = usuario("Carla Corretora", "carla@example.com", UsuarioModel.Perfil.CORRETOR);
        String outroCorretor = usuario("Bruno Corretor", "bruno@example.com", UsuarioModel.Perfil.CORRETOR);
        String admin = login("admin@example.com");
        int id = imovel("Casa de negociação", 450000, null);
        status(401, req("GET", "/ofertas", null, null));
        status(409, req("DELETE", "/usuarios/1", null, admin));

        // 1. Cliente envia a oferta: so o perfil USER, e uma oferta em aberto por imovel
        String o1 = oferta(id, "VENDA", 400000, um), o2 = oferta(id, "VENDA", 410000, dois);
        status(409, req("POST", "/ofertas", novaOferta(id, "VENDA", 405000), um));
        status(403, req("POST", "/ofertas", novaOferta(id, "VENDA", 405000), corretor));
        status(403, req("POST", "/ofertas", novaOferta(id, "VENDA", 405000), admin));
        assertEquals(1, lista(req("GET", "/ofertas", null, um)).size());
        // O corretor ve as ofertas de todos os clientes (os outros testes da classe usam o mesmo banco)
        assertEquals(2, ((List<?>) JsonPath.read(req("GET", "/ofertas", null, corretor).body(), "$[?(@.imovel.id == " + id + ")]")).size());

        // 2. Corretor aceita: o imovel fica reservado e a outra oferta entra em espera
        status(403, req("POST", "/ofertas/" + o1 + "/aceitar", null, um));
        status(403, req("POST", "/ofertas/" + o1 + "/aceitar", null, admin));
        status(200, req("POST", "/ofertas/" + o1 + "/aceitar", null, corretor));
        assertEquals("Reservado", statusImovel(id));
        assertEquals("EM_ESPERA", json(req("GET", "/ofertas", null, dois), "$[0].status"));
        status(409, req("POST", "/ofertas/" + o2 + "/aceitar", null, outroCorretor));
        status(409, req("POST", "/ofertas/" + o1 + "/recusar", null, corretor));

        var meus = req("GET", "/contratos", null, corretor);
        String c = "/contratos/" + json(meus, "$[0].id");
        assertEquals("EM_PREPARACAO", json(meus, "$[0].status"));
        assertEquals("Carla Corretora", json(meus, "$[0].corretor.nome"));
        assertEquals(0, lista(req("GET", "/contratos", null, outroCorretor)).size());
        status(403, req("GET", c, null, outroCorretor));
        status(403, req("GET", c, null, dois));
        status(403, req("GET", c + "/pdf", null, dois));
        var pdf = req("GET", c + "/pdf", null, um);
        status(200, pdf);
        assertTrue(pdf.body().startsWith("%PDF"));

        // Nenhuma etapa pode ser pulada
        String assinatura = "{\"nome\":\"Cliente Um\",\"concordo\":true}";
        status(409, req("POST", c + "/aprovar", null, admin));
        status(409, req("POST", c + "/assinar", assinatura, um));

        // 3. So o corretor responsavel prepara, com termos validos
        String valido = preparo(395000, hoje().plusDays(7), 12, "Pagamento à vista na escritura.");
        status(403, req("POST", c + "/preparar", valido, outroCorretor));
        status(403, req("POST", c + "/preparar", valido, admin));
        status(403, req("POST", c + "/preparar", valido, um));
        status(400, req("POST", c + "/preparar", preparo(0, hoje().plusDays(7), null, "Condições"), corretor));
        status(400, req("POST", c + "/preparar", preparo(395000, hoje().minusDays(1), null, "Condições"), corretor));
        status(400, req("POST", c + "/preparar", preparo(395000, hoje().plusDays(7), null, " "), corretor));
        var preparado = req("POST", c + "/preparar", valido, corretor);
        status(200, preparado);
        assertEquals("AGUARDANDO_APROVACAO", json(preparado, "$.status"));
        assertEquals(0, new BigDecimal(json(preparado, "$.valorFinal")).compareTo(new BigDecimal("395000")));
        assertNull(JsonPath.read(preparado.body(), "$.prazoMeses"), "Venda nao tem prazo");
        status(409, req("POST", c + "/preparar", valido, corretor));

        // 4. Administrador pode devolver ao corretor, explicando o ajuste
        status(403, req("POST", c + "/aprovar", null, corretor));
        status(400, req("POST", c + "/devolver", "{\"motivo\":\" \"}", admin));
        var devolvido = req("POST", c + "/devolver", "{\"motivo\":\"Confirme a data da escritura.\"}", admin);
        status(200, devolvido);
        assertEquals("EM_PREPARACAO", json(devolvido, "$.status"));
        assertEquals("Confirme a data da escritura.", json(devolvido, "$.motivoDevolucao"));
        status(409, req("POST", c + "/aprovar", null, admin));
        status(200, req("POST", c + "/preparar", valido, corretor));

        // 5. Administrador aprova. O email e um aviso: se falhar, a assinatura continua liberada
        doThrow(new IllegalStateException("sem SMTP")).when(email).enviarTexto(anyString(), anyString(), anyString());
        var aprovado = req("POST", c + "/aprovar", null, admin);
        status(200, aprovado);
        assertEquals("AGUARDANDO_ASSINATURA", json(aprovado, "$.status"));
        assertNull(JsonPath.read(aprovado.body(), "$.dataEnvio"));
        status(502, req("POST", c + "/enviar", null, admin));
        reset(email);
        status(200, req("POST", c + "/enviar", null, admin));
        verify(email).enviarTexto(eq("um@example.com"), contains("Contrato"), contains("/cliente/contratos"));
        assertNotNull(JsonPath.read(req("GET", c, null, um).body(), "$.dataEnvio"));
        status(409, req("POST", c + "/aprovar", null, admin));

        // 6. So o titular assina, com o nome do cadastro e a concordancia
        status(403, req("POST", c + "/assinar", "{\"nome\":\"Cliente Dois\",\"concordo\":true}", dois));
        status(403, req("POST", c + "/assinar", assinatura, corretor));
        status(400, req("POST", c + "/assinar", "{\"nome\":\"Outro\",\"concordo\":true}", um));
        status(400, req("POST", c + "/assinar", "{\"nome\":\"Cliente Um\",\"concordo\":false}", um));
        var assinado = req("POST", c + "/assinar", assinatura, um);
        status(200, assinado);
        assertEquals("ASSINADO", json(assinado, "$.status"));
        assertEquals("Vendido", statusImovel(id));
        var ofertaDois = req("GET", "/ofertas", null, dois);
        assertEquals("RECUSADA", json(ofertaDois, "$[0].status"));
        assertEquals("O imóvel foi negociado com outra proposta.", json(ofertaDois, "$[0].resposta"));
        status(409, req("PATCH", "/imoveis/" + id + "/status", "{\"status\":\"Disponível\"}", um));
        status(200, req("GET", c + "/pdf", null, corretor));

        // 7. Cancelar e exclusivo do administrador e devolve o imovel para a vitrine
        status(403, req("POST", c + "/cancelar", null, corretor));
        status(403, req("POST", c + "/cancelar", null, um));
        status(200, req("POST", c + "/cancelar", null, admin));
        status(409, req("POST", c + "/cancelar", null, admin));
        assertEquals("Disponível", statusImovel(id));
        oferta(id, "VENDA", 420000, dois);

        // Favoritos: unicos por cliente e isolados entre clientes
        status(200, req("POST", "/favoritos/" + id, null, um));
        status(200, req("POST", "/favoritos/" + id, null, um));
        status(403, req("POST", "/favoritos/" + id, null, corretor));
        assertEquals(1, lista(req("GET", "/favoritos", null, um)).size());
        assertEquals(0, lista(req("GET", "/favoritos", null, dois)).size());
        status(204, req("DELETE", "/favoritos/" + id, null, um));
    }

    @Test void aluguelExigePrazoEClienteDesisteOuRecebeResposta() throws Exception {
        String ana = usuario("Ana Cliente", "ana@example.com", UsuarioModel.Perfil.USER);
        String bia = usuario("Bia Cliente", "bia@example.com", UsuarioModel.Perfil.USER);
        String corretor = usuario("Caio Corretor", "caio@example.com", UsuarioModel.Perfil.CORRETOR);
        int id = imovel("Apartamento para alugar", null, 2000);
        status(409, req("POST", "/ofertas", novaOferta(id, "VENDA", 300000), ana));

        // Cliente desiste da propria oferta; ninguem mais pode cancela-la
        String o1 = oferta(id, "ALUGUEL", 1800, ana);
        status(403, req("POST", "/ofertas/" + o1 + "/cancelar", null, bia));
        status(403, req("POST", "/ofertas/" + o1 + "/cancelar", null, corretor));
        status(200, req("POST", "/ofertas/" + o1 + "/cancelar", null, ana));
        status(409, req("POST", "/ofertas/" + o1 + "/cancelar", null, ana));
        status(409, req("POST", "/ofertas/" + o1 + "/aceitar", null, corretor));

        // Corretor recusa com uma mensagem que o cliente ve
        String o2 = oferta(id, "ALUGUEL", 1500, ana);
        status(400, req("POST", "/ofertas/" + o2 + "/recusar", "{\"motivo\":\"" + "x".repeat(1001) + "\"}", corretor));
        status(200, req("POST", "/ofertas/" + o2 + "/recusar", "{\"motivo\":\"Valor abaixo do mínimo do proprietário.\"}", corretor));
        var recusadas = req("GET", "/ofertas?status=RECUSADA", null, ana);
        assertEquals("Valor abaixo do mínimo do proprietário.", json(recusadas, "$[0].resposta"));
        assertEquals("Disponível", statusImovel(id));

        // Aluguel exige prazo entre 1 e 120 meses
        String o3 = oferta(id, "ALUGUEL", 1900, ana);
        status(200, req("POST", "/ofertas/" + o3 + "/aceitar", null, corretor));
        String c = "/contratos/" + json(req("GET", "/contratos", null, ana), "$[0].id");
        status(400, req("POST", c + "/preparar", preparo(1900, hoje().plusDays(3), null, "Caução de três aluguéis."), corretor));
        status(400, req("POST", c + "/preparar", preparo(1900, hoje().plusDays(3), 121, "Caução de três aluguéis."), corretor));
        var preparado = req("POST", c + "/preparar", preparo(1950.5, hoje().plusDays(3), 30, "Caução de três aluguéis."), corretor);
        status(200, preparado);
        assertEquals("30", json(preparado, "$.prazoMeses"));

        // Assinatura so depois da aprovacao; o nome confere sem diferenciar maiusculas
        status(409, req("POST", c + "/assinar", "{\"nome\":\"Ana Cliente\",\"concordo\":true}", ana));
        status(200, req("POST", c + "/aprovar", null, login("admin@example.com")));
        status(200, req("POST", c + "/assinar", "{\"nome\":\"  ana cliente \",\"concordo\":true}", ana));
        assertEquals("Alugado", statusImovel(id));
    }

    @Test void aceiteConcorrenteReservaApenasUmaOferta() throws Exception {
        String a = usuario("Concorrente A", "concorrente-a@example.com", UsuarioModel.Perfil.USER);
        String b = usuario("Concorrente B", "concorrente-b@example.com", UsuarioModel.Perfil.USER);
        String corretor1 = usuario("Corretor Um", "corretor-um@example.com", UsuarioModel.Perfil.CORRETOR);
        String corretor2 = usuario("Corretor Dois", "corretor-dois@example.com", UsuarioModel.Perfil.CORRETOR);
        int id = imovel("Concorrência", 500000, null);
        String oa = oferta(id, "VENDA", 480000, a), ob = oferta(id, "VENDA", 490000, b);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var primeiro = pool.submit(() -> { start.await(); return req("POST", "/ofertas/" + oa + "/aceitar", null, corretor1).statusCode(); });
            var segundo = pool.submit(() -> { start.await(); return req("POST", "/ofertas/" + ob + "/aceitar", null, corretor2).statusCode(); });
            start.countDown();
            var resultados = new ArrayList<>(List.of(primeiro.get(), segundo.get()));
            Collections.sort(resultados);
            assertEquals(List.of(200, 409), resultados);
        }
        assertEquals("Reservado", statusImovel(id));
    }
}
