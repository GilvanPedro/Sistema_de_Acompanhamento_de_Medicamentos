package br.com.adapter.in.gui;

import java.util.List;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.adapter.in.gui.api.Aviso;
import br.com.adapter.in.gui.api.Conta;
import br.com.adapter.in.gui.api.Remedio;

/** O que um familiar pode fazer por um idoso: ver a situação de hoje, os remédios e o histórico. */
class TelaIdosoDoFamiliar extends Pagina {

    static void abrir(Navegador nav, Conta idoso, Runnable voltarHome) {
        nav.carregar(() -> nav.api().avisos(idoso.id()), lista -> new TelaIdosoDoFamiliar(nav, idoso, lista, voltarHome));
    }

    TelaIdosoDoFamiliar(Navegador nav, Conta idoso, List<Aviso> lista, Runnable voltarHome) {
        super(idoso.nome(), "Você acompanha esta pessoa.", voltarHome);
        Runnable voltar = () -> abrir(nav, idoso, voltarHome);

        adicionar(Texto.secao("Situação de hoje"));
        if (lista.isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Nenhum remédio para agora."));
            adicionar(vazio);
        }
        for (Aviso n : lista) {
            Remedio m = n.remedio();
            Cartao c;
            String texto;
            switch (n.tipo()) {
                case TOMADO -> { c = new Cartao(Tom.OK); texto = "Já tomou " + m.nome() + " hoje."; }
                case ESQUECIDO -> { c = new Cartao(Tom.ERRO); texto = "Ainda não tomou " + m.nome() + ". Era para as " + m.horario() + "."; }
                default -> { c = new Cartao(Tom.AVISO); texto = "Está na hora de tomar " + m.nome() + " (" + m.horario() + ")."; }
            }
            c.add(new Texto(texto, 22, true, Papel.TEXTO));
            adicionar(c);
        }

        Botao remedios = Botao.primario("Remédios de " + Rotulos.primeiroNome(idoso.nome()));
        remedios.addActionListener(e -> TelaMedicamentos.abrir(nav, idoso, voltar, null));
        Botao historico = Botao.secundario("Histórico de " + Rotulos.primeiroNome(idoso.nome()));
        historico.addActionListener(e -> TelaHistorico.abrir(nav, idoso, voltar));
        adicionar(remedios);
        adicionar(historico);
    }
}
