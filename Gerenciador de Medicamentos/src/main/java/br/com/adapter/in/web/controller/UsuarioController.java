package br.com.adapter.in.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.auth.Acesso;
import br.com.adapter.in.web.auth.ConfirmacaoDeSenha;
import br.com.adapter.in.web.auth.TokenService;
import br.com.adapter.in.web.dto.Dtos.EditarUsuarioRequest;
import br.com.adapter.in.web.dto.Dtos.ExcluirContaRequest;
import br.com.adapter.in.web.dto.Dtos.UsuarioDto;
import br.com.application.service.EditarUsuarioService;
import br.com.application.service.ExcluirUsuarioService;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.Usuario;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/me")
class UsuarioController {

    private final Acesso acesso;
    private final EditarUsuarioService editar;
    private final ExcluirUsuarioService excluir;
    private final TokenService tokens;
    private final ConfirmacaoDeSenha confirmacao;
    private final br.com.adapter.in.web.push.Dispositivos dispositivos;

    UsuarioController(Acesso acesso, EditarUsuarioService editar, ExcluirUsuarioService excluir, TokenService tokens,
                      ConfirmacaoDeSenha confirmacao, br.com.adapter.in.web.push.Dispositivos dispositivos) {
        this.acesso = acesso;
        this.editar = editar;
        this.excluir = excluir;
        this.tokens = tokens;
        this.confirmacao = confirmacao;
        this.dispositivos = dispositivos;
    }

    @GetMapping
    UsuarioDto eu(HttpServletRequest requisicao) {
        return UsuarioDto.de(acesso.logado(requisicao));
    }

    /**
     * Edição parcial: só os campos enviados mudam. Trocar a senha ou o e-mail exige a senha atual ({@code senhaAtual}).
     * Trocar a senha encerra todas as sessões.
     */
    @PatchMapping
    UsuarioDto editar(@RequestBody EditarUsuarioRequest corpo, HttpServletRequest requisicao) {
        if (corpo == null) {
            throw new DadosInvalidosException("Informe o que deseja alterar.");
        }
        Usuario atual = acesso.logado(requisicao);
        boolean trocaEmail = corpo.email() != null && !corpo.email().trim().equalsIgnoreCase(atual.getEmail());
        if (corpo.senha() != null || trocaEmail) {
            confirmacao.exigir(atual, corpo.senhaAtual());
        }
        Usuario editado = editar.editarUsuario(atual.getId(), corpo.nome(), corpo.email(), corpo.senha());
        if (corpo.senha() != null) {
            tokens.revogarTodos(atual.getId());
        }
        return UsuarioDto.de(editado);
    }

    /** Exclusão da conta (direito do titular na LGPD). Irreversível, por isso exige a senha atual. */
    @DeleteMapping
    ResponseEntity<Void> excluirConta(@RequestBody(required = false) ExcluirContaRequest corpo, HttpServletRequest requisicao) {
        Usuario atual = acesso.logado(requisicao);
        confirmacao.exigir(atual, corpo == null ? null : corpo.senha());
        excluir.excluirUsuario(atual.getId());
        tokens.revogarTodos(atual.getId());
        dispositivos.removerTodos(atual.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/idosos")
    List<UsuarioDto> idososQueAcompanho(HttpServletRequest requisicao) {
        return acesso.familiarLogado(requisicao).getIdosos().stream().map(UsuarioDto::de).toList();
    }

    @GetMapping("/familiares")
    List<UsuarioDto> meusFamiliares(HttpServletRequest requisicao) {
        return acesso.idosoLogado(requisicao).getFamiliares().stream().map(UsuarioDto::de).toList();
    }
}
