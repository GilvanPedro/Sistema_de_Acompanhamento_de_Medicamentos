package br.com.adapter.in.console;

import java.util.Scanner;

import br.com.application.service.EditarUsuarioService;
import br.com.config.AppConfig;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.exception.UsuarioNaoEncontradoException;
import br.com.domain.model.Usuario;

public class PainelPerfil {

    private final Scanner scanner;
    private final Usuario usuario;
    private final EditarUsuarioService editarUsuarioService;

    public PainelPerfil(Scanner scanner, Usuario usuario) {
        this.scanner = scanner;
        this.usuario = usuario;
        this.editarUsuarioService = AppConfig.criarEditarUsuarioService();
    }

    public void exibir() {
        System.out.println("\n=== Meus dados ===");
        System.out.println("Deixe em branco pra manter o valor atual.");

        System.out.print("Novo nome (" + usuario.getNome() + "): ");
        String nomeDigitado = scanner.nextLine();
        String nome = nomeDigitado.isBlank() ? null : nomeDigitado;

        System.out.print("Novo email (" + usuario.getEmail() + "): ");
        String emailDigitado = scanner.nextLine();
        String email = emailDigitado.isBlank() ? null : emailDigitado;

        System.out.print("Nova senha (deixe em branco pra manter a atual): ");
        String senhaDigitada = scanner.nextLine();
        String senha = senhaDigitada.isBlank() ? null : senhaDigitada;

        try {
            Usuario atualizado = editarUsuarioService.editarUsuario(usuario.getId(), nome, email, senha);
            System.out.println("Dados atualizados: " + atualizado);
        } catch (DadosInvalidosException | UsuarioNaoEncontradoException e) {
            System.out.println("Erro ao editar: " + e.getMessage());
        }
    }
}