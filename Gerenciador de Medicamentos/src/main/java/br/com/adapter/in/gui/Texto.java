package br.com.adapter.in.gui;

import javax.swing.JTextArea;

import br.com.adapter.in.gui.Tema.Papel;

/** Texto somente leitura, com quebra de linha, que se comporta como um rótulo. */
public class Texto extends JTextArea {

    public Texto(String texto, int tamanho, boolean negrito, Papel papel) {
        super(texto);
        setEditable(false);
        setFocusable(false);
        setOpaque(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        setBorder(null);
        Tema.marcar(this, tamanho, negrito, papel);
    }

    public static Texto corpo(String texto) {
        return new Texto(texto, 22, false, Papel.TEXTO);
    }

    public static Texto suave(String texto) {
        return new Texto(texto, 20, false, Papel.SUAVE);
    }

    public static Texto titulo(String texto) {
        return new Texto(texto, 34, true, Papel.TEXTO);
    }

    public static Texto secao(String texto) {
        return new Texto(texto, 26, true, Papel.DESTAQUE);
    }

    public static Texto rotulo(String texto) {
        return new Texto(texto, 22, true, Papel.TEXTO);
    }
}
