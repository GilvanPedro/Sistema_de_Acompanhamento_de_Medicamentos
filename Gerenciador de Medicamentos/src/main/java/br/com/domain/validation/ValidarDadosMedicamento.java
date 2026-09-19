package br.com.domain.validation;

import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.TipoMedicamento;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class ValidarDadosMedicamento {

    public static final int NOME_MAXIMO = 150;

    public void validarMedicamento(String nome, DayOfWeek diaSemana, LocalTime horarioMedicamento, TipoMedicamento tipoMedicamento) {
        validarNome(nome);
        validarDiaSemana(diaSemana);
        validarHorario(horarioMedicamento);
        validarTipo(tipoMedicamento);
    }

    public void validarNome(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new DadosInvalidosException("O nome do medicamento é obrigatório e não pode ser vazio.");
        }
        if (nome.trim().length() > NOME_MAXIMO) {
            throw new DadosInvalidosException("O nome do medicamento pode ter no máximo " + NOME_MAXIMO + " caracteres.");
        }
    }

    public void validarDiaSemana(DayOfWeek diaSemana) {
        if (diaSemana == null) {
            throw new DadosInvalidosException("O dia da semana é obrigatório.");
        }
    }

    public void validarHorario(LocalTime horarioMedicamento) {
        if (horarioMedicamento == null) {
            throw new DadosInvalidosException("O horário do medicamento é obrigatório.");
        }
    }

    public void validarTipo(TipoMedicamento tipoMedicamento) {
        if (tipoMedicamento == null) {
            throw new DadosInvalidosException("O tipo do medicamento é obrigatório.");
        }
    }
}