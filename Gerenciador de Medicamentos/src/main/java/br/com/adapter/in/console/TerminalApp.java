package br.com.adapter.in.console;

import java.util.Scanner;

public class TerminalApp {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        TelaLogin telaLogin = new TelaLogin(scanner);

        boolean continuar = true;
        while (continuar) {
            continuar = telaLogin.exibir();

            if (SessaoAtual.estaLogado()) {
                new MenuPrincipal(scanner).exibir();
                SessaoAtual.logout();
            }
        }

        System.out.println("Encerrando o CuidaMed. Até logo!");
        scanner.close();
    }
}