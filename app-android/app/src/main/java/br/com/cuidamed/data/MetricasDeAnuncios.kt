package br.com.cuidamed.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate

@Serializable
data class EventoDeAnuncioDto(
    val tipo: String,
    val anuncioId: String,
    val posicao: String,
    val perfil: String,
    val dia: String,
    val quantidade: Int,
)

@Serializable
data class EventosDeAnunciosRequest(val eventos: List<EventoDeAnuncioDto>)

/** Onde as contagens ainda não enviadas ficam guardadas (para não se perderem se o app fechar). */
interface GuardaDeContagens {
    fun ler(): String?
    fun salvar(texto: String)
}

enum class TipoDeEvento { EXIBICAO, CLIQUE }

/**
 * Conta, de forma anônima, quantas vezes cada banner apareceu e foi tocado, e envia essas somas ao servidor em lotes
 * (para os relatórios dos anunciantes). Só guarda números: banner, posição na tela, perfil (idoso, familiar ou
 * visitante) e dia. Nada de nome, e-mail ou identificador do aparelho. Se não der para enviar (sem internet), as
 * contagens ficam guardadas e vão junto no próximo envio.
 */
class MetricasDeAnuncios(
    private val guarda: GuardaDeContagens,
    private val enviar: suspend (List<EventoDeAnuncioDto>) -> Boolean,
    private val escopo: CoroutineScope,
    private val hoje: () -> LocalDate = { LocalDate.now() },
    private val atrasoDoEnvioMs: Long = 20_000,
) {
    private companion object {
        const val MAXIMO_DE_CONTAGENS = 500
        const val MAXIMO_POR_EVENTO = 100
        const val MAXIMO_POR_ENVIO = 150
    }

    private data class Chave(val tipo: TipoDeEvento, val anuncioId: String, val posicao: String, val perfil: String, val dia: String)

    private val pendentes = mutableMapOf<Chave, Int>()
    private val trava = Any()
    private val envio = Mutex()
    private var agendado: Job? = null

    init {
        guarda.ler()?.let { texto ->
            runCatching { jsonDaApi.decodeFromString(ListSerializer(EventoDeAnuncioDto.serializer()), texto) }
                .getOrDefault(emptyList())
                .forEach { e ->
                    val tipo = TipoDeEvento.entries.firstOrNull { it.name == e.tipo } ?: return@forEach
                    pendentes.merge(Chave(tipo, e.anuncioId, e.posicao, e.perfil, e.dia), e.quantidade, Int::plus)
                }
        }
    }

    /** Anota um evento (o envio acontece pouco depois, junto com outros). */
    fun registrar(tipo: TipoDeEvento, anuncioId: String, posicao: String, perfil: String) {
        synchronized(trava) {
            val chave = Chave(tipo, anuncioId, posicao, perfil, hoje().toString())
            if (chave !in pendentes && pendentes.size >= MAXIMO_DE_CONTAGENS) return // muito acumulado: descarta o novo
            pendentes.merge(chave, 1, Int::plus)
            guardar()
        }
        agendarEnvio()
    }

    /** Ao abrir o app: se sobrou algo de antes (o app fechou ou faltou internet), manda logo. */
    fun iniciar() {
        if (pendentes() > 0) agendarEnvio()
    }

    /** Manda agora, sem esperar (por exemplo, quando o app vai para o fundo). */
    fun enviarEmSegundoPlano() {
        escopo.launch { enviarAgora() }
    }

    /** Quantos eventos ainda esperam para ser enviados (para testes e diagnóstico). */
    fun pendentes(): Int = synchronized(trava) { pendentes.values.sum() }

    private fun agendarEnvio() {
        synchronized(trava) {
            if (agendado?.isActive == true) return
            agendado = escopo.launch {
                delay(atrasoDoEnvioMs)
                enviarAgora()
            }
        }
    }

    /** Envia o que estiver pendente. Falhou? Fica guardado para a próxima vez. */
    suspend fun enviarAgora() {
        envio.withLock {
            while (true) {
                val lote = synchronized(trava) { pendentes.entries.take(MAXIMO_POR_ENVIO).map { it.key to it.value } }
                if (lote.isEmpty()) return
                val eventos = lote.flatMap { (chave, quantidade) ->
                    // o servidor aceita até 100 por evento: quantidades maiores viram vários eventos
                    val partes = (quantidade + MAXIMO_POR_EVENTO - 1) / MAXIMO_POR_EVENTO
                    (0 until partes).map { i ->
                        EventoDeAnuncioDto(
                            chave.tipo.name, chave.anuncioId, chave.posicao, chave.perfil, chave.dia,
                            minOf(MAXIMO_POR_EVENTO, quantidade - i * MAXIMO_POR_EVENTO),
                        )
                    }
                }
                val enviou = runCatching { enviar(eventos.take(200)) }.getOrDefault(false)
                if (!enviou || eventos.size > 200) return // (mais de 200 num lote não acontece; na dúvida, tenta de novo depois)
                synchronized(trava) {
                    lote.forEach { (chave, quantidade) ->
                        val restante = (pendentes[chave] ?: 0) - quantidade // o que chegou durante o envio continua na fila
                        if (restante > 0) pendentes[chave] = restante else pendentes.remove(chave)
                    }
                    guardar()
                }
            }
        }
    }

    private fun guardar() {
        val eventos = pendentes.map { (c, q) -> EventoDeAnuncioDto(c.tipo.name, c.anuncioId, c.posicao, c.perfil, c.dia, q) }
        guarda.salvar(jsonDaApi.encodeToString(ListSerializer(EventoDeAnuncioDto.serializer()), eventos))
    }
}
