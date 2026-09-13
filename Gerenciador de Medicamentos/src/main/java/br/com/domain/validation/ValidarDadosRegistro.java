package br.com.domain.validation;

public class ValidarDadosRegistro {
    public void validarRegistro(String nome, String email, String senha){
        ValidarInformacoesVazias validarInformacoes = new ValidarInformacoesVazias();

        // verificações de espaços vazios para as informações
        if(!validarInformacoes.validar(nome)){
            throw new IllegalArgumentException("Nome é obrigatório");
        }
        if (!ValidarInformacoesVazias.validar(email)) {
            throw new IllegalArgumentException("E-mail é obrigatório.");
        }
        if (!ValidarEmail.validar(email)) {
            throw new IllegalArgumentException("Formato de e-mail inválido.");
        }
        if(!validarInformacoes.validar(senha)){
            throw new IllegalArgumentException("Senha é obrigatório");
        }
    }
}
