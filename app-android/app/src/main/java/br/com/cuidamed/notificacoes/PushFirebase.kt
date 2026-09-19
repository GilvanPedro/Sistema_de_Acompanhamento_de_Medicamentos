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
import kotlinx.coroutines.suspendCancellableCoroutine
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
 * Avisa o servidor do token do aparelho, mas só quando ele muda ou a conta muda (não a cada verificação em segundo plano).
 */
object RegistroDePush {
    private const val PREFS = "push"

    suspend fun garantir(app: CuidaMedApp) {
        val repositorio = app.repositorio
        val usuarioId = repositorio.usuarioAtualId() ?: return
        val token = AparelhoFirebase(app).token() ?: return
        val marca = "$usuarioId|$token"
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("registrado", null) == marca) return
        if (repositorio.registrarAparelho(token) is br.com.cuidamed.data.Resultado.Ok) {
            prefs.edit().putString("registrado", marca).apply()
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

    override fun onMessageReceived(mensagem: RemoteMessage) {
        Eventos.verificarAgora(applicationContext)
    }

    override fun onNewToken(token: String) {
        // O token mudou: a marca antiga não vale mais, e a próxima verificação registra o novo.
        RegistroDePush.esquecer(applicationContext)
        Eventos.verificarAgora(applicationContext)
    }
}
