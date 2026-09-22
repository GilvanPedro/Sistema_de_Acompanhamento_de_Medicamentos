package br.com.cuidamed.data.local

import br.com.cuidamed.data.MedicamentoDto
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * As mesmas regras de horário do servidor (OcorrenciasMedicamento), para o app mostrar os avisos do dia mesmo sem
 * internet: quais horários previstos ainda importam agora e se uma tomada cobre um deles. Considera a virada da
 * meia-noite (um remédio das 23h50 ainda conta como atrasado às 00h05).
 */
object HorariosDeRemedio {

    /** Depois disso sem marcar, o remédio é "esquecido": o alarme para de insistir por esse dia (ADR-0065). */
    const val TOLERANCIA_MINUTOS = 60L
    val PRAZO_APOS_MEIA_NOITE: Duration = Duration.ofHours(3)

    fun relevantes(remedio: MedicamentoDto, agora: LocalDateTime): List<LocalDateTime> {
        val dia = try {
            DayOfWeek.valueOf(remedio.diaSemana)
        } catch (e: IllegalArgumentException) {
            return emptyList()
        }
        val hora = try {
            LocalTime.parse(remedio.horario)
        } catch (e: Exception) {
            return emptyList()
        }
        val resultado = mutableListOf<LocalDateTime>()
        for (diasAtras in 1 downTo 0) {
            val data: LocalDate = agora.toLocalDate().minusDays(diasAtras.toLong())
            if (data.dayOfWeek != dia) continue
            val previsto = LocalDateTime.of(data, hora)
            if (previsto.isAfter(agora)) continue
            if (diasAtras == 1 && Duration.between(previsto, agora) > PRAZO_APOS_MEIA_NOITE) continue
            resultado.add(previsto)
        }
        return resultado
    }

    fun tomadaCobre(tomada: LocalDateTime, previsto: LocalDateTime): Boolean {
        if (tomada.toLocalDate() == previsto.toLocalDate()) return true
        return !tomada.isBefore(previsto) && !tomada.isAfter(previsto.plus(PRAZO_APOS_MEIA_NOITE))
    }

    fun minutosDeAtraso(previsto: LocalDateTime, agora: LocalDateTime): Long = Duration.between(previsto, agora).toMinutes()
}
