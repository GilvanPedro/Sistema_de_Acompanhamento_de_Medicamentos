package br.com.application.service;

import br.com.domain.model.Medicamento;
import br.com.domain.port.in.EditarMedicamentoCase;
import br.com.domain.port.out.SalvarMedicamentoPort;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class EditarMedicamentoService implements EditarMedicamentoCase {
    private SalvarMedicamentoPort salvarMedicamentoPort;

    @Override
    public Medicamento editarMedicamento(String nome, LocalTime horarioMedicamento, DayOfWeek diaMedicamento, String tipoMedicamento) {
        return null;
    }
}
