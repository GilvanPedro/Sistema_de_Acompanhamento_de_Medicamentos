package br.com.adapter.in.gui;

import java.time.DayOfWeek;
import java.time.LocalDate;

import br.com.adapter.in.gui.api.ApiException;
import br.com.adapter.in.gui.api.Remedio;
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

    static String detalhe(Remedio m) {
        return tipo(m.tipo()) + "  ·  " + dia(m.dia()) + ", " + m.horario();
    }

    static String primeiroNome(String nome) {
        String limpo = nome == null ? "" : nome.trim();
        int espaco = limpo.indexOf(' ');
        return espaco > 0 ? limpo.substring(0, espaco) : limpo;
    }

    static String dataExtenso(LocalDate data) {
        return dia(data.getDayOfWeek()).toLowerCase() + ", " + data.getDayOfMonth() + " de " + MESES[data.getMonthValue() - 1];
    }

    /** Mensagem para mostrar: a da API (já em português); erros inesperados não mostram detalhes técnicos. */
    static String erro(RuntimeException e) {
        if (e instanceof ApiException) {
            return e.getMessage();
        }
        return "Não foi possível concluir agora. Tente de novo.";
    }
}
