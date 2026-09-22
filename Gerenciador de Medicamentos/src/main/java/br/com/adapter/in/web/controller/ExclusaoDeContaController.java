package br.com.adapter.in.web.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.auth.LimiteDeTentativas;
import br.com.adapter.in.web.erro.MuitasTentativasException;
import br.com.adapter.in.web.exclusao.PaginasDeExclusao;
import br.com.adapter.in.web.exclusao.SolicitacaoDeExclusaoDeConta;
import br.com.adapter.in.web.exclusao.SolicitacaoDeExclusaoDeConta.Confirmacao;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.validation.ValidarEmail;
import jakarta.servlet.http.HttpServletRequest;

/**
 * "Excluir minha conta" (ADR-0064): uma página pública, fora do app, para quem perdeu o acesso à conta ou prefere não
 * instalar o aplicativo (Google Play exige esse caminho para todo app que permite criar conta).
 */
@RestController
class ExclusaoDeContaController {

    private final SolicitacaoDeExclusaoDeConta servico;
    private final LimiteDeTentativas limitePorIp;
    private final LimiteDeTentativas limitePorEmail;
    private final LimiteDeTentativas limiteConfirmacao;

    ExclusaoDeContaController(SolicitacaoDeExclusaoDeConta servico,
                              @Qualifier("limiteExclusaoPorIp") LimiteDeTentativas limitePorIp,
                              @Qualifier("limiteExclusaoPorEmail") LimiteDeTentativas limitePorEmail,
                              @Qualifier("limiteExclusaoConfirmacao") LimiteDeTentativas limiteConfirmacao) {
        this.servico = servico;
        this.limitePorIp = limitePorIp;
        this.limitePorEmail = limitePorEmail;
        this.limiteConfirmacao = limiteConfirmacao;
    }

    /** Sem token: o formulário para pedir o link. Com token: a confirmação (nome e e-mail de quem seria excluído). */
    @GetMapping("/excluir-conta")
    ResponseEntity<String> pagina(@RequestParam(value = "token", required = false) String token) {
        if (token == null || token.isBlank()) {
            return pagina(HttpStatus.OK, PaginasDeExclusao.formularioDeEmail(null));
        }
        Optional<Confirmacao> dados = servico.dadosParaConfirmar(token);
        if (dados.isEmpty()) {
            return pagina(HttpStatus.BAD_REQUEST, PaginasDeExclusao.linkInvalido("Este link não vale mais ou está incompleto."));
        }
        return pagina(HttpStatus.OK, PaginasDeExclusao.confirmar(token, dados.get().nome(), dados.get().email(), null));
    }

    @PostMapping(value = "/excluir-conta/solicitar", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<String> solicitar(@RequestParam(value = "email", required = false) String emailDigitado, HttpServletRequest requisicao) {
        String email = emailDigitado == null ? null : emailDigitado.trim();
        if (!ValidarEmail.validar(email)) {
            return pagina(HttpStatus.BAD_REQUEST, PaginasDeExclusao.formularioDeEmail("Informe um e-mail válido."));
        }
        String ip = requisicao.getRemoteAddr();
        String chave = email.toLowerCase();
        try {
            limitePorIp.verificar(ip);
            limitePorEmail.verificar(chave);
        } catch (MuitasTentativasException e) {
            return pagina(HttpStatus.TOO_MANY_REQUESTS, PaginasDeExclusao.formularioDeEmail(e.getMessage()));
        }
        // conta cada pedido, tenha o e-mail conta ou não: o limite é contra encher a caixa de alguém e tentativas em massa
        limitePorIp.registrar(ip);
        limitePorEmail.registrar(chave);
        servico.solicitar(email);
        return pagina(HttpStatus.OK, PaginasDeExclusao.pedidoEnviado());
    }

    @PostMapping(value = "/excluir-conta", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<String> confirmar(@RequestParam(value = "token", required = false) String token, HttpServletRequest requisicao) {
        String ip = requisicao.getRemoteAddr();
        try {
            limiteConfirmacao.verificar(ip);
            limiteConfirmacao.registrar(ip);
        } catch (MuitasTentativasException e) {
            return pagina(HttpStatus.TOO_MANY_REQUESTS, PaginasDeExclusao.linkInvalido(e.getMessage()));
        }
        try {
            Confirmacao confirmacao = servico.excluir(token);
            return pagina(HttpStatus.OK, PaginasDeExclusao.pronto(confirmacao.nome()));
        } catch (DadosInvalidosException e) {
            return pagina(HttpStatus.BAD_REQUEST, PaginasDeExclusao.linkInvalido(e.getMessage()));
        }
    }

    /** Páginas com o token na URL: nada de cache, nada de enviar a URL adiante, nada de abrir dentro de outro site. */
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
