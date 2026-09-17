package br.com.adapter.in.console;

import br.com.domain.model.Usuario;

public class SessaoAtual {

    private static Usuario usuarioLogado;

    private SessaoAtual() {
    }

    public static void login(Usuario usuario) {
        usuarioLogado = usuario;
    }

    public static void logout() {
        usuarioLogado = null;
    }

    public static Usuario getUsuarioLogado() {
        return usuarioLogado;
    }

    public static boolean estaLogado() {
        return usuarioLogado != null;
    }
}