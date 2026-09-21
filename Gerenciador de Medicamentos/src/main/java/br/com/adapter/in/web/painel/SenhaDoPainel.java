package br.com.adapter.in.web.painel;

/** Senha do painel de anúncios (variável PAINEL_SENHA). Sem ela (ou curta demais), o painel fica desligado (404). */
public record SenhaDoPainel(String valor) {
    public boolean configurada() {
        return valor != null && valor.length() >= 12;
    }
}
