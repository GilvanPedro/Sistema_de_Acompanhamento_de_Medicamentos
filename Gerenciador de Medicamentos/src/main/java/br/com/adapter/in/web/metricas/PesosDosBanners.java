package br.com.adapter.in.web.metricas;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import br.com.adapter.in.web.metricas.CatalogoDeBanners.Banner;

/**
 * Rodízio justo dos banners. O app sorteia um banner a cada tela, mas sorteio puro só iguala as exibições "na média":
 * um banner novo começa atrás, e com muitos banners a diferença entre eles cresce. Aqui o servidor olha quantas
 * exibições cada banner teve nos últimos dias e dá um peso maior a quem está abaixo da sua parte e menor a quem está
 * acima. O app sorteia com esses pesos, então as quantidades tendem a se igualar sozinhas, inclusive quando entram
 * banners novos.
 */
public class PesosDosBanners {

    /** Só os últimos dias contam: um banner novo alcança os outros sem ser inundado durante meses. */
    static final int JANELA_DIAS = 14;
    /** Suaviza contagens pequenas: com poucas exibições, os pesos mal se mexem. */
    static final double SUAVIZACAO = 30;
    /**
     * Força da correção. 1 corrigiria na proporção exata do atraso, mas exagera e oscila (em simulação ficava pior que o
     * sorteio simples com muitas exibições). 0,5 foi o melhor equilíbrio: alcança bem os banners novos e, no dia a dia,
     * fica igual ou melhor que o sorteio simples em todos os volumes testados.
     */
    static final double GANHO = 0.5;
    /** Quanto o ajuste pode favorecer ou frear um banner (de 1/4 a 4 vezes a sua parte). */
    static final double AJUSTE_MINIMO = 0.25;
    static final double AJUSTE_MAXIMO = 4.0;
    private static final long VALIDADE_DO_CALCULO_MS = 60_000;

    public record BannerComPeso(Banner banner, double peso) { }

    private final CatalogoDeBanners catalogo;
    private final MetricasDeAnuncios metricas;
    private final Supplier<LocalDate> hoje;

    private volatile List<BannerComPeso> guardado = List.of();
    private volatile long guardadoEm;

    public PesosDosBanners(CatalogoDeBanners catalogo, MetricasDeAnuncios metricas) {
        this(catalogo, metricas, LocalDate::now);
    }

    public PesosDosBanners(CatalogoDeBanners catalogo, MetricasDeAnuncios metricas, Supplier<LocalDate> hoje) {
        this.catalogo = catalogo;
        this.metricas = metricas;
        this.hoje = hoje;
    }

    /** Depois de mexer nos banners: o próximo pedido recalcula. */
    public void esquecer() {
        guardadoEm = 0;
    }

    /** Os banners com o peso de agora (calculado no máximo uma vez por minuto, para não pesar no banco). */
    public List<BannerComPeso> atuais() {
        long agora = System.currentTimeMillis();
        if (agora - guardadoEm < VALIDADE_DO_CALCULO_MS && !guardado.isEmpty()) {
            return guardado;
        }
        LocalDate fim = hoje.get();
        Map<String, Long> exibicoes = new LinkedHashMap<>();
        for (MetricasDeAnuncios.Linha l : metricas.entre(fim.minusDays(JANELA_DIAS - 1L), fim)) {
            exibicoes.merge(l.anuncioId(), l.exibicoes(), Long::sum);
        }
        List<BannerComPeso> calculado = calcular(catalogo.todos(), exibicoes);
        guardado = calculado;
        guardadoEm = agora;
        return calculado;
    }

    /**
     * O peso de cada banner. A parte desejada de cada um vem do {@code peso} do catálogo (padrão 1, todos iguais);
     * o ajuste compara as exibições recentes com essa parte. Banner com peso 0 no catálogo fica de fora (pausado).
     */
    static List<BannerComPeso> calcular(List<Banner> banners, Map<String, Long> exibicoesNaJanela) {
        double somaDosPesos = banners.stream().mapToDouble(b -> Math.max(b.peso(), 0)).sum();
        long total = banners.stream().mapToLong(b -> exibicoesNaJanela.getOrDefault(b.id(), 0L)).sum();
        List<BannerComPeso> resultado = new ArrayList<>();
        for (Banner b : banners) {
            double parte = somaDosPesos <= 0 ? 1.0 / banners.size() : Math.max(b.peso(), 0) / somaDosPesos;
            if (parte <= 0) {
                continue; // pausado
            }
            double alvo = total * parte;
            double feitas = exibicoesNaJanela.getOrDefault(b.id(), 0L);
            double ajuste = Math.min(AJUSTE_MAXIMO, Math.max(AJUSTE_MINIMO, Math.pow((alvo + SUAVIZACAO) / (feitas + SUAVIZACAO), GANHO)));
            resultado.add(new BannerComPeso(b, Math.round(parte * ajuste * banners.size() * 10_000.0) / 10_000.0));
        }
        return resultado;
    }
}
