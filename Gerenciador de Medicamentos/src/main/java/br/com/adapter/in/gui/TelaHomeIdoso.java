package br.com.adapter.in.gui;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.adapter.in.gui.api.Aviso;
import br.com.adapter.in.gui.api.ClienteApi;
import br.com.adapter.in.gui.api.Conta;
import br.com.adapter.in.gui.api.Remedio;
import br.com.domain.model.TipoNotificacao;

/** Tela inicial do idoso: avisos de hoje e um menu com poucas opções grandes. */
class TelaHomeIdoso extends Pagina {

    /** O que a tela precisa do servidor. */
    record Dados(Conta idoso, List<Aviso> avisos, int pedidos) { }

    static Dados buscar(ClienteApi api, Conta atual) {
        Conta idoso = api.eu();
        return new Dados(idoso, api.avisos(idoso.id()), api.pedidosRecebidos().size());
    }

    TelaHomeIdoso(Navegador nav, Dados dados) {
        super("Olá, " + Rotulos.primeiroNome(dados.idoso().nome()) + "!",
                "Hoje é " + Rotulos.dataExtenso(LocalDate.now()) + ".", null);
        Conta idoso = dados.idoso();
        int pedidos = dados.pedidos();

        if (pedidos > 0) {
            Cartao pedido = new Cartao(Tom.AVISO);
            pedido.add(new Texto(pedidos == 1 ? "Um familiar quer acompanhar você."
                    : pedidos + " familiares querem acompanhar você.", 22, true, Papel.TEXTO));
            Botao ver = Botao.primario("Ver pedidos");
            ver.addActionListener(e -> TelaVinculo.abrir(nav, idoso, nav::home, null));
            pedido.add(ver);
            adicionar(pedido);
        }

        adicionar(Texto.secao("Avisos de hoje"));
        List<Aviso> avisos = dados.avisos().stream()
                .sorted(Comparator.comparing((Aviso n) -> ordem(n.tipo())).thenComparing(n -> n.remedio().horario()))
                .toList();
        if (avisos.isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Nenhum remédio para tomar agora. Está tudo em dia!"));
            adicionar(vazio);
        }
        for (Aviso n : avisos) {
            Remedio m = n.remedio();
            String hora = m.horario().toString();
            Cartao cartao = switch (n.tipo()) {
                case TOMADO -> new Cartao(Tom.OK);
                case ESQUECIDO -> new Cartao(Tom.ERRO);
                case LEMBRETE -> new Cartao(Tom.AVISO);
            };
            switch (n.tipo()) {
                case TOMADO -> cartao.add(new Texto("Você já tomou " + m.nome() + " hoje.", 22, true, Papel.TEXTO));
                case ESQUECIDO -> cartao.add(new Texto("Atenção: você ainda não tomou " + m.nome() + ". Era para as " + hora + ".", 22, true, Papel.TEXTO));
                case LEMBRETE -> cartao.add(new Texto("Está na hora de tomar " + m.nome() + " (" + hora + ").", 22, true, Papel.TEXTO));
            }
            if (n.tipo() != TipoNotificacao.TOMADO) {
                Botao tomei = Botao.sucesso("Já tomei");
                tomei.addActionListener(e -> nav.fazer(() -> nav.api().registrarTomada(m.id()),
                        t -> nav.home("Anotado! Você tomou " + m.nome() + "."),
                        erro -> aviso(erro.getMessage(), Tom.AVISO)));
                cartao.add(tomei);
            }
            adicionar(cartao);
        }

        adicionar(Texto.secao("O que você quer fazer?"));
        Runnable voltar = nav::home;
        Botao tomado = Botao.primario("Tomei um remédio");
        tomado.addActionListener(e -> TelaMarcarTomado.abrir(nav, idoso, voltar));
        Botao remedios = Botao.secundario("Meus remédios");
        remedios.addActionListener(e -> TelaMedicamentos.abrir(nav, idoso, voltar, null));
        Botao historico = Botao.secundario("Meu histórico");
        historico.addActionListener(e -> TelaHistorico.abrir(nav, idoso, voltar));
        Botao familiares = Botao.secundario("Meus familiares");
        familiares.addActionListener(e -> TelaVinculo.abrir(nav, idoso, voltar, null));
        Botao meusDados = Botao.secundario("Meus dados");
        meusDados.addActionListener(e -> nav.mostrar(new TelaPerfil(nav, idoso, voltar)));
        adicionar(tomado);
        adicionar(remedios);
        adicionar(historico);
        adicionar(familiares);
        adicionar(meusDados);
    }

    private static int ordem(TipoNotificacao tipo) {
        return switch (tipo) {
            case ESQUECIDO -> 0;
            case LEMBRETE -> 1;
            case TOMADO -> 2;
        };
    }
}
