package br.com.adapter.in.gui.alarme;

import java.io.ByteArrayInputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

/**
 * Toca um tom de alarme (dois bipes seguidos de uma pausa) em loop contínuo, sintetizado na hora — sem depender de
 * nenhum arquivo de áudio no projeto. Equivalente ao som de alarme do {@code ServicoDoAlarme} do app Android.
 */
final class SomDeAlarme {

    private static final float TAXA_DE_AMOSTRAGEM = 44_100f;

    private Clip clip;

    /** Começa a tocar em loop; para o som anterior, se houver. */
    void tocar() {
        parar();
        try {
            byte[] dados = gerarToque();
            AudioFormat formato = new AudioFormat(TAXA_DE_AMOSTRAGEM, 16, 1, true, true);
            Clip novo = AudioSystem.getClip();
            novo.open(new AudioInputStream(new ByteArrayInputStream(dados), formato, dados.length / 2L));
            novo.loop(Clip.LOOP_CONTINUOUSLY);
            clip = novo;
        } catch (LineUnavailableException | java.io.IOException | IllegalArgumentException e) {
            clip = null; // sem placa de som disponível: o alarme continua só visual (a janela flutuante)
        } catch (RuntimeException e) {
            clip = null;
        }
    }

    void parar() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }

    /** Dois bipes agudos com um pequeno silêncio entre eles, em PCM de 16 bits mono. */
    private static byte[] gerarToque() {
        int duracaoBipeMs = 220;
        int duracaoPausaMs = 160;
        int duracaoSilencioFinalMs = 500;
        double frequenciaHz = 900.0;
        int amostrasBipe = amostrasPara(duracaoBipeMs);
        int amostrasPausa = amostrasPara(duracaoPausaMs);
        int amostrasSilencio = amostrasPara(duracaoSilencioFinalMs);
        int totalDeAmostras = 2 * (amostrasBipe + amostrasPausa) + amostrasSilencio;
        byte[] dados = new byte[totalDeAmostras * 2];
        int pos = 0;
        for (int bipe = 0; bipe < 2; bipe++) {
            for (int i = 0; i < amostrasBipe; i++) {
                // sobe e desce o volume nas pontas do bipe, para não estalar
                double envelope = Math.min(1.0, Math.min(i, amostrasBipe - i) / 200.0);
                short amostra = (short) (Math.sin(2 * Math.PI * frequenciaHz * i / TAXA_DE_AMOSTRAGEM)
                        * Short.MAX_VALUE * 0.6 * envelope);
                dados[pos++] = (byte) (amostra >> 8);
                dados[pos++] = (byte) amostra;
            }
            pos += amostrasPausa * 2;
        }
        return dados;
    }

    private static int amostrasPara(int milissegundos) {
        return (int) (TAXA_DE_AMOSTRAGEM * milissegundos / 1000);
    }
}
