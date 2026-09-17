package br.com.application.service;

import java.util.NoSuchElementException;

import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.exception.UsuarioNaoEncontradoException;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.CriarVinculoCase;
import br.com.domain.port.out.SalvarUsuarioPort;

public class CriarVinculoService implements CriarVinculoCase {

    private final SalvarUsuarioPort salvarUsuarioPort;

    public CriarVinculoService(SalvarUsuarioPort salvarUsuarioPort) {
        this.salvarUsuarioPort = salvarUsuarioPort;
    }

    @Override
    public void criarVinculo(int idosoId, int familiarId) {
        Idoso idoso = buscarIdoso(idosoId);
        Familiar familiar = buscarFamiliar(familiarId);

        boolean jaVinculado = idoso.getFamiliares().stream()
                .anyMatch(f -> f.getId() == familiar.getId());

        if (jaVinculado) {
            throw new DadosInvalidosException("Esse vínculo já existe.");
        }

        salvarUsuarioPort.salvarVinculo(idoso.getId(), familiar.getId());
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