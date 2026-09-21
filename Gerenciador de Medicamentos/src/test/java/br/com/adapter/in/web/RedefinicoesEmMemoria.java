package br.com.adapter.in.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.com.adapter.in.web.recuperacao.RedefinicoesDeSenha;

/** {@link RedefinicoesDeSenha} em memória, para os testes. */
class RedefinicoesEmMemoria implements RedefinicoesDeSenha {

    private static final class Registro {
        final int usuarioId;
        final String hash;
        final Instant expiraEm;
        boolean usado;

        Registro(int usuarioId, String hash, Instant expiraEm) {
            this.usuarioId = usuarioId;
            this.hash = hash;
            this.expiraEm = expiraEm;
        }
    }

    private final List<Registro> registros = new ArrayList<>();

    @Override
    public synchronized void guardar(int usuarioId, String tokenHash, Instant expiraEm) {
        registros.stream().filter(r -> r.usuarioId == usuarioId).forEach(r -> r.usado = true);
        registros.add(new Registro(usuarioId, tokenHash, expiraEm));
    }

    @Override
    public synchronized Optional<Integer> consultar(String tokenHash, Instant agora) {
        return registros.stream().filter(r -> r.hash.equals(tokenHash) && !r.usado && r.expiraEm.isAfter(agora))
                .map(r -> r.usuarioId).findFirst();
    }

    @Override
    public synchronized Optional<Integer> consumir(String tokenHash, Instant agora) {
        Optional<Registro> achado = registros.stream().filter(r -> r.hash.equals(tokenHash) && !r.usado && r.expiraEm.isAfter(agora)).findFirst();
        achado.ifPresent(r -> r.usado = true);
        return achado.map(r -> r.usuarioId);
    }

    @Override
    public synchronized void removerDe(int usuarioId) {
        registros.removeIf(r -> r.usuarioId == usuarioId);
    }
}
