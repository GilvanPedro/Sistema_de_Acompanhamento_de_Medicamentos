package br.com.adapter.in.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Caixa de confirmação grande, no lugar do JOptionPane (que não acompanha o tema nem a letra grande). */
public final class Dialogos {

    private Dialogos() { }

    /** Pergunta sim/não. O botão "não" é o padrão para evitar exclusões sem querer. */
    public static boolean confirmar(Component pai, String titulo, String mensagem, String textoSim, String textoNao) {
        JDialog dialogo = new JDialog(SwingUtilities.getWindowAncestor(pai), "CuidaMed", JDialog.ModalityType.APPLICATION_MODAL);
        boolean[] resposta = {false};

        Fundo conteudo = new Fundo(new Pilha(18));
        conteudo.setBorder(new EmptyBorder(Tema.px(26), Tema.px(26), Tema.px(26), Tema.px(26)));
        conteudo.add(Texto.titulo(titulo));
        conteudo.add(Texto.corpo(mensagem));

        Botao sim = Botao.perigo(textoSim);
        Botao nao = Botao.secundario(textoNao);
        sim.addActionListener(e -> { resposta[0] = true; dialogo.dispose(); });
        nao.addActionListener(e -> dialogo.dispose());
        conteudo.add(Ui.linha(2, nao, sim));

        JComponent raiz = dialogo.getRootPane();
        raiz.registerKeyboardAction(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialogo.dispose();
            }
        }, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialogo.setContentPane(conteudo);
        Tema.aplicar(conteudo);
        int largura = Tema.px(640);
        conteudo.setSize(largura, 1);
        conteudo.setPreferredSize(new Dimension(largura, conteudo.getPreferredSize().height));
        dialogo.pack();
        dialogo.setResizable(false);
        dialogo.setLocationRelativeTo(pai);
        SwingUtilities.invokeLater(nao::requestFocusInWindow);
        dialogo.setVisible(true);
        return resposta[0];
    }
}
