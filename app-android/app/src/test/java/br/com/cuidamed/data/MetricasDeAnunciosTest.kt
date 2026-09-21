package br.com.cuidamed.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MetricasDeAnunciosTest {

    private class GuardaEmMemoria(var texto: String? = null) : GuardaDeContagens {
        override fun ler() = texto
        override fun salvar(texto: String) { this.texto = texto }
    }

    private val trabalho = Job()
    private val escopo = CoroutineScope(trabalho)
    private var dia = LocalDate.of(2026, 9, 21)
    private val enviados = mutableListOf<List<EventoDeAnuncioDto>>()
    private var funcionando = true

    // o envio automático (20 s depois) nunca dispara nos testes: aqui o envio é chamado à mão
    private fun novas(guarda: GuardaDeContagens = GuardaEmMemoria(), aoEnviar: (() -> Unit)? = null) = MetricasDeAnuncios(
        guarda = guarda,
        enviar = { eventos -> aoEnviar?.invoke(); enviados += eventos; funcionando },
        escopo = escopo,
        hoje = { dia },
        atrasoDoEnvioMs = 3_600_000,
    )

    @After
    fun encerrar() = trabalho.cancel()

    private fun todos() = enviados.flatten()

    @Test
    fun somaEventosIguaisEEnviaUmaLinhaPorBannerPosicaoPerfilEDia() = runBlocking {
        val m = novas()
        repeat(3) { m.registrar(TipoDeEvento.EXIBICAO, "padaria", "home-topo", "IDOSO") }
        m.registrar(TipoDeEvento.CLIQUE, "padaria", "home-topo", "IDOSO")
        m.registrar(TipoDeEvento.EXIBICAO, "padaria", "home-topo", "FAMILIAR")
        m.registrar(TipoDeEvento.EXIBICAO, "padaria", "home-fim", "IDOSO")
        m.registrar(TipoDeEvento.EXIBICAO, "farmacia", "home-topo", "IDOSO")
        assertEquals(7, m.pendentes())

        m.enviarAgora()

        assertEquals(0, m.pendentes())
        val porChave = todos().associate { listOf(it.tipo, it.anuncioId, it.posicao, it.perfil, it.dia) to it.quantidade }
        assertEquals(5, porChave.size)
        assertEquals(3, porChave[listOf("EXIBICAO", "padaria", "home-topo", "IDOSO", "2026-09-21")])
        assertEquals(1, porChave[listOf("CLIQUE", "padaria", "home-topo", "IDOSO", "2026-09-21")])
        assertEquals(1, porChave[listOf("EXIBICAO", "padaria", "home-topo", "FAMILIAR", "2026-09-21")])
    }

    @Test
    fun semNadaPendenteNaoChamaOServidor() = runBlocking {
        novas().enviarAgora()
        assertTrue(enviados.isEmpty())
    }

    @Test
    fun seOEnvioFalharAsContagensFicamEVaoNoProximo() = runBlocking {
        val m = novas()
        m.registrar(TipoDeEvento.EXIBICAO, "padaria", "home-fim", "IDOSO")
        funcionando = false
        m.enviarAgora()
        assertEquals(1, m.pendentes())

        m.registrar(TipoDeEvento.CLIQUE, "padaria", "home-fim", "IDOSO") // chegou depois da falha
        funcionando = true
        enviados.clear()
        m.enviarAgora()
        assertEquals(0, m.pendentes())
        assertEquals(2, todos().sumOf { it.quantidade })
    }

    @Test
    fun quantidadeAcimaDeCemViraVariosEventosParaOServidorAceitar() = runBlocking {
        val m = novas()
        repeat(250) { m.registrar(TipoDeEvento.EXIBICAO, "padaria", "home-fim", "IDOSO") }
        m.enviarAgora()
        assertEquals(listOf(100, 100, 50), todos().map { it.quantidade })
        assertEquals(0, m.pendentes())
    }

    @Test
    fun oQueChegaDuranteOEnvioNaoSePerde() = runBlocking {
        var m: MetricasDeAnuncios? = null
        var jaRegistrou = false
        m = novas(aoEnviar = {
            if (!jaRegistrou) { // outro evento entra no meio do primeiro envio
                jaRegistrou = true
                m!!.registrar(TipoDeEvento.EXIBICAO, "padaria", "home-fim", "IDOSO")
            }
        })
        m.registrar(TipoDeEvento.EXIBICAO, "padaria", "home-fim", "IDOSO")
        m.registrar(TipoDeEvento.EXIBICAO, "padaria", "home-fim", "IDOSO")

        m.enviarAgora()

        // o primeiro envio levou 2; o que entrou durante ele foi mantido e enviado em seguida: nada se perde
        assertEquals(2, enviados.size)
        assertEquals(3, todos().sumOf { it.quantidade })
        assertEquals(0, m.pendentes())
    }

    @Test
    fun aoReabrirOAppAsContagensPendentesSaoRecuperadas() = runBlocking {
        val guarda = GuardaEmMemoria()
        val antes = novas(guarda)
        antes.registrar(TipoDeEvento.EXIBICAO, "padaria", "home-topo", "VISITANTE")
        antes.registrar(TipoDeEvento.EXIBICAO, "padaria", "home-topo", "VISITANTE")

        val depois = novas(guarda) // "o app fechou e abriu de novo"
        assertEquals(2, depois.pendentes())
        depois.enviarAgora()
        assertEquals(2, todos().sumOf { it.quantidade })
        assertEquals(0, novas(guarda).pendentes()) // depois do envio, nada sobra guardado
    }

    @Test
    fun textoGuardadoQuebradoNaoDerrubaEComecaVazio() {
        assertEquals(0, novas(GuardaEmMemoria("isto não é json")).pendentes())
        assertEquals(0, novas(GuardaEmMemoria("""[{"tipo":"INVENTADO","anuncioId":"a","posicao":"p","perfil":"IDOSO","dia":"2026-09-21","quantidade":4}]""")).pendentes())
    }

    @Test
    fun cadaDiaTemSuaPropriaContagem() = runBlocking {
        val m = novas()
        m.registrar(TipoDeEvento.EXIBICAO, "padaria", "home-fim", "IDOSO")
        dia = dia.plusDays(1)
        m.registrar(TipoDeEvento.EXIBICAO, "padaria", "home-fim", "IDOSO")
        m.enviarAgora()
        assertEquals(setOf("2026-09-21", "2026-09-22"), todos().map { it.dia }.toSet())
    }

    @Test
    fun acumularDemaisDescartaOsNovosParaNaoCrescerSemLimite() = runBlocking {
        val m = novas()
        (1..600).forEach { m.registrar(TipoDeEvento.EXIBICAO, "banner-$it", "home-fim", "IDOSO") }
        assertEquals(500, m.pendentes())
        // os que já existiam ainda podem ser contados de novo
        m.registrar(TipoDeEvento.EXIBICAO, "banner-1", "home-fim", "IDOSO")
        assertEquals(501, m.pendentes())
        m.enviarAgora()
        assertEquals(0, m.pendentes())
        assertFalse("nenhum lote pode passar do que o servidor aceita", enviados.any { it.size > 200 })
    }
}
