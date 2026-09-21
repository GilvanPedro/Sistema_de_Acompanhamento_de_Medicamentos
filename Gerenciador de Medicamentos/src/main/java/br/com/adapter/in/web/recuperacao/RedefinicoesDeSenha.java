package br.com.adapter.in.web.recuperacao;

import java.time.Instant;
import java.util.Optional;

/** Os links de redefinição de senha que já foram enviados: guarda só o hash do token, nunca o token. */
public interface RedefinicoesDeSenha {

    /** Guarda um link novo e invalida os anteriores ainda não usados dessa pessoa (vale sempre o último e-mail). */
    void guardar(int usuarioId, String tokenHash, Instant expiraEm);

    /** De quem é o link, se ele existe, ainda não foi usado e não expirou. Não gasta o link. */
    Optional<Integer> consultar(String tokenHash, Instant agora);

    /** Gasta o link (uma vez só). Devolve de quem era, ou vazio se não valia mais. É atômico: dois pedidos juntos não usam duas vezes. */
    Optional<Integer> consumir(String tokenHash, Instant agora);

    void removerDe(int usuarioId);
}
