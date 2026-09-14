package br.com.domain.port.in;

import br.com.domain.model.HistoricoMedicamento;

import java.util.List;

public interface BuscarHistoricoPorIdosoCase {
    List<HistoricoMedicamento> buscarHistoricoDoIdoso(int idosoId);
}
