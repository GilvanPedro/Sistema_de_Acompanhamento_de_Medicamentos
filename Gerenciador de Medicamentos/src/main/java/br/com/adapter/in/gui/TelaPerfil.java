package br.com.adapter.in.gui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.JFileChooser;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.api.Conta;

/**
 * O usuário edita nome, e-mail e senha (o que não mudar continua como está), lê a política de privacidade, baixa uma
 * cópia dos seus dados e pode excluir a conta. Trocar e-mail ou senha, baixar os dados e excluir pedem a senha atual.
 */
class TelaPerfil extends Pagina {

    TelaPerfil(Navegador nav, Conta usuario, Runnable voltar) {
        super("Meus dados", "Mude só o que precisar. O resto fica como está.", voltar);

        JTextField nome = Campo.texto();
        nome.setText(usuario.nome());
        JTextField email = Campo.texto();
        email.setText(usuario.email());
        JPasswordField senha = Campo.senha();
        JPasswordField senhaAtual = Campo.senha();
        Escolha mostrar = new Escolha("Mostrar as senhas");
        char oculto = senha.getEchoChar();
        mostrar.addActionListener(e -> {
            char eco = mostrar.isSelected() ? (char) 0 : oculto;
            senha.setEchoChar(eco);
            senhaAtual.setEchoChar(eco);
        });

        Botao salvar = Botao.primario("Salvar");
        salvar.addActionListener(e -> {
            String novoNome = nome.getText().trim().equals(usuario.nome()) ? null : nome.getText().trim();
            String novoEmail = email.getText().trim().equalsIgnoreCase(usuario.email()) ? null : email.getText().trim();
            String novaSenha = senha.getPassword().length == 0 ? null : new String(senha.getPassword());
            String atual = senhaAtual.getPassword().length == 0 ? null : new String(senhaAtual.getPassword());
            if (novoNome == null && novoEmail == null && novaSenha == null) {
                aviso("Você não mudou nada.", Tom.AVISO);
                return;
            }
            if (novaSenha != null && novaSenha.length() < 8) {
                aviso("A nova senha precisa ter pelo menos 8 caracteres.", Tom.AVISO);
                return;
            }
            if ((novaSenha != null || novoEmail != null) && atual == null) {
                aviso("Para trocar o e-mail ou a senha, digite a sua senha atual.", Tom.AVISO);
                return;
            }
            String senhaParaEnviar = (novaSenha != null || novoEmail != null) ? atual : null;
            nav.fazer(() -> nav.api().editarPerfil(novoNome, novoEmail, novaSenha, senhaParaEnviar), nova -> {
                if (novaSenha != null) {
                    // O servidor encerra todas as sessões ao trocar a senha: é preciso entrar de novo.
                    nav.sair();
                } else {
                    nav.home("Seus dados foram atualizados.");
                }
            }, erro -> aviso(erro.getMessage(), Tom.ERRO));
        });

        Cartao form = new Cartao();
        form.add(Ui.pilha(6, Texto.rotulo("Nome"), nome));
        form.add(Ui.pilha(6, Texto.rotulo("E-mail"), email));
        form.add(Ui.pilha(6, Texto.rotulo("Nova senha"), senha, Texto.suave("Deixe em branco para manter a senha atual.")));
        form.add(Ui.pilha(6, Texto.rotulo("Sua senha atual"),
                senhaAtual, Texto.suave("Pedida para trocar o e-mail ou a senha, baixar os seus dados ou excluir a conta.")));
        form.add(Ui.esquerda(mostrar));
        adicionar(form);
        adicionar(salvar);

        // ---- privacidade (LGPD)
        Botao ler = Botao.secundario("Ler a política de privacidade");
        ler.addActionListener(e -> Politica.ler(nav, this));
        Botao baixar = Botao.secundario("Baixar meus dados");
        baixar.addActionListener(e -> {
            if (senhaAtual.getPassword().length == 0) {
                aviso("Digite a sua senha atual para baixar os seus dados.", Tom.AVISO);
                return;
            }
            String atual = new String(senhaAtual.getPassword());
            nav.fazer(() -> nav.api().exportarDados(atual), copia -> salvarCopia(nav, copia),
                    erro -> aviso(erro.getMessage(), Tom.ERRO));
        });
        Cartao privacidade = new Cartao();
        privacidade.add(Texto.secao("Privacidade"));
        privacidade.add(Texto.corpo("Leia como os seus dados são usados e baixe uma cópia de tudo o que o CuidaMed guarda sobre a sua conta."));
        privacidade.add(ler);
        privacidade.add(baixar);
        adicionar(privacidade);

        // ---- excluir a conta
        Botao excluir = Botao.perigo("Excluir minha conta");
        excluir.addActionListener(e -> {
            if (senhaAtual.getPassword().length == 0) {
                aviso("Digite a sua senha atual para excluir a conta.", Tom.AVISO);
                return;
            }
            boolean sim = Dialogos.confirmar(nav.janela(), "Excluir a conta?",
                    "Isso apaga para sempre os seus remédios, o histórico e os vínculos. Não dá para desfazer.",
                    "Sim, excluir para sempre", "Não, manter a minha conta");
            if (!sim) {
                return;
            }
            String atual = new String(senhaAtual.getPassword());
            nav.fazer(() -> {
                nav.api().excluirConta(atual);
                return null;
            }, ok -> nav.sair(), erro -> aviso(erro.getMessage(), Tom.ERRO));
        });
        Cartao perigo = new Cartao(Tom.ERRO);
        perigo.add(Texto.secao("Excluir minha conta"));
        perigo.add(Texto.corpo("Isso apaga para sempre os seus remédios, o histórico e os vínculos."));
        perigo.add(excluir);
        adicionar(perigo);

        focoInicial(nome);
        botaoPadrao(salvar);
    }

    /** Pergunta onde salvar a cópia dos dados e grava o arquivo. */
    private void salvarCopia(Navegador nav, String copia) {
        JFileChooser escolha = new JFileChooser();
        escolha.setDialogTitle("Salvar a cópia dos meus dados");
        escolha.setSelectedFile(new File("cuidamed-meus-dados.json"));
        if (escolha.showSaveDialog(nav.janela()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Files.writeString(escolha.getSelectedFile().toPath(), copia, StandardCharsets.UTF_8);
            aviso("Cópia dos seus dados salva em " + escolha.getSelectedFile().getName() + ".", Tom.OK);
        } catch (IOException e) {
            aviso("Não foi possível salvar o arquivo.", Tom.ERRO);
        }
    }
}
