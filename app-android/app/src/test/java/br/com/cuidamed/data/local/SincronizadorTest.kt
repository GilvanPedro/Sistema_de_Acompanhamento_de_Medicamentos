package br.com.cuidamed.data.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SincronizadorTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val usuario = 7
    private val idoso = 7
    private lateinit var api: ApiFalsa
    private lateinit var armazenamento: ArmazenamentoLocal
    private lateinit var sincronizador: Sincronizador

    @Before
    fun preparar() {
        api = ApiFalsa()
        armazenamento = ArmazenamentoLocal(pasta.newFolder(), SemCifra)
        sincronizador = Sincronizador(api, armazenamento)
    }

    private fun criar(id: String, idLocal: Int, nome: String) =
        CriarRemedio(id, idoso, idLocal, nome, "MONDAY", "08:00", "COMPRIMIDO", "2026-09-19T08:00:00")

    private fun editar(id: String, remedioId: Int, nome: String) =
        EditarRemedio(id, idoso, remedioId, "?", nome = nome, criadaEm = "2026-09-19T08:01:00")

    private fun tomada(id: String, remedioId: Int, quando: String = "2026-09-19T08:05:00") =
        RegistrarTomada(id, idoso, remedioId, "Losartana", quando, "2026-09-19T08:05:00")

    private fun fila(vararg ops: Operacao) = runBlocking {
        armazenamento.alterar(usuario) { it.copy(pendencias = ops.toList()) to Unit }
    }

    private fun dados() = runBlocking { armazenamento.ler(usuario) }

    private fun enviar() = runBlocking { sincronizador.enviar(usuario) }

    @Test
    fun enviaNaOrdemETroca_oIdLocalPeloDoServidorNasAlteracoesSeguintes() {
        fila(criar("op-criar", -1, "Losartana"), editar("op-editar", -1, "Losartana 50mg"), tomada("op-tomada", -1))

        val r = enviar()

        assertEquals(ResultadoDoEnvio.Concluido(true), r)
        assertTrue(dados().pendencias.isEmpty())
        val idReal = api.remedios.keys.single()
        assertEquals("Losartana 50mg", api.remedios.getValue(idReal).nome)
        assertEquals(listOf(idReal), api.tomadas.map { it.first })
        assertEquals(listOf("criar:Losartana", "editar:$idReal", "tomada:$idReal"), api.chamadas)
        // a cópia local já tem o remédio com o id do servidor
        assertEquals(idReal, dados().remedios.getValue(idoso).single().id)
    }

    @Test
    fun semInternetMantemAFilaInteiraEDepoisEnvia() {
        fila(criar("op-1", -1, "Losartana"), tomada("op-2", -1))
        api.online = false

        val r = enviar()

        assertEquals(ResultadoDoEnvio.SemConexao(false), r)
        assertEquals(2, dados().pendencias.size)
        assertTrue(dados().conflitos.isEmpty())

        api.online = true
        assertEquals(ResultadoDoEnvio.Concluido(true), enviar())
        assertTrue(dados().pendencias.isEmpty())
        assertEquals(1, api.remedios.size)
    }

    @Test
    fun seARespostaDoCadastroSePerderOReenvioNaoDuplicaOremedio() {
        fila(criar("op-1", -1, "Losartana"))
        api.perderRespostaDoProximoCadastro = true

        assertEquals(ResultadoDoEnvio.SemConexao(false), enviar()) // o servidor criou, mas o app não soube
        assertEquals(1, api.remedios.size)
        assertEquals(1, dados().pendencias.size)

        assertEquals(ResultadoDoEnvio.Concluido(true), enviar()) // mesma chave: recebe o que já existe
        assertEquals("só um remédio no servidor", 1, api.remedios.size)
        assertTrue(dados().pendencias.isEmpty())
    }

    @Test
    fun editarUmRemedioQueOutraPessoaExcluiuDescartaAAlteracaoEAvisa() {
        api.remedios[50] = br.com.cuidamed.data.MedicamentoDto(50, idoso, "Metformina", "12:00", "TUESDAY", "COMPRIMIDO")
        api.excluidos += 50
        fila(editar("op-1", 50, "Metformina 850mg"))

        assertEquals(ResultadoDoEnvio.Concluido(true), enviar())

        assertTrue(dados().pendencias.isEmpty())
        assertTrue(dados().conflitos.single().contains("foi excluído por outra pessoa"))
    }

    @Test
    fun excluirUmRemedioQueJaFoiExcluidoContaComoFeito() {
        fila(ExcluirRemedio("op-1", idoso, 60, "Sinvastatina", "2026-09-19T09:00:00"))
        // 60 nunca existiu no servidor: 404, e o objetivo (não existir) já está cumprido

        assertEquals(ResultadoDoEnvio.Concluido(true), enviar())

        assertTrue(dados().pendencias.isEmpty())
        assertTrue("não é conflito", dados().conflitos.isEmpty())
    }

    @Test
    fun tomadaQueOServidorDizQueJaEstavaRegistradaNaoViraConflito() {
        api.remedios[70] = br.com.cuidamed.data.MedicamentoDto(70, idoso, "Losartana", "08:00", "MONDAY", "COMPRIMIDO")
        api.tomadas += 70 to "2026-09-19T08:00:00"
        fila(tomada("op-1", 70, "2026-09-19T08:05:00"))

        assertEquals(ResultadoDoEnvio.Concluido(true), enviar())

        assertTrue(dados().pendencias.isEmpty())
        assertTrue(dados().conflitos.isEmpty())
        assertEquals(1, api.tomadas.size)
    }

    @Test
    fun cadastroRecusadoDescartaTambemAsAlteracoesQueDependiamDele() {
        fila(criar("op-1", -1, "Losartana"), editar("op-2", -1, "Losartana 50mg"), criar("op-3", -2, "Metformina"))
        api.codigoDeFalhaGeral = 400
        api.mensagemDaFalha = "O nome do medicamento é obrigatório."

        assertEquals(ResultadoDoEnvio.Concluido(true), enviar())

        // tudo foi recusado (a API falsa recusa tudo): as três saem da fila, cada uma com o seu aviso
        assertTrue(dados().pendencias.isEmpty())
        assertEquals(2, dados().conflitos.size)
        assertTrue(dados().conflitos.all { it.contains("não pôde ser cadastrado") })
    }

    @Test
    fun erroDoServidorNaoDescartaNada() {
        fila(criar("op-1", -1, "Losartana"), tomada("op-2", -1))
        api.codigoDeFalhaGeral = 503

        assertEquals(ResultadoDoEnvio.SemConexao(false), enviar())

        assertEquals(2, dados().pendencias.size)
        assertTrue(dados().conflitos.isEmpty())
    }

    @Test
    fun sessaoQueAcabouInterrompeOEnvioSemDescartar() {
        fila(criar("op-1", -1, "Losartana"))
        api.codigoDeFalhaGeral = 401

        assertEquals(ResultadoDoEnvio.SessaoPerdida, enviar())
        assertEquals(1, dados().pendencias.size)
    }

    @Test
    fun aVersaoAumentaAcadaAlteracaoEnviada() {
        fila(criar("op-1", -1, "Losartana"), tomada("op-2", -1))
        val antes = sincronizador.versao
        enviar()
        assertEquals(antes + 2, sincronizador.versao)
    }
}
