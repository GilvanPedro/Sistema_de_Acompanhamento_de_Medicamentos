package br.com.cuidamed

import br.com.cuidamed.data.MedicamentoDto
import br.com.cuidamed.notificacoes.AgendadorDeLembretes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** A conta de "quando toca o próximo lembrete". 19/09/2026 é um sábado. */
class HorariosDeLembreteTest {

    private val zona = ZoneId.of("America/Sao_Paulo")

    private fun remedio(dia: String, horario: String) = MedicamentoDto(1, 10, "Losartana", horario, dia, "COMPRIMIDO")

    private fun em(dia: Int, hora: Int, minuto: Int) = ZonedDateTime.of(2026, 9, dia, hora, minuto, 0, 0, zona)

    @Test
    fun ehHojeSeOHorarioAindaNaoPassou() {
        val proxima = AgendadorDeLembretes.proximaOcorrencia(remedio("SATURDAY", "18:30"), em(19, 13, 0))
        assertEquals(em(19, 18, 30), proxima)
    }

    @Test
    fun ehNaSemanaQueVemSeOHorarioDeHojeJaPassou() {
        val proxima = AgendadorDeLembretes.proximaOcorrencia(remedio("SATURDAY", "08:00"), em(19, 13, 0))
        assertEquals(em(26, 8, 0), proxima)
    }

    @Test
    fun noExatoMomentoDoHorarioJaVaiParaASemanaQueVem() {
        val proxima = AgendadorDeLembretes.proximaOcorrencia(remedio("SATURDAY", "13:00"), em(19, 13, 0))
        assertEquals(em(26, 13, 0), proxima)
    }

    @Test
    fun outroDiaDaSemanaCaiNoProximoDiaCerto() {
        // sábado 19 -> a próxima terça é dia 22; a próxima sexta é dia 25; o próximo domingo é dia 20
        assertEquals(em(22, 19, 0), AgendadorDeLembretes.proximaOcorrencia(remedio("TUESDAY", "19:00"), em(19, 13, 0)))
        assertEquals(em(25, 7, 15), AgendadorDeLembretes.proximaOcorrencia(remedio("FRIDAY", "07:15"), em(19, 13, 0)))
        assertEquals(em(20, 0, 5), AgendadorDeLembretes.proximaOcorrencia(remedio("SUNDAY", "00:05"), em(19, 23, 50)))
    }

    @Test
    fun dadosInvalidosNaoQuebram() {
        assertNull(AgendadorDeLembretes.proximaOcorrencia(remedio("FUNDAY", "08:00"), em(19, 13, 0)))
        assertNull(AgendadorDeLembretes.proximaOcorrencia(remedio("MONDAY", "8 da manhã"), em(19, 13, 0)))
    }
}
