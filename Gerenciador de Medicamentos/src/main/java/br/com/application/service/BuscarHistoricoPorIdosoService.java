package br.com.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.BuscarHistoricoPorIdosoCase;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.port.out.SalvarUsuarioPort;
import br.com.domain.util.FaltasDeMedicamento;

public class BuscarHistoricoPorIdosoService implements BuscarHistoricoPorIdosoCase {

    private final SalvarUsuarioPort salvarUsuarioPort;
    private final SalvarMedicamentoPort salvarMedicamentoPort;
    private final SalvarHistoricoPort salvarHistoricoPort;
    private final Clock relogio;

    public BuscarHistoricoPorIdosoService(SalvarUsuarioPort salvarUsuarioPort, SalvarMedicamentoPort salvarMedicamentoPort, SalvarHistoricoPort salvarHistoricoPort) {
        this(salvarUsuarioPort, salvarMedicamentoPort, salvarHistoricoPort, Clock.systemDefaultZone());
    }

    /** O relógio vem de fora para dar para testar a virada do dia. */
    public BuscarHistoricoPorIdosoService(SalvarUsuarioPort salvarUsuarioPort, SalvarMedicamentoPort salvarMedicamentoPort,
                                          SalvarHistoricoPort salvarHistoricoPort, Clock relogio) {
        this.salvarUsuarioPort = salvarUsuarioPort;
        this.salvarMedicamentoPort = salvarMedicamentoPort;
        this.salvarHistoricoPort = salvarHistoricoPort;
        this.relogio = relogio;
    }

    @Override
    public List<HistoricoMedicamento> buscarHistoricoDoIdoso(int idosoId) {
        Usuario usuario = salvarUsuarioPort.buscarPorId(idosoId);

        if (!(usuario instanceof Idoso idoso)) {
            throw new IllegalArgumentException("O usuário com id " + idosoId + " não é um idoso.");
        }

        Map<Integer, Idoso> idosos = new HashMap<>();
        idosos.put(idoso.getId(), idoso);

        Map<Integer, Medicamento> medicamentos = new HashMap<>();
        for (Medicamento m : salvarMedicamentoPort.listarPorIdoso(idosoId)) {
            medicamentos.put(m.getId(), m);
        }

        return salvarHistoricoPort.listarHistoricoPorIdoso(idosoId, idosos, medicamentos);
    }

    @Override
    public List<HistoricoMedicamento> buscarHistoricoCompleto(int idosoId, LocalDate de, LocalDate ate) {
        Usuario usuario = salvarUsuarioPort.buscarPorId(idosoId);
        if (!(usuario instanceof Idoso idoso)) {
            throw new IllegalArgumentException("O usuário com id " + idosoId + " não é um idoso.");
        }
        List<Medicamento> medicamentos = salvarMedicamentoPort.listarPorIdoso(idosoId);
        List<HistoricoMedicamento> registros = buscarHistoricoDoIdoso(idosoId);

        List<HistoricoMedicamento> todos = new ArrayList<>(registros);
        todos.addAll(FaltasDeMedicamento.calcular(idoso, medicamentos, registros, LocalDateTime.now(relogio), de));
        return todos.stream()
                .filter(h -> de == null || !h.getDataHoraTomada().toLocalDate().isBefore(de))
                .filter(h -> ate == null || !h.getDataHoraTomada().toLocalDate().isAfter(ate))
                .sorted(Comparator.comparing(HistoricoMedicamento::getDataHoraTomada).thenComparing(h -> h.getMedicamento().getNome()))
                .toList();
    }
}
