package br.com.adapter.in.web.recuperacao;

/** Manda um e-mail de texto simples. Quem chama não espera pelo resultado: falha de envio não pode mudar a resposta da API. */
public interface EnviadorDeEmail {

    void enviar(String para, String assunto, String texto);

    /** Se o envio está ligado (só para o log de inicialização). */
    default boolean ligado() {
        return true;
    }
}
