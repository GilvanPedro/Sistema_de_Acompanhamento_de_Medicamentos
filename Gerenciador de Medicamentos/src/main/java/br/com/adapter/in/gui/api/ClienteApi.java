package br.com.adapter.in.gui.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import br.com.domain.model.TipoMedicamento;
import br.com.domain.model.TipoNotificacao;

/**
 * A interface gráfica fala com o CuidaMed só por aqui: a mesma API REST que o app Android usa. Não há acesso ao banco
 * nem às regras de negócio: quem decide tudo (senha, vínculo, quem vê o quê) é o servidor.
 * As chamadas são bloqueantes: quem chama da tela deve rodá-las fora da thread da interface.
 */
public class ClienteApi {

    /** Versão da política de privacidade que esta tela mostra (a mesma do servidor; se mudar lá, muda aqui). */
    public static final String VERSAO_DA_POLITICA = "1.0";

    private final String raiz;
    private final URI base;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private volatile String acesso;
    private volatile String renovacao;

    /** {@code urlDaApi} é o endereço da API, por exemplo {@code https://servidor/api/v1}. */
    public ClienteApi(String urlDaApi) {
        String limpa = urlDaApi.trim().replaceAll("/+$", "");
        this.base = URI.create(limpa + "/");
        this.raiz = limpa.replaceAll("/api/v1$", "");
    }

    /** Endereço da página pública da política de privacidade. */
    public String urlDaPolitica() {
        return raiz + "/politica-de-privacidade.html";
    }

    public boolean logado() {
        return acesso != null;
    }

    // ---- entrar e sair

    /** Acorda o servidor gratuito (que hiberna); falhas não importam. */
    public void acordarServidor() {
        try {
            enviar("GET", "saude", null, false);
        } catch (RuntimeException e) {
            // só um aquecimento
        }
    }

    public Conta login(String email, String senha) {
        JsonNode r = chamar("POST", "auth/login", Map.of("email", email, "senha", senha), false);
        acesso = r.get("accessToken").asText();
        renovacao = r.get("refreshToken").asText();
        return conta(r.get("usuario"));
    }

    /** Pede o link para criar uma senha nova (vai por e-mail). Devolve a mensagem da API, igual exista a conta ou não. */
    public String esqueciSenha(String email) {
        return chamar("POST", "auth/esqueci-senha", Map.of("email", email), false).get("mensagem").asText();
    }

