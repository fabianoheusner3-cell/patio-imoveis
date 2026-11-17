package com.example.demo;

import com.example.demo.config.AjusteEnumsH2;
import com.example.demo.models.*;
import com.example.demo.repositories.*;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Banco criado antes da Aula 3: o ajuste aceita CORRETOR e as etapas novas sem perder os dados. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:ajuste-enums",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"})
class AjusteEnumsH2Test {
    @Autowired AjusteEnumsH2 ajuste;
    @Autowired DataSource dataSource;
    @Autowired UsuarioRepository usuarios;
    @Autowired ImovelRepository imoveis;
    @Autowired OfertaRepository ofertas;
    @Autowired ContratoRepository contratos;

    String tipo(JdbcTemplate jdbc, String tabela, String coluna) {
        return jdbc.queryForObject("SELECT DATA_TYPE_SQL(TABLE_SCHEMA, TABLE_NAME, 'TABLE', DTD_IDENTIFIER) "
            + "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ? AND COLUMN_NAME = ?",
            String.class, tabela, coluna);
    }

    @Test void ampliaOsEnumsDoBancoAntigoSemPerderDados() throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        // Contrato do fluxo antigo: nascia "aguardando assinatura", ainda sem aprovacao
        var u = new UsuarioModel();
        u.setNome("Cliente antigo"); u.setEmail("antigo@example.com"); u.setSenha("x"); u.setPerfil(UsuarioModel.Perfil.USER);
        u = usuarios.save(u);
        var i = new ImovelModel();
        i.setTitulo("Imóvel antigo"); i.setStatus("Reservado"); i.setPrecoVenda(new BigDecimal("100000"));
        i = imoveis.save(i);
        var o = new OfertaModel();
        o.setImovel(i); o.setUsuario(u); o.setTipo(OfertaModel.Tipo.VENDA);
        o.setValorProposto(new BigDecimal("90000")); o.setStatus(OfertaModel.Status.ACEITA);
        o = ofertas.save(o);
        var c = new ContratoModel();
        c.setOferta(o); c.setStatus(ContratoModel.Status.AGUARDANDO_ASSINATURA);
        c = contratos.save(c);

        // Colunas como o Hibernate criou antes da Aula 3
        jdbc.execute("ALTER TABLE USUARIOS ALTER COLUMN PERFIL SET DATA TYPE ENUM('ADMIN', 'USER')");
        jdbc.execute("ALTER TABLE CONTRATOS ALTER COLUMN STATUS SET DATA TYPE ENUM('AGUARDANDO_ASSINATURA', 'ASSINADO', 'CANCELADO')");
        assertThrows(DataAccessException.class,
            () -> jdbc.update("UPDATE USUARIOS SET PERFIL = 'CORRETOR' WHERE EMAIL = 'antigo@example.com'"));

        ajuste.run();

        assertEquals("ENUM('ADMIN', 'USER', 'CORRETOR')", tipo(jdbc, "USUARIOS", "PERFIL"));
        assertEquals("ENUM('AGUARDANDO_ASSINATURA', 'ASSINADO', 'CANCELADO', 'EM_PREPARACAO', 'AGUARDANDO_APROVACAO')",
            tipo(jdbc, "CONTRATOS", "STATUS"));
        assertEquals(ContratoModel.Status.AGUARDANDO_APROVACAO, contratos.findById(c.getId()).orElseThrow().getStatus());
        assertEquals(UsuarioModel.Perfil.USER, usuarios.findById(u.getId()).orElseThrow().getPerfil());
        assertEquals(1, jdbc.update("UPDATE USUARIOS SET PERFIL = 'CORRETOR' WHERE EMAIL = 'antigo@example.com'"));

        // Rodar de novo (proxima inicializacao) nao muda nada
        ajuste.run();
        assertEquals("ENUM('ADMIN', 'USER', 'CORRETOR')", tipo(jdbc, "USUARIOS", "PERFIL"));
    }
}
