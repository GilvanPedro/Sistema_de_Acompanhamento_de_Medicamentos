package br.com.adapter.in.web.erro;

public class NaoAutenticadoException extends RuntimeException {
    public NaoAutenticadoException() {
        super("Entre novamente para continuar.");
    }
}
