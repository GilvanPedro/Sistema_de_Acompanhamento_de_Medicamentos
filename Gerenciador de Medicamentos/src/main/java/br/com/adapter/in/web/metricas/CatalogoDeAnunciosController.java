package br.com.adapter.in.web.metricas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
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

    private static final Pattern NOME_DO_ARQUIVO = Pattern.compile("([a-z0-9-]{1,64})\\.(png|jpg|mp4)");

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
            if (b.banner().video() != null) {
                a.put("video", base + b.banner().video());
            }
            if (b.banner().link() != null) {
                a.put("link", b.banner().link());
            }
            a.put("texto", b.banner().texto());
            a.put("peso", b.peso());
            anuncios.add(a);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("anuncios", anuncios));
    }

    /**
     * A imagem ou o vídeo de um banner cadastrado. O {@code ?v=} do endereço só serve para o app buscar de novo quando o
     * arquivo muda. Devolve como {@code Resource} para o Spring atender pedidos parciais (Range), que os players de
     * vídeo usam.
     */
    @GetMapping("/anuncios/{nome}")
    ResponseEntity<Resource> arquivo(@PathVariable("nome") String nome) {
        Matcher m = NOME_DO_ARQUIVO.matcher(nome);
        if (!m.matches()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String id = m.group(1);
        if ("mp4".equals(m.group(2))) {
            return banco.video(id).map(bytes -> resposta(bytes, "video/mp4")).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }
        return banco.imagem(id).map(i -> resposta(i.bytes(), i.tipo())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private static ResponseEntity<Resource> resposta(byte[] bytes, String tipo) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(tipo))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff").body(new ByteArrayResource(bytes));
    }
}
