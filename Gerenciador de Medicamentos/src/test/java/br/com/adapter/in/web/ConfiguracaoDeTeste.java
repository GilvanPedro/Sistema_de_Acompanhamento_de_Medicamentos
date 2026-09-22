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
    br.com.adapter.in.web.metricas.BancoDeBanners bancoDeBanners() {
        return new BancoDeBannersEmMemoria();
    }

    @Bean
    br.com.adapter.in.web.metricas.MetricasDeAnuncios metricasDeAnuncios() {
        return new br.com.adapter.in.web.metricas.MetricasDeAnuncios() {
            private final java.util.Map<String, Linha> linhas = new java.util.LinkedHashMap<>();

            @Override
            public synchronized void somar(java.time.LocalDate dia, String anuncioId, String posicao, String perfil, long exibicoes, long cliques) {
                linhas.merge(dia + "|" + anuncioId + "|" + posicao + "|" + perfil, new Linha(dia, anuncioId, posicao, perfil, exibicoes, cliques),
                        (a, b) -> new Linha(a.dia(), a.anuncioId(), a.posicao(), a.perfil(), a.exibicoes() + b.exibicoes(), a.cliques() + b.cliques()));
            }

            @Override
            public synchronized void apagarDoBanner(String anuncioId) {
                linhas.values().removeIf(l -> l.anuncioId().equals(anuncioId));
            }

            @Override
            public synchronized java.util.List<Linha> doMes(java.time.YearMonth mes) {
                return linhas.values().stream().filter(l -> java.time.YearMonth.from(l.dia()).equals(mes)).toList();
            }

            @Override
            public synchronized java.util.List<Linha> entre(java.time.LocalDate inicio, java.time.LocalDate fim) {
                return linhas.values().stream().filter(l -> !l.dia().isBefore(inicio) && !l.dia().isAfter(fim)).toList();
            }
        };
    }

    @Bean
    br.com.adapter.in.web.painel.SenhaDoPainel senhaDoPainel() {
        return new br.com.adapter.in.web.painel.SenhaDoPainel("senha-do-painel-de-teste");
    }

    @Bean
    EmailsEnviados emailsEnviados() {
        return new EmailsEnviados();
    }

    @Bean
    br.com.adapter.in.web.recuperacao.RedefinicoesDeSenha redefinicoesDeSenha() {
        return new RedefinicoesEmMemoria();
    }

    @Bean
    br.com.adapter.in.web.exclusao.ExclusoesDeConta exclusoesDeConta() {
        return new ExclusoesEmMemoria();
    }

    /** Os e-mails saem na hora e na mesma linha de execução, para o teste poder olhar o que foi enviado. */
    @Bean
    @Qualifier("executorDeEmail")
    java.util.concurrent.Executor executorDeEmail() {
        return Runnable::run;
    }

    @Bean
    JwtService jwtService() {
        return new JwtService("segredo-somente-para-testes-com-mais-de-32-caracteres");
    }
}
