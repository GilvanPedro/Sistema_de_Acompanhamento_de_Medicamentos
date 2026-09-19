package br.com.adapter.in.web.idempotencia;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import br.com.domain.exception.ErroBancoDadosException;

public class ChavesDeIdempotenciaJdbc implements ChavesDeIdempotencia {

    private final DataSource dataSource;

    public ChavesDeIdempotenciaJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Integer> buscar(int usuarioId, String chave) {
        String sql = "SELECT recurso_id FROM chave_idempotencia WHERE usuario_id = ? AND chave = ?";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            ps.setString(2, chave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("buscar chave de idempotência", e);
        }
    }

    @Override
    public void salvar(int usuarioId, String chave, int recursoId) {
        try (Connection conexao = dataSource.getConnection()) {
            try (PreparedStatement limpeza = conexao.prepareStatement(
                    "DELETE FROM chave_idempotencia WHERE criado_em < now() - interval '14 days'")) {
                limpeza.executeUpdate(); // chaves antigas não servem mais: nenhum reenvio demora tanto
            }
            try (PreparedStatement ps = conexao.prepareStatement(
                    "INSERT INTO chave_idempotencia (usuario_id, chave, recurso_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")) {
                ps.setInt(1, usuarioId);
                ps.setString(2, chave);
                ps.setInt(3, recursoId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("salvar chave de idempotência", e);
        }
    }
}
