package br.com.adapter.in.web.push;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import br.com.domain.exception.ErroBancoDadosException;

public class DispositivosJdbc implements Dispositivos {

    private final DataSource dataSource;

    public DispositivosJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void registrar(int usuarioId, String token) {
        executar("INSERT INTO dispositivo (token, usuario_id) VALUES (?, ?) "
                + "ON CONFLICT (token) DO UPDATE SET usuario_id = EXCLUDED.usuario_id, atualizado_em = now()",
                token, usuarioId, "registrar dispositivo");
    }

    @Override
    public void remover(int usuarioId, String token) {
        executar("DELETE FROM dispositivo WHERE token = ? AND usuario_id = ?", token, usuarioId, "remover dispositivo");
    }

    @Override
    public void removerTodos(int usuarioId) {
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement("DELETE FROM dispositivo WHERE usuario_id = ?")) {
            ps.setInt(1, usuarioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("remover dispositivos", e);
        }
    }

    @Override
    public void descartar(String token) {
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement("DELETE FROM dispositivo WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("descartar dispositivo", e);
        }
    }

    @Override
    public List<String> tokensDe(int usuarioId) {
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement("SELECT token FROM dispositivo WHERE usuario_id = ?")) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> tokens = new ArrayList<>();
                while (rs.next()) {
                    tokens.add(rs.getString(1));
                }
                return tokens;
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("listar dispositivos", e);
        }
    }

    private void executar(String sql, String token, int usuarioId, String operacao) {
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setInt(2, usuarioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException(operacao, e);
        }
    }
}
