package br.com.domain.port.in;

import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;

import java.time.DayOfWeek;
import java.time.LocalTime;

public interface EditarMedicamentoCase {
    Medicamento editarMedicamento(int id, String nome, LocalTime horarioMedicamento, DayOfWeek diaMedicamento, TipoMedicamento tipoMedicamento);
}
