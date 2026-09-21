package br.com.adapter.in.web.painel;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.metricas.CatalogoDeBanners;
import br.com.adapter.in.web.metricas.CatalogoDeBanners.Banner;
import br.com.adapter.in.web.metricas.MetricasDeAnuncios;
import br.com.adapter.in.web.metricas.MetricasDeAnuncios.Linha;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Painel de anúncios. O relatório geral e os de cada banner (em /painel) pedem a senha do painel. Cada banner tem
 * também um link privado (/relatorio/{banner}/{código}) que mostra só o relatório dele, para enviar à empresa.
 */
@RestController
class PainelController {

    private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);
    private static final java.util.regex.Pattern ID_VALIDO = java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final AcessoAoPainel acesso;
    private final MetricasDeAnuncios metricas;
    private final CatalogoDeBanners catalogo;
    private final TokenDeRelatorio tokens;

    PainelController(AcessoAoPainel acesso, MetricasDeAnuncios metricas, CatalogoDeBanners catalogo, TokenDeRelatorio tokens) {
        this.acesso = acesso;
        this.metricas = metricas;
        this.catalogo = catalogo;
        this.tokens = tokens;
    }

    @GetMapping("/painel")
    ResponseEntity<String> geral(@RequestParam(value = "mes", required = false) String mes, HttpServletRequest requisicao) {
        ResponseEntity<String> negado = acesso.verificar(requisicao);
        if (negado != null) {
            return negado;
        }
        YearMonth m = mes(mes);
        java.util.Set<String> existentes = idsExistentes();
        ResumoDoMes resumo = ResumoDoMes.de(m, metricas.doMes(m), existentes::contains);
        String base = base(requisicao);
        return html(PaginasDoPainel.geral(resumo, catalogo, b -> linkDeCompartilhar(base, b.id(), m)));
    }

    @GetMapping("/painel/anuncio/{id}")
    ResponseEntity<String> doAnuncio(@PathVariable("id") String id, @RequestParam(value = "mes", required = false) String mes,
                                     HttpServletRequest requisicao) {
        ResponseEntity<String> negado = acesso.verificar(requisicao);
        if (negado != null) {
            return negado;
        }
        return relatorioDoAnuncio(id, mes(mes), false, base(requisicao), "/painel/anuncio/" + id);
    }

    /** Link privado da empresa: o código faz o papel de senha, e só serve para este banner. */
    @GetMapping("/relatorio/{id}/{codigo}")
    ResponseEntity<String> paraAEmpresa(@PathVariable("id") String id, @PathVariable("codigo") String codigo,
                                        @RequestParam(value = "mes", required = false) String mes, HttpServletRequest requisicao) {
        if (!ID_VALIDO.matcher(id).matches() || !tokens.confere(id, codigo)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(HTML).cacheControl(CacheControl.noStore())
                    .body(PaginasDoPainel.naoEncontrada());
        }
        return relatorioDoAnuncio(id, mes(mes), true, base(requisicao), "/relatorio/" + id + "/" + codigo);
    }

    @GetMapping("/painel/exportar.csv")
    ResponseEntity<String> exportar(@RequestParam(value = "mes", required = false) String mes, HttpServletRequest requisicao) {
        ResponseEntity<String> negado = acesso.verificar(requisicao);
        if (negado != null) {
            return negado;
        }
        YearMonth m = mes(mes);
        StringBuilder csv = new StringBuilder("﻿").append("mes;dia;banner;empresa;posicao;perfil;exibicoes;cliques\r\n");
        java.util.Set<String> existentes = idsExistentes();
        for (Linha l : metricas.doMes(m)) {
            if (!existentes.contains(l.anuncioId())) {
                continue; // banner que já foi removido não aparece
            }
            csv.append(m).append(';').append(l.dia()).append(';').append(celula(l.anuncioId())).append(';')
                    .append(celula(catalogo.buscar(l.anuncioId()).map(Banner::empresa).orElse(l.anuncioId()))).append(';')
                    .append(celula(l.posicao())).append(';').append(celula(l.perfil())).append(';')
                    .append(l.exibicoes()).append(';').append(l.cliques()).append("\r\n");
        }
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=\"anuncios-" + m + ".csv\"")
                .cacheControl(CacheControl.noStore()).body(csv.toString());
    }

    private ResponseEntity<String> relatorioDoAnuncio(String id, YearMonth m, boolean publico, String raiz, String caminho) {
        if (!ID_VALIDO.matcher(id).matches()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(HTML).body(PaginasDoPainel.naoEncontrada());
        }
        Banner banner = catalogo.buscar(id).orElse(null);
        if (banner == null) { // banner removido: o relatório dele (e o link que a empresa tinha) deixa de existir
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(HTML).cacheControl(CacheControl.noStore()).body(PaginasDoPainel.naoEncontrada());
        }
        ResumoDoMes resumo = ResumoDoMes.de(m, metricas.doMes(m), id::equals);
        String link = publico ? null : linkDeCompartilhar(raiz, id, m);
        return html(PaginasDoPainel.anuncio(resumo, banner, publico, raiz, caminho, link));
    }

    private java.util.Set<String> idsExistentes() {
        return catalogo.todos().stream().map(Banner::id).collect(java.util.stream.Collectors.toSet());
    }

    private String linkDeCompartilhar(String base, String id, YearMonth mes) {
        return base + "/relatorio/" + id + "/" + tokens.para(id) + "?mes=" + mes;
    }

    private static ResponseEntity<String> html(String corpo) {
        return ResponseEntity.ok().contentType(HTML).cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow").body(corpo);
    }

    /** O mês pedido (AAAA-MM); sem pedido ou inválido, o mês atual. */
    private static YearMonth mes(String texto) {
        try {
            return texto == null ? YearMonth.from(LocalDate.now()) : YearMonth.parse(texto);
        } catch (RuntimeException e) {
            return YearMonth.from(LocalDate.now());
        }
    }

    /** https://servidor (sem caminho), respeitando o proxy do Render. */
    private static String base(HttpServletRequest requisicao) {
        String url = requisicao.getRequestURL().toString();
        return url.substring(0, url.length() - requisicao.getRequestURI().length());
    }

    /** Protege a célula do CSV contra fórmulas de planilha e separadores. */
    private static String celula(String texto) {
        String limpo = texto == null ? "" : texto.replace(';', ',').replace('\n', ' ').replace('\r', ' ');
        return !limpo.isEmpty() && "=+-@".indexOf(limpo.charAt(0)) >= 0 ? "'" + limpo : limpo;
    }
}
