package br.com.adapter.in.gui;

import java.awt.Desktop;
import java.net.URI;

import br.com.adapter.in.gui.Cartao.Tom;

/** A política de privacidade fica numa página do servidor: aqui só se abre o navegador nela. */
final class Politica {

    private Politica() { }

    static void ler(Navegador nav, Pagina tela) {
        String url = nav.api().urlDaPolitica();
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception e) {
            // cai no aviso com o endereço
        }
        tela.aviso("Abra este endereço no navegador para ler a política de privacidade: " + url, Tom.AVISO);
    }
}
