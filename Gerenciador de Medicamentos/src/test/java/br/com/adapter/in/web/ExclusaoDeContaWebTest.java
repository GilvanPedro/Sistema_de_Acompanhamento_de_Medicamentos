package br.com.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.adapter.in.web.exclusao.SolicitacaoDeExclusaoDeConta;
import br.com.adapter.in.web.privacidade.PoliticaDePrivacidade;
import br.com.adapter.in.web.recuperacao.RecuperacaoDeSenha;
import br.com.adapter.out.security.BcryptSenhaAdapter;
import br.com.domain.model.Idoso;

@SpringBootTest(classes = ApiApp.class, properties = "cuidamed.limite.cadastro-maximo=1000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfiguracaoDeTeste.class)
class ExclusaoDeContaWebTest {

    private static final String SENHA = "senha-da-conta-123";
    private static final AtomicInteger PROXIMO_IP = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    EmailsEnviados emails;
    @Autowired
    SolicitacaoDeExclusaoDeConta solicitacao;

    private static String ipNovo() {
        return "198.51.100." + PROXIMO_IP.getAndIncrement();
    }

    private static MockHttpServletRequestBuilder doIp(MockHttpServletRequestBuilder req, String ip) {
        return req.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    private String criarConta(String nome) throws Exception {
        String email = "exclusao." + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
        Map<String, Object> corpo = Map.of("tipo", "IDOSO", "nome", nome, "email", email, "senha", SENHA,
                "aceitouPolitica", true, "versaoPolitica", PoliticaDePrivacidade.VERSAO_ATUAL);
        mvc.perform(doIp(post("/api/v1/auth/registro"), ipNovo()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)))
                .andExpect(status().isCreated());
        return email;
    }

    private JsonNode entrar(String email, int esperado) throws Exception {
        String resposta = mvc.perform(doIp(post("/api/v1/auth/login"), ipNovo()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", SENHA))))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        return resposta.isBlank() ? null : json.readTree(resposta);
    }

    private void pedir(String email, String ip, int esperado) throws Exception {
        mvc.perform(doIp(post("/excluir-conta/solicitar"), ip).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email))
                .andExpect(status().is(esperado));
    }

    private String confirmar(String token, int esperado) throws Exception {
        return mvc.perform(doIp(post("/excluir-conta"), ipNovo()).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", token))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void fluxoCompletoApagaAContaDeVerdadeENaoUsaMaisOToken() throws Exception {
        String email = criarConta("Dona Marlene");
        String renovacaoAntiga = entrar(email, 200).get("refreshToken").asText();

        // sem token: mostra o formulário de e-mail, sem cache
        mvc.perform(get("/excluir-conta")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Frame-Options", "DENY"));

        pedir(email, ipNovo(), 200);
        assertEquals(1, emails.para(email).size());
        assertTrue(emails.para(email).get(0).assunto().contains("exclusão"));
        assertTrue(emails.para(email).get(0).texto().contains("IRREVERSÍVEL"));
        String token = emails.ultimoToken(email);

        // a página de confirmação mostra quem é, antes de apagar
        String confirmacao = mvc.perform(get("/excluir-conta").param("token", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(confirmacao.contains("Dona Marlene") && confirmacao.contains(email));
        assertTrue(confirmacao.contains("value=\"" + token + "\""));

        String pronto = confirmar(token, 200);
        assertTrue(pronto.contains("Pronto, Dona Marlene"));

        // a conta some de verdade
        entrar(email, 401);
        // as sessões abertas antes são encerradas
        mvc.perform(doIp(post("/api/v1/auth/renovar"), ipNovo()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", renovacaoAntiga)))).andExpect(status().isUnauthorized());
        // e-mail avisando que foi excluída
        assertEquals(2, emails.para(email).size());
        assertTrue(emails.para(email).get(1).assunto().contains("excluída"));

        // o link só vale uma vez
        assertTrue(confirmar(token, 400).contains("não vale mais"));
        mvc.perform(get("/excluir-conta").param("token", token)).andExpect(status().isBadRequest());

        // dá para criar uma conta nova com o mesmo e-mail (o antigo foi liberado de verdade)
        mvc.perform(doIp(post("/api/v1/auth/registro"), ipNovo()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("tipo", "IDOSO", "nome", "Dona Marlene de novo", "email", email, "senha", SENHA,
                        "aceitouPolitica", true, "versaoPolitica", PoliticaDePrivacidade.VERSAO_ATUAL))))
                .andExpect(status().isCreated());
    }

    @Test
    void emailSemContaRecebeAMesmaTelaENadaEEnviado() throws Exception {
        String desconhecido = "ninguem." + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
        pedir(desconhecido, ipNovo(), 200);
        assertTrue(emails.para(desconhecido).isEmpty());
    }

    @Test
    void emailInvalidoVoltaParaOFormularioComOMotivo() throws Exception {
        String pagina = mvc.perform(doIp(post("/excluir-conta/solicitar"), ipNovo()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "nao-e-email")).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertTrue(pagina.contains("e-mail válido"));
    }

    @Test
    void linkInexistenteNaoAbreAConfirmacaoNemApagaNada() throws Exception {
        mvc.perform(get("/excluir-conta").param("token", "isso-nao-existe")).andExpect(status().isBadRequest());
        assertTrue(confirmar("isso-nao-existe", 400).contains("não vale mais"));
    }

    @Test
    void umPedidoNovoInvalidaOLinkAnterior() throws Exception {
        String email = criarConta("Seu Osvaldo");
        pedir(email, ipNovo(), 200);
        String primeiro = emails.ultimoToken(email);
        pedir(email, ipNovo(), 200);
        String segundo = emails.ultimoToken(email);
        assertFalse(primeiro.equals(segundo));
        mvc.perform(get("/excluir-conta").param("token", primeiro)).andExpect(status().isBadRequest());
        entrar(email, 200); // nada foi excluído ainda
        mvc.perform(get("/excluir-conta").param("token", segundo)).andExpect(status().isOk());
    }

    @Test
    void ospedidosSaoLimitadosPorEmailEPorIp() throws Exception {
        String email = criarConta("Dona Iracema");
        pedir(email, ipNovo(), 200);
        pedir(email, ipNovo(), 200);
        pedir(email, ipNovo(), 200);
        pedir(email, ipNovo(), 429);
        assertEquals(3, emails.para(email).size());

        String ip = ipNovo();
        for (int i = 0; i < 5; i++) {
            pedir("alguem." + i + UUID.randomUUID().toString().substring(0, 6) + "@teste.com", ip, 200);
        }
        pedir("mais.um." + UUID.randomUUID().toString().substring(0, 6) + "@teste.com", ip, 429);
    }

    @Test
    void excluirAContaCancelaTambemUmPedidoDeNovaSenhaPendente() throws Exception {
        String email = criarConta("Seu Nivaldo");
        mvc.perform(doIp(post("/api/v1/auth/esqueci-senha"), ipNovo()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email)))).andExpect(status().isAccepted());
        String tokenDeSenha = emails.ultimoToken(email);
        mvc.perform(get("/redefinir-senha").param("token", tokenDeSenha)).andExpect(status().isOk());

        pedir(email, ipNovo(), 200);
        confirmar(emails.ultimoToken(email), 200);

        // o link de nova senha, de antes da exclusão, não vale mais
        mvc.perform(get("/redefinir-senha").param("token", tokenDeSenha)).andExpect(status().isBadRequest());
    }

    // ---- o serviço, com relógio controlado (o link expira em 30 minutos)

    private static final class RelogioMutavel extends Clock {
        Instant agora = Instant.parse("2026-09-22T09:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    @Test
    void oLinkExpiraEm30MinutosENaoApagaNada() {
        PortasEmMemoria.Usuarios usuarios = new PortasEmMemoria.Usuarios();
        BcryptSenhaAdapter cripto = new BcryptSenhaAdapter();
        usuarios.salvar(new Idoso(1, "Seu Benedito", "benedito@teste.com", cripto.criptografarSenha(SENHA), List.of()));
        RelogioMutavel relogio = new RelogioMutavel();
        EmailsEnviados enviados = new EmailsEnviados();
        var exclusoes = new ExclusoesEmMemoria();
        var servico = new SolicitacaoDeExclusaoDeConta(usuarios, new br.com.application.service.ExcluirUsuarioService(usuarios), exclusoes,
                new RedefinicoesEmMemoria(), new PortasEmMemoria.Tokens(), new PortasEmMemoria.Aparelhos(),
                fabricarConsentimentosEmMemoria(), enviados, Runnable::run, "https://exemplo.test/", relogio);

        servico.solicitar("benedito@teste.com");
        String token = enviados.ultimoToken("benedito@teste.com");

        relogio.agora = relogio.agora.plus(Duration.ofMinutes(31));
        assertTrue(servico.dadosParaConfirmar(token).isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(br.com.domain.exception.DadosInvalidosException.class, () -> servico.excluir(token));
        assertEquals("benedito@teste.com", usuarios.buscarPorEmail("benedito@teste.com").getEmail(), "a conta continua existindo");
    }

    private static br.com.adapter.in.web.privacidade.Consentimentos fabricarConsentimentosEmMemoria() {
        return new br.com.adapter.in.web.privacidade.Consentimentos() {
            private final java.util.List<Object[]> aceites = new java.util.ArrayList<>();

            @Override
            public synchronized void registrar(int usuarioId, String versao) {
                aceites.add(new Object[]{usuarioId, versao});
            }

            @Override
            public synchronized java.util.Optional<String> versaoAceita(int usuarioId) {
                return java.util.Optional.empty();
            }

            @Override
            public synchronized java.util.List<Aceite> todos(int usuarioId) {
                return java.util.List.of();
            }

            @Override
            public synchronized void removerDe(int usuarioId) {
                aceites.removeIf(a -> (int) a[0] == usuarioId);
            }
        };
    }
}
