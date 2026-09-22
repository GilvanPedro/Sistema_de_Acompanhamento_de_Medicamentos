package br.com.adapter.in.web.recuperacao;

import static br.com.adapter.in.web.paginas.PaginaPublica.esc;
import static br.com.adapter.in.web.paginas.PaginaPublica.montar;

/** As páginas em que a pessoa escolhe a nova senha (abrem pelo link do e-mail, no navegador do celular ou do computador). */
public final class PaginasDeRecuperacao {

    private PaginasDeRecuperacao() {
    }

    public static String formulario(String token, String erro) {
        return montar("Escolha uma nova senha",
                "<h1>Escolha uma nova senha</h1>"
                        + (erro == null ? "" : "<p class=\"erro\" role=\"alert\">" + esc(erro) + "</p>")
                        + "<form method=\"post\" action=\"/redefinir-senha\">"
                        + "<input type=\"hidden\" name=\"token\" value=\"" + esc(token) + "\">"
                        + "<label for=\"senha\">Nova senha (pelo menos 8 caracteres)</label>"
                        + "<input id=\"senha\" name=\"senha\" type=\"password\" autocomplete=\"new-password\" minlength=\"8\" required autofocus>"
                        + "<label for=\"confirmar\">Escreva a nova senha de novo</label>"
                        + "<input id=\"confirmar\" name=\"confirmar\" type=\"password\" autocomplete=\"new-password\" minlength=\"8\" required>"
                        + "<button type=\"submit\">Salvar a nova senha</button>"
                        + "</form>");
    }

    public static String linkInvalido(String motivo) {
        return montar("Link inválido",
                "<h1>Este link não vale mais</h1><p class=\"erro\">" + esc(motivo) + "</p>"
                        + "<p>Abra o aplicativo CuidaMed, toque em <b>Entrar</b> e depois em <b>Esqueci minha senha</b> para receber um link novo.</p>");
    }

    public static String pronto() {
        return montar("Senha alterada",
                "<h1>Senha alterada!</h1><p class=\"ok\">Pronto. Agora volte ao aplicativo CuidaMed e entre com a nova senha.</p>"
                        + "<p>Por segurança, você foi desconectado dos outros aparelhos.</p>");
    }
}
