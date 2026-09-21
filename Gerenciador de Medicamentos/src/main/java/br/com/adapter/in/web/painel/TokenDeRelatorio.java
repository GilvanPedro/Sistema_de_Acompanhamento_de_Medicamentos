package br.com.adapter.in.web.painel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Chave do link de relatório de cada banner: um código fixo e impossível de adivinhar, calculado a partir do id do banner
 * e da senha do painel. Quem tem o link de um banner vê só o relatório dele. Trocar a senha do painel invalida os links.
 */
public class TokenDeRelatorio {

    private final SenhaDoPainel senha;

    public TokenDeRelatorio(SenhaDoPainel senha) {
        this.senha = senha;
    }

    public String para(String anuncioId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(("relatorio:" + senha.valor()).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(anuncioId.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar o código do relatório.");
        }
    }

    /** Código escondido nos formulários do painel: sem ele, um site de fora não consegue enviar um formulário em seu nome. */
    public String paraFormularios() {
        return para("formularios-do-painel");
    }

    public boolean formularioConfere(String token) {
        return confere("formularios-do-painel", token);
    }

    public boolean confere(String anuncioId, String token) {
        return senha.configurada() && token != null && MessageDigest.isEqual(
                para(anuncioId).getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }
}
