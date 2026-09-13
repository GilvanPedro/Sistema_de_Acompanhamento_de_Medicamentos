package br.com.adapter.out.id;

import br.com.domain.port.out.GerarIdPort;

public class GerarIdEmMemoriaAdapter implements GerarIdPort {
    int contador = 1;

    @Override
    public int proximoId() {
        return contador++;
    }
}
