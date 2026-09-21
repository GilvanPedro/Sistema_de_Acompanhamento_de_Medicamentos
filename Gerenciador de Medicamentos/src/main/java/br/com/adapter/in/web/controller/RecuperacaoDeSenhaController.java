package br.com.adapter.in.web.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.auth.LimiteDeTentativas;
import br.com.adapter.in.web.dto.Dtos.EmailRequest;
import br.com.adapter.in.web.dto.Dtos.MensagemDto;
import br.com.adapter.in.web.erro.MuitasTentativasException;
import br.com.adapter.in.web.recuperacao.PaginasDeRecuperacao;
import br.com.adapter.in.web.recuperacao.RecuperacaoDeSenha;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.validation.ValidarEmail;
import jakarta.servlet.http.HttpServletRequest;

/**
 * "Esqueci minha senha" (ADR-0063): o app pede o link pela API; a pessoa recebe o e-mail e escolhe a nova senha numa
 * página comum do navegador (funciona em qualquer aparelho, sem depender do app instalado).
 */
@RestController
class RecuperacaoDeSenhaController {

    static final String MENSAGEM = "Se este e-mail tiver uma conta, enviamos um link para criar uma nova senha. "
            + "Ele vale por 30 minutos. Olhe também a caixa de spam.";

    private final RecuperacaoDeSenha recuperacao;
    private final LimiteDeTentativas limitePorIp;
    private final LimiteDeTentativas limitePorEmail;
    private final LimiteDeTentativas limiteRedefinicao;

    RecuperacaoDeSenhaController(RecuperacaoDeSenha recuperacao,
                                 @Qualifier("limiteEsqueciPorIp") LimiteDeTentativas limitePorIp,
                                 @Qualifier("limiteEsqueciPorEmail") LimiteDeTentativas limitePorEmail,
                                 @Qualifier("limiteRedefinicao") LimiteDeTentativas limiteRedefinicao) {
        this.recuperacao = recuperacao;
        this.limitePorIp = limitePorIp;
        this.limitePorEmail = limitePorEmail;
        this.limiteRedefinicao = limiteRedefinicao;
    }

    /** Sempre responde igual, exista a conta ou não, para não revelar quais e-mails estão cadastrados. */
    @PostMapping("/api/v1/auth/esqueci-senha")
    ResponseEntity<MensagemDto> esqueciASenha(@RequestBody EmailRequest corpo, HttpServletRequest requisicao) {
        if (corpo == null || !ValidarEmail.validar(corpo.email() == null ? null : corpo.email().trim())) {
            throw new DadosInvalidosException("Informe um e-mail válido.");
        }
        String ip = requisicao.getRemoteAddr();
        String email = corpo.email().trim().toLowerCase();
        limitePorIp.verificar(ip);
        limitePorEmail.verificar(email);
        // conta cada pedido, tenha o e-mail conta ou não: o limite é contra encher a caixa de alguém e contra tentativas em massa
        limitePorIp.registrar(ip);
        limitePorEmail.registrar(email);
        recuperacao.solicitar(email);
        return ResponseEntity.accepted().body(new MensagemDto(MENSAGEM));
    }

    @GetMapping("/redefinir-senha")
    ResponseEntity<String> formulario(@RequestParam(value = "token", required = false) String token) {
        if (!recuperacao.linkValido(token)) {
            return pagina(HttpStatus.BAD_REQUEST, PaginasDeRecuperacao.linkInvalido("Este link não vale mais ou está incompleto."));
        }
        return pagina(HttpStatus.OK, PaginasDeRecuperacao.formulario(token, null));
    }

    @PostMapping(value = "/redefinir-senha", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<String> redefinir(@RequestParam(value = "token", required = false) String token,
                                     @RequestParam(value = "senha", required = false) String senha,
                                     @RequestParam(value = "confirmar", required = false) String confirmar,
                                     HttpServletRequest requisicao) {
        String ip = requisicao.getRemoteAddr();
        try {
            limiteRedefinicao.verificar(ip);
            limiteRedefinicao.registrar(ip);
        } catch (MuitasTentativasException e) {
            return pagina(HttpStatus.TOO_MANY_REQUESTS, PaginasDeRecuperacao.linkInvalido(e.getMessage()));
        }
        try {
            recuperacao.redefinir(token, senha, confirmar);
            return pagina(HttpStatus.OK, PaginasDeRecuperacao.pronto());
        } catch (DadosInvalidosException e) {
            // senha fraca ou duas senhas diferentes: o link continua valendo, e a pessoa tenta de novo na mesma página
            if (recuperacao.linkValido(token)) {
                return pagina(HttpStatus.BAD_REQUEST, PaginasDeRecuperacao.formulario(token, e.getMessage()));
            }
            return pagina(HttpStatus.BAD_REQUEST, PaginasDeRecuperacao.linkInvalido(e.getMessage()));
        }
    }

    /** Páginas com o token na URL: nada de cache, nada de enviar a URL adiante (Referer) e nada de abrir dentro de outro site. */
    private static ResponseEntity<String> pagina(HttpStatus status, String html) {
        return ResponseEntity.status(status)
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Frame-Options", "DENY")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'")
                .body(html);
    }
}
