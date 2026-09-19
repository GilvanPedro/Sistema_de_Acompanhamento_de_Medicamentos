package br.com.adapter.in.gui.api;

import java.time.DayOfWeek;
import java.time.LocalTime;

import br.com.domain.model.TipoMedicamento;

public record Remedio(int id, int idosoId, String nome, LocalTime horario, DayOfWeek dia, TipoMedicamento tipo) {
}
