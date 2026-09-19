package br.com.application.service;

import java.time.Clock;
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
import br.com.domain.util.OcorrenciasMedicamento;

public class VerificarNotificacoesIdosoService implements VerificarNotificacoesIdosoCase {

    private final SalvarMedicamentoPort salvarMedicamentoPort;
    private final SalvarHistoricoPort salvarHistoricoPort;
    private final Clock relogio;

    public VerificarNotificacoesIdosoService(SalvarMedicamentoPort salvarMedicamentoPort, SalvarHistoricoPort salvarHistoricoPort) {
        this(salvarMedicamentoPort, salvarHistoricoPort, Clock.systemDefaultZone());
    }

    /** O relógio vem de fora para dar para testar horários (inclusive a virada da meia-noite). */
    public VerificarNotificacoesIdosoService(SalvarMedicamentoPort salvarMedicamentoPort, SalvarHistoricoPort salvarHistoricoPort,
                                             Clock relogio) {
        this.salvarMedicamentoPort = salvarMedicamentoPort;
        this.salvarHistoricoPort = salvarHistoricoPort;
        this.relogio = relogio;
    }

    @Override
    public List<NotificacaoMedicamento> verificarNotificacoes(Idoso idoso) {
        List<NotificacaoMedicamento> notificacoes = new ArrayList<>();
        LocalDateTime agora = LocalDateTime.now(relogio);
        LocalDate hoje = agora.toLocalDate();

        Map<Integer, Idoso> idososMap = new HashMap<>();
        idososMap.put(idoso.getId(), idoso);

        Map<Integer, Medicamento> medicamentosDoIdoso = new HashMap<>();
        for (Medicamento m : salvarMedicamentoPort.listarPorIdoso(idoso.getId())) {
            medicamentosDoIdoso.put(m.getId(), m);
        }

        List<HistoricoMedicamento> historicoDoIdoso =
                salvarHistoricoPort.listarHistoricoPorIdoso(idoso.getId(), idososMap, medicamentosDoIdoso);

        for (Medicamento medicamento : medicamentosDoIdoso.values()) {
            for (LocalDateTime previsto : OcorrenciasMedicamento.relevantes(medicamento, agora)) {
                boolean tomado = historicoDoIdoso.stream().anyMatch(h ->
                        h.getMedicamento().getId() == medicamento.getId()
                                && h.isFoiTomado()
                                && OcorrenciasMedicamento.tomadaCobre(h.getDataHoraTomada(), previsto));

                if (tomado) {
                    // só mostra "já tomou" para o de hoje; o de ontem, se foi tomado, não precisa de aviso nenhum
                    if (previsto.toLocalDate().equals(hoje)) {
                        notificacoes.add(new NotificacaoMedicamento(medicamento, TipoNotificacao.TOMADO));
                    }
                    continue;
                }

                long minutosDeAtraso = OcorrenciasMedicamento.minutosDeAtraso(previsto, agora);
                if (minutosDeAtraso > OcorrenciasMedicamento.TOLERANCIA_MINUTOS) {
                    notificacoes.add(new NotificacaoMedicamento(medicamento, TipoNotificacao.ESQUECIDO));
                } else {
                    notificacoes.add(new NotificacaoMedicamento(medicamento, TipoNotificacao.LEMBRETE));
                }
            }
        }

        return notificacoes;
    }
}
