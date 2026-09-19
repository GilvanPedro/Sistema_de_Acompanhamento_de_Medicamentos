package br.com.adapter.in.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.auth.LimiteDeTentativas;
import br.com.adapter.in.web.erro.NaoAutenticadoException;
import br.com.adapter.in.web.push.SegredoDoAgendador;
import br.com.adapter.in.web.push.VerificadorDeAtrasos;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Rota chamada por um agendador externo (ex.: cron-job.org) a cada poucos minutos: manda o push de "remédio esquecido".
 * Não usa login de pessoa: exige o segredo no cabeçalho X-Cron-Segredo. Sem segredo configurado, responde 404.
 */
@RestController
@RequestMapping("/api/v1/interno")
class AgendadorController {

    private final SegredoDoAgendador segredo;
    private final VerificadorDeAtrasos atrasos;
    private final LimiteDeTentativas limiteDeErros;

    AgendadorController(SegredoDoAgendador segredo, VerificadorDeAtrasos atrasos,
                        @Qualifier("limiteAgendador") LimiteDeTentativas limiteDeErros) {
        this.segredo = segredo;
        this.atrasos = atrasos;
        this.limiteDeErros = limiteDeErros;
    }

    @PostMapping("/atrasos")
    Map<String, Object> verificarAtrasos(@RequestHeader(value = "X-Cron-Segredo", required = false) String enviado,
                                         HttpServletRequest requisicao) {
        if (!segredo.configurado()) {
            throw new java.util.NoSuchElementException(); // rota desligada: responde 404 como se não existisse
        }
        String ip = requisicao.getRemoteAddr();
        limiteDeErros.verificar(ip);
        boolean confere = enviado != null && MessageDigest.isEqual(
                enviado.getBytes(StandardCharsets.UTF_8), segredo.valor().getBytes(StandardCharsets.UTF_8));
        if (!confere) {
            limiteDeErros.registrar(ip);
            throw new NaoAutenticadoException();
        }
        limiteDeErros.limpar(ip);
        return Map.of("avisados", atrasos.verificar());
    }
}
