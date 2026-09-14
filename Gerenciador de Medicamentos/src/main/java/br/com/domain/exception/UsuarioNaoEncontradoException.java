package br.com.domain.exception;

public class UsuarioNaoEncontradoException extends RuntimeException {
    public UsuarioNaoEncontradoException(int id) {
        super("Usuário com id " + id + " não encontrado.");
    }
}