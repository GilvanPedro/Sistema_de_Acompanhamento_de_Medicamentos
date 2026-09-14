package br.com.adapter.in.console;

import br.com.adapter.out.persistence.MedicamentoCsvAdapter;
import br.com.application.service.EditarUsuarioService;
import br.com.config.AppConfig;
import br.com.domain.model.Medicamento;

public class Teste2 {
    public static void main(String[] args) {
        MedicamentoCsvAdapter medicamentoCsvAdapter = new MedicamentoCsvAdapter();

        Medicamento medicamento = medicamentoCsvAdapter.buscarPorId(6);
        System.out.println(medicamento);

        EditarUsuarioService editarUsuarioService = AppConfig.criarEditarUsuarioService();
        editarUsuarioService.editarUsuario(54, "Gilvan Pedro", "pedro.gilvan@email.com", "novaSenha123");
    }
}
