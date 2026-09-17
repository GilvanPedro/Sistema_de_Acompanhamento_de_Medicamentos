package br.com.adapter.in.console;

import java.util.Scanner;
import br.com.domain.model.Idoso;
import br.com.domain.model.Usuario;

public class MenuPrincipal {

    private final Scanner scanner;

    public MenuPrincipal(Scanner scanner) {
        this.scanner = scanner;
    }

    public void exibir() {
        Usuario usuario = SessaoAtual.getUsuarioLogado();

        if (usuario instanceof Idoso idoso) {
            new TelaIdoso(scanner, idoso).exibir();
        } else {
            new TelaFamiliar(scanner).exibir();
        }
    }
}