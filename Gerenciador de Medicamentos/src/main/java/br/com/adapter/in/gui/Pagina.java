package br.com.adapter.in.gui;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;

/** Estrutura padrão de toda tela: botão Voltar, título grande, área de avisos e conteúdo com rolagem. */
public class Pagina extends Fundo {

    private static final int LARGURA_MAXIMA = 900;

    private final Rolagem conteudo = new Rolagem(16, LARGURA_MAXIMA);
    private final JPanel avisos = new JPanel(new Pilha(0));
    private final JScrollPane rolagem = new JScrollPane(conteudo);
    private JComponent focoInicial;
    private JButton botaoPadrao;

    public Pagina(String titulo, String subtitulo, Runnable voltar) {
        super(new BorderLayout());
        avisos.setOpaque(false);
        avisos.setVisible(false);

        rolagem.setBorder(null);
        rolagem.setOpaque(false);
        rolagem.getViewport().setOpaque(false);
        rolagem.getVerticalScrollBar().setUI(new BarraRolagemUI());
        rolagem.getVerticalScrollBar().setUnitIncrement(Tema.px(40));
        rolagem.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(rolagem, BorderLayout.CENTER);

        if (voltar != null) {
            Botao b = Botao.secundario("←  Voltar");
            b.addActionListener(e -> voltar.run());
            conteudo.add(Ui.esquerda(b));
        }
        if (titulo != null) {
            conteudo.add(Texto.titulo(titulo));
        }
        if (subtitulo != null) {
            conteudo.add(Texto.suave(subtitulo));
        }
        conteudo.add(avisos);
    }

    public Pagina(String titulo, Runnable voltar) {
        this(titulo, null, voltar);
    }

    /** Adiciona um item ao conteúdo da tela. */
    public void adicionar(Component c) {
        conteudo.add(c);
    }

    public void focoInicial(JComponent c) {
        this.focoInicial = c;
    }

    public JComponent focoInicial() {
        return focoInicial;
    }

    /** Botão acionado ao apertar Enter. */
    public void botaoPadrao(JButton b) {
        this.botaoPadrao = b;
    }

    public JButton botaoPadrao() {
        return botaoPadrao;
    }

    /** Mostra uma mensagem em destaque no topo da tela. */
    public void aviso(String mensagem, Tom tom) {
        avisos.removeAll();
        Cartao cartao = new Cartao(tom);
        cartao.add(new Texto(mensagem, 22, true, Papel.TEXTO));
        avisos.add(cartao);
        avisos.setVisible(true);
        Tema.aplicar(avisos);
        conteudo.revalidate();
        conteudo.repaint();
        SwingUtilities.invokeLater(() -> rolagem.getVerticalScrollBar().setValue(0));
    }

    public void limparAviso() {
        avisos.removeAll();
        avisos.setVisible(false);
        conteudo.revalidate();
        conteudo.repaint();
    }
}
