package br.com.adapter.out.notification;

import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;

public class MensagemNotificacao {
    public static String lembrete(Idoso idoso, Medicamento medicamento) {
        return "Aviso para " + idoso.getNome() +
                ": lembre-se de tomar o remédio: " + medicamento.getNome();
    }

    public static String remedioTomado(String nomeFamiliar, Idoso idoso, Medicamento medicamento) {
        return "Aviso para " + nomeFamiliar +
                ": O paciente " + idoso.getNome() +
                " tomou " + medicamento.getNome();
    }

    public static String remedioEsquecido(String nomeFamiliar, Idoso idoso, Medicamento medicamento) {
        return "Aviso para " + nomeFamiliar +
                ": O paciente " + idoso.getNome() +
                " não tomou o medicamento " + medicamento.getNome();
    }
}
