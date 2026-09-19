package br.com.adapter.in.gui;

import java.time.format.DateTimeFormatter;
import java.util.List;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.adapter.in.gui.api.Conta;
import br.com.adapter.in.gui.api.Remedio;

/** O idoso escolhe qual remédio acabou de tomar. */
class TelaMarcarTomado extends Pagina {

    static void abrir(Navegador nav, Conta idoso, Runnable voltar) {
        nav.carregar(() -> TelaMedicamentos.doIdoso(nav.api(), idoso), lista -> new TelaMarcarTomado(nav, idoso, lista, voltar));
    }

    TelaMarcarTomado(Navegador nav, Conta idoso, List<Remedio> remedios, Runnable voltar) {
        super("Qual remédio você tomou?", "Toque no botão verde do remédio que você tomou agora.", voltar);

        if (remedios.isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Você ainda não cadastrou nenhum remédio."));
            adicionar(vazio);
            Botao cadastrar = Botao.primario("Cadastrar um remédio");
            cadastrar.addActionListener(e -> TelaMedicamentos.abrir(nav, idoso, voltar, null));
            adicionar(cadastrar);
            return;
        }
        for (Remedio m : remedios) {
            Cartao cartao = new Cartao();
            cartao.add(new Texto(m.nome(), 26, true, Papel.TEXTO));
            cartao.add(Texto.suave(Rotulos.detalhe(m)));
            Botao tomei = Botao.sucesso("Tomei este remédio");
            tomei.addActionListener(e -> nav.fazer(() -> nav.api().registrarTomada(m.id()),
                    t -> aviso("Anotado! Você tomou " + m.nome() + " às "
                            + t.dataHora().format(DateTimeFormatter.ofPattern("HH:mm")) + ".", Tom.OK),
                    erro -> aviso(erro.getMessage(), Tom.AVISO)));
            cartao.add(tomei);
            adicionar(cartao);
        }
    }
}
