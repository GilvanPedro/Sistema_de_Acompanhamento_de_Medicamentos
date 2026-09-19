package br.com.adapter.in.web.push;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import br.com.application.service.VerificarNotificacoesIdosoService;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.TipoNotificacao;
import br.com.domain.port.out.SalvarUsuarioPort;

/**
 * Procura remédios esquecidos e manda o push aos familiares. Quem chama é um agendador externo (o servidor gratuito
 * hiberna e não tem cron próprio). O app, ao receber o push, busca os avisos e mostra "não tomou" como sempre.
 * Cada remédio esquecido é avisado uma vez por dia.
 */
public class VerificadorDeAtrasos {

    private final SalvarUsuarioPort usuarios;
    private final VerificarNotificacoesIdosoService notificacoes;
    private final AvisosDeAtraso jaAvisados;
    private final NotificadorPush push;
    private final Clock relogio;

    public VerificadorDeAtrasos(SalvarUsuarioPort usuarios, VerificarNotificacoesIdosoService notificacoes,
                                AvisosDeAtraso jaAvisados, NotificadorPush push, Clock relogio) {
        this.usuarios = usuarios;
        this.notificacoes = notificacoes;
        this.jaAvisados = jaAvisados;
        this.push = push;
        this.relogio = relogio;
    }

    /** Devolve quantos remédios esquecidos foram avisados agora (os que já tinham sido avisados não contam). */
    public int verificar() {
        LocalDate hoje = LocalDate.now(relogio);
        int avisados = 0;
        for (var usuario : usuarios.listarTodos()) {
            if (!(usuario instanceof Idoso idoso) || idoso.getFamiliares().isEmpty()) {
                continue; // sem familiar vinculado não há quem avisar (o idoso tem o alarme no próprio celular)
            }
            Set<Integer> quemAvisar = new LinkedHashSet<>();
            for (var n : notificacoes.verificarNotificacoes(idoso)) {
                if (n.getTipo() == TipoNotificacao.ESQUECIDO && jaAvisados.marcarSeNovo(n.getMedicamento().getId(), hoje)) {
                    avisados++;
                    for (Familiar f : idoso.getFamiliares()) {
                        quemAvisar.add(f.getId());
                    }
                }
            }
            quemAvisar.forEach(push::avisarNovidade); // um push por familiar, mesmo com vários remédios esquecidos
        }
        return avisados;
    }
}
