package br.com.adapter.in.web.privacidade;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import br.com.domain.exception.ErroBancoDadosException;

public class ConsentimentosJdbc implements Consentimentos {

    private final DataSource dataSource;

    public ConsentimentosJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void registrar(int usuarioId, String versao) {
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(
                     "INSERT INTO consentimento (usuario_id, versao) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setInt(1, usuarioId);
            ps.setString(2, versao);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("registrar consentimento", e);
        }
    }

    @Override
    public Optional<String> versaoAceita(int usuarioId) {
        List<Aceite> aceites = todos(usuarioId);
        return aceites.isEmpty() ? Optional.empty() : Optional.of(aceites.get(aceites.size() - 1).versao());
    }

    @Override
    public List<Aceite> todos(int usuarioId) {
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(
                     "SELECT versao, aceito_em FROM consentimento WHERE usuario_id = ? ORDER BY aceito_em")) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Aceite> aceites = new ArrayList<>();
                while (rs.next()) {
                    aceites.add(new Aceite(rs.getString(1), rs.getObject(2, OffsetDateTime.class)));
                }
                return aceites;
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("listar consentimentos", e);
        }
    }

    @Override
    public void removerDe(int usuarioId) {
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement("DELETE FROM consentimento WHERE usuario_id = ?")) {
            ps.setInt(1, usuarioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("remover consentimentos", e);
        }
    }
}
