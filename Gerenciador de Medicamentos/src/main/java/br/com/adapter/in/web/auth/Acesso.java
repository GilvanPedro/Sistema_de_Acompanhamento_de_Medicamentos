package br.com.adapter.in.web.auth;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;

import br.com.adapter.in.web.erro.AcessoNegadoException;
import br.com.adapter.in.web.erro.NaoAutenticadoException;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Usuario;
import br.com.domain.port.out.SalvarUsuarioPort;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Regras de quem pode ver ou alterar o quê (o papel que a SessaoAtual tem no terminal):
 * o idoso acessa só os próprios dados; o familiar, os dos idosos que aceitaram o vínculo.
 */
@Component
public class Acesso {

    private final SalvarUsuarioPort usuarios;

    public Acesso(SalvarUsuarioPort usuarios) {
        this.usuarios = usuarios;
    }

    /** O usuário dono do token, buscado no banco a cada requisição (conta excluída deixa de valer na hora). */
    public Usuario logado(HttpServletRequest requisicao) {
        Object id = requisicao.getAttribute(FiltroAutenticacao.ATRIBUTO_USUARIO_ID);
        if (!(id instanceof Integer usuarioId)) {
            throw new NaoAutenticadoException();
        }
        try {
            return usuarios.buscarPorId(usuarioId);
        } catch (NoSuchElementException e) {
            throw new NaoAutenticadoException();
        }
    }

    public Idoso idosoLogado(HttpServletRequest requisicao) {
        if (logado(requisicao) instanceof Idoso idoso) {
            return idoso;
        }
        throw new AcessoNegadoException("Esta ação é só para idosos.");
    }

    public Familiar familiarLogado(HttpServletRequest requisicao) {
        if (logado(requisicao) instanceof Familiar familiar) {
            return familiar;
        }
        throw new AcessoNegadoException("Esta ação é só para familiares.");
    }

    /** O idoso pedido, se o usuário logado for ele mesmo ou um familiar com vínculo aceito. */
    public Idoso idosoAcessivel(HttpServletRequest requisicao, int idosoId) {
        Usuario atual = logado(requisicao);
        boolean permitido = false;
        if (atual instanceof Idoso idoso) {
            permitido = idoso.getId() == idosoId;
        } else if (atual instanceof Familiar familiar) {
            permitido = familiar.getIdosos().stream().anyMatch(i -> i.getId() == idosoId);
        }
        if (!permitido) {
            throw new AcessoNegadoException("Você não tem acesso a esse idoso.");
        }
        if (atual instanceof Idoso idoso) {
            return idoso;
        }
        return (Idoso) usuarios.buscarPorId(idosoId);
    }
}
