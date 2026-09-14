package br.com.application.service;

import br.com.domain.exception.UsuarioNaoEncontradoException;
import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.port.in.EditarMedicamentoCase;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.validation.ValidarDadosMedicamento;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
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
        validarDadosMedicamento.validarMedicamento(nome, diaMedicamento, horarioMedicamento, tipoMedicamento);

        Medicamento medicamento = buscarMedicamento(id);

        if (medicamento == null) {
            throw new IllegalArgumentException("Medicamento com id " + id + " não encontrado.");
        }

        medicamento.setNome(nome);
        medicamento.setHorarioMedicamento(horarioMedicamento);
        medicamento.setDiaSemana(diaMedicamento);
        medicamento.setTipoMedicamento(tipoMedicamento);

        salvarMedicamentoPort.atualizar(medicamento);

        return medicamento;
    }

    private Medicamento buscarMedicamento(int id){
        try{
            return salvarMedicamentoPort.buscarPorId(id);
        } catch(NoSuchElementException e){
            throw new UsuarioNaoEncontradoException(id);
        }
    }
}