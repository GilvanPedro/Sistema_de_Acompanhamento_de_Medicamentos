package br.com.adapter.in.web.push;

/** Segredo que o agendador externo envia em X-Cron-Segredo. Sem ele definido (null), a rota fica desligada. */
public record SegredoDoAgendador(String valor) {
    public boolean configurado() {
        return valor != null && valor.length() >= 16;
    }
}
