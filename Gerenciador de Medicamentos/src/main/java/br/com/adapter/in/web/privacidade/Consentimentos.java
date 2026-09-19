package br.com.adapter.in.web.privacidade;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Aceites da política de privacidade, por pessoa. */
public interface Consentimentos {

    record Aceite(String versao, OffsetDateTime aceitoEm) { }

    /** Guarda que a pessoa aceitou esta versão (aceitar de novo a mesma versão não muda nada). */
    void registrar(int usuarioId, String versao);

    /** A versão aceita mais recentemente, se houver. */
    Optional<String> versaoAceita(int usuarioId);

    List<Aceite> todos(int usuarioId);

    void removerDe(int usuarioId);
}
