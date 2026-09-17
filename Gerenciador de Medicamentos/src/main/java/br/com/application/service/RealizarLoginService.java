package br.com.application.service;

import br.com.domain.exception.CredenciaisInvalidasException;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.RealizarLoginCase;
import br.com.domain.port.out.CriptografarSenhaPort;
import br.com.domain.port.out.SalvarUsuarioPort;

public class RealizarLoginService implements RealizarLoginCase {

    private final SalvarUsuarioPort salvarUsuarioPort;
    private final CriptografarSenhaPort criptografarSenhaPort;

    public RealizarLoginService(SalvarUsuarioPort salvarUsuarioPort, CriptografarSenhaPort criptografarSenhaPort) {
        this.salvarUsuarioPort = salvarUsuarioPort;
        this.criptografarSenhaPort = criptografarSenhaPort;
    }

    @Override
    public Usuario realizarLogin(String email, String senha) {
        Usuario usuario = salvarUsuarioPort.buscarPorEmail(email);

        if (usuario == null) {
            throw new CredenciaisInvalidasException();
        }

        if (!criptografarSenhaPort.verificarSenha(senha, usuario.getSenha())) {
            throw new CredenciaisInvalidasException();
        }

        return usuario;
    }
}