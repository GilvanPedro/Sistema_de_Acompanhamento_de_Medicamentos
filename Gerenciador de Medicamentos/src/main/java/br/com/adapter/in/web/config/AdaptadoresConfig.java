package br.com.adapter.in.web.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import br.com.adapter.in.web.auth.JwtService;
import br.com.adapter.in.web.auth.RefreshTokenJdbcStore;
import br.com.adapter.in.web.auth.RefreshTokenStore;
import br.com.adapter.out.id.GerarIdPostgresAdapter;
import br.com.adapter.out.persistence.postgres.ConexaoPostgres;
import br.com.adapter.out.persistence.postgres.HistoricoPostgresAdapter;
import br.com.adapter.out.persistence.postgres.MedicamentoPostgresAdapter;
import br.com.adapter.out.persistence.postgres.UsuarioPostgresAdapter;
import br.com.config.Ambiente;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.port.out.SalvarUsuarioPort;

/** Adaptadores reais (PostgreSQL). Nos testes (perfil "test") entram versões em memória no lugar. */
@Configuration
@Profile("!test")
class AdaptadoresConfig {

    @Bean
    DataSource dataSource() {
        return ConexaoPostgres.dataSource();
    }

    @Bean
    SalvarUsuarioPort usuarioPort(DataSource dataSource) {
        return new UsuarioPostgresAdapter(dataSource);
    }

    @Bean
    SalvarMedicamentoPort medicamentoPort(DataSource dataSource) {
        return new MedicamentoPostgresAdapter(dataSource);
    }

    @Bean
    SalvarHistoricoPort historicoPort(DataSource dataSource) {
        return new HistoricoPostgresAdapter(dataSource);
    }

    @Bean
    @Qualifier("gerarIdUsuario")
    GerarIdPort gerarIdUsuario(DataSource dataSource) {
        return new GerarIdPostgresAdapter(dataSource, "usuario");
    }

    @Bean
    @Qualifier("gerarIdMedicamento")
    GerarIdPort gerarIdMedicamento(DataSource dataSource) {
        return new GerarIdPostgresAdapter(dataSource, "medicamento");
    }

    @Bean
    @Qualifier("gerarIdHistorico")
    GerarIdPort gerarIdHistorico(DataSource dataSource) {
        return new GerarIdPostgresAdapter(dataSource, "historico");
    }

    @Bean
    RefreshTokenStore refreshTokenStore(DataSource dataSource) {
        return new RefreshTokenJdbcStore(dataSource);
    }

    @Bean
    JwtService jwtService() {
        return new JwtService(Ambiente.valor("JWT_SECRET"));
    }
}
