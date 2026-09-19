package br.com.adapter.in.web.push;

/** Usado quando o Firebase não está configurado (desenvolvimento, terminal, GUI): não faz nada. */
public class NotificadorPushDesligado implements NotificadorPush {

    @Override
    public void avisarNovidade(int usuarioId) {
    }
}
