package br.com.adapter.in.web.painel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import br.com.adapter.in.web.auth.LimiteDeTentativas;
import br.com.adapter.in.web.erro.MuitasTentativasException;
import jakarta.servlet.http.HttpServletRequest;

/** Proteção do painel: senha por HTTP Basic (o navegador pede usuário e senha; o usuário pode ser qualquer um). */
public class AcessoAoPainel {

    private final SenhaDoPainel senha;
    private final LimiteDeTentativas limite;

    public AcessoAoPainel(SenhaDoPainel senha, @Qualifier("limitePainel") LimiteDeTentativas limite) {
        this.senha = senha;
        this.limite = limite;
    }

    /** null se pode entrar; senão, a resposta a devolver (404 se desligado, 401 sem senha certa, 429 se errou demais). */
    public ResponseEntity<String> verificar(HttpServletRequest requisicao) {
        if (!senha.configurada()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String ip = requisicao.getRemoteAddr();
        try {
            limite.verificar(ip);
        } catch (MuitasTentativasException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Muitas tentativas. Tente de novo mais tarde.");
        }
        if (senhaEnviada(requisicao).map(this::confere).orElse(false)) {
            limite.limpar(ip);
            return null;
        }
        limite.registrar(ip);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"CuidaMed - painel de anuncios\", charset=\"UTF-8\"")
                .body("Senha necessária.");
    }

    private boolean confere(String enviada) {
        return MessageDigest.isEqual(enviada.getBytes(StandardCharsets.UTF_8), senha.valor().getBytes(StandardCharsets.UTF_8));
    }

    private static java.util.Optional<String> senhaEnviada(HttpServletRequest requisicao) {
        String cabecalho = requisicao.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecalho == null || !cabecalho.regionMatches(true, 0, "Basic ", 0, 6)) {
            return java.util.Optional.empty();
        }
        try {
            String usuarioESenha = new String(Base64.getDecoder().decode(cabecalho.substring(6).trim()), StandardCharsets.UTF_8);
            int dois = usuarioESenha.indexOf(':');
            return dois < 0 ? java.util.Optional.empty() : java.util.Optional.of(usuarioESenha.substring(dois + 1));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
