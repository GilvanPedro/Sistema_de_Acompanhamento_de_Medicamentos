package br.com.adapter.in.gui;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;

import br.com.adapter.in.gui.Tema.Papel;

/** Campos de texto grandes, com contorno que engrossa quando o campo está selecionado. */
public final class Campo {

    private Campo() { }

    public static JTextField texto() {
        return new Digitavel();
    }

    public static JPasswordField senha() {
        return new Secreto();
    }

    private static void desenharFundo(Graphics g, JTextComponent c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Tema.Paleta p = Tema.p();
        int arco = Tema.px(16);
        g2.setColor(p.cartao());
        g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arco, arco);
        boolean foco = c.hasFocus();
        g2.setColor(foco ? p.primario() : p.borda());
        g2.setStroke(new BasicStroke(foco ? 4f : 2f));
        int m = foco ? 2 : 1;
        g2.drawRoundRect(m, m, c.getWidth() - 2 * m - 1, c.getHeight() - 2 * m - 1, arco, arco);
        g2.dispose();
    }

    private static Insets margem() {
        return new Insets(Tema.px(10), Tema.px(16), Tema.px(10), Tema.px(16));
    }

    private static Dimension tamanho(JTextComponent c, Dimension base) {
        int h = c.getFontMetrics(c.getFont()).getHeight() + margem().top + margem().bottom;
        return new Dimension(base.width, Math.max(Tema.px(56), h));
    }

    private static final class Digitavel extends JTextField {
        Digitavel() {
            setOpaque(false);
            setBorder(null);
            Tema.marcar(this, 24, false, Papel.TEXTO);
            Tema.aplicar(this);
        }

        @Override
        public Insets getInsets() {
            return margem();
        }

        @Override
        public Dimension getPreferredSize() {
            return tamanho(this, super.getPreferredSize());
        }

        @Override
        protected void paintComponent(Graphics g) {
            desenharFundo(g, this);
            super.paintComponent(g);
        }
    }

    private static final class Secreto extends JPasswordField {
        Secreto() {
            setOpaque(false);
            setBorder(null);
            Tema.marcar(this, 24, false, Papel.TEXTO);
            Tema.aplicar(this);
        }

        @Override
        public Insets getInsets() {
            return margem();
        }

        @Override
        public Dimension getPreferredSize() {
            return tamanho(this, super.getPreferredSize());
        }

        @Override
        protected void paintComponent(Graphics g) {
            desenharFundo(g, this);
            super.paintComponent(g);
        }
    }
}
