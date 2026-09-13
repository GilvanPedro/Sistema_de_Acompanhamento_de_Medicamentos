package br.com.application.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.RegistrarMedicamentoCase;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.SalvarMedicamentoPort;

public class RegistrarMedicamentoService implements RegistrarMedicamentoCase {

    private final GerarIdPort gerarIdPort;
    private final SalvarMedicamentoPort salvarMedicamentoPort;

    public RegistrarMedicamentoService(GerarIdPort gerarIdPort, SalvarMedicamentoPort salvarMedicamentoPort) {
        this.gerarIdPort = gerarIdPort;
        this.salvarMedicamentoPort = salvarMedicamentoPort;
    }

    @Override
    public Medicamento registrarMedicamento(String nome, DayOfWeek diaSemana, LocalTime horario, TipoMedicamento tipo, int idosoId) {
        int novoId = gerarIdPort.proximoId();
        Medicamento medicamento = new Medicamento(novoId, idosoId, nome, horario, diaSemana, tipo);
        salvarMedicamentoPort.salvar(medicamento);
        return medicamento;
    }
}