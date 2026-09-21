package br.com.adapter.in.web.relatorio;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.ColumnText;

import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.TipoMedicamento;

/** Monta o PDF do histórico de um idoso num período: resumo no topo e uma linha por tomada ou por horário esquecido. */
public final class PdfDoHistorico {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);
    private static final DateTimeFormatter DIA_DA_SEMANA = DateTimeFormatter.ofPattern("EEE", PT_BR);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", PT_BR);
    private static final DateTimeFormatter GERADO_EM = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PT_BR);

    private static final Color AZUL = new Color(0x0B, 0x4F, 0xC4);
    private static final Color VERDE = new Color(0x1B, 0x7A, 0x3A);
    private static final Color VERMELHO = new Color(0xB4, 0x23, 0x18);
    private static final Color LINHA_ZEBRADA = new Color(0xF3, 0xF5, 0xFA);

    private PdfDoHistorico() {
    }

    public static byte[] gerar(Idoso idoso, LocalDate de, LocalDate ate, List<HistoricoMedicamento> historico, LocalDateTime geradoEm) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 40, 40, 48, 56);
        try {
            PdfWriter escritor = PdfWriter.getInstance(documento, saida);
            escritor.setPageEvent(new Rodape());
            documento.addTitle("Histórico de medicamentos - " + idoso.getNome());
            documento.addCreator("CuidaMed");
            documento.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, AZUL);
            Font subtitulo = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
            Font negrito = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);

            documento.add(new Paragraph("CuidaMed", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.GRAY)));
            documento.add(new Paragraph("Histórico de medicamentos", titulo));
            Paragraph dados = new Paragraph();
            dados.setSpacingBefore(6);
            dados.add(new Phrase("Paciente: ", negrito));
            dados.add(new Phrase(idoso.getNome() + "\n", normal));
            dados.add(new Phrase("Período: ", negrito));
            dados.add(new Phrase("de " + DATA.format(de) + " a " + DATA.format(ate) + "\n", normal));
            dados.add(new Phrase("Gerado em: " + GERADO_EM.format(geradoEm), subtitulo));
            documento.add(dados);

            long tomados = historico.stream().filter(HistoricoMedicamento::isFoiTomado).count();
            long naoTomados = historico.size() - tomados;
            documento.add(resumo(tomados, naoTomados));

            if (historico.isEmpty()) {
                Paragraph vazio = new Paragraph("Nenhum registro neste período.", normal);
                vazio.setSpacingBefore(18);
                documento.add(vazio);
            } else {
                documento.add(tabela(historico));
            }

            Paragraph nota = new Paragraph("\"Tomou\" é o que a pessoa (ou quem cuida dela) marcou no aplicativo, na hora informada. "
                    + "\"Não tomou\" é um horário previsto que terminou o dia sem nenhuma marcação; a hora mostrada é a do horário previsto. "
                    + "Este documento é um apoio para a consulta e não substitui a avaliação do médico.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY));
            nota.setSpacingBefore(14);
            documento.add(nota);
        } catch (DocumentException e) {
            throw new IllegalStateException("Não foi possível montar o PDF do histórico.", e);
        } finally {
            documento.close();
        }
        return saida.toByteArray();
    }

    private static PdfPTable resumo(long tomados, long naoTomados) throws DocumentException {
        PdfPTable tabela = new PdfPTable(3);
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(14);
        long total = tomados + naoTomados;
        String adesao = total == 0 ? "-" : Math.round(tomados * 100.0 / total) + "%";
        tabela.addCell(quadro("Tomou", String.valueOf(tomados), VERDE));
        tabela.addCell(quadro("Não tomou", String.valueOf(naoTomados), VERMELHO));
        tabela.addCell(quadro("Dos horários previstos, tomou", adesao, AZUL));
        return tabela;
    }

    private static PdfPCell quadro(String rotulo, String valor, Color cor) {
        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_CENTER);
        p.add(new Phrase(valor + "\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, cor)));
        p.add(new Phrase(rotulo, FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY)));
        PdfPCell celula = new PdfPCell(p);
        celula.setHorizontalAlignment(Element.ALIGN_CENTER);
        celula.setPadding(8);
        celula.setBorderColor(new Color(0xC9, 0xCF, 0xDC));
        return celula;
    }

    private static PdfPTable tabela(List<HistoricoMedicamento> historico) throws DocumentException {
        PdfPTable tabela = new PdfPTable(new float[] {2.1f, 1.2f, 4.2f, 1.7f});
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(16);
        tabela.setHeaderRows(1);
        for (String cabecalho : List.of("Data", "Hora", "Remédio", "Situação")) {
            PdfPCell c = new PdfPCell(new Phrase(cabecalho, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
            c.setBackgroundColor(AZUL);
            c.setPadding(6);
            c.setBorder(Rectangle.NO_BORDER);
            tabela.addCell(c);
        }
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        boolean zebra = false;
        for (HistoricoMedicamento h : historico) {
            Color fundo = zebra ? LINHA_ZEBRADA : Color.WHITE;
            zebra = !zebra;
            LocalDateTime quando = h.getDataHoraTomada();
            String remedio = h.getMedicamento().getNome() + " (" + descricao(h.getMedicamento().getTipoMedicamento()) + ")";
            tabela.addCell(celula(DATA.format(quando) + "  " + DIA_DA_SEMANA.format(quando), normal, fundo));
            tabela.addCell(celula(HORA.format(quando), normal, fundo));
            tabela.addCell(celula(remedio, normal, fundo));
            tabela.addCell(celula(h.isFoiTomado() ? "Tomou" : "Não tomou",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, h.isFoiTomado() ? VERDE : VERMELHO), fundo));
        }
        return tabela;
    }

    private static String descricao(TipoMedicamento tipo) {
        return switch (tipo) {
            case COMPRIMIDO -> "comprimido";
            case GOTAS -> "gotas";
            case INJECAO -> "injeção";
            case OUTRO -> "outro";
        };
    }

    private static PdfPCell celula(String texto, Font fonte, Color fundo) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fonte));
        c.setBackgroundColor(fundo);
        c.setPadding(5);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(new Color(0xE1, 0xE5, 0xEE));
        return c;
    }

    /** Numeração "Página x" no pé de cada página. */
    private static final class Rodape extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            Phrase texto = new Phrase("CuidaMed  ·  página " + escritor.getPageNumber(),
                    FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
            ColumnText.showTextAligned(escritor.getDirectContent(), Element.ALIGN_CENTER, texto,
                    (documento.right() + documento.left()) / 2, documento.bottom() - 24, 0);
        }
    }
}
