package br.com.application.service;

import br.com.domain.port.in.ExcluirMedicamentoCase;
import br.com.domain.port.out.SalvarMedicamentoPort;

public class ExcluirMedicamentoService implements ExcluirMedicamentoCase {
    private final SalvarMedicamentoPort salvarMedicamentoPort;

    public ExcluirMedicamentoService(SalvarMedicamentoPort salvarMedicamentoPort) {
        this.salvarMedicamentoPort = salvarMedicamentoPort;
    }

    @Override
    public void excluirMedicamento(int id) {

        salvarMedicamentoPort.excluir(id);
    }
}
