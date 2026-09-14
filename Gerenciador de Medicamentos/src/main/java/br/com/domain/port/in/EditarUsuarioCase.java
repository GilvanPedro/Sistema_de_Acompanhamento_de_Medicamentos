package br.com.domain.port.in;

import br.com.domain.model.Usuario;

public interface EditarUsuarioCase {
    Usuario editarUsuario(int id, String nome, String email, String senha);
}
