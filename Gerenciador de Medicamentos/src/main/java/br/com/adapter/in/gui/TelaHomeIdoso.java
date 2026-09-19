package br.com.adapter.in.gui;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.application.service.RegistrarTomadaService;
import br.com.application.service.VerificarNotificacoesIdosoService;
import br.com.config.AppConfig;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.NotificacaoMedicamento;
import br.com.domain.model.TipoNotificacao;

/** Tela inicial do idoso: avisos de hoje e um menu com poucas opções grandes. */
class TelaHomeIdoso extends Pagina {

    TelaHomeIdoso(Navegador nav, Idoso idoso) {
        super("Olá, " + Rotulos.primeiroNome(idoso.getNome()) + "!",
                "Hoje é " + Rotulos.dataExtenso(LocalDate.now()) + ".", null);
        VerificarNotificacoesIdosoService notificacoes = AppConfig.criarVerificarNotificacoesIdosoService();
        RegistrarTomadaService registrarTomada = AppConfig.criarRegistrarTomadaService();

        adicionar(Texto.secao("Avisos de hoje"));
        List<NotificacaoMedicamento> avisos = notificacoes.verificarNotificacoes(idoso).stream()
                .sorted(Comparator.comparing((NotificacaoMedicamento n) -> ordem(n.getTipo()))
                        .thenComparing(n -> n.getMedicamento().getHorarioMedicamento()))
                .toList();

        if (avisos.isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Nenhum remédio para tomar agora. Está tudo em dia!"));
            adicionar(vazio);
        }
        for (NotificacaoMedicamento n : avisos) {
            Medicamento m = n.getMedicamento();
            String hora = m.getHorarioMedicamento().toString();
            Cartao cartao = switch (n.getTipo()) {
                case TOMADO -> new Cartao(Tom.OK);
                case ESQUECIDO -> new Cartao(Tom.ERRO);
                case LEMBRETE -> new Cartao(Tom.AVISO);
            };
            switch (n.getTipo()) {
                case TOMADO -> cartao.add(new Texto("Você já tomou " + m.getNome() + " hoje.", 22, true, Papel.TEXTO));
                case ESQUECIDO -> cartao.add(new Texto("Atenção: você ainda não tomou " + m.getNome() + ". Era para as " + hora + ".", 22, true, Papel.TEXTO));
                case LEMBRETE -> cartao.add(new Texto("Está na hora de tomar " + m.getNome() + " (" + hora + ").", 22, true, Papel.TEXTO));
            }
            if (n.getTipo() != TipoNotificacao.TOMADO) {
                Botao tomei = Botao.sucesso("Já tomei");
                tomei.addActionListener(e -> {
                    try {
                        registrarTomada.registrarTomada(idoso, m, true);
                        nav.home("Anotado! Você tomou " + m.getNome() + ".");
                    } catch (RuntimeException ex) {
                        aviso(Rotulos.erro(ex), Tom.AVISO);
                    }
                });
                cartao.add(tomei);
            }
            adicionar(cartao);
        }

        adicionar(Texto.secao("O que você quer fazer?"));
        Runnable voltar = nav::home;

        Botao tomado = Botao.primario("Tomei um remédio");
        tomado.addActionListener(e -> nav.mostrar(new TelaMarcarTomado(nav, idoso, voltar)));
        Botao remedios = Botao.secundario("Meus remédios");
        remedios.addActionListener(e -> nav.mostrar(new TelaMedicamentos(nav, idoso, voltar, null)));
        Botao historico = Botao.secundario("Meu histórico");
        historico.addActionListener(e -> nav.mostrar(new TelaHistorico(idoso, voltar)));
        Botao familiares = Botao.secundario("Meus familiares");
        familiares.addActionListener(e -> nav.mostrar(new TelaVinculo(nav, idoso, voltar, null)));
        Botao dados = Botao.secundario("Meus dados");
        dados.addActionListener(e -> nav.mostrar(new TelaPerfil(nav, idoso, voltar)));

        adicionar(tomado);
        adicionar(remedios);
        adicionar(historico);
        adicionar(familiares);
        adicionar(dados);
    }

    private static int ordem(TipoNotificacao tipo) {
        return switch (tipo) {
            case ESQUECIDO -> 0;
            case LEMBRETE -> 1;
            case TOMADO -> 2;
        };
    }
}
