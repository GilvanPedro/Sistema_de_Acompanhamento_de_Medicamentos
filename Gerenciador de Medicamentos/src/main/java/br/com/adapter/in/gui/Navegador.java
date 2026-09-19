package br.com.adapter.in.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.NoSuchElementException;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import br.com.adapter.in.gui.Tema.Papel;
import br.com.config.AppConfig;
import br.com.domain.model.Idoso;
import br.com.domain.model.Usuario;

/** Janela principal: barra superior (letra, modo claro/escuro, sair) e a tela atual. */
public class Navegador {

    private JFrame janela;
    private final Fundo palco = new Fundo(new BorderLayout());
    private final Botao letraMenor = Botao.barra("A−");
    private final Botao letraMaior = Botao.barra("A+");
    private final Botao modo = Botao.barra(textoModo());
    private final Botao sair = Botao.barra("Sair");
    private final JPanel barra = criarBarra();
    private Usuario usuario;

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

    public Usuario usuario() {
        return usuario;
    }

    public void entrar(Usuario novo) {
        usuario = novo;
        atualizarBarra();
        home();
    }

    public void sair() {
        usuario = null;
        atualizarBarra();
        inicio();
    }

    /** Recarrega o usuário do arquivo (para enxergar vínculos e dados novos). */
    public Usuario atualizarUsuario() {
        try {
            usuario = AppConfig.getUsuarioPort().buscarPorId(usuario.getId());
        } catch (NoSuchElementException e) {
            sair();
        }
        return usuario;
    }

    public void inicio() {
        mostrar(new TelaInicio(this));
    }

    public void home() {
        home(null);
    }

    public void home(String aviso) {
        Usuario atual = atualizarUsuario();
        if (atual == null) {
            return;
        }
        Pagina tela = atual instanceof Idoso idoso ? new TelaHomeIdoso(this, idoso) : new TelaHomeFamiliar(this, atual);
        if (aviso != null) {
            tela.aviso(aviso, Cartao.Tom.OK);
        }
        mostrar(tela);
    }
}
