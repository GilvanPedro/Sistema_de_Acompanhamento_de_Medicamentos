package br.com.adapter.in.gui;

import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.JTextField;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.application.service.CriarVinculoService;
import br.com.application.service.GerenciarVinculoService;
import br.com.config.AppConfig;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.PedidoVinculo;
import br.com.domain.model.Usuario;

/**
 * Vínculo entre idoso e familiar. O familiar pede para acompanhar um idoso e o idoso precisa aceitar
 * (o pedido vale 24 horas). O idoso também pode adicionar um familiar direto e remover quem já acompanha.
 */
class TelaVinculo extends Pagina {

    private static final DateTimeFormatter QUANDO = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    TelaVinculo(Navegador nav, Usuario usuario, Runnable voltar, String mensagem) {
        super(usuario instanceof Idoso ? "Meus familiares" : "Pessoas que eu acompanho", null, voltar);
        boolean souIdoso = usuario instanceof Idoso;
        CriarVinculoService criarVinculo = AppConfig.criarCriarVinculoService();
        GerenciarVinculoService gerenciar = AppConfig.criarGerenciarVinculoService();

        if (souIdoso) {
            mostrarPedidos(nav, (Idoso) usuario, gerenciar, voltar);
        }

        List<? extends Usuario> vinculados = souIdoso ? ((Idoso) usuario).getFamiliares() : ((Familiar) usuario).getIdosos();
        if (souIdoso) {
            adicionar(Texto.secao("Quem acompanha você"));
        }
        if (vinculados.isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo(souIdoso ? "Nenhum familiar vinculado ainda." : "Você ainda não acompanha ninguém."));
            adicionar(vazio);
        }
        for (Usuario v : vinculados) {
            Cartao c = new Cartao();
            c.add(new Texto(v.getNome(), 26, true, Papel.TEXTO));
            c.add(Texto.suave(v.getEmail()));
            if (souIdoso) {
                Botao remover = Botao.perigo("Remover");
                remover.addActionListener(e -> {
                    boolean sim = Dialogos.confirmar(nav.janela(), "Remover este familiar?",
                            v.getNome() + " deixará de ver seus remédios e avisos.", "Sim, remover", "Não, manter");
                    if (!sim) {
                        return;
                    }
                    try {
                        gerenciar.removerFamiliar(usuario.getId(), v.getId());
                        nav.atualizarUsuario();
                        nav.mostrar(new TelaVinculo(nav, nav.usuario(), voltar, v.getNome() + " foi removido."));
                    } catch (RuntimeException ex) {
                        aviso(Rotulos.erro(ex), Tom.ERRO);
                    }
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
            try {
                Usuario outro = localizar(entrada);
                if (souIdoso && !(outro instanceof Familiar)) {
                    throw new DadosInvalidosException("Essa conta é de um idoso. Digite o e-mail de um familiar.");
                }
                if (!souIdoso && !(outro instanceof Idoso)) {
                    throw new DadosInvalidosException("Essa conta é de um familiar. Digite o e-mail de um idoso.");
                }
                if (souIdoso) {
                    criarVinculo.criarVinculo(usuario.getId(), outro.getId());
                    nav.atualizarUsuario();
                    nav.mostrar(new TelaVinculo(nav, nav.usuario(), voltar, "Pronto! " + outro.getNome() + " agora acompanha você."));
                } else {
                    gerenciar.solicitarVinculo(usuario.getId(), outro.getId());
                    nav.mostrar(new TelaVinculo(nav, nav.usuario(), voltar,
                            "Pedido enviado para " + outro.getNome() + ". A pessoa tem 24 horas para aceitar."));
                }
            } catch (RuntimeException ex) {
                aviso(Rotulos.erro(ex), Tom.ERRO);
            }
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
    private void mostrarPedidos(Navegador nav, Idoso idoso, GerenciarVinculoService gerenciar, Runnable voltar) {
        List<PedidoVinculo> pedidos = gerenciar.listarPedidosPendentes(idoso.getId());
        if (pedidos.isEmpty()) {
            return;
        }
        adicionar(Texto.secao("Pedidos para acompanhar você"));
        for (PedidoVinculo pedido : pedidos) {
            Familiar familiar = pedido.getFamiliar();
            Cartao c = new Cartao(Tom.AVISO);
            c.add(new Texto(familiar.getNome() + " quer acompanhar você.", 24, true, Papel.TEXTO));
            c.add(Texto.suave(familiar.getEmail() + "  ·  pedido de " + pedido.getSolicitadoEm().format(QUANDO)));
            Botao aceitar = Botao.sucesso("Aceitar");
            aceitar.addActionListener(e -> responder(nav, idoso, familiar, true, gerenciar, voltar));
            Botao recusar = Botao.perigo("Recusar");
            recusar.addActionListener(e -> responder(nav, idoso, familiar, false, gerenciar, voltar));
            c.add(Ui.linha(2, aceitar, recusar));
            adicionar(c);
        }
    }

    private void responder(Navegador nav, Idoso idoso, Familiar familiar, boolean aceitar,
                           GerenciarVinculoService gerenciar, Runnable voltar) {
        try {
            if (aceitar) {
                gerenciar.aceitarPedido(idoso.getId(), familiar.getId());
            } else {
                gerenciar.recusarPedido(idoso.getId(), familiar.getId());
            }
            nav.atualizarUsuario();
            nav.mostrar(new TelaVinculo(nav, nav.usuario(), voltar,
                    aceitar ? familiar.getNome() + " agora acompanha você." : "Pedido de " + familiar.getNome() + " recusado."));
        } catch (RuntimeException ex) {
            aviso(Rotulos.erro(ex), Tom.ERRO);
        }
    }

    private static Usuario localizar(String entrada) {
        Usuario achado;
        try {
            achado = entrada.matches("\\d+") ? AppConfig.getUsuarioPort().buscarPorId(Integer.parseInt(entrada))
                    : AppConfig.getUsuarioPort().buscarPorEmail(entrada);
        } catch (java.util.NoSuchElementException e) {
            achado = null;
        }
        if (achado == null) {
            throw new DadosInvalidosException("Não encontramos nenhuma conta com \"" + entrada + "\". Confira se está escrito certo.");
        }
        return achado;
    }
}
