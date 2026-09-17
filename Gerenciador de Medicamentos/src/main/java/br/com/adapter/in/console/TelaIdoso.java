package br.com.adapter.in.console;

import java.util.Scanner;

import br.com.application.service.VerificarNotificacoesIdosoService;
import br.com.config.AppConfig;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.NotificacaoMedicamento;

public class TelaIdoso {

    private final Scanner scanner;
    private final Idoso idoso;
    private final VerificarNotificacoesIdosoService verificarNotificacoesIdosoService;

    public TelaIdoso(Scanner scanner, Idoso idoso) {
        this.scanner = scanner;
        this.idoso = idoso;
        this.verificarNotificacoesIdosoService = AppConfig.criarVerificarNotificacoesIdosoService();
    }

    public void exibir() {
        exibirNotificacoes();
        new PainelMedicamentos(scanner, idoso).exibir();
    }

    private void exibirNotificacoes() {
        for (NotificacaoMedicamento n : verificarNotificacoesIdosoService.verificarNotificacoes(idoso)) {
            Medicamento m = n.getMedicamento();
            switch (n.getTipo()) {
                case LEMBRETE -> System.out.println("Lembrete: está na hora de tomar " + m.getNome() + ".");
                case TOMADO -> System.out.println("Você já tomou " + m.getNome() + " hoje.");
                case ESQUECIDO -> System.out.println("Atenção: você ainda não tomou " + m.getNome() + ", previsto para " + m.getHorarioMedicamento() + ".");
            }
        }
    }
}