package br.com.adapter.in.web.auth;

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

public class RefreshTokenJdbcStore implements RefreshTokenStore {

    private final DataSource dataSource;

    public RefreshTokenJdbcStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void salvar(String hash, int usuarioId, Instant expiraEm) {
        String sql = "INSERT INTO refresh_token (usuario_id, token_hash, expira_em) VALUES (?, ?, ?)";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            ps.setString(2, hash);
            ps.setObject(3, OffsetDateTime.ofInstant(expiraEm, ZoneOffset.UTC));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("salvar token de renovação", e);
        }
    }

    @Override
    public Optional<RefreshRegistro> buscar(String hash) {
        String sql = "SELECT usuario_id, expira_em, revogado_em IS NOT NULL AS revogado FROM refresh_token WHERE token_hash = ?";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new RefreshRegistro(
                        rs.getInt("usuario_id"),
                        rs.getObject("expira_em", OffsetDateTime.class).toInstant(),
                        rs.getBoolean("revogado")));
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("buscar token de renovação", e);
        }
    }

    @Override
    public boolean revogar(String hash) {
        String sql = "UPDATE refresh_token SET revogado_em = now() WHERE token_hash = ? AND revogado_em IS NULL";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, hash);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new ErroBancoDadosException("revogar token de renovação", e);
        }
    }

    @Override
    public void revogarTodos(int usuarioId) {
        String sql = "UPDATE refresh_token SET revogado_em = now() WHERE usuario_id = ? AND revogado_em IS NULL";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("revogar tokens do usuário", e);
        }
    }
}
