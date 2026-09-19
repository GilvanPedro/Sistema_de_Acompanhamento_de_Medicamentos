package br.com.adapter.in.web.erro;

import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import br.com.domain.exception.CredenciaisInvalidasException;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.exception.MedicamentoNaoEncontradoException;
import br.com.domain.exception.UsuarioNaoEncontradoException;

/** Traduz as exceções do domínio em respostas HTTP {@code {"erro": "mensagem"}}, sem detalhes técnicos. */
@RestControllerAdvice
class TratadorDeErros {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler({DadosInvalidosException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> requisicaoInvalida(RuntimeException e) {
        return resposta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> corpoInvalido(RuntimeException e) {
        return resposta(HttpStatus.BAD_REQUEST, "Dados inválidos. Confira os campos enviados.");
    }

    @ExceptionHandler({CredenciaisInvalidasException.class, NaoAutenticadoException.class})
    ResponseEntity<Map<String, String>> naoAutenticado(RuntimeException e) {
        return resposta(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    ResponseEntity<Map<String, String>> proibido(AcessoNegadoException e) {
        return resposta(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler({UsuarioNaoEncontradoException.class, MedicamentoNaoEncontradoException.class, NoSuchElementException.class})
    ResponseEntity<Map<String, String>> naoEncontrado(RuntimeException e) {
        return resposta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MuitasTentativasException.class)
    ResponseEntity<Map<String, String>> muitasTentativas(MuitasTentativasException e) {
        return resposta(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<Map<String, String>> inesperado(RuntimeException e) {
        LOG.error("Erro inesperado na API", e);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir agora. Tente de novo.");
    }

    private static ResponseEntity<Map<String, String>> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("erro", mensagem));
    }
}
