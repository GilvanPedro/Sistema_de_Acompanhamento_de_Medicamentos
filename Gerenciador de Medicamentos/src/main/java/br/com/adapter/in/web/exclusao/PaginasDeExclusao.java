package br.com.adapter.in.web.exclusao;

import static br.com.adapter.in.web.paginas.PaginaPublica.esc;
import static br.com.adapter.in.web.paginas.PaginaPublica.montar;

/** As páginas públicas de "excluir minha conta": pedir o link, confirmar (com aviso bem claro) e o resultado. */
public final class PaginasDeExclusao {

    private PaginasDeExclusao() {
    }

    public static String formularioDeEmail(String erro) {
        return montar("Excluir minha conta",
                "<h1>Excluir a minha conta</h1>"
                        + "<p class=\"suave\">Digite o e-mail da sua conta no CuidaMed. Vamos mandar um link para você confirmar a exclusão.</p>"
                        + (erro == null ? "" : "<p class=\"erro\" role=\"alert\">" + esc(erro) + "</p>")
                        + "<form method=\"post\" action=\"/excluir-conta/solicitar\">"
                        + "<label for=\"email\">Seu e-mail</label>"
                        + "<input id=\"email\" name=\"email\" type=\"email\" autocomplete=\"email\" required autofocus>"
                        + "<button type=\"submit\" class=\"perigo\">Pedir o link para excluir</button>"
                        + "</form>");
    }

    public static String pedidoEnviado() {
        return montar("Pedido enviado",
                "<h1>Pedido enviado</h1>"
                        + "<p class=\"ok\">Se esse e-mail tiver uma conta, mandamos um link para confirmar a exclusão. Ele vale por 30 minutos.</p>"
                        + "<p class=\"suave\">Olhe também a caixa de spam. Se você não pediu isso, é só ignorar: nada muda na sua conta.</p>");
    }

    public static String confirmar(String token, String nome, String email, String erro) {
        return montar("Confirmar exclusão",
                "<h1>Excluir a conta de " + esc(nome) + "?</h1>"
                        + "<p class=\"suave\">Conta: " + esc(email) + "</p>"
                        + (erro == null ? "" : "<p class=\"erro\" role=\"alert\">" + esc(erro) + "</p>")
                        + "<p><b>Isso é irreversível.</b> Vamos apagar, para sempre: os remédios cadastrados, todo o histórico de tomadas, "
                        + "os vínculos com familiares ou idosos e os avisos configurados. O nome e o e-mail deixam de ficar associados à conta.</p>"
                        + "<form method=\"post\" action=\"/excluir-conta\">"
                        + "<input type=\"hidden\" name=\"token\" value=\"" + esc(token) + "\">"
                        + "<button type=\"submit\" class=\"perigo\">Sim, excluir minha conta e meus dados</button>"
                        + "</form>"
                        + "<p class=\"suave\">Mudou de ideia? É só fechar esta página: nada é apagado até você confirmar acima.</p>");
    }

    public static String linkInvalido(String motivo) {
        return montar("Link inválido",
                "<h1>Este link não vale mais</h1><p class=\"erro\">" + esc(motivo) + "</p>"
                        + "<p>Peça um novo em <a href=\"/excluir-conta\">Excluir minha conta</a>.</p>");
    }

    public static String pronto(String nome) {
        return montar("Conta excluída",
                "<h1>Pronto, " + esc(nome) + "</h1>"
                        + "<p class=\"ok\">A sua conta e os seus dados foram excluídos do CuidaMed.</p>"
                        + "<p class=\"suave\">Mandamos um e-mail confirmando. Se um dia quiser voltar, é só criar uma conta nova no aplicativo.</p>");
    }
}
