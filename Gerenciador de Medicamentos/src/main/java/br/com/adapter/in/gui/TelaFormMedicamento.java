package br.com.adapter.in.gui;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.function.Consumer;

import javax.swing.ButtonGroup;
import javax.swing.JTextField;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.application.service.EditarMedicamentoService;
import br.com.application.service.RegistrarMedicamentoService;
import br.com.config.AppConfig;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.TipoMedicamento;

/** Formulário para cadastrar ou editar um remédio, sem digitar hora nem dia: tudo por botões. */
class TelaFormMedicamento extends Pagina {

    TelaFormMedicamento(Navegador nav, Idoso idoso, Medicamento existente, Consumer<String> aoVoltar) {
        super(existente == null ? "Novo remédio" : "Editar remédio", null, () -> aoVoltar.accept(null));
        RegistrarMedicamentoService registrar = AppConfig.criarRegistrarMedicamentoService();
        EditarMedicamentoService editar = AppConfig.criarEditarMedicamentoService();

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
            nome.setText(existente.getNome());
            botoesTipo[existente.getTipoMedicamento().ordinal()].setSelected(true);
            botoesDia[existente.getDiaSemana().ordinal()].setSelected(true);
            hora.definir(existente.getHorarioMedicamento().getHour());
            minuto.definir(existente.getHorarioMedicamento().getMinute());
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
            try {
                if (existente == null) {
                    registrar.registrarMedicamento(nomeFinal, dias[diaEscolhido], horario, tipos[tipoEscolhido], idoso.getId());
                } else {
                    editar.editarMedicamento(existente.getId(), nomeFinal, horario, dias[diaEscolhido], tipos[tipoEscolhido]);
                }
                aoVoltar.accept(nomeFinal + " foi salvo.");
            } catch (RuntimeException ex) {
                aviso(Rotulos.erro(ex), Tom.ERRO);
            }
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
