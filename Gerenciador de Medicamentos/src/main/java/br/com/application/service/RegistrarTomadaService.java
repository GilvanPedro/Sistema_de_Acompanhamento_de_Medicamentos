package br.com.application.service;

import java.time.Clock;
import java.time.Duration;
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

    /** Tolerância para o relógio do celular estar um pouco adiantado em relação ao do servidor. */
    private static final Duration TOLERANCIA_DE_RELOGIO = Duration.ofMinutes(5);

    /** Até quando dá para registrar uma tomada feita sem internet (o celular pode ficar dias desconectado). */
    private static final Duration IDADE_MAXIMA = Duration.ofDays(7);

    private final GerarIdPort gerarIdPort;
    private final SalvarHistoricoPort salvarHistoricoPort;
    private final Clock relogio;

    public RegistrarTomadaService(GerarIdPort gerarIdPort, SalvarHistoricoPort salvarHistoricoPort) {
        this(gerarIdPort, salvarHistoricoPort, Clock.systemDefaultZone());
    }

    /** O relógio vem de fora para dar para testar as regras de horário. */
    public RegistrarTomadaService(GerarIdPort gerarIdPort, SalvarHistoricoPort salvarHistoricoPort, Clock relogio) {
        this.gerarIdPort = gerarIdPort;
        this.salvarHistoricoPort = salvarHistoricoPort;
        this.relogio = relogio;
    }

    @Override
    public HistoricoMedicamento registrarTomada(Idoso idoso, Medicamento medicamento, boolean tomou) {
        return registrarTomada(idoso, medicamento, tomou, null);
    }

    /**
     * @param quando a hora em que a tomada aconteceu de verdade (o app manda a hora do momento em que o idoso tocou
     *               em "já tomei", mesmo sem internet); null = agora. Não pode estar no futuro nem ser muito antiga.
     */
    @Override
    public HistoricoMedicamento registrarTomada(Idoso idoso, Medicamento medicamento, boolean tomou, LocalDateTime quando) {
        LocalDateTime agora = LocalDateTime.now(relogio);
        if (quando != null) {
            if (quando.isAfter(agora.plus(TOLERANCIA_DE_RELOGIO))) {
                throw new DadosInvalidosException("A hora da tomada não pode estar no futuro.");
            }
            if (quando.isBefore(agora.minus(IDADE_MAXIMA))) {
                throw new DadosInvalidosException("Essa tomada é antiga demais para ser registrada (o máximo é de 7 dias).");
            }
        }
        LocalDateTime momento = quando != null ? quando : agora;

        if (tomou && jaTomouNoDia(idoso, medicamento, momento.toLocalDate())) {
            throw new DadosInvalidosException("Você já registrou que tomou " + medicamento.getNome() + " nesse dia.");
        }

        int novoId = gerarIdPort.proximoId();
        HistoricoMedicamento historico = new HistoricoMedicamento(novoId, medicamento, idoso, momento, tomou);

        salvarHistoricoPort.salvar(historico);

        return historico;
    }

    private boolean jaTomouNoDia(Idoso idoso, Medicamento medicamento, LocalDate dia) {
        Map<Integer, Idoso> idosos = new HashMap<>();
        idosos.put(idoso.getId(), idoso);

        Map<Integer, Medicamento> medicamentos = new HashMap<>();
        medicamentos.put(medicamento.getId(), medicamento);

        return salvarHistoricoPort.listarHistoricoPorIdoso(idoso.getId(), idosos, medicamentos).stream()
                .anyMatch(h -> h.getMedicamento().getId() == medicamento.getId()
                        && h.isFoiTomado()
                        && h.getDataHoraTomada().toLocalDate().equals(dia));
    }
}
