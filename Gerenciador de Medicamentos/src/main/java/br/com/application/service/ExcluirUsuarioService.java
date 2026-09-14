package br.com.application.service;

import br.com.domain.exception.UsuarioNaoEncontradoException;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.ExcluirUsuaioCase;
import br.com.domain.port.out.SalvarUsuarioPort;

import java.util.NoSuchElementException;

public class ExcluirUsuarioService implements ExcluirUsuaioCase {
    private final SalvarUsuarioPort salvarUsuarioPort;

    public ExcluirUsuarioService(SalvarUsuarioPort salvarUsuarioPort) {
        this.salvarUsuarioPort = salvarUsuarioPort;
    }

    @Override
    public void excluirUsuario(int id) {
        buscarUsuario(id);

        salvarUsuarioPort.excluir(id);
    }

    private Usuario buscarUsuario(int id) {
        try {
            return salvarUsuarioPort.buscarPorId(id);
        } catch (NoSuchElementException e) {
            throw new UsuarioNaoEncontradoException(id);
        }
    }
}
