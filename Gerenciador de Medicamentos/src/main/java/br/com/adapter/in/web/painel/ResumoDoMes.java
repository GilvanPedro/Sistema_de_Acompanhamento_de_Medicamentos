package br.com.adapter.in.web.painel;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import br.com.adapter.in.web.metricas.MetricasDeAnuncios.Linha;

/** As contagens de um mês, somadas de várias formas (para os relatórios). */
public final class ResumoDoMes {

    public record Totais(long exibicoes, long cliques) {
        static final Totais ZERO = new Totais(0, 0);

        Totais somar(long e, long c) {
            return new Totais(exibicoes + e, cliques + c);
        }

        /** Cliques por 100 exibições. */
        public double ctr() {
            return exibicoes == 0 ? 0 : 100.0 * cliques / exibicoes;
        }
    }

    public final YearMonth mes;
    public final Totais geral;
    public final Map<String, Totais> porAnuncio;
    public final Map<String, Totais> porPerfil;
    public final Map<String, Totais> porPosicao;
    public final TreeMap<LocalDate, Totais> porDia;

    private ResumoDoMes(YearMonth mes, Totais geral, Map<String, Totais> porAnuncio, Map<String, Totais> porPerfil,
                        Map<String, Totais> porPosicao, TreeMap<LocalDate, Totais> porDia) {
        this.mes = mes;
        this.geral = geral;
        this.porAnuncio = porAnuncio;
        this.porPerfil = porPerfil;
        this.porPosicao = porPosicao;
        this.porDia = porDia;
    }

    /** Soma as linhas do mês; {@code doAnuncio} escolhe quais banners entram (todos, ou só um). */
    public static ResumoDoMes de(YearMonth mes, List<Linha> linhas, Predicate<String> doAnuncio) {
        Totais geral = Totais.ZERO;
        Map<String, Totais> anuncios = new LinkedHashMap<>();
        Map<String, Totais> perfis = new LinkedHashMap<>();
        Map<String, Totais> posicoes = new LinkedHashMap<>();
        TreeMap<LocalDate, Totais> dias = new TreeMap<>();
        for (Linha l : linhas) {
            if (!doAnuncio.test(l.anuncioId()) || !YearMonth.from(l.dia()).equals(mes)) {
                continue;
            }
            geral = geral.somar(l.exibicoes(), l.cliques());
            anuncios.merge(l.anuncioId(), new Totais(l.exibicoes(), l.cliques()), (a, b) -> a.somar(b.exibicoes(), b.cliques()));
            perfis.merge(l.perfil(), new Totais(l.exibicoes(), l.cliques()), (a, b) -> a.somar(b.exibicoes(), b.cliques()));
            posicoes.merge(l.posicao(), new Totais(l.exibicoes(), l.cliques()), (a, b) -> a.somar(b.exibicoes(), b.cliques()));
            dias.merge(l.dia(), new Totais(l.exibicoes(), l.cliques()), (a, b) -> a.somar(b.exibicoes(), b.cliques()));
        }
        Map<String, Totais> anunciosEmOrdem = new LinkedHashMap<>();
        anuncios.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Totais> e) -> e.getValue().exibicoes()).reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(e -> anunciosEmOrdem.put(e.getKey(), e.getValue()));
        return new ResumoDoMes(mes, geral, anunciosEmOrdem, perfis, posicoes, dias);
    }
}
