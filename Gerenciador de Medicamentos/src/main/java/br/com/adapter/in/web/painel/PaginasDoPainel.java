package br.com.adapter.in.web.painel;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import br.com.adapter.in.web.metricas.CatalogoDeBanners;
import br.com.adapter.in.web.metricas.CatalogoDeBanners.Banner;
import br.com.adapter.in.web.painel.ResumoDoMes.Totais;

/** Monta o HTML dos relatórios (sem bibliotecas: texto, tabelas e gráficos em SVG). Tudo que vem de fora é escapado. */
final class PaginasDoPainel {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final Map<String, String> PERFIS = Map.of(
            "IDOSO", "Idosos", "FAMILIAR", "Familiares", "VISITANTE", "Visitantes (sem login)");
    private static final Map<String, String> POSICOES = Map.of(
            "entrada-fim", "Telas de entrada (início, login e cadastro)",
            "home-topo", "Tela inicial: topo",
            "home-fim", "Tela inicial: fim",
            "idoso-fim", "Tela de um idoso (para o familiar)",
            "remedios-fim", "Lista de remédios",
            "perfil-topo", "Meus dados: topo",
            "vinculos-fim", "Vincular idoso ou familiar");

    private PaginasDoPainel() {
    }

    static String esc(String texto) {
        return texto == null ? "" : texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String numero(long n) {
        return NumberFormat.getIntegerInstance(PT_BR).format(n);
    }

    static String porcento(double p) {
        return String.format(PT_BR, "%.2f%%", p);
    }

    static String nomeDoMes(YearMonth mes) {
        return mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR) + " de " + mes.getYear();
    }

    // ------------------------------------------------------------------ páginas

    /** Relatório de todos os banners (só para o dono do app). */
    static String geral(ResumoDoMes r, CatalogoDeBanners catalogo, Function<Banner, String> linkParaEmpresa) {
        StringBuilder h = new StringBuilder();
        h.append("<h1>Painel de anúncios</h1><p class=\"suave\">Relatório geral de ").append(esc(nomeDoMes(r.mes))).append("</p>");
        h.append("<p class=\"nao-imprimir\"><a class=\"botao\" href=\"/painel/banners\">Gerenciar banners</a></p>");
        h.append(navegacao(r.mes, "/painel", true));
        h.append(cartoes(r.geral));
        h.append(graficos(r));

        h.append("<h2>Por banner</h2><div class=\"tabela\"><table><thead><tr><th>Empresa / banner</th><th class=\"n\">Exibições</th>")
                .append("<th class=\"n\">% das exibições</th><th class=\"n\">Cliques</th><th class=\"n\">Taxa de cliques</th><th class=\"nao-imprimir\">Relatório para a empresa</th></tr></thead><tbody>");
        java.util.Set<String> mostrados = new java.util.LinkedHashSet<>();
        for (Banner b : catalogo.todos()) {
            mostrados.add(b.id());
            linhaDeBanner(h, b.empresa(), b.id(), r.porAnuncio.getOrDefault(b.id(), Totais.ZERO), r.geral.exibicoes(), r.mes, linkParaEmpresa.apply(b));
        }
        for (Map.Entry<String, Totais> e : r.porAnuncio.entrySet()) {
            if (mostrados.add(e.getKey())) { // banner que já saiu do catálogo, mas tem números neste mês
                linhaDeBanner(h, e.getKey() + " (fora do catálogo)", e.getKey(), e.getValue(), r.geral.exibicoes(), r.mes, null);
            }
        }
        h.append("</tbody></table></div>");

        h.append(tabelaPor("Quem usa o app", "Perfil", r.porPerfil, PERFIS, r.geral));
        h.append(tabelaPor("Onde o banner apareceu", "Posição na tela", r.porPosicao, POSICOES, r.geral));
        h.append(definicoes());
        return pagina("Painel de anúncios — " + nomeDoMes(r.mes), h.toString());
    }

