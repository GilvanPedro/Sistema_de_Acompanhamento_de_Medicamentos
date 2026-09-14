package br.com.application.service;

import br.com.domain.port.in.ExcluirUsuaioCase;
import br.com.domain.port.out.SalvarUsuarioPort;

public class ExcluirUsuarioService implements ExcluirUsuaioCase {
    private final SalvarUsuarioPort salvarUsuarioPort;

    public ExcluirUsuarioService(SalvarUsuarioPort salvarUsuarioPort) {
        this.salvarUsuarioPort = salvarUsuarioPort;
    }

    @Override
    public void excluirUsuario(int id) {

        salvarUsuarioPort.excluir(id);
    }
}
