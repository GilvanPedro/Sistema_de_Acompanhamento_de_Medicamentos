package br.com.adapter.in.web.push;

/**
 * Avisa os aparelhos de uma conta que há novidade. O push é só um "acorde e sincronize": não leva nome, remédio nem
 * qualquer dado de saúde (o Google não vê nada), o app é quem busca e mostra o conteúdo.
 */
public interface NotificadorPush {

    /** Melhor esforço: nunca lança erro nem atrasa quem chama. */
    void avisarNovidade(int usuarioId);
}
