package br.com.adapter.in.web.metricas;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Os banners que existem. Os de verdade ficam no banco (cadastrados pelo painel); enquanto não houver nenhum, vale só o
 * banner de exemplo que acompanha o projeto ({@code static/anuncios-exemplo}). Serve para saber quais ids são válidos ao
 * contar eventos, calcular os pesos e mostrar nome e imagem nos relatórios.
 */
public class CatalogoDeBanners {

    /**
     * {@code imagem}: caminho da imagem no servidor, começando com "/" (por exemplo /anuncios/padaria.png?v=123).
     * {@code video}: caminho do vídeo curto (MP4), ou null se o banner só tem imagem.
     * {@code peso}: parte desejada das exibições (1 = igual aos outros; 2 = o dobro; 0 = pausado).
     */
    public record Banner(String id, String empresa, String texto, String imagem, String video, String link, double peso) { }

    private static final long VALIDADE_MS = 30_000;

    private final BancoDeBanners banco;
    private final List<Banner> exemplos;
    private volatile List<Banner> guardado = List.of();
    private volatile long guardadoEm;
    private volatile boolean sohExemplos;

    public CatalogoDeBanners(BancoDeBanners banco) {
        this.banco = banco;
        this.exemplos = carregarExemplos();
    }

    /** Os banners de agora (a lista é guardada por 30 segundos para não ir ao banco a cada pedido). */
    public List<Banner> todos() {
        long agora = System.currentTimeMillis();
        if (agora - guardadoEm < VALIDADE_MS) {
            return guardado;
        }
        List<Banner> doBanco = new ArrayList<>();
        for (BancoDeBanners.Registro r : banco.listar()) {
            doBanco.add(new Banner(r.id(), r.empresa(), r.texto(), "/anuncios/" + r.id() + "." + r.extensao() + "?v=" + r.versao(),
                    r.temVideo() ? "/anuncios/" + r.id() + ".mp4?v=" + r.versao() : null, r.link(), r.peso()));
        }
        sohExemplos = doBanco.isEmpty();
        guardado = doBanco.isEmpty() ? exemplos : List.copyOf(doBanco);
        guardadoEm = agora;
        return guardado;
    }

    public Optional<Banner> buscar(String id) {
        return todos().stream().filter(b -> b.id().equals(id)).findFirst();
    }

    /** true se ainda não há nenhum banner cadastrado e o que aparece é só o exemplo do projeto. */
    public boolean somenteExemplos() {
        todos();
        return sohExemplos;
    }

    /** Depois de cadastrar, alterar ou remover: a próxima consulta vai ao banco. */
    public void esquecer() {
        guardadoEm = 0;
    }

    private static List<Banner> carregarExemplos() {
        List<Banner> lidos = new ArrayList<>();
        try (InputStream in = new ClassPathResource("static/anuncios-exemplo/anuncios.json").getInputStream()) {
            for (JsonNode n : new ObjectMapper().readTree(in).path("anuncios")) {
                String id = n.path("id").asText("");
                if (!id.isBlank()) {
                    lidos.add(new Banner(id, n.path("empresa").asText(id), n.path("texto").asText(""),
                            "/anuncios-exemplo/" + n.path("imagem").asText(""), null, n.hasNonNull("link") ? n.get("link").asText() : null, 1.0));
                }
            }
        } catch (IOException e) {
            // sem exemplo legível: a lista fica vazia
        }
        return List.copyOf(lidos);
    }
}
