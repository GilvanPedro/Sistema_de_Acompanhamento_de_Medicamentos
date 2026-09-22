package br.com.adapter.in.web.exclusao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import javax.sql.DataSource;

import br.com.domain.exception.ErroBancoDadosException;

public class ExclusoesDeContaJdbc implements ExclusoesDeConta {

    private final DataSource dataSource;

    public ExclusoesDeContaJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void guardar(int usuarioId, String tokenHash, Instant expiraEm) {
        try (Connection conexao = dataSource.getConnection()) {
            try (PreparedStatement ps = conexao.prepareStatement(
                    "UPDATE exclusao_conta SET usado_em = now() WHERE usuario_id = ? AND usado_em IS NULL")) {
                ps.setInt(1, usuarioId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conexao.prepareStatement(
                    "INSERT INTO exclusao_conta (usuario_id, token_hash, expira_em) VALUES (?, ?, ?)")) {
                ps.setInt(1, usuarioId);
                ps.setString(2, tokenHash);
                ps.setObject(3, OffsetDateTime.ofInstant(expiraEm, ZoneOffset.UTC));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conexao.prepareStatement(
                    "DELETE FROM exclusao_conta WHERE expira_em < now() - interval '1 day'")) {
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("guardar pedido de exclusão de conta", e);
        }
    }

    @Override
    public Optional<Integer> consultar(String tokenHash, Instant agora) {
        return buscar("SELECT usuario_id FROM exclusao_conta WHERE token_hash = ? AND usado_em IS NULL AND expira_em > ?",
                tokenHash, agora, "consultar pedido de exclusão de conta");
    }

    @Override
    public Optional<Integer> consumir(String tokenHash, Instant agora) {
        return buscar("UPDATE exclusao_conta SET usado_em = now() WHERE token_hash = ? AND usado_em IS NULL AND expira_em > ? "
                + "RETURNING usuario_id", tokenHash, agora, "usar pedido de exclusão de conta");
    }

    @Override
    public void removerDe(int usuarioId) {
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement("DELETE FROM exclusao_conta WHERE usuario_id = ?")) {
            ps.setInt(1, usuarioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("apagar pedidos de exclusão de conta", e);
        }
    }

    private Optional<Integer> buscar(String sql, String tokenHash, Instant agora, String operacao) {
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ps.setObject(2, OffsetDateTime.ofInstant(agora, ZoneOffset.UTC));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException(operacao, e);
        }
    }
}
