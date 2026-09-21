package br.com.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

import br.com.adapter.in.web.recuperacao.RecuperacaoDeSenha;
import br.com.adapter.in.web.recuperacao.RedefinicoesDeSenha;
import br.com.adapter.out.security.BcryptSenhaAdapter;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.Idoso;

@SpringBootTest(classes = ApiApp.class, properties = "cuidamed.limite.cadastro-maximo=1000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfiguracaoDeTeste.class)
class RecuperacaoDeSenhaTest {

    private static final String SENHA_ANTIGA = "senha-antiga-123";
    private static final String SENHA_NOVA = "senha-nova-456";
    /** Cada teste usa IPs próprios: os limites por IP são guardados na memória do servidor, que os testes dividem. */
    private static final AtomicInteger PROXIMO_IP = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    EmailsEnviados emails;

    private static String ipNovo() {
        return "203.0.113." + PROXIMO_IP.getAndIncrement();
    }

    private static MockHttpServletRequestBuilder doIp(MockHttpServletRequestBuilder req, String ip) {
        return req.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    private String criarConta() throws Exception {
        String email = "esqueci." + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
        Map<String, Object> corpo = Map.of("tipo", "IDOSO", "nome", "Dona Esquecida", "email", email, "senha", SENHA_ANTIGA,
                "aceitouPolitica", true, "versaoPolitica", br.com.adapter.in.web.privacidade.PoliticaDePrivacidade.VERSAO_ATUAL);
        mvc.perform(doIp(post("/api/v1/auth/registro"), ipNovo()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)))
                .andExpect(status().isCreated());
        return email;
    }

    private JsonNode entrar(String email, String senha, int esperado) throws Exception {
        String resposta = mvc.perform(doIp(post("/api/v1/auth/login"), ipNovo()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", senha))))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        return json.readTree(resposta);
    }

    private void pedir(String email, String ip, int esperado) throws Exception {
        mvc.perform(doIp(post("/api/v1/auth/esqueci-senha"), ip).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().is(esperado));
    }

    private String enviarNovaSenha(String token, String senha, String confirmar, int esperado) throws Exception {
        return mvc.perform(doIp(post("/redefinir-senha"), ipNovo()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token).param("senha", senha).param("confirmar", confirmar))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void fluxoCompletoDoLinkNoEmailAteEntrarComASenhaNova() throws Exception {
        String email = criarConta();
        String renovacaoAntiga = entrar(email, SENHA_ANTIGA, 200).get("refreshToken").asText();

        mvc.perform(doIp(post("/api/v1/auth/esqueci-senha"), ipNovo()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("Se este e-mail tiver uma conta")));
        assertEquals(1, emails.para(email).size());
        assertTrue(emails.para(email).get(0).assunto().contains("nova senha"));
        assertTrue(emails.para(email).get(0).texto().contains("30 minutos"));
        String token = emails.ultimoToken(email);

        // a página abre, com o formulário, e não fica em cache nem manda o endereço adiante
        String pagina = mvc.perform(get("/redefinir-senha").param("token", token)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(pagina.contains("name=\"senha\"") && pagina.contains("Salvar a nova senha"));
        assertTrue(pagina.contains("value=\"" + token + "\""));

        assertTrue(enviarNovaSenha(token, SENHA_NOVA, SENHA_NOVA, 200).contains("Senha alterada"));
        entrar(email, SENHA_ANTIGA, 401);
        entrar(email, SENHA_NOVA, 200);
        // as sessões que já estavam abertas foram encerradas
        mvc.perform(doIp(post("/api/v1/auth/renovar"), ipNovo()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", renovacaoAntiga)))).andExpect(status().isUnauthorized());
        // avisa por e-mail que a senha mudou
        assertEquals(2, emails.para(email).size());
        assertTrue(emails.para(email).get(1).assunto().contains("alterada"));

        // o link só vale uma vez
        assertTrue(enviarNovaSenha(token, "outra-senha-789", "outra-senha-789", 400).contains("não vale mais"));
        entrar(email, SENHA_NOVA, 200);
        mvc.perform(get("/redefinir-senha").param("token", token)).andExpect(status().isBadRequest());
    }

    @Test
    void emailSemContaRecebeAMesmaRespostaENadaEEnviado() throws Exception {
        String desconhecido = "ninguem." + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
        String existente = criarConta();
        String comConta = mvc.perform(doIp(post("/api/v1/auth/esqueci-senha"), ipNovo()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", existente)))).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String semConta = mvc.perform(doIp(post("/api/v1/auth/esqueci-senha"), ipNovo()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", desconhecido)))).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(comConta, semConta, "a resposta não pode revelar se o e-mail tem conta");
        assertTrue(emails.para(desconhecido).isEmpty());
        assertEquals(1, emails.para(existente).size());
    }

    @Test
    void emailMalEscritoENaoPreenchidoSaoRecusados() throws Exception {
        for (String ruim : new String[] {"sem-arroba", "  ", ""}) {
            pedir(ruim, ipNovo(), 400);
        }
        mvc.perform(doIp(post("/api/v1/auth/esqueci-senha"), ipNovo()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void senhaFracaOuDiferenteNaoGastaOLinkEAPessoaTentaDeNovo() throws Exception {
        String email = criarConta();
        pedir(email, ipNovo(), 202);
        String token = emails.ultimoToken(email);

        assertTrue(enviarNovaSenha(token, "curta", "curta", 400).contains("pelo menos 8 caracteres"));
        String diferentes = enviarNovaSenha(token, SENHA_NOVA, "outra-coisa-999", 400);
        assertTrue(diferentes.contains("não são iguais") && diferentes.contains("name=\"senha\""), "volta ao formulário com o motivo");
        entrar(email, SENHA_ANTIGA, 200); // nada mudou até aqui

        assertTrue(enviarNovaSenha(token, SENHA_NOVA, SENHA_NOVA, 200).contains("Senha alterada"));
        entrar(email, SENHA_NOVA, 200);
    }

    @Test
    void linkInexistenteOuSemTokenNaoAbreOFormulario() throws Exception {
        mvc.perform(get("/redefinir-senha").param("token", "isso-nao-existe")).andExpect(status().isBadRequest());
        mvc.perform(get("/redefinir-senha")).andExpect(status().isBadRequest());
        assertTrue(enviarNovaSenha("isso-nao-existe", SENHA_NOVA, SENHA_NOVA, 400).contains("não vale mais"));
    }

    @Test
    void umPedidoNovoInvalidaOLinkAnterior() throws Exception {
        String email = criarConta();
        pedir(email, ipNovo(), 202);
        String primeiro = emails.ultimoToken(email);
        pedir(email, ipNovo(), 202);
        String segundo = emails.ultimoToken(email);
        assertFalse(primeiro.equals(segundo));
        mvc.perform(get("/redefinir-senha").param("token", primeiro)).andExpect(status().isBadRequest());
        mvc.perform(get("/redefinir-senha").param("token", segundo)).andExpect(status().isOk());
    }

    @Test
    void oTokenNaoApareceEmClaroNemEReaproveitadoEntreContas() throws Exception {
        String a = criarConta();
        String b = criarConta();
        pedir(a, ipNovo(), 202);
        pedir(b, ipNovo(), 202);
        String tokenA = emails.ultimoToken(a);
        String tokenB = emails.ultimoToken(b);
        assertFalse(tokenA.equals(tokenB));
        assertTrue(tokenA.length() >= 43, "256 bits em base64: " + tokenA);
        // o link de A não muda a senha de B
        enviarNovaSenha(tokenA, SENHA_NOVA, SENHA_NOVA, 200);
        entrar(b, SENHA_ANTIGA, 200);
    }

    @Test
    void ospedidosSaoLimitadosPorEmailEPorIp() throws Exception {
        String email = criarConta();
        pedir(email, ipNovo(), 202);
        pedir(email, ipNovo(), 202);
        pedir(email, ipNovo(), 202);
        pedir(email, ipNovo(), 429); // o quarto no mesmo e-mail, mesmo de outro IP
        assertEquals(3, emails.para(email).size());

        String ip = ipNovo();
        for (int i = 0; i < 5; i++) {
            pedir("alguem." + i + UUID.randomUUID().toString().substring(0, 6) + "@teste.com", ip, 202);
        }
        pedir("mais.um." + UUID.randomUUID().toString().substring(0, 6) + "@teste.com", ip, 429);
    }

    // ---- o serviço, com relógio controlado (o link expira em 30 minutos)

    private static final class RelogioMutavel extends Clock {
        Instant agora = Instant.parse("2026-09-21T15:00:00Z");

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
    void oLinkExpiraEm30Minutos() {
        PortasEmMemoria.Usuarios usuarios = new PortasEmMemoria.Usuarios();
        BcryptSenhaAdapter cripto = new BcryptSenhaAdapter();
        usuarios.salvar(new Idoso(1, "Seu Antonio", "antonio@teste.com", cripto.criptografarSenha(SENHA_ANTIGA), List.of()));
        RelogioMutavel relogio = new RelogioMutavel();
        EmailsEnviados enviados = new EmailsEnviados();
        RedefinicoesDeSenha redefinicoes = new RedefinicoesEmMemoria();
        RecuperacaoDeSenha servico = new RecuperacaoDeSenha(usuarios, cripto, redefinicoes, new PortasEmMemoria.Tokens(), enviados,
                Runnable::run, "https://exemplo.test/", relogio);

        servico.solicitar("antonio@teste.com");
        String token = enviados.ultimoToken("antonio@teste.com");
        assertTrue(enviados.para("antonio@teste.com").get(0).texto().contains("https://exemplo.test/redefinir-senha?token="));

        relogio.agora = relogio.agora.plus(Duration.ofMinutes(29));
        assertTrue(servico.linkValido(token));
        relogio.agora = relogio.agora.plus(Duration.ofMinutes(2));
        assertFalse(servico.linkValido(token));
        assertThrows(DadosInvalidosException.class, () -> servico.redefinir(token, SENHA_NOVA, SENHA_NOVA));
        assertTrue(cripto.verificarSenha(SENHA_ANTIGA, usuarios.buscarPorEmail("antonio@teste.com").getSenha()), "a senha não mudou");

        // um pedido novo, feito dentro da validade, funciona
        servico.solicitar("antonio@teste.com");
        servico.redefinir(enviados.ultimoToken("antonio@teste.com"), SENHA_NOVA, SENHA_NOVA);
        assertTrue(cripto.verificarSenha(SENHA_NOVA, usuarios.buscarPorEmail("antonio@teste.com").getSenha()));
    }

    @Test
    void falhaNoEnvioDoEmailNaoQuebraOPedido() {
        PortasEmMemoria.Usuarios usuarios = new PortasEmMemoria.Usuarios();
        BcryptSenhaAdapter cripto = new BcryptSenhaAdapter();
        usuarios.salvar(new Idoso(2, "Dona Rita", "rita@teste.com", cripto.criptografarSenha(SENHA_ANTIGA), List.of()));
        RecuperacaoDeSenha servico = new RecuperacaoDeSenha(usuarios, cripto, new RedefinicoesEmMemoria(), new PortasEmMemoria.Tokens(),
                (para, assunto, texto) -> {
                    throw new IllegalStateException("provedor fora do ar");
                }, Runnable::run, "https://exemplo.test", new RelogioMutavel());
        servico.solicitar("rita@teste.com"); // não pode lançar
    }
}
