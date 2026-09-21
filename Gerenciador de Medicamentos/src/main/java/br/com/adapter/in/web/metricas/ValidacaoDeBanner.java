package br.com.adapter.in.web.metricas;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

    public static final int TAMANHO_MAXIMO_DO_VIDEO = 4 * 1024 * 1024;
    public static final int DURACAO_MAXIMA_DO_VIDEO_MS = 15_000;

    public record ImagemValida(String tipo, int largura, int altura) { }

    public record VideoValido(int duracaoMs, int largura, int altura) { }

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

    // ------------------------------------------------------------------ vídeo

    /**
     * Confere o vídeo pelo conteúdo: MP4 com vídeo H.264, até 4 MB e 15 segundos, com formato de banner. Lê só o cabeçalho
     * do arquivo (as "caixas" do MP4), sem decodificar nada.
     */
    public static VideoValido video(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new DadosInvalidosException("Escolha o vídeo do banner.");
        }
        if (bytes.length > TAMANHO_MAXIMO_DO_VIDEO) {
            throw new DadosInvalidosException("O vídeo tem mais de 4 MB. Comprima ou encurte (o ideal é até uns 2 MB).");
        }
        if (bytes.length < 16 || !"ftyp".equals(texto4(bytes, 4))) {
            throw new DadosInvalidosException("O vídeo precisa ser um arquivo MP4.");
        }
        Mp4 mp4 = Mp4.ler(bytes);
        if (mp4.codec == null) {
            throw new DadosInvalidosException("Não encontrei imagem em movimento no arquivo. Confira se ele é mesmo um vídeo MP4.");
        }
        if (!"avc1".equals(mp4.codec) && !"avc3".equals(mp4.codec)) {
            throw new DadosInvalidosException("O vídeo precisa usar o codec H.264 (o padrão do MP4 comum). O seu usa \"" + mp4.codec + "\".");
        }
        if (mp4.timescale <= 0 || mp4.duracao <= 0) {
            throw new DadosInvalidosException("Não consegui ler a duração do vídeo. Tente exportar de novo.");
        }
        int duracaoMs = (int) Math.min(Integer.MAX_VALUE, mp4.duracao * 1000 / mp4.timescale);
        if (duracaoMs < 1000 || duracaoMs > DURACAO_MAXIMA_DO_VIDEO_MS) {
            throw new DadosInvalidosException("O vídeo precisa ter de 1 a 15 segundos. O seu tem " + String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", duracaoMs / 1000.0) + ".");
        }
        int largura = mp4.largura, altura = mp4.altura;
        if (largura < 320 || largura > 1920 || altura < 60 || altura > 1080) {
            throw new DadosInvalidosException("O vídeo precisa ter de 320 a 1920 pixels de largura e de 60 a 1080 de altura (o ideal é 1280×400). O seu tem " + largura + "×" + altura + ".");
        }
        double proporcao = largura / (double) altura;
        if (proporcao < 2.0 || proporcao > 6.0) {
            throw new DadosInvalidosException("O vídeo precisa ter formato de banner: a largura entre 2 e 6 vezes a altura (o ideal é 1280×400). O seu tem " + largura + "×" + altura + ".");
        }
        return new VideoValido(duracaoMs, largura, altura);
    }

    /** O vídeo aparece no lugar da imagem: os dois precisam ter quase o mesmo formato, senão o vídeo seria cortado. */
    public static void videoCombinaComAImagem(VideoValido video, int larguraDaImagem, int alturaDaImagem) {
        double doVideo = video.largura() / (double) video.altura();
        double daImagem = larguraDaImagem / (double) alturaDaImagem;
        if (Math.abs(doVideo - daImagem) / daImagem > 0.2) {
            throw new DadosInvalidosException("O vídeo (" + video.largura() + "×" + video.altura() + ") tem um formato diferente da imagem ("
                    + larguraDaImagem + "×" + alturaDaImagem + "). Use o mesmo formato nos dois, por exemplo 1280×400.");
        }
    }

    /** Mede a largura e a altura de uma imagem já salva (ao trocar só o vídeo de um banner existente). */
    public static ImagemValida medirImagem(byte[] bytes) {
        return imagem(bytes);
    }

    private static String texto4(byte[] b, int posicao) {
        return posicao + 4 > b.length ? "" : new String(b, posicao, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    /** As "caixas" (boxes) do MP4 que interessam: duração, tamanho da tela e codec da faixa de vídeo. */
    private static final class Mp4 {
        private final byte[] b;
        long timescale;
        long duracao;
        int largura;
        int altura;
        String codec;

        private Mp4(byte[] b) {
            this.b = b;
        }

        static Mp4 ler(byte[] b) {
            Mp4 m = new Mp4(b);
            for (int[] caixa : caixas(b, 0, b.length)) {
                if ("moov".equals(texto4(b, caixa[0] + 4))) {
                    m.lerMoov(caixa[1], caixa[2]);
                }
            }
            return m;
        }

        /** Cada caixa: {início do cabeçalho, início do conteúdo, fim}. Para no primeiro defeito, sem lançar erro. */
        private static List<int[]> caixas(byte[] b, int inicio, int fim) {
            List<int[]> lista = new ArrayList<>();
            int p = inicio;
            while (p + 8 <= fim && lista.size() < 5000) {
                long tamanho = u32(b, p);
                int cabecalho = 8;
                if (tamanho == 1) {
                    if (p + 16 > fim) {
                        break;
                    }
                    tamanho = u64(b, p + 8);
                    cabecalho = 16;
                } else if (tamanho == 0) {
                    tamanho = fim - p;
                }
                if (tamanho < cabecalho || p + tamanho > fim) {
                    break;
                }
                lista.add(new int[]{p, p + cabecalho, (int) (p + tamanho)});
                p += (int) tamanho;
            }
            return lista;
        }

        private void lerMoov(int inicio, int fim) {
            for (int[] c : caixas(b, inicio - 8 < 0 ? 0 : inicio, fim)) {
                String tipo = texto4(b, c[0] + 4);
                if ("mvhd".equals(tipo) && c[1] + 24 <= c[2]) {
                    boolean v1 = b[c[1]] == 1;
                    timescale = u32(b, c[1] + (v1 ? 20 : 12));
                    duracao = v1 ? u64(b, c[1] + 24) : u32(b, c[1] + 16);
                } else if ("trak".equals(tipo)) {
                    lerTrak(c[1], c[2]);
                }
            }
        }

        private void lerTrak(int inicio, int fim) {
            int larguraDaFaixa = 0, alturaDaFaixa = 0;
            String manipulador = null, formato = null;
            for (int[] c : caixas(b, inicio, fim)) {
                String tipo = texto4(b, c[0] + 4);
                if ("tkhd".equals(tipo)) {
                    int base = c[1] + (b[c[1]] == 1 ? 88 : 76);
                    if (base + 8 <= c[2]) {
                        larguraDaFaixa = (int) (u32(b, base) >>> 16);
                        alturaDaFaixa = (int) (u32(b, base + 4) >>> 16);
                    }
                } else if ("mdia".equals(tipo)) {
                    for (int[] m : caixas(b, c[1], c[2])) {
                        String tm = texto4(b, m[0] + 4);
                        if ("hdlr".equals(tm) && m[1] + 12 <= m[2]) {
                            manipulador = texto4(b, m[1] + 8);
                        } else if ("minf".equals(tm)) {
                            formato = formatoDaAmostra(m[1], m[2]);
                        }
                    }
                }
            }
            if ("vide".equals(manipulador) && codec == null) {
                codec = formato == null ? "desconhecido" : formato;
                largura = larguraDaFaixa;
                altura = alturaDaFaixa;
            }
        }

        private String formatoDaAmostra(int inicio, int fim) {
            for (int[] stbl : caixas(b, inicio, fim)) {
                if (!"stbl".equals(texto4(b, stbl[0] + 4))) {
                    continue;
                }
                for (int[] stsd : caixas(b, stbl[1], stbl[2])) {
                    if ("stsd".equals(texto4(b, stsd[0] + 4)) && stsd[1] + 16 <= stsd[2]) {
                        return texto4(b, stsd[1] + 12);
                    }
                }
            }
            return null;
        }

        private static long u32(byte[] b, int p) {
            return ((b[p] & 0xFFL) << 24) | ((b[p + 1] & 0xFFL) << 16) | ((b[p + 2] & 0xFFL) << 8) | (b[p + 3] & 0xFFL);
        }

        private static long u64(byte[] b, int p) {
            return (u32(b, p) << 32) | u32(b, p + 4);
        }
    }
}
