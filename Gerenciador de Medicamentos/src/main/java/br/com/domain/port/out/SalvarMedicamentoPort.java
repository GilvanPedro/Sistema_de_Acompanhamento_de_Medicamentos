package br.com.domain.port.out;

import java.util.List;
import br.com.domain.model.Medicamento;
import br.com.domain.model.Usuario;

public interface SalvarMedicamentoPort {
    void salvar(Medicamento medicamento);
    List<Medicamento> listarTodos();
    void atualizar(Medicamento medicamento);
    List<Medicamento> buscarPorId(int id);
    void excluir(int id);
}