package br.com.adapter.in.web.idempotencia;

import java.util.Optional;

/**
 * Lembra qual recurso uma chave de idempotência já criou. O app manda uma chave nova para cada remédio cadastrado
 * (mesmo sem internet); se a resposta se perder e ele reenviar, o servidor devolve o remédio que já criou em vez de
 * criar outro igual.
 */
public interface ChavesDeIdempotencia {

    Optional<Integer> buscar(int usuarioId, String chave);

    void salvar(int usuarioId, String chave, int recursoId);
}
