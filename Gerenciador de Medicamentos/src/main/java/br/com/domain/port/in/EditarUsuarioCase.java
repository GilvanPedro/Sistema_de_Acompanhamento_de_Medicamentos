package br.com.domain.port.in;

import br.com.domain.model.Usuario;

public interface EditarUsuarioCase {
    Usuario editarIdoso(int id, String nome, String email, String senha);
    Usuario editarFamiliar(int id, String nome, String email, String senha);
}
