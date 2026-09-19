package br.com.adapter.in.gui;

import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import br.com.adapter.in.gui.Tema.Papel;

/** Escolha de um número com botões grandes de mais e menos (sem precisar digitar), com um rótulo em cima. */
public class Contador extends JPanel {

    private final int minimo;
    private final int maximo;
    private final int passo;
    private final JLabel visor = new JLabel("", SwingConstants.CENTER) {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(getFontMetrics(getFont()).stringWidth("00") + Tema.px(24), super.getPreferredSize().height);
        }
    };
    private int valor;

    public Contador(String rotulo, int minimo, int maximo, int passo, int inicial) {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.minimo = minimo;
        this.maximo = maximo;
        this.passo = passo;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 40));

        Botao menos = Botao.secundario("−");
        Botao mais = Botao.secundario("+");
        menos.setToolTipText("Diminuir");
        mais.setToolTipText("Aumentar");
        menos.addActionListener(e -> mudar(-passo));
        mais.addActionListener(e -> mudar(passo));

        Tema.marcar(visor, 40, true, Papel.TEXTO);
        JLabel titulo = Tema.marcar(new JLabel(rotulo), 20, false, Papel.SUAVE);
        titulo.setAlignmentX(LEFT_ALIGNMENT);
        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, Tema.px(4)));
        controles.setOpaque(false);
        controles.setAlignmentX(LEFT_ALIGNMENT);
        controles.add(menos);
        controles.add(visor);
        controles.add(mais);
        add(titulo);
        add(controles);
        definir(inicial);
    }

    /** Ao passar do limite, volta para o outro extremo (23h → 00h). */
    private void mudar(int delta) {
        int novo = valor + delta;
        if (novo > maximo) {
            novo = minimo;
        } else if (novo < minimo) {
            novo = minimo + ((maximo - minimo) / passo) * passo;
        }
        definir(novo);
    }

    public void definir(int novo) {
        valor = Math.max(minimo, Math.min(maximo, novo));
        visor.setText(String.format("%02d", valor));
    }

    public int valor() {
        return valor;
    }
}
