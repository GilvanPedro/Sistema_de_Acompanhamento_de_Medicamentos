package br.com.application.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.RegistrarMedicamentoCase;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.port.out.SalvarUsuarioPort;
import br.com.domain.validation.ValidarDadosMedicamento;

public class RegistrarMedicamentoService implements RegistrarMedicamentoCase {

    private final GerarIdPort gerarIdPort;
    private final SalvarMedicamentoPort salvarMedicamentoPort;
    private final SalvarUsuarioPort salvarUsuarioPort;
    private final ValidarDadosMedicamento validarDadosMedicamento;

    public RegistrarMedicamentoService(GerarIdPort gerarIdPort, SalvarMedicamentoPort salvarMedicamentoPort, SalvarUsuarioPort salvarUsuarioPort) {
        this.gerarIdPort = gerarIdPort;
        this.salvarMedicamentoPort = salvarMedicamentoPort;
        this.salvarUsuarioPort = salvarUsuarioPort;
        this.validarDadosMedicamento = new ValidarDadosMedicamento();
    }

    @Override
    public Medicamento registrarMedicamento(String nome, DayOfWeek diaSemana, LocalTime horario, TipoMedicamento tipo, int idosoId) {
        validarDadosMedicamento.validarMedicamento(nome, diaSemana, horario, tipo);

        Usuario encontrado = salvarUsuarioPort.buscarPorId(idosoId);

        if (!(encontrado instanceof Idoso)) {
            throw new IllegalArgumentException("O id " + idosoId + " pertence a um familiar. Apenas idosos podem ter medicamentos cadastrados.");
        }

        int novoId = gerarIdPort.proximoId();
        Medicamento medicamento = new Medicamento(novoId, idosoId, nome, horario, diaSemana, tipo);
        salvarMedicamentoPort.salvar(medicamento);
        return medicamento;
    }
}