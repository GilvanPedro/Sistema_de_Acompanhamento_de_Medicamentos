package br.com.domain.util;

import br.com.domain.exception.DadosInvalidosException;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.util.Map;

public class ConverterDiaSemanaUtil {

    private static final Map<String, DayOfWeek> DIAS_DA_SEMANA = Map.of(
            "segunda", DayOfWeek.MONDAY,
            "terca", DayOfWeek.TUESDAY,
            "quarta", DayOfWeek.WEDNESDAY,
            "quinta", DayOfWeek.THURSDAY,
            "sexta", DayOfWeek.FRIDAY,
            "sabado", DayOfWeek.SATURDAY,
            "domingo", DayOfWeek.SUNDAY
    );

    private ConverterDiaSemanaUtil() {
    }

    /**
     Converte um dia da semana digitado em português para {@link DayOfWeek}.
     Aceita as 3 formas: "sexta", "sexta feira" e "sexta-feira" (com ou sem acento,maiúsculas/minúsculas), valendo para todos os dias da semana.
     */
    public static DayOfWeek converter(String textoDigitado) {
        if (textoDigitado == null || textoDigitado.isBlank()) {
            throw new DadosInvalidosException("O dia da semana é obrigatório.");
        }

        String normalizado = normalizar(textoDigitado);
        DayOfWeek dia = DIAS_DA_SEMANA.get(normalizado);

        if (dia == null) {
            throw new DadosInvalidosException(
                    "Dia da semana inválido: \"" + textoDigitado.trim() + "\". " +
                            "Use, por exemplo, \"sexta\", \"sexta feira\" ou \"sexta-feira\"."
            );
        }

        return dia;
    }

    private static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        String semHifen = semAcento.replace("-", " ").trim().replaceAll("\\s+", " ");

        if (semHifen.endsWith(" feira")) {
            semHifen = semHifen.substring(0, semHifen.length() - " feira".length()).trim();
        }

        return semHifen;
    }
}