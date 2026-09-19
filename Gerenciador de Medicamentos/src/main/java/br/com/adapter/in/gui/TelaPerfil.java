package br.com.adapter.in.gui;

import javax.swing.JPasswordField;
import javax.swing.JTextField;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.application.service.EditarUsuarioService;
import br.com.config.AppConfig;
import br.com.domain.model.Usuario;

/** O usuário edita nome, e-mail e senha. O que não mudar continua como está. */
class TelaPerfil extends Pagina {

    TelaPerfil(Navegador nav, Usuario usuario, Runnable voltar) {
        super("Meus dados", "Mude só o que precisar. O resto fica como está.", voltar);
        EditarUsuarioService editar = AppConfig.criarEditarUsuarioService();

        JTextField nome = Campo.texto();
        nome.setText(usuario.getNome());
        JTextField email = Campo.texto();
        email.setText(usuario.getEmail());
        JPasswordField senha = Campo.senha();
        Escolha mostrar = new Escolha("Mostrar a senha");
        char oculto = senha.getEchoChar();
        mostrar.addActionListener(e -> senha.setEchoChar(mostrar.isSelected() ? (char) 0 : oculto));

        Botao salvar = Botao.primario("Salvar");
        salvar.addActionListener(e -> {
            String novoNome = nome.getText().trim().equals(usuario.getNome()) ? null : nome.getText().trim();
            String novoEmail = email.getText().trim().equals(usuario.getEmail()) ? null : email.getText().trim();
            String novaSenha = senha.getPassword().length == 0 ? null : new String(senha.getPassword());
            if (novoNome == null && novoEmail == null && novaSenha == null) {
                aviso("Você não mudou nada.", Tom.AVISO);
                return;
            }
            try {
                editar.editarUsuario(usuario.getId(), novoNome, novoEmail, novaSenha);
                nav.home("Seus dados foram atualizados.");
            } catch (RuntimeException ex) {
                aviso(Rotulos.erro(ex), Tom.ERRO);
            }
        });

        Cartao form = new Cartao();
        form.add(Ui.pilha(6, Texto.rotulo("Nome"), nome));
        form.add(Ui.pilha(6, Texto.rotulo("E-mail"), email));
        form.add(Ui.pilha(6, Texto.rotulo("Nova senha"), senha, Texto.suave("Deixe em branco para manter a senha atual.")));
        form.add(Ui.esquerda(mostrar));
        adicionar(form);
        adicionar(salvar);

        focoInicial(nome);
        botaoPadrao(salvar);
    }
}
