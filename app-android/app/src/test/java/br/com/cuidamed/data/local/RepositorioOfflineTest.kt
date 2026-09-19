package br.com.cuidamed.data.local

import br.com.cuidamed.data.AparelhoDePush
import br.com.cuidamed.data.EfeitosLocais
import br.com.cuidamed.data.SemPush
import br.com.cuidamed.data.EstadoDaSessao
import br.com.cuidamed.data.GuardaDeTokens
import br.com.cuidamed.data.HistoricoDto
import br.com.cuidamed.data.MedicamentoDto
import br.com.cuidamed.data.MedicamentoRequest
import br.com.cuidamed.data.Repositorio
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.data.Tokens
import br.com.cuidamed.data.UltimaSessao
import br.com.cuidamed.data.UsuarioDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

private class TokensEmMemoria : GuardaDeTokens {
    private var atuais: Tokens? = null
    override fun tokens() = atuais
    override fun salvar(tokens: Tokens) { atuais = tokens }
    override fun limpar() { atuais = null }
}

private class UltimaSessaoEmMemoria : UltimaSessao {
    private var id: Int? = null
    override fun lerId() = id
    override fun salvarId(id: Int?) { this.id = id }
}

private class EfeitosGravados : EfeitosLocais {
    val listasDeAlarme = mutableListOf<List<MedicamentoDto>>()
    val tomadas = mutableListOf<Int>()
    var pedidosDeSincronizacao = 0
    override fun aoEntrar(usuario: UsuarioDto) {}
    override fun aoSair() {}
    override fun aoAtualizarRemediosDoIdoso(remedios: List<MedicamentoDto>) { listasDeAlarme += remedios }
    override fun aoRegistrarTomada(medicamentoId: Int) { tomadas += medicamentoId }
    override fun agendarSincronizacao() { pedidosDeSincronizacao++ }
}