    /** Relatório de um banner: usado no painel e, com {@code publico}, no link que vai para a empresa. */
    static String anuncio(ResumoDoMes r, Banner banner, boolean publico, String raiz, String caminho, String linkParaEmpresa) {
        StringBuilder h = new StringBuilder();
        h.append("<h1>Relatório de anúncio</h1>");
        h.append("<p class=\"suave\">").append(esc(banner.empresa())).append(" · ").append(esc(nomeDoMes(r.mes))).append("</p>");
        if (!banner.imagem().isBlank()) {
            h.append("<img class=\"banner\" src=\"").append(esc(raiz + banner.imagem())).append("\" alt=\"")
                    .append(esc(banner.texto())).append("\">");
        }
        h.append(navegacao(r.mes, caminho, false));
        if (!publico) {
            h.append("<p class=\"nao-imprimir\"><a href=\"/painel?mes=").append(r.mes).append("\">← Voltar ao painel geral</a></p>");
            if (linkParaEmpresa != null) {
                h.append(campoDeLink("Link para enviar à empresa (mostra só este banner)", linkParaEmpresa));
            }
        }
        h.append(cartoes(r.geral));
        h.append(graficos(r));
        h.append(tabelaPor("Quem usa o app", "Perfil", r.porPerfil, PERFIS, r.geral));
        h.append(tabelaPor("Onde o banner apareceu", "Posição na tela", r.porPosicao, POSICOES, r.geral));
        h.append(definicoes());
        return pagina("Relatório — " + banner.empresa() + " — " + nomeDoMes(r.mes), h.toString());
    }

    static String naoEncontrada() {
        return pagina("Relatório não encontrado", "<h1>Relatório não encontrado</h1><p>O endereço está incorreto ou não vale mais. Peça um link novo.</p>");
    }

    // ------------------------------------------------------------------ pedaços

    private static void linhaDeBanner(StringBuilder h, String nome, String id, Totais t, long totalDeExibicoes, YearMonth mes, String linkParaEmpresa) {
        h.append("<tr><td><a href=\"/painel/anuncio/").append(esc(id)).append("?mes=").append(mes).append("\">")
                .append(esc(nome)).append("</a></td><td class=\"n\">").append(numero(t.exibicoes()))
                .append("</td><td class=\"n\">").append(porcento(totalDeExibicoes == 0 ? 0 : 100.0 * t.exibicoes() / totalDeExibicoes))
                .append("</td><td class=\"n\">").append(numero(t.cliques())).append("</td><td class=\"n\">")
                .append(porcento(t.ctr())).append("</td><td class=\"nao-imprimir\">");
        if (linkParaEmpresa != null) {
            h.append("<button type=\"button\" onclick=\"copiar(this,'").append(esc(linkParaEmpresa)).append("')\">Copiar link</button>");
        }
        h.append("</td></tr>");
    }

    private static String navegacao(YearMonth mes, String base, boolean comExportar) {
        String q = "?mes=";
        StringBuilder h = new StringBuilder("<div class=\"nav nao-imprimir\">");
        h.append("<a href=\"").append(esc(base)).append(q).append(mes.minusMonths(1)).append("\">← ").append(esc(nomeDoMes(mes.minusMonths(1)))).append("</a>");
        h.append("<span class=\"suave\">").append(esc(nomeDoMes(mes))).append("</span>");
        h.append("<a href=\"").append(esc(base)).append(q).append(mes.plusMonths(1)).append("\">").append(esc(nomeDoMes(mes.plusMonths(1)))).append(" →</a>");
        h.append("<button type=\"button\" onclick=\"window.print()\">Imprimir / salvar em PDF</button>");
        if (comExportar) {
            h.append("<a class=\"botao\" href=\"/painel/exportar.csv?mes=").append(mes).append("\">Baixar planilha (CSV)</a>");
        }
        return h.append("</div>").toString();
    }

    private static String cartoes(Totais t) {
        return "<div class=\"cartoes\">"
                + cartao("Exibições", numero(t.exibicoes()), "vezes em que o banner apareceu na tela")
                + cartao("Cliques", numero(t.cliques()), "toques no banner")
                + cartao("Taxa de cliques", porcento(t.ctr()), "cliques por exibição")
                + "</div>";
    }

    private static String cartao(String titulo, String valor, String legenda) {
        return "<div class=\"cartao\"><div class=\"suave\">" + esc(titulo) + "</div><div class=\"valor\">" + esc(valor)
                + "</div><div class=\"suave pequeno\">" + esc(legenda) + "</div></div>";
    }

    private static String graficos(ResumoDoMes r) {
        return "<h2>Exibições por dia</h2>" + graficoDeBarras(r, true) + "<h2>Cliques por dia</h2>" + graficoDeBarras(r, false);
    }

