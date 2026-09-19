package br.com.adapter.in.web.erro;

public class MuitasTentativasException extends RuntimeException {
    public MuitasTentativasException() {
        super("Muitas tentativas. Aguarde alguns minutos e tente de novo.");
    }
}
