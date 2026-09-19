package br.com.adapter.in.console;

import java.util.List;
import java.util.Scanner;

import br.com.application.service.BuscarHistoricoPorIdosoService;
import br.com.application.service.GerenciarVinculoService;
import br.com.application.service.VerificarNotificacoesIdosoService;
import br.com.config.AppConfig;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.exception.UsuarioNaoEncontradoException;
import br.com.domain.model.Familiar;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.NotificacaoMedicamento;
import br.com.domain.model.Usuario;

public class TelaFamiliar {

    private final Scanner scanner;
    private final Familiar familiar;
    private final VerificarNotificacoesIdosoService verificarNotificacoesIdosoService;
    private final BuscarHistoricoPorIdosoService buscarHistoricoPorIdosoService;
    private final GerenciarVinculoService gerenciarVinculoService;

    public TelaFamiliar(Scanner scanner) {
        this.scanner = scanner;
        this.familiar = (Familiar) SessaoAtual.getUsuarioLogado();
        this.verificarNotificacoesIdosoService = AppConfig.criarVerificarNotificacoesIdosoService();
        this.buscarHistoricoPorIdosoService = AppConfig.criarBuscarHistoricoPorIdosoService();
        this.gerenciarVinculoService = AppConfig.criarGerenciarVinculoService();
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
                    System.out.println((i + 1) + " - Acessar " + idosos.get(i).getNome());
                }
            }
            System.out.println("E - Editar meus dados");
            System.out.println("V - Pedir para acompanhar um idoso");
            System.out.println("0 - Deslogar");
            System.out.print("Escolha uma opção: ");

            String opcao = scanner.nextLine();

            switch (opcao.toUpperCase()) {
                case "0" -> continuar = false;
                case "E" -> new PainelPerfil(scanner, familiar).exibir();
                case "V" -> vincularIdoso();
                default -> abrirIdosoSelecionado(opcao, idosos);
            }
        }
    }

    private void abrirIdosoSelecionado(String opcao, List<Idoso> idosos) {
        try {
            int indice = Integer.parseInt(opcao) - 1;
            if (indice >= 0 && indice < idosos.size()) {
                menuDoIdoso(idosos.get(indice));
            } else {
                System.out.println("Opção inválida.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Opção inválida.");
        }
    }

    private void menuDoIdoso(Idoso idoso) {
        boolean continuar = true;
        while (continuar) {
            System.out.println("\n=== " + idoso.getNome() + " ===");
            System.out.println("1 - Gerenciar medicamentos (ver, cadastrar, editar, excluir)");
            System.out.println("2 - Ver histórico");
            System.out.println("0 - Voltar");
            System.out.print("Escolha uma opção: ");

            switch (scanner.nextLine()) {
                case "1" -> new PainelMedicamentos(scanner, idoso).exibir();
                case "2" -> verHistorico(idoso);
                case "0" -> continuar = false;
                default -> System.out.println("Opção inválida.");
            }
        }
    }

    private void verHistorico(Idoso idoso) {
        List<HistoricoMedicamento> historico = buscarHistoricoPorIdosoService.buscarHistoricoDoIdoso(idoso.getId());

        if (historico.isEmpty()) {
            System.out.println("Nenhum histórico registrado ainda.");
            return;
        }

        System.out.println("\n=== Histórico de " + idoso.getNome() + " ===");
        for (HistoricoMedicamento h : historico) {
            System.out.println(h);
        }
    }

    private void exibirNotificacoes() {
        for (Idoso idoso : familiar.getIdosos()) {
            for (NotificacaoMedicamento n : verificarNotificacoesIdosoService.verificarNotificacoes(idoso)) {
                Medicamento m = n.getMedicamento();
                switch (n.getTipo()) {
                    case TOMADO -> System.out.println(idoso.getNome() + " já tomou " + m.getNome() + " hoje.");
                    case ESQUECIDO -> System.out.println("Atenção: " + idoso.getNome() + " ainda não tomou " + m.getNome() + ", previsto para " + m.getHorarioMedicamento() + ".");
                    case LEMBRETE -> { }
                }
            }
        }
    }

    private void vincularIdoso() {
        System.out.print("Email ou id do idoso: ");
        String entrada = scanner.nextLine().trim();

        try {
            int idosoId = EntradaUtil.pareceId(entrada) ? Integer.parseInt(entrada) : buscarIdPorEmail(entrada);
            gerenciarVinculoService.solicitarVinculo(familiar.getId(), idosoId);
            System.out.println("Pedido enviado. O idoso tem 24 horas para aceitar.");
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