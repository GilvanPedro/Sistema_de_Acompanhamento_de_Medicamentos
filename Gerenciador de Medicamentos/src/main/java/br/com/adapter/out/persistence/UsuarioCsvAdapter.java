package br.com.adapter.out.persistence;

import java.util.*;

import br.com.domain.exception.ArquivoCsvCorrompidoException;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Usuario;
import br.com.domain.port.out.SalvarUsuarioPort;
import br.com.domain.util.ArquivoCsvUtil;
import br.com.domain.util.LeituraCsvUtil;

public class UsuarioCsvAdapter implements SalvarUsuarioPort {

    private static final String ARQUIVO_USUARIOS = "arquivo/usuarios.csv";
    private static final String ARQUIVO_VINCULOS = "arquivo/vinculos.csv";

    @Override
    public void salvar(Usuario usuario) {
        String tipo = (usuario instanceof Idoso) ? "IDOSO" : "FAMILIAR";
        String linha = montarLinha(usuario, tipo);
        ArquivoCsvUtil.escreverLinha(ARQUIVO_USUARIOS, linha);
    }

    @Override
    public void salvarVinculo(int idosoId, int familiarId) {
        ArquivoCsvUtil.escreverLinha(ARQUIVO_VINCULOS, idosoId + ";" + familiarId);
    }

    @Override
    public List<Usuario> listarTodos() {
        Map<Integer, Idoso> idosos = new HashMap<>();
        Map<Integer, Familiar> familiares = new HashMap<>();

        for (String linha : ArquivoCsvUtil.lerLinhas(ARQUIVO_USUARIOS)) {
            try {
                String[] campos = linha.split(";");
                int id = Integer.parseInt(campos[0]);
                String tipo = campos[1];
                String nome = campos[2];
                String email = campos[3];
                String senha = campos[4];

                if (tipo.equals("IDOSO")) {
                    idosos.put(id, new Idoso(id, nome, email, senha));
                } else {
                    familiares.put(id, new Familiar(id, nome, email, senha));
                }
            } catch (RuntimeException e) {
                throw new ArquivoCsvCorrompidoException(ARQUIVO_USUARIOS, linha, e);
            }
        }

        for (String linha : ArquivoCsvUtil.lerLinhas(ARQUIVO_VINCULOS)) {
            try {
                String[] campos = linha.split(";");
                int idosoId = Integer.parseInt(campos[0]);
                int familiarId = Integer.parseInt(campos[1]);

                Idoso idoso = idosos.get(idosoId);
                Familiar familiar = familiares.get(familiarId);
                if (idoso != null && familiar != null) {
                    idoso.adicionarFamiliares(familiar);
                    familiar.adicionarIdosos(idoso);
                }
            } catch (RuntimeException e) {
                throw new ArquivoCsvCorrompidoException(ARQUIVO_VINCULOS, linha, e);
            }
        }

        List<Usuario> todos = new ArrayList<>();
        todos.addAll(idosos.values());
        todos.addAll(familiares.values());
        return todos;
    }

    @Override
    public void atualizar(Usuario usuario) {
        List<Usuario> todos = listarTodos();
        List<Usuario> atualizados = new ArrayList<>();

        for (Usuario u : todos) {
            atualizados.add(u.getId() == usuario.getId() ? usuario : u);
        }

        reescreverArquivoUsuarios(atualizados);
    }

    @Override
    public void excluir(int id) {
        List<Usuario> todos = listarTodos();
        List<Usuario> restantes = new ArrayList<>();

        for (Usuario u : todos) {
            if (u.getId() != id) {
                restantes.add(u);
            }
        }

        reescreverArquivoUsuarios(restantes);
        removerVinculosDoUsuario(id);
    }

    @Override
    public List<Usuario> buscarPorNome(String nome) {
        List<Usuario> resultado = new ArrayList<>();
        for (Usuario u : listarTodos()) {
            if (u.getNome().toLowerCase().contains(nome.toLowerCase())) {
                resultado.add(u);
            }
        }
        return resultado;
    }

    @Override
    public Usuario buscarPorEmail(String email) {
        for (Usuario u : listarTodos()) {
            if (u.getEmail().equalsIgnoreCase(email)) {
                return u;
            }
        }
        return null;
    }

    @Override
    public Usuario buscarPorId(int id) {
        return LeituraCsvUtil.buscarPrimeiro(
                ARQUIVO_USUARIOS,
                linha -> {
                    String[] campos = linha.split(";");
                    return Integer.parseInt(campos[0]) == id;
                },
                this::converterLinhaParaUsuario
        ).orElseThrow(() -> new NoSuchElementException("Usuário com id: " + id + " não encontrado."));
    }

    private void removerVinculosDoUsuario(int id) {
        List<String> linhasRestantes = new ArrayList<>();

        for (String linha : ArquivoCsvUtil.lerLinhas(ARQUIVO_VINCULOS)) {
            try {
                String[] campos = linha.split(";");
                int idosoId = Integer.parseInt(campos[0]);
                int familiarId = Integer.parseInt(campos[1]);

                if (idosoId != id && familiarId != id) {
                    linhasRestantes.add(linha);
                }
            } catch (RuntimeException e) {
                throw new ArquivoCsvCorrompidoException(ARQUIVO_VINCULOS, linha, e);
            }
        }

        ArquivoCsvUtil.reescreverLinhas(ARQUIVO_VINCULOS, linhasRestantes);
    }

    private void reescreverArquivoUsuarios(List<Usuario> usuarios) {
        List<String> linhas = new ArrayList<>();
        for (Usuario u : usuarios) {
            String tipo = (u instanceof Idoso) ? "IDOSO" : "FAMILIAR";
            linhas.add(montarLinha(u, tipo));
        }
        ArquivoCsvUtil.reescreverLinhas(ARQUIVO_USUARIOS, linhas);
    }

    private String montarLinha(Usuario usuario, String tipo) {
        return usuario.getId() + ";" + tipo + ";" + usuario.getNome() + ";" +
                usuario.getEmail() + ";" + usuario.getSenha();
    }

    private Usuario converterLinhaParaUsuario(String linha) {
        try {
            String[] campos = linha.split(";");
            int id = Integer.parseInt(campos[0]);
            String tipo = campos[1];
            String nome = campos[2];
            String email = campos[3];
            String senha = campos[4];

            if ("IDOSO".equalsIgnoreCase(tipo)) {
                return new Idoso(id, nome, email, senha);
            } else {
                return new Familiar(id, nome, email, senha);
            }
        } catch (RuntimeException e) {
            throw new ArquivoCsvCorrompidoException(ARQUIVO_USUARIOS, linha, e);
        }
    }
}