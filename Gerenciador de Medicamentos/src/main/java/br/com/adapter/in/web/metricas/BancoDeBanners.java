package br.com.adapter.in.web.metricas;

import java.util.List;
import java.util.Optional;

/** Onde ficam os banners cadastrados pelo painel (texto na tabela, imagem junto). */
public interface BancoDeBanners {

    /** Um banner sem os bytes da imagem. {@code versao} muda quando o banner é alterado (serve para renovar o cache). */
    record Registro(String id, String empresa, String texto, String link, double peso, String tipoImagem, long versao) {
        public String extensao() {
            return "image/png".equals(tipoImagem) ? "png" : "jpg";
        }
    }

    record Imagem(String tipo, byte[] bytes) { }

    /** Todos os banners (sem as imagens), do mais antigo ao mais novo. */
    List<Registro> listar();

    Optional<Registro> buscar(String id);

    Optional<Imagem> imagem(String id);

    /** Cria ou atualiza. {@code imagem} nula mantém a imagem que já existe (só edição de texto). */
    void salvar(Registro registro, byte[] imagem);

    /** true se existia. */
    boolean remover(String id);
}
