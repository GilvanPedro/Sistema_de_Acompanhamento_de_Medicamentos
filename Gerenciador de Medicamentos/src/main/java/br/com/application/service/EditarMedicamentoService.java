package br.com.application.service;

import br.com.domain.exception.MedicamentoNaoEncontradoException;
import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.port.in.EditarMedicamentoCase;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.validation.ValidarDadosMedicamento;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.NoSuchElementException;

public class EditarMedicamentoService implements EditarMedicamentoCase {
    private final SalvarMedicamentoPort salvarMedicamentoPort;
    private final ValidarDadosMedicamento validarDadosMedicamento;

    public EditarMedicamentoService(SalvarMedicamentoPort salvarMedicamentoPort) {
        this.salvarMedicamentoPort = salvarMedicamentoPort;
        this.validarDadosMedicamento = new ValidarDadosMedicamento();
    }

    @Override
    public Medicamento editarMedicamento(int id, String nome, LocalTime horarioMedicamento, DayOfWeek diaMedicamento, TipoMedicamento tipoMedicamento) {
        Medicamento medicamento = buscarMedicamento(id);

        if (nome != null) {
            validarDadosMedicamento.validarNome(nome);
            medicamento.setNome(nome);
        }

        if (horarioMedicamento != null) {
            validarDadosMedicamento.validarHorario(horarioMedicamento);
            medicamento.setHorarioMedicamento(horarioMedicamento);
        }

        if (diaMedicamento != null) {
            validarDadosMedicamento.validarDiaSemana(diaMedicamento);
            medicamento.setDiaSemana(diaMedicamento);
        }

        if (tipoMedicamento != null) {
            validarDadosMedicamento.validarTipo(tipoMedicamento);
            medicamento.setTipoMedicamento(tipoMedicamento);
        }

        salvarMedicamentoPort.atualizar(medicamento);

        return medicamento;
    }

    private Medicamento buscarMedicamento(int id) {
        try {
            return salvarMedicamentoPort.buscarPorId(id);
        } catch (NoSuchElementException e) {
            throw new MedicamentoNaoEncontradoException(id);
        }
    }
}