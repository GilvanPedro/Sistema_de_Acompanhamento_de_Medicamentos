package br.com.adapter.in.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.JComponent;
import javax.swing.text.JTextComponent;

/**
 * Tema visual da interface: modo claro/escuro e tamanho da letra.
 * Todas as cores têm contraste alto (nível AAA para o texto principal) por causa do público idoso.
 */
public final class Tema {

    public enum Modo { CLARO, ESCURO }

    public enum Papel { TEXTO, SUAVE, DESTAQUE }

    public record Paleta(Color fundo, Color cartao, Color texto, Color suave, Color destaque,
                         Color primario, Color sobrePrimario, Color borda, Color foco,
                         Color perigo, Color sobrePerigo, Color sucesso, Color sobreSucesso,
                         Color avisoFundo, Color avisoBorda, Color erroFundo, Color okFundo) { }

    private static final Paleta CLARA = new Paleta(
            cor(0xF4F6F9), cor(0xFFFFFF), cor(0x111827), cor(0x44505F), cor(0x0B4F9C),
            cor(0x0B5CAD), cor(0xFFFFFF), cor(0x9AA7B6), cor(0xE07800),
            cor(0xB3261E), cor(0xFFFFFF), cor(0x17673A), cor(0xFFFFFF),
            cor(0xFFF1C2), cor(0xB07800), cor(0xFDE4E1), cor(0xDDF3E4));

    private static final Paleta ESCURA = new Paleta(
            cor(0x0E1620), cor(0x19242F), cor(0xF3F4F6), cor(0xB9C3CF), cor(0x8CC4FF),
            cor(0x7DBBFF), cor(0x06213F), cor(0x55667A), cor(0xFFB84D),
            cor(0xFF9A90), cor(0x3B0503), cor(0x7ADCA0), cor(0x06301A),
            cor(0x3A2E0A), cor(0xE0B040), cor(0x4A1C19), cor(0x143524));

    private static final float[] ESCALAS = {0.85f, 1.0f, 1.2f, 1.4f, 1.6f};

    private static final Preferences PREFS = Preferences.userNodeForPackage(Tema.class);
    private static final List<Runnable> OUVINTES = new ArrayList<>();

    private static Modo modo = lerModo();
    private static int indiceEscala = Math.max(0, Math.min(ESCALAS.length - 1, PREFS.getInt("escala", 1)));

    private Tema() { }

    private static Color cor(int rgb) {
        return new Color(rgb);
    }

    private static Modo lerModo() {
        try {
            return Modo.valueOf(PREFS.get("modo", Modo.CLARO.name()));
        } catch (IllegalArgumentException e) {
            return Modo.CLARO;
        }
    }

    public static Paleta p() {
        return modo == Modo.ESCURO ? ESCURA : CLARA;
    }

    public static Modo modo() {
        return modo;
    }

    public static void alternarModo() {
        modo = modo == Modo.CLARO ? Modo.ESCURO : Modo.CLARO;
        PREFS.put("modo", modo.name());
        avisar();
    }

    public static boolean podeAumentar() {
        return indiceEscala < ESCALAS.length - 1;
    }

    public static boolean podeDiminuir() {
        return indiceEscala > 0;
    }

    public static void aumentarLetra() {
        if (podeAumentar()) {
            mudarEscala(indiceEscala + 1);
        }
    }

    public static void diminuirLetra() {
        if (podeDiminuir()) {
            mudarEscala(indiceEscala - 1);
        }
    }

    private static void mudarEscala(int novo) {
        indiceEscala = novo;
        PREFS.putInt("escala", indiceEscala);
        avisar();
    }

    /** Tamanho em pixels, acompanhando a escala da letra (espaçamentos crescem junto com o texto). */
    public static int px(int base) {
        return Math.round(base * ESCALAS[indiceEscala]);
    }

    public static Font fonte(int tamanhoBase, boolean negrito) {
        return new Font(Font.SANS_SERIF, negrito ? Font.BOLD : Font.PLAIN, px(tamanhoBase));
    }

    public static void aoMudar(Runnable ouvinte) {
        OUVINTES.add(ouvinte);
    }

    private static void avisar() {
        for (Runnable o : new ArrayList<>(OUVINTES)) {
            o.run();
        }
    }

    public static Color corDoPapel(Papel papel) {
        return switch (papel) {
            case TEXTO -> p().texto();
            case SUAVE -> p().suave();
            case DESTAQUE -> p().destaque();
        };
    }

    /** Reaplica fontes e cores em toda a árvore de componentes marcados com {@link #marcar}. */
    public static void aplicar(Component c) {
        if (c instanceof JComponent j) {
            Integer tam = (Integer) j.getClientProperty("tam");
            if (tam != null) {
                j.setFont(fonte(tam, Boolean.TRUE.equals(j.getClientProperty("neg"))));
            }
            Papel papel = (Papel) j.getClientProperty("papel");
            if (papel != null) {
                j.setForeground(corDoPapel(papel));
            }
            if (j instanceof JTextComponent t) {
                t.setCaretColor(p().texto());
                t.setSelectionColor(p().primario());
                t.setSelectedTextColor(p().sobrePrimario());
            }
        }
        if (c instanceof Container ct) {
            for (Component filho : ct.getComponents()) {
                aplicar(filho);
            }
        }
        c.invalidate();
    }

    public static <T extends JComponent> T marcar(T c, int tamanhoBase, boolean negrito, Papel papel) {
        c.putClientProperty("tam", tamanhoBase);
        c.putClientProperty("neg", negrito);
        c.putClientProperty("papel", papel);
        c.setFont(fonte(tamanhoBase, negrito));
        c.setForeground(corDoPapel(papel));
        return c;
    }

    public static Color misturar(Color a, Color b, float peso) {
        float inv = 1 - peso;
        return new Color(
                Math.round(a.getRed() * inv + b.getRed() * peso),
                Math.round(a.getGreen() * inv + b.getGreen() * peso),
                Math.round(a.getBlue() * inv + b.getBlue() * peso));
    }
}
