package br.com.cuidamed.data.local

import br.com.cuidamed.data.ConsentimentoDto
import br.com.cuidamed.data.ConsentimentoRequest
import br.com.cuidamed.data.CuidaMedApi
import br.com.cuidamed.data.DispositivoRequest
import br.com.cuidamed.data.EditarUsuarioRequest
import br.com.cuidamed.data.EmailRequest
import br.com.cuidamed.data.ExcluirContaRequest
import br.com.cuidamed.data.HistoricoDto
import br.com.cuidamed.data.LoginRequest
import br.com.cuidamed.data.LoginResposta
import br.com.cuidamed.data.MedicamentoDto
import br.com.cuidamed.data.MedicamentoRequest
import br.com.cuidamed.data.MensagemDto
import br.com.cuidamed.data.NotificacaoDto
import br.com.cuidamed.data.PedidoVinculoDto
import br.com.cuidamed.data.RefreshRequest
import br.com.cuidamed.data.RegistroRequest
import br.com.cuidamed.data.TomadaRequest
import br.com.cuidamed.data.UsuarioDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Um servidor de mentira que se comporta como o de verdade nas partes que a sincronização usa: guarda remédios e
 * tomadas, respeita a chave de idempotência, dá 404 para remédio excluído e 400 para tomada repetida no dia.
 * Também dá para "derrubar a internet" ou perder a resposta de um pedido que o servidor chegou a processar.
 */
class ApiFalsa : CuidaMedApi {
    var online = true
    var perderRespostaDoProximoCadastro = false
    var codigoDeFalhaGeral: Int? = null
    var mensagemDaFalha = "Dados inválidos."

    val remedios = mutableMapOf<Int, MedicamentoDto>()
    val excluidos = mutableSetOf<Int>()
    val tomadas = mutableListOf<Pair<Int, String>>() // remédio, hora
    val chaves = mutableMapOf<String, Int>()
    val chamadas = mutableListOf<String>()
    private var proximoId = 100

    private fun erro(codigo: Int, mensagem: String): Nothing =
        throw HttpException(Response.error<Any>(codigo, """{"erro":"$mensagem"}""".toResponseBody("application/json".toMediaType())))

    /** Tentativas que falharam por falta de internet (não chegaram ao servidor, então não entram em [chamadas]). */
    var tentativasSemInternet = 0

    private fun <T> comRede(chamada: String, bloco: () -> T): T {
        if (!online) {
            tentativasSemInternet++
            throw IOException("sem internet")
        }
        chamadas += chamada
        codigoDeFalhaGeral?.let { erro(it, mensagemDaFalha) }
        return bloco()
    }

    override suspend fun cadastrarMedicamentoComChave(idosoId: Int, corpo: MedicamentoRequest, chave: String): MedicamentoDto = comRede("criar:${corpo.nome}") {
        val existente = chaves[chave]
        if (existente != null) return@comRede remedios.getValue(existente)
        val criado = MedicamentoDto(proximoId++, idosoId, corpo.nome!!, corpo.horario!!, corpo.diaSemana!!, corpo.tipo!!)
        remedios[criado.id] = criado
        chaves[chave] = criado.id
        if (perderRespostaDoProximoCadastro) {
            perderRespostaDoProximoCadastro = false
            throw IOException("a resposta se perdeu")
        }
        criado
    }

    override suspend fun editarMedicamento(id: Int, corpo: MedicamentoRequest): MedicamentoDto = comRede("editar:$id") {
        if (id in excluidos || id !in remedios) erro(404, "Medicamento com id: $id não encontrado.")
        val atual = remedios.getValue(id)
        val novo = atual.copy(
            nome = corpo.nome ?: atual.nome, diaSemana = corpo.diaSemana ?: atual.diaSemana,
            horario = corpo.horario ?: atual.horario, tipo = corpo.tipo ?: atual.tipo,
        )
        remedios[id] = novo
        novo
    }

    override suspend fun excluirMedicamento(id: Int): Response<Unit> {
        if (!online) {
            tentativasSemInternet++
            throw IOException("sem internet")
        }
        chamadas += "excluir:$id"
        codigoDeFalhaGeral?.let { return Response.error(it, """{"erro":"$mensagemDaFalha"}""".toResponseBody("application/json".toMediaType())) }
        if (id in excluidos || id !in remedios) return Response.error(404, """{"erro":"não encontrado"}""".toResponseBody("application/json".toMediaType()))
        remedios.remove(id)
        excluidos += id
        return Response.success(Unit)
    }

