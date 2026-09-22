package br.com.adapter.in.web.metricas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import br.com.adapter.in.web.metricas.CatalogoDeBanners.Banner;
import br.com.adapter.in.web.metricas.PesosDosBanners.BannerComPeso;

class PesosDosBannersTest {

    private static Banner banner(String id, double peso) {
        return new Banner(id, id, "", id + ".png", null, null, peso);
    }

    private static List<Banner> banners(int n) {
        List<Banner> lista = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            lista.add(banner("b" + i, 1.0));
        }
        return lista;
    }

    private static double peso(List<BannerComPeso> pesos, String id) {
        return pesos.stream().filter(p -> p.banner().id().equals(id)).findFirst().orElseThrow().peso();
    }

    @Test
    void semExibicoesOuComContagensIguaisTodosTemOMesmoPeso() {
        for (BannerComPeso p : PesosDosBanners.calcular(banners(4), Map.of())) {
            assertEquals(1.0, p.peso(), 1e-9);
        }
        for (BannerComPeso p : PesosDosBanners.calcular(banners(3), Map.of("b1", 500L, "b2", 500L, "b3", 500L))) {
            assertEquals(1.0, p.peso(), 1e-9);
        }
    }

    @Test
    void quemEstaAtrasadoGanhaPesoEQuemEstaNaFrenteEhFreado() {
        var pesos = PesosDosBanners.calcular(banners(3), Map.of("b1", 300L, "b2", 1000L, "b3", 1700L));
        assertTrue(peso(pesos, "b1") > 1.0, "atrasado deveria ganhar peso");
        assertTrue(peso(pesos, "b3") < 1.0, "na frente deveria perder peso");
        assertTrue(peso(pesos, "b1") > peso(pesos, "b2") && peso(pesos, "b2") > peso(pesos, "b3"));
    }

    @Test
    void oAjusteTemLimiteParaUmBannerNovoNaoInundarAsTelas() {
        var pesos = PesosDosBanners.calcular(banners(3), Map.of("b1", 10_000L, "b2", 10_000L)); // b3 é novo, com zero
        assertEquals(4.0, peso(pesos, "b3"), 1e-9, "no máximo 4 vezes a sua parte");
        assertEquals(peso(pesos, "b1"), peso(pesos, "b2"), 1e-9);
        assertTrue(peso(pesos, "b1") > 0.24, "quem está na frente ainda aparece");
    }

    @Test
    void pesoManualDaOParteMaiorEPesoZeroPausa() {
        List<Banner> lista = List.of(banner("premium", 2.0), banner("comum", 1.0), banner("pausado", 0.0));
        var pesos = PesosDosBanners.calcular(lista, Map.of());
        assertEquals(2, pesos.size(), "banner pausado não é servido");
        assertEquals(2.0 * peso(pesos, "comum"), peso(pesos, "premium"), 1e-3);

        // com as exibições já na proporção 2:1, o ajuste não mexe em nada
        var equilibrado = PesosDosBanners.calcular(lista, Map.of("premium", 2000L, "comum", 1000L));
        assertEquals(2.0 * peso(equilibrado, "comum"), peso(equilibrado, "premium"), 1e-3);
    }

    @Test
    void catalogoVazioOuTodosPausadosNaoQuebraEmostraNada() {
        assertTrue(PesosDosBanners.calcular(List.of(), Map.of()).isEmpty());
        // com todos pausados (nenhum peso positivo), a lista fica vazia: nunca volta a exibir quem foi pausado
        assertTrue(PesosDosBanners.calcular(List.of(banner("a", 0), banner("b", 0)), Map.of()).isEmpty());
        assertTrue(PesosDosBanners.calcular(List.of(banner("a", -1)), Map.of()).isEmpty(), "peso negativo também conta como pausado");
    }

    @Test
    void umUnicoBannerPausadoNaoAparece() {
        // é o caso relatado no uso real: só existe um banner cadastrado, e ele foi pausado
        assertTrue(PesosDosBanners.calcular(List.of(banner("unico", 0)), Map.of()).isEmpty());
        assertTrue(PesosDosBanners.calcular(List.of(banner("unico", 0)), Map.of("unico", 500L)).isEmpty(),
                "mesmo com exibições antigas registradas, continua pausado");
    }

    /**
     * Simula 60 dias: 6 banners, e mais 4 entram no dia 30. Devolve, para os últimos 14 dias, quanto o maior e o menor
     * banner se afastam da média (em %). {@code justo} liga o rodízio por pesos; desligado, o sorteio é simples.
     * O servidor recalcula os pesos várias vezes ao dia: aqui, a cada 1/10 das exibições do dia.
     */
    private static double afastamentoNosUltimosDias(boolean justo, int exibicoesPorDia, long semente) {
        return afastamentoNoDia(justo, exibicoesPorDia, semente, 59);
    }

    private static double afastamentoNoDia(boolean justo, int exibicoesPorDia, long semente, int diaDaAvaliacao) {
        Random sorteio = new Random(semente);
        List<Banner> ativos = new ArrayList<>(banners(6));
        Map<String, long[]> porDia = new HashMap<>();
        for (int dia = 0; dia <= diaDaAvaliacao; dia++) {
            if (dia == 30) {
                for (int i = 7; i <= 10; i++) {
                    ativos.add(banner("b" + i, 1.0));
                }
            }
            for (int lote = 0; lote < 10; lote++) {
                Map<String, Long> janela = new HashMap<>();
                for (Banner b : ativos) {
                    long[] dias = porDia.computeIfAbsent(b.id(), k -> new long[60]);
                    long soma = 0;
                    for (int d = Math.max(0, dia - 13); d <= dia; d++) {
                        soma += dias[d];
                    }
                    janela.put(b.id(), soma);
                }
                List<BannerComPeso> pesos = justo
                        ? PesosDosBanners.calcular(ativos, janela)
                        : ativos.stream().map(b -> new BannerComPeso(b, 1.0)).toList();
                double total = pesos.stream().mapToDouble(BannerComPeso::peso).sum();
                for (int i = 0; i < exibicoesPorDia / 10; i++) {
                    double r = sorteio.nextDouble() * total;
                    for (BannerComPeso p : pesos) {
                        r -= p.peso();
                        if (r <= 0) {
                            porDia.get(p.banner().id())[dia]++;
                            break;
                        }
                    }
                }
            }
        }
        long menor = Long.MAX_VALUE, maior = 0, soma = 0;
        for (Banner b : ativos) {
            long n = 0;
            for (int d = diaDaAvaliacao - 13; d <= diaDaAvaliacao; d++) {
                n += porDia.get(b.id())[d];
            }
            menor = Math.min(menor, n);
            maior = Math.max(maior, n);
            soma += n;
        }
        double media = soma / (double) ativos.size();
        return 100.0 * Math.max(maior - media, media - menor) / media;
    }

    @Test
    void asExibicoesSeIgualamMesmoQuandoEntramBannersNovosNoMeio() {
        for (long semente = 1; semente <= 5; semente++) {
            double afastamento = afastamentoNosUltimosDias(true, 300, semente);
            assertTrue(afastamento <= 10.0, "com 300 exibições por dia, todos deveriam ficar a menos de 10% da média (deu " + afastamento + "%, semente " + semente + ")");
        }
    }

    private static double media(boolean justo, int exibicoesPorDia, int dia) {
        double soma = 0;
        for (long semente = 1; semente <= 20; semente++) {
            soma += afastamentoNoDia(justo, exibicoesPorDia, semente, dia);
        }
        return soma / 20;
    }

    /** Dez dias depois de entrarem 4 banners novos, o sorteio simples ainda os deixa bem atrás; o rodízio já os alcançou. */
    @Test
    void bannersNovosAlcancamOsOutrosBemMaisRapidoQueNoSorteioSimples() {
        for (int volume : new int[]{60, 300, 3000}) {
            double justo = media(true, volume, 40), simples = media(false, volume, 40);
            System.out.printf("dia 40, %d exibicoes/dia: rodizio justo=%.1f%%  sorteio simples=%.1f%%%n", volume, justo, simples);
            assertTrue(justo < simples * 0.7, "com " + volume + " por dia, o rodízio deveria ganhar bastante (justo=" + justo + "%, simples=" + simples + "%)");
        }
    }

    /** No dia a dia o rodízio não pode ser pior que o sorteio simples (com correção forte demais ele era). */
    @Test
    void noDiaADiaORodizioNaoEPiorQueOSorteioSimples() {
        for (int volume : new int[]{60, 300, 3000}) {
            double justo = media(true, volume, 59), simples = media(false, volume, 59);
            System.out.printf("dia 59, %d exibicoes/dia: rodizio justo=%.1f%%  sorteio simples=%.1f%%%n", volume, justo, simples);
            assertTrue(justo <= simples, "com " + volume + " por dia o rodízio não deveria piorar (justo=" + justo + "%, simples=" + simples + "%)");
        }
    }
}
