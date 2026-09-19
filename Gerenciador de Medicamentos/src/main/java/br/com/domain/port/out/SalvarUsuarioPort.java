package br.com.domain.port.out;

import java.time.Duration;
import java.util.List;
import br.com.domain.model.PedidoVinculo;
import br.com.domain.model.StatusVinculo;
import br.com.domain.model.Usuario;

public interface SalvarUsuarioPort {
    void salvar(Usuario usuario);
    List<Usuario> listarTodos();
    void salvarVinculo(int idosoId, int familiarId);
    void atualizar(Usuario usuario);
    void excluir(int id);
    List<Usuario> buscarPorNome(String nome);
    Usuario buscarPorId(int id);
    Usuario buscarPorEmail(String email);

    // Aceite do vínculo pelo idoso (ADR-0045). Só o PostgreSQL implementa; o CSV não guarda o status.

    /** Cria (ou renova) um pedido PENDENTE do familiar para o idoso. */
    default void solicitarVinculo(int idosoId, int familiarId) {
        throw new UnsupportedOperationException("O aceite de vínculo exige o banco PostgreSQL.");
    }

    /** Status do vínculo, ou null se não existe (um pedido pendente mais antigo que a validade conta como inexistente). */
    default StatusVinculo buscarStatusVinculo(int idosoId, int familiarId, Duration validade) {
        return null;
    }

    default List<PedidoVinculo> listarPedidosPendentes(int idosoId, Duration validade) {
        return List.of();
    }

    /** Aceita ou recusa um pedido pendente e ainda válido; devolve false se não havia pedido válido. */
    default boolean responderPedidoVinculo(int idosoId, int familiarId, boolean aceitar, Duration validade) {
        throw new UnsupportedOperationException("O aceite de vínculo exige o banco PostgreSQL.");
    }

    default void removerVinculo(int idosoId, int familiarId) {
        throw new UnsupportedOperationException("O aceite de vínculo exige o banco PostgreSQL.");
    }
}
