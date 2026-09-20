package br.com.cuidamed.notificacoes

import android.content.Context
import android.util.Log
import br.com.cuidamed.CuidaMedApp
import br.com.cuidamed.data.AparelhoDePush
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** O token de push deste aparelho, vindo do Firebase. Sem o google-services.json no build, o push fica desligado. */
class AparelhoFirebase(private val contexto: Context) : AparelhoDePush {

    override suspend fun token(): String? {
        if (FirebaseApp.getApps(contexto).isEmpty()) return null
        return suspendCancellableCoroutine { continuacao ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { tarefa ->
                if (!tarefa.isSuccessful) {
                    Log.w("CuidaMed", "Push: sem token do Firebase agora (${tarefa.exception?.javaClass?.simpleName}: ${tarefa.exception?.message})")
                }
                if (continuacao.isActive) continuacao.resume(tarefa.result?.takeIf { tarefa.isSuccessful })
            }
        }
    }
}

/**
 * Mantém o servidor com o código de push atual deste aparelho. Reenvia quando o código ou a conta muda, quando o app é
 * aberto e, no mais, pelo menos a cada hora: o servidor pode ter perdido o registro (por exemplo, ao descartar um código
 * que o Firebase recusou), e sem registro nenhum aviso chega. É um pedido pequeno.
 */
object RegistroDePush {
    private const val PREFS = "push"
    private const val VALIDADE_MS = 60L * 60 * 1000

    /** [tokenNovo] vem do Firebase quando o código do aparelho muda; sem ele, pergunta ao Firebase qual é o atual. */
    suspend fun garantir(app: CuidaMedApp, tokenNovo: String? = null, forcar: Boolean = false) {
        val repositorio = app.repositorio
        val usuarioId = repositorio.usuarioAtualId() ?: return
        val token = tokenNovo ?: AparelhoFirebase(app).token() ?: return
        val marca = "$usuarioId|$token"
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val agora = System.currentTimeMillis()
        val recente = agora - prefs.getLong("quando", 0L) < VALIDADE_MS
        if (!forcar && recente && prefs.getString("registrado", null) == marca) return
        if (repositorio.registrarAparelho(token) is br.com.cuidamed.data.Resultado.Ok) {
            prefs.edit().putString("registrado", marca).putLong("quando", agora).apply()
        }
    }

    fun esquecer(contexto: Context) {
        contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/**
 * Recebe o push. Ele não traz dado nenhum (só "tem novidade"): o app busca no servidor, como faz a cada 15 minutos,
 * e a notificação mostrada é montada aqui no aparelho.
 */
class ServicoDePush : FirebaseMessagingService() {

    private companion object {
        const val TEMPO_MAXIMO_MS = 8_000L
    }

    override fun onMessageReceived(mensagem: RemoteMessage) {
        // O Android deixa o serviço rodar por cerca de 10 segundos, mesmo com o app fechado e o celular parado: dá para
        // buscar e mostrar o aviso aqui mesmo. Agendar um trabalho (WorkManager) esperaria a próxima janela do sistema,
        // que com o aparelho parado pode levar minutos. Só se não der tempo ou faltar rede é que se agenda para depois.
        val concluiu = try {
            runBlocking { withTimeoutOrNull(TEMPO_MAXIMO_MS) { VerificadorDeEventos.executar(applicationContext) } }
        } catch (e: Exception) {
            Log.w("CuidaMed", "Push: falha ao verificar na hora (${e.javaClass.simpleName})")
            null
        }
        if (concluiu != true) Eventos.verificarAgora(applicationContext)
    }

    override fun onNewToken(token: String) {
        // O Firebase trocou o código deste aparelho: se o servidor ficar com o antigo, os avisos deixam de chegar.
        // Registra o novo na hora (o serviço tem alguns segundos), sem esperar um trabalho em segundo plano.
        RegistroDePush.esquecer(applicationContext)
        val app = applicationContext as CuidaMedApp
        try {
            runBlocking {
                withTimeoutOrNull(TEMPO_MAXIMO_MS) {
                    app.repositorio.restaurarSessaoLocal()
                    RegistroDePush.garantir(app, token)
                }
            }
        } catch (e: Exception) {
            Log.w("CuidaMed", "Push: não deu para registrar o novo código agora (${e.javaClass.simpleName})")
        }
        Eventos.verificarAgora(applicationContext) // rede de segurança: tenta de novo pelo trabalho agendado
    }
}
