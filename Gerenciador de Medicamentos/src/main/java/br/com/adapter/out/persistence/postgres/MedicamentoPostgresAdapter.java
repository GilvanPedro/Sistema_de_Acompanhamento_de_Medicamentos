package br.com.adapter.out.persistence.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import javax.sql.DataSource;

import br.com.domain.exception.ErroBancoDadosException;
import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.port.out.SalvarMedicamentoPort;

public class MedicamentoPostgresAdapter implements SalvarMedicamentoPort {

    private static final String COLUNAS = "id, idoso_id, nome, horario, dia_semana, tipo, vigente_desde";

    private final DataSource dataSource;

    public MedicamentoPostgresAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void salvar(Medicamento medicamento) {
        String sql = "INSERT INTO medicamento (id, idoso_id, nome, horario, dia_semana, tipo) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, medicamento.getId());
            ps.setInt(2, medicamento.getIdosoId());
            ps.setString(3, medicamento.getNome());
            ps.setObject(4, medicamento.getHorarioMedicamento());
            ps.setString(5, medicamento.getDiaSemana().name());
            ps.setString(6, medicamento.getTipoMedicamento().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("salvar medicamento", e);
        }
    }

    @Override
    public List<Medicamento> listarTodos() {
        List<Medicamento> resultado = new ArrayList<>();
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(
                     "SELECT " + COLUNAS + " FROM medicamento WHERE excluido_em IS NULL ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(converter(rs));
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("listar medicamentos", e);
        }
        return resultado;
    }

    @Override
    public List<Medicamento> listarPorIdoso(int idosoId) {
        List<Medicamento> resultado = new ArrayList<>();
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(
                     "SELECT " + COLUNAS + " FROM medicamento WHERE idoso_id = ? AND excluido_em IS NULL ORDER BY id")) {
            ps.setInt(1, idosoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(converter(rs));
                }
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("listar medicamentos do idoso", e);
        }
        return resultado;
    }

    @Override
    public void atualizar(Medicamento medicamento) {
        String sql = "UPDATE medicamento SET nome = ?, horario = ?, dia_semana = ?, tipo = ?, atualizado_em = now(), "
                + "vigente_desde = CASE WHEN horario <> ? OR dia_semana <> ? THEN now() ELSE vigente_desde END "
                + "WHERE id = ? AND excluido_em IS NULL";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, medicamento.getNome());
            ps.setObject(2, medicamento.getHorarioMedicamento());
            ps.setString(3, medicamento.getDiaSemana().name());
            ps.setString(4, medicamento.getTipoMedicamento().name());
            ps.setObject(5, medicamento.getHorarioMedicamento());
            ps.setString(6, medicamento.getDiaSemana().name());
            ps.setInt(7, medicamento.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("atualizar medicamento", e);
        }
    }

    @Override
    public Medicamento buscarPorId(int id) {
        String sql = "SELECT " + COLUNAS + " FROM medicamento WHERE id = ? AND excluido_em IS NULL";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NoSuchElementException("Medicamento com id: " + id + " não encontrado.");
                }
                return converter(rs);
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("buscar medicamento por id", e);
        }
    }

    /** Exclusão lógica (ver ADR-0046): a linha fica só como marca de exclusão, e o nome do remédio é apagado. */
    @Override
    public void excluir(int id) {
        String sql = "UPDATE medicamento SET nome = '(excluído)', excluido_em = now(), atualizado_em = now() "
                + "WHERE id = ? AND excluido_em IS NULL";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("excluir medicamento", e);
        }
    }

    private Medicamento converter(ResultSet rs) throws SQLException {
        return new Medicamento(
                rs.getInt("id"),
                rs.getInt("idoso_id"),
                rs.getString("nome"),
                rs.getObject("horario", LocalTime.class),
                DayOfWeek.valueOf(rs.getString("dia_semana")),
                TipoMedicamento.valueOf(rs.getString("tipo")),
                rs.getObject("vigente_desde", java.time.OffsetDateTime.class)
                        .atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime());
    }
}
