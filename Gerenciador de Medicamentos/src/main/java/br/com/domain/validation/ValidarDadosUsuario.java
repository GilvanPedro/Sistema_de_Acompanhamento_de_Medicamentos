package br.com.domain.validation;

import br.com.domain.exception.DadosInvalidosException;

public class ValidarDadosUsuario {
    public void validarUsuario(String nome, String email, String senha){
        // verificações de espaços vazios para as informações
        if(!ValidarInformacoesVazias.validar(nome)){
            throw new DadosInvalidosException("Nome é obrigatório");
        }
        if (!ValidarInformacoesVazias.validar(email)) {
            throw new DadosInvalidosException("E-mail é obrigatório.");
        }
        if (!ValidarEmail.validar(email)) {
            throw new DadosInvalidosException("Formato de e-mail inválido.");
        }
        if(!ValidarInformacoesVazias.validar(senha)){
            throw new DadosInvalidosException("Senha é obrigatório");
        }
    }
}