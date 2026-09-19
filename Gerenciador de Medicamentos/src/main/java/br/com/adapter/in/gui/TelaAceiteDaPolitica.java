package br.com.adapter.in.gui;

import br.com.adapter.in.gui.Cartao.Tom;

/** Para quem já tinha conta antes da política de privacidade (ou quando o texto mudar): só segue depois do aceite. */
class TelaAceiteDaPolitica extends Pagina {

    TelaAceiteDaPolitica(Navegador nav) {
        super("Antes de continuar", "Leia a política de privacidade do CuidaMed e, se estiver de acordo, aceite.", null);
        Botao ler = Botao.secundario("Ler a política de privacidade");
        ler.addActionListener(e -> Politica.ler(nav, this));
        Escolha aceito = new Escolha("Li e aceito as políticas de privacidade");
        Botao continuar = Botao.primario("Continuar");
        continuar.addActionListener(e -> {
            if (!aceito.isSelected()) {
                aviso("Para continuar, leia e aceite a política de privacidade.", Tom.AVISO);
                return;
            }
            nav.fazer(() -> {
                nav.api().aceitarPolitica();
                return null;
            }, ok -> nav.home(), erro -> aviso(erro.getMessage(), Tom.ERRO));
        });
        Botao recusar = Botao.secundario("Não aceito: sair da conta");
        recusar.addActionListener(e -> nav.sair());

        Cartao cartao = new Cartao();
        cartao.add(Texto.corpo("A política explica quais dados o CuidaMed guarda (inclusive os seus remédios), para que usa e como você controla tudo isso."));
        cartao.add(ler);
        cartao.add(Ui.esquerda(aceito));
        adicionar(cartao);
        adicionar(continuar);
        adicionar(recusar);
        botaoPadrao(continuar);
    }
}
