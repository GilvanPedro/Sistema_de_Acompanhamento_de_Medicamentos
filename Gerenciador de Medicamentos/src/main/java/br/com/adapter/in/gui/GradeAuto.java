package br.com.adapter.in.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Grade de células iguais que escolhe sozinha quantas colunas cabem na largura disponível
 * (até o máximo pedido). Com letra grande os botões passam para a linha de baixo em vez de ficarem cortados.
 */
public class GradeAuto implements LayoutManager {

    private final int maxColunas;
    private final int espaco;

    public GradeAuto(int maxColunas, int espaco) {
        this.maxColunas = maxColunas;
        this.espaco = espaco;
    }

    @Override
    public void addLayoutComponent(String nome, Component c) { }

    @Override
    public void removeLayoutComponent(Component c) { }

    @Override
    public Dimension preferredLayoutSize(Container pai) {
        int largura = pai.getWidth() > 0 ? pai.getWidth() : 640;
        Insets in = pai.getInsets();
        Medidas m = medir(pai, largura - in.left - in.right);
        return new Dimension(largura, in.top + in.bottom + m.altura());
    }

    @Override
    public Dimension minimumLayoutSize(Container pai) {
        return new Dimension(0, preferredLayoutSize(pai).height);
    }

    @Override
    public void layoutContainer(Container pai) {
        Insets in = pai.getInsets();
        Medidas m = medir(pai, pai.getWidth() - in.left - in.right);
        int gap = Tema.px(espaco);
        int i = 0;
        for (Component c : pai.getComponents()) {
            if (!c.isVisible()) {
                continue;
            }
            int linha = i / m.colunas();
            int coluna = i % m.colunas();
            c.setBounds(in.left + coluna * (m.celula() + gap), in.top + linha * (m.altCelula() + gap), m.celula(), m.altCelula());
            i++;
        }
    }

    private record Medidas(int colunas, int celula, int altCelula, int linhas, int gap) {
        int altura() {
            return linhas == 0 ? 0 : linhas * altCelula + (linhas - 1) * gap;
        }
    }

    private Medidas medir(Container pai, int larguraUtil) {
        int gap = Tema.px(espaco);
        int visiveis = 0;
        int maiorLargura = 0;
        int maiorAltura = 0;
        for (Component c : pai.getComponents()) {
            if (c.isVisible()) {
                Dimension d = c.getPreferredSize();
                maiorLargura = Math.max(maiorLargura, d.width);
                maiorAltura = Math.max(maiorAltura, d.height);
                visiveis++;
            }
        }
        if (visiveis == 0) {
            return new Medidas(1, 0, 0, 0, gap);
        }
        int colunas = Math.max(1, Math.min(Math.min(maxColunas, visiveis), (larguraUtil + gap) / (maiorLargura + gap)));
        int celula = (larguraUtil - (colunas - 1) * gap) / colunas;
        int linhas = (visiveis + colunas - 1) / colunas;
        return new Medidas(colunas, celula, maiorAltura, linhas, gap);
    }
}
