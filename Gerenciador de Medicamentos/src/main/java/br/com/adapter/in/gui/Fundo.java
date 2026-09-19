package br.com.adapter.in.gui;

import java.awt.Graphics;
import java.awt.LayoutManager;

import javax.swing.JPanel;

/** Painel com o fundo do tema atual. */
public class Fundo extends JPanel {

    public Fundo(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Tema.p().fundo());
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }
}
