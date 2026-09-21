package br.com.adapter.in.web.recuperacao;

import java.util.logging.Logger;

/** Sem provedor de e-mail configurado: não envia nada (e não escreve o e-mail nem o link no log, que contêm o segredo). */
public class EnviadorDeEmailDesligado implements EnviadorDeEmail {

    private static final Logger LOG = Logger.getLogger(EnviadorDeEmailDesligado.class.getName());

    @Override
    public void enviar(String para, String assunto, String texto) {
        LOG.warning("E-mail NÃO enviado (envio desligado: defina EMAIL_BREVO_CHAVE e EMAIL_REMETENTE): " + assunto);
    }

    @Override
    public boolean ligado() {
        return false;
    }
}
