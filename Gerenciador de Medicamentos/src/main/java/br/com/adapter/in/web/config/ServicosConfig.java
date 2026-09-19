package br.com.adapter.in.web.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.adapter.in.web.auth.JwtService;
import br.com.adapter.in.web.auth.RefreshTokenStore;
import br.com.adapter.in.web.auth.TokenService;
import br.com.adapter.out.security.BcryptSenhaAdapter;
import br.com.application.service.BuscarHistoricoPorIdosoService;
import br.com.application.service.CriarVinculoService;
import br.com.application.service.EditarMedicamentoService;
import br.com.application.service.EditarUsuarioService;
import br.com.application.service.ExcluirMedicamentoService;
import br.com.application.service.ExcluirUsuarioService;
import br.com.application.service.GerenciarVinculoService;
import br.com.application.service.RealizarLoginService;
import br.com.application.service.RegistrarMedicamentoService;
import br.com.application.service.RegistrarTomadaService;
import br.com.application.service.RegistrarUsuarioService;
import br.com.application.service.VerificarNotificacoesIdosoService;
import br.com.domain.port.out.CriptografarSenhaPort;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.port.out.SalvarUsuarioPort;

/** Monta os serviços da aplicação a partir das portas (as mesmas classes que o terminal e a GUI usam). */
@Configuration
class ServicosConfig {

    @Bean
    CriptografarSenhaPort criptografarSenhaPort() {
        return new BcryptSenhaAdapter();
    }

    @Bean
    RealizarLoginService realizarLoginService(SalvarUsuarioPort usuarios, CriptografarSenhaPort cripto) {
        return new RealizarLoginService(usuarios, cripto);
    }

    @Bean
    RegistrarUsuarioService registrarUsuarioService(@Qualifier("gerarIdUsuario") GerarIdPort gerarId,
                                                    SalvarUsuarioPort usuarios, CriptografarSenhaPort cripto) {
        return new RegistrarUsuarioService(gerarId, usuarios, cripto);
    }

    @Bean
    EditarUsuarioService editarUsuarioService(SalvarUsuarioPort usuarios, CriptografarSenhaPort cripto) {
        return new EditarUsuarioService(usuarios, cripto);
    }

    @Bean
    ExcluirUsuarioService excluirUsuarioService(SalvarUsuarioPort usuarios) {
        return new ExcluirUsuarioService(usuarios);
    }

    @Bean
    RegistrarMedicamentoService registrarMedicamentoService(@Qualifier("gerarIdMedicamento") GerarIdPort gerarId,
                                                            SalvarMedicamentoPort medicamentos, SalvarUsuarioPort usuarios) {
        return new RegistrarMedicamentoService(gerarId, medicamentos, usuarios);
    }

    @Bean
    EditarMedicamentoService editarMedicamentoService(SalvarMedicamentoPort medicamentos) {
        return new EditarMedicamentoService(medicamentos);
    }

    @Bean
    ExcluirMedicamentoService excluirMedicamentoService(SalvarMedicamentoPort medicamentos) {
        return new ExcluirMedicamentoService(medicamentos);
    }

    @Bean
    RegistrarTomadaService registrarTomadaService(@Qualifier("gerarIdHistorico") GerarIdPort gerarId,
                                                  SalvarHistoricoPort historico) {
        return new RegistrarTomadaService(gerarId, historico);
    }

    @Bean
    BuscarHistoricoPorIdosoService buscarHistoricoPorIdosoService(SalvarUsuarioPort usuarios,
                                                                  SalvarMedicamentoPort medicamentos,
                                                                  SalvarHistoricoPort historico) {
        return new BuscarHistoricoPorIdosoService(usuarios, medicamentos, historico);
    }

    @Bean
    br.com.adapter.in.web.push.VerificadorDeAtrasos verificadorDeAtrasos(SalvarUsuarioPort usuarios,
                                                                          VerificarNotificacoesIdosoService notificacoes,
                                                                          br.com.adapter.in.web.push.AvisosDeAtraso jaAvisados,
                                                                          br.com.adapter.in.web.push.NotificadorPush push) {
        return new br.com.adapter.in.web.push.VerificadorDeAtrasos(usuarios, notificacoes, jaAvisados, push,
                java.time.Clock.systemDefaultZone());
    }

    @Bean
    VerificarNotificacoesIdosoService verificarNotificacoesIdosoService(SalvarMedicamentoPort medicamentos,
                                                                        SalvarHistoricoPort historico) {
        return new VerificarNotificacoesIdosoService(medicamentos, historico);
    }

    @Bean
    CriarVinculoService criarVinculoService(SalvarUsuarioPort usuarios) {
        return new CriarVinculoService(usuarios);
    }

    @Bean
    GerenciarVinculoService gerenciarVinculoService(SalvarUsuarioPort usuarios) {
        return new GerenciarVinculoService(usuarios);
    }

    @Bean
    TokenService tokenService(JwtService jwt, RefreshTokenStore store, SalvarUsuarioPort usuarios) {
        return new TokenService(jwt, store, usuarios);
    }
}
