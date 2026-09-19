package br.com.cuidamed

import br.com.cuidamed.data.Resultado
import br.com.cuidamed.ui.CarregadorViewModel
import br.com.cuidamed.ui.Estado
import br.com.cuidamed.ui.aoRetomar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Garante que uma lista atualiza ao voltar para a tela (o defeito do "cadastrei o remédio e ele não aparece"). */
@OptIn(ExperimentalCoroutinesApi::class)
class CarregadorTest {

    private var relogioNs = 0L
    private var chamadas = 0
    private var dados = listOf("Losartana")

    @Before
    fun prepararMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limparMain() {
        Dispatchers.resetMain()
    }

    private fun novoCarregador() = CarregadorViewModel(
        buscar = {
            chamadas++
            Resultado.Ok(dados)
        },
        agora = { relogioNs },
    )

    private fun passarSegundos(s: Long) {
        relogioNs += s * 1_000_000_000L
    }

    @Test
    fun aoVoltarParaATelaBuscaDeNovoEMostraOQueFoiSalvo() {
        val carregador = novoCarregador()
        assertEquals(1, chamadas)

        // o usuário abre o formulário, salva um remédio novo e volta (alguns segundos depois)
        dados = listOf("Losartana", "Metformina")
        passarSegundos(20)
        carregador.aoRetomar()

        assertEquals(2, chamadas)
        assertEquals(listOf("Losartana", "Metformina"), (carregador.estado as Estado.Pronto).valor)
    }

    @Test
    fun aAberturaDaPropriaTelaNaoBuscaDuasVezes() {
        val carregador = novoCarregador()
        passarSegundos(1) // a tela acabou de abrir e "retoma" logo em seguida
        carregador.aoRetomar()
        assertEquals(1, chamadas)
    }

    @Test
    fun voltarVariasVezesSeguidasContinuaAtualizando() {
        val carregador = novoCarregador()
        repeat(3) {
            passarSegundos(10)
            carregador.aoRetomar()
        }
        assertEquals(4, chamadas)
    }
}
