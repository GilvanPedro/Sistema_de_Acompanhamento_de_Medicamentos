package br.com.domain.port.out;

import java.util.List;
import java.util.Map;

import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;

public interface SalvarHistoricoPort {
    void salvar(HistoricoMedicamento historico);
    List<HistoricoMedicamento> listarTodos(Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos);
    void atualizar(HistoricoMedicamento historico, Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos);
    void excluir(int id);
    List<HistoricoMedicamento> listarHistoricoPorIdoso(int idIdoso, Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos);
}