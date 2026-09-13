package br.com.application.service;

import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.port.in.RegistrarMedicamentoCase;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.validation.ValidarDadosMedicamento;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class RegistrarMedicamentoService implements RegistrarMedicamentoCase {
    private final GerarIdPort gerarIdPort;
    private ValidarDadosMedicamento validarDados = new ValidarDadosMedicamento();

    public RegistrarMedicamentoService(GerarIdPort gerarIdPort) {
        this.gerarIdPort = gerarIdPort;
    }

    @Override
    public Medicamento registrarMedicamento(String nome, DayOfWeek diaSemana, LocalTime horarioMedicamento, TipoMedicamento tipoMedicamento) {
        validarDados.validarMedicamento(nome, diaSemana, horarioMedicamento, tipoMedicamento);
        int novoId = gerarIdPort.proximoId();
        return new Medicamento(novoId, nome, horarioMedicamento, diaSemana, tipoMedicamento);
    }
}
