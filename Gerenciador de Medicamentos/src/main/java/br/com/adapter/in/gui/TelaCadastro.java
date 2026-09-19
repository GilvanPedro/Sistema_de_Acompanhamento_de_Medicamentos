package br.com.adapter.in.gui;

import javax.swing.JPasswordField;
import javax.swing.JTextField;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.application.service.RealizarLoginService;
import br.com.application.service.RegistrarUsuarioService;
import br.com.config.AppConfig;

/** Criação de conta (idoso ou familiar). Ao terminar, já entra no sistema. */
class TelaCadastro extends Pagina {

    TelaCadastro(Navegador nav, boolean comoIdoso) {
        super(comoIdoso ? "Criar conta de idoso" : "Criar conta de familiar",
                comoIdoso ? "Você vai cadastrar e acompanhar os seus remédios."
                        : "Você vai acompanhar os remédios das pessoas que ama.",
                nav::inicio);
        RegistrarUsuarioService registrar = AppConfig.criarRegistrarUsuarioService();
        RealizarLoginService login = AppConfig.criarRealizarLoginService();

        JTextField nome = Campo.texto();
        JTextField email = Campo.texto();
        JPasswordField senha = Campo.senha();
        Escolha mostrar = new Escolha("Mostrar a senha");
        char oculto = senha.getEchoChar();
        mostrar.addActionListener(e -> senha.setEchoChar(mostrar.isSelected() ? (char) 0 : oculto));

        Botao criar = Botao.primario("Criar minha conta");
        criar.addActionListener(e -> {
            String senhaDigitada = new String(senha.getPassword());
            try {
                if (comoIdoso) {
                    registrar.registrarIdoso(nome.getText().trim(), email.getText().trim(), senhaDigitada);
                } else {
                    registrar.registrarFamiliar(nome.getText().trim(), email.getText().trim(), senhaDigitada);
                }
                nav.entrar(login.realizarLogin(email.getText().trim(), senhaDigitada));
            } catch (RuntimeException ex) {
                aviso(Rotulos.erro(ex), Tom.ERRO);
            }
        });

        Cartao form = new Cartao();
        form.add(Ui.pilha(6, Texto.rotulo("Seu nome"), nome));
        form.add(Ui.pilha(6, Texto.rotulo("Seu e-mail"), email));
        form.add(Ui.pilha(6, Texto.rotulo("Escolha uma senha"), senha));
        form.add(Ui.esquerda(mostrar));
        form.add(criar);
        adicionar(form);

        focoInicial(nome);
        botaoPadrao(criar);
    }
}
