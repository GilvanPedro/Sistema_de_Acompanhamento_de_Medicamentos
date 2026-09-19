package br.com.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


import org.junit.jupiter.api.Test;

import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.validation.ValidarDadosMedicamento;
import br.com.domain.validation.ValidarDadosUsuario;
import br.com.domain.validation.ValidarEmail;

class ValidacoesTest {

    private final ValidarDadosUsuario usuario = new ValidarDadosUsuario();

    @Test
    void senhaPrecisaTerEntre8CaracteresE72Bytes() {
        assertThrows(DadosInvalidosException.class, () -> usuario.validarSenha("1234567"));
        assertThrows(DadosInvalidosException.class, () -> usuario.validarSenha("        "));
        assertThrows(DadosInvalidosException.class, () -> usuario.validarSenha("x".repeat(73)));
        // 40 letras com acento ocupam 80 bytes em UTF-8: passa dos 72 bytes do BCrypt
        assertThrows(DadosInvalidosException.class, () -> usuario.validarSenha("é".repeat(40)));
        assertDoesNotThrow(() -> usuario.validarSenha("12345678"));
        assertDoesNotThrow(() -> usuario.validarSenha("x".repeat(72)));
    }

    @Test
    void nomesEEmailsRespeitamOTamanhoDasColunasDoBanco() {
        assertDoesNotThrow(() -> usuario.validarNome("N".repeat(150)));
        assertThrows(DadosInvalidosException.class, () -> usuario.validarNome("N".repeat(151)));
        assertThrows(DadosInvalidosException.class, () -> usuario.validarEmail("a".repeat(250) + "@b.com"));
        assertThrows(DadosInvalidosException.class, () -> new ValidarDadosMedicamento().validarNome("R".repeat(151)));
    }

    @Test
    void emailAceitaDominiosLongosERejeitaFormatosErrados() {
        for (String ok : new String[]{"ana@exemplo.com", "ana@exemplo.com.br", "ana@empresa.photography",
                "ana@meusite.consulting", "ana@exemplo.technology", "ana+teste@exemplo.com"}) {
            assertTrue(ValidarEmail.validar(ok), ok);
        }
        for (String ruim : new String[]{"ana", "ana@", "@exemplo.com", "ana@exemplo", "ana@exemplo.c", "ana @exemplo.com", "ana@exemplo.123"}) {
            assertFalse(ValidarEmail.validar(ruim), ruim);
        }
    }
}