    /** Barras por dia do mês, em SVG (sem nenhuma biblioteca). */
    private static String graficoDeBarras(ResumoDoMes r, boolean exibicoes) {
        int dias = r.mes.lengthOfMonth();
        long maximo = 1;
        for (Totais t : r.porDia.values()) {
            maximo = Math.max(maximo, exibicoes ? t.exibicoes() : t.cliques());
        }
        double larguraTotal = 720, altura = 140, base = 150, passo = larguraTotal / dias;
        StringBuilder s = new StringBuilder("<svg class=\"grafico\" viewBox=\"0 0 720 175\" role=\"img\" aria-label=\"")
                .append(exibicoes ? "Exibições por dia" : "Cliques por dia").append("\">");
        s.append("<text x=\"0\" y=\"10\" class=\"eixo\">").append(numero(maximo)).append("</text>");
        s.append("<line x1=\"0\" y1=\"").append(base).append("\" x2=\"720\" y2=\"").append(base).append("\" class=\"linha\"/>");
        for (int d = 1; d <= dias; d++) {
            Totais t = r.porDia.getOrDefault(r.mes.atDay(d), Totais.ZERO);
            long v = exibicoes ? t.exibicoes() : t.cliques();
            double h = altura * v / maximo;
            double x = (d - 1) * passo;
            s.append("<rect x=\"").append(String.format(Locale.ROOT, "%.1f", x + 1)).append("\" y=\"")
                    .append(String.format(Locale.ROOT, "%.1f", base - h)).append("\" width=\"")
                    .append(String.format(Locale.ROOT, "%.1f", Math.max(1, passo - 2))).append("\" height=\"")
                    .append(String.format(Locale.ROOT, "%.1f", h)).append("\" class=\"").append(exibicoes ? "e" : "c").append("\"><title>Dia ")
                    .append(d).append(": ").append(numero(v)).append("</title></rect>");
            if (d == 1 || d % 5 == 0) {
                s.append("<text x=\"").append(String.format(Locale.ROOT, "%.1f", x + passo / 2)).append("\" y=\"168\" class=\"eixo\" text-anchor=\"middle\">")
                        .append(d).append("</text>");
            }
        }
        return s.append("</svg>").toString();
    }

