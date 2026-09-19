package br.com.adapter.in.gui;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.application.service.VerificarNotificacoesIdosoService;
import br.com.config.AppConfig;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.NotificacaoMedicamento;
import br.com.domain.model.Usuario;

/** Tela inicial do familiar: situação de hoje de cada idoso e acesso a cada um. */
class TelaHomeFamiliar extends Pagina {

    TelaHomeFamiliar(Navegador nav, Usuario usuario) {
        super("Olá, " + Rotulos.primeiroNome(usuario.getNome()) + "!", "Veja como estão as pessoas que você acompanha.", null);
        Familiar familiar = (Familiar) usuario;
        VerificarNotificacoesIdosoService notificacoes = AppConfig.criarVerificarNotificacoesIdosoService();

        adicionar(Texto.secao("Avisos de hoje"));
        boolean algum = false;
        for (Idoso idoso : familiar.getIdosos()) {
            for (NotificacaoMedicamento n : notificacoes.verificarNotificacoes(idoso)) {
                Medicamento m = n.getMedicamento();
                switch (n.getTipo()) {
                    case ESQUECIDO -> {
                        adicionar(aviso(Tom.ERRO, "Atenção: " + idoso.getNome() + " ainda não tomou " + m.getNome()
                                + ". Era para as " + m.getHorarioMedicamento() + "."));
                        algum = true;
                    }
                    case TOMADO -> {
                        adicionar(aviso(Tom.OK, idoso.getNome() + " já tomou " + m.getNome() + " hoje."));
                        algum = true;
                    }
                    case LEMBRETE -> { }
                }
            }
        }
        if (!algum) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Nenhum aviso por enquanto."));
            adicionar(vazio);
        }

        adicionar(Texto.secao("Pessoas que você acompanha"));
        Runnable voltar = nav::home;
        if (familiar.getIdosos().isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Você ainda não acompanha ninguém. Use o botão \"Vincular um idoso\" abaixo."));
            adicionar(vazio);
        }
        for (Idoso idoso : familiar.getIdosos()) {
            Cartao cartao = new Cartao();
            cartao.add(new Texto(idoso.getNome(), 26, true, Papel.TEXTO));
            Botao abrir = Botao.primario("Abrir");
            abrir.addActionListener(e -> nav.mostrar(new TelaIdosoDoFamiliar(nav, idoso, voltar)));
            cartao.add(abrir);
            adicionar(cartao);
        }

        Botao vincular = Botao.secundario("Vincular um idoso");
        vincular.addActionListener(e -> nav.mostrar(new TelaVinculo(nav, familiar, voltar, null)));
        Botao dados = Botao.secundario("Meus dados");
        dados.addActionListener(e -> nav.mostrar(new TelaPerfil(nav, familiar, voltar)));
        adicionar(vincular);
        adicionar(dados);
    }

    private static Cartao aviso(Tom tom, String mensagem) {
        Cartao c = new Cartao(tom);
        c.add(new Texto(mensagem, 22, true, Papel.TEXTO));
        return c;
    }
}
