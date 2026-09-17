package br.com.adapter.in.console;

import java.util.Scanner;

import br.com.application.service.RealizarLoginService;
import br.com.application.service.RegistrarUsuarioService;
import br.com.config.AppConfig;
import br.com.domain.exception.CredenciaisInvalidasException;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.Usuario;

public class TelaLogin {

    private final Scanner scanner;
    private final RealizarLoginService realizarLoginService;
    private final RegistrarUsuarioService registrarUsuarioService;

    public TelaLogin(Scanner scanner) {
        this.scanner = scanner;
        this.realizarLoginService = AppConfig.criarRealizarLoginService();
        this.registrarUsuarioService = AppConfig.criarRegistrarUsuarioService();
    }

    public boolean exibir() {
        System.out.println("\n=== CuidaMed ===");
        System.out.println("1 - Entrar");
        System.out.println("2 - Cadastrar como idoso");
        System.out.println("3 - Cadastrar como familiar");
        System.out.println("0 - Sair");
        System.out.print("Escolha uma opção: ");

        String opcao = scanner.nextLine();

        switch (opcao) {
            case "1" -> realizarLogin();
            case "2" -> cadastrar(true);
            case "3" -> cadastrar(false);
            case "0" -> { return false; }
            default -> System.out.println("Opção inválida.");
        }
        return true;
    }

    private void realizarLogin() {
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Senha: ");
        String senha = scanner.nextLine();

        try {
            Usuario usuario = realizarLoginService.realizarLogin(email, senha);
            SessaoAtual.login(usuario);
            System.out.println("Login realizado. Bem-vindo, " + usuario.getNome() + "!");
        } catch (CredenciaisInvalidasException e) {
            System.out.println(e.getMessage());
        }
    }

    private void cadastrar(boolean comoIdoso) {
        System.out.print("Nome: ");
        String nome = scanner.nextLine();
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Senha: ");
        String senha = scanner.nextLine();

        try {
            Usuario usuario = comoIdoso
                    ? registrarUsuarioService.registrarIdoso(nome, email, senha)
                    : registrarUsuarioService.registrarFamiliar(nome, email, senha);
            System.out.println("Cadastro realizado. Id: " + usuario.getId());
        } catch (DadosInvalidosException e) {
            System.out.println("Erro no cadastro: " + e.getMessage());
        }
    }
}