package br.com.application.service;

import br.com.domain.exception.UsuarioNaoEncontradoException;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.EditarUsuarioCase;
import br.com.domain.port.out.SalvarUsuarioPort;

import java.util.NoSuchElementException;

public class EditarUsuarioService implements EditarUsuarioCase {
    private final SalvarUsuarioPort salvarUsuario;

    public EditarUsuarioService(SalvarUsuarioPort salvarUsuario) {
        this.salvarUsuario = salvarUsuario;
    }

    @Override
    public Usuario editarUsuario(int id, String nome, String email, String senha) {
        Usuario usuario = salvarUsuario.buscarPorId(id);

        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenha(senha);

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


