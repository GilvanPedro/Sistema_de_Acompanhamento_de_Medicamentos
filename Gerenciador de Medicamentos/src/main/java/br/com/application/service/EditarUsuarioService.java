package br.com.application.service;

import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.exception.UsuarioNaoEncontradoException;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.EditarUsuarioCase;
import br.com.domain.port.out.CriptografarSenhaPort;
import br.com.domain.port.out.SalvarUsuarioPort;
import br.com.domain.validation.ValidarDadosUsuario;

import java.util.NoSuchElementException;

public class EditarUsuarioService implements EditarUsuarioCase {
    private final SalvarUsuarioPort salvarUsuario;
    private final ValidarDadosUsuario validarDadosUsuario;
    private final CriptografarSenhaPort criptografarSenhaPort;

    public EditarUsuarioService(SalvarUsuarioPort salvarUsuario, CriptografarSenhaPort criptografarSenhaPort) {
        this.salvarUsuario = salvarUsuario;
        this.validarDadosUsuario = new ValidarDadosUsuario();
        this.criptografarSenhaPort = criptografarSenhaPort;
    }

    @Override
    public Usuario editarUsuario(int id, String nome, String email, String senha) {
        Usuario usuario = buscarUsuario(id);

        if (nome != null) {
            validarDadosUsuario.validarNome(nome);
            usuario.setNome(nome);
        }

        if (email != null) {
            validarDadosUsuario.validarEmail(email);

            Usuario usuarioComEmail = salvarUsuario.buscarPorEmail(email);
            if (usuarioComEmail != null && usuarioComEmail.getId() != id) {
                throw new DadosInvalidosException("Já existe um usuário cadastrado com o email " + email + ".");
            }
            usuario.setEmail(email);
        }

        if (senha != null) {
            validarDadosUsuario.validarSenha(senha);

            boolean senhaMudou = !criptografarSenhaPort.verificarSenha(senha, usuario.getSenha());
            if (senhaMudou) {
                usuario.setSenha(criptografarSenhaPort.criptografarSenha(senha));
            }
        }

        salvarUsuario.atualizar(usuario);

        return usuario;
    }

    private Usuario buscarUsuario(int id) {
        try {
            return salvarUsuario.buscarPorId(id);
        } catch (NoSuchElementException e) {
            throw new UsuarioNaoEncontradoException(id);
        }
    }
}