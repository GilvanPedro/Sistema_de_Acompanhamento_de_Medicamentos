package br.com.domain.port.out;

import java.util.List;
import br.com.domain.model.Medicamento;
import br.com.domain.model.Usuario;

public interface SalvarMedicamentoPort {
    void salvar(Medicamento medicamento);
    List<Medicamento> listarTodos();
    void atualizar(Medicamento medicamento);
    Medicamento buscarPorId(int id);

    /** Os medicamentos (não excluídos) de um idoso. O banco filtra; o padrão serve para quem só sabe listar tudo (CSV). */
    default List<Medicamento> listarPorIdoso(int idosoId) {
        return listarTodos().stream().filter(m -> m.getIdosoId() == idosoId).toList();
    }
    void excluir(int id);
}