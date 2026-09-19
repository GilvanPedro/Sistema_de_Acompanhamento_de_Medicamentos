package br.com.adapter.in.gui.api;

import br.com.domain.model.TipoNotificacao;

/** Situação de hoje de um remédio: lembrete, esquecido ou já tomado. */
public record Aviso(TipoNotificacao tipo, Remedio remedio) {
}
