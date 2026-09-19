package br.com.adapter.in.gui;

import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.JTextField;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.adapter.in.gui.api.Conta;
import br.com.adapter.in.gui.api.Pedido;

/**
 * Vínculo entre idoso e familiar. O familiar pede para acompanhar um idoso e o idoso precisa aceitar
 * (o pedido vale 24 horas). O idoso também pode adicionar um familiar direto e remover quem já acompanha.
 */
class TelaVinculo extends Pagina {

    private static final DateTimeFormatter QUANDO = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    /** O que a tela precisa do servidor: pedidos recebidos (só o idoso) e as pessoas já vinculadas. */
    record Dados(List<Pedido> pedidos, List<Conta> vinculados) { }

    static void abrir(Navegador nav, Conta usuario, Runnable voltar, String mensagem) {
        nav.carregar(() -> usuario.ehIdoso()
                        ? new Dados(nav.api().pedidosRecebidos(), nav.api().meusFamiliares())
                        : new Dados(List.of(), nav.api().meusIdosos()),
                dados -> new TelaVinculo(nav, usuario, dados, voltar, mensagem));
    }

    TelaVinculo(Navegador nav, Conta usuario, Dados dados, Runnable voltar, String mensagem) {
        super(usuario.ehIdoso() ? "Meus familiares" : "Pessoas que eu acompanho", null, voltar);
        boolean souIdoso = usuario.ehIdoso();

        if (souIdoso) {
            mostrarPedidos(nav, usuario, dados.pedidos(), voltar);
            adicionar(Texto.secao("Quem acompanha você"));
        }
        if (dados.vinculados().isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo(souIdoso ? "Nenhum familiar vinculado ainda." : "Você ainda não acompanha ninguém."));
            adicionar(vazio);
        }
        for (Conta v : dados.vinculados()) {
            Cartao c = new Cartao();
            c.add(new Texto(v.nome(), 26, true, Papel.TEXTO));
            c.add(Texto.suave(v.email()));
            if (souIdoso) {
                Botao remover = Botao.perigo("Remover");
                remover.addActionListener(e -> {
                    boolean sim = Dialogos.confirmar(nav.janela(), "Remover este familiar?",
                            v.nome() + " deixará de ver seus remédios e avisos.", "Sim, remover", "Não, manter");
                    if (!sim) {
                        return;
                    }
                    nav.fazer(() -> {
                        nav.api().removerFamiliar(v.id());
                        return null;
                    }, ok -> abrir(nav, usuario, voltar, v.nome() + " foi removido."),
                            erro -> aviso(erro.getMessage(), Tom.ERRO));
                });
                c.add(remover);
            }
            adicionar(c);
        }

        JTextField busca = Campo.texto();
        Botao vincular = Botao.primario(souIdoso ? "Adicionar familiar" : "Pedir para acompanhar");
        vincular.addActionListener(e -> {
            String entrada = busca.getText().trim();
            if (entrada.isEmpty()) {
                aviso("Digite o e-mail " + (souIdoso ? "do familiar." : "do idoso."), Tom.AVISO);
                return;
            }
            // O servidor responde igual exista a conta ou não (não revela quem usa o sistema): mostramos o que ele diz.
            nav.fazer(() -> souIdoso ? nav.api().adicionarFamiliar(entrada) : nav.api().pedirVinculo(entrada),
                    resposta -> abrir(nav, usuario, voltar, resposta),
                    erro -> aviso(erro.getMessage(), Tom.ERRO));
        });

        Cartao novo = new Cartao();
        novo.add(Texto.secao(souIdoso ? "Adicionar um familiar" : "Pedir para acompanhar um idoso"));
        novo.add(Texto.corpo("A pessoa precisa ter uma conta no CuidaMed. Digite o e-mail dela:"));
        if (!souIdoso) {
            novo.add(Texto.suave("O idoso vai receber o pedido e precisa aceitar. Ele vale por 24 horas."));
        }
        novo.add(busca);
        novo.add(vincular);
        adicionar(novo);

        if (mensagem != null) {
            aviso(mensagem, Tom.OK);
        }
        focoInicial(busca);
        botaoPadrao(vincular);
    }

    /** Pedidos de familiares que querem acompanhar o idoso e aguardam a resposta dele. */
    private void mostrarPedidos(Navegador nav, Conta idoso, List<Pedido> pedidos, Runnable voltar) {
        if (pedidos.isEmpty()) {
            return;
        }
        adicionar(Texto.secao("Pedidos para acompanhar você"));
        for (Pedido pedido : pedidos) {
            Conta familiar = pedido.familiar();
            Cartao c = new Cartao(Tom.AVISO);
            c.add(new Texto(familiar.nome() + " quer acompanhar você.", 24, true, Papel.TEXTO));
            c.add(Texto.suave(familiar.email() + "  ·  pedido de " + pedido.solicitadoEm().format(QUANDO)));
            Botao aceitar = Botao.sucesso("Aceitar");
            aceitar.addActionListener(e -> responder(nav, idoso, familiar, true, voltar));
            Botao recusar = Botao.perigo("Recusar");
            recusar.addActionListener(e -> responder(nav, idoso, familiar, false, voltar));
            c.add(Ui.linha(2, aceitar, recusar));
            adicionar(c);
        }
    }

    private void responder(Navegador nav, Conta idoso, Conta familiar, boolean aceitar, Runnable voltar) {
        nav.fazer(() -> {
            if (aceitar) {
                nav.api().aceitarPedido(familiar.id());
            } else {
                nav.api().recusarPedido(familiar.id());
            }
            return null;
        }, ok -> abrir(nav, idoso, voltar,
                aceitar ? familiar.nome() + " agora acompanha você." : "Pedido de " + familiar.nome() + " recusado."),
                erro -> aviso(erro.getMessage(), Tom.ERRO));
    }
}
