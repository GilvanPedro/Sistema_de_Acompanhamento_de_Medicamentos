package br.com.adapter.in.gui.api;

import java.time.LocalDateTime;

/** Pedido de um familiar para acompanhar o idoso. */
public record Pedido(Conta familiar, LocalDateTime solicitadoEm) {
}
