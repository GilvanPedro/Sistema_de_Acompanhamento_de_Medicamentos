package br.com.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.VerificarAtrasoMedicamentoCase;
import br.com.domain.port.out.NotificarPort;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.port.out.SalvarUsuarioPort;
import br.com.domain.util.OcorrenciasMedicamento;

public class VerificarAtrasoMedicamentoService implements VerificarAtrasoMedicamentoCase {

    private final SalvarUsuarioPort salvarUsuarioPort;
    private final SalvarMedicamentoPort salvarMedicamentoPort;
    private final SalvarHistoricoPort salvarHistoricoPort;
    private final NotificarPort notificarPort;
    private final Clock relogio;

    /**
     * Avisos já enviados, para o mesmo horário previsto não gerar um aviso por minuto até o fim do dia.
     * Guarda o horário previsto de cada um, para limpar os antigos. Fica em memória: ao reiniciar, pode repetir um aviso.
     */
    private final Map<String, LocalDateTime> jaAvisados = new HashMap<>();

    public VerificarAtrasoMedicamentoService(SalvarUsuarioPort salvarUsuarioPort, SalvarMedicamentoPort salvarMedicamentoPort,
                                             SalvarHistoricoPort salvarHistoricoPort, NotificarPort notificarPort) {
        this(salvarUsuarioPort, salvarMedicamentoPort, salvarHistoricoPort, notificarPort, Clock.systemDefaultZone());
    }

    /** O relógio vem de fora para dar para testar horários (inclusive a virada da meia-noite). */
    public VerificarAtrasoMedicamentoService(SalvarUsuarioPort salvarUsuarioPort, SalvarMedicamentoPort salvarMedicamentoPort,
                                             SalvarHistoricoPort salvarHistoricoPort, NotificarPort notificarPort, Clock relogio) {
        this.salvarUsuarioPort = salvarUsuarioPort;
        this.salvarMedicamentoPort = salvarMedicamentoPort;
        this.salvarHistoricoPort = salvarHistoricoPort;
        this.notificarPort = notificarPort;
        this.relogio = relogio;
    }

    /**
     * Para cada remédio com horário previsto que ainda importa: avisa o idoso uma vez enquanto está na tolerância
     * (lembrete) e avisa os familiares uma vez quando passou dela sem ter sido tomado (esquecido).
     */
    @Override
    public synchronized void verificarAtrasos() {
        Map<Integer, Idoso> idosos = new HashMap<>();
        for (Usuario u : salvarUsuarioPort.listarTodos()) {
            if (u instanceof Idoso idoso) {
                idosos.put(idoso.getId(), idoso);
            }
        }

        List<Medicamento> medicamentos = salvarMedicamentoPort.listarTodos();
        Map<Integer, Medicamento> medicamentosPorId = new HashMap<>();
        for (Medicamento m : medicamentos) {
            medicamentosPorId.put(m.getId(), m);
        }

        List<HistoricoMedicamento> historico = salvarHistoricoPort.listarTodos(idosos, medicamentosPorId);
        LocalDateTime agora = LocalDateTime.now(relogio);
        jaAvisados.values().removeIf(previsto -> previsto.isBefore(agora.minusDays(2)));

        for (Medicamento medicamento : medicamentos) {
            Idoso idoso = idosos.get(medicamento.getIdosoId());
            if (idoso == null) {
                continue;
            }

            for (LocalDateTime previsto : OcorrenciasMedicamento.relevantes(medicamento, agora)) {
                boolean tomado = historico.stream().anyMatch(h ->
                        h.getMedicamento().getId() == medicamento.getId()
                                && h.getIdoso().getId() == idoso.getId()
                                && h.isFoiTomado()
                                && OcorrenciasMedicamento.tomadaCobre(h.getDataHoraTomada(), previsto));
                if (tomado) {
                    continue;
                }

                boolean esquecido = OcorrenciasMedicamento.minutosDeAtraso(previsto, agora) > OcorrenciasMedicamento.TOLERANCIA_MINUTOS;
                String chave = (esquecido ? "esquecido|" : "lembrete|") + medicamento.getId() + "|" + previsto;
                if (jaAvisados.putIfAbsent(chave, previsto) != null) {
                    continue;
                }

                if (esquecido) {
                    notificarPort.avisarRemedioEsquecido(idoso, medicamento);
                } else {
                    notificarPort.lembrarIdoso(idoso, medicamento);
                }
            }
        }
    }
}
