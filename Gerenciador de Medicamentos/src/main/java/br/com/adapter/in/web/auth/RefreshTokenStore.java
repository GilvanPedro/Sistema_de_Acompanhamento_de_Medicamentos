package br.com.adapter.in.web.auth;

import java.time.Instant;
import java.util.Optional;

/** Guarda só o hash dos tokens de renovação; o token em si existe apenas no aparelho do usuário. */
public interface RefreshTokenStore {

    void salvar(String hash, int usuarioId, Instant expiraEm);

    Optional<RefreshRegistro> buscar(String hash);

    /** Revoga o token; devolve true só se ele estava ativo (operação atômica, evita uso simultâneo). */
    boolean revogar(String hash);

    void revogarTodos(int usuarioId);

    record RefreshRegistro(int usuarioId, Instant expiraEm, boolean revogado) {
    }
}
