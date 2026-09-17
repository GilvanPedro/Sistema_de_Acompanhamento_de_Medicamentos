package br.com.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.port.in.RegistrarTomadaCase;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.SalvarHistoricoPort;

public class RegistrarTomadaService implements RegistrarTomadaCase {

    private final GerarIdPort gerarIdPort;
    private final SalvarHistoricoPort salvarHistoricoPort;

    public RegistrarTomadaService(GerarIdPort gerarIdPort, SalvarHistoricoPort salvarHistoricoPort) {
        this.gerarIdPort = gerarIdPort;
        this.salvarHistoricoPort = salvarHistoricoPort;
    }

    @Override
    public HistoricoMedicamento registrarTomada(Idoso idoso, Medicamento medicamento, boolean tomou) {
        if (tomou && jaTomouHoje(idoso, medicamento)) {
            throw new DadosInvalidosException("Você já registrou que tomou " + medicamento.getNome() + " hoje.");
        }

        int novoId = gerarIdPort.proximoId();
        HistoricoMedicamento historico = new HistoricoMedicamento(novoId, medicamento, idoso, LocalDateTime.now(), tomou);

        salvarHistoricoPort.salvar(historico);

        return historico;
    }

    private boolean jaTomouHoje(Idoso idoso, Medicamento medicamento) {
        Map<Integer, Idoso> idosos = new HashMap<>();
        idosos.put(idoso.getId(), idoso);

        Map<Integer, Medicamento> medicamentos = new HashMap<>();
        medicamentos.put(medicamento.getId(), medicamento);

        LocalDate hoje = LocalDate.now();

        return salvarHistoricoPort.listarHistoricoPorIdoso(idoso.getId(), idosos, medicamentos).stream()
                .anyMatch(h -> h.getMedicamento().getId() == medicamento.getId()
                        && h.isFoiTomado()
                        && h.getDataHoraTomada().toLocalDate().equals(hoje));
    }
}