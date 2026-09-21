package br.com.adapter.in.web.painel;

import static br.com.adapter.in.web.painel.PaginasDoPainel.esc;

import java.util.List;
import java.util.Locale;

import br.com.adapter.in.web.metricas.BancoDeBanners.Registro;

/** As páginas de gerenciar banners (lista, cadastro e edição). Tudo que vem de fora é escapado. */
final class PaginasDeBanners {

    /** O que a pessoa digitou no formulário (para não perder tudo se algum campo estiver errado). */
    record Preenchido(String empresa, String texto, String link, String peso) {
        static Preenchido vazio() {
            return new Preenchido("", "", "", "1");
        }
    }

    private static final String ESTILO_EXTRA = """
            <style>
            .aviso{padding:.7rem 1rem;border-radius:.6rem;margin:1rem 0}.ok{background:#e3f6e8;color:#0d4a1f}.erro{background:#fde7e7;color:#7a1414}
            @media (prefers-color-scheme:dark){.ok{background:#12351d;color:#b7ecc6}.erro{background:#3a1717;color:#ffc9c9}}
            .formulario{display:grid;gap:.8rem;background:var(--cartao);border:1px solid var(--borda);border-radius:.8rem;padding:1rem;margin:1rem 0}
            .formulario label{display:grid;gap:.25rem;font-weight:600}.formulario small{font-weight:400;color:var(--suave)}
            .formulario input:not([type=file]){font:inherit;padding:.5rem;border:1px solid var(--borda);border-radius:.5rem;background:var(--fundo);color:var(--texto)}
            .miniatura{width:14rem;max-width:100%;border-radius:.4rem;border:1px solid var(--borda)}
            .acoes{display:flex;flex-wrap:wrap;gap:.4rem}.acoes form{margin:0}.perigo{color:#b42318}.pausado{color:var(--suave)}
            .topo-banners{display:flex;justify-content:space-between;align-items:center;gap:1rem;flex-wrap:wrap;margin:.4rem 0 1rem}
            .voltar{display:inline-block;padding:.5rem 0}
            .novo{--texto-do-botao:#fff;background:var(--azul);color:var(--texto-do-botao);border:0;border-radius:.8rem;padding:.75rem 1.4rem;font:inherit;font-weight:700;font-size:1.05rem;
            cursor:pointer;box-shadow:0 3px 10px rgba(11,79,196,.35);transition:transform .08s,box-shadow .08s}
            .novo:hover{transform:translateY(-1px);box-shadow:0 5px 14px rgba(11,79,196,.45)}.novo:active{transform:none}
            .novo:focus-visible{outline:3px solid var(--laranja);outline-offset:2px}
            @media (prefers-color-scheme:dark){.novo{--texto-do-botao:#0b1a33;box-shadow:0 3px 10px rgba(0,0,0,.5)}}
            dialog{border:1px solid var(--borda);border-radius:1rem;background:var(--cartao);color:var(--texto);padding:1.2rem 1.3rem;width:min(42rem,calc(100% - 1.5rem));max-height:92vh;overflow:auto}
            dialog::backdrop{background:rgba(10,15,25,.6)}
            dialog .cabecalho{display:flex;justify-content:space-between;align-items:center;gap:1rem;margin-bottom:.4rem}
            dialog h2{margin:0}.fechar{border-radius:50%;width:2.4rem;height:2.4rem;padding:0;font-size:1.2rem;line-height:1}
            dialog .formulario{border:0;padding:0;margin:.4rem 0 0;background:transparent}
            .acoes-do-formulario{display:flex;gap:.6rem;flex-wrap:wrap}.acoes-do-formulario .principal{background:var(--azul);color:var(--texto-do-botao,#fff);border-color:transparent;font-weight:700}
            </style>""";

    private PaginasDeBanners() {
    }

