package br.com.adapter.in.web.metricas;

import java.util.List;
import java.util.Optional;

/** Onde ficam os banners cadastrados pelo painel (texto na tabela, imagem junto). */
public interface BancoDeBanners {

    /** Um banner sem os bytes da imagem. {@code versao} muda quando o banner é alterado (serve para renovar o cache). */
    record Registro(String id, String empresa, String texto, String link, double peso, String tipoImagem, long versao,
                    boolean temVideo, int duracaoVideoMs) {

        /** Um banner só com imagem. */
        public Registro(String id, String empresa, String texto, String link, double peso, String tipoImagem, long versao) {
            this(id, empresa, texto, link, peso, tipoImagem, versao, false, 0);
        }

        public String extensao() {
            return "image/png".equals(tipoImagem) ? "png" : "jpg";
        }
    }

    record Imagem(String tipo, byte[] bytes) { }

    /** Todos os banners (sem as imagens), do mais antigo ao mais novo. */
    List<Registro> listar();

    Optional<Registro> buscar(String id);

    Optional<Imagem> imagem(String id);

    /** Os bytes do vídeo (MP4) do banner, se ele tiver. */
    Optional<byte[]> video(String id);

    /**
     * Cria ou atualiza. {@code imagem} nula mantém a imagem que já existe (só edição de texto). {@code video} não nulo
     * troca o vídeo (a duração vem do registro); {@code removerVideo} apaga o vídeo; sem nenhum dos dois, o vídeo fica como está.
     */
    void salvar(Registro registro, byte[] imagem, byte[] video, boolean removerVideo);

    /** Cria ou atualiza sem mexer no vídeo. */
    default void salvar(Registro registro, byte[] imagem) {
        salvar(registro, imagem, null, false);
    }

    /** true se existia. */
    boolean remover(String id);
}
