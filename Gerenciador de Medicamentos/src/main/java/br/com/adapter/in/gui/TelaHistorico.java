package br.com.adapter.in.gui;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.adapter.in.gui.api.Conta;
import br.com.adapter.in.gui.api.Tomada;

/** Histórico de tomadas: o que foi tomado (verde) e o que foi esquecido (vermelho), do mais novo ao mais antigo. */
class TelaHistorico extends Pagina {

    private static final int LIMITE = 60;
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    static void abrir(Navegador nav, Conta idoso, Runnable voltar) {
        nav.carregar(() -> nav.api().historico(idoso.id()).stream()
                        .sorted(Comparator.comparing(Tomada::dataHora).reversed())
                        .toList(),
                historico -> new TelaHistorico(idoso, historico, voltar));
    }

    TelaHistorico(Conta idoso, List<Tomada> historico, Runnable voltar) {
        super("Histórico", "Remédios de " + idoso.nome() + ", do mais recente ao mais antigo.", voltar);
        if (historico.isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Ainda não há nada registrado."));
            adicionar(vazio);
            return;
        }
        for (Tomada h : historico.stream().limit(LIMITE).toList()) {
            Cartao cartao = new Cartao(h.foiTomado() ? Tom.OK : Tom.ERRO);
            cartao.add(new Texto(h.remedioNome(), 26, true, Papel.TEXTO));
            cartao.add(new Texto((h.foiTomado() ? "Tomou" : "Não tomou") + "  ·  " + h.dataHora().format(FORMATO),
                    22, false, Papel.TEXTO));
            adicionar(cartao);
        }
        if (historico.size() > LIMITE) {
            adicionar(Texto.suave("Mostrando os " + LIMITE + " registros mais recentes."));
        }
    }
}
