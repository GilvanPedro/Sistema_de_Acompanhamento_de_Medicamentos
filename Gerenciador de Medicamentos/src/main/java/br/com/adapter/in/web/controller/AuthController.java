package br.com.adapter.in.web.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.auth.LimiteDeTentativas;
import br.com.adapter.in.web.auth.TokenService;
import br.com.adapter.in.web.dto.Dtos.LoginRequest;
import br.com.adapter.in.web.dto.Dtos.LoginResponse;
import br.com.adapter.in.web.dto.Dtos.RefreshRequest;
import br.com.adapter.in.web.dto.Dtos.RegistroRequest;
import br.com.adapter.in.web.privacidade.Consentimentos;
import br.com.adapter.in.web.privacidade.PoliticaDePrivacidade;
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
    private final LimiteDeTentativas limitePorConta;
    private final LimiteDeTentativas limitePorIp;
    private final LimiteDeTentativas limiteCadastro;
    private final Consentimentos consentimentos;

    AuthController(RegistrarUsuarioService registrar, RealizarLoginService login, TokenService tokens,
                   @Qualifier("limiteLoginPorConta") LimiteDeTentativas limitePorConta,
                   @Qualifier("limiteLoginPorIp") LimiteDeTentativas limitePorIp,
                   @Qualifier("limiteCadastro") LimiteDeTentativas limiteCadastro, Consentimentos consentimentos) {
        this.registrar = registrar;
        this.login = login;
        this.tokens = tokens;
        this.limitePorConta = limitePorConta;
        this.limitePorIp = limitePorIp;
        this.limiteCadastro = limiteCadastro;
        this.consentimentos = consentimentos;
    }

    @PostMapping("/registro")
    ResponseEntity<UsuarioDto> registro(@RequestBody RegistroRequest corpo, HttpServletRequest requisicao) {
        // Conta cada tentativa, deu certo ou não: o limite é sobre criar contas em massa.
        String ip = requisicao.getRemoteAddr();
        limiteCadastro.verificar(ip);
        limiteCadastro.registrar(ip);
        if (corpo == null || corpo.tipo() == null) {
            throw new DadosInvalidosException("Informe o tipo da conta: IDOSO ou FAMILIAR.");
        }
        if (!Boolean.TRUE.equals(corpo.aceitouPolitica())) {
            throw new DadosInvalidosException("Para criar a conta, aceite a política de privacidade.");
        }
        if (!PoliticaDePrivacidade.VERSAO_ATUAL.equals(corpo.versaoPolitica())) {
            throw new DadosInvalidosException("A política de privacidade foi atualizada. Atualize o app e tente de novo.");
        }
        Usuario criado = switch (corpo.tipo().trim().toUpperCase()) {
            case "IDOSO" -> registrar.registrarIdoso(corpo.nome(), corpo.email(), corpo.senha());
            case "FAMILIAR" -> registrar.registrarFamiliar(corpo.nome(), corpo.email(), corpo.senha());
            default -> throw new DadosInvalidosException("Tipo de conta inválido. Use IDOSO ou FAMILIAR.");
        };
        consentimentos.registrar(criado.getId(), PoliticaDePrivacidade.VERSAO_ATUAL);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioDto.de(criado));
    }

    @PostMapping("/login")
    LoginResponse login(@RequestBody LoginRequest corpo, HttpServletRequest requisicao) {
        if (corpo == null || corpo.email() == null || corpo.email().isBlank() || corpo.senha() == null) {
            throw new CredenciaisInvalidasException();
        }
        String ip = requisicao.getRemoteAddr();
        String chaveConta = corpo.email().trim().toLowerCase() + "|" + ip;
        limitePorConta.verificar(chaveConta);
        limitePorIp.verificar(ip);
        try {
            Usuario usuario = login.realizarLogin(corpo.email().trim(), corpo.senha());
            limitePorConta.limpar(chaveConta);
            return LoginResponse.de(tokens.emitir(usuario), usuario);
        } catch (CredenciaisInvalidasException e) {
            limitePorConta.registrar(chaveConta);
            limitePorIp.registrar(ip);
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
