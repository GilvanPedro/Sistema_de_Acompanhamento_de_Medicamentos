package br.com.adapter.in.web.paginas;

/**
 * O visual comum das páginas públicas simples do servidor (nova senha, exclusão de conta): sem depender do app,
 * abrem em qualquer navegador. Mesmas cores e variáveis de sempre (claro/escuro), num cartão só.
 */
public final class PaginaPublica {

    private PaginaPublica() {
    }

    public static String montar(String titulo, String corpo) {
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
                + "p{color:var(--texto)}.suave{color:var(--suave)}"
                + "label{display:block;font-weight:600;margin:1rem 0 .3rem}"
                + "input[type=password],input[type=email]{width:100%;box-sizing:border-box;font:inherit;padding:.8rem;border:2px solid var(--borda);border-radius:.6rem;background:var(--fundo);color:var(--texto)}"
                + "input:focus{outline:3px solid var(--azul);outline-offset:1px}"
                + "button{margin-top:1.4rem;width:100%;font:inherit;font-weight:700;padding:.9rem;border:0;border-radius:.8rem;background:var(--azul);color:#fff;cursor:pointer}"
                + "button.perigo{background:var(--vermelho)}"
                + "@media (prefers-color-scheme:dark){button{color:#0b1a33}button.perigo{color:#3a0a06}}"
                + "a{color:var(--azul)}"
                + ".erro{color:var(--vermelho);font-weight:600}.ok{color:var(--verde);font-weight:600}"
                + "</style></head><body><main><p class=\"marca\">CuidaMed</p><div class=\"cartao\">" + corpo + "</div></main></body></html>";
    }

    public static String esc(String texto) {
        return texto == null ? "" : texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
