package br.com.application.service;

import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.port.in.RegistrarTomadaCase;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.NotificarPort;
import br.com.domain.port.out.SalvarHistoricoPort;

import java.time.LocalDateTime;

public class RegistrarTomadaService implements RegistrarTomadaCase {
    private final GerarIdPort gerarIdPort;
    private final SalvarHistoricoPort salvarHistoricoPort;
    private final NotificarPort notificarPort;

    public RegistrarTomadaService(GerarIdPort gerarIdPort, SalvarHistoricoPort salvarHistoricoPort, NotificarPort notificarPort) {
        this.gerarIdPort = gerarIdPort;
        this.salvarHistoricoPort = salvarHistoricoPort;
        this.notificarPort = notificarPort;
    }

    @Override
    public HistoricoMedicamento registrarTomada(Idoso idoso, Medicamento medicamento, boolean tomou) {
        int novoId = gerarIdPort.proximoId();
        HistoricoMedicamento historico = new HistoricoMedicamento(novoId, medicamento, idoso, LocalDateTime.now(), tomou);
        return null;
    }
}
