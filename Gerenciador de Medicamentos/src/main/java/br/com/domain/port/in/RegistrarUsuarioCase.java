package br.com.domain.port.in;

import br.com.domain.model.Usuario;

public interface RegistrarUsuarioCase {
    Usuario registrarIdoso(String nome, String email, String senha);
    Usuario registrarFamiliar(String nome, String email, String senha);
}
