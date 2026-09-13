package br.com.domain.port.out;

import java.util.List;
import br.com.domain.model.Medicamento;

public interface SalvarMedicamentoPort {
    void salvar(Medicamento medicamento);
    List<Medicamento> listarTodos();
    void atualizar(Medicamento medicamento);
    void excluir(int id);
}