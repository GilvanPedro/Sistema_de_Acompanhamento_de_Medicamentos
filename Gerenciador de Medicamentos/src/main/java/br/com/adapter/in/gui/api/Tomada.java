package br.com.adapter.in.gui.api;

import java.time.LocalDateTime;

public record Tomada(int id, int remedioId, String remedioNome, LocalDateTime dataHora, boolean foiTomado) {
}
