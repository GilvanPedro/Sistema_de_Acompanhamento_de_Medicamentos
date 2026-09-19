package br.com.adapter.in.gui;

import java.awt.Component;
import java.awt.FlowLayout;

import javax.swing.JPanel;

/** Atalhos para montar telas. */
public final class Ui {

    private Ui() { }

    /** Mantém o componente no tamanho natural, alinhado à esquerda. */
    public static JPanel esquerda(Component c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    /** Botões lado a lado; se não couberem, passam para a linha de baixo. */
    public static JPanel linha(int maxColunas, Component... componentes) {
        JPanel p = new JPanel(new GradeAuto(maxColunas, 12));
        p.setOpaque(false);
        for (Component c : componentes) {
            p.add(c);
        }
        return p;
    }

    /** Componentes lado a lado, no tamanho natural de cada um. */
    public static JPanel lado(Component... componentes) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        for (Component c : componentes) {
            p.add(c);
        }
        return p;
    }

    public static JPanel pilha(int espaco, Component... componentes) {
        JPanel p = new JPanel(new Pilha(espaco));
        p.setOpaque(false);
        for (Component c : componentes) {
            p.add(c);
        }
        return p;
    }
}
