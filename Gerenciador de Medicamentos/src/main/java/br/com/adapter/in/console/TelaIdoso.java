package br.com.adapter.in.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import br.com.application.service.CriarVinculoService;
import br.com.application.service.GerenciarVinculoService;
import br.com.application.service.RegistrarTomadaService;
import br.com.application.service.VerificarNotificacoesIdosoService;
import br.com.config.AppConfig;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.exception.UsuarioNaoEncontradoException;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.Familiar;
import br.com.domain.model.NotificacaoMedicamento;
import br.com.domain.model.PedidoVinculo;
import br.com.domain.model.Usuario;

public class TelaIdoso {

    private final Scanner scanner;
    private final Idoso idoso;
    private final VerificarNotificacoesIdosoService verificarNotificacoesIdosoService;
    private final RegistrarTomadaService registrarTomadaService;
    private final CriarVinculoService criarVinculoService;
    private final GerenciarVinculoService gerenciarVinculoService;

    public TelaIdoso(Scanner scanner, Idoso idoso) {
        this.scanner = scanner;
        this.idoso = idoso;
        this.verificarNotificacoesIdosoService = AppConfig.criarVerificarNotificacoesIdosoService();
        this.registrarTomadaService = AppConfig.criarRegistrarTomadaService();
        this.criarVinculoService = AppConfig.criarCriarVinculoService();
        this.gerenciarVinculoService = AppConfig.criarGerenciarVinculoService();
    }

    public void exibir() {
        exibirNotificacoes();
        avisarPedidosDeVinculo();

        boolean continuar = true;
        while (continuar) {
            System.out.println("\n=== Área do idoso: " + idoso.getNome() + " ===");
            System.out.println("1 - Gerenciar medicamentos (ver, cadastrar, editar, excluir)");
            System.out.println("2 - Marcar remédio como tomado");
            System.out.println("3 - Editar meus dados");
            System.out.println("4 - Adicionar um familiar");
            System.out.println("5 - Pedidos de familiares para acompanhar você");
            System.out.println("6 - Remover um familiar");
            System.out.println("0 - Deslogar");
            System.out.print("Escolha uma opção: ");

            switch (scanner.nextLine()) {
                case "1" -> new PainelMedicamentos(scanner, idoso).exibir();
                case "2" -> marcarComoTomado();
                case "3" -> new PainelPerfil(scanner, idoso).exibir();
                case "4" -> vincularFamiliar();
                case "5" -> responderPedidos();
                case "6" -> removerFamiliar();
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
        List<Medicamento> medicamentos = new ArrayList<>(AppConfig.getMedicamentoPort().listarPorIdoso(idoso.getId()));

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

    private void avisarPedidosDeVinculo() {
        int pedidos = gerenciarVinculoService.listarPedidosPendentes(idoso.getId()).size();
        if (pedidos > 0) {
            System.out.println("Você tem " + pedidos + " pedido(s) de familiares para acompanhar você (opção 5).");
        }
    }

    private void responderPedidos() {
        List<PedidoVinculo> pedidos = gerenciarVinculoService.listarPedidosPendentes(idoso.getId());
        if (pedidos.isEmpty()) {
            System.out.println("Nenhum pedido pendente.");
            return;
        }
        for (int i = 0; i < pedidos.size(); i++) {
            Familiar f = pedidos.get(i).getFamiliar();
            System.out.println((i + 1) + " - " + f.getNome() + " (" + f.getEmail() + ") pediu em " + pedidos.get(i).getSolicitadoEm());
        }
        System.out.print("Qual pedido você quer responder? (0 para voltar) ");
        int indice = lerIndice(pedidos.size());
        if (indice < 0) {
            return;
        }
        Familiar familiar = pedidos.get(indice).getFamiliar();
        System.out.print("A - Aceitar  |  R - Recusar: ");
        String resposta = scanner.nextLine().trim().toUpperCase();
        try {
            switch (resposta) {
                case "A" -> {
                    gerenciarVinculoService.aceitarPedido(idoso.getId(), familiar.getId());
                    System.out.println(familiar.getNome() + " agora acompanha você.");
                }
                case "R" -> {
                    gerenciarVinculoService.recusarPedido(idoso.getId(), familiar.getId());
                    System.out.println("Pedido recusado.");
                }
                default -> System.out.println("Opção inválida.");
            }
        } catch (DadosInvalidosException | UnsupportedOperationException e) {
            System.out.println(e.getMessage());
        }
    }

    private void removerFamiliar() {
        Idoso atual = (Idoso) AppConfig.getUsuarioPort().buscarPorId(idoso.getId());
        List<Familiar> familiares = atual.getFamiliares();
        if (familiares.isEmpty()) {
            System.out.println("Você não tem familiares vinculados.");
            return;
        }
        for (int i = 0; i < familiares.size(); i++) {
            System.out.println((i + 1) + " - " + familiares.get(i).getNome() + " (" + familiares.get(i).getEmail() + ")");
        }
        System.out.print("Qual familiar você quer remover? (0 para voltar) ");
        int indice = lerIndice(familiares.size());
        if (indice < 0) {
            return;
        }
        Familiar familiar = familiares.get(indice);
        System.out.print("Remover " + familiar.getNome() + "? Ele(a) deixará de ver seus remédios e avisos. (S/N): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("S")) {
            System.out.println("Nada foi removido.");
            return;
        }
        try {
            gerenciarVinculoService.removerFamiliar(idoso.getId(), familiar.getId());
            System.out.println(familiar.getNome() + " foi removido.");
        } catch (DadosInvalidosException | UnsupportedOperationException e) {
            System.out.println(e.getMessage());
        }
    }

    /** Lê um número de 1 a {@code total} e devolve o índice (0-based); devolve -1 se for 0 ou inválido. */
    private int lerIndice(int total) {
        try {
            int numero = Integer.parseInt(scanner.nextLine().trim());
            if (numero >= 1 && numero <= total) {
                return numero - 1;
            }
        } catch (NumberFormatException e) {
            // cai no retorno abaixo
        }
        System.out.println("Voltando.");
        return -1;
    }

    private int buscarIdPorEmail(String email) {
        Usuario usuario = AppConfig.getUsuarioPort().buscarPorEmail(email);
        if (usuario == null) {
            throw new DadosInvalidosException("Nenhum usuário encontrado com o email " + email + ".");
        }
        return usuario.getId();
    }
}