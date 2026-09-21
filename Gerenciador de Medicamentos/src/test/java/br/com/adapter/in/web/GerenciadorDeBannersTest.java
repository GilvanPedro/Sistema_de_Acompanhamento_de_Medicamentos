package br.com.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import br.com.adapter.in.web.metricas.BancoDeBanners;
import br.com.adapter.in.web.metricas.CatalogoDeBanners;
import br.com.adapter.in.web.metricas.ValidacaoDeBanner;
import br.com.domain.exception.DadosInvalidosException;

@SpringBootTest(classes = ApiApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfiguracaoDeTeste.class)
class GerenciadorDeBannersTest {

    private static final String SENHA = "senha-do-painel-de-teste";
    private static final Pattern CODIGO = Pattern.compile("name=\"csrf\" value=\"([0-9a-f]{32})\"");

    @Autowired
    MockMvc mvc;
    @Autowired
    BancoDeBanners banco;
    @Autowired
    CatalogoDeBanners catalogo;

    private static String ip(int n) {
        return "198.51.101." + n;
    }

    private static MockHttpServletRequestBuilder logado(MockHttpServletRequestBuilder req, int ip) {
        return (MockHttpServletRequestBuilder) req.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(("painel:" + SENHA).getBytes()))
                .with(r -> { r.setRemoteAddr(ip(ip)); return r; });
    }

    private String codigo(int ip) throws Exception {
        String pagina = mvc.perform(logado(get("/painel/banners"), ip)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher m = CODIGO.matcher(pagina);
        assertTrue(m.find(), "o formulário precisa trazer o código escondido");
        return m.group(1);
    }

    private MockMultipartHttpServletRequestBuilder novo(String url, int ip, String csrf, String empresa, String link, String peso, byte[] imagem) {
        MockMultipartHttpServletRequestBuilder req = multipart(url);
        req.file(new MockMultipartFile("imagem", "banner.png", "image/png", imagem == null ? new byte[0] : imagem));
        req.param("csrf", csrf).param("empresa", empresa).param("texto", "").param("link", link).param("peso", peso);
        req.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(("painel:" + SENHA).getBytes()));
        req.with(r -> { r.setRemoteAddr(ip(ip)); return r; });
        return req;
    }

    @Test
    void semSenhaNaoVeNemMexeNosBanners() throws Exception {
        mvc.perform(get("/painel/banners")).andExpect(status().isUnauthorized());
        mvc.perform(post("/painel/banners/exemplo-1/remover").param("csrf", "x")).andExpect(status().isUnauthorized());
        assertTrue(banco.buscar("exemplo-1").isPresent());
    }

    @Test
    void formularioSemOCodigoOuDeOutroSiteEhRecusado() throws Exception {
        mvc.perform(logado(post("/painel/banners/exemplo-2/remover").param("csrf", "codigo-errado"), 1)).andExpect(status().isForbidden());
        mvc.perform(logado(post("/painel/banners/exemplo-2/remover"), 1)).andExpect(status().isForbidden());
        String codigo = codigo(2);
        mvc.perform(logado(post("/painel/banners/exemplo-2/remover").param("csrf", codigo).header("Origin", "https://site-do-atacante.exemplo"), 3))
                .andExpect(status().isForbidden());
        assertTrue(banco.buscar("exemplo-2").isPresent(), "nada deveria ter sido removido");
    }

    @Test
    void adicionaBannerQueApareceNaListaDoAppEComImagemServida() throws Exception {
        String codigo = codigo(4);
        mvc.perform(novo("/painel/banners", 4, codigo, "Padaria do Zé!", " https://padariadoze.com.br ", "2,5", BancoDeBannersEmMemoria.png(1280, 400)))
                .andExpect(status().isSeeOther()).andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/painel/banners?ok=")));

        var registro = banco.buscar("padaria-do-ze").orElseThrow();
        assertEquals("Padaria do Zé!", registro.empresa());
        assertEquals("https://padariadoze.com.br", registro.link());
        assertEquals(2.5, registro.peso(), 1e-9);
        assertEquals("Anúncio de Padaria do Zé!", registro.texto(), "sem descrição, usa o nome da empresa");

        // a imagem é servida com o tipo certo, e o banner entra na lista do app
        mvc.perform(get("/anuncios/padaria-do-ze.png")).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/anuncios/nao-existe.png")).andExpect(status().isNotFound());
        mvc.perform(get("/anuncios/..%2Fsegredo.png")).andExpect(status().isNotFound());
        String lista = mvc.perform(get("/api/v1/anuncios")).andReturn().getResponse().getContentAsString();
        assertTrue(lista.contains("padaria-do-ze"));
        assertTrue(lista.contains("http://localhost/anuncios/padaria-do-ze.png?v="));

        // outro cadastro com o mesmo nome ganha um número no fim
        mvc.perform(novo("/painel/banners", 4, codigo, "Padaria do Ze", "", "1", BancoDeBannersEmMemoria.png(1280, 400))).andExpect(status().isSeeOther());
        assertTrue(banco.buscar("padaria-do-ze-2").isPresent());
    }

    @Test
    void recusaImagemLinkPesoEEmpresaInvalidosMostrandoOMotivo() throws Exception {
        String codigo = codigo(5);
        long antes = banco.listar().size();
        byte[] boa = BancoDeBannersEmMemoria.png(1280, 400);

        // não é imagem de verdade (só o nome e o tipo dizem que é)
        String falsa = mvc.perform(novo("/painel/banners", 5, codigo, "Empresa Falsa", "", "1", "<svg onload=alert(1)></svg>".getBytes()))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertTrue(falsa.contains("PNG ou JPEG"));
        assertFalse(falsa.contains("<svg onload"), "nada digitado pode voltar sem escape");
        mvc.perform(novo("/painel/banners", 5, codigo, "Sem Imagem", "", "1", null)).andExpect(status().isBadRequest());
        // formato que não é de banner
        assertTrue(mvc.perform(novo("/painel/banners", 5, codigo, "Quadrada", "", "1", BancoDeBannersEmMemoria.png(800, 800)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString().contains("formato de banner"));
        assertTrue(mvc.perform(novo("/painel/banners", 5, codigo, "Pequena", "", "1", BancoDeBannersEmMemoria.png(200, 50)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString().contains("pixels"));
        // link, peso e empresa
        mvc.perform(novo("/painel/banners", 5, codigo, "Link Ruim", "javascript:alert(1)", "1", boa)).andExpect(status().isBadRequest());
        mvc.perform(novo("/painel/banners", 5, codigo, "Link Ruim", "http://sem-seguranca.exemplo", "1", boa)).andExpect(status().isBadRequest());
        mvc.perform(novo("/painel/banners", 5, codigo, "Link Ruim", "mailto:a@b.com?bcc=x@y.com", "1", boa)).andExpect(status().isBadRequest());
        mvc.perform(novo("/painel/banners", 5, codigo, "Peso Ruim", "", "101", boa)).andExpect(status().isBadRequest());
        mvc.perform(novo("/painel/banners", 5, codigo, "Peso Ruim", "", "abc", boa)).andExpect(status().isBadRequest());
        mvc.perform(novo("/painel/banners", 5, codigo, "   ", "", "1", boa)).andExpect(status().isBadRequest());
        mvc.perform(novo("/painel/banners", 5, codigo, "!!!", "", "1", boa)).andExpect(status().isBadRequest());
        // o e-mail permitido (assunto e mensagem) passa
        mvc.perform(novo("/painel/banners", 5, codigo, "Email Bom", "mailto:contato@exemplo.com?subject=Oi", "1", boa)).andExpect(status().isSeeOther());
        assertEquals(antes + 1, banco.listar().size(), "só o cadastro válido deveria ter entrado");
    }

    @Test
    void editaTrocaImagemPausaAtivaERemove() throws Exception {
        String codigo = codigo(6);
        mvc.perform(novo("/painel/banners", 6, codigo, "Loja Teste", "", "1", BancoDeBannersEmMemoria.png(1280, 400))).andExpect(status().isSeeOther());
        long versaoInicial = banco.buscar("loja-teste").orElseThrow().versao();

        // a página de edição mostra os dados
        String edicao = mvc.perform(logado(get("/painel/banners/loja-teste/editar"), 6)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(edicao.contains("Loja Teste"));

        // muda só o texto (sem imagem nova): a imagem antiga fica
        mvc.perform(novo("/painel/banners/loja-teste", 6, codigo, "Loja Teste 2", "", "3", null)).andExpect(status().isSeeOther());
        assertEquals("Loja Teste 2", banco.buscar("loja-teste").orElseThrow().empresa());
        assertEquals(3.0, banco.buscar("loja-teste").orElseThrow().peso(), 1e-9);
        assertNotNull(banco.imagem("loja-teste").orElseThrow().bytes());
        assertTrue(banco.buscar("loja-teste").orElseThrow().versao() > versaoInicial, "a versão muda para os celulares buscarem de novo");

        // troca a imagem por uma JPEG de verdade
        var jpeg = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1280, 400, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", jpeg);
        MockMultipartHttpServletRequestBuilder troca = multipart("/painel/banners/loja-teste");
        troca.file(new MockMultipartFile("imagem", "nova.jpg", "image/jpeg", jpeg.toByteArray()));
        troca.param("csrf", codigo).param("empresa", "Loja Teste 2").param("texto", "").param("link", "").param("peso", "3");
        troca.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(("painel:" + SENHA).getBytes())).with(r -> { r.setRemoteAddr(ip(6)); return r; });
        mvc.perform(troca).andExpect(status().isSeeOther());
        assertEquals("image/jpeg", banco.imagem("loja-teste").orElseThrow().tipo());
        mvc.perform(get("/anuncios/loja-teste.jpg")).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg"));

        // pausar tira do sorteio (peso 0); ativar volta ao normal
        mvc.perform(logado(post("/painel/banners/loja-teste/pausar").param("csrf", codigo), 6)).andExpect(status().isSeeOther());
        assertEquals(0.0, banco.buscar("loja-teste").orElseThrow().peso(), 1e-9);
        assertFalse(mvc.perform(get("/api/v1/anuncios")).andReturn().getResponse().getContentAsString().contains("loja-teste"), "pausado não vai para o app");
        mvc.perform(logado(post("/painel/banners/loja-teste/ativar").param("csrf", codigo), 6)).andExpect(status().isSeeOther());
        assertEquals(1.0, banco.buscar("loja-teste").orElseThrow().peso(), 1e-9);

        // remove
        mvc.perform(logado(post("/painel/banners/loja-teste/remover").param("csrf", codigo), 6)).andExpect(status().isSeeOther());
        assertTrue(banco.buscar("loja-teste").isEmpty());
        mvc.perform(get("/anuncios/loja-teste.jpg")).andExpect(status().isNotFound());
        mvc.perform(logado(post("/painel/banners/loja-teste/remover").param("csrf", codigo), 6)).andExpect(status().isNotFound());
    }

    @Test
    void oNomeDaEmpresaNaListaEEscapado() throws Exception {
        String codigo = codigo(7);
        mvc.perform(novo("/painel/banners", 7, codigo, "<script>alert(1)</script> Ltda", "", "1", BancoDeBannersEmMemoria.png(1280, 400))).andExpect(status().isSeeOther());
        String pagina = mvc.perform(logado(get("/painel/banners"), 7)).andReturn().getResponse().getContentAsString();
        assertFalse(pagina.contains("<script>alert(1)</script>"));
        assertTrue(pagina.contains("&lt;script&gt;alert(1)&lt;/script&gt; Ltda"));
        String relatorio = mvc.perform(logado(get("/painel"), 7)).andReturn().getResponse().getContentAsString();
        assertFalse(relatorio.contains("<script>alert(1)</script>"));
    }

    @Test
    void semNenhumBannerCadastradoValeSoOExemploDoProjeto() {
        BancoDeBanners vazio = new BancoDeBanners() {
            public java.util.List<Registro> listar() { return java.util.List.of(); }
            public java.util.Optional<Registro> buscar(String id) { return java.util.Optional.empty(); }
            public java.util.Optional<Imagem> imagem(String id) { return java.util.Optional.empty(); }
            public void salvar(Registro r, byte[] imagem) { }
            public boolean remover(String id) { return false; }
        };
        CatalogoDeBanners catalogoVazio = new CatalogoDeBanners(vazio);
        assertTrue(catalogoVazio.somenteExemplos());
        assertEquals(1, catalogoVazio.todos().size());
        assertEquals("exemplo", catalogoVazio.todos().get(0).id());
        assertTrue(catalogoVazio.todos().get(0).imagem().startsWith("/anuncios-exemplo/"));
    }

    @Test
    void regrasDeValidacaoDeIdentificacaoEPeso() {
        assertEquals("padaria-do-ze", ValidacaoDeBanner.identificacao("Padaria do Zé!"));
        assertEquals("cafe-sao-joao-2", ValidacaoDeBanner.identificacao("  Café São João #2 "));
        assertEquals(0.0, ValidacaoDeBanner.peso("0"), 1e-9);
        assertEquals(1.0, ValidacaoDeBanner.peso(""), 1e-9);
        org.junit.jupiter.api.Assertions.assertThrows(DadosInvalidosException.class, () -> ValidacaoDeBanner.peso("-1"));
        org.junit.jupiter.api.Assertions.assertThrows(DadosInvalidosException.class, () -> ValidacaoDeBanner.peso("NaN"));
        org.junit.jupiter.api.Assertions.assertThrows(DadosInvalidosException.class, () -> ValidacaoDeBanner.imagem(new byte[2 * 1024 * 1024]));
    }
}
