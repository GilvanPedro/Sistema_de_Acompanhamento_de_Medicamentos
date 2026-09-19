package br.com.adapter.in.gui;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Ponto de entrada da interface gráfica. */
public class GuiApp {

    private static final Path PASTA_DADOS = Path.of("arquivos");

    public static void main(String[] args) throws Exception {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // Os adaptadores de CSV usam o caminho relativo "arquivos/", então o programa precisa ser
        // iniciado na pasta que contém os dados. Sem esse aviso o login parece "não funcionar".
        if (!Files.isDirectory(PASTA_DADOS)) {
            if (Files.isDirectory(Path.of("..", "arquivos"))) {
                SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(null,
                        "Não encontrei a pasta \"arquivos\" (seus dados) em:\n" + Path.of("").toAbsolutePath()
                                + "\n\nAbra o CuidaMed a partir da pasta do projeto, que contém a pasta \"arquivos\".",
                        "CuidaMed", JOptionPane.ERROR_MESSAGE));
                return;
            }
            Files.createDirectories(PASTA_DADOS);
        }

        SwingUtilities.invokeLater(() -> new Navegador().iniciar());
    }
}
