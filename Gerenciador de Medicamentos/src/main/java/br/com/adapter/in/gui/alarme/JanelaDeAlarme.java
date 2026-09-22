package br.com.adapter.in.gui.alarme;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.function.IntConsumer;

import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import br.com.adapter.in.gui.Botao;
import br.com.adapter.in.gui.Cartao;
import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Fundo;
import br.com.adapter.in.gui.Navegador;
import br.com.adapter.in.gui.Rolagem;
import br.com.adapter.in.gui.Tema;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.adapter.in.gui.Texto;

/**
 * Janela flutuante do alarme de remédio: fica sempre visível por cima das outras janelas, com letra grande, tocando
 * o alarme sonoro — equivalente à {@code SobreposicaoDoAlarme}/{@code AlarmeActivity} do app Android. Mostra um
 * remédio em destaque, ou a lista inteira se mais de um estiver pendente ao mesmo tempo, cada um com "Já tomei"; um
 * único "Parar o alarme" cala o som de todos de uma vez, sem trancar a janela principal do app.
 */
final class JanelaDeAlarme extends JDialog {

    record Item(int medicamentoId, String nome, String horario, boolean atraso) { }

    /** Depois desse tempo sem resposta, o som para sozinho (igual ao app), mas a janela continua mostrando o aviso. */
    private static final int DURACAO_DO_SOM_MS = 60_000;

    private final SomDeAlarme som = new SomDeAlarme();
    private final IntConsumer aoTomar;
    private final Runnable aoFechar;
    private final Rolagem conteudo = new Rolagem(14, 480);
    private final Timer silenciador = new Timer(DURACAO_DO_SOM_MS, e -> som.parar());
    private boolean fechando;

    JanelaDeAlarme(Navegador nav, IntConsumer aoTomar, Runnable aoFechar) {
        super(nav.janela(), "CuidaMed");
        this.aoTomar = aoTomar;
        this.aoFechar = aoFechar;
        silenciador.setRepeats(false);

        JScrollPane rolagem = new JScrollPane(conteudo);
        rolagem.setBorder(null);
        rolagem.setOpaque(false);
        rolagem.getViewport().setOpaque(false);
        rolagem.getVerticalScrollBar().setUnitIncrement(Tema.px(40));
        rolagem.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Fundo raiz = new Fundo(new BorderLayout());
        raiz.add(rolagem, BorderLayout.CENTER);
        setContentPane(raiz);

        setAlwaysOnTop(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                parar();
            }
        });
    }

    /** Redesenha a janela com a lista atual de remédios pendentes; toca o som (do início) se {@code tocarSom}. */
    void atualizar(List<Item> itens, boolean tocarSom) {
        montar(itens);
        if (tocarSom) {
            som.tocar();
            silenciador.restart();
        }
        boolean primeiraVez = !isVisible();
        pack();
        if (primeiraVez) {
            setMinimumSize(new Dimension(Tema.px(420), getSize().height));
            setLocationRelativeTo(getOwner());
            setVisible(true);
        }
        toFront();
    }

    private void montar(List<Item> itens) {
        conteudo.removeAll();
        boolean algumAtraso = itens.stream().anyMatch(Item::atraso);
        conteudo.add(new Texto(itens.size() == 1
                ? (itens.get(0).atraso() ? "Você ainda não tomou o remédio" : "Hora de tomar o remédio")
                : "Você tem " + itens.size() + " remédios para tomar agora", 26, true, Papel.TEXTO));
        for (Item item : itens) {
            Cartao cartao = new Cartao(item.atraso() ? Tom.ERRO : Tom.AVISO);
            cartao.add(new Texto(item.nome(), 26, true, Papel.TEXTO));
            cartao.add(Texto.suave((item.atraso() ? "Ainda não tomou · era para as " : "Às ") + item.horario()));
            Botao tomei = Botao.sucesso("Já tomei");
            tomei.addActionListener(e -> tomar(item.medicamentoId(), tomei));
            cartao.add(tomei);
            conteudo.add(cartao);
        }
        if (!algumAtraso) {
            conteudo.add(Texto.suave("Se você não marcar que tomou, o aviso continua aparecendo até 1 hora depois do horário."));
        }
        Botao parar = Botao.secundario("Parar o alarme");
        parar.addActionListener(e -> parar());
        conteudo.add(parar);
        Tema.aplicar(conteudo);
        conteudo.revalidate();
        conteudo.repaint();
    }

    private void tomar(int medicamentoId, Botao botao) {
        botao.setEnabled(false);
        botao.setText("Registrando…");
        aoTomar.accept(medicamentoId);
    }

    private void parar() {
        fecharSemAvisar();
        aoFechar.run();
    }

    /** Fecha a janela e para o som, sem chamar o retorno de fechamento (para quando quem chama já sabe que fechou). */
    void fecharSemAvisar() {
        if (fechando) {
            return;
        }
        fechando = true;
        silenciador.stop();
        som.parar();
        setVisible(false);
        dispose();
    }
}
