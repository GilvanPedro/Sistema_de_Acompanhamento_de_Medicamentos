package br.com.adapter.in.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JPanel;

/** Cartão arredondado. O tom colore o cartão para avisos (atenção, erro, tudo certo). */
public class Cartao extends JPanel {

    public enum Tom { NEUTRO, AVISO, ERRO, OK }

    private final Tom tom;

    public Cartao(Tom tom) {
        super(new Pilha(10));
        this.tom = tom;
        setOpaque(false);
    }

    public Cartao() {
        this(Tom.NEUTRO);
    }

    @Override
    public Insets getInsets() {
        int v = Tema.px(18);
        int h = Tema.px(22);
        return new Insets(v, h, v, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Tema.Paleta p = Tema.p();
        Color fundo = switch (tom) {
            case NEUTRO -> p.cartao();
            case AVISO -> p.avisoFundo();
            case ERRO -> p.erroFundo();
            case OK -> p.okFundo();
        };
        Color borda = switch (tom) {
            case NEUTRO -> p.borda();
            case AVISO -> p.avisoBorda();
            case ERRO -> p.perigo();
            case OK -> p.sucesso();
        };
        float espessura = tom == Tom.NEUTRO ? 1.5f : 3f;
        int arco = Tema.px(20);
        g2.setColor(fundo);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arco, arco);
        g2.setColor(borda);
        g2.setStroke(new BasicStroke(espessura));
        int m = Math.round(espessura / 2);
        g2.drawRoundRect(m, m, getWidth() - 2 * m - 1, getHeight() - 2 * m - 1, arco, arco);
        g2.dispose();
        super.paintComponent(g);
    }
}
