package br.com.adapter.in.gui.alarme;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/**
 * Notificação na bandeja do sistema operacional, para os avisos que não precisam do alarme sonoro: o familiar sendo
 * avisado de que um remédio foi tomado ou esquecido, e o idoso sendo avisado de um pedido de vínculo novo.
 * Equivalente ao {@code Notificador} do app Android, dentro do que o Java Desktop oferece.
 */
public final class NotificacaoDoSistema {

    private static TrayIcon icone;

    private NotificacaoDoSistema() { }

    /** Cria o ícone da bandeja, se o sistema operacional suportar. Chamar uma vez, ao iniciar a janela principal. */
    public static void iniciar() {
        if (icone != null || !SystemTray.isSupported()) {
            return;
        }
        try {
            TrayIcon novo = new TrayIcon(criarImagem(), "CuidaMed");
            novo.setImageAutoSize(true);
            SystemTray.getSystemTray().add(novo);
            icone = novo;
        } catch (AWTException | RuntimeException e) {
            icone = null; // sem bandeja disponível: os avisos continuam aparecendo só dentro das telas do app
        }
    }

    /** Mostra um balão de notificação; não faz nada se a bandeja não estiver disponível. */
    public static void mostrar(String titulo, String mensagem) {
        if (icone != null) {
            icone.displayMessage(titulo, mensagem, TrayIcon.MessageType.INFO);
        }
    }

    /** Ícone simples (um "C" sobre um círculo azul), gerado na hora para não depender de arquivo de imagem. */
    private static Image criarImagem() {
        int tamanho = 32;
        BufferedImage imagem = new BufferedImage(tamanho, tamanho, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagem.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x0B5CAD));
        g.fillOval(0, 0, tamanho, tamanho);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        FontMetrics fm = g.getFontMetrics();
        String letra = "C";
        int x = (tamanho - fm.stringWidth(letra)) / 2;
        int y = (tamanho - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(letra, x, y);
        g.dispose();
        return imagem;
    }
}
