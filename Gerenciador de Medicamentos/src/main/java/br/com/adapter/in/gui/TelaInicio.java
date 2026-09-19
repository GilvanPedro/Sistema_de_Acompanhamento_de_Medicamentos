package br.com.adapter.in.gui;

import javax.swing.JPasswordField;
import javax.swing.JTextField;

import br.com.adapter.in.gui.Cartao.Tom;

/** Primeira tela: entrar com e-mail e senha, ou criar uma conta. */
class TelaInicio extends Pagina {

    TelaInicio(Navegador nav) {
        super("Bem-vindo ao CuidaMed", "Acompanhe os remédios com tranquilidade.", null);

        JTextField email = Campo.texto();
        JPasswordField senha = Campo.senha();
        Escolha mostrar = new Escolha("Mostrar a senha");
        char oculto = senha.getEchoChar();
        mostrar.addActionListener(e -> senha.setEchoChar(mostrar.isSelected() ? (char) 0 : oculto));

        Botao entrar = Botao.primario("Entrar");
        entrar.addActionListener(e -> {
            if (email.getText().isBlank() || senha.getPassword().length == 0) {
                aviso("Digite seu e-mail e sua senha para entrar.", Tom.AVISO);
                return;
            }
            String emailDigitado = email.getText().trim();
            String senhaDigitada = new String(senha.getPassword());
            aviso("Entrando… se o servidor estiver descansando, pode levar até um minuto.", Tom.AVISO);
            nav.fazer(() -> nav.api().login(emailDigitado, senhaDigitada), nav::entrar,
                    erro -> aviso(erro.getMessage(), Tom.ERRO));
        });

        Cartao entrada = new Cartao();
        entrada.add(Texto.secao("Entrar na minha conta"));
        entrada.add(Ui.pilha(6, Texto.rotulo("E-mail"), email));
        entrada.add(Ui.pilha(6, Texto.rotulo("Senha"), senha));
        entrada.add(Ui.esquerda(mostrar));
        entrada.add(entrar);
        adicionar(entrada);

        Botao criarIdoso = Botao.secundario("Sou idoso");
        criarIdoso.addActionListener(e -> nav.mostrar(new TelaCadastro(nav, true)));
        Botao criarFamiliar = Botao.secundario("Sou familiar");
        criarFamiliar.addActionListener(e -> nav.mostrar(new TelaCadastro(nav, false)));

        Cartao cadastro = new Cartao();
        cadastro.add(Texto.secao("Ainda não tenho conta"));
        cadastro.add(Texto.corpo("Escolha como você vai usar o CuidaMed:"));
        cadastro.add(Ui.linha(2, criarIdoso, criarFamiliar));
        cadastro.add(Texto.suave("Idoso é quem toma os remédios. Familiar é quem acompanha e ajuda."));
        adicionar(cadastro);

        focoInicial(email);
        botaoPadrao(entrar);
    }
}
