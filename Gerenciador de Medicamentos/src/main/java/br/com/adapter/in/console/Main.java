package br.com.adapter.in.console;

import br.com.adapter.out.notification.ConsoleNotificationAdapter;
import br.com.application.service.RegistrarMedicamentoService;
import br.com.application.service.RegistrarUsuarioService;
import br.com.config.AppConfig;
import br.com.domain.model.*;
import br.com.domain.port.out.NotificarPort;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class Main {
    public static void main(String[] args) {

        RegistrarUsuarioService registrarUsuarioService = AppConfig.criarRegistrarUsuarioService();
        RegistrarMedicamentoService registrarMedicamentoService = AppConfig.criarRegistrarMedicamentoService();
        NotificarPort notificarPort = new ConsoleNotificationAdapter();

        System.out.println("=== Cadastro de usuários ===");

        Idoso idoso1 = (Idoso) registrarUsuarioService.registrarIdoso("Maria Silva", "maria@email.com", "123456");
        Idoso idoso2 = (Idoso) registrarUsuarioService.registrarIdoso("João Souza", "joao@email.com", "654321");
        Familiar familiar1 = (Familiar) registrarUsuarioService.registrarFamiliar("Ana Silva", "ana@email.com", "abc123");

        System.out.println("Id gerado para Maria: " + idoso1.getId());
        System.out.println("Id gerado para João: " + idoso2.getId());
        System.out.println("Id gerado para Ana: " + familiar1.getId());

        System.out.println("\n=== Vinculando idoso e familiar ===");

        idoso1.adicionarFamiliares(familiar1);
        familiar1.adicionarIdosos(idoso1);

        System.out.println("Familiares de Maria: " + idoso1.getFamiliares().size());
        System.out.println("Idosos acompanhados por Ana: " + familiar1.getIdosos().size());

        System.out.println("\n=== Cadastro de medicamento ===");

        Medicamento medicamento1 = registrarMedicamentoService.registrarMedicamento(
                "Losartana",
                DayOfWeek.MONDAY,
                LocalTime.of(8, 0),
                TipoMedicamento.COMPRIMIDO
        );

        System.out.println("Id gerado para o medicamento: " + medicamento1.getId());
        System.out.println(medicamento1);

        System.out.println("\n=== Simulando notificações ===");

        notificarPort.lembrarIdoso(idoso1, medicamento1);
        notificarPort.avisarRemedioTomado(idoso1, medicamento1);
        notificarPort.avisarRemedioEsquecido(idoso1, medicamento1);

        System.out.println("\n=== Idoso sem familiar vinculado (não deve travar nem imprimir aviso) ===");

        notificarPort.avisarRemedioTomado(idoso2, medicamento1);
        System.out.println("(nenhuma linha acima significa que a lista de familiares do João está vazia, como esperado)");

        System.out.println("\n=== Testando validação (dados inválidos) ===");

        try {
            registrarUsuarioService.registrarIdoso("", "emailinvalido", "");
        } catch (IllegalArgumentException e) {
            System.out.println("Validação funcionou, erro capturado: " + e.getMessage());
        }

        try {
            registrarMedicamentoService.registrarMedicamento(null, null, null, null);
        } catch (IllegalArgumentException e) {
            System.out.println("Validação funcionou, erro capturado: " + e.getMessage());
        }
    }
}