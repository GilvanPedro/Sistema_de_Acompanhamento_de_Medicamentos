package br.com.cuidamed.data.local

import br.com.cuidamed.data.HistoricoDto
import br.com.cuidamed.data.MedicamentoDto
import br.com.cuidamed.data.NotificacaoDto
import java.time.LocalDateTime

/**
 * O que a tela mostra: a última cópia do servidor com as alterações ainda não enviadas aplicadas por cima. Assim, o que a
 * pessoa acabou de fazer aparece na hora, com ou sem internet.
 */
object Visao {

    fun remedios(base: List<MedicamentoDto>, pendencias: List<Operacao>, idosoId: Int): List<MedicamentoDto> {
        var lista = base
        for (op in pendencias) {
            if (op.idosoId != idosoId) continue
            lista = when (op) {
                is CriarRemedio ->
                    if (lista.any { it.id == op.idLocal }) lista
                    else lista + MedicamentoDto(op.idLocal, op.idosoId, op.nome, op.horario, op.diaSemana, op.tipo)
                is EditarRemedio -> lista.map {
                    if (it.id != op.remedioId) it
                    else it.copy(
                        nome = op.nome ?: it.nome,
                        diaSemana = op.diaSemana ?: it.diaSemana,
                        horario = op.horario ?: it.horario,
                        tipo = op.tipo ?: it.tipo,
                    )
                }
                is ExcluirRemedio -> lista.filter { it.id != op.remedioId }
                is RegistrarTomada -> lista
            }
        }
        return lista
    }

    /** As tomadas ainda não enviadas aparecem no histórico e contam para os avisos, com um id local negativo. */
    fun historico(base: List<HistoricoDto>, pendencias: List<Operacao>, idosoId: Int): List<HistoricoDto> {
        val tomadas = pendencias.filterIsInstance<RegistrarTomada>().filter { it.idosoId == idosoId }
        val locais = tomadas.mapIndexed { i, t -> HistoricoDto(-(i + 1), t.remedioId, t.nomeDoRemedio, t.quando, true) }
        // Um "não tomou" que o servidor calculou some quando uma tomada ainda não enviada já o cobre (o servidor confirma depois).
        val semFaltasCobertas = base.filterNot { h ->
            !h.foiTomado && tomadas.any { t ->
                t.remedioId == h.medicamentoId && try {
                    HorariosDeRemedio.tomadaCobre(LocalDateTime.parse(t.quando), LocalDateTime.parse(h.dataHora))
                } catch (e: Exception) {
                    false
                }
            }
        }
        return semFaltasCobertas + locais
    }

    /** Os avisos do dia calculados no aparelho (o servidor calcula o mesmo, com as mesmas regras). */
    fun avisos(remedios: List<MedicamentoDto>, historico: List<HistoricoDto>, agora: LocalDateTime): List<NotificacaoDto> {
        val hoje = agora.toLocalDate()
        val resultado = mutableListOf<NotificacaoDto>()
        for (remedio in remedios) {
            for (previsto in HorariosDeRemedio.relevantes(remedio, agora)) {
                val tomado = historico.any { h ->
                    h.medicamentoId == remedio.id && h.foiTomado && try {
                        HorariosDeRemedio.tomadaCobre(LocalDateTime.parse(h.dataHora), previsto)
                    } catch (e: Exception) {
                        false
                    }
                }
                if (tomado) {
                    if (previsto.toLocalDate() == hoje) resultado.add(NotificacaoDto("TOMADO", remedio))
                    continue
                }
                val atraso = HorariosDeRemedio.minutosDeAtraso(previsto, agora)
                resultado.add(NotificacaoDto(if (atraso > HorariosDeRemedio.TOLERANCIA_MINUTOS) "ESQUECIDO" else "LEMBRETE", remedio))
            }
        }
        return resultado
    }
}
