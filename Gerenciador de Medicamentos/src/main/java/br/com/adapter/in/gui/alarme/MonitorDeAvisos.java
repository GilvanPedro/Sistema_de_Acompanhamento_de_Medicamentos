package br.com.adapter.in.gui.alarme;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingWorker;
import javax.swing.Timer;

import br.com.adapter.in.gui.Navegador;
import br.com.adapter.in.gui.api.ApiException;
import br.com.adapter.in.gui.api.Aviso;
import br.com.adapter.in.gui.api.ClienteApi;
import br.com.adapter.in.gui.api.Conta;
import br.com.adapter.in.gui.api.Pedido;
import br.com.domain.model.TipoNotificacao;

/**
 * Verificação periódica em segundo plano — equivalente ao {@code VerificadorDeEventos} do app Android: enquanto a
 * pessoa está logada, busca os avisos na mesma API a cada minuto e aciona o alarme (idoso) ou a notificação do
 * sistema (familiar, pedidos de vínculo), mesmo que a tela inicial não esteja aberta no momento.
 */
public final class MonitorDeAvisos {

    private static final int INTERVALO_MS = 60_000;

    private final Navegador nav;
    private final Timer timer;
    /** Chaves dos avisos já alarmados/notificados nesta sessão, para não repetir a cada verificação. */
    private final Set<String> jaAvisados = new HashSet<>();
    private JanelaDeAlarme janela;
    private boolean emExecucao;

    public MonitorDeAvisos(Navegador nav) {
        this.nav = nav;
        this.timer = new Timer(INTERVALO_MS, e -> verificar());
        this.timer.setInitialDelay(5_000);
    }

    /** Começa a verificar (sem efeito se já estiver rodando: não perde o que já foi avisado nesta sessão). */
    public void iniciar() {
        if (timer.isRunning()) {
            return;
        }
        timer.start();
        verificar();
    }

    /** Para de verificar (ao sair da conta ou perder a sessão) e fecha um alarme que porventura esteja tocando. */
    public void parar() {
        timer.stop();
        jaAvisados.clear();
        if (janela != null) {
            janela.fecharSemAvisar();
            janela = null;
        }
    }

    private void verificar() {
        Conta usuario = nav.usuario();
        if (usuario == null || emExecucao) {
            return;
        }
        emExecucao = true;
        ClienteApi api = nav.api();
        new SwingWorker<Void, Void>() {
            private List<Aviso> avisosIdoso;
            private List<Pedido> pedidos;
            private List<Conta> idosos;
            private Map<Integer, List<Aviso>> avisosPorIdoso;

            @Override
            protected Void doInBackground() {
                try {
                    if (usuario.ehIdoso()) {
                        avisosIdoso = api.avisos(usuario.id());
                        pedidos = api.pedidosRecebidos();
                    } else {
                        idosos = api.meusIdosos();
                        avisosPorIdoso = new LinkedHashMap<>();
                        for (Conta idoso : idosos) {
                            avisosPorIdoso.put(idoso.id(), api.avisos(idoso.id()));
                        }
                    }
                } catch (ApiException e) {
                    // sem conexão, servidor descansando, sessão perdida etc.: só tenta de novo na próxima verificação
                }
                return null;
            }

            @Override
            protected void done() {
                emExecucao = false;
                Conta atual = nav.usuario();
                if (atual == null || atual.id() != usuario.id()) {
                    return; // saiu da conta, ou trocou de usuário, enquanto isso verificava
                }
                if (usuario.ehIdoso()) {
                    if (avisosIdoso != null) {
                        processarIdoso(avisosIdoso, pedidos == null ? List.of() : pedidos);
                    }
                } else if (avisosPorIdoso != null) {
                    processarFamiliar(idosos, avisosPorIdoso);
                }
            }
        }.execute();
    }

    private void processarIdoso(List<Aviso> avisos, List<Pedido> pedidos) {
        LocalDate hoje = LocalDate.now();
        List<JanelaDeAlarme.Item> itens = new ArrayList<>();
        boolean novidade = false;
        for (Aviso a : avisos) {
            if (a.tipo() == TipoNotificacao.TOMADO) {
                continue;
            }
            String chave = (a.tipo() == TipoNotificacao.LEMBRETE ? "L|" : "E|") + a.remedio().id() + "|" + hoje;
            if (jaAvisados.add(chave)) {
                novidade = true;
            }
            itens.add(new JanelaDeAlarme.Item(a.remedio().id(), a.remedio().nome(), a.remedio().horario().toString(),
                    a.tipo() == TipoNotificacao.ESQUECIDO));
        }
        if (!itens.isEmpty() && (novidade || janela != null)) {
            mostrarAlarme(itens, novidade);
        } else if (itens.isEmpty() && janela != null) {
            janela.fecharSemAvisar();
            janela = null;
        }
        for (Pedido p : pedidos) {
            String chave = "P|" + p.familiar().id() + "|" + p.solicitadoEm();
            if (jaAvisados.add(chave)) {
                NotificacaoDoSistema.mostrar("Novo pedido de acompanhamento",
                        p.familiar().nome() + " quer acompanhar você. Abra o CuidaMed para aceitar ou recusar.");
            }
        }
    }

    private void processarFamiliar(List<Conta> idosos, Map<Integer, List<Aviso>> avisosPorIdoso) {
        LocalDate hoje = LocalDate.now();
        for (Conta idoso : idosos) {
            for (Aviso a : avisosPorIdoso.getOrDefault(idoso.id(), List.of())) {
                switch (a.tipo()) {
                    case ESQUECIDO -> {
                        String chave = "E|" + idoso.id() + "|" + a.remedio().id() + "|" + hoje;
                        if (jaAvisados.add(chave)) {
                            NotificacaoDoSistema.mostrar("Remédio não tomado", idoso.nome() + " ainda não tomou "
                                    + a.remedio().nome() + ". Era para as " + a.remedio().horario() + ".");
                        }
                    }
                    case TOMADO -> {
                        String chave = "T|" + idoso.id() + "|" + a.remedio().id() + "|" + hoje;
                        if (jaAvisados.add(chave)) {
                            NotificacaoDoSistema.mostrar("Remédio tomado", idoso.nome() + " já tomou " + a.remedio().nome() + ".");
                        }
                    }
                    case LEMBRETE -> { }
                }
            }
        }
    }

    private void mostrarAlarme(List<JanelaDeAlarme.Item> itens, boolean tocarSom) {
        if (janela == null) {
            janela = new JanelaDeAlarme(nav, this::aoTomar, () -> janela = null);
        }
        janela.atualizar(itens, tocarSom);
    }

    /** Chamado pelo botão "Já tomei" da janela do alarme: registra a tomada e atualiza a lista de avisos na hora. */
    private void aoTomar(int medicamentoId) {
        ClienteApi api = nav.api();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    api.registrarTomada(medicamentoId);
                } catch (ApiException e) {
                    // se falhar, a próxima verificação periódica mostra o aviso de novo (nada fica perdido)
                }
                return null;
            }

            @Override
            protected void done() {
                verificar();
            }
        }.execute();
    }
}
