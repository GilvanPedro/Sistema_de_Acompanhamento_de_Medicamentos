package br.com.adapter.in.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;

/** Desenho compartilhado por {@link Botao} e {@link Escolha}: grande, arredondado e com foco bem visível. */
final class PintorBotao {

    enum Estilo { PRIMARIO, SECUNDARIO, PERIGO, SUCESSO }

    private PintorBotao() { }

    static Dimension tamanho(AbstractButton b, int folgaMinima) {
        FontMetrics fm = b.getFontMetrics(b.getFont());
        int w = fm.stringWidth(b.getText()) + 2 * Tema.px(26);
        int h = Math.max(Tema.px(folgaMinima), fm.getHeight() + 2 * Tema.px(12));
        return new Dimension(b.getText().length() <= 2 ? h : w, h);
    }

    static void pintar(Graphics g, AbstractButton b, Estilo estilo, boolean selecionado) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Tema.Paleta p = Tema.p();
        ButtonModel m = b.getModel();

        Color fundo;
        Color texto;
        Color borda = null;
        switch (estilo) {
            case PRIMARIO -> { fundo = p.primario(); texto = p.sobrePrimario(); }
            case PERIGO -> { fundo = p.perigo(); texto = p.sobrePerigo(); }
            case SUCESSO -> { fundo = p.sucesso(); texto = p.sobreSucesso(); }
            default -> { fundo = p.cartao(); texto = p.texto(); borda = p.borda(); }
        }
        if (selecionado) {
            fundo = p.primario();
            texto = p.sobrePrimario();
            borda = null;
        }
        if (m.isPressed()) {
            fundo = Tema.misturar(fundo, p.texto(), 0.22f);
        } else if (m.isRollover()) {
            fundo = Tema.misturar(fundo, p.texto(), 0.10f);
        }
        if (!b.isEnabled()) {
            fundo = Tema.misturar(fundo, p.fundo(), 0.6f);
            texto = Tema.misturar(texto, p.fundo(), 0.5f);
        }

        int arco = Tema.px(18);
        int w = b.getWidth();
        int h = b.getHeight();
        g2.setColor(fundo);
        g2.fillRoundRect(0, 0, w, h, arco, arco);
        if (borda != null) {
            g2.setColor(borda);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, arco, arco);
        }
        if (b.hasFocus()) {
            g2.setColor(p.foco());
            g2.setStroke(new BasicStroke(4f));
            g2.drawRoundRect(2, 2, w - 5, h - 5, arco, arco);
        }

        g2.setFont(b.getFont());
        FontMetrics fm = g2.getFontMetrics();
        String rotulo = b.getText();
        int x = (w - fm.stringWidth(rotulo)) / 2;
        int y = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(texto);
        g2.drawString(rotulo, Math.max(Tema.px(6), x), y);
        g2.dispose();
    }
}
