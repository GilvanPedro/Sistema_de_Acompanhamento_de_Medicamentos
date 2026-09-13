package br.com.domain.port.in;

import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;

import java.time.DayOfWeek;
import java.time.LocalTime;

public interface RegistrarMedicamentoCase {
    Medicamento registrarMedicamento(String nome, DayOfWeek diaSemana, LocalTime horarioMedicamento, TipoMedicamento tipoMedicamento);
}
