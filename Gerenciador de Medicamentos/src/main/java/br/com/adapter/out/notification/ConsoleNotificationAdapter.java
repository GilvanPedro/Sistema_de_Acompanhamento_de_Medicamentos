package br.com.adapter.out.notification;

import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.port.out.NotificarPort;

public class ConsoleNotificationAdapter implements NotificarPort {
    MensagemNotificacao mensagemNotificacao;

    @Override
    public void lembrarIdoso(Idoso idoso, Medicamento medicamento) {
        System.out.println(mensagemNotificacao.lembrete(idoso, medicamento));
    }

    @Override
    public void avisarRemedioTomado(Idoso idoso, Medicamento medicamento) {
        for (Familiar familiar : idoso.getFamiliares()) {
            System.out.println(mensagemNotificacao.remedioTomado(familiar.getNome(), idoso, medicamento));
        }
    }

    @Override
    public void avisarRemedioEsquecido(Idoso idoso, Medicamento medicamento) {
        for (Familiar familiar : idoso.getFamiliares()) {
            System.out.println(mensagemNotificacao.remedioEsquecido(familiar.getNome(), idoso, medicamento));
        }
    }
}