package br.com.domain.model;

import java.time.LocalDateTime;

/** Pedido de um familiar para acompanhar um idoso, aguardando a resposta do idoso. */
public class PedidoVinculo {
    private final int idosoId;
    private final Familiar familiar;
    private final LocalDateTime solicitadoEm;

    public PedidoVinculo(int idosoId, Familiar familiar, LocalDateTime solicitadoEm) {
        this.idosoId = idosoId;
        this.familiar = familiar;
        this.solicitadoEm = solicitadoEm;
    }

    public int getIdosoId() {
        return idosoId;
    }

    public Familiar getFamiliar() {
        return familiar;
    }

    public LocalDateTime getSolicitadoEm() {
        return solicitadoEm;
    }
}
