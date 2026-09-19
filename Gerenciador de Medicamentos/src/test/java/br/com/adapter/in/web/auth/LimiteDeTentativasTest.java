package br.com.adapter.in.web.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import br.com.adapter.in.web.erro.MuitasTentativasException;

class LimiteDeTentativasTest {

    @Test
    void bloqueiaDepoisDoMaximoEDepoisDeLimparLibera() {
        LimiteDeTentativas limite = new LimiteDeTentativas(3, Duration.ofMinutes(15), 100);
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> limite.verificar("a"));
            limite.registrar("a");
        }
        assertThrows(MuitasTentativasException.class, () -> limite.verificar("a"));
        assertDoesNotThrow(() -> limite.verificar("b"));

        limite.limpar("a");
        assertDoesNotThrow(() -> limite.verificar("a"));
    }

    @Test
    void asTentativasExpiramDepoisDaJanela() throws InterruptedException {
        LimiteDeTentativas limite = new LimiteDeTentativas(1, Duration.ofMillis(150), 100);
        limite.registrar("a");
        assertThrows(MuitasTentativasException.class, () -> limite.verificar("a"));
        Thread.sleep(250);
        assertDoesNotThrow(() -> limite.verificar("a"));
    }

    @Test
    void oMapaNaoCrescePassandoDaCapacidade() {
        LimiteDeTentativas limite = new LimiteDeTentativas(5, Duration.ofMinutes(15), 50);
        for (int i = 0; i < 1000; i++) {
            limite.registrar("email" + i + "@teste.com|1.2.3.4");
        }
        assertTrue(limite.tamanho() <= 50, "tamanho = " + limite.tamanho());
    }
}
