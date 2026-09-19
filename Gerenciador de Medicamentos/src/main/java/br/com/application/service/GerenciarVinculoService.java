package br.com.application.service;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.exception.UsuarioNaoEncontradoException;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.PedidoVinculo;
import br.com.domain.model.StatusVinculo;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.GerenciarVinculoCase;
import br.com.domain.port.out.SalvarUsuarioPort;

public class GerenciarVinculoService implements GerenciarVinculoCase {

    /** Depois desse tempo sem resposta, o pedido expira e o familiar precisa pedir de novo. */
    public static final Duration VALIDADE_PEDIDO = Duration.ofHours(24);

    private static final String PEDIDO_INEXISTENTE = "Esse pedido não existe mais ou já expirou. Peça para a pessoa enviar de novo.";

    private final SalvarUsuarioPort salvarUsuarioPort;

    public GerenciarVinculoService(SalvarUsuarioPort salvarUsuarioPort) {
        this.salvarUsuarioPort = salvarUsuarioPort;
    }

    @Override
    public void solicitarVinculo(int familiarId, int idosoId) {
        buscarFamiliar(familiarId);
        buscarIdoso(idosoId);

        StatusVinculo status = salvarUsuarioPort.buscarStatusVinculo(idosoId, familiarId, VALIDADE_PEDIDO);
        if (status == StatusVinculo.ACEITO) {
            throw new DadosInvalidosException("Você já acompanha essa pessoa.");
        }
        if (status == StatusVinculo.PENDENTE) {
            throw new DadosInvalidosException("Você já enviou um pedido para essa pessoa. Aguarde ela aceitar.");
        }

        salvarUsuarioPort.solicitarVinculo(idosoId, familiarId);
    }

    @Override
    public List<PedidoVinculo> listarPedidosPendentes(int idosoId) {
        return salvarUsuarioPort.listarPedidosPendentes(idosoId, VALIDADE_PEDIDO);
    }

    @Override
    public void aceitarPedido(int idosoId, int familiarId) {
        responder(idosoId, familiarId, true);
    }

    @Override
    public void recusarPedido(int idosoId, int familiarId) {
        responder(idosoId, familiarId, false);
    }

    @Override
    public void removerFamiliar(int idosoId, int familiarId) {
        StatusVinculo status = salvarUsuarioPort.buscarStatusVinculo(idosoId, familiarId, VALIDADE_PEDIDO);
        if (status != StatusVinculo.ACEITO) {
            throw new DadosInvalidosException("Esse familiar não está vinculado a você.");
        }
        salvarUsuarioPort.removerVinculo(idosoId, familiarId);
    }

    private void responder(int idosoId, int familiarId, boolean aceitar) {
        if (!salvarUsuarioPort.responderPedidoVinculo(idosoId, familiarId, aceitar, VALIDADE_PEDIDO)) {
            throw new DadosInvalidosException(PEDIDO_INEXISTENTE);
        }
    }

    private Idoso buscarIdoso(int id) {
        Usuario usuario = buscarUsuario(id);
        if (!(usuario instanceof Idoso idoso)) {
            throw new IllegalArgumentException("O id " + id + " não pertence a um idoso.");
        }
        return idoso;
    }

    private Familiar buscarFamiliar(int id) {
        Usuario usuario = buscarUsuario(id);
        if (!(usuario instanceof Familiar familiar)) {
            throw new IllegalArgumentException("O id " + id + " não pertence a um familiar.");
        }
        return familiar;
    }

    private Usuario buscarUsuario(int id) {
        try {
            return salvarUsuarioPort.buscarPorId(id);
        } catch (NoSuchElementException e) {
            throw new UsuarioNaoEncontradoException(id);
        }
    }
}
