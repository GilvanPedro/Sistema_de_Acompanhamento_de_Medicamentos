package br.com.adapter.out.persistence.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import br.com.domain.exception.ErroBancoDadosException;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.port.out.SalvarHistoricoPort;

public class HistoricoPostgresAdapter implements SalvarHistoricoPort {

    private static final String COLUNAS = "id, idoso_id, medicamento_id, data_hora_tomada, foi_tomado";

    private final DataSource dataSource;

    public HistoricoPostgresAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void salvar(HistoricoMedicamento historico) {
        String sql = "INSERT INTO historico (id, idoso_id, medicamento_id, data_hora_tomada, foi_tomado) VALUES (?, ?, ?, ?, ?)";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, historico.getId());
            ps.setInt(2, historico.getIdoso().getId());
            ps.setInt(3, historico.getMedicamento().getId());
            ps.setObject(4, historico.getDataHoraTomada());
            ps.setBoolean(5, historico.isFoiTomado());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("salvar histórico", e);
        }
    }

    @Override
    public List<HistoricoMedicamento> listarTodos(Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
        return consultar("SELECT " + COLUNAS + " FROM historico ORDER BY id", null, idosos, medicamentos);
    }

    @Override
    public List<HistoricoMedicamento> listarHistoricoPorIdoso(int idIdoso, Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
        return consultar("SELECT " + COLUNAS + " FROM historico WHERE idoso_id = ? ORDER BY id", idIdoso, idosos, medicamentos);
    }

    @Override
    public void atualizar(HistoricoMedicamento historico, Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
        String sql = "UPDATE historico SET data_hora_tomada = ?, foi_tomado = ? WHERE id = ?";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, historico.getDataHoraTomada());
            ps.setBoolean(2, historico.isFoiTomado());
            ps.setInt(3, historico.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("atualizar histórico", e);
        }
    }

    @Override
    public void excluir(int id) {
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement("DELETE FROM historico WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBancoDadosException("excluir histórico", e);
        }
    }

    /** Registros de idoso ou medicamento que não estão nos mapas (por exemplo, já excluídos) são ignorados, como no CSV. */
    private List<HistoricoMedicamento> consultar(String sql, Integer idIdoso,
                                                 Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
        List<HistoricoMedicamento> resultado = new ArrayList<>();
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            if (idIdoso != null) {
                ps.setInt(1, idIdoso);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Idoso idoso = idosos.get(rs.getInt("idoso_id"));
                    Medicamento medicamento = medicamentos.get(rs.getInt("medicamento_id"));
                    if (idoso == null || medicamento == null) {
                        continue;
                    }
                    resultado.add(new HistoricoMedicamento(
                            rs.getInt("id"),
                            medicamento,
                            idoso,
                            rs.getObject("data_hora_tomada", LocalDateTime.class),
                            rs.getBoolean("foi_tomado")));
                }
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("listar histórico", e);
        }
        return resultado;
    }
}
