package br.com.adapter.in.gui;

import java.time.DayOfWeek;
import java.time.LocalDate;

import br.com.domain.exception.CredenciaisInvalidasException;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.exception.MedicamentoNaoEncontradoException;
import br.com.domain.exception.UsuarioNaoEncontradoException;
import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;

/** Textos em português para mostrar na tela. */
final class Rotulos {

    private static final String[] DIAS = {"Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado", "Domingo"};
    private static final String[] MESES = {"janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"};

    private Rotulos() { }

    static String dia(DayOfWeek dia) {
        return DIAS[dia.getValue() - 1];
    }

    static String diaCurto(DayOfWeek dia) {
        String nome = DIAS[dia.getValue() - 1];
        int hifen = nome.indexOf('-');
        return hifen > 0 ? nome.substring(0, hifen) : nome;
    }

    static String tipo(TipoMedicamento tipo) {
        return switch (tipo) {
            case COMPRIMIDO -> "Comprimido";
            case GOTAS -> "Gotas";
            case INJECAO -> "Injeção";
            case OUTRO -> "Outro";
        };
    }

    static String detalhe(Medicamento m) {
        return tipo(m.getTipoMedicamento()) + "  ·  " + dia(m.getDiaSemana()) + ", " + m.getHorarioMedicamento();
    }

    static String primeiroNome(String nome) {
        String limpo = nome == null ? "" : nome.trim();
        int espaco = limpo.indexOf(' ');
        return espaco > 0 ? limpo.substring(0, espaco) : limpo;
    }

    static String dataExtenso(LocalDate data) {
        return dia(data.getDayOfWeek()).toLowerCase() + ", " + data.getDayOfMonth() + " de " + MESES[data.getMonthValue() - 1];
    }

    /** Mensagem amigável para o erro; erros inesperados não mostram detalhes técnicos. */
    static String erro(RuntimeException e) {
        if (e instanceof DadosInvalidosException || e instanceof CredenciaisInvalidasException
                || e instanceof UsuarioNaoEncontradoException || e instanceof MedicamentoNaoEncontradoException
                || e instanceof IllegalArgumentException || e instanceof UnsupportedOperationException) {
            return e.getMessage();
        }
        return "Não foi possível concluir agora. Tente de novo.";
    }
}
