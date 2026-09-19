package br.com.cuidamed.data

import android.util.Log
import br.com.cuidamed.data.local.ArmazenamentoLocal
import br.com.cuidamed.data.local.CriarRemedio
import br.com.cuidamed.data.local.DadosLocais
import br.com.cuidamed.data.local.EditarRemedio
import br.com.cuidamed.data.local.ExcluirRemedio
import br.com.cuidamed.data.local.Operacao
import br.com.cuidamed.data.local.RegistrarTomada
import br.com.cuidamed.data.local.ResultadoDoEnvio
import br.com.cuidamed.data.local.Sincronizador
import br.com.cuidamed.data.local.Visao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

sealed interface Resultado<out T> {
    data class Ok<T>(val valor: T) : Resultado<T>
    data class Falha(val mensagem: String, val codigo: Int? = null) : Resultado<Nothing>
}

/** Em que pé está o login: ainda vendo se há sessão guardada, sem login, ou logado. */
sealed interface EstadoDaSessao {
    data object Verificando : EstadoDaSessao
    data object SemLogin : EstadoDaSessao
    data class Logado(val usuario: UsuarioDto) : EstadoDaSessao

    /** Havia sessão, mas não deu para confirmá-la por falta de internet (ou o servidor está fora do ar). */
    data class SemConexao(val mensagem: String) : EstadoDaSessao
}

private const val MSG_SEM_INTERNET = "Sem conexão com a internet. Confira o Wi-Fi ou os dados e tente de novo."

suspend fun <T> chamar(bloco: suspend () -> T): Resultado<T> = try {
    Resultado.Ok(bloco())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    Resultado.Falha(mensagemDoErro(e.code(), e.response()?.errorBody()?.string()), e.code())
} catch (e: IOException) {
    Log.w("CuidaMed", "Falha de rede: ${e.javaClass.simpleName}: ${e.message}", e)
    Resultado.Falha(mensagemDeRede(e))
} catch (e: SerializationException) {
    Resultado.Falha("O servidor respondeu algo inesperado. Tente de novo.")
}

/** Para chamadas que respondem 204 sem corpo (devolvem Response<Unit>). */
suspend fun chamarVazio(bloco: suspend () -> Response<Unit>): Resultado<Unit> = try {
    val resposta = bloco()
    if (resposta.isSuccessful) Resultado.Ok(Unit)
    else Resultado.Falha(mensagemDoErro(resposta.code(), resposta.errorBody()?.string()), resposta.code())
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w("CuidaMed", "Falha de rede: ${e.javaClass.simpleName}: ${e.message}", e)
    Resultado.Falha(mensagemDeRede(e))
}

/** Timeout é diferente de estar sem internet: o servidor gratuito pode estar acordando. */
private fun mensagemDeRede(e: IOException): String =
    if (e is java.net.SocketTimeoutException) "O servidor demorou para responder. Tente de novo em instantes."
    else MSG_SEM_INTERNET

private fun mensagemDoErro(codigo: Int, corpo: String?): String {
    val doServidor = try {
        corpo?.let { jsonDaApi.decodeFromString<ErroDto>(it).erro }
    } catch (e: SerializationException) {
        null
    }
    return doServidor ?: when (codigo) {
        401 -> "Entre novamente para continuar."
        429 -> "Muitas tentativas. Aguarde alguns minutos e tente de novo."
        in 500..599 -> "O servidor está com problema agora. Tente de novo em instantes."
        else -> "Não foi possível concluir. Tente de novo."
    }
}

/** Como está a conversa com o servidor e a fila de envios: alimenta a faixa de aviso das telas. */
data class EstadoDeSincronizacao(
    val semConexao: Boolean = false,
    val enviando: Boolean = false,
    val pendentes: Int = 0,
    val conflitos: List<String> = emptyList(),
)

/** Qual foi a última pessoa logada neste aparelho (para abrir o app sem internet com os dados dela). */
interface UltimaSessao {
    fun lerId(): Int?
    fun salvarId(id: Int?)
}

