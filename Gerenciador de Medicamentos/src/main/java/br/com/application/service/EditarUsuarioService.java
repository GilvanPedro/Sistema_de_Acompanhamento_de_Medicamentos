package br.com.application.service;

import br.com.adapter.out.persistence.UsuarioCsvAdapter;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Usuario;
import br.com.domain.port.in.EditarUsuarioCase;
import br.com.domain.port.out.SalvarUsuarioPort;

public class EditarUsuarioService implements EditarUsuarioCase {
    private final SalvarUsuarioPort salvarUsuario;

    public EditarUsuarioService(SalvarUsuarioPort salvarUsuario) {
        this.salvarUsuario = salvarUsuario;
    }

    @Override
    public Usuario editarIdoso(int id, String nome, String email, String senha) {
        Idoso editarIdoso = (Idoso) buscarUsuario(id);

        editarIdoso.setNome(nome);
        editarIdoso.setEmail(email);
        editarIdoso.setSenha(senha);

        salvarUsuario.atualizar(editarIdoso);
        return editarIdoso;
    }

    @Override
    public Usuario editarFamiliar(int id, String nome, String email, String senha) {
        Familiar editarFamiliar = (Familiar) buscarUsuario(id);

        editarFamiliar.setNome(nome);
        editarFamiliar.setEmail(email);
        editarFamiliar.setSenha(senha);

        salvarUsuario.atualizar(editarFamiliar);
        return editarFamiliar;
    }

    private Usuario buscarUsuario(int id){
        Usuario usuarioBuscado = salvarUsuario.buscarPorId(id);
        return usuarioBuscado;
    }
}
