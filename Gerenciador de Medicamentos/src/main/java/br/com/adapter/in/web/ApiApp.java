package br.com.adapter.in.web;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import br.com.adapter.out.persistence.postgres.ConexaoPostgres;
import br.com.config.Ambiente;

/**
 * Ponto de entrada da API REST (ADR-0047). Usa sempre o PostgreSQL: sem {@code DATABASE_URL} ela não sobe.
 */
@SpringBootApplication
public class ApiApp {

    public static void main(String[] args) {
        // O servidor (ex.: Render) roda em UTC; os horários dos remédios são os do relógio de quem toma.
        TimeZone.setDefault(TimeZone.getTimeZone(Ambiente.valor("APP_TIMEZONE", "America/Sao_Paulo")));

        if (!ConexaoPostgres.configurada()) {
            System.err.println("[CuidaMed API] Defina DATABASE_URL (ou DB_URL, DB_USER e DB_PASSWORD) para iniciar a API.");
            System.exit(1);
        }
        SpringApplication.run(ApiApp.class, args);
    }
}
