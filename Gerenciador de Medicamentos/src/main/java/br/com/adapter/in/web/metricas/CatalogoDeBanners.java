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
 * Os banners que existem (o mesmo {@code anuncios.json} que o app baixa). Serve para saber quais ids são válidos ao
 * contar eventos e para mostrar o nome da empresa e a imagem nos relatórios.
 */
public class CatalogoDeBanners {

    public record Banner(String id, String empresa, String texto, String imagem) { }

    private final List<Banner> banners;

    public CatalogoDeBanners() {
        List<Banner> lidos = new ArrayList<>();
        try (InputStream in = new ClassPathResource("static/anuncios/anuncios.json").getInputStream()) {
            for (JsonNode n : new ObjectMapper().readTree(in).path("anuncios")) {
                String id = n.path("id").asText("");
                if (!id.isBlank()) {
                    lidos.add(new Banner(id, n.path("empresa").asText(id), n.path("texto").asText(""), n.path("imagem").asText("")));
                }
            }
        } catch (IOException e) {
            // sem catálogo legível: nenhum id é aceito (e os relatórios ficam vazios)
        }
        this.banners = List.copyOf(lidos);
    }

    public List<Banner> todos() {
        return banners;
    }

    public Optional<Banner> buscar(String id) {
        return banners.stream().filter(b -> b.id().equals(id)).findFirst();
    }
}
