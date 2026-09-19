package br.com.adapter.in.web.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import br.com.adapter.in.web.erro.AcessoNegadoException;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.Usuario;
import br.com.domain.port.out.CriptografarSenhaPort;

/**
 * Pede a senha atual antes de ações perigosas (trocar senha ou e-mail, excluir a conta). Quem só roubou um token de acesso
 * (que dura 15 minutos) não tem a senha. As tentativas erradas também são limitadas, para o token roubado não servir
 * para adivinhar a senha por aqui.
 */
@Component
public class ConfirmacaoDeSenha {

    private final CriptografarSenhaPort criptografia;
    private final LimiteDeTentativas limite;

    public ConfirmacaoDeSenha(CriptografarSenhaPort criptografia, @Qualifier("limiteSenhaAtual") LimiteDeTentativas limite) {
        this.criptografia = criptografia;
        this.limite = limite;
    }

    public void exigir(Usuario usuario, String senhaAtual) {
        if (senhaAtual == null || senhaAtual.isEmpty()) {
            throw new DadosInvalidosException("Informe a sua senha atual para confirmar.");
        }
        String chave = "usuario|" + usuario.getId();
        limite.verificar(chave);
        if (!criptografia.verificarSenha(senhaAtual, usuario.getSenha())) {
            limite.registrar(chave);
            throw new AcessoNegadoException("Senha atual incorreta.");
        }
        limite.limpar(chave);
    }
}
