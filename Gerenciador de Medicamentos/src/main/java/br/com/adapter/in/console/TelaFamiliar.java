package br.com.adapter.in.console;

import java.util.List;
import java.util.Scanner;

import br.com.application.service.VerificarNotificacoesIdosoService;
import br.com.config.AppConfig;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.NotificacaoMedicamento;

public class TelaFamiliar {

    private final Scanner scanner;
    private final Familiar familiar;
    private final VerificarNotificacoesIdosoService verificarNotificacoesIdosoService;

    public TelaFamiliar(Scanner scanner) {
        this.scanner = scanner;
        this.familiar = (Familiar) SessaoAtual.getUsuarioLogado();
        this.verificarNotificacoesIdosoService = AppConfig.criarVerificarNotificacoesIdosoService();
    }

    public void exibir() {
        exibirNotificacoes();

        boolean continuar = true;
        while (continuar) {
            System.out.println("\n=== Área do familiar: " + familiar.getNome() + " ===");

            List<Idoso> idosos = familiar.getIdosos();
            if (idosos.isEmpty()) {
                System.out.println("Você não está vinculado a nenhum idoso ainda.");
            } else {
                for (int i = 0; i < idosos.size(); i++) {
                    System.out.println((i + 1) + " - Ver medicamentos de " + idosos.get(i).getNome());
                }
            }
            System.out.println("0 - Deslogar");
            System.out.print("Escolha uma opção: ");

            String opcao = scanner.nextLine();
            if (opcao.equals("0")) {
                continuar = false;
                continue;
            }

            try {
                int indice = Integer.parseInt(opcao) - 1;
                if (indice >= 0 && indice < idosos.size()) {
                    new PainelMedicamentos(scanner, idosos.get(indice)).exibir();
                } else {
                    System.out.println("Opção inválida.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Opção inválida.");
            }
        }
    }

    private void exibirNotificacoes() {
        for (Idoso idoso : familiar.getIdosos()) {
            for (NotificacaoMedicamento n : verificarNotificacoesIdosoService.verificarNotificacoes(idoso)) {
                Medicamento m = n.getMedicamento();
                switch (n.getTipo()) {
                    case TOMADO -> System.out.println(idoso.getNome() + " já tomou " + m.getNome() + " hoje.");
                    case ESQUECIDO -> System.out.println("Atenção: " + idoso.getNome() + " ainda não tomou " + m.getNome() + ", previsto para " + m.getHorarioMedicamento() + ".");
                    case LEMBRETE -> { } // lembrete de "hora de tomar" é só pro próprio idoso
                }
            }
        }
    }

    private void listarMedicamentosDoIdoso(Idoso idoso) {
        System.out.println("\n=== Medicamentos de " + idoso.getNome() + " ===");
        boolean encontrouAlgum = false;
        for (Medicamento m : AppConfig.getMedicamentoPort().listarTodos()) {
            if (m.getIdosoId() == idoso.getId()) {
                System.out.println(m);
                encontrouAlgum = true;
            }
        }
        if (!encontrouAlgum) {
            System.out.println("Nenhum medicamento cadastrado ainda.");
        }
    }
}