    override suspend fun registrarTomadaEm(id: Int, corpo: TomadaRequest): HistoricoDto = comRede("tomada:$id") {
        if (id in excluidos || id !in remedios) erro(404, "Medicamento com id: $id não encontrado.")
        val dia = corpo.dataHora.substring(0, 10)
        if (tomadas.any { it.first == id && it.second.startsWith(dia) }) erro(400, "Você já registrou que tomou ${remedios.getValue(id).nome} nesse dia.")
        tomadas += id to corpo.dataHora
        HistoricoDto(500 + tomadas.size, id, remedios.getValue(id).nome, corpo.dataHora, true)
    }

    override suspend fun medicamentos(idosoId: Int): List<MedicamentoDto> = comRede("listar:$idosoId") { remedios.values.filter { it.idosoId == idosoId } }
    override suspend fun historico(idosoId: Int): List<HistoricoDto> = comRede("historico:$idosoId") {
        tomadas.mapIndexed { i, t -> HistoricoDto(500 + i + 1, t.first, remedios[t.first]?.nome ?: "?", t.second, true) }
    }

    /** Quem está logado no servidor de mentira. */
    var usuario = UsuarioDto(7, "IDOSO", "Dona Maria", "maria@teste.com")

    override suspend fun login(corpo: LoginRequest): LoginResposta = comRede("login") { LoginResposta("acesso", "renovacao", 900, usuario) }
    override suspend fun eu(): UsuarioDto = comRede("eu") { usuario }
    override suspend fun sair(corpo: RefreshRequest): Response<Unit> {
        chamadas += "sair"
        if (!online) throw IOException("sem internet")
        return Response.success(Unit)
    }
    override suspend fun notificacoes(idosoId: Int): List<NotificacaoDto> = comRede("notificacoes:$idosoId") { emptyList() }
    override suspend fun meusIdosos(): List<UsuarioDto> = comRede("meusIdosos") { emptyList() }

    // O resto não é usado nos testes.
    override suspend fun saude(): Response<Unit> = TODO()
    override suspend fun registro(corpo: RegistroRequest): UsuarioDto = TODO()
    override suspend fun editarEu(corpo: EditarUsuarioRequest): UsuarioDto = TODO()
    override suspend fun excluirConta(corpo: ExcluirContaRequest): Response<Unit> = TODO()
    override suspend fun enviarEventosDeAnuncios(corpo: br.com.cuidamed.data.EventosDeAnunciosRequest): Response<Unit> = Response.success(Unit)
    override suspend fun consentimento(): ConsentimentoDto = TODO()
    override suspend fun aceitarPolitica(corpo: ConsentimentoRequest): Response<Unit> = Response.success(Unit)
    override suspend fun exportar(corpo: ExcluirContaRequest): okhttp3.ResponseBody =
        "{}".toResponseBody("application/json".toMediaType())
    override suspend fun registrarDispositivo(corpo: DispositivoRequest): Response<Unit> = comRede("registrarDispositivo:${corpo.token}") { Response.success(Unit) }
    override suspend fun removerDispositivo(corpo: DispositivoRequest): Response<Unit> = comRede("removerDispositivo:${corpo.token}") { Response.success(Unit) }
    override suspend fun meusFamiliares(): List<UsuarioDto> = TODO()
    override suspend fun adicionarFamiliar(corpo: EmailRequest): MensagemDto = TODO()
    override suspend fun removerFamiliar(familiarId: Int): Response<Unit> = TODO()
    override suspend fun cadastrarMedicamento(idosoId: Int, corpo: MedicamentoRequest): MedicamentoDto = TODO()
    override suspend fun registrarTomada(id: Int): HistoricoDto = TODO()
    override suspend fun pedirVinculo(corpo: EmailRequest): MensagemDto = TODO()
    override suspend fun pedidosRecebidos(): List<PedidoVinculoDto> = TODO()
    override suspend fun aceitarPedido(familiarId: Int): Response<Unit> = TODO()
    override suspend fun recusarPedido(familiarId: Int): Response<Unit> = TODO()
}
