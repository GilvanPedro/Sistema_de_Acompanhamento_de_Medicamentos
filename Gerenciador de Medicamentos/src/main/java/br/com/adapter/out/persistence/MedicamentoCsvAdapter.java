package br.com.adapter.out.persistence;

import java.io.*;
import java.nio.file.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.port.out.SalvarMedicamentoPort;

public class MedicamentoCsvAdapter implements SalvarMedicamentoPort {

    private static final String ARQUIVO = "medicamentos.csv";

    @Override
    public void salvar(Medicamento medicamento) {
        escreverLinha(montarLinha(medicamento));
    }

    @Override
    public List<Medicamento> listarTodos() {
        List<Medicamento> resultado = new ArrayList<>();

        for (String linha : lerLinhas()) {
            String[] campos = linha.split(";");
            int id = Integer.parseInt(campos[0]);
            String nome = campos[1];
            LocalTime horario = LocalTime.parse(campos[2]);
            DayOfWeek diaSemana = DayOfWeek.valueOf(campos[3]);
            TipoMedicamento tipo = TipoMedicamento.valueOf(campos[4]);

            resultado.add(new Medicamento(id, nome, horario, diaSemana, tipo));
        }

        return resultado;
    }

    @Override
    public void atualizar(Medicamento medicamento) {
        List<Medicamento> todos = listarTodos();
        List<Medicamento> atualizados = new ArrayList<>();

        for (Medicamento m : todos) {
            atualizados.add(m.getId() == medicamento.getId() ? medicamento : m);
        }

        reescreverArquivo(atualizados);
    }

    @Override
    public void excluir(int id) {
        List<Medicamento> todos = listarTodos();
        List<Medicamento> restantes = new ArrayList<>();

        for (Medicamento m : todos) {
            if (m.getId() != id) {
                restantes.add(m);
            }
        }

        reescreverArquivo(restantes);
    }

    private void reescreverArquivo(List<Medicamento> medicamentos) {
        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(ARQUIVO), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Medicamento m : medicamentos) {
                writer.write(montarLinha(m));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao reescrever " + ARQUIVO, e);
        }
    }

    private String montarLinha(Medicamento medicamento) {
        return medicamento.getId() + ";" +
                medicamento.getNome() + ";" +
                medicamento.getHorarioMedicamento() + ";" +
                medicamento.getDiaSemana() + ";" +
                medicamento.getTipoMedicamento();
    }

    private void escreverLinha(String linha) {
        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(ARQUIVO), StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(linha);
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar em " + ARQUIVO, e);
        }
    }

    private List<String> lerLinhas() {
        Path caminho = Paths.get(ARQUIVO);
        if (!Files.exists(caminho)) {
            return new ArrayList<>();
        }
        try {
            return Files.readAllLines(caminho);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler " + ARQUIVO, e);
        }
    }
}