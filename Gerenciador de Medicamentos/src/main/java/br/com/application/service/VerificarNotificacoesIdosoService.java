package br.com.application.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.NotificacaoMedicamento;
import br.com.domain.model.TipoNotificacao;
import br.com.domain.port.in.VerificarNotificacoesIdosoCase;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.port.out.SalvarMedicamentoPort;

public class VerificarNotificacoesIdosoService implements VerificarNotificacoesIdosoCase {

    private static final int TOLERANCIA_MINUTOS = 10;

    private final SalvarMedicamentoPort salvarMedicamentoPort;
    private final SalvarHistoricoPort salvarHistoricoPort;

    public VerificarNotificacoesIdosoService(SalvarMedicamentoPort salvarMedicamentoPort, SalvarHistoricoPort salvarHistoricoPort) {
        this.salvarMedicamentoPort = salvarMedicamentoPort;
        this.salvarHistoricoPort = salvarHistoricoPort;
    }

    @Override
    public List<NotificacaoMedicamento> verificarNotificacoes(Idoso idoso) {
        List<NotificacaoMedicamento> notificacoes = new ArrayList<>();

        LocalDate hoje = LocalDate.now();
        LocalDateTime agora = LocalDateTime.now();

        Map<Integer, Idoso> idososMap = new HashMap<>();
        idososMap.put(idoso.getId(), idoso);

        Map<Integer, Medicamento> medicamentosDoIdoso = new HashMap<>();
        for (Medicamento m : salvarMedicamentoPort.listarTodos()) {
            if (m.getIdosoId() == idoso.getId()) {
                medicamentosDoIdoso.put(m.getId(), m);
            }
        }

        List<HistoricoMedicamento> historicoDoIdoso =
                salvarHistoricoPort.listarHistoricoPorIdoso(idoso.getId(), idososMap, medicamentosDoIdoso);

        for (Medicamento medicamento : medicamentosDoIdoso.values()) {
            if (medicamento.getDiaSemana() != hoje.getDayOfWeek()) {
                continue;
            }

            boolean tomadoHoje = historicoDoIdoso.stream().anyMatch(h ->
                    h.getMedicamento().getId() == medicamento.getId() &&
                            h.isFoiTomado() &&
                            h.getDataHoraTomada().toLocalDate().equals(hoje)
            );

            if (tomadoHoje) {
                notificacoes.add(new NotificacaoMedicamento(medicamento, TipoNotificacao.TOMADO));
                continue;
            }

            LocalDateTime horarioPrevisto = LocalDateTime.of(hoje, medicamento.getHorarioMedicamento());
            long minutosDeAtraso = Duration.between(horarioPrevisto, agora).toMinutes();

            if (minutosDeAtraso > TOLERANCIA_MINUTOS) {
                notificacoes.add(new NotificacaoMedicamento(medicamento, TipoNotificacao.ESQUECIDO));
            } else if (minutosDeAtraso >= 0) {
                notificacoes.add(new NotificacaoMedicamento(medicamento, TipoNotificacao.LEMBRETE));
            }
        }

        return notificacoes;
    }
}