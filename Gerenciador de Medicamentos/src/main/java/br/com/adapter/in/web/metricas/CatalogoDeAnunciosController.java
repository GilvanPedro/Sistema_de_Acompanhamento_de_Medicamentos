package br.com.adapter.in.web.metricas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.metricas.PesosDosBanners.BannerComPeso;
import jakarta.servlet.http.HttpServletRequest;

/**
 * A lista de banners que o app usa (com o peso de cada um, ver {@link PesosDosBanners}) e as imagens. Tudo público e sem
 * dado de ninguém. A lista também sai em {@code /anuncios/anuncios.json}, o endereço que as versões antigas do app usam.
 */
@RestController
class CatalogoDeAnunciosController {

    private static final Pattern NOME_DA_IMAGEM = Pattern.compile("([a-z0-9-]{1,64})\\.(png|jpg)");

    private final PesosDosBanners pesos;
    private final BancoDeBanners banco;

    CatalogoDeAnunciosController(PesosDosBanners pesos, BancoDeBanners banco) {
        this.pesos = pesos;
        this.banco = banco;
    }

    @GetMapping({"/api/v1/anuncios", "/anuncios/anuncios.json"})
    ResponseEntity<Map<String, Object>> lista(HttpServletRequest requisicao) {
        String url = requisicao.getRequestURL().toString();
        String base = url.substring(0, url.length() - requisicao.getRequestURI().length());
        List<Map<String, Object>> anuncios = new ArrayList<>();
        for (BannerComPeso b : pesos.atuais()) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", b.banner().id());
            a.put("empresa", b.banner().empresa());
            a.put("imagem", base + b.banner().imagem());
            if (b.banner().link() != null) {
                a.put("link", b.banner().link());
            }
            a.put("texto", b.banner().texto());
            a.put("peso", b.peso());
            anuncios.add(a);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("anuncios", anuncios));
    }

    /** A imagem de um banner cadastrado. O {@code ?v=} do endereço só serve para o app buscar de novo quando ela muda. */
    @GetMapping("/anuncios/{nome}")
    ResponseEntity<byte[]> imagem(@PathVariable("nome") String nome) {
        Matcher m = NOME_DA_IMAGEM.matcher(nome);
        if (!m.matches()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return banco.imagem(m.group(1))
                .map(i -> ResponseEntity.ok().contentType(MediaType.parseMediaType(i.tipo()))
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                        .header("X-Content-Type-Options", "nosniff").body(i.bytes()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
