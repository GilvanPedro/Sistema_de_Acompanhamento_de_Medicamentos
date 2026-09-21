package br.com.domain.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;

/**
 * Descobre os horários em que o remédio deveria ter sido tomado e não foi. A falta não é guardada: sai da conta
 * "horário previsto sem tomada que o cubra", feita quando o histórico é pedido. Assim ela some sozinha se a tomada
 * chegar depois (o celular pode ficar dias sem internet) e nunca fica desatualizada.
 *
 * <p>Um horário só vira falta depois de fechado: acabou o dia dele e também o prazo da madrugada
 * ({@link OcorrenciasMedicamento#PRAZO_APOS_MEIA_NOITE}). E só valem os horários a partir de
 * {@link Medicamento#getVigenteDesde()}, para um remédio recém-cadastrado não ter faltas nas semanas anteriores.
 */
public final class FaltasDeMedicamento {

    /** Até onde para trás olhamos por padrão. */
    public static final int DIAS_PARA_TRAS = 365;

    private FaltasDeMedicamento() {
    }

    /**
     * @param registros as tomadas já registradas do idoso (as faltas só são criadas onde nenhuma delas cobre o horário)
     * @param de        primeiro dia considerado (null = {@value #DIAS_PARA_TRAS} dias atrás)
     * @return entradas com {@code foiTomado = false}, no horário previsto, e id negativo (não existem no banco)
     */
    public static List<HistoricoMedicamento> calcular(Idoso idoso, List<Medicamento> medicamentos,
                                                      List<HistoricoMedicamento> registros, LocalDateTime agora, LocalDate de) {
        LocalDate inicio = de != null ? de : agora.toLocalDate().minusDays(DIAS_PARA_TRAS);
        List<HistoricoMedicamento> faltas = new ArrayList<>();
        for (Medicamento medicamento : medicamentos) {
            LocalDateTime vigente = medicamento.getVigenteDesde();
            if (vigente == null) {
                continue;
            }
            LocalDate primeiroDia = vigente.toLocalDate().isAfter(inicio) ? vigente.toLocalDate() : inicio;
            for (LocalDate dia = primeiroDia; !dia.isAfter(agora.toLocalDate()); dia = dia.plusDays(1)) {
                if (dia.getDayOfWeek() != medicamento.getDiaSemana()) {
                    continue;
                }
                LocalDateTime previsto = LocalDateTime.of(dia, medicamento.getHorarioMedicamento());
                if (previsto.isBefore(vigente) || !fechado(previsto, agora) || foiTomado(medicamento, previsto, registros)) {
                    continue;
                }
                faltas.add(new HistoricoMedicamento(idDaFalta(medicamento, dia), medicamento, idoso, previsto, false));
            }
        }
        return faltas;
    }

    /** O horário só vira falta quando o dia dele acabou e passou o prazo depois da meia-noite. */
    static boolean fechado(LocalDateTime previsto, LocalDateTime agora) {
        LocalDateTime fim = previsto.toLocalDate().plusDays(1).atStartOfDay().plus(OcorrenciasMedicamento.PRAZO_APOS_MEIA_NOITE);
        return agora.isAfter(fim);
    }

    private static boolean foiTomado(Medicamento medicamento, LocalDateTime previsto, List<HistoricoMedicamento> registros) {
        return registros.stream().anyMatch(h -> h.getMedicamento().getId() == medicamento.getId()
                && h.isFoiTomado() && OcorrenciasMedicamento.tomadaCobre(h.getDataHoraTomada(), previsto));
    }

    /** Id negativo e estável (mesmo remédio e dia dão o mesmo id), para nunca colidir com os do banco. */
    private static int idDaFalta(Medicamento medicamento, LocalDate dia) {
        return -(int) ((medicamento.getId() * 100_000L + dia.toEpochDay()) % 2_000_000_000L) - 1;
    }
}
