package br.com.cuidamed

import br.com.cuidamed.data.HistoricoDto
import br.com.cuidamed.data.MedicamentoDto
import br.com.cuidamed.data.local.RegistrarTomada
import br.com.cuidamed.data.local.Visao
import br.com.cuidamed.ui.telas.agruparRemedios
import br.com.cuidamed.ui.telas.grupoDoRemedio
import br.com.cuidamed.ui.telas.rotuloDosDias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Um remédio que se toma em vários dias é um registro por dia no servidor, mas um só para a pessoa. */
class RemediosEmVariosDiasTest {

    private fun remedio(id: Int, dia: String, nome: String = "Losartana", horario: String = "08:00", tipo: String = "COMPRIMIDO") =
        MedicamentoDto(id, 10, nome, horario, dia, tipo)

    @Test
    fun registrosDeMesmoNomeTipoEHorarioViramUmRemedioSo() {
        val lista = listOf(remedio(1, "FRIDAY"), remedio(2, "MONDAY"), remedio(3, "MONDAY", nome = "Insulina", horario = "20:00"), remedio(4, "WEDNESDAY"))
        val grupos = agruparRemedios(lista)
        assertEquals(listOf("Losartana", "Insulina"), grupos.map { it.nome })
        assertEquals(listOf("MONDAY", "WEDNESDAY", "FRIDAY"), grupos[0].remedios.map { it.diaSemana })
    }

    @Test
    fun horarioOuTipoDiferentesNaoSeJuntam() {
        val lista = listOf(remedio(1, "MONDAY"), remedio(2, "TUESDAY", horario = "09:00"), remedio(3, "WEDNESDAY", tipo = "GOTAS"))
        assertEquals(3, agruparRemedios(lista).size)
    }

    @Test
    fun ograpoDeUmRegistroEAchadoPeloIdDeQualquerDia() {
        val lista = listOf(remedio(1, "MONDAY"), remedio(2, "TUESDAY"))
        assertEquals(setOf("MONDAY", "TUESDAY"), grupoDoRemedio(lista, 2)!!.dias)
        assertNull(grupoDoRemedio(lista, 99))
    }

    @Test
    fun oJaTomeiVaiParaORegistroDeHojeOuParaOPrimeiro() {
        val grupo = agruparRemedios(listOf(remedio(1, "MONDAY"), remedio(2, "WEDNESDAY"))).single()
        assertEquals(2, grupo.paraTomar("WEDNESDAY").id)
        assertEquals(1, grupo.paraTomar("SUNDAY").id)
    }

    @Test
    fun osDiasSaemEscritosDeFormaNatural() {
        assertEquals("Todos os dias", rotuloDosDias(setOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")))
        assertEquals("Segunda a sexta", rotuloDosDias(setOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")))
        assertEquals("Sábado e domingo", rotuloDosDias(setOf("SUNDAY", "SATURDAY")))
        assertEquals("Segunda, quarta e sexta", rotuloDosDias(setOf("FRIDAY", "MONDAY", "WEDNESDAY")))
        assertEquals("Terça", rotuloDosDias(setOf("TUESDAY")))
    }

    @Test
    fun umNaoTomouDoServidorSomeQuandoUmaTomadaLocalJaCobreOHorario() {
        val falta = HistoricoDto(-500, 1, "Losartana", "2026-09-21T08:00:00", false)
        val outraFalta = HistoricoDto(-501, 1, "Losartana", "2026-09-14T08:00:00", false)
        val pendencia = RegistrarTomada("k", 10, 1, "Losartana", "2026-09-21T14:00:00", "2026-09-21T14:00:00")
        val visao = Visao.historico(listOf(falta, outraFalta), listOf(pendencia), 10)
        assertTrue(visao.none { it.id == -500 })
        assertTrue(visao.any { it.id == -501 })
        assertTrue(visao.any { it.foiTomado })
    }
}
