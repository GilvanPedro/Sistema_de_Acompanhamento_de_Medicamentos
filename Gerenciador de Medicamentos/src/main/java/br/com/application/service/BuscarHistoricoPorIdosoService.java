package br.com.application.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.BuscarHistoricoPorIdosoCase;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.port.out.SalvarUsuarioPort;

public class BuscarHistoricoPorIdosoService implements BuscarHistoricoPorIdosoCase {

    private final SalvarUsuarioPort salvarUsuarioPort;
    private final SalvarMedicamentoPort salvarMedicamentoPort;
    private final SalvarHistoricoPort salvarHistoricoPort;

    public BuscarHistoricoPorIdosoService(SalvarUsuarioPort salvarUsuarioPort, SalvarMedicamentoPort salvarMedicamentoPort, SalvarHistoricoPort salvarHistoricoPort) {
        this.salvarUsuarioPort = salvarUsuarioPort;
        this.salvarMedicamentoPort = salvarMedicamentoPort;
        this.salvarHistoricoPort = salvarHistoricoPort;
    }

    @Override
    public List<HistoricoMedicamento> buscarHistoricoDoIdoso(int idosoId) {
        Usuario usuario = salvarUsuarioPort.buscarPorId(idosoId);

        if (!(usuario instanceof Idoso idoso)) {
            throw new IllegalArgumentException("O usuário com id " + idosoId + " não é um idoso.");
        }

        Map<Integer, Idoso> idosos = new HashMap<>();
        idosos.put(idoso.getId(), idoso);

        Map<Integer, Medicamento> medicamentos = new HashMap<>();
        for (Medicamento m : salvarMedicamentoPort.listarTodos()) {
            medicamentos.put(m.getId(), m);
        }

        return salvarHistoricoPort.listarHistoricoPorIdoso(idosoId, idosos, medicamentos);
    }
}