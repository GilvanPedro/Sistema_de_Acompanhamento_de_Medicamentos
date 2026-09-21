package br.com.domain.port.in;

import br.com.domain.model.HistoricoMedicamento;

import java.time.LocalDate;
import java.util.List;

public interface BuscarHistoricoPorIdosoCase {
    List<HistoricoMedicamento> buscarHistoricoDoIdoso(int idosoId);

    /**
     * As tomadas registradas mais os horários previstos que passaram sem tomada ("não tomou"), do mais antigo para o
     * mais novo, entre os dias dados (inclusive; null = sem limite daquele lado, com no máximo um ano de faltas).
     */
    List<HistoricoMedicamento> buscarHistoricoCompleto(int idosoId, LocalDate de, LocalDate ate);
}
