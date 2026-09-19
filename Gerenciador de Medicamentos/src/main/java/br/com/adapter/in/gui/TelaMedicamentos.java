package br.com.adapter.in.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.adapter.in.gui.api.ClienteApi;
import br.com.adapter.in.gui.api.Conta;
import br.com.adapter.in.gui.api.Remedio;

/** Lista de remédios de um idoso, com adicionar, editar e excluir. Usada pelo idoso e pelo familiar. */
class TelaMedicamentos extends Pagina {

    static void abrir(Navegador nav, Conta idoso, Runnable voltar, String mensagem) {
        nav.carregar(() -> doIdoso(nav.api(), idoso), lista -> new TelaMedicamentos(nav, idoso, lista, voltar, mensagem));
    }

    TelaMedicamentos(Navegador nav, Conta idoso, List<Remedio> medicamentos, Runnable voltar, String mensagem) {
        super(nav.usuario().id() == idoso.id() ? "Meus remédios" : "Remédios de " + Rotulos.primeiroNome(idoso.nome()),
                null, voltar);
        Consumer<String> aoVoltar = msg -> abrir(nav, idoso, voltar, msg);

        Botao novo = Botao.primario("+  Adicionar remédio");
        novo.addActionListener(e -> nav.mostrar(new TelaFormMedicamento(nav, idoso, null, aoVoltar)));
        adicionar(novo);

        if (medicamentos.isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Nenhum remédio cadastrado ainda. Toque em \"Adicionar remédio\" para começar."));
            adicionar(vazio);
        }
        for (Remedio m : medicamentos) {
            Cartao cartao = new Cartao();
            cartao.add(new Texto(m.nome(), 26, true, Papel.TEXTO));
            cartao.add(Texto.suave(Rotulos.detalhe(m)));
            Botao editar = Botao.secundario("Editar");
            editar.addActionListener(e -> nav.mostrar(new TelaFormMedicamento(nav, idoso, m, aoVoltar)));
            Botao apagar = Botao.perigo("Excluir");
            apagar.addActionListener(e -> {
                boolean sim = Dialogos.confirmar(nav.janela(), "Excluir este remédio?",
                        "Você vai excluir " + m.nome() + ". Isso não pode ser desfeito.", "Sim, excluir", "Não, manter");
                if (!sim) {
                    return;
                }
                nav.fazer(() -> {
                    nav.api().excluirRemedio(m.id());
                    return null;
                }, ok -> aoVoltar.accept(m.nome() + " foi excluído."), erro -> aviso(erro.getMessage(), Tom.ERRO));
            });
            cartao.add(Ui.linha(2, editar, apagar));
            adicionar(cartao);
        }
        if (mensagem != null) {
            aviso(mensagem, Tom.OK);
        }
    }

    /** Remédios do idoso, na ordem da semana e do horário. */
    static List<Remedio> doIdoso(ClienteApi api, Conta idoso) {
        List<Remedio> lista = new ArrayList<>(api.remedios(idoso.id()));
        lista.sort(Comparator.comparing(Remedio::dia).thenComparing(Remedio::horario));
        return lista;
    }
}
