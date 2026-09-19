package br.com.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.NotificacaoMedicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.model.TipoNotificacao;
import br.com.domain.model.Usuario;
import br.com.domain.port.out.NotificarPort;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.port.out.SalvarUsuarioPort;

/** Horários fixos: 21/09/2026 é uma segunda-feira, e 22/09/2026 uma terça. */
class AvisosDeMedicamentoTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final Idoso idoso = new Idoso(1, "Dona Maria", "maria@teste.com", "hash");
    private final SalvarMedicamentoPort medicamentos = mock(SalvarMedicamentoPort.class);
    private final SalvarHistoricoPort historico = mock(SalvarHistoricoPort.class);
    private final SalvarUsuarioPort usuarios = mock(SalvarUsuarioPort.class);
    private final NotificarPort notificar = mock(NotificarPort.class);
    private final List<HistoricoMedicamento> tomadas = new ArrayList<>();

    private Medicamento remedioDeSegunda(int hora, int minuto) {
        Medicamento m = new Medicamento(10, idoso.getId(), "Losartana", LocalTime.of(hora, minuto), DayOfWeek.MONDAY, TipoMedicamento.COMPRIMIDO);
        when(medicamentos.listarPorIdoso(idoso.getId())).thenReturn(List.of(m));
        when(medicamentos.listarTodos()).thenReturn(List.of(m));
        return m;
    }

    @BeforeEach
    void preparar() {
        when(historico.listarHistoricoPorIdoso(anyInt(), any(), any())).thenAnswer(i -> new ArrayList<>(tomadas));
        when(historico.listarTodos(any(), any())).thenAnswer(i -> new ArrayList<>(tomadas));
        when(usuarios.listarTodos()).thenReturn(List.<Usuario>of(idoso));
    }

    private static Clock em(int dia, int hora, int minuto) {
        return Clock.fixed(LocalDateTime.of(2026, 9, dia, hora, minuto).atZone(ZONA).toInstant(), ZONA);
    }

    private List<NotificacaoMedicamento> avisos(Clock relogio) {
        return new VerificarNotificacoesIdosoService(medicamentos, historico, relogio).verificarNotificacoes(idoso);
    }

    private VerificarAtrasoMedicamentoService atraso(Clock relogio) {
        return new VerificarAtrasoMedicamentoService(usuarios, medicamentos, historico, notificar, relogio);
    }

    private void tomouEm(Medicamento m, int dia, int hora, int minuto) {
        tomadas.add(new HistoricoMedicamento(tomadas.size() + 1, m, idoso, LocalDateTime.of(2026, 9, dia, hora, minuto), true));
    }

    // ---- avisos na tela

    @Test
    void lembreteNaToleranciaEsquecidoDepoisETomadoQuandoRegistrado() {
        Medicamento m = remedioDeSegunda(8, 0);
        assertTrue(avisos(em(21, 7, 59)).isEmpty(), "antes do horário não há aviso");
        assertEquals(TipoNotificacao.LEMBRETE, avisos(em(21, 8, 5)).get(0).getTipo());
        assertEquals(TipoNotificacao.ESQUECIDO, avisos(em(21, 8, 30)).get(0).getTipo());
        assertEquals(TipoNotificacao.ESQUECIDO, avisos(em(21, 23, 0)).get(0).getTipo());

        tomouEm(m, 21, 8, 20);
        assertEquals(TipoNotificacao.TOMADO, avisos(em(21, 9, 0)).get(0).getTipo());
    }

    @Test
    void remedioDeOutroDiaDaSemanaNaoGeraAviso() {
        remedioDeSegunda(8, 0);
        assertTrue(avisos(em(22, 8, 30)).isEmpty());
    }

    @Test
    void remedioDas2350AindaContaComoEsquecidoDepoisDaMeiaNoite() {
        Medicamento m = remedioDeSegunda(23, 50);
        assertEquals(TipoNotificacao.ESQUECIDO, avisos(em(22, 0, 5)).get(0).getTipo());
        assertEquals(TipoNotificacao.ESQUECIDO, avisos(em(22, 2, 30)).get(0).getTipo());
        assertTrue(avisos(em(22, 3, 30)).isEmpty(), "passado o prazo de 3 horas, some");

        tomouEm(m, 22, 0, 20); // tomou depois da meia-noite: cobre o horário de ontem
        assertTrue(avisos(em(22, 1, 0)).isEmpty(), "o de ontem, se foi tomado, não gera aviso nenhum");
    }

    @Test
    void lembreteAposAMeiaNoiteParaRemedioDas2358() {
        remedioDeSegunda(23, 58);
        assertEquals(TipoNotificacao.LEMBRETE, avisos(em(22, 0, 3)).get(0).getTipo());
    }

    // ---- aviso ao idoso e ao familiar (agendador)

    @Test
    void oAgendadorAvisaUmaVezSoEmVezDeUmAvisoPorMinuto() {
        Medicamento m = remedioDeSegunda(8, 0);

        atraso(em(21, 8, 2)).verificarAtrasos();
        VerificarAtrasoMedicamentoService servico = atraso(em(21, 8, 2));
        servico.verificarAtrasos();
        servico.verificarAtrasos();
        verify(notificar, times(2)).lembrarIdoso(idoso, m); // um por serviço novo; o mesmo serviço não repete
        verify(notificar, never()).avisarRemedioEsquecido(idoso, m);
    }

    @Test
    void oFamiliarSoRecebeUmAvisoDeEsquecidoPorHorarioPrevisto() {
        Medicamento m = remedioDeSegunda(8, 0);
        // mesmo serviço rodando minuto a minuto, como o agendador faz
        List<Clock> minutos = new ArrayList<>();
        for (int minuto = 11; minuto <= 40; minuto++) {
            minutos.add(em(21, 8, minuto));
        }
        MutableClock relogio = new MutableClock(minutos.get(0));
        VerificarAtrasoMedicamentoService servico = new VerificarAtrasoMedicamentoService(usuarios, medicamentos, historico, notificar, relogio);
        for (Clock c : minutos) {
            relogio.definir(c);
            servico.verificarAtrasos();
        }
        verify(notificar, times(1)).avisarRemedioEsquecido(idoso, m);
        verify(notificar, never()).lembrarIdoso(idoso, m);

        // na semana seguinte é outro horário previsto, e o aviso volta a valer
        relogio.definir(em(28, 8, 15));
        servico.verificarAtrasos();
        verify(notificar, times(2)).avisarRemedioEsquecido(idoso, m);
    }

    @Test
    void semAvisoSeOIdosoJaTomou() {
        Medicamento m = remedioDeSegunda(8, 0);
        tomouEm(m, 21, 8, 5);
        atraso(em(21, 8, 30)).verificarAtrasos();
        verify(notificar, never()).avisarRemedioEsquecido(any(), any());
        verify(notificar, never()).lembrarIdoso(any(), any());
    }

    /** Relógio que dá para mudar, para simular o agendador rodando de minuto em minuto. */
    private static final class MutableClock extends Clock {
        private Clock atual;

        MutableClock(Clock inicial) {
            this.atual = inicial;
        }

        void definir(Clock novo) {
            this.atual = novo;
        }

        @Override
        public ZoneId getZone() {
            return atual.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return atual.withZone(zone);
        }

        @Override
        public java.time.Instant instant() {
            return atual.instant();
        }
    }
}
