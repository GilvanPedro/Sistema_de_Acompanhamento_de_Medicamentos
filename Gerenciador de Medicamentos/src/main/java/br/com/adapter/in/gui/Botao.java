package br.com.adapter.in.gui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JButton;

import br.com.adapter.in.gui.PintorBotao.Estilo;
import br.com.adapter.in.gui.Tema.Papel;

/** Botão grande, com texto legível e cantos arredondados. */
public class Botao extends JButton {

    private final Estilo estilo;
    private final int alturaMinima;

    private Botao(String texto, Estilo estilo, int tamanhoLetra, int alturaMinima) {
        super(texto);
        this.estilo = estilo;
        this.alturaMinima = alturaMinima;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Tema.marcar(this, tamanhoLetra, true, Papel.TEXTO);
    }

    public static Botao primario(String texto) {
        return new Botao(texto, Estilo.PRIMARIO, 24, 64);
    }

    public static Botao secundario(String texto) {
        return new Botao(texto, Estilo.SECUNDARIO, 24, 64);
    }

    public static Botao sucesso(String texto) {
        return new Botao(texto, Estilo.SUCESSO, 24, 64);
    }

    public static Botao perigo(String texto) {
        return new Botao(texto, Estilo.PERIGO, 24, 64);
    }

    /** Botão menor, para a barra superior. */
    public static Botao barra(String texto) {
        return new Botao(texto, Estilo.SECUNDARIO, 18, 48);
    }

    @Override
    public Dimension getPreferredSize() {
        return PintorBotao.tamanho(this, alturaMinima);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        PintorBotao.pintar(g, this, estilo, false);
    }
}
