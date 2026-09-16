package br.com.adapter.out.persistence;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

import br.com.domain.exception.ArquivoCsvCorrompidoException;
import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.util.ArquivoCsvUtil;
import br.com.domain.util.LeituraCsvUtil;

public class MedicamentoCsvAdapter implements SalvarMedicamentoPort {

    private static final String ARQUIVO = "arquivo/medicamentos.csv";

    @Override
    public void salvar(Medicamento medicamento) {
        ArquivoCsvUtil.escreverLinha(ARQUIVO, montarLinha(medicamento));
    }

    @Override
    public List<Medicamento> listarTodos() {
        List<Medicamento> resultado = new ArrayList<>();

        for (String linha : ArquivoCsvUtil.lerLinhas(ARQUIVO)) {
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
        return LeituraCsvUtil.buscarPrimeiro(
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
        List<String> linhas = new ArrayList<>();
        for (Medicamento m : medicamentos) {
            linhas.add(montarLinha(m));
        }
        ArquivoCsvUtil.reescreverLinhas(ARQUIVO, linhas);
    }

    private String montarLinha(Medicamento medicamento) {
        return medicamento.getId() + ";" +
                medicamento.getIdosoId() + ";" +
                medicamento.getNome() + ";" +
                medicamento.getHorarioMedicamento() + ";" +
                medicamento.getDiaSemana() + ";" +
                medicamento.getTipoMedicamento();
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