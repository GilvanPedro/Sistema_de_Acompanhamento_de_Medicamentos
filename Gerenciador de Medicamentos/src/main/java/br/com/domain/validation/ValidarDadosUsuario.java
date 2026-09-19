package br.com.domain.validation;

import br.com.domain.exception.DadosInvalidosException;

public class ValidarDadosUsuario {

    // Limites das colunas do banco (nome e e-mail) e do BCrypt (72 bytes de senha).
    public static final int NOME_MAXIMO = 150;
    public static final int EMAIL_MAXIMO = 254;
    public static final int SENHA_MINIMA = 8;
    public static final int SENHA_MAXIMA_BYTES = 72;

    public void validarUsuario(String nome, String email, String senha) {
        validarNome(nome);
        validarEmail(email);
        validarSenha(senha);
    }

    public void validarNome(String nome) {
        if (!ValidarInformacoesVazias.validar(nome)) {
            throw new DadosInvalidosException("Nome é obrigatório");
        }
        if (nome.trim().length() > NOME_MAXIMO) {
            throw new DadosInvalidosException("O nome pode ter no máximo " + NOME_MAXIMO + " caracteres.");
        }
    }

    public void validarEmail(String email) {
        if (!ValidarInformacoesVazias.validar(email)) {
            throw new DadosInvalidosException("E-mail é obrigatório.");
        }
        if (email.length() > EMAIL_MAXIMO) {
            throw new DadosInvalidosException("O e-mail pode ter no máximo " + EMAIL_MAXIMO + " caracteres.");
        }
        if (!ValidarEmail.validar(email)) {
            throw new DadosInvalidosException("Formato de e-mail inválido.");
        }
    }

    public void validarSenha(String senha) {
        if (!ValidarInformacoesVazias.validar(senha)) {
            throw new DadosInvalidosException("Senha é obrigatório");
        }
        if (senha.length() < SENHA_MINIMA) {
            throw new DadosInvalidosException("A senha precisa ter pelo menos " + SENHA_MINIMA + " caracteres.");
        }
        if (senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > SENHA_MAXIMA_BYTES) {
            throw new DadosInvalidosException("A senha é grande demais. Use no máximo " + SENHA_MAXIMA_BYTES + " caracteres.");
        }
    }
}