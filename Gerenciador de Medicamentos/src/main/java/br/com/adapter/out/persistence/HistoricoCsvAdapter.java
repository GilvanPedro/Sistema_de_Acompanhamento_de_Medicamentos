package br.com.adapter.out.persistence;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

import br.com.domain.exception.ArquivoCsvCorrompidoException;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.port.out.SalvarHistoricoPort;

public class HistoricoCsvAdapter implements SalvarHistoricoPort {

    private static final String ARQUIVO = "historico.csv";

    @Override
    public void salvar(HistoricoMedicamento historico) {
        escreverLinha(montarLinha(historico));
    }

    @Override
    public List<HistoricoMedicamento> listarTodos(Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
        List<HistoricoMedicamento> resultado = new ArrayList<>();

        for (String linha : lerLinhas()) {
            HistoricoMedicamento historico = montarObjeto(linha, idosos, medicamentos);
            if (historico != null) {
                resultado.add(historico);
            }
        }

        return resultado;
    }

    @Override
    public void atualizar(HistoricoMedicamento historico, Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
        List<HistoricoMedicamento> todos = listarTodos(idosos, medicamentos);
        List<HistoricoMedicamento> atualizados = new ArrayList<>();

        for (HistoricoMedicamento h : todos) {
            atualizados.add(h.getId() == historico.getId() ? historico : h);
        }

        reescreverArquivo(atualizados);
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

        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(ARQUIVO), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String linha : restantes) {
                writer.write(linha);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao reescrever " + ARQUIVO, e);
        }
    }

    @Override
    public List<HistoricoMedicamento> listarHistoricoPorIdoso(int idIdoso, Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
        List<HistoricoMedicamento> resultado = new ArrayList<>();

        for(String linha : lerLinhas()){
            HistoricoMedicamento historico = montarObjeto(linha, idosos, medicamentos);
            if(historico != null && historico.getIdoso().getId() == idIdoso){
                resultado.add(historico);
            }
        }
        return resultado;
    }

    private HistoricoMedicamento montarObjeto(String linha, Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
        try {
            String[] campos = linha.split(";");
            int id = Integer.parseInt(campos[0]);
            int idosoId = Integer.parseInt(campos[1]);
            int medicamentoId = Integer.parseInt(campos[2]);
            LocalDateTime dataHora = LocalDateTime.parse(campos[3]);
            boolean foiTomado = Boolean.parseBoolean(campos[4]);

            Idoso idoso = idosos.get(idosoId);
            Medicamento medicamento = medicamentos.get(medicamentoId);

            if (idoso == null || medicamento == null) {
                return null;
            }

            return new HistoricoMedicamento(id, medicamento, idoso, dataHora, foiTomado);
        } catch (RuntimeException e) {
            throw new ArquivoCsvCorrompidoException(ARQUIVO, linha, e);
        }
    }

    private void reescreverArquivo(List<HistoricoMedicamento> historicos) {
        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(ARQUIVO), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (HistoricoMedicamento h : historicos) {
                writer.write(montarLinha(h));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao reescrever " + ARQUIVO, e);
        }
    }

    private String montarLinha(HistoricoMedicamento historico) {
        return historico.getId() + ";" +
                historico.getIdoso().getId() + ";" +
                historico.getMedicamento().getId() + ";" +
                historico.getDataHoraTomada() + ";" +
                historico.isFoiTomado();
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