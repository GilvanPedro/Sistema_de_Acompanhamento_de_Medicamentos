package br.com.adapter.out.id;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import br.com.domain.exception.ErroBancoDadosException;
import br.com.domain.port.out.GerarIdPort;

/** Pega o próximo id da sequência da coluna {@code id} da tabela; funciona com vários clientes ao mesmo tempo. */
public class GerarIdPostgresAdapter implements GerarIdPort {

    private final DataSource dataSource;
    private final String tabela;

    public GerarIdPostgresAdapter(DataSource dataSource, String tabela) {
        this.dataSource = dataSource;
        this.tabela = tabela;
    }

    @Override
    public int proximoId() {
        String sql = "SELECT nextval(pg_get_serial_sequence(?, 'id'))";
        try (Connection conexao = dataSource.getConnection();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, tabela);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new ErroBancoDadosException("gerar id de " + tabela, e);
        }
    }
}
