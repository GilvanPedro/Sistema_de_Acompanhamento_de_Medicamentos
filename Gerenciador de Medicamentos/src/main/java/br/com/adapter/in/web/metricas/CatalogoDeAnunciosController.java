package br.com.adapter.in.web.metricas;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.metricas.PesosDosBanners.BannerComPeso;
import jakarta.servlet.http.HttpServletRequest;

/**
 * A lista de banners que o app usa, já com o peso de cada um (rodízio justo, ver {@link PesosDosBanners}). Público e
 * sem dado de ninguém. As imagens vêm com endereço completo. Versões antigas do app usam o arquivo estático
 * (/anuncios/anuncios.json), que continua existindo, com sorteio simples.
 */
@RestController
class CatalogoDeAnunciosController {

    private final PesosDosBanners pesos;

    CatalogoDeAnunciosController(PesosDosBanners pesos) {
        this.pesos = pesos;
    }

    @GetMapping("/api/v1/anuncios")
    ResponseEntity<Map<String, Object>> lista(HttpServletRequest requisicao) {
        String url = requisicao.getRequestURL().toString();
        String base = url.substring(0, url.length() - requisicao.getRequestURI().length());
        List<Map<String, Object>> anuncios = new ArrayList<>();
        for (BannerComPeso b : pesos.atuais()) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", b.banner().id());
            a.put("empresa", b.banner().empresa());
            a.put("imagem", URI.create(base + "/anuncios/").resolve(b.banner().imagem()).toString());
            if (b.banner().link() != null) {
                a.put("link", b.banner().link());
            }
            a.put("texto", b.banner().texto());
            a.put("peso", b.peso());
            anuncios.add(a);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("anuncios", anuncios));
    }
}
