package br.com.adapter.in.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.auth.Acesso;
import br.com.adapter.in.web.dto.Dtos.DispositivoRequest;
import br.com.adapter.in.web.push.Dispositivos;
import br.com.domain.exception.DadosInvalidosException;
import jakarta.servlet.http.HttpServletRequest;

/** O app entrega aqui o token do Firebase do aparelho para a conta logada receber push. */
@RestController
@RequestMapping("/api/v1/me/dispositivos")
class DispositivoController {

    private final Acesso acesso;
    private final Dispositivos dispositivos;

    DispositivoController(Acesso acesso, Dispositivos dispositivos) {
        this.acesso = acesso;
        this.dispositivos = dispositivos;
    }

    @PutMapping
    ResponseEntity<Void> registrar(@RequestBody DispositivoRequest corpo, HttpServletRequest requisicao) {
        dispositivos.registrar(acesso.logado(requisicao).getId(), token(corpo));
        return ResponseEntity.noContent().build();
    }

    /** Sair da conta: o aparelho deixa de receber os avisos dela. */
    @DeleteMapping
    ResponseEntity<Void> remover(@RequestBody DispositivoRequest corpo, HttpServletRequest requisicao) {
        dispositivos.remover(acesso.logado(requisicao).getId(), token(corpo));
        return ResponseEntity.noContent().build();
    }

    private static String token(DispositivoRequest corpo) {
        if (corpo == null || corpo.token() == null || corpo.token().isBlank() || corpo.token().length() > 400) {
            throw new DadosInvalidosException("Token do aparelho inválido.");
        }
        return corpo.token().trim();
    }
}
