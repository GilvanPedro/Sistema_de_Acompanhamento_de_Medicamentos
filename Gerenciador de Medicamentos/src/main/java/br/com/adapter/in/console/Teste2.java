package br.com.adapter.in.console;

import br.com.adapter.out.persistence.UsuarioCsvAdapter;
import br.com.application.service.EditarUsuarioService;
import br.com.application.service.RegistrarMedicamentoService;
import br.com.application.service.RegistrarUsuarioService;
import br.com.config.AppConfig;
import br.com.domain.model.*;
import br.com.domain.port.out.*;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class Teste2 {
    public static void main(String[] args) {
        System.out.println("\n=== 5.1. Editando apenas o nome do idoso ===");

        EditarUsuarioService editarUsuarioService = AppConfig.criarEditarUsuarioService();
        UsuarioCsvAdapter usuarioCsvAdapter = new UsuarioCsvAdapter();
        SalvarUsuarioPort usuarioPort = new UsuarioCsvAdapter();

        Usuario idoso1 = usuarioCsvAdapter.buscarPorId(1);

                editarUsuarioService.editarUsuario(idoso1.getId(), "Pedro Carlos Santos", null, null);
        System.out.println("Depois de editar só o nome: " + usuarioPort.buscarPorId(idoso1.getId()));

        System.out.println("\n=== 5.2. Editando apenas o email do idoso ===");

        editarUsuarioService.editarUsuario(idoso1.getId(), null, "pedro.final@email.com", null);
        System.out.println("Depois de editar só o email: " + usuarioPort.buscarPorId(idoso1.getId()));

        System.out.println("\n=== 5.3. Editando apenas a senha do idoso ===");

        editarUsuarioService.editarUsuario(idoso1.getId(), null, null, "novaSenha456");
        System.out.println("Depois de editar só a senha: " + usuarioPort.buscarPorId(idoso1.getId()));

        System.out.println("\n=== 5.4. Editando os três campos de uma vez ===");

        editarUsuarioService.editarUsuario(idoso1.getId(), "Pedro Carlos Final Completo", "pedro.finalcompleto@email.com", "senhaFinalCompleta789");
        System.out.println("Depois de editar tudo: " + usuarioPort.buscarPorId(idoso1.getId()));

        System.out.println("\n=== 5.5. Tentando editar sem mudar nada (os três null) ===");

        editarUsuarioService.editarUsuario(idoso1.getId(), null, null, null);
        System.out.println("Depois de editar sem mudar nada: " + usuarioPort.buscarPorId(idoso1.getId()));
    }
}