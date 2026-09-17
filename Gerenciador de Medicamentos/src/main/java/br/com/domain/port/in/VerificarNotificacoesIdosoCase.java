package br.com.domain.port.in;

import java.util.List;
import br.com.domain.model.Idoso;
import br.com.domain.model.NotificacaoMedicamento;

public interface VerificarNotificacoesIdosoCase {
    List<NotificacaoMedicamento> verificarNotificacoes(Idoso idoso);
}