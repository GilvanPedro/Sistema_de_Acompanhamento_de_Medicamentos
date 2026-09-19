package br.com.adapter.in.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.adapter.in.gui.api.Aviso;
import br.com.adapter.in.gui.api.ClienteApi;
import br.com.adapter.in.gui.api.Conta;
import br.com.adapter.in.gui.api.Remedio;

/** Tela inicial do familiar: situação de hoje de cada idoso e acesso a cada um. */
class TelaHomeFamiliar extends Pagina {

    /** O que a tela precisa do servidor: quem a pessoa acompanha e os avisos de cada um. */
    record Dados(Conta familiar, List<Conta> idosos, Map<Integer, List<Aviso>> avisos) { }

    static Dados buscar(ClienteApi api, Conta atual) {
        Conta familiar = api.eu();
        List<Conta> idosos = api.meusIdosos();
        Map<Integer, List<Aviso>> avisos = new LinkedHashMap<>();
        for (Conta idoso : idosos) {
            avisos.put(idoso.id(), api.avisos(idoso.id()));
        }
        return new Dados(familiar, idosos, avisos);
    }

    TelaHomeFamiliar(Navegador nav, Dados dados) {
        super("Olá, " + Rotulos.primeiroNome(dados.familiar().nome()) + "!", "Veja como estão as pessoas que você acompanha.", null);
        Conta familiar = dados.familiar();

        adicionar(Texto.secao("Avisos de hoje"));
        boolean algum = false;
        for (Conta idoso : dados.idosos()) {
            for (Aviso n : dados.avisos().getOrDefault(idoso.id(), List.of())) {
                Remedio m = n.remedio();
                switch (n.tipo()) {
                    case ESQUECIDO -> {
                        adicionar(aviso(Tom.ERRO, "Atenção: " + idoso.nome() + " ainda não tomou " + m.nome()
                                + ". Era para as " + m.horario() + "."));
                        algum = true;
                    }
                    case TOMADO -> {
                        adicionar(aviso(Tom.OK, idoso.nome() + " já tomou " + m.nome() + " hoje."));
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
        if (dados.idosos().isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Você ainda não acompanha ninguém. Use o botão \"Vincular um idoso\" abaixo."));
            adicionar(vazio);
        }
        for (Conta idoso : dados.idosos()) {
            Cartao cartao = new Cartao();
            cartao.add(new Texto(idoso.nome(), 26, true, Papel.TEXTO));
            Botao abrir = Botao.primario("Abrir");
            abrir.addActionListener(e -> TelaIdosoDoFamiliar.abrir(nav, idoso, voltar));
            cartao.add(abrir);
            adicionar(cartao);
        }

        Botao vincular = Botao.secundario("Vincular um idoso");
        vincular.addActionListener(e -> TelaVinculo.abrir(nav, familiar, voltar, null));
        Botao meusDados = Botao.secundario("Meus dados");
        meusDados.addActionListener(e -> nav.mostrar(new TelaPerfil(nav, familiar, voltar)));
        adicionar(vincular);
        adicionar(meusDados);
    }

    private static Cartao aviso(Tom tom, String mensagem) {
        Cartao c = new Cartao(tom);
        c.add(new Texto(mensagem, 22, true, Papel.TEXTO));
        return c;
    }
}
