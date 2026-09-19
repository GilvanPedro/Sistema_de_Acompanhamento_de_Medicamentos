package br.com.adapter.in.gui;

import javax.swing.JPasswordField;
import javax.swing.JTextField;

import br.com.adapter.in.gui.Cartao.Tom;

/** Criação de conta (idoso ou familiar), com o aceite da política de privacidade. Ao terminar, já entra no sistema. */
class TelaCadastro extends Pagina {

    TelaCadastro(Navegador nav, boolean comoIdoso) {
        super(comoIdoso ? "Criar conta de idoso" : "Criar conta de familiar",
                comoIdoso ? "Você vai cadastrar e acompanhar os seus remédios."
                        : "Você vai acompanhar os remédios das pessoas que ama.",
                nav::inicio);

        JTextField nome = Campo.texto();
        JTextField email = Campo.texto();
        JPasswordField senha = Campo.senha();
        Escolha mostrar = new Escolha("Mostrar a senha");
        char oculto = senha.getEchoChar();
        mostrar.addActionListener(e -> senha.setEchoChar(mostrar.isSelected() ? (char) 0 : oculto));
        Escolha aceito = new Escolha("Li e aceito as políticas de privacidade");
        Botao ler = Botao.secundario("Ler a política de privacidade");
        ler.addActionListener(e -> Politica.ler(nav, this));

        Botao criar = Botao.primario("Criar minha conta");
        criar.addActionListener(e -> {
            if (nome.getText().isBlank() || email.getText().isBlank()) {
                aviso("Escreva o seu nome e o seu e-mail.", Tom.AVISO);
                return;
            }
            if (senha.getPassword().length < 8) {
                aviso("A senha precisa ter pelo menos 8 caracteres.", Tom.AVISO);
                return;
            }
            if (!aceito.isSelected()) {
                aviso("Para criar a conta, leia e aceite a política de privacidade.", Tom.AVISO);
                return;
            }
            String tipo = comoIdoso ? "IDOSO" : "FAMILIAR";
            String nomeFinal = nome.getText().trim();
            String emailFinal = email.getText().trim();
            String senhaFinal = new String(senha.getPassword());
            aviso("Criando a conta… se o servidor estiver descansando, pode levar até um minuto.", Tom.AVISO);
            nav.fazer(() -> nav.api().cadastrar(tipo, nomeFinal, emailFinal, senhaFinal), nav::entrar,
                    erro -> aviso(erro.getMessage(), Tom.ERRO));
        });

        Cartao form = new Cartao();
        form.add(Ui.pilha(6, Texto.rotulo("Seu nome"), nome));
        form.add(Ui.pilha(6, Texto.rotulo("Seu e-mail"), email));
        form.add(Ui.pilha(6, Texto.rotulo("Escolha uma senha"), senha, Texto.suave("Pelo menos 8 caracteres.")));
        form.add(Ui.esquerda(mostrar));
        form.add(Ui.esquerda(aceito));
        form.add(ler);
        form.add(criar);
        adicionar(form);
        focoInicial(nome);
        botaoPadrao(criar);
    }
}
