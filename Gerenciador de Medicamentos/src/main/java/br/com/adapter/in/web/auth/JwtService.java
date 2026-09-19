package br.com.adapter.in.web.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import br.com.domain.model.Idoso;
import br.com.domain.model.Usuario;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Token de acesso de vida curta (JWT assinado com HMAC-SHA256). */
public class JwtService {

    static final Duration VALIDADE = Duration.ofMinutes(15);
    private static final String EMISSOR = "cuidamed";

    private final SecretKey chave;

    public JwtService(String segredo) {
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Defina JWT_SECRET com pelo menos 32 caracteres (ex.: gere com: openssl rand -base64 48).");
        }
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
    }

    public String gerar(Usuario usuario) {
        Date agora = new Date();
        return Jwts.builder()
                .issuer(EMISSOR)
                .subject(String.valueOf(usuario.getId()))
                .claim("tipo", usuario instanceof Idoso ? "IDOSO" : "FAMILIAR")
                .issuedAt(agora)
                .expiration(new Date(agora.getTime() + VALIDADE.toMillis()))
                .signWith(chave)
                .compact();
    }

    /** Devolve o id do usuário se o token for válido, assinado por nós e não expirado. */
    public Optional<Integer> validar(String token) {
        try {
            String assunto = Jwts.parser()
                    .verifyWith(chave)
                    .requireIssuer(EMISSOR)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(Integer.parseInt(assunto));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long segundosDeValidade() {
        return VALIDADE.toSeconds();
    }
}
