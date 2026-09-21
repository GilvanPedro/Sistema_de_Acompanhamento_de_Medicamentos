package br.com.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.util.FaltasDeMedicamento;

class FaltasDeMedicamentoTest {

    private static final Idoso IDOSO = new Idoso(1, "Dona Neusa", "n@x.com", "x", List.of());
    // 2026-09-21 é uma segunda-feira
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 9, 21);

    private static Medicamento remedio(DayOfWeek dia, String hora, LocalDateTime vigenteDesde) {
        return new Medicamento(7, 1, "Enalapril", LocalTime.parse(hora), dia, TipoMedicamento.COMPRIMIDO, vigenteDesde);
    }

    private static HistoricoMedicamento tomada(Medicamento m, LocalDateTime quando) {
        return new HistoricoMedicamento(50, m, IDOSO, quando, true);
    }

    @Test
    void horarioSemTomadaViraFaltaDepoisQueOFimDoDiaPassa() {
        Medicamento m = remedio(DayOfWeek.MONDAY, "08:00", SEGUNDA.minusDays(30).atStartOfDay());
        // na segunda à noite ainda não é falta: o dia não acabou
        assertTrue(FaltasDeMedicamento.calcular(IDOSO, List.of(m), List.of(), SEGUNDA.atTime(23, 0), null).stream()
                .noneMatch(f -> f.getDataHoraTomada().toLocalDate().equals(SEGUNDA)));
        // na terça às 4h (passou a folga da madrugada), a segunda vira falta, no horário previsto
        List<HistoricoMedicamento> faltas = FaltasDeMedicamento.calcular(IDOSO, List.of(m), List.of(), SEGUNDA.plusDays(1).atTime(4, 0), null);
        HistoricoMedicamento hoje = faltas.stream().filter(f -> f.getDataHoraTomada().toLocalDate().equals(SEGUNDA)).findFirst().orElseThrow();
        assertEquals(SEGUNDA.atTime(8, 0), hoje.getDataHoraTomada());
        assertFalse(hoje.isFoiTomado());
        assertTrue(hoje.getId() < 0, "a falta não existe no banco: id negativo");
    }

    @Test
    void naMadrugadaSeguinteAindaHaFolgaParaQuemTomaTarde() {
        Medicamento m = remedio(DayOfWeek.MONDAY, "23:50", SEGUNDA.minusDays(3).atStartOfDay());
        assertTrue(FaltasDeMedicamento.calcular(IDOSO, List.of(m), List.of(), SEGUNDA.plusDays(1).atTime(1, 0), SEGUNDA).isEmpty());
    }

    @Test
    void tomadaNoDiaCobreOHorarioEAFaltaSome() {
        Medicamento m = remedio(DayOfWeek.MONDAY, "08:00", SEGUNDA.minusDays(30).atStartOfDay());
        List<HistoricoMedicamento> registros = List.of(tomada(m, SEGUNDA.atTime(9, 15)));
        assertTrue(FaltasDeMedicamento.calcular(IDOSO, List.of(m), registros, SEGUNDA.plusDays(2).atStartOfDay(), SEGUNDA).isEmpty());
    }

    @Test
    void naoHaFaltaAntesDoRemedioTerComecadoAValer() {
        Medicamento m = remedio(DayOfWeek.MONDAY, "08:00", SEGUNDA.atTime(10, 0)); // cadastrado depois do horário de hoje
        assertTrue(FaltasDeMedicamento.calcular(IDOSO, List.of(m), List.of(), SEGUNDA.plusDays(3).atStartOfDay(), null).isEmpty(),
                "o horário de hoje já tinha passado quando o remédio foi cadastrado");
        Medicamento semData = remedio(DayOfWeek.MONDAY, "08:00", null);
        assertTrue(FaltasDeMedicamento.calcular(IDOSO, List.of(semData), List.of(), SEGUNDA.plusDays(30).atStartOfDay(), null).isEmpty());
    }

    @Test
    void cadaSemanaSemTomadaContaUmaFaltaEOPeriodoLimitaAsDatas() {
        Medicamento m = remedio(DayOfWeek.MONDAY, "08:00", SEGUNDA.minusWeeks(10).atStartOfDay());
        LocalDateTime agora = SEGUNDA.plusDays(1).atTime(12, 0);
        assertEquals(11, FaltasDeMedicamento.calcular(IDOSO, List.of(m), List.of(), agora, null).size());
        assertEquals(3, FaltasDeMedicamento.calcular(IDOSO, List.of(m), List.of(), agora, SEGUNDA.minusWeeks(2)).size());
    }

    @Test
    void idsDasFaltasSaoDiferentesEEstaveis() {
        Medicamento m = remedio(DayOfWeek.MONDAY, "08:00", SEGUNDA.minusWeeks(4).atStartOfDay());
        LocalDateTime agora = SEGUNDA.plusDays(1).atTime(12, 0);
        List<HistoricoMedicamento> a = FaltasDeMedicamento.calcular(IDOSO, List.of(m), List.of(), agora, null);
        List<HistoricoMedicamento> b = FaltasDeMedicamento.calcular(IDOSO, List.of(m), List.of(), agora, null);
        assertEquals(a.stream().map(HistoricoMedicamento::getId).toList(), b.stream().map(HistoricoMedicamento::getId).toList());
        assertEquals(a.size(), a.stream().map(HistoricoMedicamento::getId).distinct().count());
    }
}