    /** Cria a conta (com o aceite da política, obrigatório) e já entra. */
    public Conta cadastrar(String tipo, String nome, String email, String senha) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("tipo", tipo);
        corpo.put("nome", nome);
        corpo.put("email", email);
        corpo.put("senha", senha);
        corpo.put("aceitouPolitica", true);
        corpo.put("versaoPolitica", VERSAO_DA_POLITICA);
        chamar("POST", "auth/registro", corpo, false);
        return login(email, senha);
    }

    /** Sai da conta: o servidor deixa de aceitar o token de renovação. */
    public void sair() {
        String token = renovacao;
        acesso = null;
        renovacao = null;
        if (token != null) {
            try {
                chamar("POST", "auth/sair", Map.of("refreshToken", token), false);
            } catch (RuntimeException e) {
                // melhor esforço: o token expira sozinho
            }
        }
    }

    // ---- a própria conta

    public Conta eu() {
        return conta(chamar("GET", "me", null, true));
    }

    /** Só o que não for nulo muda. Trocar e-mail ou senha exige a senha atual. */
    public Conta editarPerfil(String nome, String email, String novaSenha, String senhaAtual) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        if (nome != null) {
            corpo.put("nome", nome);
        }
        if (email != null) {
            corpo.put("email", email);
        }
        if (novaSenha != null) {
            corpo.put("senha", novaSenha);
        }
        if (senhaAtual != null) {
            corpo.put("senhaAtual", senhaAtual);
        }
        return conta(chamar("PATCH", "me", corpo, true));
    }

    public void excluirConta(String senha) {
        chamar("DELETE", "me", Map.of("senha", senha), true);
        acesso = null;
        renovacao = null;
    }

    public List<Conta> meusIdosos() {
        return contas(chamar("GET", "me/idosos", null, true));
    }

    public List<Conta> meusFamiliares() {
        return contas(chamar("GET", "me/familiares", null, true));
    }

    // ---- remédios, tomadas e avisos

    public List<Remedio> remedios(int idosoId) {
        return remedios(chamar("GET", "idosos/" + idosoId + "/medicamentos", null, true));
    }

    public Remedio cadastrarRemedio(int idosoId, String nome, DayOfWeek dia, LocalTime horario, TipoMedicamento tipo) {
        return remedio(chamar("POST", "idosos/" + idosoId + "/medicamentos", corpoDoRemedio(nome, dia, horario, tipo), true));
    }

    public Remedio editarRemedio(int id, String nome, DayOfWeek dia, LocalTime horario, TipoMedicamento tipo) {
        return remedio(chamar("PATCH", "medicamentos/" + id, corpoDoRemedio(nome, dia, horario, tipo), true));
    }

    public void excluirRemedio(int id) {
        chamar("DELETE", "medicamentos/" + id, null, true);
    }

    /** O idoso marca que tomou o remédio agora. */
    public Tomada registrarTomada(int remedioId) {
        return tomada(chamar("POST", "medicamentos/" + remedioId + "/tomadas", null, true));
    }

    public List<Tomada> historico(int idosoId) {
        List<Tomada> lista = new ArrayList<>();
        chamar("GET", "idosos/" + idosoId + "/historico", null, true).forEach(n -> lista.add(tomada(n)));
        return lista;
    }

    public List<Aviso> avisos(int idosoId) {
        List<Aviso> lista = new ArrayList<>();
        chamar("GET", "idosos/" + idosoId + "/notificacoes", null, true).forEach(n ->
                lista.add(new Aviso(TipoNotificacao.valueOf(n.get("tipo").asText()), remedio(n.get("medicamento")))));
        return lista;
    }

    // ---- vínculos

    /** Devolve a mensagem do servidor (a resposta é sempre a mesma, exista a conta ou não). */
    public String pedirVinculo(String emailDoIdoso) {
        return chamar("POST", "vinculos/pedidos", Map.of("email", emailDoIdoso), true).path("mensagem").asText();
    }

    public List<Pedido> pedidosRecebidos() {
        List<Pedido> lista = new ArrayList<>();
        chamar("GET", "vinculos/pedidos", null, true).forEach(n ->
                lista.add(new Pedido(conta(n.get("familiar")), LocalDateTime.parse(n.get("solicitadoEm").asText()))));
        return lista;
    }

    public void aceitarPedido(int familiarId) {
        chamar("POST", "vinculos/pedidos/" + familiarId + "/aceitar", null, true);
    }

    public void recusarPedido(int familiarId) {
        chamar("POST", "vinculos/pedidos/" + familiarId + "/recusar", null, true);
    }

    public String adicionarFamiliar(String emailDoFamiliar) {
        return chamar("POST", "me/familiares", Map.of("email", emailDoFamiliar), true).path("mensagem").asText();
    }

    public void removerFamiliar(int familiarId) {
        chamar("DELETE", "me/familiares/" + familiarId, null, true);
    }

    // ---- privacidade (LGPD)

    /** true se a conta já aceitou a versão atual da política. */
    public boolean politicaAceita() {
        JsonNode r = chamar("GET", "me/consentimento", null, true);
        return r.hasNonNull("versaoAceita") && r.get("versaoAceita").asText().equals(r.get("versaoAtual").asText());
    }

    public void aceitarPolitica() {
        chamar("POST", "me/consentimento", Map.of("versao", VERSAO_DA_POLITICA), true);
    }

    /** Cópia dos dados da conta, como texto JSON legível. Exige a senha. */
    public String exportarDados(String senha) {
        try {
            return json.writeValueAsString(chamar("POST", "me/exportar", Map.of("senha", senha), true));
        } catch (IOException e) {
            throw new ApiException(0, "Não foi possível montar a cópia dos dados.");
        }
    }

    // ---- por dentro

    private static Map<String, Object> corpoDoRemedio(String nome, DayOfWeek dia, LocalTime horario, TipoMedicamento tipo) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("nome", nome);
        corpo.put("diaSemana", dia.name());
        corpo.put("horario", horario.toString());
        corpo.put("tipo", tipo.name());
        return corpo;
    }

    private static Conta conta(JsonNode n) {
        return new Conta(n.get("id").asInt(), n.get("tipo").asText(), n.get("nome").asText(), n.get("email").asText());
    }

    private static List<Conta> contas(JsonNode lista) {
        List<Conta> contas = new ArrayList<>();
        lista.forEach(n -> contas.add(conta(n)));
        return contas;
    }

    private static Remedio remedio(JsonNode n) {
        return new Remedio(n.get("id").asInt(), n.get("idosoId").asInt(), n.get("nome").asText(),
                LocalTime.parse(n.get("horario").asText()), DayOfWeek.valueOf(n.get("diaSemana").asText()),
                TipoMedicamento.valueOf(n.get("tipo").asText()));
    }

    private static List<Remedio> remedios(JsonNode lista) {
        List<Remedio> remedios = new ArrayList<>();
        lista.forEach(n -> remedios.add(remedio(n)));
        return remedios;
    }

    private static Tomada tomada(JsonNode n) {
        return new Tomada(n.get("id").asInt(), n.get("medicamentoId").asInt(), n.get("medicamentoNome").asText(),
                LocalDateTime.parse(n.get("dataHora").asText()), n.get("foiTomado").asBoolean());
    }

    /** Chama a API; se o token de acesso venceu, renova uma vez e tenta de novo. */
    private JsonNode chamar(String metodo, String caminho, Object corpo, boolean autenticado) {
        String usado = acesso;
        HttpResponse<String> r = enviar(metodo, caminho, corpo, autenticado);
        if (r.statusCode() == 401 && autenticado && renovacao != null && renovar(usado)) {
            r = enviar(metodo, caminho, corpo, true);
        }
        if (r.statusCode() >= 200 && r.statusCode() < 300) {
            try {
                return r.body() == null || r.body().isBlank() ? json.nullNode() : json.readTree(r.body());
            } catch (IOException e) {
                throw new ApiException(0, "O servidor respondeu algo inesperado. Tente de novo.");
            }
        }
        throw new ApiException(r.statusCode(), mensagemDoErro(r));
    }

    /** Renova o token; só uma thread faz isso por vez (o token de renovação vale uma vez só). */
    private synchronized boolean renovar(String tokenQueFalhou) {
        if (acesso != null && !acesso.equals(tokenQueFalhou)) {
            return true; // outra chamada já renovou
        }
        try {
            HttpResponse<String> r = enviar("POST", "auth/renovar", Map.of("refreshToken", renovacao), false);
            if (r.statusCode() != 200) {
                acesso = null;
                renovacao = null;
                return false;
            }
            JsonNode t = json.readTree(r.body());
            acesso = t.get("accessToken").asText();
            renovacao = t.get("refreshToken").asText();
            return true;
        } catch (IOException | ApiException e) {
            return false;
        }
    }

    private HttpResponse<String> enviar(String metodo, String caminho, Object corpo, boolean autenticado) {
        try {
            HttpRequest.Builder pedido = HttpRequest.newBuilder(base.resolve(caminho)).timeout(Duration.ofSeconds(70));
            if (autenticado && acesso != null) {
                pedido.header("Authorization", "Bearer " + acesso);
            }
            HttpRequest.BodyPublisher publicador = corpo == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo));
            if (corpo != null) {
                pedido.header("Content-Type", "application/json");
            }
            return http.send(pedido.method(metodo, publicador).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ApiException(0, "Sem conexão com o CuidaMed. Confira a internet e tente de novo.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "A operação foi interrompida.");
        }
    }

    private String mensagemDoErro(HttpResponse<String> r) {
        try {
            JsonNode n = json.readTree(r.body());
            if (n != null && n.hasNonNull("erro")) {
                return n.get("erro").asText();
            }
        } catch (IOException | RuntimeException e) {
            // corpo sem JSON: cai na mensagem geral
        }
        return r.statusCode() >= 500
                ? "O servidor está com problema agora. Tente de novo em instantes."
                : "Não foi possível concluir. Tente de novo.";
    }
}
