package br.com.adapter.in.web.config;

import java.util.logging.Logger;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import br.com.adapter.in.web.auth.JwtService;
import br.com.adapter.in.web.auth.RefreshTokenJdbcStore;
import br.com.adapter.in.web.auth.RefreshTokenStore;
import br.com.adapter.in.web.idempotencia.ChavesDeIdempotencia;
import br.com.adapter.in.web.idempotencia.ChavesDeIdempotenciaJdbc;
import br.com.adapter.in.web.push.AvisosDeAtraso;
import br.com.adapter.in.web.push.AvisosDeAtrasoJdbc;
import br.com.adapter.in.web.push.Dispositivos;
import br.com.adapter.in.web.push.DispositivosJdbc;
import br.com.adapter.in.web.push.FcmNotificadorPush;
import br.com.adapter.in.web.push.NotificadorPush;
import br.com.adapter.in.web.push.NotificadorPushDesligado;
import br.com.adapter.in.web.push.SegredoDoAgendador;
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
    ChavesDeIdempotencia chavesDeIdempotencia(DataSource dataSource) {
        return new ChavesDeIdempotenciaJdbc(dataSource);
    }

    @Bean
    AvisosDeAtraso avisosDeAtraso(DataSource dataSource) {
        return new AvisosDeAtrasoJdbc(dataSource);
    }

    /** CRON_SEGREDO: segredo do agendador externo (pelo menos 16 caracteres). Sem ele, a rota do agendador fica desligada. */
    @Bean
    SegredoDoAgendador segredoDoAgendador() {
        return new SegredoDoAgendador(Ambiente.valor("CRON_SEGREDO"));
    }

    @Bean
    Dispositivos dispositivos(DataSource dataSource) {
        return new DispositivosJdbc(dataSource);
    }

    /** Push só liga se FIREBASE_CREDENTIALS (ou FIREBASE_CREDENCIAIS; JSON da conta de serviço) estiver definida; senão, nada é enviado. */
    @Bean
    NotificadorPush notificadorPush(Dispositivos dispositivos) {
        String credenciais = Ambiente.valor("FIREBASE_CREDENTIALS", Ambiente.valor("FIREBASE_CREDENCIAIS"));
        Logger log = Logger.getLogger(AdaptadoresConfig.class.getName());
        if (credenciais == null) {
            log.info("Push: DESLIGADO (variável FIREBASE_CREDENTIALS não definida)");
            return new NotificadorPushDesligado();
        }
        log.info("Push: LIGADO (Firebase)");
        return new FcmNotificadorPush(dispositivos, credenciais);
    }

    @Bean
    JwtService jwtService() {
        return new JwtService(Ambiente.valor("JWT_SECRET"));
    }
}
