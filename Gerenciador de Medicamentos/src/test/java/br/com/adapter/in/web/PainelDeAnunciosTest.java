package br.com.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.adapter.in.web.metricas.MetricasDeAnuncios;
import br.com.adapter.in.web.painel.AcessoAoPainel;
import br.com.adapter.in.web.painel.SenhaDoPainel;

@SpringBootTest(classes = ApiApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfiguracaoDeTeste.class)
class PainelDeAnunciosTest {

    private static final String SENHA = "senha-do-painel-de-teste";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    MetricasDeAnuncios metricas;

    private static Map<String, Object> evento(String tipo, String banner, String posicao, String perfil, int quantidade) {
        return Map.of("tipo", tipo, "anuncioId", banner, "posicao", posicao, "perfil", perfil, "dia", LocalDate.now().toString(), "quantidade", quantidade);
    }

    private void enviar(String ip, Map<String, Object>... eventos) throws Exception {
        mvc.perform(post("/api/v1/anuncios/eventos").with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("eventos", List.of(eventos)))))
                .andExpect(status().isNoContent());
    }

    private static MockHttpServletRequestBuilder comSenha(MockHttpServletRequestBuilder req, String senha) {
        return req.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(("painel:" + senha).getBytes()));
    }

    private long total(String banner, boolean exibicoes, String perfil) {
        return metricas.doMes(YearMonth.now()).stream()
                .filter(l -> l.anuncioId().equals(banner) && (perfil == null || l.perfil().equals(perfil)))
                .mapToLong(l -> exibicoes ? l.exibicoes() : l.cliques()).sum();
    }

    @Test
    @SuppressWarnings("unchecked")
    void contagensSaoSomadasSemLoginEIgnoraBannerPosicaoEQuantidadeInvalidos() throws Exception {
        long exibAntes = total("exemplo-1", true, "IDOSO"), cliqueAntes = total("exemplo-1", false, "IDOSO");
        long visitanteAntes = total("exemplo-1", true, "VISITANTE"), outroAntes = total("exemplo-2", true, null);

        enviar("198.51.100.20",
                evento("EXIBICAO", "exemplo-1", "home-topo", "IDOSO", 3),
                evento("CLIQUE", "exemplo-1", "home-topo", "IDOSO", 1),
                evento("EXIBICAO", "banner-que-nao-existe", "home-topo", "IDOSO", 5), // banner desconhecido
                evento("EXIBICAO", "exemplo-2", "posicao-inventada", "IDOSO", 5),       // posição desconhecida
                evento("EXIBICAO", "exemplo-2", "home-fim", "IDOSO", 0),                // quantidade zero
                evento("EXIBICAO", "exemplo-2", "home-fim", "IDOSO", 101),              // quantidade absurda
                evento("VISUALIZACAO", "exemplo-2", "home-fim", "IDOSO", 1),            // tipo desconhecido
                evento("EXIBICAO", "exemplo-1", "entrada-fim", "QUALQUER-COISA", 2));   // perfil estranho vira visitante

        assertEquals(exibAntes + 3, total("exemplo-1", true, "IDOSO"));
        assertEquals(cliqueAntes + 1, total("exemplo-1", false, "IDOSO"));
        assertEquals(visitanteAntes + 2, total("exemplo-1", true, "VISITANTE"));
        assertEquals(outroAntes, total("exemplo-2", true, null), "nada do banner 2 deveria ter sido contado");

        // sem corpo, ou com lista enorme, é recusado
        mvc.perform(post("/api/v1/anuncios/eventos").with(r -> { r.setRemoteAddr("198.51.100.21"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        Map<String, Object>[] demais = new Map[201];
        java.util.Arrays.fill(demais, evento("EXIBICAO", "exemplo-1", "home-fim", "IDOSO", 1));
        mvc.perform(post("/api/v1/anuncios/eventos").with(r -> { r.setRemoteAddr("198.51.100.21"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("eventos", List.of(demais)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void asSeteTelasComBannerContamEExibemONomeNoRelatorio() throws Exception {
        List<String> posicoes = List.of("entrada-fim", "home-topo", "home-fim", "idoso-fim", "remedios-fim", "perfil-topo", "vinculos-fim");
        long antes = total("exemplo-2", true, "IDOSO");
        @SuppressWarnings("unchecked")
        Map<String, Object>[] eventos = posicoes.stream().map(p -> evento("EXIBICAO", "exemplo-2", p, "IDOSO", 1)).toArray(Map[]::new);
        enviar("198.51.100.70", eventos);
        assertEquals(antes + 7, total("exemplo-2", true, "IDOSO"), "todas as posições precisam ser aceitas");
        String pagina = mvc.perform(comSenha(get("/painel"), SENHA).with(r -> { r.setRemoteAddr("198.51.100.71"); return r; }))
                .andReturn().getResponse().getContentAsString();
        assertTrue(pagina.contains("Meus dados: topo") && pagina.contains("Vincular idoso ou familiar"));
    }

    @Test
    void listaDeBannersDoAppEPublicaTemPesoEImagemComEnderecoCompleto() throws Exception {
        String texto = mvc.perform(get("/api/v1/anuncios")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var anuncios = json.readTree(texto).get("anuncios");
        assertTrue(anuncios.size() >= 3);
        for (var a : anuncios) {
            assertTrue(a.get("imagem").asText().startsWith("http://localhost/anuncios/"), "imagem com endereço completo: " + a.get("imagem"));
            assertTrue(a.get("peso").asDouble() > 0);
            assertFalse(a.get("id").asText().isBlank());
        }
        // o arquivo estático que as versões antigas do app usam continua existindo
        mvc.perform(get("/anuncios/anuncios.json")).andExpect(status().isOk());
    }

    @Test
    void eventosDeDiaMuitoAntigoOuFuturoContamNoDiaDeHoje() throws Exception {
        long antes = total("exemplo-3", true, "FAMILIAR");
        Map<String, Object> velho = Map.of("tipo", "EXIBICAO", "anuncioId", "exemplo-3", "posicao", "home-fim", "perfil", "FAMILIAR",
                "dia", LocalDate.now().minusDays(200).toString(), "quantidade", 4);
        Map<String, Object> futuro = Map.of("tipo", "EXIBICAO", "anuncioId", "exemplo-3", "posicao", "home-fim", "perfil", "FAMILIAR",
                "dia", LocalDate.now().plusDays(5).toString(), "quantidade", 2);
        Map<String, Object> lixo = Map.of("tipo", "EXIBICAO", "anuncioId", "exemplo-3", "posicao", "home-fim", "perfil", "FAMILIAR",
                "dia", "ontem", "quantidade", 1);
        enviar("198.51.100.22", velho, futuro, lixo);
        assertEquals(antes + 7, total("exemplo-3", true, "FAMILIAR"));
    }

    @Test
    void oPainelExigeSenhaELimitaTentativas() throws Exception {
        String ip = "198.51.100.30";
        mvc.perform(get("/painel").with(r -> { r.setRemoteAddr(ip); return r; }))
                .andExpect(status().isUnauthorized()).andExpect(header().exists("WWW-Authenticate"));
        mvc.perform(comSenha(get("/painel"), "senha-errada").with(r -> { r.setRemoteAddr(ip); return r; }))
                .andExpect(status().isUnauthorized());
        // a senha certa entra (o navegador manda usuário:senha)
        mvc.perform(comSenha(get("/painel"), SENHA).with(r -> { r.setRemoteAddr("198.51.100.31"); return r; }))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        // depois de erros demais, até a senha certa fica bloqueada por um tempo
        String ipQueErra = "198.51.100.32";
        for (int i = 0; i < 5; i++) {
            mvc.perform(comSenha(get("/painel"), "errada" + i).with(r -> { r.setRemoteAddr(ipQueErra); return r; }))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(comSenha(get("/painel"), SENHA).with(r -> { r.setRemoteAddr(ipQueErra); return r; }))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void semSenhaConfiguradaOPainelFicaDesligado() {
        AcessoAoPainel acesso = new AcessoAoPainel(new SenhaDoPainel(null), new br.com.adapter.in.web.auth.LimiteDeTentativas(5, java.time.Duration.ofMinutes(1), 10));
        var resposta = acesso.verificar(new org.springframework.mock.web.MockHttpServletRequest());
        assertEquals(404, resposta.getStatusCode().value());
        // senha curta demais também não liga
        assertFalse(new SenhaDoPainel("curta").configurada());
    }

    @Test
    void relatorioGeralMostraOsNumerosELinksPrivadosPorEmpresa() throws Exception {
        enviar("198.51.100.40", evento("EXIBICAO", "exemplo-1", "home-topo", "FAMILIAR", 10), evento("CLIQUE", "exemplo-1", "home-topo", "FAMILIAR", 2));
        String pagina = mvc.perform(comSenha(get("/painel"), SENHA).with(r -> { r.setRemoteAddr("198.51.100.41"); return r; }))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(pagina.contains("Painel de anúncios"));
        assertTrue(pagina.contains("Espaço para anunciar (exemplo 1)"));
        assertTrue(pagina.contains("Familiares"));
        assertTrue(pagina.contains("Baixar planilha"));

        // cada banner tem um link privado próprio
        Matcher m = Pattern.compile("/relatorio/(exemplo-1)/([0-9a-f]{32})").matcher(pagina);
        assertTrue(m.find(), "o painel deveria oferecer o link privado do banner");
        String id = m.group(1), codigo = m.group(2);

        // o link privado mostra só aquele banner, sem pedir senha
        String publica = mvc.perform(get("/relatorio/" + id + "/" + codigo).with(r -> { r.setRemoteAddr("198.51.100.42"); return r; }))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(publica.contains("Espaço para anunciar (exemplo 1)"));
        assertFalse(publica.contains("exemplo 2"), "a empresa não pode ver os outros banners");
        assertFalse(publica.contains("/painel"), "a página pública não leva ao painel");

        // o código de um banner não abre outro; códigos errados e ids estranhos dão 404 (sem vazar HTML de fora)
        mvc.perform(get("/relatorio/exemplo-2/" + codigo)).andExpect(status().isNotFound());
        mvc.perform(get("/relatorio/" + id + "/" + "0".repeat(32))).andExpect(status().isNotFound());
        String estranho = mvc.perform(get("/relatorio/<script>alert(1)</script>/x")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertFalse(estranho.contains("<script>alert(1)"));
    }

    @Test
    void relatorioDoBannerNoPainelExigeSenhaEAceitaMesInvalido() throws Exception {
        mvc.perform(get("/painel/anuncio/exemplo-1")).andExpect(status().isUnauthorized());
        mvc.perform(comSenha(get("/painel/anuncio/exemplo-1?mes=isto-nao-e-mes"), SENHA).with(r -> { r.setRemoteAddr("198.51.100.43"); return r; }))
                .andExpect(status().isOk());
        mvc.perform(comSenha(get("/painel/anuncio/%3Cscript%3E"), SENHA).with(r -> { r.setRemoteAddr("198.51.100.43"); return r; }))
                .andExpect(status().isNotFound());
    }

    @Test
    void planilhaCsvTemUmaLinhaPorContagemESemFormulas() throws Exception {
        enviar("198.51.100.50", evento("EXIBICAO", "exemplo-2", "idoso-fim", "IDOSO", 7));
        String csv = mvc.perform(comSenha(get("/painel/exportar.csv"), SENHA).with(r -> { r.setRemoteAddr("198.51.100.51"); return r; }))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("anuncios-")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("mes;dia;banner;empresa;posicao;perfil;exibicoes;cliques"));
        assertTrue(csv.lines().anyMatch(l -> l.contains(";exemplo-2;") && l.contains(";idoso-fim;IDOSO;")));
        mvc.perform(get("/painel/exportar.csv")).andExpect(status().isUnauthorized());
    }

    @Test
    void enviosDemaisDoMesmoIpSaoLimitados() throws Exception {
        String ip = "198.51.100.60";
        for (int i = 0; i < 120; i++) {
            enviar(ip, evento("EXIBICAO", "exemplo-3", "home-fim", "IDOSO", 1));
        }
        mvc.perform(post("/api/v1/anuncios/eventos").with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("eventos", List.of(evento("EXIBICAO", "exemplo-3", "home-fim", "IDOSO", 1))))))
                .andExpect(status().isTooManyRequests());
    }
}
