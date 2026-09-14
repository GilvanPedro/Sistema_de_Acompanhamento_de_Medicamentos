package br.com.domain.validation;

import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.TipoMedicamento;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class ValidarDadosMedicamento {
    public void validarMedicamento(String nome, DayOfWeek diaSemana, LocalTime horarioMedicamento, TipoMedicamento tipoMedicamento){
        if (nome == null || nome.trim().isEmpty()) {
            throw new DadosInvalidosException("O nome do medicamento é obrigatório e não pode ser vazio.");
        }

        if (diaSemana == null) {
            throw new DadosInvalidosException("O dia da semana é obrigatório.");
        }

        if (horarioMedicamento == null) {
            throw new DadosInvalidosException("O horário do medicamento é obrigatório.");
        }

        if (tipoMedicamento == null) {
            throw new DadosInvalidosException("O tipo do medicamento é obrigatório.");
        }
    }
}