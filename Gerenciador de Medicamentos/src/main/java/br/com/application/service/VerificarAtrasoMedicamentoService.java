package br.com.application.service;

import java.time.Duration;
import java.time.LocalDate;
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

public class VerificarAtrasoMedicamentoService implements VerificarAtrasoMedicamentoCase {

    private static final int TOLERANCIA_MINUTOS = 10;

    private final SalvarUsuarioPort salvarUsuarioPort;
    private final SalvarMedicamentoPort salvarMedicamentoPort;
    private final SalvarHistoricoPort salvarHistoricoPort;
    private final NotificarPort notificarPort;

    public VerificarAtrasoMedicamentoService(SalvarUsuarioPort salvarUsuarioPort, SalvarMedicamentoPort salvarMedicamentoPort,
                                             SalvarHistoricoPort salvarHistoricoPort, NotificarPort notificarPort) {
        this.salvarUsuarioPort = salvarUsuarioPort;
        this.salvarMedicamentoPort = salvarMedicamentoPort;
        this.salvarHistoricoPort = salvarHistoricoPort;
        this.notificarPort = notificarPort;
    }

    @Override
    public void verificarAtrasos() {
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

        LocalDate hoje = LocalDate.now();
        LocalDateTime agora = LocalDateTime.now();

        for (Medicamento medicamento : medicamentos) {
            if (medicamento.getDiaSemana() != hoje.getDayOfWeek()) {
                continue;
            }

            LocalDateTime horarioPrevisto = LocalDateTime.of(hoje, medicamento.getHorarioMedicamento());
            long minutosDeAtraso = Duration.between(horarioPrevisto, agora).toMinutes();

            if (minutosDeAtraso <= TOLERANCIA_MINUTOS) {
                continue;
            }

            Idoso idoso = idosos.get(medicamento.getIdosoId());
            if (idoso == null) {
                continue;
            }

            boolean jaTomouHoje = historico.stream().anyMatch(h ->
                    h.getMedicamento().getId() == medicamento.getId() &&
                            h.getIdoso().getId() == idoso.getId() &&
                            h.isFoiTomado() &&
                            h.getDataHoraTomada().toLocalDate().equals(hoje)
            );

            if (jaTomouHoje) {
                continue;
            }

            notificarPort.lembrarIdoso(idoso, medicamento);
            notificarPort.avisarRemedioEsquecido(idoso, medicamento);
        }
    }
}