package br.com.adapter.in.web.metricas;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import br.com.domain.exception.ErroBancoDadosException;

public class MetricasDeAnunciosJdbc implements MetricasDeAnuncios {

    private final DataSource dataSource;

    public MetricasDeAnunciosJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void somar(LocalDate dia, String anuncioId, String posicao, String perfil, long exibicoes, long cliques) {
        String sql = "INSERT INTO metrica_anuncio (dia, anuncio_id, posicao, perfil, exibicoes, cliques) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (dia, anuncio_id, posicao, perfil) DO UPDATE SET "
                + "exibicoes = metrica_anuncio.exibicoes + EXCLUDED.exibicoes, cliques = metrica_anuncio.cliques + EXCLUDED.cliques";
        try (Connection conexao = dataSource.getConnection(); PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(dia));
            ps.setString(2, anuncioId);
            ps.setString(3, posicao);
            ps.setString(4, perfil);
            ps.setLong(5, exibicoes);
            ps.setLong(6, cliques);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("somar métrica de anúncio", e);
        }
    }

    @Override
    public List<Linha> doMes(YearMonth mes) {
        String sql = "SELECT dia, anuncio_id, posicao, perfil, exibicoes, cliques FROM metrica_anuncio "
                + "WHERE dia >= ? AND dia < ? ORDER BY dia, anuncio_id, posicao, perfil";
        try (Connection conexao = dataSource.getConnection(); PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(mes.atDay(1)));
            ps.setDate(2, Date.valueOf(mes.plusMonths(1).atDay(1)));
            try (ResultSet rs = ps.executeQuery()) {
                List<Linha> linhas = new ArrayList<>();
                while (rs.next()) {
                    linhas.add(new Linha(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getLong(5), rs.getLong(6)));
                }
                return linhas;
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("ler métricas de anúncios", e);
        }
    }
}
