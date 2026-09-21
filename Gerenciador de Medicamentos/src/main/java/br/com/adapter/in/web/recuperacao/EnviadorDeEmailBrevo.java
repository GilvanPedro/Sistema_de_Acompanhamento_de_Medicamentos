package br.com.adapter.in.web.recuperacao;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Envia pela API HTTP da Brevo (plano grátis: 300 e-mails por dia). Usa HTTPS e não SMTP porque o plano gratuito do
 * Render bloqueia as portas de SMTP.
 */
public class EnviadorDeEmailBrevo implements EnviadorDeEmail {

    private static final URI ENDERECO = URI.create("https://api.brevo.com/v3/smtp/email");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String chave;
    private final String remetente;
    private final String nomeDoRemetente;

    public EnviadorDeEmailBrevo(String chave, String remetente, String nomeDoRemetente) {
        this.chave = chave;
        this.remetente = remetente;
        this.nomeDoRemetente = nomeDoRemetente;
    }

    @Override
    public void enviar(String para, String assunto, String texto) {
        try {
            String corpo = json.writeValueAsString(Map.of(
                    "sender", Map.of("name", nomeDoRemetente, "email", remetente),
                    "to", List.of(Map.of("email", para)),
                    "subject", assunto,
                    "textContent", texto));
            HttpRequest pedido = HttpRequest.newBuilder(ENDERECO)
                    .timeout(Duration.ofSeconds(20))
                    .header("api-key", chave)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo))
                    .build();
            HttpResponse<String> resposta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() / 100 != 2) {
                // só o código: a resposta pode repetir o destinatário, e o corpo do pedido tem o link secreto
                throw new IllegalStateException("A Brevo recusou o e-mail (HTTP " + resposta.statusCode() + ").");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível montar o e-mail.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível falar com a Brevo.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("O envio do e-mail foi interrompido.", e);
        }
    }
}
