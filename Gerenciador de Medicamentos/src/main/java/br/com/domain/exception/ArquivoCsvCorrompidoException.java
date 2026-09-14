package br.com.domain.exception;

public class ArquivoCsvCorrompidoException extends RuntimeException {
    public ArquivoCsvCorrompidoException(String arquivo, String linha, Throwable causa) {
        super("Linha corrompida no arquivo " + arquivo + ": \"" + linha + "\"", causa);
    }
}
