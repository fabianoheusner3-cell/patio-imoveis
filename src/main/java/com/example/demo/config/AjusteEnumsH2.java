package com.example.demo.config;

import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.example.demo.models.ContratoModel;
import com.example.demo.models.OfertaModel;
import com.example.demo.models.UsuarioModel;

/**
 * O Hibernate cria as colunas de enum do H2 como ENUM('ADMIN', 'USER') e o ddl-auto=update
 * nao altera colunas que ja existem. Sem este ajuste, um banco criado antes da Aula 3
 * recusaria os valores novos: o perfil CORRETOR e as etapas novas do contrato.
 * Roda na inicializacao, somente no H2, e apenas acrescenta os valores que faltam
 * (os valores antigos continuam na mesma ordem, entao nenhum dado muda).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AjusteEnumsH2 implements CommandLineRunner {
    private static final Pattern VALOR = Pattern.compile("'([^']*)'");
    private final JdbcTemplate jdbc;

    public AjusteEnumsH2(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override public void run(String... args) {
        String banco = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        if (!"H2".equalsIgnoreCase(banco)) return;
        ampliar("USUARIOS", "PERFIL", UsuarioModel.Perfil.class);
        ampliar("OFERTAS", "STATUS", OfertaModel.Status.class);
        if (ampliar("CONTRATOS", "STATUS", ContratoModel.Status.class)) {
            // No fluxo antigo o contrato nascia "aguardando assinatura" antes da aprovacao.
            // Esses contratos passam para a etapa em que o administrador aprova.
            jdbc.update("UPDATE CONTRATOS SET STATUS = 'AGUARDANDO_APROVACAO' "
                + "WHERE STATUS = 'AGUARDANDO_ASSINATURA' AND DATA_APROVACAO IS NULL");
        }
    }

    /** Devolve true se a coluna existe; acrescenta ao ENUM os valores do Java que faltarem. */
    private boolean ampliar(String tabela, String coluna, Class<? extends Enum<?>> tipo) {
        var definicao = jdbc.queryForList("""
            SELECT DATA_TYPE_SQL(TABLE_SCHEMA, TABLE_NAME, 'TABLE', DTD_IDENTIFIER)
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ? AND COLUMN_NAME = ?""", String.class, tabela, coluna);
        if (definicao.isEmpty() || definicao.get(0) == null) return false;
        if (!definicao.get(0).toUpperCase().startsWith("ENUM")) return true;
        var valores = new LinkedHashSet<String>();
        var encontrados = VALOR.matcher(definicao.get(0));
        while (encontrados.find()) valores.add(encontrados.group(1));
        boolean faltava = false;
        for (var constante : tipo.getEnumConstants()) faltava |= valores.add(constante.name());
        if (faltava) {
            var lista = String.join(", ", valores.stream().map(v -> "'" + v + "'").toList());
            jdbc.execute("ALTER TABLE " + tabela + " ALTER COLUMN " + coluna + " SET DATA TYPE ENUM(" + lista + ")");
        }
        return true;
    }
}