    static String lista(List<Registro> banners, boolean somenteExemplos, String csrf, String ok, String erro, Preenchido preenchido) {
        StringBuilder h = new StringBuilder(ESTILO_EXTRA);
        h.append("<div class=\"topo-banners\"><a class=\"voltar\" href=\"/painel\">← Voltar ao painel</a>"
                + "<button type=\"button\" class=\"novo\" onclick=\"abrirNovo()\">＋ Adicionar banner</button></div>");
        h.append("<h1>Banners de anúncio</h1>");
        avisos(h, ok, null);
        if (somenteExemplos) {
            h.append("<p class=\"aviso\" style=\"background:var(--cartao);border:1px solid var(--borda)\">Você ainda não cadastrou nenhum banner: o app está mostrando só o banner de exemplo que acompanha o projeto. Clique em <b>Adicionar banner</b>, no topo da página, para cadastrar o primeiro; o exemplo deixa de aparecer.</p>");
        }
        h.append("<h2>Seus banners (").append(banners.size()).append(")</h2>");
        if (banners.isEmpty()) {
            h.append("<p class=\"suave\">Nenhum ainda.</p>");
        } else {
            h.append("<div class=\"tabela\"><table><thead><tr><th>Banner</th><th>Empresa</th><th class=\"n\">Peso</th><th>Situação</th><th class=\"nao-imprimir\">Ações</th></tr></thead><tbody>");
            for (Registro b : banners) {
                boolean pausado = b.peso() <= 0;
                h.append("<tr><td><img class=\"miniatura\" src=\"/anuncios/").append(esc(b.id())).append('.').append(b.extensao()).append("?v=").append(b.versao())
                        .append("\" alt=\"").append(esc(b.texto())).append("\"></td><td><b>").append(esc(b.empresa())).append("</b><br><span class=\"suave pequeno\">")
                        .append(esc(b.texto())).append("</span><br><span class=\"suave pequeno\">")
                        .append(b.link() == null ? "sem link" : esc(b.link())).append("</span>")
                        .append(b.temVideo() ? "<br><span class=\"pequeno\">🎬 Com vídeo (" + segundos(b.duracaoVideoMs()) + ")</span>" : "").append("</td><td class=\"n\">")
                        .append(numero(b.peso())).append("</td><td>").append(pausado ? "<span class=\"pausado\">Pausado</span>" : "Ativo").append("</td><td class=\"nao-imprimir\"><div class=\"acoes\">")
                        .append("<a class=\"botao\" href=\"/painel/banners/").append(esc(b.id())).append("/editar\">Editar</a>")
                        .append(acao(b.id(), pausado ? "ativar" : "pausar", pausado ? "Ativar" : "Pausar", csrf, null))
                        .append(acao(b.id(), "remover", "Remover", csrf, "Remover o banner de " + b.empresa() + "? Não dá para desfazer."))
                        .append("</div></td></tr>");
            }
            h.append("</tbody></table></div>");
        }
        h.append(janelaDeNovoBanner(csrf, erro, preenchido));
        h.append(dicas());
        return PaginasDoPainel.pagina("Banners de anúncio", h.toString());
    }

    static String edicao(Registro b, String csrf, String erro, Preenchido preenchido) {
        StringBuilder h = new StringBuilder(ESTILO_EXTRA);
        h.append("<h1>Editar banner</h1><p class=\"suave\"><a href=\"/painel/banners\">← Voltar aos banners</a></p>");
        avisos(h, null, erro);
        h.append("<img class=\"miniatura\" src=\"/anuncios/").append(esc(b.id())).append('.').append(b.extensao()).append("?v=").append(b.versao())
                .append("\" alt=\"").append(esc(b.texto())).append("\">");
        if (b.temVideo()) {
            h.append("<p class=\"suave\">Vídeo atual (").append(segundos(b.duracaoVideoMs())).append("):</p><video class=\"miniatura\" src=\"/anuncios/").append(esc(b.id()))
                    .append(".mp4?v=").append(b.versao()).append("\" controls muted playsinline preload=\"metadata\"></video>");
        }
        h.append(formulario("/painel/banners/" + b.id(), "Salvar alterações", csrf, preenchido, false, b));
        return PaginasDoPainel.pagina("Editar banner", h.toString());
    }

    /**
     * O formulário de cadastro fica numa janela (elemento {@code <dialog>}) que abre ao clicar em "Adicionar banner".
     * Se o envio anterior deu erro, a página já volta com a janela aberta, mostrando o motivo e o que foi digitado.
     */
    private static String janelaDeNovoBanner(String csrf, String erro, Preenchido preenchido) {
        boolean abrir = erro != null && !erro.isBlank();
        StringBuilder h = new StringBuilder("<dialog id=\"novo-banner\"" + (abrir ? " data-abrir=\"sim\"" : "") + " aria-labelledby=\"titulo-novo\">");
        h.append("<div class=\"cabecalho\"><h2 id=\"titulo-novo\">Adicionar banner</h2>"
                + "<button type=\"button\" class=\"fechar\" aria-label=\"Fechar\" onclick=\"document.getElementById('novo-banner').close()\">✕</button></div>");
        if (abrir) {
            h.append("<p class=\"aviso erro\">").append(esc(erro)).append("</p>");
        }
        h.append(formulario("/painel/banners", "Adicionar banner", csrf, preenchido, true, null));
        h.append("</dialog>");
        h.append("<script>function abrirNovo(){document.getElementById('novo-banner').showModal()}"
                + "(function(){var d=document.getElementById('novo-banner');"
                + "d.addEventListener('click',function(e){if(e.target===d)d.close()});"
                + "if(d.dataset.abrir||location.hash==='#novo')d.showModal()})()</script>");
        return h.toString();
    }

