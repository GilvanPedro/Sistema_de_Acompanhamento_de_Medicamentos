package br.com.adapter.in.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.auth.LimiteDeLogin;
import br.com.adapter.in.web.auth.TokenService;
import br.com.adapter.in.web.dto.Dtos.LoginRequest;
import br.com.adapter.in.web.dto.Dtos.LoginResponse;
import br.com.adapter.in.web.dto.Dtos.RefreshRequest;
import br.com.adapter.in.web.dto.Dtos.RegistroRequest;
import br.com.adapter.in.web.dto.Dtos.UsuarioDto;
import br.com.application.service.RealizarLoginService;
import br.com.application.service.RegistrarUsuarioService;
import br.com.domain.exception.CredenciaisInvalidasException;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.Usuario;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegistrarUsuarioService registrar;
    private final RealizarLoginService login;
    private final TokenService tokens;
    private final LimiteDeLogin limite;

    AuthController(RegistrarUsuarioService registrar, RealizarLoginService login, TokenService tokens, LimiteDeLogin limite) {
        this.registrar = registrar;
        this.login = login;
        this.tokens = tokens;
        this.limite = limite;
    }

    @PostMapping("/registro")
    ResponseEntity<UsuarioDto> registro(@RequestBody RegistroRequest corpo) {
        if (corpo == null || corpo.tipo() == null) {
            throw new DadosInvalidosException("Informe o tipo da conta: IDOSO ou FAMILIAR.");
        }
        Usuario criado = switch (corpo.tipo().trim().toUpperCase()) {
            case "IDOSO" -> registrar.registrarIdoso(corpo.nome(), corpo.email(), corpo.senha());
            case "FAMILIAR" -> registrar.registrarFamiliar(corpo.nome(), corpo.email(), corpo.senha());
            default -> throw new DadosInvalidosException("Tipo de conta inválido. Use IDOSO ou FAMILIAR.");
        };
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioDto.de(criado));
    }

    @PostMapping("/login")
    LoginResponse login(@RequestBody LoginRequest corpo, HttpServletRequest requisicao) {
        if (corpo == null || corpo.email() == null || corpo.email().isBlank() || corpo.senha() == null) {
            throw new CredenciaisInvalidasException();
        }
        String chave = corpo.email().trim().toLowerCase() + "|" + requisicao.getRemoteAddr();
        limite.verificar(chave);
        try {
            Usuario usuario = login.realizarLogin(corpo.email().trim(), corpo.senha());
            limite.limparFalhas(chave);
            return LoginResponse.de(tokens.emitir(usuario), usuario);
        } catch (CredenciaisInvalidasException e) {
            limite.registrarFalha(chave);
            throw e;
        }
    }

    @PostMapping("/renovar")
    TokenService.Tokens renovar(@RequestBody RefreshRequest corpo) {
        exigirToken(corpo);
        return tokens.renovar(corpo.refreshToken());
    }

    @PostMapping("/sair")
    ResponseEntity<Void> sair(@RequestBody RefreshRequest corpo) {
        exigirToken(corpo);
        tokens.revogar(corpo.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private static void exigirToken(RefreshRequest corpo) {
        if (corpo == null || corpo.refreshToken() == null || corpo.refreshToken().isBlank()) {
            throw new DadosInvalidosException("Informe o refreshToken.");
        }
    }
}
