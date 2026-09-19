package br.com.adapter.in.gui;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.application.service.BuscarHistoricoPorIdosoService;
import br.com.config.AppConfig;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;

/** Histórico de tomadas: o que foi tomado (verde) e o que foi esquecido (vermelho), do mais novo ao mais antigo. */
class TelaHistorico extends Pagina {

    private static final int LIMITE = 60;
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    TelaHistorico(Idoso idoso, Runnable voltar) {
        super("Histórico", "Remédios de " + idoso.getNome() + ", do mais recente ao mais antigo.", voltar);
        BuscarHistoricoPorIdosoService service = AppConfig.criarBuscarHistoricoPorIdosoService();

        List<HistoricoMedicamento> historico;
        try {
            historico = service.buscarHistoricoDoIdoso(idoso.getId()).stream()
                    .sorted(Comparator.comparing(HistoricoMedicamento::getDataHoraTomada).reversed())
                    .toList();
        } catch (RuntimeException e) {
            aviso(Rotulos.erro(e), Tom.ERRO);
            return;
        }

        if (historico.isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Ainda não há nada registrado."));
            adicionar(vazio);
            return;
        }

        for (HistoricoMedicamento h : historico.stream().limit(LIMITE).toList()) {
            Cartao cartao = new Cartao(h.isFoiTomado() ? Tom.OK : Tom.ERRO);
            cartao.add(new Texto(h.getMedicamento().getNome(), 26, true, Papel.TEXTO));
            cartao.add(new Texto((h.isFoiTomado() ? "Tomou" : "Não tomou") + "  ·  " + h.getDataHoraTomada().format(FORMATO),
                    22, false, Papel.TEXTO));
            adicionar(cartao);
        }
        if (historico.size() > LIMITE) {
            adicionar(Texto.suave("Mostrando os " + LIMITE + " registros mais recentes."));
        }
    }
}
