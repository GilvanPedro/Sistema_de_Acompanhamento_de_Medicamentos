package br.com.adapter.in.gui.api;

/** Uma pessoa como a API a mostra (nunca com senha). */
public record Conta(int id, String tipo, String nome, String email) {
    public boolean ehIdoso() {
        return "IDOSO".equals(tipo);
    }
}