/**
 * O app inteiro sem internet, passando pelo repositório de verdade (fila, cópia local e sincronização) com um servidor de
 * mentira. "Agora" fixo: segunda-feira, 21/09/2026, 10:00.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepositorioOfflineTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val trabalhoDeFundo = SupervisorJob()
    private val agora = LocalDateTime.of(2026, 9, 21, 10, 0)
    private lateinit var api: ApiFalsa
    private lateinit var tokens: TokensEmMemoria
    private lateinit var ultimaSessao: UltimaSessaoEmMemoria
    private lateinit var efeitos: EfeitosGravados
    private lateinit var armazenamento: ArmazenamentoLocal
    private lateinit var repo: Repositorio
    private var chaves = 0
    private var aparelho: AparelhoDePush = SemPush

    @Before
    fun preparar() {
        Dispatchers.setMain(dispatcher)
        api = ApiFalsa()
        tokens = TokensEmMemoria()
        ultimaSessao = UltimaSessaoEmMemoria()
        efeitos = EfeitosGravados()
        armazenamento = ArmazenamentoLocal(pasta.newFolder(), SemCifra)
        repo = novoRepositorio()
    }

    @After
    fun limpar() {
        Dispatchers.resetMain()
    }

    private fun novoRepositorio() = Repositorio(
        api, tokens, efeitos, armazenamento, ultimaSessao, CoroutineScope(dispatcher + trabalhoDeFundo),
        agora = { agora }, novaChave = { "chave-${++chaves}-teste" }, aparelho = aparelho,
    )

    /** O envio automático roda em segundo plano depois de cada alteração; os testes esperam por ele antes de conferir. */
    private suspend fun aguardar() {
        trabalhoDeFundo.children.toList().forEach { it.join() }
    }

    private suspend fun cad(idoso: Int, m: MedicamentoRequest) = repo.cadastrarMedicamento(idoso, m).also { aguardar() }
    private suspend fun ed(id: Int, m: MedicamentoRequest) = repo.editarMedicamento(id, m).also { aguardar() }
    private suspend fun ex(id: Int) = repo.excluirMedicamento(id).also { aguardar() }
    private suspend fun tomar(id: Int) = repo.registrarTomada(id).also { aguardar() }

    private fun pedido(nome: String, dia: String = "MONDAY", horario: String = "08:00") = MedicamentoRequest(nome, dia, horario, "COMPRIMIDO")

    private suspend fun entrarComoIdoso() {
        assertTrue(repo.entrar("maria@teste.com", "senha-teste") is Resultado.Ok)
    }

    private fun remedioNoServidor(id: Int, nome: String = "Losartana") {
        api.remedios[id] = MedicamentoDto(id, 7, nome, "08:00", "MONDAY", "COMPRIMIDO")
    }

    @Test
    fun cadastrarSemInternetApareceNaHoraESobeQuandoAInternetVolta() = runTest(dispatcher) {
        entrarComoIdoso()
        api.online = false

        val criado = (cad(7, pedido("Atenolol")) as Resultado.Ok).valor

        assertTrue("id provisório, só do aparelho", criado.id < 0)
        assertEquals(listOf("Atenolol"), repo.remediosLocais(7)!!.map { it.nome })
        assertEquals(1, repo.sincronizacao.value.pendentes)
        assertTrue(repo.sincronizacao.value.semConexao)
        assertTrue("os alarmes já usam o remédio novo", efeitos.listasDeAlarme.last().any { it.nome == "Atenolol" })
        assertTrue(efeitos.pedidosDeSincronizacao > 0)
        assertTrue("nada chegou ao servidor", api.remedios.isEmpty())

        api.online = true
        repo.aoVoltarAInternet()

        assertEquals(1, api.remedios.size)
        assertEquals(0, repo.sincronizacao.value.pendentes)
        assertFalse(repo.sincronizacao.value.semConexao)
        val local = repo.remediosLocais(7)!!
        assertEquals(1, local.size)
        assertTrue("o id provisório virou o do servidor", local.single().id > 0)
    }

    @Test
    fun marcarJaTomeiSemInternetMudaOAvisoOHistoricoEUsaAHoraDoToque() = runTest(dispatcher) {
        remedioNoServidor(1)
        entrarComoIdoso()
        repo.medicamentos(7) // guarda a cópia do servidor
        assertEquals(listOf("ESQUECIDO"), repo.avisosLocais(7)!!.map { it.tipo }) // 08:00 já passou, e ninguém tomou

        api.online = false
        assertTrue(tomar(1) is Resultado.Ok)

        assertEquals(listOf("TOMADO"), repo.avisosLocais(7)!!.map { it.tipo })
        assertEquals(1, repo.historicoLocal(7)!!.size)
        assertEquals(listOf(1), efeitos.tomadas)
        // marcar de novo no mesmo dia não vale (e não entra na fila)
        val repetida = tomar(1)
        assertTrue(repetida is Resultado.Falha && repetida.codigo == 400)
        assertEquals(1, repo.sincronizacao.value.pendentes)

        api.online = true
        repo.aoVoltarAInternet()
        assertEquals("a hora enviada é a do toque, não a do envio", listOf(1 to "2026-09-21T10:00:00"), api.tomadas)
        assertEquals(0, repo.sincronizacao.value.pendentes)
    }

    @Test
    fun editarEExcluirSemInternetValemNaHoraESoAExclusaoSobe() = runTest(dispatcher) {
        remedioNoServidor(1)
        remedioNoServidor(2, "Metformina")
        entrarComoIdoso()
        repo.medicamentos(7)
        api.online = false

        assertTrue(ed(1, MedicamentoRequest(nome = "Losartana 50mg")) is Resultado.Ok)
        assertEquals(setOf("Losartana 50mg", "Metformina"), repo.remediosLocais(7)!!.map { it.nome }.toSet())
        assertTrue(ex(1) is Resultado.Ok)
        assertEquals(listOf("Metformina"), repo.remediosLocais(7)!!.map { it.nome })
        // a edição pendente do remédio excluído sai da fila: só a exclusão vai ao servidor
        assertEquals(1, repo.sincronizacao.value.pendentes)

        api.online = true
        repo.aoVoltarAInternet()
        assertEquals(setOf(2), api.remedios.keys)
        assertEquals(listOf("excluir:1"), api.chamadas.filter { it.startsWith("editar") || it.startsWith("excluir") })
    }

    @Test
    fun remedioCriadoECancelado_antesDeSubirNaoEnviaNada() = runTest(dispatcher) {
        entrarComoIdoso()
        api.online = false
        val criado = (cad(7, pedido("Atenolol")) as Resultado.Ok).valor
        ed(criado.id, MedicamentoRequest(nome = "Atenolol 25mg"))
        assertEquals("a edição foi para dentro do cadastro pendente", 1, repo.sincronizacao.value.pendentes)
        assertEquals("Atenolol 25mg", repo.remediosLocais(7)!!.single().nome)

        assertTrue(ex(criado.id) is Resultado.Ok)
        assertEquals(0, repo.sincronizacao.value.pendentes)

        api.online = true
        repo.aoVoltarAInternet()
        assertTrue(api.remedios.isEmpty())
        assertTrue(api.chamadas.none { it.startsWith("criar") })
    }

    @Test
    fun oQueOutraPessoaExcluiuNaoEditaEOUsuarioRecebeOAviso() = runTest(dispatcher) {
        remedioNoServidor(1, "Metformina")
        entrarComoIdoso()
        repo.medicamentos(7)
        api.online = false
        ed(1, MedicamentoRequest(nome = "Metformina 850mg"))

        api.excluidos += 1 // enquanto isso, outra pessoa excluiu o remédio
        api.remedios.remove(1)
        api.online = true
        repo.aoVoltarAInternet()

        assertEquals(0, repo.sincronizacao.value.pendentes)
        assertTrue(repo.sincronizacao.value.conflitos.single().contains("foi excluído por outra pessoa"))
        repo.dispensarConflitos()
        assertTrue(repo.sincronizacao.value.conflitos.isEmpty())
    }

    @Test
    fun semInternetALeituraMostraACopiaGuardada() = runTest(dispatcher) {
        remedioNoServidor(1)
        entrarComoIdoso()
        assertEquals(1, (repo.medicamentos(7) as Resultado.Ok).valor.size)
        api.online = false

        val r = repo.medicamentos(7)

        assertEquals(listOf("Losartana"), (r as Resultado.Ok).valor.map { it.nome })
        assertTrue(repo.sincronizacao.value.semConexao)
    }

    @Test
    fun semCopiaESemInternetALeituraFalhaComMensagemClara() = runTest(dispatcher) {
        entrarComoIdoso()
        api.online = false
        val r = repo.medicamentos(7)
        assertTrue(r is Resultado.Falha && r.codigo == null)
    }

    @Test
    fun aberturaDoAppSemInternetUsaOUsuarioGuardado() = runTest(dispatcher) {
        entrarComoIdoso()
        api.online = false

        val reaberto = novoRepositorio() // como se o app tivesse sido fechado e aberto de novo
        reaberto.restaurarSessao()

        val sessao = reaberto.sessao.value
        assertTrue(sessao is EstadoDaSessao.Logado && sessao.usuario.id == 7)
        assertTrue(reaberto.sincronizacao.value.semConexao)
    }

    @Test
    fun aberturaSemInternetENadaGuardadoPedeConexao() = runTest(dispatcher) {
        tokens.salvar(Tokens("a", "r")) // há login, mas este aparelho nunca guardou dados
        api.online = false
        repo.restaurarSessao()
        assertTrue(repo.sessao.value is EstadoDaSessao.SemConexao)
    }

    @Test
    fun sairApagaOsDadosGuardados() = runTest(dispatcher) {
        remedioNoServidor(1)
        entrarComoIdoso()
        repo.medicamentos(7)
        assertNotNull(repo.remediosLocais(7))

        repo.sair()

        assertNull(ultimaSessao.lerId())
        assertEquals(DadosLocais(), armazenamento.ler(7))
    }

    @Test
    fun aoEntrarOAparelhoSeRegistraEAoSairSeRemoveDoPush() = runTest(dispatcher) {
        aparelho = object : AparelhoDePush {
            override suspend fun token() = "tk-firebase"
        }
        repo = novoRepositorio()

        entrarComoIdoso()
        aguardar()
        assertTrue(api.chamadas.contains("registrarDispositivo:tk-firebase"))

        repo.sair()
        assertTrue(api.chamadas.indexOf("removerDispositivo:tk-firebase") < api.chamadas.indexOf("sair"))
    }

    @Test
    fun semPushConfiguradoNadaEEnviadoSobreOAparelho() = runTest(dispatcher) {
        entrarComoIdoso()
        aguardar()
        repo.sair()
        assertTrue(api.chamadas.none { it.contains("Dispositivo") })
    }

    @Test
    fun oQueEstaErradoEAvisadoNaHoraSemPrecisarDeInternet() = runTest(dispatcher) {
        entrarComoIdoso()
        api.online = false
        val r = cad(7, MedicamentoRequest("  ", "MONDAY", "08:00", "GOTAS"))
        assertTrue(r is Resultado.Falha && r.mensagem.contains("nome do medicamento"))
        assertEquals(0, repo.sincronizacao.value.pendentes)
    }

    @Test
    fun oIdProvisorioQueVirouRealNaoDuplicaAoLerDoServidor() = runTest(dispatcher) {
        entrarComoIdoso()
        api.online = false
        cad(7, pedido("Atenolol"))
        api.online = true
        repo.aoVoltarAInternet()

        val lista = (repo.medicamentos(7) as Resultado.Ok).valor
        assertEquals(listOf("Atenolol"), lista.map { it.nome })
    }

    @Test
    fun umaTomadaDeRemedioCriadoSemInternetSobeDepoisDoCadastro() = runTest(dispatcher) {
        entrarComoIdoso()
        api.online = false
        val criado = (cad(7, pedido("Atenolol")) as Resultado.Ok).valor
        assertTrue(tomar(criado.id) is Resultado.Ok)
        assertEquals(2, repo.sincronizacao.value.pendentes)

        api.online = true
        repo.aoVoltarAInternet()

        assertEquals(0, repo.sincronizacao.value.pendentes)
        val idReal = api.remedios.keys.single()
        assertEquals(listOf(idReal), api.tomadas.map { it.first })
        assertEquals(listOf("criar:Atenolol", "tomada:$idReal"), api.chamadas.filter { it.startsWith("criar") || it.startsWith("tomada") })
    }
}
