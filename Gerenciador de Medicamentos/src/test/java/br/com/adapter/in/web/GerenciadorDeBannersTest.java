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
    void oFormularioDeCadastroFicaEscondidoNumaJanelaQueAbreComOBotaoDoTopo() throws Exception {
        String pagina = mvc.perform(logado(get("/painel/banners"), 20)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(pagina.contains("class=\"novo\"") && pagina.contains("Adicionar banner"), "botão de destaque no topo");
        assertTrue(pagina.indexOf("class=\"novo\"") < pagina.indexOf("<h1>"), "o botão fica no topo, antes do título");
        assertTrue(pagina.contains("<dialog id=\"novo-banner\""), "o formulário fica numa janela");
        assertFalse(pagina.contains("data-abrir"), "sem erro, a janela começa fechada");
        assertTrue(pagina.contains("Voltar ao painel"));
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
        assertTrue(falsa.contains("data-abrir=\"sim\""), "com erro, a janela já volta aberta");
        assertTrue(falsa.contains("value=\"Empresa Falsa\""), "o que foi digitado não se perde");
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
    void bannerRemovidoSomeDoPainelEDosRelatoriosENumerosVelhosNaoRessuscitam() throws Exception {
        String codigo = codigo(8);
        mvc.perform(novo("/painel/banners", 8, codigo, "Loja Que Sai", "", "1", BancoDeBannersEmMemoria.png(1280, 400))).andExpect(status().isSeeOther());
        mvc.perform(post("/api/v1/anuncios/eventos").with(r -> { r.setRemoteAddr(ip(80)); return r; }).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"eventos\":[{\"tipo\":\"EXIBICAO\",\"anuncioId\":\"loja-que-sai\",\"posicao\":\"home-fim\",\"perfil\":\"IDOSO\",\"dia\":\"" + java.time.LocalDate.now() + "\",\"quantidade\":9}]}"))
                .andExpect(status().isNoContent());
        String antes = mvc.perform(logado(get("/painel"), 8)).andReturn().getResponse().getContentAsString();
        assertTrue(antes.contains("Loja Que Sai"));
        String link = java.util.regex.Pattern.compile("/relatorio/(loja-que-sai)/([0-9a-f]{32})").matcher(antes).results().findFirst().orElseThrow().group();
        mvc.perform(get(link)).andExpect(status().isOk());

        mvc.perform(logado(post("/painel/banners/loja-que-sai/remover").param("csrf", codigo), 8)).andExpect(status().isSeeOther());

        // some da tabela, do CSV e dos relatórios (o link que a empresa tinha para de funcionar)
        assertFalse(mvc.perform(logado(get("/painel"), 8)).andReturn().getResponse().getContentAsString().contains("Loja Que Sai"));
        assertFalse(mvc.perform(logado(get("/painel/exportar.csv"), 8)).andReturn().getResponse().getContentAsString().contains("loja-que-sai"));
        mvc.perform(logado(get("/painel/anuncio/loja-que-sai"), 8)).andExpect(status().isNotFound());
        mvc.perform(get(link)).andExpect(status().isNotFound());

        // um banner novo com o mesmo nome começa do zero (não herda os números do antigo)
        mvc.perform(novo("/painel/banners", 8, codigo, "Loja Que Sai", "", "1", BancoDeBannersEmMemoria.png(1280, 400))).andExpect(status().isSeeOther());
        String depois = mvc.perform(logado(get("/painel/anuncio/loja-que-sai"), 8)).andReturn().getResponse().getContentAsString();
        assertTrue(depois.contains("<div class=\"valor\">0</div>"), "o banner novo não pode herdar as exibições do antigo");
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
            public java.util.Optional<byte[]> video(String id) { return java.util.Optional.empty(); }
            public void salvar(Registro r, byte[] imagem, byte[] video, boolean removerVideo) { }
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

    // ------------------------------------------------------------------ vídeo

    private static byte[] arquivo(String nome) throws Exception {
        try (var in = GerenciadorDeBannersTest.class.getResourceAsStream("/videos/" + nome)) {
            return java.util.Objects.requireNonNull(in, nome).readAllBytes();
        }
    }

    private MockMultipartHttpServletRequestBuilder comVideo(String url, int ip, String csrf, String empresa, byte[] imagem, byte[] video, boolean removerVideo) {
        MockMultipartHttpServletRequestBuilder req = multipart(url);
        if (imagem != null) {
            req.file(new MockMultipartFile("imagem", "banner.png", "image/png", imagem));
        }
        if (video != null) {
            req.file(new MockMultipartFile("video", "banner.mp4", "video/mp4", video));
        }
        req.param("csrf", csrf).param("empresa", empresa).param("texto", "").param("link", "").param("peso", "1");
        if (removerVideo) {
            req.param("removerVideo", "on");
        }
        req.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(("painel:" + SENHA).getBytes()));
        req.with(r -> { r.setRemoteAddr(ip(ip)); return r; });
        return req;
    }

    @Test
    void oCabecalhoDoMp4EhLidoSemBibliotecaERecusaOQueNaoServe() throws Exception {
        var ok = ValidacaoDeBanner.video(arquivo("banner-ok.mp4"));
        assertEquals(1280, ok.largura());
        assertEquals(400, ok.altura());
        assertTrue(Math.abs(ok.duracaoMs() - 4000) < 100, "duração de 4 s, deu " + ok.duracaoMs());

        var longo = org.junit.jupiter.api.Assertions.assertThrows(DadosInvalidosException.class, () -> ValidacaoDeBanner.video(arquivo("banner-longo.mp4")));
        assertTrue(longo.getMessage().contains("1 a 15 segundos"), longo.getMessage());
        var quadrado = org.junit.jupiter.api.Assertions.assertThrows(DadosInvalidosException.class, () -> ValidacaoDeBanner.video(arquivo("banner-quadrado.mp4")));
        assertTrue(quadrado.getMessage().contains("320 a 1920") || quadrado.getMessage().contains("formato de banner"), quadrado.getMessage());

        byte[] hevc = arquivo("banner-ok.mp4").clone();
        for (int i = 0; i < hevc.length - 4; i++) { // troca o nome do codec de avc1 para hvc1 (H.265)
            if (hevc[i] == 'a' && hevc[i + 1] == 'v' && hevc[i + 2] == 'c' && hevc[i + 3] == '1') { hevc[i] = 'h'; hevc[i + 1] = 'v'; }
        }
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(DadosInvalidosException.class, () -> ValidacaoDeBanner.video(hevc)).getMessage().contains("H.264"));

        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(DadosInvalidosException.class, () -> ValidacaoDeBanner.video("isto não é um vídeo, só texto qualquer".getBytes())).getMessage().contains("MP4"));
        byte[] cortado = java.util.Arrays.copyOf(arquivo("banner-ok.mp4"), 200); // arquivo truncado: sem moov
        org.junit.jupiter.api.Assertions.assertThrows(DadosInvalidosException.class, () -> ValidacaoDeBanner.video(cortado));
        byte[] grande = new byte[4 * 1024 * 1024 + 1];
        System.arraycopy(arquivo("banner-ok.mp4"), 0, grande, 0, 32);
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(DadosInvalidosException.class, () -> ValidacaoDeBanner.video(grande)).getMessage().contains("4 MB"));
        org.junit.jupiter.api.Assertions.assertThrows(DadosInvalidosException.class, () -> ValidacaoDeBanner.video(new byte[0]));
    }

    @Test
    void adicionaBannerComVideoQueEhServidoComSuporteAPedidosParciais() throws Exception {
        String codigo = codigo(10);
        mvc.perform(comVideo("/painel/banners", 10, codigo, "Padaria Com Video", BancoDeBannersEmMemoria.png(1280, 400), arquivo("banner-ok.mp4"), false))
                .andExpect(status().isSeeOther());
        var registro = banco.buscar("padaria-com-video").orElseThrow();
        assertTrue(registro.temVideo());
        assertTrue(Math.abs(registro.duracaoVideoMs() - 4000) < 100);

        // a lista do app traz o endereço completo do vídeo
        String lista = mvc.perform(get("/api/v1/anuncios")).andReturn().getResponse().getContentAsString();
        assertTrue(lista.contains("\"video\":\"http://localhost/anuncios/padaria-com-video.mp4?v="), lista);

        // o vídeo é servido com o tipo certo e aceita Range (o player do Android pede pedaços)
        byte[] completo = mvc.perform(get("/anuncios/padaria-com-video.mp4")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "video/mp4")).andExpect(header().string("Accept-Ranges", "bytes"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(arquivo("banner-ok.mp4").length, completo.length);
        mvc.perform(get("/anuncios/padaria-com-video.mp4").header("Range", "bytes=0-99")).andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-99/" + completo.length));
        mvc.perform(get("/anuncios/exemplo-1.mp4")).andExpect(status().isNotFound()); // banner sem vídeo

        String pagina = mvc.perform(logado(get("/painel/banners"), 10)).andReturn().getResponse().getContentAsString();
        assertTrue(pagina.contains("Com vídeo"));
    }

    @Test
    void recusaVideoQueNaoServeMostrandoOMotivo() throws Exception {
        String codigo = codigo(11);
        long antes = banco.listar().size();
        byte[] imagem = BancoDeBannersEmMemoria.png(1280, 400);
        assertTrue(mvc.perform(comVideo("/painel/banners", 11, codigo, "Video Longo", imagem, arquivo("banner-longo.mp4"), false))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString().contains("1 a 15 segundos"));
        mvc.perform(comVideo("/painel/banners", 11, codigo, "Video Falso", imagem, "isto não é um vídeo, só texto qualquer".getBytes(), false)).andExpect(status().isBadRequest());
        // vídeo com formato diferente da imagem (6:1 contra 3,2:1)
        assertTrue(mvc.perform(comVideo("/painel/banners", 11, codigo, "Formato Diferente", BancoDeBannersEmMemoria.png(1200, 200), arquivo("banner-ok.mp4"), false))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString().contains("formato diferente"));
        assertEquals(antes, banco.listar().size(), "nenhum desses deveria ter sido cadastrado");
    }

    @Test
    void editarMantemOVideoAteAPessoaTrocarOuRemover() throws Exception {
        String codigo = codigo(12);
        byte[] imagem = BancoDeBannersEmMemoria.png(1280, 400);
        mvc.perform(comVideo("/painel/banners", 12, codigo, "Loja Video", imagem, arquivo("banner-ok.mp4"), false)).andExpect(status().isSeeOther());
        long versao = banco.buscar("loja-video").orElseThrow().versao();

        // editar só o texto: o vídeo fica
        mvc.perform(comVideo("/painel/banners/loja-video", 12, codigo, "Loja Video 2", null, null, false)).andExpect(status().isSeeOther());
        assertTrue(banco.buscar("loja-video").orElseThrow().temVideo());
        assertTrue(banco.buscar("loja-video").orElseThrow().versao() > versao);
        assertTrue(mvc.perform(logado(get("/painel/banners/loja-video/editar"), 12)).andReturn().getResponse().getContentAsString().contains("Remover o vídeo atual"));

        // trocar só a imagem por uma de formato incompatível com o vídeo é recusado
        assertTrue(mvc.perform(comVideo("/painel/banners/loja-video", 12, codigo, "Loja Video 2", BancoDeBannersEmMemoria.png(1200, 200), null, false))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString().contains("formato diferente"));
        assertTrue(banco.buscar("loja-video").orElseThrow().temVideo(), "a recusa não pode apagar o vídeo");

        // remover o vídeo: o banner vira só imagem e o endereço do vídeo some
        mvc.perform(comVideo("/painel/banners/loja-video", 12, codigo, "Loja Video 2", null, null, true)).andExpect(status().isSeeOther());
        assertFalse(banco.buscar("loja-video").orElseThrow().temVideo());
        mvc.perform(get("/anuncios/loja-video.mp4")).andExpect(status().isNotFound());
        assertFalse(mvc.perform(get("/api/v1/anuncios")).andReturn().getResponse().getContentAsString().contains("loja-video.mp4"));

        // acrescentar vídeo a um banner que só tinha imagem
        mvc.perform(comVideo("/painel/banners/loja-video", 12, codigo, "Loja Video 2", null, arquivo("banner-ok.mp4"), false)).andExpect(status().isSeeOther());
        assertTrue(banco.buscar("loja-video").orElseThrow().temVideo());
    }
}
