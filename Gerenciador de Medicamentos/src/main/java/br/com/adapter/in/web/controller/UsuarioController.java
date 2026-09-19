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
import br.com.adapter.in.web.auth.TokenService;
import br.com.adapter.in.web.dto.Dtos.EditarUsuarioRequest;
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

    UsuarioController(Acesso acesso, EditarUsuarioService editar, ExcluirUsuarioService excluir, TokenService tokens) {
        this.acesso = acesso;
        this.editar = editar;
        this.excluir = excluir;
        this.tokens = tokens;
    }

    @GetMapping
    UsuarioDto eu(HttpServletRequest requisicao) {
        return UsuarioDto.de(acesso.logado(requisicao));
    }

    /** Edição parcial: só os campos enviados mudam. Trocar a senha encerra todas as sessões. */
    @PatchMapping
    UsuarioDto editar(@RequestBody EditarUsuarioRequest corpo, HttpServletRequest requisicao) {
        if (corpo == null) {
            throw new DadosInvalidosException("Informe o que deseja alterar.");
        }
        Usuario atual = acesso.logado(requisicao);
        Usuario editado = editar.editarUsuario(atual.getId(), corpo.nome(), corpo.email(), corpo.senha());
        if (corpo.senha() != null) {
            tokens.revogarTodos(atual.getId());
        }
        return UsuarioDto.de(editado);
    }

    /** Exclusão da conta (direito do titular na LGPD): a conta some e as sessões são encerradas. */
    @DeleteMapping
    ResponseEntity<Void> excluirConta(HttpServletRequest requisicao) {
        Usuario atual = acesso.logado(requisicao);
        excluir.excluirUsuario(atual.getId());
        tokens.revogarTodos(atual.getId());
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
