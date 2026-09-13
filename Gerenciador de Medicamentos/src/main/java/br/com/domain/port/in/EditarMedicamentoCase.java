package br.com.domain.port.in;

import br.com.domain.model.Medicamento;

import java.time.DayOfWeek;
import java.time.LocalTime;

public interface EditarMedicamentoCase {
    Medicamento editarMedicamento(String nome, LocalTime horarioMedicamento, DayOfWeek diaMedicamento, String tipoMedicamento);
}
