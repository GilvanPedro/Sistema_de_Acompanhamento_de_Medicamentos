package br.com.domain.validation;

import br.com.domain.model.TipoMedicamento;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class ValidarDadosMedicamento {
    public void validarMedicamento(String nome, DayOfWeek diaSemana, LocalTime horarioMedicamento, TipoMedicamento tipoMedicamento){
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome do medicamento é obrigatório e não pode ser vazio.");
        }

        if (diaSemana == null) {
            throw new IllegalArgumentException("O dia da semana é obrigatório.");
        }

        if (horarioMedicamento == null) {
            throw new IllegalArgumentException("O horário do medicamento é obrigatório.");
        }

        if (tipoMedicamento == null) {
            throw new IllegalArgumentException("O tipo do medicamento é obrigatório.");
        }
    }
}
