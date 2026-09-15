package br.com.domain.exception;

public class MedicamentoNaoEncontradoException extends RuntimeException {
    public MedicamentoNaoEncontradoException(int id) {
        super("Medicamento com id " + id + " não encontrado.");
    }
}