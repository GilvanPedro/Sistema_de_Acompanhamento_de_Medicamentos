package br.com.adapter.in.console;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import br.com.application.service.EditarMedicamentoService;
import br.com.application.service.ExcluirMedicamentoService;
import br.com.application.service.RegistrarMedicamentoService;
import br.com.config.AppConfig;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.exception.MedicamentoNaoEncontradoException;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.util.ConverterDiaSemanaUtil;

public class PainelMedicamentos {

    private final Scanner scanner;
    private final Idoso idoso;
    private final RegistrarMedicamentoService registrarMedicamentoService;
    private final EditarMedicamentoService editarMedicamentoService;
    private final ExcluirMedicamentoService excluirMedicamentoService;

    public PainelMedicamentos(Scanner scanner, Idoso idoso) {
        this.scanner = scanner;
        this.idoso = idoso;
        this.registrarMedicamentoService = AppConfig.criarRegistrarMedicamentoService();
        this.editarMedicamentoService = AppConfig.criarEditarMedicamentoService();
        this.excluirMedicamentoService = AppConfig.criarExcluirMedicamentoService();
    }

    public void exibir() {
        boolean continuar = true;
        while (continuar) {
            System.out.println("\n=== Medicamentos de " + idoso.getNome() + " ===");
            System.out.println("1 - Ver medicamentos");
            System.out.println("2 - Cadastrar medicamento");
            System.out.println("3 - Editar medicamento");
            System.out.println("4 - Excluir medicamento");
            System.out.println("0 - Voltar");
            System.out.print("Escolha uma opção: ");

            switch (scanner.nextLine()) {
                case "1" -> listar();
                case "2" -> cadastrar();
                case "3" -> editar();
                case "4" -> excluir();
                case "0" -> continuar = false;
                default -> System.out.println("Opção inválida.");
            }
        }
    }

    private List<Medicamento> medicamentosDoIdoso() {
        List<Medicamento> resultado = new ArrayList<>();
        for (Medicamento m : AppConfig.getMedicamentoPort().listarTodos()) {
            if (m.getIdosoId() == idoso.getId()) {
                resultado.add(m);
            }
        }
        return resultado;
    }

    private void listar() {
        List<Medicamento> medicamentos = medicamentosDoIdoso();
        if (medicamentos.isEmpty()) {
            System.out.println("Nenhum medicamento cadastrado ainda.");
            return;
        }
        for (Medicamento m : medicamentos) {
            System.out.println(m);
        }
    }

    private void cadastrar() {
        try {
            System.out.print("Nome do medicamento: ");
            String nome = scanner.nextLine();
            System.out.print("Horário (HH:mm): ");
            LocalTime horario = LocalTime.parse(scanner.nextLine());
            System.out.print("Dia da semana (ex: sexta, sexta feira ou sexta-feira): ");
            DayOfWeek dia = ConverterDiaSemanaUtil.converter(scanner.nextLine());
            System.out.print("Tipo (COMPRIMIDO, GOTAS, INJECAO, OUTRO): ");
            TipoMedicamento tipo = TipoMedicamento.valueOf(scanner.nextLine().toUpperCase());

            Medicamento medicamento = registrarMedicamentoService.registrarMedicamento(nome, dia, horario, tipo, idoso.getId());
            System.out.println("Medicamento cadastrado: " + medicamento);
        } catch (DadosInvalidosException | IllegalArgumentException e) {
            System.out.println("Erro no cadastro: " + e.getMessage());
        }
    }

    private void editar() {
        List<Medicamento> medicamentos = medicamentosDoIdoso();
        if (medicamentos.isEmpty()) {
            System.out.println("Nenhum medicamento cadastrado ainda.");
            return;
        }

        for (int i = 0; i < medicamentos.size(); i++) {
            System.out.println((i + 1) + " - " + medicamentos.get(i));
        }
        System.out.print("Qual medicamento editar? ");

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

        Medicamento selecionado = medicamentos.get(indice);
        System.out.println("Deixe em branco pra manter o valor atual.");

        try {
            System.out.print("Novo nome (" + selecionado.getNome() + "): ");
            String nomeDigitado = scanner.nextLine();
            String nome = nomeDigitado.isBlank() ? null : nomeDigitado;

            System.out.print("Novo horário HH:mm (" + selecionado.getHorarioMedicamento() + "): ");
            String horarioDigitado = scanner.nextLine();
            LocalTime horario = horarioDigitado.isBlank() ? null : LocalTime.parse(horarioDigitado);

            System.out.print("Novo dia da semana, ex: sexta, sexta feira ou sexta-feira (" + selecionado.getDiaSemana() + "): ");
            String diaDigitado = scanner.nextLine();
            DayOfWeek dia = diaDigitado.isBlank() ? null : ConverterDiaSemanaUtil.converter(diaDigitado);

            System.out.print("Novo tipo (" + selecionado.getTipoMedicamento() + "): ");
            String tipoDigitado = scanner.nextLine();
            TipoMedicamento tipo = tipoDigitado.isBlank() ? null : TipoMedicamento.valueOf(tipoDigitado.toUpperCase());

            Medicamento atualizado = editarMedicamentoService.editarMedicamento(selecionado.getId(), nome, horario, dia, tipo);
            System.out.println("Medicamento atualizado: " + atualizado);
        } catch (DadosInvalidosException | MedicamentoNaoEncontradoException | IllegalArgumentException e) {
            System.out.println("Erro ao editar: " + e.getMessage());
        }
    }

    private void excluir() {
        List<Medicamento> medicamentos = medicamentosDoIdoso();
        if (medicamentos.isEmpty()) {
            System.out.println("Nenhum medicamento cadastrado ainda.");
            return;
        }

        for (int i = 0; i < medicamentos.size(); i++) {
            System.out.println((i + 1) + " - " + medicamentos.get(i));
        }
        System.out.print("Qual medicamento excluir? ");

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

        Medicamento selecionado = medicamentos.get(indice);
        excluirMedicamentoService.excluirMedicamento(selecionado.getId());
        System.out.println("Medicamento excluído: " + selecionado.getNome());
    }
}