package br.com.adapter.in.gui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Barra de rolagem larga e que acompanha o tema (a padrão é fina e sempre clara). */
final class BarraRolagemUI extends BasicScrollBarUI {

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return semSeta();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return semSeta();
    }

    private static JButton semSeta() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        return b;
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        return new Dimension(Tema.px(26), Tema.px(26));
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle area) {
        g.setColor(Tema.p().fundo());
        g.fillRect(area.x, area.y, area.width, area.height);
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle area) {
        if (area.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isThumbRollover() || isDragging ? Tema.p().primario() : Tema.p().borda());
        int m = Tema.px(4);
        g2.fillRoundRect(area.x + m, area.y + m, area.width - 2 * m, area.height - 2 * m, Tema.px(14), Tema.px(14));
        g2.dispose();
    }
}
