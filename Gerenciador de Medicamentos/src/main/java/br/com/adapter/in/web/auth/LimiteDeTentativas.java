package br.com.adapter.in.web.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.adapter.in.web.erro.MuitasTentativasException;

/**
 * Bloqueia por um tempo quem repete uma ação (errar a senha, criar contas...) mais vezes que o permitido dentro de uma janela.
 * Fica em memória, com tamanho máximo: se aparecerem chaves demais, as menos usadas são descartadas, para um ataque com
 * milhares de chaves diferentes não estourar a memória do servidor.
 */
public class LimiteDeTentativas {

    private final int maximo;
    private final Duration janela;
    private final int capacidade;
    private final Map<String, Deque<Instant>> registros;

    public LimiteDeTentativas(int maximo, Duration janela, int capacidade) {
        this.maximo = maximo;
        this.janela = janela;
        this.capacidade = capacidade;
        this.registros = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
                return size() > LimiteDeTentativas.this.capacidade;
            }
        };
    }

    /** Lança {@link MuitasTentativasException} se a chave já atingiu o máximo dentro da janela. */
    public synchronized void verificar(String chave) {
        Deque<Instant> tentativas = registros.get(chave);
        if (tentativas == null) {
            return;
        }
        descartarAntigas(tentativas);
        if (tentativas.isEmpty()) {
            registros.remove(chave);
        } else if (tentativas.size() >= maximo) {
            throw new MuitasTentativasException();
        }
    }

    public synchronized void registrar(String chave) {
        if (registros.size() >= capacidade) {
            descartarExpiradas();
        }
        Deque<Instant> tentativas = registros.computeIfAbsent(chave, k -> new ArrayDeque<>());
        descartarAntigas(tentativas);
        tentativas.addLast(Instant.now());
    }

    public synchronized void limpar(String chave) {
        registros.remove(chave);
    }

    synchronized int tamanho() {
        return registros.size();
    }

    private void descartarExpiradas() {
        for (Iterator<Deque<Instant>> it = registros.values().iterator(); it.hasNext(); ) {
            Deque<Instant> tentativas = it.next();
            descartarAntigas(tentativas);
            if (tentativas.isEmpty()) {
                it.remove();
            }
        }
    }

    private void descartarAntigas(Deque<Instant> tentativas) {
        Instant limite = Instant.now().minus(janela);
        while (!tentativas.isEmpty() && tentativas.peekFirst().isBefore(limite)) {
            tentativas.removeFirst();
        }
    }
}
