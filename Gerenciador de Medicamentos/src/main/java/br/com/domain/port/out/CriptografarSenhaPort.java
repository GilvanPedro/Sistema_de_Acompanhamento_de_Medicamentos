package br.com.domain.port.out;

public interface CriptografarSenhaPort {
    String criptografarSenha(String senha);
    boolean verificarSenha(String senhaDigitada, String senhaCriptografada);
}
