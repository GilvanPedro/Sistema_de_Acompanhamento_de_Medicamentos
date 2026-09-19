package br.com.adapter.in.web.push;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

import javax.sql.DataSource;

import br.com.domain.exception.ErroBancoDadosException;

public class AvisosDeAtrasoJdbc implements AvisosDeAtraso {

    private final DataSource dataSource;

    public AvisosDeAtrasoJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean marcarSeNovo(int medicamentoId, LocalDate dia) {
        try (Connection conexao = dataSource.getConnection()) {
            try (PreparedStatement limpeza = conexao.prepareStatement(
                    "DELETE FROM aviso_atraso_enviado WHERE criado_em < now() - interval '7 days'")) {
                limpeza.executeUpdate();
            }
            try (PreparedStatement ps = conexao.prepareStatement(
                    "INSERT INTO aviso_atraso_enviado (medicamento_id, dia) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
                ps.setInt(1, medicamentoId);
                ps.setDate(2, Date.valueOf(dia));
                return ps.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("marcar aviso de atraso", e);
        }
    }
}
