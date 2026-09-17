package br.com.adapter.in.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import br.com.application.service.CriarVinculoService;
import br.com.application.service.RegistrarTomadaService;
import br.com.application.service.VerificarNotificacoesIdosoService;
import br.com.config.AppConfig;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.exception.UsuarioNaoEncontradoException;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.NotificacaoMedicamento;
import br.com.domain.model.Usuario;

public class TelaIdoso {

    private final Scanner scanner;
    private final Idoso idoso;
    private final VerificarNotificacoesIdosoService verificarNotificacoesIdosoService;
    private final RegistrarTomadaService registrarTomadaService;
    private final CriarVinculoService criarVinculoService;

    public TelaIdoso(Scanner scanner, Idoso idoso) {
        this.scanner = scanner;
        this.idoso = idoso;
        this.verificarNotificacoesIdosoService = AppConfig.criarVerificarNotificacoesIdosoService();
        this.registrarTomadaService = AppConfig.criarRegistrarTomadaService();
        this.criarVinculoService = AppConfig.criarCriarVinculoService();
    }

    public void exibir() {
        exibirNotificacoes();

        boolean continuar = true;
        while (continuar) {
            System.out.println("\n=== Área do idoso: " + idoso.getNome() + " ===");
            System.out.println("1 - Gerenciar medicamentos (ver, cadastrar, editar, excluir)");
            System.out.println("2 - Marcar remédio como tomado");
            System.out.println("3 - Editar meus dados");
            System.out.println("4 - Vincular um familiar");
            System.out.println("0 - Deslogar");
            System.out.print("Escolha uma opção: ");

            switch (scanner.nextLine()) {
                case "1" -> new PainelMedicamentos(scanner, idoso).exibir();
                case "2" -> marcarComoTomado();
                case "3" -> new PainelPerfil(scanner, idoso).exibir();
                case "4" -> vincularFamiliar();
                case "0" -> continuar = false;
                default -> System.out.println("Opção inválida.");
            }
        }
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

    private void marcarComoTomado() {
        List<Medicamento> medicamentos = new ArrayList<>();
        for (Medicamento m : AppConfig.getMedicamentoPort().listarTodos()) {
            if (m.getIdosoId() == idoso.getId()) {
                medicamentos.add(m);
            }
        }

        if (medicamentos.isEmpty()) {
            System.out.println("Nenhum medicamento cadastrado ainda.");
            return;
        }

        for (int i = 0; i < medicamentos.size(); i++) {
            System.out.println((i + 1) + " - " + medicamentos.get(i));
        }
        System.out.print("Qual medicamento você tomou? ");

        int indice;
        try {
            indice = Integer.parseInt(scanner.nextLine()) - 1;
        } catch (NumberFormatException e) {
            System.out.println("Opção inválida.");
            return;
        }

        if (indice < 0 || indice >= medicamentos.size()) {
            System.out.println("Opção inválida.");
            return;
        }

        Medicamento medicamento = medicamentos.get(indice);
        try {
            HistoricoMedicamento historico = registrarTomadaService.registrarTomada(idoso, medicamento, true);
            System.out.println("Registrado: você tomou " + medicamento.getNome() + " às " + historico.getDataHoraTomada() + ".");
        } catch (DadosInvalidosException e) {
            System.out.println(e.getMessage());
        }
    }

    private void vincularFamiliar() {
        System.out.print("Email ou id do familiar: ");
        String entrada = scanner.nextLine().trim();

        try {
            int familiarId = EntradaUtil.pareceId(entrada) ? Integer.parseInt(entrada) : buscarIdPorEmail(entrada);
            criarVinculoService.criarVinculo(idoso.getId(), familiarId);
            System.out.println("Vínculo criado com sucesso.");
        } catch (DadosInvalidosException | UsuarioNaoEncontradoException | IllegalArgumentException e) {
            System.out.println("Erro ao vincular: " + e.getMessage());
        }
    }

    private int buscarIdPorEmail(String email) {
        Usuario usuario = AppConfig.getUsuarioPort().buscarPorEmail(email);
        if (usuario == null) {
            throw new DadosInvalidosException("Nenhum usuário encontrado com o email " + email + ".");
        }
        return usuario.getId();
    }
}