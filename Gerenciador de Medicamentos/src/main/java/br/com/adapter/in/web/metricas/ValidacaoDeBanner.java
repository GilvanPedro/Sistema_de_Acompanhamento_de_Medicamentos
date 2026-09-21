package br.com.adapter.in.web.metricas;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import br.com.domain.exception.DadosInvalidosException;

/** As regras do que pode virar banner: o servidor confere tudo, nunca confia no que o navegador diz. */
public final class ValidacaoDeBanner {

    public static final int TAMANHO_MAXIMO_DA_IMAGEM = 1024 * 1024;
    private static final Pattern EMAIL_LINK = Pattern.compile(
            "^mailto:[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(\\?(subject|body)=[^&\\s]*(&(subject|body)=[^&\\s]*)?)?$");

    public record ImagemValida(String tipo, int largura, int altura) { }

    private ValidacaoDeBanner() {
    }

    /** Uma identificação curta a partir do nome da empresa: "Padaria do Zé!" vira "padaria-do-ze". */
    public static String identificacao(String empresa) {
        String sem = Normalizer.normalize(empresa == null ? "" : empresa, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = sem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 50) {
            slug = slug.substring(0, 50).replaceAll("-+$", "");
        }
        if (slug.isEmpty()) {
            throw new DadosInvalidosException("Escreva o nome da empresa (com letras ou números).");
        }
        return slug;
    }

    public static String empresa(String texto) {
        String limpo = texto == null ? "" : texto.trim();
        if (limpo.isEmpty() || limpo.length() > 120) {
            throw new DadosInvalidosException("O nome da empresa precisa ter de 1 a 120 caracteres.");
        }
        return limpo;
    }

    public static String texto(String texto, String empresa) {
        String limpo = texto == null ? "" : texto.trim();
        if (limpo.length() > 200) {
            throw new DadosInvalidosException("A descrição do banner pode ter no máximo 200 caracteres.");
        }
        return limpo.isEmpty() ? "Anúncio de " + empresa : limpo;
    }

    /** Vazio vira "sem link". Aceita site (https) ou e-mail (mailto: só com destinatário, assunto e mensagem). */
    public static String link(String texto) {
        String limpo = texto == null ? "" : texto.trim();
        if (limpo.isEmpty()) {
            return null;
        }
        boolean https = false;
        try {
            https = limpo.length() <= 500 && limpo.startsWith("https://") && URI.create(limpo).getHost() != null;
        } catch (IllegalArgumentException e) {
            // não é um endereço válido
        }
        if (https || (limpo.length() <= 500 && EMAIL_LINK.matcher(limpo).matches())) {
            return limpo;
        }
        throw new DadosInvalidosException("O link precisa começar com https:// (site) ou ser um e-mail no formato mailto:pessoa@exemplo.com.");
    }

    /** Aceita ponto ou vírgula. De 0 (pausado) a 100. */
    public static double peso(String texto) {
        String limpo = texto == null ? "" : texto.trim().replace(',', '.');
        if (limpo.isEmpty()) {
            return 1.0;
        }
        try {
            double peso = Double.parseDouble(limpo);
            if (Double.isFinite(peso) && peso >= 0 && peso <= 100) {
                return Math.round(peso * 100.0) / 100.0;
            }
        } catch (NumberFormatException e) {
            // cai na mensagem abaixo
        }
        throw new DadosInvalidosException("O peso precisa ser um número de 0 (pausado) a 100. O normal é 1.");
    }

    /**
     * Confere a imagem pelo conteúdo (não pelo nome nem pelo tipo que o navegador informou): só PNG ou JPEG, até 1 MB, e
     * com formato de banner (bem mais larga que alta). Lê só o cabeçalho para medir, para uma imagem "bomba" não estourar a
     * memória do servidor.
     */
    public static ImagemValida imagem(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new DadosInvalidosException("Escolha a imagem do banner.");
        }
        if (bytes.length > TAMANHO_MAXIMO_DA_IMAGEM) {
            throw new DadosInvalidosException("A imagem tem mais de 1 MB. Reduza o tamanho do arquivo (o ideal é até uns 300 KB).");
        }
        String tipo = tipoPeloConteudo(bytes);
        if (tipo == null) {
            throw new DadosInvalidosException("A imagem precisa ser PNG ou JPEG.");
        }
        int largura, altura;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(in);
            if (!leitores.hasNext()) {
                throw new DadosInvalidosException("Não foi possível ler a imagem. Ela pode estar corrompida.");
            }
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(in);
                largura = leitor.getWidth(0);
                altura = leitor.getHeight(0);
            } finally {
                leitor.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof DadosInvalidosException d) {
                throw d;
            }
            throw new DadosInvalidosException("Não foi possível ler a imagem. Ela pode estar corrompida.");
        }
        if (largura < 300 || largura > 4000 || altura < 60 || altura > 2000) {
            throw new DadosInvalidosException("A imagem precisa ter de 300 a 4000 pixels de largura e de 60 a 2000 de altura (o ideal é 1280×400).");
        }
        double proporcao = largura / (double) altura;
        if (proporcao < 2.0 || proporcao > 6.0) {
            throw new DadosInvalidosException("A imagem precisa ter formato de banner: a largura entre 2 e 6 vezes a altura (o ideal é 3,2 vezes, como 1280×400). A sua tem " + largura + "×" + altura + ".");
        }
        return new ImagemValida(tipo, largura, altura);
    }

    private static String tipoPeloConteudo(byte[] b) {
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }
}
