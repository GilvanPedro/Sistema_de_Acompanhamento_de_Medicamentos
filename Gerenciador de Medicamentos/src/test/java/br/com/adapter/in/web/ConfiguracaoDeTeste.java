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
    br.com.adapter.in.web.idempotencia.ChavesDeIdempotencia chavesDeIdempotencia() {
        return new PortasEmMemoria.Chaves();
    }

    @Bean
    /** Faz as vezes de Dispositivos e de NotificadorPush (anota quem foi avisado). */
    PortasEmMemoria.Aparelhos aparelhos() {
        return new PortasEmMemoria.Aparelhos();
    }

    @Bean
    br.com.adapter.in.web.push.AvisosDeAtraso avisosDeAtraso() {
        java.util.Set<String> vistos = new java.util.HashSet<>();
        return (medicamentoId, dia) -> vistos.add(medicamentoId + "|" + dia);
    }

    @Bean
    br.com.adapter.in.web.push.SegredoDoAgendador segredoDoAgendador() {
        return new br.com.adapter.in.web.push.SegredoDoAgendador("segredo-do-agendador-de-teste");
    }

    @Bean
    br.com.adapter.in.web.privacidade.Consentimentos consentimentos() {
        return new br.com.adapter.in.web.privacidade.Consentimentos() {
            private final java.util.List<Object[]> aceites = new java.util.ArrayList<>();

            @Override
            public synchronized void registrar(int usuarioId, String versao) {
                if (aceites.stream().noneMatch(a -> (int) a[0] == usuarioId && a[1].equals(versao))) {
                    aceites.add(new Object[]{usuarioId, versao, java.time.OffsetDateTime.now()});
                }
            }

            @Override
            public synchronized java.util.Optional<String> versaoAceita(int usuarioId) {
                var lista = todos(usuarioId);
                return lista.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(lista.get(lista.size() - 1).versao());
            }

            @Override
            public synchronized java.util.List<Aceite> todos(int usuarioId) {
                return aceites.stream().filter(a -> (int) a[0] == usuarioId)
                        .map(a -> new Aceite((String) a[1], (java.time.OffsetDateTime) a[2])).toList();
            }

            @Override
            public synchronized void removerDe(int usuarioId) {
                aceites.removeIf(a -> (int) a[0] == usuarioId);
            }
        };
    }

    @Bean
    JwtService jwtService() {
        return new JwtService("segredo-somente-para-testes-com-mais-de-32-caracteres");
    }
}
