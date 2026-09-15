package br.com.domain.validation;

import br.com.domain.exception.DadosInvalidosException;

public class ValidarDadosUsuario {

    public void validarUsuario(String nome, String email, String senha) {
        validarNome(nome);
        validarEmail(email);
        validarSenha(senha);
    }

    public void validarNome(String nome) {
        if (!ValidarInformacoesVazias.validar(nome)) {
            throw new DadosInvalidosException("Nome é obrigatório");
        }
    }

    public void validarEmail(String email) {
        if (!ValidarInformacoesVazias.validar(email)) {
            throw new DadosInvalidosException("E-mail é obrigatório.");
        }
        if (!ValidarEmail.validar(email)) {
            throw new DadosInvalidosException("Formato de e-mail inválido.");
        }
    }

    public void validarSenha(String senha) {
        if (!ValidarInformacoesVazias.validar(senha)) {
            throw new DadosInvalidosException("Senha é obrigatório");
        }
    }
}