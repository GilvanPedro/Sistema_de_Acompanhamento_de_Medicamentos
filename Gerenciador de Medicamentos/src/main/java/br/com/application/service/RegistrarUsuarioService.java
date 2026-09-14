package br.com.application.service;

import br.com.domain.model.Idoso;
import br.com.domain.model.Familiar;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.RegistrarUsuarioCase;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.SalvarUsuarioPort;
import br.com.domain.validation.ValidarDadosUsuario;

public class RegistrarUsuarioService implements RegistrarUsuarioCase {

    private final GerarIdPort gerarIdPort;
    private final SalvarUsuarioPort salvarUsuarioPort;
    private final ValidarDadosUsuario validarDadosUsuario;

    public RegistrarUsuarioService(GerarIdPort gerarIdPort, SalvarUsuarioPort salvarUsuarioPort) {
        this.gerarIdPort = gerarIdPort;
        this.salvarUsuarioPort = salvarUsuarioPort;
        this.validarDadosUsuario = new ValidarDadosUsuario();
    }

    @Override
    public Usuario registrarIdoso(String nome, String email, String senha) {
        validarDadosUsuario.validarUsuario(nome, email, senha);

        int novoId = gerarIdPort.proximoId();
        Idoso idoso = new Idoso(novoId, nome, email, senha);
        salvarUsuarioPort.salvar(idoso);
        return idoso;
    }

    @Override
    public Usuario registrarFamiliar(String nome, String email, String senha) {
        validarDadosUsuario.validarUsuario(nome, email, senha);

        int novoId = gerarIdPort.proximoId();
        Familiar familiar = new Familiar(novoId, nome, email, senha);
        salvarUsuarioPort.salvar(familiar);
        return familiar;
    }
}