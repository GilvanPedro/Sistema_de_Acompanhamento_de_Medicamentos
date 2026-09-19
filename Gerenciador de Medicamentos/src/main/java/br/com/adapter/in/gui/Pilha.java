package br.com.adapter.in.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Empilha os componentes na vertical, todos com a largura total disponível.
 * Diferente do BoxLayout, calcula a altura de textos com quebra de linha a partir da largura real,
 * o que é essencial quando a letra fica grande. Opcionalmente limita e centraliza a largura.
 * Espaço e largura máxima são valores-base, multiplicados pela escala da letra.
 */
public class Pilha implements LayoutManager {

    private static final int LARGURA_PADRAO = 640;

    private final int espaco;
    private final int larguraMaxima;

    public Pilha(int espaco) {
        this(espaco, Integer.MAX_VALUE);
    }

    public Pilha(int espaco, int larguraMaxima) {
        this.espaco = espaco;
        this.larguraMaxima = larguraMaxima;
    }

    private int maxima() {
        return larguraMaxima == Integer.MAX_VALUE ? larguraMaxima : Tema.px(larguraMaxima);
    }

    @Override
    public void addLayoutComponent(String nome, Component c) { }

    @Override
    public void removeLayoutComponent(Component c) { }

    @Override
    public Dimension preferredLayoutSize(Container pai) {
        return tamanhoPara(pai, pai.getWidth() > 0 ? pai.getWidth() : LARGURA_PADRAO);
    }

    @Override
    public Dimension minimumLayoutSize(Container pai) {
        return new Dimension(0, preferredLayoutSize(pai).height);
    }

    /** Tamanho preferido quando o contêiner tem a largura informada. */
    public Dimension tamanhoPara(Container pai, int largura) {
        Insets in = pai.getInsets();
        int interna = Math.min(maxima(), largura - in.left - in.right);
        int altura = in.top + in.bottom;
        boolean primeiro = true;
        for (Component c : pai.getComponents()) {
            if (!c.isVisible()) {
                continue;
            }
            altura += (primeiro ? 0 : Tema.px(espaco)) + alturaDo(c, interna);
            primeiro = false;
        }
        return new Dimension(largura, altura);
    }

    @Override
    public void layoutContainer(Container pai) {
        Insets in = pai.getInsets();
        int disponivel = pai.getWidth() - in.left - in.right;
        int interna = Math.min(maxima(), disponivel);
        int x = in.left + (disponivel - interna) / 2;
        int y = in.top;
        for (Component c : pai.getComponents()) {
            if (!c.isVisible()) {
                continue;
            }
            int h = alturaDo(c, interna);
            c.setBounds(x, y, interna, h);
            y += h + Tema.px(espaco);
        }
    }

    private static int alturaDo(Component c, int largura) {
        c.setSize(largura, Math.max(1, c.getHeight()));
        return c.getPreferredSize().height;
    }
}
