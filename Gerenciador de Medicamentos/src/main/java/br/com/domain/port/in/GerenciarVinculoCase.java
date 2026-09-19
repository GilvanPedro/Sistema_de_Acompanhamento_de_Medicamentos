package br.com.domain.port.in;

import java.util.List;

import br.com.domain.model.PedidoVinculo;

public interface GerenciarVinculoCase {
    /** O familiar pede para acompanhar o idoso; o pedido fica pendente até o idoso responder (ou expirar). */
    void solicitarVinculo(int familiarId, int idosoId);

    List<PedidoVinculo> listarPedidosPendentes(int idosoId);

    void aceitarPedido(int idosoId, int familiarId);

    void recusarPedido(int idosoId, int familiarId);

    /** O idoso remove um familiar já vinculado. */
    void removerFamiliar(int idosoId, int familiarId);
}
