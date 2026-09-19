package br.com.adapter.in.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.application.service.ExcluirMedicamentoService;
import br.com.config.AppConfig;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;

/** Lista de remédios de um idoso, com adicionar, editar e excluir. Usada pelo idoso e pelo familiar. */
class TelaMedicamentos extends Pagina {

    TelaMedicamentos(Navegador nav, Idoso idoso, Runnable voltar, String mensagem) {
        super(nav.usuario().getId() == idoso.getId() ? "Meus remédios" : "Remédios de " + Rotulos.primeiroNome(idoso.getNome()),
                null, voltar);
        ExcluirMedicamentoService excluir = AppConfig.criarExcluirMedicamentoService();

        java.util.function.Consumer<String> aoVoltar = msg -> nav.mostrar(new TelaMedicamentos(nav, idoso, voltar, msg));

        Botao novo = Botao.primario("+  Adicionar remédio");
        novo.addActionListener(e -> nav.mostrar(new TelaFormMedicamento(nav, idoso, null, aoVoltar)));
        adicionar(novo);

        List<Medicamento> medicamentos = doIdoso(idoso);
        if (medicamentos.isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Nenhum remédio cadastrado ainda. Toque em \"Adicionar remédio\" para começar."));
            adicionar(vazio);
        }

        for (Medicamento m : medicamentos) {
            Cartao cartao = new Cartao();
            cartao.add(new Texto(m.getNome(), 26, true, Papel.TEXTO));
            cartao.add(Texto.suave(Rotulos.detalhe(m)));

            Botao editar = Botao.secundario("Editar");
            editar.addActionListener(e -> nav.mostrar(new TelaFormMedicamento(nav, idoso, m, aoVoltar)));
            Botao apagar = Botao.perigo("Excluir");
            apagar.addActionListener(e -> {
                boolean sim = Dialogos.confirmar(nav.janela(), "Excluir este remédio?",
                        "Você vai excluir " + m.getNome() + ". Isso não pode ser desfeito.", "Sim, excluir", "Não, manter");
                if (!sim) {
                    return;
                }
                try {
                    excluir.excluirMedicamento(m.getId());
                    aoVoltar.accept(m.getNome() + " foi excluído.");
                } catch (RuntimeException ex) {
                    aviso(Rotulos.erro(ex), Tom.ERRO);
                }
            });
            cartao.add(Ui.linha(2, editar, apagar));
            adicionar(cartao);
        }

        if (mensagem != null) {
            aviso(mensagem, Tom.OK);
        }
    }

    /** Remédios do idoso, na ordem da semana e do horário. */
    static List<Medicamento> doIdoso(Idoso idoso) {
        List<Medicamento> lista = new ArrayList<>(AppConfig.getMedicamentoPort().listarPorIdoso(idoso.getId()));
        lista.sort(Comparator.comparing(Medicamento::getDiaSemana).thenComparing(Medicamento::getHorarioMedicamento));
        return lista;
    }
}
