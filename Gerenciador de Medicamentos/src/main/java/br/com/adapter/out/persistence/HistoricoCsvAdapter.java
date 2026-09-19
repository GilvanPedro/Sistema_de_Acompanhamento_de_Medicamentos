package br.com.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.*;

import br.com.domain.exception.ArquivoCsvCorrompidoException;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.util.ArquivoCsvUtil;

public class HistoricoCsvAdapter implements SalvarHistoricoPort {

    private static final String ARQUIVO = "arquivos/historico.csv";

    @Override
    public synchronized void salvar(HistoricoMedicamento historico) {
        ArquivoCsvUtil.escreverLinha(ARQUIVO, montarLinha(historico));
    }

    @Override
    public synchronized List<HistoricoMedicamento> listarTodos(Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
        List<HistoricoMedicamento> resultado = new ArrayList<>();

        for (String linha : ArquivoCsvUtil.lerLinhas(ARQUIVO)) {
            HistoricoMedicamento historico = montarObjeto(linha, idosos, medicamentos);
            if (historico != null) {
                resultado.add(historico);
            }
        }

        return resultado;
    }

    @Override
    public synchronized void atualizar(HistoricoMedicamento historico, Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
        List<HistoricoMedicamento> todos = listarTodos(idosos, medicamentos);
        List<HistoricoMedicamento> atualizados = new ArrayList<>();

        for (HistoricoMedicamento h : todos) {
            atualizados.add(h.getId() == historico.getId() ? historico : h);
        }

        reescreverArquivo(atualizados);
    }

    @Override
    public synchronized void excluir(int id) {
        List<String> restantes = new ArrayList<>();

        for (String linha : ArquivoCsvUtil.lerLinhas(ARQUIVO)) {
            try {
                int idLinha = Integer.parseInt(linha.split(";")[0]);
                if (idLinha != id) {
                    restantes.add(linha);
                }
            } catch (RuntimeException e) {
                throw new ArquivoCsvCorrompidoException(ARQUIVO, linha, e);
            }
        }

        ArquivoCsvUtil.reescreverLinhas(ARQUIVO, restantes);
    }

    @Override
    public synchronized List<HistoricoMedicamento> listarHistoricoPorIdoso(int idIdoso, Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
        List<HistoricoMedicamento> resultado = new ArrayList<>();

        for (String linha : ArquivoCsvUtil.lerLinhas(ARQUIVO)) {
            HistoricoMedicamento historico = montarObjeto(linha, idosos, medicamentos);
            if (historico != null && historico.getIdoso().getId() == idIdoso) {
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
        List<String> linhas = new ArrayList<>();
        for (HistoricoMedicamento h : historicos) {
            linhas.add(montarLinha(h));
        }
        ArquivoCsvUtil.reescreverLinhas(ARQUIVO, linhas);
    }

    private String montarLinha(HistoricoMedicamento historico) {
        return historico.getId() + ";" +
                historico.getIdoso().getId() + ";" +
                historico.getMedicamento().getId() + ";" +
                historico.getDataHoraTomada() + ";" +
                historico.isFoiTomado();
    }
}