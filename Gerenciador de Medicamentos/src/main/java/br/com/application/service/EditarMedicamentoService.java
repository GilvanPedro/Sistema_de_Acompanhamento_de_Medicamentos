package br.com.application.service;

import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.port.in.EditarMedicamentoCase;
import br.com.domain.port.out.SalvarMedicamentoPort;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public class EditarMedicamentoService implements EditarMedicamentoCase {
    private final SalvarMedicamentoPort salvarMedicamentoPort;

    public EditarMedicamentoService(SalvarMedicamentoPort salvarMedicamentoPort) {
        this.salvarMedicamentoPort = salvarMedicamentoPort;
    }

    @Override
    public Medicamento editarMedicamento(int id, String nome, LocalTime horarioMedicamento, DayOfWeek diaMedicamento, TipoMedicamento tipoMedicamento) {
        List<Medicamento> encontrados = salvarMedicamentoPort.buscarPorId(id);

        if (encontrados.isEmpty()) {
            throw new IllegalArgumentException("Medicamento com id " + id + " não encontrado.");
        }

        Medicamento medicamento = encontrados.get(0);

        medicamento.setNome(nome);
        medicamento.setHorarioMedicamento(horarioMedicamento);
        medicamento.setDiaSemana(diaMedicamento);
        medicamento.setTipoMedicamento(tipoMedicamento);

        salvarMedicamentoPort.atualizar(medicamento);

        return medicamento;
    }
}