package br.com.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.adapter.in.web.recuperacao.EnviadorDeEmail;

/** O "enviador de e-mail" dos testes: guarda o que seria enviado. */
class EmailsEnviados implements EnviadorDeEmail {

    record Email(String para, String assunto, String texto) { }

    // Serve tanto para o link de "esqueci minha senha" (/redefinir-senha?token=...) quanto para o de
    // "excluir minha conta" (/excluir-conta?token=...): o teste olha para o parâmetro, não para o caminho.
    private static final Pattern LINK = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");
    private final List<Email> enviados = new ArrayList<>();

    @Override
    public synchronized void enviar(String para, String assunto, String texto) {
        enviados.add(new Email(para, assunto, texto));
    }

    synchronized List<Email> todos() {
        return List.copyOf(enviados);
    }

    synchronized List<Email> para(String endereco) {
        return enviados.stream().filter(e -> e.para().equalsIgnoreCase(endereco)).toList();
    }

    /** O token do último link enviado para esse endereço. */
    synchronized String ultimoToken(String endereco) {
        List<Email> lista = para(endereco);
        for (int i = lista.size() - 1; i >= 0; i--) {
            Matcher m = LINK.matcher(lista.get(i).texto());
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new AssertionError("Nenhum e-mail com link (token=...) foi enviado para " + endereco);
    }
}
