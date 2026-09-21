package br.com.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import br.com.adapter.in.web.relatorio.PdfDoHistorico;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;

class PdfDoHistoricoTest {

    private static final Idoso IDOSO = new Idoso(1, "Dona Neusa Conceição", "n@x.com", "x", List.of());
    private static final Medicamento M = new Medicamento(7, 1, "Enalapril", LocalTime.of(8, 0), DayOfWeek.MONDAY, TipoMedicamento.COMPRIMIDO);

    @Test
    void geraUmPdfComPeriodoResumoELinhas() throws Exception {
        List<HistoricoMedicamento> linhas = List.of(
                new HistoricoMedicamento(1, M, IDOSO, LocalDateTime.of(2026, 8, 3, 8, 12), true),
                new HistoricoMedicamento(-2, M, IDOSO, LocalDateTime.of(2026, 8, 10, 8, 0), false));
        byte[] pdf = PdfDoHistorico.gerar(IDOSO, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 9, 4), linhas, LocalDateTime.of(2026, 9, 21, 14, 0));

        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1));
        PdfReader leitor = new PdfReader(pdf);
        String texto = new PdfTextExtractor(leitor).getTextFromPage(1);
        assertTrue(texto.contains("Dona Neusa Conceição"), texto);
        assertTrue(texto.contains("03/08/2026") && texto.contains("04/09/2026"), "período: " + texto);
        assertTrue(texto.contains("Enalapril") && texto.contains("Tomou") && texto.contains("Não tomou"), texto);
        assertTrue(texto.contains("50%"), "1 de 2 horários tomados: " + texto);
    }

    @Test
    void periodoSemRegistrosAindaGeraODocumento() throws Exception {
        byte[] pdf = PdfDoHistorico.gerar(IDOSO, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4), List.of(), LocalDateTime.of(2026, 9, 21, 14, 0));
        assertTrue(new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1).contains("Nenhum registro neste período"));
    }

    @Test
    void historicosGrandesGanhamVariasPaginas() throws Exception {
        List<HistoricoMedicamento> muitas = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            muitas.add(new HistoricoMedicamento(i + 1, M, IDOSO, LocalDateTime.of(2026, 1, 1, 8, 0).plusDays(i), i % 3 != 0));
        }
        byte[] pdf = PdfDoHistorico.gerar(IDOSO, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 1), muitas, LocalDateTime.of(2026, 9, 21, 14, 0));
        assertTrue(new PdfReader(pdf).getNumberOfPages() > 3);
    }
}
