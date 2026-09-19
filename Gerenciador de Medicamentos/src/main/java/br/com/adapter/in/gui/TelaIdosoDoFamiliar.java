package br.com.adapter.in.gui;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.application.service.VerificarNotificacoesIdosoService;
import br.com.config.AppConfig;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.NotificacaoMedicamento;

/** O que um familiar pode fazer por um idoso: ver a situação de hoje, os remédios e o histórico. */
class TelaIdosoDoFamiliar extends Pagina {

    TelaIdosoDoFamiliar(Navegador nav, Idoso idoso, Runnable voltarHome) {
        super(idoso.getNome(), "Você acompanha esta pessoa.", voltarHome);
        VerificarNotificacoesIdosoService notificacoes = AppConfig.criarVerificarNotificacoesIdosoService();
        Runnable voltar = () -> nav.mostrar(new TelaIdosoDoFamiliar(nav, idoso, voltarHome));

        adicionar(Texto.secao("Situação de hoje"));
        var lista = notificacoes.verificarNotificacoes(idoso);
        if (lista.isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Nenhum remédio para agora."));
            adicionar(vazio);
        }
        for (NotificacaoMedicamento n : lista) {
            Medicamento m = n.getMedicamento();
            Cartao c;
            String texto;
            switch (n.getTipo()) {
                case TOMADO -> { c = new Cartao(Tom.OK); texto = "Já tomou " + m.getNome() + " hoje."; }
                case ESQUECIDO -> { c = new Cartao(Tom.ERRO); texto = "Ainda não tomou " + m.getNome() + ". Era para as " + m.getHorarioMedicamento() + "."; }
                default -> { c = new Cartao(Tom.AVISO); texto = "Está na hora de tomar " + m.getNome() + " (" + m.getHorarioMedicamento() + ")."; }
            }
            c.add(new Texto(texto, 22, true, Papel.TEXTO));
            adicionar(c);
        }

        Botao remedios = Botao.primario("Remédios de " + Rotulos.primeiroNome(idoso.getNome()));
        remedios.addActionListener(e -> nav.mostrar(new TelaMedicamentos(nav, idoso, voltar, null)));
        Botao historico = Botao.secundario("Histórico de " + Rotulos.primeiroNome(idoso.getNome()));
        historico.addActionListener(e -> nav.mostrar(new TelaHistorico(idoso, voltar)));
        adicionar(remedios);
        adicionar(historico);
    }
}
