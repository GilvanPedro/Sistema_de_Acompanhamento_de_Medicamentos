package br.com.adapter.in.gui;

import java.time.format.DateTimeFormatter;
import java.util.List;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.application.service.RegistrarTomadaService;
import br.com.config.AppConfig;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;

/** O idoso escolhe qual remédio acabou de tomar. */
class TelaMarcarTomado extends Pagina {

    TelaMarcarTomado(Navegador nav, Idoso idoso, Runnable voltar) {
        super("Qual remédio você tomou?", "Toque no botão verde do remédio que você tomou agora.", voltar);
        RegistrarTomadaService registrarTomada = AppConfig.criarRegistrarTomadaService();

        List<Medicamento> medicamentos = TelaMedicamentos.doIdoso(idoso);
        if (medicamentos.isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo("Você ainda não cadastrou nenhum remédio."));
            adicionar(vazio);
            Botao cadastrar = Botao.primario("Cadastrar um remédio");
            cadastrar.addActionListener(e -> nav.mostrar(new TelaMedicamentos(nav, idoso, voltar, null)));
            adicionar(cadastrar);
            return;
        }

        for (Medicamento m : medicamentos) {
            Cartao cartao = new Cartao();
            cartao.add(new Texto(m.getNome(), 26, true, Papel.TEXTO));
            cartao.add(Texto.suave(Rotulos.detalhe(m)));
            Botao tomei = Botao.sucesso("Tomei este remédio");
            tomei.addActionListener(e -> {
                try {
                    HistoricoMedicamento h = registrarTomada.registrarTomada(idoso, m, true);
                    aviso("Anotado! Você tomou " + m.getNome() + " às "
                            + h.getDataHoraTomada().format(DateTimeFormatter.ofPattern("HH:mm")) + ".", Tom.OK);
                } catch (RuntimeException ex) {
                    aviso(Rotulos.erro(ex), Tom.AVISO);
                }
            });
            cartao.add(tomei);
            adicionar(cartao);
        }
    }
}
