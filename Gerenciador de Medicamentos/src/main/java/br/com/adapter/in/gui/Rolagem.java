package br.com.adapter.in.gui;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/** Conteúdo de uma página: acompanha a largura da janela e rola na vertical quando a letra é grande. */
public class Rolagem extends JPanel implements Scrollable {

    private final Pilha pilha;

    public Rolagem(int espaco, int larguraMaxima) {
        super(null);
        this.pilha = new Pilha(espaco, larguraMaxima);
        setLayout(pilha);
        setOpaque(false);
    }

    @Override
    public Insets getInsets() {
        return new Insets(Tema.px(20), Tema.px(24), Tema.px(40), Tema.px(24));
    }

    @Override
    public Dimension getPreferredSize() {
        int largura = getParent() instanceof JViewport v ? v.getWidth() : getWidth();
        return pilha.tamanhoPara(this, largura > 0 ? largura : 640);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle area, int orientacao, int direcao) {
        return Tema.px(40);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle area, int orientacao, int direcao) {
        return orientacao == SwingConstants.VERTICAL ? area.height - Tema.px(40) : area.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        Container pai = getParent();
        return pai instanceof JViewport v && v.getHeight() > getPreferredSize().height;
    }
}
