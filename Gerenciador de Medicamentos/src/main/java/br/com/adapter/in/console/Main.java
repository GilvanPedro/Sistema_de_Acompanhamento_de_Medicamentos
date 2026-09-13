package br.com.adapter.in.console;

import br.com.adapter.in.scheduler.AgendadorVerificacaoAtraso;
import br.com.adapter.out.notification.ConsoleNotificationAdapter;
import br.com.application.service.RegistrarMedicamentoService;
import br.com.application.service.RegistrarTomadaService;
import br.com.application.service.RegistrarUsuarioService;
import br.com.config.AppConfig;
import br.com.domain.model.*;
import br.com.domain.port.out.*;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {

        RegistrarUsuarioService registrarUsuarioService = AppConfig.criarRegistrarUsuarioService();
        RegistrarMedicamentoService registrarMedicamentoService = AppConfig.criarRegistrarMedicamentoService();
        SalvarUsuarioPort usuarioPort = AppConfig.getUsuarioPort();
        SalvarMedicamentoPort medicamentoPort = AppConfig.getMedicamentoPort();
        SalvarHistoricoPort historicoPort = AppConfig.getHistoricoPort();
        NotificarPort notificarPort = new ConsoleNotificationAdapter();

        System.out.println("=== 1. Cadastro de usuários ===");

        Idoso idoso1 = (Idoso) registrarUsuarioService.registrarIdoso("Pedro Carlos", "pedrocarlos@email.com", "432156");
        Familiar familiar1 = (Familiar) registrarUsuarioService.registrarFamiliar("Marcos Henrrique", "marcos@email.com", "34b56gf");

        idoso1.adicionarFamiliares(familiar1);
        familiar1.adicionarIdosos(idoso1);
        usuarioPort.salvarVinculo(idoso1.getId(), familiar1.getId());

        System.out.println("Idoso cadastrado: " + idoso1);
        System.out.println("Familiar cadastrado: " + familiar1);

        System.out.println("\n=== 2. Listando todos os usuários (lidos do CSV) ===");

        for (Usuario usuario : usuarioPort.listarTodos()) {
            System.out.println(usuario);
        }

        System.out.println("\n=== 3. Buscando por nome ===");

        List<Usuario> encontrados = usuarioPort.buscarPorNome("Maria");
        for (Usuario usuario : encontrados) {
            System.out.println("Encontrado: " + usuario);
        }

        System.out.println("\n=== 4. Idoso com os cuidadores por extenso ===");

        for (Usuario usuario : encontrados) {
            if (usuario instanceof Idoso idosoEncontrado) {
                System.out.println(idosoEncontrado);
                System.out.println("Cuidadores:");
                for (Familiar familiar : idosoEncontrado.getFamiliares()) {
                    System.out.println("  - " + familiar);
                }
            }
        }

        System.out.println("\n=== 5. Criando um segundo familiar e vinculando ao mesmo idoso ===");

        Familiar familiar2 = (Familiar) registrarUsuarioService.registrarFamiliar("Carlos Souza", "carlos@email.com", "senha123");
        idoso1.adicionarFamiliares(familiar2);
        familiar2.adicionarIdosos(idoso1);
        usuarioPort.salvarVinculo(idoso1.getId(), familiar2.getId());

        System.out.println("Idoso agora com " + idoso1.getFamiliares().size() + " cuidadores: " + idoso1);

        System.out.println("\n=== 6. Cadastro de medicamento ===");

        Medicamento medicamento1 = registrarMedicamentoService.registrarMedicamento(
                "Losartana", DayOfWeek.SUNDAY, LocalTime.of(16, 7), TipoMedicamento.COMPRIMIDO, idoso1.getId()
        );

        System.out.println("Medicamento cadastrado: " + medicamento1);

        System.out.println("\n=== 7. Listando todos os medicamentos (lidos do CSV) ===");

        for (Medicamento m : medicamentoPort.listarTodos()) {
            System.out.println(m);
        }

        System.out.println("\n=== 10. Lendo o histórico de volta do CSV ===");

        Map<Integer, Idoso> mapaIdosos = new HashMap<>();
        for (Usuario u : usuarioPort.listarTodos()) {
            if (u instanceof Idoso idoso) {
                mapaIdosos.put(idoso.getId(), idoso);
            }
        }

        Map<Integer, Medicamento> mapaMedicamentos = new HashMap<>();
        for (Medicamento m : medicamentoPort.listarTodos()) {
            mapaMedicamentos.put(m.getId(), m);
        }

        for (HistoricoMedicamento h : historicoPort.listarTodos(mapaIdosos, mapaMedicamentos)) {
            System.out.println(h);
        }

        System.out.println("\n=== 11. Simulando notificações ===");

        notificarPort.lembrarIdoso(idoso1, medicamento1);
        notificarPort.avisarRemedioTomado(idoso1, medicamento1);
        notificarPort.avisarRemedioEsquecido(idoso1, medicamento1);

        System.out.println("\n=== 12. Excluindo o medicamento ===");

        medicamentoPort.excluir(medicamento1.getId());

        System.out.println("Medicamentos restantes:");
        for (Medicamento m : medicamentoPort.listarTodos()) {
            System.out.println(m);
        }
        if (medicamentoPort.listarTodos().isEmpty()) {
            System.out.println("(nenhum — a exclusão funcionou)");
        }

        System.out.println("\n=== Fim dos testes ===");

        AgendadorVerificacaoAtraso agendador = new AgendadorVerificacaoAtraso(AppConfig.criarVerificarAtrasoMedicamentoService());
        agendador.iniciar();

        System.out.println("\n=== Agendador rodando — verificando atrasos a cada minuto ===");
    }
}