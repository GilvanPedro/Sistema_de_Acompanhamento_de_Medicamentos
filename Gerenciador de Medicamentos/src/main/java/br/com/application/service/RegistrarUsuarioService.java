package br.com.application.service;

import br.com.adapter.out.id.GerarIdEmMemoriaAdapter;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.RegistrarUsuarioCase;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.validation.ValidarDadosRegistro;
import br.com.domain.validation.ValidarEmail;
import br.com.domain.validation.ValidarInformacoesVazias;

public class RegistrarUsuarioService implements RegistrarUsuarioCase {
    private ValidarEmail validarEmail = new ValidarEmail();
    private final GerarIdPort gerarIdPort;
    private ValidarDadosRegistro validarDados= new ValidarDadosRegistro();

    public RegistrarUsuarioService(GerarIdPort gerarIdPort) {
        this.gerarIdPort = gerarIdPort;
    }

    @Override
    public Usuario registrarIdoso(String nome, String email, String senha) {
        validarDados.validarRegistro(nome, email, senha);
        int novoId = gerarIdPort.proximoId();
        return new Idoso(novoId, nome, email, senha);
    }

    @Override
    public Usuario registrarFamiliar(String nome, String email, String senha) {
        validarDados.validarRegistro(nome, email, senha);
        int novoId = gerarIdPort.proximoId();
        return new Familiar(novoId, nome, email, senha);
    }
}
