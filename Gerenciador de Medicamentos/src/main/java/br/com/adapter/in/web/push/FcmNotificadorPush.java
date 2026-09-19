package br.com.adapter.in.web.push;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;

/**
 * Envia o push pelo Firebase Cloud Messaging (API HTTP v1), autenticando com a conta de serviço.
 * As credenciais vêm da variável FIREBASE_CREDENCIAIS (o JSON da chave), nunca do Git.
 */
public class FcmNotificadorPush implements NotificadorPush {

    private static final Logger LOG = Logger.getLogger(FcmNotificadorPush.class.getName());
    private static final String ESCOPO = "https://www.googleapis.com/auth/firebase.messaging";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Dispositivos dispositivos;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService fila = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fcm-envio");
        t.setDaemon(true);
        return t;
    });

    private final String projeto;
    private final String emailDaConta;
    private final PrivateKey chave;
    private final String urlDoToken;

    private String acesso;
    private Instant acessoExpira = Instant.EPOCH;

    public FcmNotificadorPush(Dispositivos dispositivos, String credenciaisJson) {
        this.dispositivos = dispositivos;
        try {
            JsonNode c = JSON.readTree(credenciaisJson);
            this.projeto = c.get("project_id").asText();
            this.emailDaConta = c.get("client_email").asText();
            this.urlDoToken = c.has("token_uri") ? c.get("token_uri").asText() : "https://oauth2.googleapis.com/token";
            String pem = c.get("private_key").asText()
                    .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            this.chave = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (Exception e) {
            throw new IllegalStateException("FIREBASE_CREDENCIAIS inválida (esperado o JSON da conta de serviço).");
        }
    }

    @Override
    public void avisarNovidade(int usuarioId) {
        List<String> tokens;
        try {
            tokens = dispositivos.tokensDe(usuarioId);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Push: não foi possível listar aparelhos");
            return;
        }
        for (String token : tokens) {
            fila.execute(() -> enviar(token));
        }
    }

    private void enviar(String token) {
        try {
            Map<String, Object> mensagem = Map.of("message", Map.of(
                    "token", token,
                    "data", Map.of("tipo", "novidade"),
                    "android", Map.of("priority", "HIGH")));
            HttpRequest pedido = HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/" + projeto + "/messages:send"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + tokenDeAcesso())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(mensagem)))
                    .build();
            HttpResponse<String> resposta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            int status = resposta.statusCode();
            if (status == 404 || status == 400 && resposta.body().contains("INVALID_ARGUMENT")) {
                dispositivos.descartar(token); // aparelho desinstalou o app ou o token mudou
            } else if (status >= 300) {
                LOG.warning("Push: FCM respondeu " + status);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Push: falha ao enviar (" + e.getClass().getSimpleName() + ")");
        }
    }

    private synchronized String tokenDeAcesso() throws Exception {
        if (acesso != null && Instant.now().isBefore(acessoExpira.minusSeconds(60))) {
            return acesso;
        }
        Instant agora = Instant.now();
        String assertiva = Jwts.builder().issuer(emailDaConta).claim("scope", ESCOPO).audience().add(urlDoToken).and()
                .issuedAt(Date.from(agora)).expiration(Date.from(agora.plusSeconds(3600)))
                .signWith(chave, Jwts.SIG.RS256).compact();
        String corpo = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(assertiva, StandardCharsets.UTF_8);
        HttpResponse<String> resposta = http.send(HttpRequest.newBuilder(URI.create(urlDoToken))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo)).build(), HttpResponse.BodyHandlers.ofString());
        if (resposta.statusCode() != 200) {
            throw new IllegalStateException("Google recusou a conta de serviço (" + resposta.statusCode() + ")");
        }
        JsonNode j = JSON.readTree(resposta.body());
        acesso = j.get("access_token").asText();
        acessoExpira = agora.plusSeconds(j.path("expires_in").asLong(3600));
        return acesso;
    }
}