    private static String tabelaPor(String titulo, String coluna, Map<String, Totais> dados, Map<String, String> nomes, Totais geral) {
        StringBuilder h = new StringBuilder("<h2>").append(esc(titulo)).append("</h2>");
        if (dados.isEmpty()) {
            return h.append("<p class=\"suave\">Ainda sem dados neste mês.</p>").toString();
        }
        h.append("<div class=\"tabela\"><table><thead><tr><th>").append(esc(coluna)).append("</th><th class=\"n\">Exibições</th>")
                .append("<th class=\"n\">% das exibições</th><th class=\"n\">Cliques</th><th class=\"n\">Taxa de cliques</th></tr></thead><tbody>");
        dados.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().exibicoes(), a.getValue().exibicoes()))
                .forEach(e -> {
                    Totais t = e.getValue();
                    double parte = geral.exibicoes() == 0 ? 0 : 100.0 * t.exibicoes() / geral.exibicoes();
                    h.append("<tr><td>").append(esc(nomes.getOrDefault(e.getKey(), e.getKey()))).append("</td><td class=\"n\">")
                            .append(numero(t.exibicoes())).append("</td><td class=\"n\">").append(porcento(parte))
                            .append("</td><td class=\"n\">").append(numero(t.cliques())).append("</td><td class=\"n\">")
                            .append(porcento(t.ctr())).append("</td></tr>");
                });
        return h.append("</tbody></table></div>").toString();
    }

    private static String campoDeLink(String rotulo, String link) {
        return "<div class=\"nao-imprimir link\"><label>" + esc(rotulo) + "</label><div><input readonly value=\"" + esc(link)
                + "\" onclick=\"this.select()\"><button type=\"button\" onclick=\"copiar(this,'" + esc(link) + "')\">Copiar link</button></div></div>";
    }

    private static String definicoes() {
        return "<h2>Como os números são contados</h2><ul class=\"suave\">"
                + "<li><b>Exibição:</b> o banner ficou pelo menos 50% visível na tela por pelo menos 1 segundo. Um banner no fim de uma tela longa só conta se a pessoa rolou até ele.</li>"
                + "<li><b>Clique:</b> toque no banner (só banners com link podem ser tocados).</li>"
                + "<li><b>Taxa de cliques:</b> cliques divididos pelas exibições.</li>"
                + "<li><b>Perfil:</b> se a pessoa usa o app como idoso, como familiar ou ainda sem entrar na conta.</li>"
                + "<li>Os números são <b>anônimos</b>: somas por dia, sem nome, e-mail, aparelho ou endereço de IP de ninguém. Não medem quantas pessoas diferentes viram o banner, só quantas vezes ele apareceu.</li>"
                + "<li>Os dados vêm do aplicativo e não passam por verificação independente de fraude.</li></ul>";
    }

    // ------------------------------------------------------------------ estrutura

    static String pagina(String titulo, String corpo) {
        return "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<meta name=\"robots\" content=\"noindex, nofollow\"><title>" + esc(titulo) + "</title><style>" + ESTILO + "</style></head><body><main>"
                + corpo + "<p class=\"suave pequeno rodape\">Gerado pelo CuidaMed em " + esc(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))) + ".</p>"
                + "</main><script>" + SCRIPT + "</script></body></html>";
    }

    private static final String SCRIPT = "function copiar(b,t){navigator.clipboard.writeText(t).then(function(){var o=b.textContent;b.textContent='Copiado!';setTimeout(function(){b.textContent=o},1500)})}";

    private static final String ESTILO = """
            :root{--fundo:#f6f8fc;--cartao:#fff;--texto:#1b1f24;--suave:#5a6472;--borda:#d8dee8;--azul:#0b4fc4;--laranja:#d97706}
            @media (prefers-color-scheme:dark){:root{--fundo:#12161b;--cartao:#1b2129;--texto:#e6e9ee;--suave:#9aa4b2;--borda:#2b3440;--azul:#7fb1ff;--laranja:#f59e0b}}
            *{box-sizing:border-box}body{margin:0;background:var(--fundo);color:var(--texto);font:16px/1.5 system-ui,sans-serif}
            main{max-width:60rem;margin:0 auto;padding:1rem 1rem 3rem}h1{font-size:1.7rem;margin:.6rem 0 0}h2{font-size:1.2rem;margin:1.8rem 0 .6rem}
            a{color:var(--azul)}.suave{color:var(--suave)}.pequeno{font-size:.85rem}.rodape{margin-top:2rem}
            .nav{display:flex;flex-wrap:wrap;gap:.6rem 1rem;align-items:center;margin:1rem 0}
            button,.botao{font:inherit;padding:.45rem .9rem;border:1px solid var(--borda);border-radius:.5rem;background:var(--cartao);color:var(--texto);cursor:pointer;text-decoration:none}
            .cartoes{display:grid;grid-template-columns:repeat(auto-fit,minmax(11rem,1fr));gap:.8rem;margin:1rem 0}
            .cartao{background:var(--cartao);border:1px solid var(--borda);border-radius:.8rem;padding:.9rem 1rem}.valor{font-size:2rem;font-weight:700}
            .tabela{overflow-x:auto}table{width:100%;border-collapse:collapse;background:var(--cartao);border:1px solid var(--borda)}
            th,td{padding:.55rem .7rem;text-align:left;border-bottom:1px solid var(--borda)}th{font-size:.85rem;color:var(--suave)}.n{text-align:right;white-space:nowrap}
            .grafico{width:100%;height:auto;background:var(--cartao);border:1px solid var(--borda);border-radius:.6rem}.grafico .e{fill:var(--azul)}.grafico .c{fill:var(--laranja)}
            .grafico .eixo{fill:var(--suave);font-size:10px}.grafico .linha{stroke:var(--borda)}
            .banner{max-width:100%;width:32rem;border-radius:.6rem;border:1px solid var(--borda);margin:.6rem 0}
            .link div{display:flex;gap:.5rem}.link input{flex:1;font:inherit;padding:.45rem;border:1px solid var(--borda);border-radius:.5rem;background:var(--cartao);color:var(--texto)}
            @media print{body{background:#fff;color:#000}.nao-imprimir{display:none!important}.cartao,table,.grafico{border-color:#999;background:#fff}}
            """;
}