    private static String formulario(String destino, String botao, String csrf, Preenchido p, boolean imagemObrigatoria, Registro atual) {
        return "<form class=\"formulario\" method=\"post\" action=\"" + esc(destino) + "\" enctype=\"multipart/form-data\">"
                + "<input type=\"hidden\" name=\"csrf\" value=\"" + esc(csrf) + "\">"
                + "<label>Empresa<input name=\"empresa\" required maxlength=\"120\" value=\"" + esc(p.empresa()) + "\"></label>"
                + "<label>Descrição do banner <small>(lida por leitores de tela; se ficar em branco, usa o nome da empresa)</small>"
                + "<input name=\"texto\" maxlength=\"200\" value=\"" + esc(p.texto()) + "\"></label>"
                + "<label>Link <small>(opcional: site que começa com https:// ou e-mail no formato mailto:pessoa@exemplo.com)</small>"
                + "<input name=\"link\" maxlength=\"500\" value=\"" + esc(p.link()) + "\" placeholder=\"https://exemplo.com.br\"></label>"
                + "<label>Peso <small>(1 = igual aos outros; 2 = o dobro de exibições; 0 = pausado)</small>"
                + "<input name=\"peso\" inputmode=\"decimal\" value=\"" + esc(p.peso()) + "\"></label>"
                + "<label>Imagem " + (imagemObrigatoria ? "" : "<small>(deixe em branco para manter a atual)</small>")
                + "<input type=\"file\" name=\"imagem\" accept=\"image/png,image/jpeg\"" + (imagemObrigatoria ? " required" : "") + "></label>"
                + "<label>Vídeo curto <small>(opcional: MP4 com H.264, de 1 a 15 segundos e até 4 MB, no mesmo formato da imagem"
                + (atual != null && atual.temVideo() ? "; deixe em branco para manter o atual" : "") + ". Toca sem som, em repetição)</small>"
                + "<input type=\"file\" name=\"video\" accept=\"video/mp4\"></label>"
                + (atual != null && atual.temVideo() ? "<label style=\"display:flex;gap:.5rem;align-items:center;font-weight:400\"><input type=\"checkbox\" name=\"removerVideo\" value=\"on\"> Remover o vídeo atual (o banner volta a ser só imagem)</label>" : "")
                + "<div class=\"acoes-do-formulario\"><button type=\"submit\" class=\"principal\">" + esc(botao) + "</button>"
                + (imagemObrigatoria ? "<button type=\"button\" onclick=\"document.getElementById('novo-banner').close()\">Cancelar</button>"
                        : "<a class=\"botao\" href=\"/painel/banners\">Cancelar</a>")
                + "</div></form>";
    }

    private static String acao(String id, String acao, String texto, String csrf, String confirmacao) {
        return "<form method=\"post\" action=\"/painel/banners/" + esc(id) + "/" + acao + "\""
                + (confirmacao == null ? "" : " onsubmit=\"return confirm('" + esc(confirmacao).replace("&#39;", "\\'") + "')\"")
                + "><input type=\"hidden\" name=\"csrf\" value=\"" + esc(csrf) + "\"><button type=\"submit\""
                + ("remover".equals(acao) ? " class=\"perigo\"" : "") + ">" + esc(texto) + "</button></form>";
    }

    private static void avisos(StringBuilder h, String ok, String erro) {
        if (ok != null && !ok.isBlank()) {
            h.append("<p class=\"aviso ok\">").append(esc(ok)).append("</p>");
        }
        if (erro != null && !erro.isBlank()) {
            h.append("<p class=\"aviso erro\">").append(esc(erro)).append("</p>");
        }
    }

    private static String dicas() {
        return "<h2>Sobre as imagens</h2><ul class=\"suave\"><li>PNG ou JPEG, até <b>1 MB</b> (o ideal é até uns 300 KB).</li>"
                + "<li>Formato de banner: a largura de 2 a 6 vezes a altura. O ideal é <b>1280×400</b> (3,2 para 1).</li>"
                + "<li>Entre 300 e 4000 pixels de largura. Não aceitamos GIF, SVG nem outros formatos.</li>"
                + "<li>Para trocar a imagem de um banner, use <b>Editar</b> e escolha o arquivo novo.</li></ul>"
                + "<h2>Sobre o vídeo (opcional)</h2><ul class=\"suave\"><li><b>MP4 com H.264</b>, de <b>1 a 15 segundos</b> e até <b>4 MB</b> (o ideal é uns 2 MB). No máximo 15 banners com vídeo.</li>"
                + "<li>Mesmo formato da imagem (por exemplo, os dois em 1280×400). A <b>imagem continua obrigatória</b>: ela aparece enquanto o vídeo carrega, na internet do celular (dados móveis) e para quem desligou as animações.</li>"
                + "<li>O vídeo toca <b>sem som</b> e em repetição, só enquanto o banner está na tela.</li>"
                + "<li>Para deixar o arquivo leve e pronto para a internet, com o <i>ffmpeg</i>: <code>ffmpeg -i original.mp4 -vf scale=1280:400 -c:v libx264 -crf 30 -an -movflags +faststart banner.mp4</code></li></ul>";
    }

    private static String segundos(int ms) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f s", ms / 1000.0);
    }

    private static String numero(double n) {
        return n == Math.rint(n) ? String.valueOf((long) n) : String.format(Locale.forLanguageTag("pt-BR"), "%.2f", n);
    }
}
