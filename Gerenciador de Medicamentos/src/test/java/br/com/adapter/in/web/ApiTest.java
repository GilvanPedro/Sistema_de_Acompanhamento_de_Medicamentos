package br.com.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private static final String SENHA = "senha-forte-123";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    /** Cria uma conta com e-mail único (cada teste usa contas próprias) e entra; devolve o login. */
    private Sessao criarConta(String tipo, String nome) throws Exception {
        String email = nome.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tipo", tipo, "nome", nome, "email", email, "senha", SENHA))))
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
                        .content(corpo(Map.of("tipo", "IDOSO", "nome", "Outra", "email", ana.email(), "senha", SENHA))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("tipo", "ADMIN", "nome", "X", "email", "x@x.com", "senha", SENHA))))
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
                            .content(corpo(Map.of("tipo", "IDOSO", "nome", "Fraca", "email", "fraca@teste.com", "senha", senha))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void nomeGrandeDemaisERecusadoEEmailComDominioLongoEAceito() throws Exception {
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("tipo", "IDOSO", "nome", "N".repeat(151), "email", "nome.longo@teste.com", "senha", SENHA))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("tipo", "FAMILIAR", "nome", "Dominio Longo",
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
    void rotaInexistenteRetorna404() throws Exception {
        Sessao ana = criarConta("IDOSO", "Ana Rota");
        mvc.perform(com(get("/api/v1/nao-existe"), ana)).andExpect(status().isNotFound());
    }
}
