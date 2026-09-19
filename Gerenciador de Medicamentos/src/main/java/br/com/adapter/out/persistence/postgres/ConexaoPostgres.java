package br.com.adapter.out.persistence.postgres;

import java.net.URI;
import java.net.URISyntaxException;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import br.com.config.Ambiente;

/**
 * Cria o pool de conexões com o PostgreSQL a partir de variáveis de ambiente (nunca do código):
 * <ul>
 *   <li>{@code DATABASE_URL}: a string de conexão no formato da Neon,
 *       {@code postgresql://usuario:senha@host/banco?sslmode=require}; ou</li>
 *   <li>{@code DB_URL} (no formato {@code jdbc:postgresql://host/banco?sslmode=require}),
 *       {@code DB_USER} e {@code DB_PASSWORD}.</li>
 * </ul>
 * Se a variável não existir no ambiente, é lida do arquivo {@code .env} (ver {@link Ambiente}).
 * Sem nenhuma delas, o sistema continua usando os arquivos CSV.
 */
public final class ConexaoPostgres {

    private static HikariDataSource dataSource;

    private ConexaoPostgres() {
    }

    public static boolean configurada() {
        return !vazio(valor("DATABASE_URL")) || !vazio(valor("DB_URL"));
    }

    public static synchronized DataSource dataSource() {
        if (dataSource == null) {
            dataSource = new HikariDataSource(criarConfiguracao());
        }
        return dataSource;
    }

    private static HikariConfig criarConfiguracao() {
        HikariConfig config = new HikariConfig();
        String databaseUrl = valor("DATABASE_URL");

        if (!vazio(databaseUrl)) {
            preencherAPartirDaUrl(config, databaseUrl.trim());
        } else {
            config.setJdbcUrl(valor("DB_URL"));
            config.setUsername(valor("DB_USER"));
            config.setPassword(valor("DB_PASSWORD"));
        }

        config.setPoolName("cuidamed");
        // Plano gratuito: poucas conexões, e nenhuma ociosa (o Neon suspende o banco sem uso).
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setIdleTimeout(60_000);
        config.setMaxLifetime(300_000);
        // O primeiro acesso após a suspensão pode demorar um pouco.
        config.setConnectionTimeout(30_000);
        return config;
    }

    private static void preencherAPartirDaUrl(HikariConfig config, String url) {
        try {
            URI uri = new URI(url);
            String esquema = uri.getScheme();
            if (!"postgresql".equals(esquema) && !"postgres".equals(esquema)) {
                throw new IllegalStateException("DATABASE_URL deve começar com postgresql://");
            }

            StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
            if (uri.getPort() != -1) {
                jdbc.append(':').append(uri.getPort());
            }
            jdbc.append(uri.getRawPath());
            String consulta = removerParametro(uri.getRawQuery(), "channel_binding");
            if (!consulta.isEmpty()) {
                jdbc.append('?').append(consulta);
            }
            config.setJdbcUrl(jdbc.toString());

            String info = uri.getUserInfo();
            if (info != null) {
                int separador = info.indexOf(':');
                config.setUsername(separador < 0 ? info : info.substring(0, separador));
                if (separador >= 0) {
                    config.setPassword(info.substring(separador + 1));
                }
            }
        } catch (URISyntaxException e) {
            // Não repassa a causa: a mensagem do erro poderia conter a senha.
            throw new IllegalStateException("DATABASE_URL inválida. Confira o formato da string de conexão.");
        }
    }

    private static String removerParametro(String consulta, String nome) {
        if (consulta == null) {
            return "";
        }
        StringBuilder resultado = new StringBuilder();
        for (String parametro : consulta.split("&")) {
            if (parametro.isEmpty() || parametro.startsWith(nome + "=")) {
                continue;
            }
            if (resultado.length() > 0) {
                resultado.append('&');
            }
            resultado.append(parametro);
        }
        return resultado.toString();
    }

    private static String valor(String nome) {
        return Ambiente.valor(nome);
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
