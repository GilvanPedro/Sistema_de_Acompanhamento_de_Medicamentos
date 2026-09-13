package br.com.adapter.out.notification;

import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.port.out.NotificarPort;

public class ConsoleNotificationAdapter implements NotificarPort {

    @Override
    public void lembrarIdoso(Idoso idoso, Medicamento medicamento) {
        System.out.println("Aviso para " + idoso.getNome() + ": lembre-se de tomar o remédio: " + medicamento.getNome());
    }

    @Override
    public void avisarRemedioTomado(Idoso idoso, Medicamento medicamento) {
        for (Familiar familiar : idoso.getFamiliares()) {
            System.out.println(
                    "Aviso para " + familiar.getNome() +
                            ": O paciente " + idoso.getNome() +
                            " tomou " + medicamento.getNome());
        }
    }

    @Override
    public void avisarRemedioEsquecido(Idoso idoso, Medicamento medicamento) {
        for (Familiar familiar : idoso.getFamiliares()) {
            System.out.println(
                    "Aviso para " + familiar.getNome() +
                            ": O paciente " + idoso.getNome() +
                            " não tomou o medicamento " + medicamento.getNome());
        }
    }
}