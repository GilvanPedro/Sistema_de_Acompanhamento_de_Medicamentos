package br.com.cuidamed.data.local

import br.com.cuidamed.data.HistoricoDto
import br.com.cuidamed.data.MedicamentoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** O que a tela mostra com alterações pendentes por cima, e os avisos do dia calculados no aparelho. */
class VisaoTest {

    private val losartana = MedicamentoDto(1, 7, "Losartana", "08:00", "MONDAY", "COMPRIMIDO")
    private val metformina = MedicamentoDto(2, 7, "Metformina", "12:00", "TUESDAY", "COMPRIMIDO")

    private fun criar(idLocal: Int, nome: String) = CriarRemedio("c$idLocal", 7, idLocal, nome, "FRIDAY", "20:00", "GOTAS", "x")

    @Test
    fun umCadastroPendenteAparecePorCimaDaCopiaDoServidor() {
        val lista = Visao.remedios(listOf(losartana), listOf(criar(-1, "Atenolol")), 7)
        assertEquals(listOf("Losartana", "Atenolol"), lista.map { it.nome })
        assertEquals(-1, lista.last().id)
    }

    @Test
    fun edicaoEExclusaoPendentesValemNaHora() {
        val pend = listOf(
            EditarRemedio("e1", 7, 1, "Losartana", nome = "Losartana 50mg", horario = "09:30", criadaEm = "x"),
            ExcluirRemedio("x1", 7, 2, "Metformina", "x"),
        )
        val lista = Visao.remedios(listOf(losartana, metformina), pend, 7)
        assertEquals(1, lista.size)
        assertEquals("Losartana 50mg", lista.single().nome)
        assertEquals("09:30", lista.single().horario)
        assertEquals("MONDAY", lista.single().diaSemana) // o que não foi editado continua
    }

    @Test
    fun pendenciasDeOutroIdosoNaoAparecem() {
        val lista = Visao.remedios(listOf(losartana), listOf(criar(-1, "Atenolol").copy(idosoId = 99)), 7)
        assertEquals(1, lista.size)
    }

    @Test
    fun tomadaPendenteAparecenoHistorico() {
        val base = listOf(HistoricoDto(10, 1, "Losartana", "2026-09-18T08:00:00", true))
        val pend = listOf(RegistrarTomada("t1", 7, 1, "Losartana", "2026-09-19T08:05:00", "x"))
        val h = Visao.historico(base, pend, 7)
        assertEquals(2, h.size)
        assertEquals("2026-09-19T08:05:00", h.last().dataHora)
        assertTrue(h.last().id < 0)
    }

    // 21/09/2026 é uma segunda-feira

    @Test
    fun avisosLembreteEsquecidoETomado() {
        fun avisos(agora: LocalDateTime, historico: List<HistoricoDto> = emptyList()) = Visao.avisos(listOf(losartana), historico, agora).map { it.tipo }
        assertEquals(emptyList<String>(), avisos(LocalDateTime.of(2026, 9, 21, 7, 59)))
        assertEquals(listOf("LEMBRETE"), avisos(LocalDateTime.of(2026, 9, 21, 8, 5)))
        assertEquals(listOf("ESQUECIDO"), avisos(LocalDateTime.of(2026, 9, 21, 8, 30)))
        val tomou = listOf(HistoricoDto(1, 1, "Losartana", "2026-09-21T08:20:00", true))
        assertEquals(listOf("TOMADO"), avisos(LocalDateTime.of(2026, 9, 21, 9, 0), tomou))
    }

    @Test
    fun umaTomadaMarcadaSemInternetJaAlteraOAvisoDoDia() {
        val pend = listOf(RegistrarTomada("t1", 7, 1, "Losartana", "2026-09-21T08:12:00", "x"))
        val historico = Visao.historico(emptyList(), pend, 7)
        val avisos = Visao.avisos(listOf(losartana), historico, LocalDateTime.of(2026, 9, 21, 8, 30))
        assertEquals(listOf("TOMADO"), avisos.map { it.tipo })
    }

    @Test
    fun remedioDasVinteETresECinquentaAindaContaDepoisDaMeiaNoite() {
        val noite = losartana.copy(horario = "23:50")
        val avisos = Visao.avisos(listOf(noite), emptyList(), LocalDateTime.of(2026, 9, 22, 0, 5))
        assertEquals(listOf("ESQUECIDO"), avisos.map { it.tipo })
        assertTrue(Visao.avisos(listOf(noite), emptyList(), LocalDateTime.of(2026, 9, 22, 3, 30)).isEmpty())
        // tomado depois da meia-noite cobre o horário de ontem, e não gera aviso nenhum
        val tomou = listOf(HistoricoDto(1, 1, "Losartana", "2026-09-22T00:10:00", true))
        assertTrue(Visao.avisos(listOf(noite), tomou, LocalDateTime.of(2026, 9, 22, 1, 0)).isEmpty())
    }
}
