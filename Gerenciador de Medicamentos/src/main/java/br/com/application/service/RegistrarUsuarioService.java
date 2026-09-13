package br.com.application.service;

import br.com.domain.model.Idoso;
import br.com.domain.model.Familiar;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.RegistrarUsuarioCase;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.SalvarUsuarioPort;

public class RegistrarUsuarioService implements RegistrarUsuarioCase {

    private final GerarIdPort gerarIdPort;
    private final SalvarUsuarioPort salvarUsuarioPort;

    public RegistrarUsuarioService(GerarIdPort gerarIdPort, SalvarUsuarioPort salvarUsuarioPort) {
        this.gerarIdPort = gerarIdPort;
        this.salvarUsuarioPort = salvarUsuarioPort;
    }

    @Override
    public Usuario registrarIdoso(String nome, String email, String senha) {
        int novoId = gerarIdPort.proximoId();
        Idoso idoso = new Idoso(novoId, nome, email, senha);
        salvarUsuarioPort.salvar(idoso);
        return idoso;
    }

    @Override
    public Usuario registrarFamiliar(String nome, String email, String senha) {
        int novoId = gerarIdPort.proximoId();
        Familiar familiar = new Familiar(novoId, nome, email, senha);
        salvarUsuarioPort.salvar(familiar);
        return familiar;
    }
}