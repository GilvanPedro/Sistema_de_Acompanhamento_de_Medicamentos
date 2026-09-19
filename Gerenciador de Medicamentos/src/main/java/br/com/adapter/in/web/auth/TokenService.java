package br.com.adapter.in.web.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.NoSuchElementException;

import br.com.adapter.in.web.erro.NaoAutenticadoException;
import br.com.domain.model.Usuario;
import br.com.domain.port.out.SalvarUsuarioPort;

/**
 * Emite o par de tokens: acesso (JWT, ~15 min) e renovação (aleatório, ~30 dias). O token de renovação é
 * trocado a cada uso; se um já usado aparecer de novo, todas as sessões do usuário são revogadas.
 */
public class TokenService {

    private static final Duration VALIDADE_RENOVACAO = Duration.ofDays(30);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final JwtService jwt;
    private final RefreshTokenStore store;
    private final SalvarUsuarioPort usuarios;

    public TokenService(JwtService jwt, RefreshTokenStore store, SalvarUsuarioPort usuarios) {
        this.jwt = jwt;
        this.store = store;
        this.usuarios = usuarios;
    }

    public Tokens emitir(Usuario usuario) {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.salvar(hash(refresh), usuario.getId(), Instant.now().plus(VALIDADE_RENOVACAO));
        return new Tokens(jwt.gerar(usuario), refresh, jwt.segundosDeValidade());
    }

    public Tokens renovar(String refreshToken) {
        String hash = hash(refreshToken);
        RefreshTokenStore.RefreshRegistro registro = store.buscar(hash).orElseThrow(NaoAutenticadoException::new);

        if (registro.revogado()) {
            store.revogarTodos(registro.usuarioId());
            throw new NaoAutenticadoException();
        }
        if (registro.expiraEm().isBefore(Instant.now()) || !store.revogar(hash)) {
            throw new NaoAutenticadoException();
        }

        try {
            return emitir(usuarios.buscarPorId(registro.usuarioId()));
        } catch (NoSuchElementException e) {
            throw new NaoAutenticadoException();
        }
    }

    public void revogar(String refreshToken) {
        store.revogar(hash(refreshToken));
    }

    public void revogarTodos(int usuarioId) {
        store.revogarTodos(usuarioId);
    }

    private static String hash(String token) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Tokens(String accessToken, String refreshToken, long expiresIn) {
    }
}
