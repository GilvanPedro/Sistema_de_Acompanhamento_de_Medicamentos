package br.com.adapter.in.gui;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.function.Consumer;

import javax.swing.ButtonGroup;
import javax.swing.JTextField;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.api.Conta;
import br.com.adapter.in.gui.api.Remedio;
import br.com.domain.model.TipoMedicamento;

/** Formulário para cadastrar ou editar um remédio, sem digitar hora nem dia: tudo por botões. */
class TelaFormMedicamento extends Pagina {

    TelaFormMedicamento(Navegador nav, Conta idoso, Remedio existente, Consumer<String> aoVoltar) {
        super(existente == null ? "Novo remédio" : "Editar remédio", null, () -> aoVoltar.accept(null));

        JTextField nome = Campo.texto();
        TipoMedicamento[] tipos = TipoMedicamento.values();
        Escolha[] botoesTipo = new Escolha[tipos.length];
        ButtonGroup grupoTipo = new ButtonGroup();
        for (int i = 0; i < tipos.length; i++) {
            botoesTipo[i] = new Escolha(Rotulos.tipo(tipos[i]));
            grupoTipo.add(botoesTipo[i]);
        }

        DayOfWeek[] dias = DayOfWeek.values();
        Escolha[] botoesDia = new Escolha[dias.length];
        ButtonGroup grupoDia = new ButtonGroup();
        for (int i = 0; i < dias.length; i++) {
            botoesDia[i] = new Escolha(Rotulos.diaCurto(dias[i]));
            grupoDia.add(botoesDia[i]);
        }

        Contador hora = new Contador("Hora", 0, 23, 1, 8);
        Contador minuto = new Contador("Minutos", 0, 59, 5, 0);

        if (existente == null) {
            botoesTipo[0].setSelected(true);
        } else {
            nome.setText(existente.nome());
            botoesTipo[existente.tipo().ordinal()].setSelected(true);
            botoesDia[existente.dia().ordinal()].setSelected(true);
            hora.definir(existente.horario().getHour());
            minuto.definir(existente.horario().getMinute());
        }

        Botao salvar = Botao.primario(existente == null ? "Salvar remédio" : "Salvar mudanças");
        salvar.addActionListener(e -> {
            int diaEscolhido = -1;
            int tipoEscolhido = -1;
            for (int i = 0; i < botoesDia.length; i++) {
                if (botoesDia[i].isSelected()) {
                    diaEscolhido = i;
                }
            }
            for (int i = 0; i < botoesTipo.length; i++) {
                if (botoesTipo[i].isSelected()) {
                    tipoEscolhido = i;
                }
            }
            if (nome.getText().isBlank()) {
                aviso("Escreva o nome do remédio.", Tom.AVISO);
                nome.requestFocusInWindow();
                return;
            }
            if (diaEscolhido < 0) {
                aviso("Escolha o dia da semana em que você toma este remédio.", Tom.AVISO);
                return;
            }
            LocalTime horario = LocalTime.of(hora.valor(), minuto.valor());
            String nomeFinal = nome.getText().trim();
            DayOfWeek dia = dias[diaEscolhido];
            TipoMedicamento tipo = tipos[tipoEscolhido];
            nav.fazer(() -> existente == null
                            ? nav.api().cadastrarRemedio(idoso.id(), nomeFinal, dia, horario, tipo)
                            : nav.api().editarRemedio(existente.id(), nomeFinal, dia, horario, tipo),
                    salvo -> aoVoltar.accept(nomeFinal + " foi salvo."),
                    erro -> aviso(erro.getMessage(), Tom.ERRO));
        });
        Botao cancelar = Botao.secundario("Cancelar");
        cancelar.addActionListener(e -> aoVoltar.accept(null));

        Cartao form = new Cartao();
        form.add(Ui.pilha(6, Texto.rotulo("Nome do remédio"), nome));
        form.add(Ui.pilha(6, Texto.rotulo("Como é o remédio?"), Ui.linha(4, botoesTipo)));
        form.add(Ui.pilha(6, Texto.rotulo("Em que dia da semana?"), Ui.linha(4, botoesDia)));
        form.add(Ui.pilha(6, Texto.rotulo("A que horas?"), Ui.lado(hora, minuto)));
        adicionar(form);
        adicionar(salvar);
        adicionar(cancelar);

        focoInicial(nome);
        botaoPadrao(salvar);
    }
}
