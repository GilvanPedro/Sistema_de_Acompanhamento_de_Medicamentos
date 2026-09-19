package br.com.adapter.in.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import br.com.adapter.in.web.auth.JwtService;
import br.com.adapter.in.web.auth.RefreshTokenStore;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.port.out.SalvarUsuarioPort;

@TestConfiguration
@Profile("test")
class ConfiguracaoDeTeste {

    @Bean
    SalvarUsuarioPort usuarioPort() {
        return new PortasEmMemoria.Usuarios();
    }

    @Bean
    SalvarMedicamentoPort medicamentoPort() {
        return new PortasEmMemoria.Medicamentos();
    }

    @Bean
    SalvarHistoricoPort historicoPort() {
        return new PortasEmMemoria.Historico();
    }

    @Bean
    @Qualifier("gerarIdUsuario")
    GerarIdPort gerarIdUsuario() {
        return new PortasEmMemoria.Ids();
    }

    @Bean
    @Qualifier("gerarIdMedicamento")
    GerarIdPort gerarIdMedicamento() {
        return new PortasEmMemoria.Ids();
    }

    @Bean
    @Qualifier("gerarIdHistorico")
    GerarIdPort gerarIdHistorico() {
        return new PortasEmMemoria.Ids();
    }

    @Bean
    RefreshTokenStore refreshTokenStore() {
        return new PortasEmMemoria.Tokens();
    }

    @Bean
    JwtService jwtService() {
        return new JwtService("segredo-somente-para-testes-com-mais-de-32-caracteres");
    }
}
