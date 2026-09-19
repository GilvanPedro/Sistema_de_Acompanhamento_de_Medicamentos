package br.com.adapter.in.web.push;

import java.time.LocalDate;

/** Registro dos atrasos que já foram avisados por push (um por remédio por dia). */
public interface AvisosDeAtraso {

    /** true se este remédio ainda não tinha sido avisado neste dia (e já o marca como avisado); false se já tinha. */
    boolean marcarSeNovo(int medicamentoId, LocalDate dia);
}
