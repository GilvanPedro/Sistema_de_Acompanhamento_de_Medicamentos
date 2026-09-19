package br.com.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.SalvarHistoricoPort;

/** Regras da hora da tomada. "Agora" fixo: 21/09/2026, 10:00. */
class RegistrarTomadaServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 9, 21, 10, 0);

    private final Idoso idoso = new Idoso(1, "Dona Maria", "maria@teste.com", "hash");
    private final Medicamento remedio = new Medicamento(5, 1, "Losartana", LocalTime.of(8, 0), DayOfWeek.MONDAY, TipoMedicamento.COMPRIMIDO);
    private final SalvarHistoricoPort historico = mock(SalvarHistoricoPort.class);
    private final List<HistoricoMedicamento> salvas = new ArrayList<>();
    private final RegistrarTomadaService servico;

    RegistrarTomadaServiceTest() {
        GerarIdPort ids = mock(GerarIdPort.class);
        when(ids.proximoId()).thenReturn(1);
        when(historico.listarHistoricoPorIdoso(anyInt(), any(), any())).thenAnswer(i -> new ArrayList<>(salvas));
        org.mockito.Mockito.doAnswer(i -> salvas.add(i.getArgument(0))).when(historico).salvar(any());
        servico = new RegistrarTomadaService(ids, historico, Clock.fixed(AGORA.atZone(ZONA).toInstant(), ZONA));
    }

    @Test
    void usaAHoraInformadaEnaoAHoraQueOPedidoChegou() {
        HistoricoMedicamento h = servico.registrarTomada(idoso, remedio, true, AGORA.minusHours(2));
        assertEquals(AGORA.minusHours(2), h.getDataHoraTomada());
    }

    @Test
    void semHoraInformadaUsaAgora() {
        assertEquals(AGORA, servico.registrarTomada(idoso, remedio, true).getDataHoraTomada());
    }

    @Test
    void recusaHoraNoFuturoMasAceitaUmPoucoDeRelogioAdiantado() {
        assertThrows(DadosInvalidosException.class, () -> servico.registrarTomada(idoso, remedio, true, AGORA.plusMinutes(6)));
        assertEquals(AGORA.plusMinutes(4), servico.registrarTomada(idoso, remedio, true, AGORA.plusMinutes(4)).getDataHoraTomada());
    }

    @Test
    void recusaTomadaComMaisDeSeteDias() {
        assertThrows(DadosInvalidosException.class, () -> servico.registrarTomada(idoso, remedio, true, AGORA.minusDays(7).minusMinutes(1)));
        verify(historico, never()).salvar(any());
    }

    @Test
    void umaTomadaPorDiaMasDiasDiferentesPodem() {
        servico.registrarTomada(idoso, remedio, true, AGORA.minusHours(3));
        assertThrows(DadosInvalidosException.class, () -> servico.registrarTomada(idoso, remedio, true, AGORA.minusHours(1)));
        // outro dia (ontem) é permitido, e "não tomou" também
        servico.registrarTomada(idoso, remedio, true, AGORA.minusDays(1));
        servico.registrarTomada(idoso, remedio, false, AGORA.minusHours(1));
        assertEquals(3, salvas.size());
    }
}
