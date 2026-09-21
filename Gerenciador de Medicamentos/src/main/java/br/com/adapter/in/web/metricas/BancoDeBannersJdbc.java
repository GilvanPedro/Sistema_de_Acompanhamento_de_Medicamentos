package br.com.adapter.in.web.metricas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import br.com.domain.exception.ErroBancoDadosException;

public class BancoDeBannersJdbc implements BancoDeBanners {

    private static final String COLUNAS = "id, empresa, texto, link, peso, tipo_imagem, (EXTRACT(EPOCH FROM atualizado_em) * 1000)::bigint";

    private final DataSource dataSource;

    public BancoDeBannersJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Registro> listar() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUNAS + " FROM banner ORDER BY atualizado_em, id");
             ResultSet rs = ps.executeQuery()) {
            List<Registro> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(registro(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new ErroBancoDadosException("listar banners", e);
        }
    }

    @Override
    public Optional<Registro> buscar(String id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUNAS + " FROM banner WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(registro(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("buscar banner", e);
        }
    }

    @Override
    public Optional<Imagem> imagem(String id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT tipo_imagem, imagem FROM banner WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Imagem(rs.getString(1), rs.getBytes(2))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("ler imagem do banner", e);
        }
    }

    @Override
    public void salvar(Registro r, byte[] imagem) {
        try (Connection c = dataSource.getConnection()) {
            if (imagem != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO banner (id, empresa, texto, link, peso, tipo_imagem, imagem) VALUES (?, ?, ?, ?, ?, ?, ?) "
                                + "ON CONFLICT (id) DO UPDATE SET empresa = EXCLUDED.empresa, texto = EXCLUDED.texto, link = EXCLUDED.link, "
                                + "peso = EXCLUDED.peso, tipo_imagem = EXCLUDED.tipo_imagem, imagem = EXCLUDED.imagem, atualizado_em = now()")) {
                    ps.setString(1, r.id());
                    ps.setString(2, r.empresa());
                    ps.setString(3, r.texto());
                    ps.setString(4, r.link());
                    ps.setBigDecimal(5, java.math.BigDecimal.valueOf(r.peso()));
                    ps.setString(6, r.tipoImagem());
                    ps.setBytes(7, imagem);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE banner SET empresa = ?, texto = ?, link = ?, peso = ?, atualizado_em = now() WHERE id = ?")) {
                    ps.setString(1, r.empresa());
                    ps.setString(2, r.texto());
                    ps.setString(3, r.link());
                    ps.setBigDecimal(4, java.math.BigDecimal.valueOf(r.peso()));
                    ps.setString(5, r.id());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("salvar banner", e);
        }
    }

    @Override
    public boolean remover(String id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM banner WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new ErroBancoDadosException("remover banner", e);
        }
    }

    private static Registro registro(ResultSet rs) throws SQLException {
        return new Registro(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDouble(5), rs.getString(6), rs.getLong(7));
    }
}