/**
 * A porta de entrada dos dados para as telas. Funciona assim:
 *
 * - **Leitura:** mostra o que está guardado no aparelho (a última cópia do servidor com as alterações pendentes por cima)
 *   e tenta atualizar pelo servidor. Se não houver internet, fica com o que já tem.
 * - **Escrita de remédios e tomadas:** entra numa fila no aparelho, aparece na tela na hora e é enviada ao servidor assim
 *   que possível (na hora, se houver internet; depois, se não houver). Ver [Sincronizador] para os conflitos.
 * - Tudo que não é remédio nem tomada (login, perfil, vínculos) precisa de internet.
 */
/** De onde vem o token de push do aparelho (Firebase). Sem push configurado, não há token. */
interface AparelhoDePush {
    suspend fun token(): String?
}

object SemPush : AparelhoDePush {
    override suspend fun token(): String? = null
}

class Repositorio(
    private val api: CuidaMedApi,
    private val cofre: GuardaDeTokens,
    private val efeitos: EfeitosLocais,
    private val armazenamento: ArmazenamentoLocal,
    private val ultimaSessao: UltimaSessao,
    private val escopo: CoroutineScope,
    private val agora: () -> LocalDateTime = { LocalDateTime.now() },
    private val novaChave: () -> String = { UUID.randomUUID().toString() },
    private val aparelho: AparelhoDePush = SemPush,
) {

    private val sincronizador = Sincronizador(api, armazenamento)

    private val _sessao = MutableStateFlow<EstadoDaSessao>(EstadoDaSessao.Verificando)
    val sessao: StateFlow<EstadoDaSessao> = _sessao

    private val _sincronizacao = MutableStateFlow(EstadoDeSincronizacao())
    val sincronizacao: StateFlow<EstadoDeSincronizacao> = _sincronizacao

    /** Sobe a cada mudança nos dados locais (alteração feita, envio concluído): as telas se atualizam a partir daqui. */
    private val _mudancas = MutableStateFlow(0)
    val mudancasLocais: StateFlow<Int> = _mudancas

    /** Sobe quando a internet volta: as telas buscam de novo no servidor. */
    private val _conexoes = MutableStateFlow(0)
    val conexoesRestabelecidas: StateFlow<Int> = _conexoes

    /** O Android avisou que há rede de novo: manda a fila e faz as telas atualizarem. */
    suspend fun aoVoltarAInternet() {
        _sincronizacao.update { it.copy(semConexao = false) }
        _conexoes.update { it + 1 }
        enviarPendencias()
    }

    // ---------------------------------------------------------------- sessão

    /** Ao abrir o app: se há tokens guardados, confirma quem é o usuário; sem internet, abre com os dados guardados. */
    suspend fun restaurarSessao() {
        if (cofre.tokens() == null) {
            _sessao.value = EstadoDaSessao.SemLogin
            return
        }
        _sessao.value = EstadoDaSessao.Verificando
        when (val r = chamar { api.eu() }) {
            is Resultado.Ok -> {
                guardarUsuario(r.valor)
                _sessao.value = EstadoDaSessao.Logado(r.valor)
                efeitos.aoEntrar(r.valor)
                atualizarEstado()
                escopo.launch { enviarPendencias() }
            }
            is Resultado.Falha -> when {
                // 401 (sessão vencida) já limpou os tokens
                cofre.tokens() == null || r.codigo == 401 -> _sessao.value = EstadoDaSessao.SemLogin
                // sem internet (ou servidor fora): abre com o que está guardado no aparelho
                r.codigo == null -> {
                    val guardado = usuarioGuardado()
                    if (guardado != null) {
                        _sessao.value = EstadoDaSessao.Logado(guardado)
                        efeitos.aoEntrar(guardado)
                        _sincronizacao.update { it.copy(semConexao = true) }
                        atualizarEstado()
                    } else {
                        _sessao.value = EstadoDaSessao.SemConexao(r.mensagem)
                    }
                }
                else -> _sessao.value = EstadoDaSessao.SemConexao(r.mensagem)
            }
        }
    }

    /**
     * Para trabalhos em segundo plano, que rodam com o app fechado: põe a pessoa como logada a partir do que está guardado,
     * sem depender de internet.
     */
    suspend fun restaurarSessaoLocal() {
        if (_sessao.value is EstadoDaSessao.Logado || cofre.tokens() == null) return
        usuarioGuardado()?.let { _sessao.value = EstadoDaSessao.Logado(it) }
    }

    private suspend fun usuarioGuardado(): UsuarioDto? = ultimaSessao.lerId()?.let { armazenamento.ler(it).usuario }

    private suspend fun guardarUsuario(usuario: UsuarioDto) {
        armazenamento.alterar(usuario.id) { it.copy(usuario = usuario) to Unit }
        ultimaSessao.salvarId(usuario.id)
    }

    /**
     * Acorda o servidor gratuito (que hiberna) sem esperar resposta útil: assim, quando a pessoa terminar de digitar o
     * e-mail e a senha, ele já está de pé. Falhas aqui não importam.
     */
    suspend fun acordarServidor() {
        chamarVazio { api.saude() }
    }

    /** O renovador de token chama isto quando o servidor recusa a renovação. */
    fun sessaoPerdida() {
        val id = usuarioLogado()?.id
        _sessao.value = EstadoDaSessao.SemLogin
        efeitos.aoSair()
        escopo.launch { esquecerDados(id) }
    }

    /** Qual versão da política o servidor já tem aceita para esta conta. */
    suspend fun consentimento(): Resultado<ConsentimentoDto> = chamar { api.consentimento() }

    suspend fun aceitarPolitica(): Resultado<Unit> = chamarVazio { api.aceitarPolitica(ConsentimentoRequest(VERSAO_DA_POLITICA)) }

    /** Cópia de todos os dados da conta (LGPD), como texto JSON. Exige a senha. */
    suspend fun exportarDados(senha: String): Resultado<String> = chamar { api.exportar(ExcluirContaRequest(senha)).string() }

    /** Entrega ao servidor o token de push deste aparelho para a conta logada. */
    suspend fun registrarAparelho(token: String): Resultado<Unit> {
        if (!temSessao()) return Resultado.Ok(Unit)
        return chamarVazio { api.registrarDispositivo(DispositivoRequest(token)) }
            .also { Log.i("CuidaMed", "Push: registro do aparelho -> ${if (it is Resultado.Ok) "ok" else "falhou"}") }
    }

    fun temSessao(): Boolean = cofre.tokens() != null

    fun usuarioAtualId(): Int? = usuarioLogado()?.id

    suspend fun eu() = chamar { api.eu() }

    private fun usuarioLogado(): UsuarioDto? = (_sessao.value as? EstadoDaSessao.Logado)?.usuario

    private suspend fun uid(): Int? = usuarioLogado()?.id

    suspend fun entrar(email: String, senha: String): Resultado<UsuarioDto> =
        when (val r = chamar { api.login(LoginRequest(email.trim(), senha)) }) {
            is Resultado.Ok -> {
                cofre.salvar(Tokens(r.valor.accessToken, r.valor.refreshToken))
                guardarUsuario(r.valor.usuario)
                _sessao.value = EstadoDaSessao.Logado(r.valor.usuario)
                efeitos.aoEntrar(r.valor.usuario)
                escopo.launch { aparelho.token()?.let { registrarAparelho(it) } }
                atualizarEstado()
                Resultado.Ok(r.valor.usuario)
            }
            is Resultado.Falha -> r
        }

    suspend fun cadastrar(tipo: String, nome: String, email: String, senha: String): Resultado<UsuarioDto> =
        when (val r = chamar { api.registro(RegistroRequest(tipo, nome.trim(), email.trim(), senha, true, VERSAO_DA_POLITICA)) }) {
            is Resultado.Ok -> entrar(email, senha)
            is Resultado.Falha -> r
        }

    /** Sair apaga os dados guardados no aparelho (inclusive alterações que ainda não foram enviadas). */
    suspend fun sair() {
        val id = usuarioLogado()?.id
        // melhor esforço: o servidor deixa de mandar push da conta para este aparelho e revoga o token de renovação
        aparelho.token()?.let { token -> chamarVazio { api.removerDispositivo(DispositivoRequest(token)) } }
        cofre.tokens()?.let { chamar { api.sair(RefreshRequest(it.renovacao)) } }
        cofre.limpar()
        _sessao.value = EstadoDaSessao.SemLogin
        efeitos.aoSair()
        esquecerDados(id)
    }

    private suspend fun esquecerDados(id: Int?) {
        id?.let { armazenamento.apagar(it) }
        ultimaSessao.salvarId(null)
        _sincronizacao.value = EstadoDeSincronizacao()
    }

    /** Depois de editar o cadastro, atualiza o usuário guardado (nome e e-mail aparecem em várias telas). */
    private suspend fun atualizarUsuario(usuario: UsuarioDto) {
        if (_sessao.value is EstadoDaSessao.Logado) _sessao.value = EstadoDaSessao.Logado(usuario)
        guardarUsuario(usuario)
    }

    suspend fun editarPerfil(pedido: EditarUsuarioRequest): Resultado<UsuarioDto> =
        chamar { api.editarEu(pedido) }.also { if (it is Resultado.Ok) atualizarUsuario(it.valor) }

    /** Excluir a conta é irreversível e exige a senha. Se der certo, a sessão termina. */
    suspend fun excluirConta(senha: String): Resultado<Unit> {
        val id = usuarioLogado()?.id
        return chamarVazio { api.excluirConta(ExcluirContaRequest(senha)) }.also {
            if (it is Resultado.Ok) {
                cofre.limpar()
                _sessao.value = EstadoDaSessao.SemLogin
                efeitos.aoSair()
                esquecerDados(id)
            }
        }
    }

    // ------------------------------------------------- estado da sincronização

    private suspend fun atualizarEstado() {
        val id = uid() ?: return
        val d = armazenamento.ler(id)
        _sincronizacao.update { it.copy(pendentes = d.pendencias.size, conflitos = d.conflitos) }
    }

    private fun marcarConexao(online: Boolean) {
        _sincronizacao.update { it.copy(semConexao = !online) }
    }

    /** O usuário leu os avisos de alterações que o servidor recusou. */
    suspend fun dispensarConflitos() {
        val id = uid() ?: return
        armazenamento.alterar(id) { it.copy(conflitos = emptyList()) to Unit }
        atualizarEstado()
    }

    /** Envia a fila de alterações feitas (com ou sem internet). Seguro chamar de vários lugares: um envio por vez. */
    suspend fun enviarPendencias(): ResultadoDoEnvio {
        val id = uid() ?: return ResultadoDoEnvio.Concluido(false)
        if (armazenamento.ler(id).pendencias.isEmpty()) {
            atualizarEstado()
            return ResultadoDoEnvio.Concluido(false)
        }
        _sincronizacao.update { it.copy(enviando = true) }
        val r = sincronizador.enviar(id)
        _sincronizacao.update { it.copy(enviando = false) }
        when (r) {
            is ResultadoDoEnvio.SemConexao -> marcarConexao(false)
            is ResultadoDoEnvio.Concluido -> if (r.mudou) marcarConexao(true)
            ResultadoDoEnvio.SessaoPerdida -> sessaoPerdida()
            ResultadoDoEnvio.JaEmAndamento -> Unit
        }
        val mudou = (r as? ResultadoDoEnvio.Concluido)?.mudou == true || (r as? ResultadoDoEnvio.SemConexao)?.mudou == true
        if (mudou) _mudancas.update { it + 1 }
        atualizarEstado()
        return r
    }

    /** Depois de qualquer alteração feita aqui: atualiza as telas, os alarmes e tenta enviar (agora ou quando der). */
    private suspend fun aposAlterarLocalmente(idosoId: Int) {
        _mudancas.update { it + 1 }
        atualizarEstado()
        val eu = usuarioLogado()
        if (eu != null && eu.ehIdoso && eu.id == idosoId) remediosLocais(idosoId)?.let { efeitos.aoAtualizarRemediosDoIdoso(it) }
        efeitos.agendarSincronizacao()
        escopo.launch { enviarPendencias() }
    }

    // ------------------------------------------------------------ leituras

    /** Espera pelo servidor, mas, se já existe uma cópia no aparelho, só por um tempo: depois disso mostra a cópia. */
    private suspend fun <T> buscarNoServidor(temCopia: Boolean, bloco: suspend () -> T): Resultado<T> {
        if (!temCopia) return chamar(bloco)
        return withTimeoutOrNull(TEMPO_COM_COPIA_MS) { chamar(bloco) } ?: Resultado.Falha("O servidor demorou para responder.")
    }

    private fun semInternet(r: Resultado.Falha) = r.codigo == null

    suspend fun remediosLocais(idosoId: Int): List<MedicamentoDto>? {
        val id = uid() ?: return null
        val d = armazenamento.ler(id)
        val base = d.remedios[idosoId]
        if (base == null && d.pendencias.none { it.idosoId == idosoId }) return null
        return Visao.remedios(base.orEmpty(), d.pendencias, idosoId)
    }

    suspend fun medicamentos(idosoId: Int): Resultado<List<MedicamentoDto>> {
        val id = uid() ?: return chamar { api.medicamentos(idosoId) }
        val temCopia = armazenamento.ler(id).remedios.containsKey(idosoId)
        val versao = sincronizador.versao
        return when (val r = buscarNoServidor(temCopia) { api.medicamentos(idosoId) }) {
            is Resultado.Ok -> {
                // se um envio terminou enquanto a busca corria, esta cópia já nasceu velha: não a guarda
                if (sincronizador.versao == versao) armazenamento.alterar(id) { it.copy(remedios = it.remedios + (idosoId to r.valor)) to Unit }
                marcarConexao(true)
                val lista = remediosLocais(idosoId).orEmpty()
                val eu = usuarioLogado()
                if (eu != null && eu.ehIdoso && eu.id == idosoId) efeitos.aoAtualizarRemediosDoIdoso(lista) // base dos alarmes
                Resultado.Ok(lista)
            }
            is Resultado.Falha -> if (semInternet(r)) {
                marcarConexao(false)
                remediosLocais(idosoId)?.let { Resultado.Ok(it) } ?: r
            } else r
        }
    }

    suspend fun historicoLocal(idosoId: Int): List<HistoricoDto>? {
        val id = uid() ?: return null
        val d = armazenamento.ler(id)
        val base = d.historicos[idosoId]
        if (base == null && d.pendencias.none { it is RegistrarTomada && it.idosoId == idosoId }) return null
        return Visao.historico(base.orEmpty(), d.pendencias, idosoId)
    }

    suspend fun historico(idosoId: Int): Resultado<List<HistoricoDto>> {
        val id = uid() ?: return chamar { api.historico(idosoId) }
        val temCopia = armazenamento.ler(id).historicos.containsKey(idosoId)
        val versao = sincronizador.versao
        return when (val r = buscarNoServidor(temCopia) { api.historico(idosoId) }) {
            is Resultado.Ok -> {
                if (sincronizador.versao == versao) armazenamento.alterar(id) { it.copy(historicos = it.historicos + (idosoId to r.valor)) to Unit }
                marcarConexao(true)
                Resultado.Ok(historicoLocal(idosoId).orEmpty())
            }
            is Resultado.Falha -> if (semInternet(r)) {
                marcarConexao(false)
                historicoLocal(idosoId)?.let { Resultado.Ok(it) } ?: r
            } else r
        }
    }

    /**
     * Os avisos do dia sem falar com o servidor. Do próprio idoso, são calculados aqui (com as mesmas regras do servidor,
     * contando as tomadas que ainda não subiram). De quem o familiar acompanha, são os últimos que ele viu.
     */
    suspend fun avisosLocais(idosoId: Int): List<NotificacaoDto>? {
        val id = uid() ?: return null
        val eu = usuarioLogado() ?: return null
        val d = armazenamento.ler(id)
        if (eu.ehIdoso && eu.id == idosoId) {
            val remedios = remediosLocais(idosoId) ?: return null
            return Visao.avisos(remedios, Visao.historico(d.historicos[idosoId].orEmpty(), d.pendencias, idosoId), agora())
        }
        return d.avisos[idosoId]
    }

    suspend fun notificacoes(idosoId: Int): Resultado<List<NotificacaoDto>> {
        val id = uid() ?: return chamar { api.notificacoes(idosoId) }
        val eu = usuarioLogado()
        val proprio = eu != null && eu.ehIdoso && eu.id == idosoId
        val d = armazenamento.ler(id)

        // Com alterações a caminho, o servidor ainda não sabe delas: o aviso certo é o calculado aqui.
        if (proprio && d.pendencias.any { it.idosoId == idosoId }) avisosLocais(idosoId)?.let { return Resultado.Ok(it) }

        val temCopia = if (proprio) d.remedios.containsKey(idosoId) else d.avisos.containsKey(idosoId)
        return when (val r = buscarNoServidor(temCopia) { api.notificacoes(idosoId) }) {
            is Resultado.Ok -> {
                if (!proprio) armazenamento.alterar(id) { it.copy(avisos = it.avisos + (idosoId to r.valor)) to Unit }
                marcarConexao(true)
                r
            }
            is Resultado.Falha -> if (semInternet(r)) {
                marcarConexao(false)
                avisosLocais(idosoId)?.let { Resultado.Ok(it) } ?: r
            } else r
        }
    }

    suspend fun idososLocais(): List<UsuarioDto>? = uid()?.let { armazenamento.ler(it).idosos }

    suspend fun meusIdosos(): Resultado<List<UsuarioDto>> {
        val id = uid() ?: return chamar { api.meusIdosos() }
        val temCopia = armazenamento.ler(id).idosos != null
        return when (val r = buscarNoServidor(temCopia) { api.meusIdosos() }) {
            is Resultado.Ok -> {
                armazenamento.alterar(id) { it.copy(idosos = r.valor) to Unit }
                marcarConexao(true)
                r
            }
            is Resultado.Falha -> if (semInternet(r)) {
                marcarConexao(false)
                idososLocais()?.let { Resultado.Ok(it) } ?: r
            } else r
        }
    }

    // ---------------------------------------- escritas de remédios e tomadas

    private fun erro(mensagem: String) = Resultado.Falha(mensagem, 400)

    /** As mesmas regras do servidor, para a pessoa saber já (e sem internet) que algo está errado. */
    private fun validarRemedio(m: MedicamentoRequest, cadastro: Boolean): String? {
        if (cadastro || m.nome != null) {
            if (m.nome.isNullOrBlank()) return "O nome do medicamento é obrigatório e não pode ser vazio."
            if (m.nome.trim().length > 150) return "O nome do medicamento pode ter no máximo 150 caracteres."
        }
        if (cadastro && m.diaSemana == null) return "O dia da semana é obrigatório."
        if (cadastro && m.horario == null) return "O horário do medicamento é obrigatório."
        if (cadastro && m.tipo == null) return "O tipo do medicamento é obrigatório."
        return null
    }

    private fun idosoDoRemedio(d: DadosLocais, remedioId: Int): Int? =
        d.remedios.entries.firstOrNull { (_, lista) -> lista.any { it.id == remedioId } }?.key
            ?: d.pendencias.filterIsInstance<CriarRemedio>().firstOrNull { it.idLocal == remedioId }?.idosoId

    private fun remedioNaVisao(d: DadosLocais, remedioId: Int): MedicamentoDto? {
        val idoso = idosoDoRemedio(d, remedioId) ?: return null
        return Visao.remedios(d.remedios[idoso].orEmpty(), d.pendencias, idoso).firstOrNull { it.id == remedioId }
    }

    private fun agoraTexto() = agora().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    suspend fun cadastrarMedicamento(idosoId: Int, m: MedicamentoRequest): Resultado<MedicamentoDto> {
        val id = uid() ?: return Resultado.Falha("Entre novamente para continuar.", 401)
        validarRemedio(m, cadastro = true)?.let { return erro(it) }
        val criado = armazenamento.alterar(id) { d ->
            val idLocal = d.proximoIdLocal
            val op = CriarRemedio(novaChave(), idosoId, idLocal, m.nome!!.trim(), m.diaSemana!!, m.horario!!, m.tipo!!, agoraTexto())
            d.copy(pendencias = d.pendencias + op, proximoIdLocal = idLocal - 1) to
                MedicamentoDto(idLocal, idosoId, op.nome, op.horario, op.diaSemana, op.tipo)
        }
        aposAlterarLocalmente(idosoId)
        return Resultado.Ok(criado)
    }

    suspend fun editarMedicamento(remedioId: Int, m: MedicamentoRequest): Resultado<MedicamentoDto> {
        val id = uid() ?: return Resultado.Falha("Entre novamente para continuar.", 401)
        validarRemedio(m, cadastro = false)?.let { return erro(it) }
        val resultado = armazenamento.alterar(id) { d ->
            val atual = remedioNaVisao(d, remedioId) ?: return@alterar d to null
            val idosoId = atual.idosoId
            val pendencias = if (remedioId < 0) {
                // remédio que ainda nem subiu: a edição vai direto para o cadastro pendente
                d.pendencias.map {
                    if (it is CriarRemedio && it.idLocal == remedioId)
                        it.copy(nome = m.nome?.trim() ?: it.nome, diaSemana = m.diaSemana ?: it.diaSemana, horario = m.horario ?: it.horario, tipo = m.tipo ?: it.tipo)
                    else it
                }
            } else {
                val existente = d.pendencias.filterIsInstance<EditarRemedio>().firstOrNull { it.remedioId == remedioId }
                if (existente != null) {
                    // já há uma edição na fila: junta as duas (o que veio depois vale)
                    d.pendencias.map {
                        if (it === existente)
                            existente.copy(nome = m.nome?.trim() ?: existente.nome, diaSemana = m.diaSemana ?: existente.diaSemana,
                                horario = m.horario ?: existente.horario, tipo = m.tipo ?: existente.tipo)
                        else it
                    }
                } else {
                    d.pendencias + EditarRemedio(novaChave(), idosoId, remedioId, atual.nome, m.nome?.trim(), m.diaSemana, m.horario, m.tipo, agoraTexto())
                }
            }
            val novo = d.copy(pendencias = pendencias)
            novo to remedioNaVisao(novo, remedioId)
        }
        val editado = resultado ?: return Resultado.Falha("Este remédio não existe mais.", 404)
        aposAlterarLocalmente(editado.idosoId)
        return Resultado.Ok(editado)
    }

    suspend fun excluirMedicamento(remedioId: Int): Resultado<Unit> {
        val id = uid() ?: return Resultado.Falha("Entre novamente para continuar.", 401)
        val idosoId = armazenamento.alterar(id) { d ->
            val atual = remedioNaVisao(d, remedioId) ?: return@alterar d to null
            val pendencias = if (remedioId < 0) {
                // remédio que nem chegou a subir: some com tudo o que era dele, sem nada para enviar
                d.pendencias.filterNot { (it is CriarRemedio && it.idLocal == remedioId) || refereRemedio(it, remedioId) }
            } else {
                d.pendencias.filterNot { refereRemedio(it, remedioId) } +
                    ExcluirRemedio(novaChave(), atual.idosoId, remedioId, atual.nome, agoraTexto())
            }
            d.copy(pendencias = pendencias) to atual.idosoId
        } ?: return Resultado.Falha("Este remédio não existe mais.", 404)
        aposAlterarLocalmente(idosoId)
        return Resultado.Ok(Unit)
    }

    private fun refereRemedio(op: Operacao, remedioId: Int) = when (op) {
        is EditarRemedio -> op.remedioId == remedioId
        is RegistrarTomada -> op.remedioId == remedioId
        is ExcluirRemedio -> op.remedioId == remedioId
        is CriarRemedio -> false
    }

    private class SaidaDaTomada(val resultado: Resultado<HistoricoDto>, val idosoId: Int? = null)

    /** O idoso tocou em "já tomei": vale na hora, com a hora do toque, e sobe quando houver internet. */
    suspend fun registrarTomada(remedioId: Int): Resultado<HistoricoDto> {
        val id = uid() ?: return Resultado.Falha("Entre novamente para continuar.", 401)
        val quando = agora().truncatedTo(ChronoUnit.SECONDS)
        val saida = armazenamento.alterar<SaidaDaTomada>(id) { d ->
            val atual = remedioNaVisao(d, remedioId) ?: return@alterar d to SaidaDaTomada(Resultado.Falha("Este remédio não existe mais.", 404))
            // uma tomada por remédio por dia (como no servidor), contando as que ainda não subiram
            val dia = quando.toLocalDate().toString()
            val historico = Visao.historico(d.historicos[atual.idosoId].orEmpty(), d.pendencias, atual.idosoId)
            if (historico.any { it.medicamentoId == remedioId && it.foiTomado && it.dataHora.startsWith(dia) }) {
                return@alterar d to SaidaDaTomada(Resultado.Falha("Você já registrou que tomou ${atual.nome} nesse dia.", 400))
            }
            val texto = quando.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val op = RegistrarTomada(novaChave(), atual.idosoId, remedioId, atual.nome, texto, texto)
            d.copy(pendencias = d.pendencias + op) to
                SaidaDaTomada(Resultado.Ok(HistoricoDto(-1_000_000 - d.pendencias.size, remedioId, atual.nome, texto, true)), atual.idosoId)
        }
        val resultado = saida.resultado
        // Deu certo, ou já estava registrado: nos dois casos não precisa mais avisar de atraso.
        if (resultado is Resultado.Ok || (resultado is Resultado.Falha && resultado.codigo == 400)) efeitos.aoRegistrarTomada(remedioId)
        saida.idosoId?.let { aposAlterarLocalmente(it) }
        return resultado
    }

    // ----------------------------------------- o que precisa de internet

    suspend fun meusFamiliares() = chamar { api.meusFamiliares() }
    suspend fun adicionarFamiliar(email: String) = chamar { api.adicionarFamiliar(EmailRequest(email.trim())) }
    suspend fun removerFamiliar(id: Int) = chamarVazio { api.removerFamiliar(id) }

    suspend fun pedirVinculo(email: String) = chamar { api.pedirVinculo(EmailRequest(email.trim())) }
    suspend fun pedidosRecebidos() = chamar { api.pedidosRecebidos() }
    suspend fun aceitarPedido(familiarId: Int) = chamarVazio { api.aceitarPedido(familiarId) }
    suspend fun recusarPedido(familiarId: Int) = chamarVazio { api.recusarPedido(familiarId) }

    private companion object {
        /** Com uma cópia no aparelho, não vale esperar o servidor (que pode estar acordando) por mais que isto. */
        const val TEMPO_COM_COPIA_MS = 8_000L
    }
}
