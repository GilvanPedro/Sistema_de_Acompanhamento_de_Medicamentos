package br.com.domain.port.out;

import java.util.List;
import br.com.domain.model.Usuario;

public interface SalvarUsuarioPort {
    void salvar(Usuario usuario);
    List<Usuario> listarTodos();
    void salvarVinculo(int idosoId, int familiarId);
    void atualizar(Usuario usuario);
    void excluir(int id);
    List<Usuario> buscarPorNome(String nome);
    Usuario buscarPorId(int id);
    Usuario buscarPorEmail(String email);
}