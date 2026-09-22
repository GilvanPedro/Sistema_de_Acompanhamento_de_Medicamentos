package br.com.adapter.in.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.adapter.in.gui.alarme.MonitorDeAvisos;
import br.com.adapter.in.gui.alarme.NotificacaoDoSistema;
import br.com.adapter.in.gui.api.ApiException;
import br.com.adapter.in.gui.api.ClienteApi;
import br.com.adapter.in.gui.api.Conta;
import br.com.config.Ambiente;

/** Janela principal: barra superior (letra, modo claro/escuro, sair) e a tela atual. */
public class Navegador {

    private JFrame janela;
    private final Fundo palco = new Fundo(new BorderLayout());
    private final Botao letraMenor = Botao.barra("A−");
    private final Botao letraMaior = Botao.barra("A+");
    private final Botao modo = Botao.barra(textoModo());
    private final Botao sair = Botao.barra("Sair");
    private final JPanel barra = criarBarra();
    private Conta usuario;
    private final ClienteApi api = new ClienteApi(Ambiente.valor("CUIDAMED_API_URL", URL_DA_API));
    /** Alarme de remédio e avisos em segundo plano, mesmo com a tela inicial fechada — equivalente ao do app Android. */
    private final MonitorDeAvisos monitor = new MonitorDeAvisos(this);
    /** Muda a cada tela mostrada: resposta que chega depois de a pessoa ter ido para outra tela é descartada. */
    private int geracao;

    /** A API no Render (plano gratuito: hiberna, e a primeira resposta pode levar cerca de um minuto). */
    static final String URL_DA_API = "https://sistema-de-acompanhamento-de-medicamentos.onrender.com/api/v1";

    public ClienteApi api() {
        return api;
    }

    public void iniciar() {
        janela = new JFrame("CuidaMed");
        Fundo raiz = new Fundo(new BorderLayout());
        raiz.add(barra, BorderLayout.NORTH);
        raiz.add(palco, BorderLayout.CENTER);
        janela.setContentPane(raiz);

        letraMenor.setToolTipText("Diminuir o tamanho da letra");
        letraMaior.setToolTipText("Aumentar o tamanho da letra");
        modo.setToolTipText("Trocar entre modo claro e modo escuro");
        letraMenor.addActionListener(e -> Tema.diminuirLetra());
        letraMaior.addActionListener(e -> Tema.aumentarLetra());
        modo.addActionListener(e -> Tema.alternarModo());
        sair.addActionListener(e -> sair());
        Tema.aoMudar(this::temaMudou);

        janela.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        janela.setMinimumSize(new Dimension(760, 560));
        janela.setSize(1100, 800);
        janela.setLocationRelativeTo(null);

        atualizarBarra();
        inicio();
        janela.setVisible(true);
        NotificacaoDoSistema.iniciar();
        Thread aquecimento = new Thread(api::acordarServidor, "acordar-servidor");
        aquecimento.setDaemon(true);
        aquecimento.start();
    }

