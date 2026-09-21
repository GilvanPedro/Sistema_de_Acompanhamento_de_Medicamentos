package br.com.adapter.in.web.recuperacao;

/** As páginas em que a pessoa escolhe a nova senha (abrem pelo link do e-mail, no navegador do celular ou do computador). */
public final class PaginasDeRecuperacao {

    private PaginasDeRecuperacao() {
    }

    public static String formulario(String token, String erro) {
        return pagina("Escolha uma nova senha",
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
        return pagina("Link inválido",
                "<h1>Este link não vale mais</h1><p class=\"erro\">" + esc(motivo) + "</p>"
                        + "<p>Abra o aplicativo CuidaMed, toque em <b>Entrar</b> e depois em <b>Esqueci minha senha</b> para receber um link novo.</p>");
    }

    public static String pronto() {
        return pagina("Senha alterada",
                "<h1>Senha alterada!</h1><p class=\"ok\">Pronto. Agora volte ao aplicativo CuidaMed e entre com a nova senha.</p>"
                        + "<p>Por segurança, você foi desconectado dos outros aparelhos.</p>");
    }

    private static String pagina(String titulo, String corpo) {
        return "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<meta name=\"robots\" content=\"noindex\">"
                + "<title>" + esc(titulo) + " - CuidaMed</title><style>"
                + ":root{--fundo:#f5f7fb;--cartao:#fff;--texto:#14213d;--suave:#4a5568;--borda:#c9cfdc;--azul:#0b4fc4;--verde:#1b7a3a;--vermelho:#b42318}"
                + "@media (prefers-color-scheme:dark){:root{--fundo:#0f1623;--cartao:#182234;--texto:#eef2fa;--suave:#b6c0d4;--borda:#34425c;--azul:#8ab4ff;--verde:#6fd58c;--vermelho:#ff8a80}}"
                + "body{margin:0;background:var(--fundo);color:var(--texto);font:19px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}"
                + "main{max-width:32rem;margin:0 auto;padding:1.2rem 16px 3rem}"
                + ".marca{font-weight:700;color:var(--azul);margin:.6rem 0}"
                + ".cartao{background:var(--cartao);border:1px solid var(--borda);border-radius:1rem;padding:1.2rem}"
                + "h1{font-size:1.5rem;margin:.2rem 0 1rem}"
                + "label{display:block;font-weight:600;margin:1rem 0 .3rem}"
                + "input[type=password]{width:100%;box-sizing:border-box;font:inherit;padding:.8rem;border:2px solid var(--borda);border-radius:.6rem;background:var(--fundo);color:var(--texto)}"
                + "input:focus{outline:3px solid var(--azul);outline-offset:1px}"
                + "button{margin-top:1.4rem;width:100%;font:inherit;font-weight:700;padding:.9rem;border:0;border-radius:.8rem;background:var(--azul);color:#fff;cursor:pointer}"
                + "@media (prefers-color-scheme:dark){button{color:#0b1a33}}"
                + ".erro{color:var(--vermelho);font-weight:600}.ok{color:var(--verde);font-weight:600}"
                + "</style></head><body><main><p class=\"marca\">CuidaMed</p><div class=\"cartao\">" + corpo + "</div></main></body></html>";
    }

    private static String esc(String texto) {
        return texto == null ? "" : texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
