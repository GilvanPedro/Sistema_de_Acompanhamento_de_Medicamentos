package br.com.domain.exception;

public class ErroBancoDadosException extends RuntimeException {
    public ErroBancoDadosException(String operacao, Throwable causa) {
        super("Erro ao acessar o banco de dados (" + operacao + ")", causa);
    }
}
