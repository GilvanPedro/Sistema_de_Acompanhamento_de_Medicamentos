package br.com.adapter.in.gui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JToggleButton;

import br.com.adapter.in.gui.PintorBotao.Estilo;
import br.com.adapter.in.gui.Tema.Papel;

/** Botão de escolha (uma opção entre várias). Marcado = preenchido; não marcado = só contorno. */
public class Escolha extends JToggleButton {

    public Escolha(String texto) {
        super(texto);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Tema.marcar(this, 22, true, Papel.TEXTO);
    }

    @Override
    public Dimension getPreferredSize() {
        return PintorBotao.tamanho(this, 60);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        PintorBotao.pintar(g, this, Estilo.SECUNDARIO, isSelected());
    }
}
