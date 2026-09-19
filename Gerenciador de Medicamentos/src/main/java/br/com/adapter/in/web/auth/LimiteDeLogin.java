package br.com.adapter.in.web.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import br.com.adapter.in.web.erro.MuitasTentativasException;

/** Bloqueia por um tempo quem erra a senha muitas vezes seguidas (dificulta adivinhar senhas). */
@Component
public class LimiteDeLogin {

    private static final int MAXIMO_FALHAS = 5;
    private static final Duration JANELA = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> falhas = new ConcurrentHashMap<>();

    public void verificar(String chave) {
        Deque<Instant> registros = falhas.get(chave);
        if (registros == null) {
            return;
        }
        synchronized (registros) {
            limpar(registros);
            if (registros.size() >= MAXIMO_FALHAS) {
                throw new MuitasTentativasException();
            }
        }
    }

    public void registrarFalha(String chave) {
        Deque<Instant> registros = falhas.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (registros) {
            limpar(registros);
            registros.addLast(Instant.now());
        }
    }

    public void limparFalhas(String chave) {
        falhas.remove(chave);
    }

    private void limpar(Deque<Instant> registros) {
        Instant limite = Instant.now().minus(JANELA);
        while (!registros.isEmpty() && registros.peekFirst().isBefore(limite)) {
            registros.removeFirst();
        }
    }
}
