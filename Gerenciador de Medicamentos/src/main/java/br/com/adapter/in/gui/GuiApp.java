package br.com.adapter.in.gui;

import javax.swing.SwingUtilities;

/**
 * Ponto de entrada da interface gráfica. Ela é só um cliente da API do CuidaMed (a mesma do app Android):
 * não acessa o banco de dados. O endereço da API pode ser trocado com a variável CUIDAMED_API_URL.
 */
public class GuiApp {

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> new Navegador().iniciar());
    }
}
