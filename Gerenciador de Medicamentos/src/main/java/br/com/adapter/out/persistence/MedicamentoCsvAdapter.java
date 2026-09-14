package br.com.adapter.out.persistence;

import java.io.*;
import java.nio.file.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

import br.com.domain.exception.ArquivoCsvCorrompidoException;
import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.util.LeituraCsvUtil;

public class MedicamentoCsvAdapter implements SalvarMedicamentoPort {

    private static final String ARQUIVO = "medicamentos.csv";
    private static LeituraCsvUtil leituraCsvUtil = new LeituraCsvUtil();

    @Override
    public void salvar(Medicamento medicamento) {
        escreverLinha(montarLinha(medicamento));
    }

    @Override
    public List<Medicamento> listarTodos() {
        List<Medicamento> resultado = new ArrayList<>();

        for (String linha : lerLinhas()) {
            try {
                String[] campos = linha.split(";");
                int id = Integer.parseInt(campos[0]);
                int idosoId = Integer.parseInt(campos[1]);
                String nome = campos[2];
                LocalTime horario = LocalTime.parse(campos[3]);
                DayOfWeek diaSemana = DayOfWeek.valueOf(campos[4]);
                TipoMedicamento tipo = TipoMedicamento.valueOf(campos[5]);

                resultado.add(new Medicamento(id, idosoId, nome, horario, diaSemana, tipo));
            } catch (RuntimeException e) {
                throw new ArquivoCsvCorrompidoException(ARQUIVO, linha, e);
            }
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
    public Medicamento buscarPorId(int id) {
        return leituraCsvUtil.buscarPrimeiro(
                ARQUIVO,
                linha -> {
                    String[] campos = linha.split(";");
                    return Integer.parseInt(campos[0]) == id;
                },
                this::converterLinhaParaMedicamento
        ).orElseThrow(() -> new NoSuchElementException("Medicamento com id: " + id + " não encontrado."));
    }

    @Override
    public void excluir(int id) {
        List<String> restantes = new ArrayList<>();

        for (String linha : lerLinhas()) {
            try {
                int idLinha = Integer.parseInt(linha.split(";")[0]);
                if (idLinha != id) {
                    restantes.add(linha);
                }
            } catch (RuntimeException e) {
                throw new ArquivoCsvCorrompidoException(ARQUIVO, linha, e);
            }
        }

        reescreverLinhasBrutas(ARQUIVO, restantes);
    }

    private void reescreverLinhasBrutas(String arquivo, List<String> linhas) {
        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(arquivo), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String linha : linhas) {
                writer.write(linha);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao reescrever " + arquivo, e);
        }
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
                medicamento.getIdosoId() + ";" +
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

    private Medicamento converterLinhaParaMedicamento(String linha) {
        try {
            String[] campos = linha.split(";");
            int id = Integer.parseInt(campos[0]);
            int idosoId = Integer.parseInt(campos[1]);
            String nome = campos[2];
            LocalTime horario = LocalTime.parse(campos[3]);
            DayOfWeek diaSemana = DayOfWeek.valueOf(campos[4]);
            TipoMedicamento tipo = TipoMedicamento.valueOf(campos[5]);

            return new Medicamento(id, idosoId, nome, horario, diaSemana, tipo);
        } catch (RuntimeException e) {
            throw new ArquivoCsvCorrompidoException(ARQUIVO, linha, e);
        }
    }
}