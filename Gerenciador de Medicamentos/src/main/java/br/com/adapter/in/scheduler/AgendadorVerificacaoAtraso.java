package br.com.adapter.in.scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import br.com.domain.port.in.VerificarAtrasoMedicamentoCase;

public class AgendadorVerificacaoAtraso {

    private final VerificarAtrasoMedicamentoCase verificarAtrasoMedicamentoCase;
    private final ScheduledExecutorService executor;

    public AgendadorVerificacaoAtraso(VerificarAtrasoMedicamentoCase verificarAtrasoMedicamentoCase) {
        this.verificarAtrasoMedicamentoCase = verificarAtrasoMedicamentoCase;
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }

    public void iniciar() {
        executor.scheduleAtFixedRate(() -> {
            try {
                verificarAtrasoMedicamentoCase.verificarAtrasos();
            } catch (Exception e) {
                System.err.println("Erro ao verificar atrasos: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    public void parar() {
        executor.shutdown();
    }
}