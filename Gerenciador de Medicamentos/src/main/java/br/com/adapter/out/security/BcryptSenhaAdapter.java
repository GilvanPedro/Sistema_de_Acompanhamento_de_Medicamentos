package br.com.adapter.out.security;

import br.com.domain.port.out.CriptografarSenhaPort;
import org.mindrot.jbcrypt.BCrypt;

public class BcryptSenhaAdapter implements CriptografarSenhaPort {
    private static final int CUSTO = 12;

    @Override
    public String criptografarSenha(String senha) {
        return BCrypt.hashpw(senha, BCrypt.gensalt(CUSTO));
    }

    @Override
    public boolean verificarSenha(String senhaDigitada, String senhaCriptografada) {
        return BCrypt.checkpw(senhaDigitada, senhaCriptografada);
    }
}