    private JPanel criarBarra() {
        JPanel b = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Tema.p().cartao());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Tema.p().borda());
                g.fillRect(0, getHeight() - 2, getWidth(), 2);
            }

            @Override
            public Insets getInsets() {
                return new Insets(Tema.px(10), Tema.px(20), Tema.px(12), Tema.px(20));
            }
        };
        b.setOpaque(false);

        JLabel marca = Tema.marcar(new JLabel("CuidaMed"), 30, true, Papel.DESTAQUE);

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        botoes.setOpaque(false);
        botoes.add(letraMenor);
        botoes.add(letraMaior);
        botoes.add(modo);
        botoes.add(sair);
        b.setLayout(new BarraLayout(marca, botoes));
        b.add(marca);
        b.add(botoes);
        return b;
    }

    /** Marca à esquerda e botões à direita; se não couberem juntos (letra grande), os botões descem para a linha de baixo. */
    private static final class BarraLayout implements LayoutManager {
        private final Component marca;
        private final Component botoes;

        BarraLayout(Component marca, Component botoes) {
            this.marca = marca;
            this.botoes = botoes;
        }

        private boolean cabeEmUmaLinha(Container pai) {
            Insets in = pai.getInsets();
            int largura = pai.getWidth() > 0 ? pai.getWidth() : Integer.MAX_VALUE;
            return marca.getPreferredSize().width + Tema.px(20) + botoes.getPreferredSize().width <= largura - in.left - in.right;
        }

        @Override
        public Dimension preferredLayoutSize(Container pai) {
            Insets in = pai.getInsets();
            Dimension m = marca.getPreferredSize();
            Dimension b = botoes.getPreferredSize();
            int altura = cabeEmUmaLinha(pai) ? Math.max(m.height, b.height) : m.height + b.height;
            return new Dimension(m.width + b.width + in.left + in.right, altura + in.top + in.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container pai) {
            return preferredLayoutSize(pai);
        }

        @Override
        public void layoutContainer(Container pai) {
            Insets in = pai.getInsets();
            int largura = pai.getWidth() - in.left - in.right;
            Dimension m = marca.getPreferredSize();
            Dimension b = botoes.getPreferredSize();
            if (cabeEmUmaLinha(pai)) {
                int altura = Math.max(m.height, b.height);
                marca.setBounds(in.left, in.top + (altura - m.height) / 2, m.width, m.height);
                botoes.setBounds(in.left + largura - b.width, in.top + (altura - b.height) / 2, b.width, b.height);
            } else {
                marca.setBounds(in.left, in.top, m.width, m.height);
                int x = Math.max(in.left, in.left + largura - b.width);
                botoes.setBounds(x, in.top + m.height, Math.min(b.width, largura), b.height);
            }
        }

        @Override
        public void addLayoutComponent(String nome, Component c) { }

        @Override
        public void removeLayoutComponent(Component c) { }
    }

    private void temaMudou() {
        atualizarBarra();
        Tema.aplicar(janela.getContentPane());
        janela.getContentPane().revalidate();
        janela.getContentPane().repaint();
    }

    private void atualizarBarra() {
        modo.setText(textoModo());
        letraMenor.setEnabled(Tema.podeDiminuir());
        letraMaior.setEnabled(Tema.podeAumentar());
        sair.setVisible(usuario != null);
    }

    private static String textoModo() {
        return Tema.modo() == Tema.Modo.CLARO ? "Modo escuro" : "Modo claro";
    }

    public JFrame janela() {
        return janela;
    }

    /** Troca a tela atual. */
    public void mostrar(Pagina pagina) {
        geracao++;
        palco.removeAll();
        palco.add(pagina, BorderLayout.CENTER);
        Tema.aplicar(pagina);
        palco.revalidate();
        palco.repaint();
        if (janela != null) {
            janela.getRootPane().setDefaultButton(pagina.botaoPadrao());
        }
        SwingUtilities.invokeLater(() -> {
            if (pagina.focoInicial() != null) {
                pagina.focoInicial().requestFocusInWindow();
            }
        });
    }

    public Conta usuario() {
        return usuario;
    }

    /**
     * Busca dados na API fora da thread da tela (o servidor pode demorar) e só então monta a tela. Enquanto isso mostra
     * "Carregando". Se der erro, mostra o motivo com "Tentar de novo"; se a sessão acabou, volta para o início.
     */
    public <T> void carregar(Supplier<T> busca, Function<T, Pagina> montar) {
        mostrar(new Pagina("CuidaMed", "Carregando…", null));
        int minha = geracao;
        executar(busca, dados -> {
            if (minha == geracao) {
                mostrar(montar.apply(dados));
            }
        }, erro -> {
            if (minha == geracao) {
                falhouCarregando(erro, () -> carregar(busca, montar));
            }
        });
    }

    /**
     * Faz uma ação da tela atual (salvar, excluir...) fora da thread da tela. Os retornos só rodam se a pessoa ainda
     * estiver nesta tela; se a sessão acabou, volta para o início.
     */
    public <T> void fazer(Supplier<T> acao, Consumer<T> aoConcluir, Consumer<ApiException> aoFalhar) {
        int minha = geracao;
        janela.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        executar(acao, dados -> {
            janela.setCursor(java.awt.Cursor.getDefaultCursor());
            if (minha == geracao) {
                aoConcluir.accept(dados);
            }
        }, erro -> {
            janela.setCursor(java.awt.Cursor.getDefaultCursor());
            if (minha != geracao) {
                return;
            }
            if (erro.sessaoPerdida() && usuario != null) {
                sessaoPerdida();
            } else {
                aoFalhar.accept(erro);
            }
        });
    }

    private <T> void executar(Supplier<T> tarefa, Consumer<T> ok, Consumer<ApiException> erro) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return tarefa.get();
            }

            @Override
            protected void done() {
                try {
                    ok.accept(get());
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable causa = e.getCause();
                    erro.accept(causa instanceof ApiException a ? a
                            : new ApiException(0, "Não foi possível concluir agora. Tente de novo."));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void falhouCarregando(ApiException erro, Runnable tentarDeNovo) {
        if (erro.sessaoPerdida() && usuario != null) {
            sessaoPerdida();
            return;
        }
        Pagina tela = new Pagina("Não foi possível carregar", null, null);
        tela.aviso(erro.getMessage(), Tom.ERRO);
        Botao denovo = Botao.primario("Tentar de novo");
        denovo.addActionListener(e -> tentarDeNovo.run());
        Botao sair = Botao.secundario(usuario != null ? "Sair da conta" : "Voltar ao início");
        sair.addActionListener(e -> sair());
        tela.adicionar(denovo);
        tela.adicionar(sair);
        mostrar(tela);
    }

    private void sessaoPerdida() {
        usuario = null;
        monitor.parar();
        atualizarBarra();
        Pagina inicio = new TelaInicio(this);
        inicio.aviso("Sua sessão terminou. Entre de novo.", Tom.AVISO);
        mostrar(inicio);
    }

    /** Depois do login: só segue para a tela inicial se a conta já aceitou a política de privacidade. */
    public void entrar(Conta novo) {
        usuario = novo;
        atualizarBarra();
        mostrar(new Pagina("CuidaMed", "Carregando…", null));
        int minha = geracao;
        executar(api::politicaAceita, aceita -> {
            if (minha != geracao) {
                return;
            }
            if (aceita) {
                home();
            } else {
                mostrar(new TelaAceiteDaPolitica(this));
            }
        }, erro -> {
            if (minha == geracao) {
                falhouCarregando(erro, () -> entrar(novo));
            }
        });
    }

    public void sair() {
        if (api.logado()) {
            Thread saida = new Thread(api::sair, "sair");
            saida.setDaemon(true);
            saida.start();
        }
        usuario = null;
        monitor.parar();
        atualizarBarra();
        inicio();
    }

    public void inicio() {
        mostrar(new TelaInicio(this));
    }

    public void home() {
        home(null);
    }

    public void home(String aviso) {
        Conta atual = usuario;
        if (atual == null) {
            return;
        }
        monitor.iniciar();
        if (atual.ehIdoso()) {
            carregar(() -> TelaHomeIdoso.buscar(api, atual), dados -> {
                usuario = dados.idoso();
                Pagina tela = new TelaHomeIdoso(this, dados);
                if (aviso != null) {
                    tela.aviso(aviso, Tom.OK);
                }
                return tela;
            });
        } else {
            carregar(() -> TelaHomeFamiliar.buscar(api, atual), dados -> {
                usuario = dados.familiar();
                Pagina tela = new TelaHomeFamiliar(this, dados);
                if (aviso != null) {
                    tela.aviso(aviso, Tom.OK);
                }
                return tela;
            });
        }
    }
}
