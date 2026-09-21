package br.com.adapter.in.web.metricas;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Contagens anônimas de exibições e toques nos banners (somas por dia, banner, posição e perfil). */
public interface MetricasDeAnuncios {

    record Linha(LocalDate dia, String anuncioId, String posicao, String perfil, long exibicoes, long cliques) { }

    /** Soma as quantidades às contagens do dia (cria a linha se ainda não existir). */
    void somar(LocalDate dia, String anuncioId, String posicao, String perfil, long exibicoes, long cliques);

    /** Todas as linhas de um mês. */
    List<Linha> doMes(YearMonth mes);

    /** Todas as linhas entre duas datas (inclusive). */
    List<Linha> entre(LocalDate inicio, LocalDate fim);
}
