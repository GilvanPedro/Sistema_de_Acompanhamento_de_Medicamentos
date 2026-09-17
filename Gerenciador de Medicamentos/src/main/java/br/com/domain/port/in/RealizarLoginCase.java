package br.com.domain.port.in;

import br.com.domain.model.Usuario;

public interface RealizarLoginCase {
    Usuario realizarLogin(String email, String senha);
}