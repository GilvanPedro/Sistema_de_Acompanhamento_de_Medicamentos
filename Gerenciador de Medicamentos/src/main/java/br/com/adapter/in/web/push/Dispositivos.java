package br.com.adapter.in.web.push;

import java.util.List;

/** Tokens de push dos aparelhos de cada conta. */
public interface Dispositivos {

    /** Guarda o token para a conta (se o token era de outra conta, passa para esta). */
    void registrar(int usuarioId, String token);

    /** Remove o token, só se for dessa conta (saída da conta no aparelho). */
    void remover(int usuarioId, String token);

    /** Conta excluída: nenhum aparelho recebe mais nada por ela. */
    void removerTodos(int usuarioId);

    /** Remove o token que o FCM disse não existir mais. */
    void descartar(String token);

    List<String> tokensDe(int usuarioId);
}
