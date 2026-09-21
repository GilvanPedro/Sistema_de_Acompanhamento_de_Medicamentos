package br.com.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

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

@SpringBootTest(classes = ApiApp.class, properties = "cuidamed.limite.cadastro-maximo=1000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfiguracaoDeTeste.class)
class ApiTest {

    /** Corpo de cadastro válido: os dados recebidos mais o aceite da política de privacidade (obrigatório). */
    private static Map<String, Object> reg(Object... paresChaveValor) {
        Map<String, Object> corpo = new java.util.HashMap<>();
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            corpo.put((String) paresChaveValor[i], paresChaveValor[i + 1]);
        }
        corpo.put("aceitouPolitica", true);
        corpo.put("versaoPolitica", "1.0");
        return corpo;
    }

    private static final String SENHA = "senha-forte-123";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    PortasEmMemoria.Aparelhos aparelhos;

    /** Cria uma conta com e-mail único (cada teste usa contas próprias) e entra; devolve o login. */
    private Sessao criarConta(String tipo, String nome) throws Exception {
        String email = nome.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reg("tipo", tipo, "nome", nome, "email", email, "senha", SENHA))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
        JsonNode login = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", SENHA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return new Sessao(email, login.get("usuario").get("id").asInt(), login.get("accessToken").asText(),
                login.get("refreshToken").asText());
    }

    private record Sessao(String email, int id, String acesso, String renovacao) { }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder req, Sessao s) {
        return req.header("Authorization", "Bearer " + s.acesso());
    }

    private String corpo(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    private int cadastrarMedicamento(Sessao idoso, String nome) throws Exception {
        String resposta = mvc.perform(com(post("/api/v1/idosos/" + idoso.id() + "/medicamentos"), idoso)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", nome, "diaSemana", "MONDAY", "horario", "08:00", "tipo", "COMPRIMIDO"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("id").asInt();
    }

    /** Familiar pede, idoso aceita. */
    private void vincular(Sessao familiar, Sessao idoso) throws Exception {
        mvc.perform(com(post("/api/v1/vinculos/pedidos"), familiar).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("email", idoso.email())))).andExpect(status().isAccepted());
        mvc.perform(com(post("/api/v1/vinculos/pedidos/" + familiar.id() + "/aceitar"), idoso)).andExpect(status().isNoContent());
    }

    @Test
    void semTokenOuComTokenInvalidoNegaAcesso() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer isto-nao-e-um-token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/saude")).andExpect(status().isOk());
    }

    @Test
    void loginComSenhaErradaDaMensagemGenericaEBloqueiaDepoisDeVariasFalhas() throws Exception {
        Sessao ana = criarConta("IDOSO", "Ana Idosa");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of("email", ana.email(), "senha", "errada"))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("email", ana.email(), "senha", SENHA))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void cadastroComEmailRepetidoOuDadosInvalidosRetorna400() throws Exception {
        Sessao ana = criarConta("IDOSO", "Ana Repetida");
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(reg("tipo", "IDOSO", "nome", "Outra", "email", ana.email(), "senha", SENHA))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(reg("tipo", "ADMIN", "nome", "X", "email", "x@x.com", "senha", SENHA))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content("isto nao e json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void familiarSoVeOIdosoDepoisDeELeAceitarOPedido() throws Exception {
        Sessao idoso = criarConta("IDOSO", "Dona Maria");
        Sessao familiar = criarConta("FAMILIAR", "Filho Joao");
        cadastrarMedicamento(idoso, "Losartana");

        mvc.perform(com(get("/api/v1/idosos/" + idoso.id() + "/medicamentos"), familiar)).andExpect(status().isForbidden());

        mvc.perform(com(post("/api/v1/vinculos/pedidos"), familiar).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("email", idoso.email())))).andExpect(status().isAccepted());
        // pedido pendente ainda não dá acesso, e não pode ser repetido
        mvc.perform(com(get("/api/v1/idosos/" + idoso.id() + "/medicamentos"), familiar)).andExpect(status().isForbidden());
        mvc.perform(com(post("/api/v1/vinculos/pedidos"), familiar).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("email", idoso.email())))).andExpect(status().isAccepted());

        // o pedido repetido não duplica nada: o idoso continua vendo um só
        mvc.perform(com(get("/api/v1/vinculos/pedidos"), idoso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].familiar.id").value(familiar.id()));
        mvc.perform(com(post("/api/v1/vinculos/pedidos/" + familiar.id() + "/aceitar"), idoso)).andExpect(status().isNoContent());

        mvc.perform(com(get("/api/v1/idosos/" + idoso.id() + "/medicamentos"), familiar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Losartana"));
        mvc.perform(com(get("/api/v1/me/idosos"), familiar)).andExpect(jsonPath("$[0].id").value(idoso.id()));

        // o idoso remove o familiar e o acesso acaba
        mvc.perform(com(delete("/api/v1/me/familiares/" + familiar.id()), idoso)).andExpect(status().isNoContent());
        mvc.perform(com(get("/api/v1/idosos/" + idoso.id() + "/medicamentos"), familiar)).andExpect(status().isForbidden());
    }

    @Test
    void pedidoRecusadoNaoDaAcessoEPodeSerRefeito() throws Exception {
        Sessao idoso = criarConta("IDOSO", "Seu Antonio");
        Sessao familiar = criarConta("FAMILIAR", "Neta Bia");
        mvc.perform(com(post("/api/v1/vinculos/pedidos"), familiar).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("email", idoso.email())))).andExpect(status().isAccepted());
        mvc.perform(com(post("/api/v1/vinculos/pedidos/" + familiar.id() + "/recusar"), idoso)).andExpect(status().isNoContent());
        mvc.perform(com(get("/api/v1/idosos/" + idoso.id() + "/medicamentos"), familiar)).andExpect(status().isForbidden());
        // responder de novo não funciona (já não há pedido pendente)
        mvc.perform(com(post("/api/v1/vinculos/pedidos/" + familiar.id() + "/aceitar"), idoso)).andExpect(status().isBadRequest());
        mvc.perform(com(post("/api/v1/vinculos/pedidos"), familiar).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("email", idoso.email())))).andExpect(status().isAccepted());
    }

    @Test
    void familiarNaoVinculadoNaoMexeNosMedicamentosDeOutroIdoso() throws Exception {
        Sessao idoso = criarConta("IDOSO", "Dona Rosa");
        Sessao intruso = criarConta("FAMILIAR", "Estranho Silva");
        int id = cadastrarMedicamento(idoso, "Metformina");

        mvc.perform(com(patch("/api/v1/medicamentos/" + id), intruso).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "Outro")))).andExpect(status().isForbidden());
        mvc.perform(com(delete("/api/v1/medicamentos/" + id), intruso)).andExpect(status().isForbidden());
        mvc.perform(com(post("/api/v1/idosos/" + idoso.id() + "/medicamentos"), intruso).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "X", "diaSemana", "MONDAY", "horario", "08:00", "tipo", "GOTAS"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void familiarVinculadoEditaEExcluiMedicamentoEEdicaoEParcial() throws Exception {
        Sessao idoso = criarConta("IDOSO", "Dona Lurdes");
        Sessao familiar = criarConta("FAMILIAR", "Filha Carla");
        vincular(familiar, idoso);
        int id = cadastrarMedicamento(idoso, "Sinvastatina");

        mvc.perform(com(patch("/api/v1/medicamentos/" + id), familiar).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", "Sinvastatina 20mg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Sinvastatina 20mg"))
                .andExpect(jsonPath("$.horario").value("08:00"))
                .andExpect(jsonPath("$.tipo").value("COMPRIMIDO"));

        mvc.perform(com(delete("/api/v1/medicamentos/" + id), familiar)).andExpect(status().isNoContent());
        mvc.perform(com(get("/api/v1/idosos/" + idoso.id() + "/medicamentos"), idoso))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void medicamentoInvalidoRetorna400() throws Exception {
        Sessao idoso = criarConta("IDOSO", "Seu Pedro");
        mvc.perform(com(post("/api/v1/idosos/" + idoso.id() + "/medicamentos"), idoso).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", "", "diaSemana", "MONDAY", "horario", "08:00", "tipo", "GOTAS"))))
                .andExpect(status().isBadRequest());
        mvc.perform(com(post("/api/v1/idosos/" + idoso.id() + "/medicamentos"), idoso).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", "X", "diaSemana", "FUNDAY", "horario", "08:00", "tipo", "GOTAS"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soOIdosoRegistraTomadaDoProprioRemedioEUmaVezPorDia() throws Exception {
        Sessao idoso = criarConta("IDOSO", "Dona Neusa");
        Sessao familiar = criarConta("FAMILIAR", "Filho Ze");
        Sessao outroIdoso = criarConta("IDOSO", "Seu Otavio");
        vincular(familiar, idoso);
        int id = cadastrarMedicamento(idoso, "Enalapril");

        mvc.perform(com(post("/api/v1/medicamentos/" + id + "/tomadas"), familiar)).andExpect(status().isForbidden());
        mvc.perform(com(post("/api/v1/medicamentos/" + id + "/tomadas"), outroIdoso)).andExpect(status().isForbidden());

        mvc.perform(com(post("/api/v1/medicamentos/" + id + "/tomadas"), idoso))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.foiTomado").value(true));
        mvc.perform(com(post("/api/v1/medicamentos/" + id + "/tomadas"), idoso)).andExpect(status().isBadRequest());

        // idoso e familiar vinculado veem o histórico
        mvc.perform(com(get("/api/v1/idosos/" + idoso.id() + "/historico"), idoso)).andExpect(jsonPath("$.length()").value(1))
                // a data precisa sair como texto ISO (2026-09-19T08:30:00), e não como lista de números
                .andExpect(jsonPath("$[0].dataHora").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*")));
        mvc.perform(com(get("/api/v1/idosos/" + idoso.id() + "/historico"), familiar)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(com(get("/api/v1/idosos/" + idoso.id() + "/historico"), outroIdoso)).andExpect(status().isForbidden());
    }

    @Test
    void renovarTrocaOTokenEReusoDeTokenAntigoEncerraTodasAsSessoes() throws Exception {
        Sessao ana = criarConta("IDOSO", "Ana Renova");

        JsonNode novo = json.readTree(mvc.perform(post("/api/v1/auth/renovar").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("refreshToken", ana.renovacao()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String novoRefresh = novo.get("refreshToken").asText();
        assertFalse(novoRefresh.equals(ana.renovacao()));

        // reutilizar o token antigo é suspeito: falha e derruba também o token novo
        mvc.perform(post("/api/v1/auth/renovar").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("refreshToken", ana.renovacao())))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/renovar").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("refreshToken", novoRefresh)))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/renovar").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("refreshToken", "token-inexistente")))).andExpect(status().isUnauthorized());
    }

    @Test
    void sairRevogaOTokenDeRenovacao() throws Exception {
        Sessao ana = criarConta("IDOSO", "Ana Sai");
        mvc.perform(post("/api/v1/auth/sair").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("refreshToken", ana.renovacao())))).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/renovar").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("refreshToken", ana.renovacao())))).andExpect(status().isUnauthorized());
    }

    @Test
    void trocarASenhaEncerraAsSessoesEExcluirAContaInvalidaOToken() throws Exception {
        Sessao ana = criarConta("IDOSO", "Ana Muda");
        mvc.perform(com(patch("/api/v1/me"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("senha", "outra-senha-456", "senhaAtual", SENHA))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/renovar").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("refreshToken", ana.renovacao())))).andExpect(status().isUnauthorized());

        mvc.perform(com(delete("/api/v1/me"), ana).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("senha", "outra-senha-456")))).andExpect(status().isNoContent());
        // o token de acesso ainda não expirou, mas a conta não existe mais
        mvc.perform(com(get("/api/v1/me"), ana)).andExpect(status().isUnauthorized());
    }

    @Test
    void senhaFracaOuGrandeDemaisERecusadaNoCadastro() throws Exception {
        for (String senha : new String[]{"1", "curta", "1234567", "x".repeat(73)}) {
            mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(reg("tipo", "IDOSO", "nome", "Fraca", "email", "fraca@teste.com", "senha", senha))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void nomeGrandeDemaisERecusadoEEmailComDominioLongoEAceito() throws Exception {
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(reg("tipo", "IDOSO", "nome", "N".repeat(151), "email", "nome.longo@teste.com", "senha", SENHA))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(reg("tipo", "FAMILIAR", "nome", "Dominio Longo",
                                "email", "longo." + UUID.randomUUID().toString().substring(0, 8) + "@empresa.photography", "senha", SENHA))))
                .andExpect(status().isCreated());
    }

    @Test
    void trocarSenhaOuEmailExigeASenhaAtualMasTrocarONomeNao() throws Exception {
        Sessao ana = criarConta("IDOSO", "Ana Confirma");

        mvc.perform(com(patch("/api/v1/me"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", "Ana Nova"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Ana Nova"));

        // sem a senha atual, ou com a errada, não troca nada
        mvc.perform(com(patch("/api/v1/me"), ana).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("senha", "outra-senha-456")))).andExpect(status().isBadRequest());
        mvc.perform(com(patch("/api/v1/me"), ana).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("email", "outro@teste.com", "senhaAtual", "errada-errada")))).andExpect(status().isForbidden());

        // a senha continua a antiga
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("email", ana.email(), "senha", SENHA)))).andExpect(status().isOk());
    }

    @Test
    void excluirAContaExigeASenhaEAsTentativasErradasSaoLimitadas() throws Exception {
        Sessao ana = criarConta("IDOSO", "Ana Exclui");

        mvc.perform(com(delete("/api/v1/me"), ana)).andExpect(status().isBadRequest());
        for (int i = 0; i < 5; i++) {
            mvc.perform(com(delete("/api/v1/me"), ana).contentType(MediaType.APPLICATION_JSON)
                    .content(corpo(Map.of("senha", "senha-errada-" + i)))).andExpect(status().isForbidden());
        }
        // depois de 5 erros, nem a senha certa é aceita por um tempo (token roubado não adivinha a senha)
        mvc.perform(com(delete("/api/v1/me"), ana).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("senha", SENHA)))).andExpect(status().isTooManyRequests());
        // e a conta continua de pé
        mvc.perform(com(get("/api/v1/me"), ana)).andExpect(status().isOk());
    }

    @Test
    void pedidoDeVinculoNaoRevelaSeOEmailExiste() throws Exception {
        Sessao familiar = criarConta("FAMILIAR", "Curioso Silva");
        Sessao idoso = criarConta("IDOSO", "Dona Reservada");
        Sessao outroFamiliar = criarConta("FAMILIAR", "Outro Familiar");

        String existente = mvc.perform(com(post("/api/v1/vinculos/pedidos"), familiar).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("email", idoso.email()))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String inexistente = mvc.perform(com(post("/api/v1/vinculos/pedidos"), familiar).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("email", "ninguem@nao-existe.com"))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String naoEIdoso = mvc.perform(com(post("/api/v1/vinculos/pedidos"), familiar).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("email", outroFamiliar.email()))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(existente, inexistente);
        org.junit.jupiter.api.Assertions.assertEquals(existente, naoEIdoso);

        // o mesmo vale quando o idoso adiciona um familiar direto
        String adicionou = mvc.perform(com(post("/api/v1/me/familiares"), idoso).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("email", familiar.email()))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String semConta = mvc.perform(com(post("/api/v1/me/familiares"), idoso).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("email", "ninguem@nao-existe.com"))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(adicionou, semConta);
        mvc.perform(com(get("/api/v1/me/familiares"), idoso)).andExpect(jsonPath("$[0].id").value(familiar.id()));
    }

    @Test
    void tomadaAceitaAHoraRealDoToqueERecusaHoraNoFuturoOuMuitoAntiga() throws Exception {
        Sessao idoso = criarConta("IDOSO", "Dona Hora");
        int id = cadastrarMedicamento(idoso, "Enalapril");
        java.time.LocalDateTime duasHorasAtras = java.time.LocalDateTime.now().minusHours(2).withNano(0);

        // a hora que vale é a do toque (mesmo que o pedido chegue horas depois, por falta de internet)
        mvc.perform(com(post("/api/v1/medicamentos/" + id + "/tomadas"), idoso).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("dataHora", duasHorasAtras.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataHora").value(org.hamcrest.Matchers.startsWith(duasHorasAtras.toString().substring(0, 16))));
        // no mesmo dia, vale uma tomada só
        mvc.perform(com(post("/api/v1/medicamentos/" + id + "/tomadas"), idoso).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("dataHora", duasHorasAtras.plusMinutes(1).toString())))).andExpect(status().isBadRequest());

        int outro = cadastrarMedicamento(idoso, "Atenolol");
        mvc.perform(com(post("/api/v1/medicamentos/" + outro + "/tomadas"), idoso).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("dataHora", java.time.LocalDateTime.now().plusHours(1).toString())))).andExpect(status().isBadRequest());
        mvc.perform(com(post("/api/v1/medicamentos/" + outro + "/tomadas"), idoso).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("dataHora", java.time.LocalDateTime.now().minusDays(8).toString())))).andExpect(status().isBadRequest());
        // sem corpo continua valendo "agora"
        mvc.perform(com(post("/api/v1/medicamentos/" + outro + "/tomadas"), idoso)).andExpect(status().isCreated());
    }

    @Test
    void cadastrarComAMesmaChaveDeIdempotenciaNaoDuplicaOMedicamento() throws Exception {
        Sessao idoso = criarConta("IDOSO", "Seu Reenvio");
        String medicamento = corpo(Map.of("nome", "Sinvastatina", "diaSemana", "MONDAY", "horario", "20:00", "tipo", "COMPRIMIDO"));

        String primeira = mvc.perform(com(post("/api/v1/idosos/" + idoso.id() + "/medicamentos"), idoso)
                        .header("Idempotency-Key", "chave-de-teste-0001").contentType(MediaType.APPLICATION_JSON).content(medicamento))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        // o app reenvia (a resposta se perdeu): recebe o mesmo remédio, sem criar outro
        String segunda = mvc.perform(com(post("/api/v1/idosos/" + idoso.id() + "/medicamentos"), idoso)
                        .header("Idempotency-Key", "chave-de-teste-0001").contentType(MediaType.APPLICATION_JSON).content(medicamento))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(json.readTree(primeira).get("id"), json.readTree(segunda).get("id"));
        mvc.perform(com(get("/api/v1/idosos/" + idoso.id() + "/medicamentos"), idoso)).andExpect(jsonPath("$.length()").value(1));

        // outra chave é outro remédio; chave malformada é recusada; sem chave continua como antes
        mvc.perform(com(post("/api/v1/idosos/" + idoso.id() + "/medicamentos"), idoso)
                .header("Idempotency-Key", "chave-de-teste-0002").contentType(MediaType.APPLICATION_JSON).content(medicamento)).andExpect(status().isCreated());
        mvc.perform(com(post("/api/v1/idosos/" + idoso.id() + "/medicamentos"), idoso)
                .header("Idempotency-Key", "curta").contentType(MediaType.APPLICATION_JSON).content(medicamento)).andExpect(status().isBadRequest());
        mvc.perform(com(post("/api/v1/idosos/" + idoso.id() + "/medicamentos"), idoso)
                .contentType(MediaType.APPLICATION_JSON).content(medicamento)).andExpect(status().isCreated());
        mvc.perform(com(get("/api/v1/idosos/" + idoso.id() + "/medicamentos"), idoso)).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void aparelhoRegistradoRecebePushDeTomadaPedidoEMudancaNosRemedios() throws Exception {
        Sessao idoso = criarConta("IDOSO", "Dona Alzira");
        Sessao familiar = criarConta("FAMILIAR", "Neto Beto");

        // sem token (ou com token enorme) é recusado; sem login também
        mvc.perform(com(put("/api/v1/me/dispositivos"), idoso).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("token", " ")))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/me/dispositivos").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("token", "abc")))).andExpect(status().isUnauthorized());
        mvc.perform(com(put("/api/v1/me/dispositivos"), familiar).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("token", "token-familiar")))).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me/dispositivos/estado")).andExpect(status().isUnauthorized());
        mvc.perform(com(get("/api/v1/me/dispositivos/estado"), familiar))
                .andExpect(status().isOk()).andExpect(jsonPath("$.push").value("TESTE")).andExpect(jsonPath("$.aparelhosDestaConta").value(1));

        // pedido de vínculo acorda o idoso; aceitar acorda o familiar
        aparelhos.avisados.clear();
        mvc.perform(com(post("/api/v1/vinculos/pedidos"), familiar).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("email", idoso.email())))).andExpect(status().isAccepted());
        assertEquals(java.util.List.of(idoso.id()), aparelhos.avisados);
        aparelhos.avisados.clear();
        mvc.perform(com(post("/api/v1/vinculos/pedidos/" + familiar.id() + "/aceitar"), idoso)).andExpect(status().isNoContent());
        assertEquals(java.util.List.of(familiar.id()), aparelhos.avisados);

        // o familiar mexe nos remédios: o idoso é avisado; o próprio idoso mexendo não se avisa
        aparelhos.avisados.clear();
        int id = cadastrarMedicamento(idoso, "Losartana");
        assertEquals(java.util.List.of(), aparelhos.avisados);
        mvc.perform(com(patch("/api/v1/medicamentos/" + id), familiar).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "Losartana 50")))).andExpect(status().isOk());
        assertEquals(java.util.List.of(idoso.id()), aparelhos.avisados);

        // a tomada acorda o familiar
        aparelhos.avisados.clear();
        mvc.perform(com(post("/api/v1/medicamentos/" + id + "/tomadas"), idoso)).andExpect(status().isCreated());
        assertEquals(java.util.List.of(familiar.id()), aparelhos.avisados);
    }

    @Test
    void sairDaContaOuExcluiLaTiraOAparelhoDaLista() throws Exception {
        Sessao familiar = criarConta("FAMILIAR", "Tia Rita");
        Sessao outra = criarConta("FAMILIAR", "Tio Rui");
        String token = "tk-" + UUID.randomUUID();
        mvc.perform(com(put("/api/v1/me/dispositivos"), familiar).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("token", token)))).andExpect(status().isNoContent());
        assertEquals(java.util.List.of(token), aparelhos.tokensDe(familiar.id()));

        // outra conta não consegue remover o token alheio
        mvc.perform(com(delete("/api/v1/me/dispositivos"), outra).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("token", token)))).andExpect(status().isNoContent());
        assertEquals(1, aparelhos.tokensDe(familiar.id()).size());

        mvc.perform(com(delete("/api/v1/me/dispositivos"), familiar).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("token", token)))).andExpect(status().isNoContent());
        assertEquals(0, aparelhos.tokensDe(familiar.id()).size());

        // token passa de uma conta para outra (mesmo aparelho, outro login); excluir a conta limpa os tokens dela
        mvc.perform(com(put("/api/v1/me/dispositivos"), familiar).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("token", token)))).andExpect(status().isNoContent());
        mvc.perform(com(put("/api/v1/me/dispositivos"), outra).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("token", token)))).andExpect(status().isNoContent());
        assertEquals(0, aparelhos.tokensDe(familiar.id()).size());
        mvc.perform(com(delete("/api/v1/me"), outra).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("senha", SENHA)))).andExpect(status().isNoContent());
        assertEquals(0, aparelhos.tokensDe(outra.id()).size());
    }

    @Test
    void agendadorAvisaOFamiliarDoRemedioEsquecidoUmaVezSoEExigeSegredo() throws Exception {
        java.time.LocalDateTime agora = java.time.LocalDateTime.now();
        org.junit.jupiter.api.Assumptions.assumeTrue(agora.getHour() >= 1, "perto da meia-noite o atraso cairia no dia anterior");
        Sessao idoso = criarConta("IDOSO", "Seu Anselmo");
        Sessao familiar = criarConta("FAMILIAR", "Filha Bia");
        vincular(familiar, idoso);
        mvc.perform(com(put("/api/v1/me/dispositivos"), familiar).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("token", "token-bia")))).andExpect(status().isNoContent());

        // remédio de hoje, marcado para uma hora atrás (passou da tolerância) e ainda não tomado
        mvc.perform(com(post("/api/v1/idosos/" + idoso.id() + "/medicamentos"), idoso).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "Metformina", "diaSemana", agora.getDayOfWeek().name(),
                        "horario", agora.minusHours(1).toLocalTime().withSecond(0).withNano(0).toString(), "tipo", "COMPRIMIDO"))))
                .andExpect(status().isCreated());

        // sem segredo, ou com segredo errado: recusado e nada é enviado; o de verdade é aceito
        aparelhos.avisados.clear();
        mvc.perform(post("/api/v1/interno/atrasos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/interno/atrasos").header("X-Cron-Segredo", "errado")).andExpect(status().isUnauthorized());
        assertEquals(0, aparelhos.avisados.size());
        mvc.perform(post("/api/v1/interno/atrasos").header("X-Cron-Segredo", "segredo-do-agendador-de-teste"))
                .andExpect(status().isOk());
        assertEquals(1, java.util.Collections.frequency(aparelhos.avisados, familiar.id()));

        // a segunda verificação não repete o aviso do mesmo remédio
        int antes = aparelhos.avisados.size();
        mvc.perform(post("/api/v1/interno/atrasos").header("X-Cron-Segredo", "segredo-do-agendador-de-teste"))
                .andExpect(status().isOk());
        assertEquals(antes, aparelhos.avisados.size());
    }

    @Test
    void cadastroExigeAceiteDaPoliticaNaVersaoAtualEORegistra() throws Exception {
        String email = "aceite." + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
        Map<String, Object> base = Map.of("tipo", "IDOSO", "nome", "Sem Aceite", "email", email, "senha", SENHA);
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(corpo(base)))
                .andExpect(status().isBadRequest());
        Map<String, Object> naoAceitou = new java.util.HashMap<>(base);
        naoAceitou.put("aceitouPolitica", false);
        naoAceitou.put("versaoPolitica", "1.0");
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(corpo(naoAceitou)))
                .andExpect(status().isBadRequest());
        Map<String, Object> versaoVelha = new java.util.HashMap<>(base);
        versaoVelha.put("aceitouPolitica", true);
        versaoVelha.put("versaoPolitica", "0.1");
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(corpo(versaoVelha)))
                .andExpect(status().isBadRequest());

        Sessao pessoa = criarConta("IDOSO", "Com Aceite");
        mvc.perform(com(get("/api/v1/me/consentimento"), pessoa)).andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoAtual").value("1.0")).andExpect(jsonPath("$.versaoAceita").value("1.0"));
    }

    @Test
    void quemAindaNaoAceitouAceitaPeloAppESoNaVersaoAtual() throws Exception {
        Sessao pessoa = criarConta("FAMILIAR", "Conta Antiga");
        // simula conta anterior à política: o aceite some
        mvc.perform(com(post("/api/v1/me/consentimento"), pessoa).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("versao", "0.1")))).andExpect(status().isBadRequest());
        mvc.perform(com(post("/api/v1/me/consentimento"), pessoa).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("versao", "1.0")))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/consentimento")).andExpect(status().isUnauthorized());
    }

    @Test
    void exportarTrazSoOsDadosDaPropriaContaEPedeASenha() throws Exception {
        Sessao idoso = criarConta("IDOSO", "Dona Cecilia");
        Sessao familiar = criarConta("FAMILIAR", "Genro Caio");
        vincular(familiar, idoso);
        int id = cadastrarMedicamento(idoso, "Sinvastatina");
        mvc.perform(com(post("/api/v1/medicamentos/" + id + "/tomadas"), idoso)).andExpect(status().isCreated());

        mvc.perform(com(post("/api/v1/me/exportar"), idoso).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(com(post("/api/v1/me/exportar"), idoso).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("senha", "senha-errada-123")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/me/exportar").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("senha", SENHA)))).andExpect(status().isUnauthorized());

        String resposta = mvc.perform(com(post("/api/v1/me/exportar"), idoso).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("senha", SENHA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conta.email").value(idoso.email()))
                .andExpect(jsonPath("$.medicamentos[0].nome").value("Sinvastatina"))
                .andExpect(jsonPath("$.historico.length()").value(1))
                .andExpect(jsonPath("$.familiaresVinculados[0]").value("Genro Caio"))
                .andExpect(jsonPath("$.aceitesDaPolitica[0].versao").value("1.0"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(resposta.toLowerCase().contains("senha"), "a exportação nunca pode conter senha nem hash");

        mvc.perform(com(post("/api/v1/me/exportar"), familiar).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("senha", SENHA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idososQueAcompanha[0]").value("Dona Cecilia"))
                .andExpect(jsonPath("$.medicamentos").doesNotExist());
    }

    @Test
    void politicaDePrivacidadeEPublica() throws Exception {
        mvc.perform(get("/politica-de-privacidade.html")).andExpect(status().isOk());
    }

    @Test
    void catalogoDeAnunciosEstaNoArComImagensQueExistemELinksValidos() throws Exception {
        String texto = mvc.perform(get("/anuncios/anuncios.json")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode anuncios = json.readTree(texto).get("anuncios");
        assertTrue(anuncios.size() >= 1, "precisa haver pelo menos um banner");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonNode a : anuncios) {
            assertTrue(ids.add(a.get("id").asText()), "ids de anúncio não podem repetir");
            String imagem = a.get("imagem").asText();
            // imagens relativas ao catálogo têm de existir de verdade (senão o banner some sem aviso)
            if (!imagem.startsWith("https://")) {
                mvc.perform(get("/anuncios/" + imagem)).andExpect(status().isOk());
            }
            if (a.hasNonNull("link")) {
                String link = a.get("link").asText();
                assertTrue(link.startsWith("https://") || link.matches("^mailto:[^\\s@]+@[^\\s@]+\\.[A-Za-z]{2,}(\\?.*)?$"),
                        "o link do anúncio precisa ser https ou mailto");
            }
        }
    }

    @Test
    void rotaInexistenteRetorna404() throws Exception {
        Sessao ana = criarConta("IDOSO", "Ana Rota");
        mvc.perform(com(get("/api/v1/nao-existe"), ana)).andExpect(status().isNotFound());
    }
}
