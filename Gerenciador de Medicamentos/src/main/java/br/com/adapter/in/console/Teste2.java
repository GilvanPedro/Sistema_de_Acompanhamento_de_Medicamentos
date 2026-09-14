package br.com.adapter.in.console;

import br.com.adapter.out.persistence.MedicamentoCsvAdapter;
import br.com.application.service.EditarUsuarioService;
import br.com.application.service.ExcluirMedicamentoService;
import br.com.application.service.ExcluirUsuarioService;
import br.com.config.AppConfig;
import br.com.domain.model.Medicamento;
import br.com.domain.port.in.ExcluirMedicamentoCase;

public class Teste2 {
    public static void main(String[] args) {
        MedicamentoCsvAdapter medicamentoCsvAdapter = new MedicamentoCsvAdapter();

        EditarUsuarioService editarUsuarioService = AppConfig.criarEditarUsuarioService();

        ExcluirUsuarioService excluirUsuarioService = new AppConfig().criarExcluirUsuarioService();

        ExcluirMedicamentoService excluirMedicamentoService = new AppConfig().criarExcluirMedicamentoService();

        // editarUsuarioService.editarUsuario(5, "Gilvan Pedro", "pedro.gilvan@email.com", "novaSenha123");

        System.out.println(medicamentoCsvAdapter.buscarPorId(7));

        excluirMedicamentoService.excluirMedicamento(7);

        System.out.println(medicamentoCsvAdapter.buscarPorId(7));
    }
}
