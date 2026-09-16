package br.com.adapter.in.console;

import br.com.adapter.out.persistence.UsuarioCsvAdapter;
import br.com.application.service.RegistrarMedicamentoService;
import br.com.application.service.RegistrarTomadaService;
import br.com.config.AppConfig;
import br.com.domain.model.*;
import br.com.domain.port.out.SalvarHistoricoPort;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

public class Teste2 {
    public static void main(String[] args) {

        System.out.println("\n=== Testando RegistrarTomadaService ===");
        UsuarioCsvAdapter usuarioCsvAdapter = new UsuarioCsvAdapter();

        Idoso idoso = (Idoso) usuarioCsvAdapter.buscarPorId(1);

        RegistrarMedicamentoService registrarMedicamentoService = AppConfig.criarRegistrarMedicamentoService();
        Medicamento medicamentoTeste = registrarMedicamentoService.registrarMedicamento(
                "Teste Historico", DayOfWeek.MONDAY, LocalTime.of(8, 0), TipoMedicamento.COMPRIMIDO, idoso.getId()
        );
        System.out.println("Medicamento de teste criado: " + medicamentoTeste);

        Map<Integer, Idoso> idososMap = new HashMap<>();
        for (Usuario u : usuarioCsvAdapter.listarTodos()) {
            if (u instanceof Idoso i) {
                idososMap.put(i.getId(), i);
            }
        }

        Map<Integer, Medicamento> medicamentosMap = new HashMap<>();
        for (Medicamento m : AppConfig.getMedicamentoPort().listarTodos()) {
            medicamentosMap.put(m.getId(), m);
        }

        SalvarHistoricoPort historicoPort = AppConfig.getHistoricoPort();
        int quantidadeAntes = historicoPort.listarHistoricoPorIdoso(idoso.getId(), idososMap, medicamentosMap).size();
        System.out.println("Registros de histórico ANTES: " + quantidadeAntes);

        RegistrarTomadaService registrarTomadaService = AppConfig.criarRegistrarTomadaService();
        HistoricoMedicamento historicoRegistrado = registrarTomadaService.registrarTomada(idoso, medicamentoTeste, true);
        System.out.println("Retorno do service: " + historicoRegistrado);

        int quantidadeDepois = historicoPort.listarHistoricoPorIdoso(idoso.getId(), idososMap, medicamentosMap).size();
        System.out.println("Registros de histórico DEPOIS: " + quantidadeDepois);

        if (historicoRegistrado != null && quantidadeDepois == quantidadeAntes + 1) {
            System.out.println("OK: RegistrarTomadaService salvou o histórico corretamente.");
        } else {
            System.out.println("FALHOU: o histórico não foi salvo como esperado.");
        }
    }
}