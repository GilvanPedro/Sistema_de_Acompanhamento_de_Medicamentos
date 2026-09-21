package br.com.adapter.in.web;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import br.com.adapter.in.web.metricas.BancoDeBanners;

/** Banco de banners dos testes, já com três banners de exemplo (exemplo-1, exemplo-2 e exemplo-3). */
class BancoDeBannersEmMemoria implements BancoDeBanners {

    private final Map<String, Registro> registros = new LinkedHashMap<>();
    private final Map<String, Imagem> imagens = new LinkedHashMap<>();
    private final Map<String, byte[]> videos = new LinkedHashMap<>();
    private long relogio = 1_000;

    BancoDeBannersEmMemoria() {
        salvar(new Registro("exemplo-1", "Espaço para anunciar (exemplo 1)", "Anuncie aqui", "mailto:contato@exemplo.com?subject=Quero%20anunciar", 1.0, "image/png", 0), png(1280, 400));
        salvar(new Registro("exemplo-2", "Espaço para anunciar (exemplo 2)", "Seu banner aqui", null, 1.0, "image/png", 0), png(1280, 400));
        salvar(new Registro("exemplo-3", "CuidaMed (política de privacidade)", "Leia a política", "https://exemplo.com/politica", 1.0, "image/png", 0), png(640, 200));
    }

    static byte[] png(int largura, int altura) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB), "png", saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized List<Registro> listar() {
        return new ArrayList<>(registros.values());
    }

    @Override
    public synchronized Optional<Registro> buscar(String id) {
        return Optional.ofNullable(registros.get(id));
    }

    @Override
    public synchronized Optional<Imagem> imagem(String id) {
        return Optional.ofNullable(imagens.get(id));
    }

    @Override
    public synchronized Optional<byte[]> video(String id) {
        return Optional.ofNullable(videos.get(id));
    }

    @Override
    public synchronized void salvar(Registro r, byte[] imagem, byte[] video, boolean removerVideo) {
        relogio++;
        if (imagem != null) {
            imagens.put(r.id(), new Imagem(r.tipoImagem(), imagem));
        } else if (!registros.containsKey(r.id())) {
            return; // edição de banner que não existe
        }
        if (video != null) {
            videos.put(r.id(), video);
        } else if (removerVideo) {
            videos.remove(r.id());
        }
        boolean temVideo = videos.containsKey(r.id());
        registros.put(r.id(), new Registro(r.id(), r.empresa(), r.texto(), r.link(), r.peso(), r.tipoImagem(), relogio, temVideo, temVideo ? r.duracaoVideoMs() : 0));
    }

    @Override
    public synchronized boolean remover(String id) {
        imagens.remove(id);
        videos.remove(id);
        return registros.remove(id) != null;
    }
}
