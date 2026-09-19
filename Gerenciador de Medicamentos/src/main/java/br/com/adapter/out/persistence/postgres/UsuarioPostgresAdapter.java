package br.com.adapter.out.persistence.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.sql.DataSource;

import br.com.domain.exception.ErroBancoDadosException;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.PedidoVinculo;
import br.com.domain.model.StatusVinculo;
import br.com.domain.model.Usuario;
import br.com.domain.port.out.SalvarUsuarioPort;

public class UsuarioPostgresAdapter implements SalvarUsuarioPort {

    private static final String COLUNAS = "id, tipo, nome, email, senha_hash";

    private final DataSource dataSource;

    public UsuarioPostgresAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void salvar(Usuario usuario) {
        String sql = "INSERT INTO usuario (id, tipo, nome, email, senha_hash) VALUES (?, ?, ?, ?, ?)";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, usuario.getId());
            ps.setString(2, usuario instanceof Idoso ? "IDOSO" : "FAMILIAR");
            ps.setString(3, usuario.getNome());
            ps.setString(4, usuario.getEmail());
            ps.setString(5, usuario.getSenha());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("salvar usuário", e);
        }
    }

    /** Vínculo iniciado pelo próprio idoso: já nasce ACEITO (adicionar o familiar é o consentimento). */
    @Override
    public void salvarVinculo(int idosoId, int familiarId) {
        String sql = "INSERT INTO vinculo (idoso_id, familiar_id, status, respondido_em) VALUES (?, ?, 'ACEITO', now()) "
                + "ON CONFLICT (idoso_id, familiar_id) DO UPDATE SET status = 'ACEITO', respondido_em = now()";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, idosoId);
            ps.setInt(2, familiarId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("salvar vínculo", e);
        }
    }

    /** O relógio do banco decide a validade, para não depender da hora do aparelho de cada pessoa. */
    @Override
    public void solicitarVinculo(int idosoId, int familiarId) {
        String sql = "INSERT INTO vinculo (idoso_id, familiar_id, status, solicitado_em, respondido_em) "
                + "VALUES (?, ?, 'PENDENTE', now(), NULL) "
                + "ON CONFLICT (idoso_id, familiar_id) DO UPDATE "
                + "SET status = 'PENDENTE', solicitado_em = now(), respondido_em = NULL "
                + "WHERE vinculo.status <> 'ACEITO'";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, idosoId);
            ps.setInt(2, familiarId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("solicitar vínculo", e);
        }
    }

    @Override
    public StatusVinculo buscarStatusVinculo(int idosoId, int familiarId, Duration validade) {
        String sql = "SELECT status, solicitado_em >= now() - make_interval(secs => ?) AS valido "
                + "FROM vinculo WHERE idoso_id = ? AND familiar_id = ?";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setDouble(1, validade.toSeconds());
            ps.setInt(2, idosoId);
            ps.setInt(3, familiarId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                StatusVinculo status = StatusVinculo.valueOf(rs.getString("status"));
                return status == StatusVinculo.PENDENTE && !rs.getBoolean("valido") ? null : status;
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("buscar status do vínculo", e);
        }
    }

    @Override
    public List<PedidoVinculo> listarPedidosPendentes(int idosoId, Duration validade) {
        String sql = "SELECT u." + COLUNAS.replace(", ", ", u.") + ", v.solicitado_em "
                + "FROM vinculo v JOIN usuario u ON u.id = v.familiar_id "
                + "WHERE v.idoso_id = ? AND v.status = 'PENDENTE' AND u.excluido_em IS NULL "
                + "AND v.solicitado_em >= now() - make_interval(secs => ?) ORDER BY v.solicitado_em";
        List<PedidoVinculo> pedidos = new ArrayList<>();
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, idosoId);
            ps.setDouble(2, validade.toSeconds());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (converter(rs) instanceof Familiar familiar) {
                        LocalDateTime quando = rs.getObject("solicitado_em", OffsetDateTime.class)
                                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                        pedidos.add(new PedidoVinculo(idosoId, familiar, quando));
                    }
                }
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("listar pedidos de vínculo", e);
        }
        return pedidos;
    }

    @Override
    public boolean responderPedidoVinculo(int idosoId, int familiarId, boolean aceitar, Duration validade) {
        String sql = "UPDATE vinculo SET status = ?, respondido_em = now() "
                + "WHERE idoso_id = ? AND familiar_id = ? AND status = 'PENDENTE' "
                + "AND solicitado_em >= now() - make_interval(secs => ?)";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, aceitar ? "ACEITO" : "RECUSADO");
            ps.setInt(2, idosoId);
            ps.setInt(3, familiarId);
            ps.setDouble(4, validade.toSeconds());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new ErroBancoDadosException("responder pedido de vínculo", e);
        }
    }

    @Override
    public void removerVinculo(int idosoId, int familiarId) {
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(
                     "DELETE FROM vinculo WHERE idoso_id = ? AND familiar_id = ?")) {
            ps.setInt(1, idosoId);
            ps.setInt(2, familiarId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("remover vínculo", e);
        }
    }

    @Override
    public List<Usuario> listarTodos() {
        Map<Integer, Idoso> idosos = new HashMap<>();
        Map<Integer, Familiar> familiares = new HashMap<>();

        try (Connection conexao = dataSource.getConnection()) {
            try (PreparedStatement ps = conexao.prepareStatement(
                    "SELECT " + COLUNAS + " FROM usuario WHERE excluido_em IS NULL ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Usuario usuario = converter(rs);
                    if (usuario instanceof Idoso idoso) {
                        idosos.put(idoso.getId(), idoso);
                    } else {
                        familiares.put(usuario.getId(), (Familiar) usuario);
                    }
                }
            }

            try (PreparedStatement ps = conexao.prepareStatement(
                    "SELECT idoso_id, familiar_id FROM vinculo WHERE status = 'ACEITO'");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Idoso idoso = idosos.get(rs.getInt("idoso_id"));
                    Familiar familiar = familiares.get(rs.getInt("familiar_id"));
                    if (idoso != null && familiar != null) {
                        idoso.adicionarFamiliares(familiar);
                        familiar.adicionarIdosos(idoso);
                    }
                }
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("listar usuários", e);
        }

        List<Usuario> todos = new ArrayList<>(idosos.values());
        todos.addAll(familiares.values());
        return todos;
    }

    @Override
    public void atualizar(Usuario usuario) {
        String sql = "UPDATE usuario SET nome = ?, email = ?, senha_hash = ?, atualizado_em = now() "
                + "WHERE id = ? AND excluido_em IS NULL";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, usuario.getNome());
            ps.setString(2, usuario.getEmail());
            ps.setString(3, usuario.getSenha());
            ps.setInt(4, usuario.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("atualizar usuário", e);
        }
    }

    /** Exclusão lógica: a linha fica marcada como excluída (ver ADR-0045); os vínculos são removidos. */
    @Override
    public void excluir(int id) {
        try (Connection conexao = dataSource.getConnection()) {
            conexao.setAutoCommit(false);
            try (PreparedStatement marcar = conexao.prepareStatement(
                    "UPDATE usuario SET excluido_em = now(), atualizado_em = now() WHERE id = ? AND excluido_em IS NULL");
                 PreparedStatement vinculos = conexao.prepareStatement(
                         "DELETE FROM vinculo WHERE idoso_id = ? OR familiar_id = ?")) {
                marcar.setInt(1, id);
                marcar.executeUpdate();
                vinculos.setInt(1, id);
                vinculos.setInt(2, id);
                vinculos.executeUpdate();
                conexao.commit();
            } catch (SQLException e) {
                conexao.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("excluir usuário", e);
        }
    }

    @Override
    public List<Usuario> buscarPorNome(String nome) {
        List<Usuario> resultado = new ArrayList<>();
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(
                     "SELECT " + COLUNAS + " FROM usuario WHERE excluido_em IS NULL "
                             + "AND position(lower(?) in lower(nome)) > 0 ORDER BY id")) {
            ps.setString(1, nome);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(converter(rs));
                }
            }
            for (Usuario usuario : resultado) {
                popularVinculos(conexao, usuario);
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("buscar usuário por nome", e);
        }
        return resultado;
    }

    @Override
    public Usuario buscarPorId(int id) {
        Usuario usuario = buscarUm("WHERE id = ?", ps -> ps.setInt(1, id), "buscar usuário por id");
        if (usuario == null) {
            throw new NoSuchElementException("Usuário com id: " + id + " não encontrado.");
        }
        return usuario;
    }

    @Override
    public Usuario buscarPorEmail(String email) {
        return buscarUm("WHERE lower(email) = lower(?)", ps -> ps.setString(1, email), "buscar usuário por e-mail");
    }

    private Usuario buscarUm(String filtro, Parametros parametros, String operacao) {
        String sql = "SELECT " + COLUNAS + " FROM usuario " + filtro + " AND excluido_em IS NULL";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            parametros.preencher(ps);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Usuario usuario = converter(rs);
                popularVinculos(conexao, usuario);
                return usuario;
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException(operacao, e);
        }
    }

    /** Preenche os vínculos aceitos do usuário; quem está do outro lado é carregado sem os próprios vínculos. */
    private void popularVinculos(Connection conexao, Usuario usuario) throws SQLException {
        boolean souIdoso = usuario instanceof Idoso;
        String sql = souIdoso
                ? "SELECT u." + COLUNAS.replace(", ", ", u.") + " FROM vinculo v JOIN usuario u ON u.id = v.familiar_id "
                        + "WHERE v.idoso_id = ? AND v.status = 'ACEITO' AND u.excluido_em IS NULL"
                : "SELECT u." + COLUNAS.replace(", ", ", u.") + " FROM vinculo v JOIN usuario u ON u.id = v.idoso_id "
                        + "WHERE v.familiar_id = ? AND v.status = 'ACEITO' AND u.excluido_em IS NULL";

        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, usuario.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Usuario outro = converter(rs);
                    if (souIdoso && outro instanceof Familiar familiar) {
                        ((Idoso) usuario).adicionarFamiliares(familiar);
                    } else if (!souIdoso && outro instanceof Idoso idoso) {
                        ((Familiar) usuario).adicionarIdosos(idoso);
                    }
                }
            }
        }
    }

    private Usuario converter(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String nome = rs.getString("nome");
        String email = rs.getString("email");
        String senha = rs.getString("senha_hash");
        return "IDOSO".equals(rs.getString("tipo"))
                ? new Idoso(id, nome, email, senha)
                : new Familiar(id, nome, email, senha);
    }

    @FunctionalInterface
    private interface Parametros {
        void preencher(PreparedStatement ps) throws SQLException;
    }
}
