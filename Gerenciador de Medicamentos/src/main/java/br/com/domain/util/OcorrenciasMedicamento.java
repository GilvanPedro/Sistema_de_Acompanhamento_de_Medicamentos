package br.com.domain.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import br.com.domain.model.Medicamento;

/**
 * Regras de tempo compartilhadas pelos avisos de remédio: quais horários previstos ainda importam agora e se uma tomada
 * cobre um deles. Considera a virada da meia-noite: um remédio das 23h50 ainda conta como atrasado às 00h05 do dia seguinte.
 */
public final class OcorrenciasMedicamento {

    /** Até quantos minutos depois do horário ainda é um "lembrete"; passou disso, o remédio foi esquecido. */
    public static final int TOLERANCIA_MINUTOS = 10;

    /** Depois da meia-noite, o horário previsto de ontem ainda importa por este tempo. */
    public static final Duration PRAZO_APOS_MEIA_NOITE = Duration.ofHours(3);

    private OcorrenciasMedicamento() {
    }

    /** Os horários previstos, já passados, que ainda importam agora: o de hoje e, logo após a meia-noite, o de ontem. */
    public static List<LocalDateTime> relevantes(Medicamento medicamento, LocalDateTime agora) {
        List<LocalDateTime> resultado = new ArrayList<>();
        for (int diasAtras = 1; diasAtras >= 0; diasAtras--) {
            LocalDate dia = agora.toLocalDate().minusDays(diasAtras);
            if (medicamento.getDiaSemana() != dia.getDayOfWeek()) {
                continue;
            }
            LocalDateTime previsto = LocalDateTime.of(dia, medicamento.getHorarioMedicamento());
            if (previsto.isAfter(agora)) {
                continue;
            }
            if (diasAtras == 1 && Duration.between(previsto, agora).compareTo(PRAZO_APOS_MEIA_NOITE) > 0) {
                continue;
            }
            resultado.add(previsto);
        }
        return resultado;
    }

    /**
     * A tomada cobre o horário previsto se foi no mesmo dia dele ou, para quem toma depois da meia-noite, logo depois
     * do horário e dentro do prazo.
     */
    public static boolean tomadaCobre(LocalDateTime tomada, LocalDateTime previsto) {
        if (tomada.toLocalDate().equals(previsto.toLocalDate())) {
            return true;
        }
        return !tomada.isBefore(previsto) && !tomada.isAfter(previsto.plus(PRAZO_APOS_MEIA_NOITE));
    }

    public static long minutosDeAtraso(LocalDateTime previsto, LocalDateTime agora) {
        return Duration.between(previsto, agora).toMinutes();
    }
}
