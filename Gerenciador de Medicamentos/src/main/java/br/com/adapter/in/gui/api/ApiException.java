package br.com.adapter.in.gui.api;

/** Erro devolvido pela API (a mensagem já vem em português, para mostrar à pessoa) ou falha de conexão (status 0). */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public int status() {
        return status;
    }

    /** Sem internet, servidor fora do ar ou demorando demais. */
    public boolean semConexao() {
        return status == 0;
    }

    /** O login não vale mais (nem renovando): é preciso entrar de novo. */
    public boolean sessaoPerdida() {
        return status == 401;
    }
}
