package br.com.cuidamed.notificacoes

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.cuidamed.data.jsonDaApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.TimeUnit

/** Quais avisos já viraram notificação, para o mesmo aviso não tocar de novo a cada verificação. */
object EventosJaAvisados {
    private const val PREFS = "eventos_avisados"
    private val serializador = MapSerializer(String.serializer(), Long.serializer())

    private fun ler(contexto: Context): MutableMap<String, Long> {
        val texto = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mapa", null) ?: return mutableMapOf()
        return try {
            jsonDaApi.decodeFromString(serializador, texto).toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    /** true se este aviso é novo (e já o marca como avisado). Esquece os de mais de 3 dias. */
    @Synchronized
    fun ehNovo(contexto: Context, chave: String): Boolean {
        val mapa = ler(contexto)
        val agora = System.currentTimeMillis()
        mapa.values.removeAll { agora - it > TimeUnit.DAYS.toMillis(3) }
        val novo = !mapa.containsKey(chave)
        if (novo) mapa[chave] = agora
        contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mapa", jsonDaApi.encodeToString(serializador, mapa)).apply()
        return novo
    }

    fun limpar(contexto: Context) {
        contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/**
 * Liga e desliga a verificação de eventos em segundo plano. O Android só permite rodar isso a cada 15 minutos
 * (no mínimo), então sozinha ela pode atrasar alguns minutos. O push do Firebase (ServicoDePush) acorda o app na
 * hora e chama [verificarAgora]; esta rotina periódica fica como rede de segurança (push pode falhar ou não chegar).
 */
object Eventos {
    private const val PERIODICO = "eventos-periodico"
    private const val AGORA = "eventos-agora"

    fun iniciar(contexto: Context) {
        val rede = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val gerente = WorkManager.getInstance(contexto)
        gerente.enqueueUniquePeriodicWork(
            PERIODICO,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<VerificadorDeEventosWorker>(15, TimeUnit.MINUTES)
                .setConstraints(rede)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build(),
        )
        // Uma verificação logo de cara (ao entrar ou abrir o app), sem esperar os 15 minutos.
        gerente.enqueueUniqueWork(
            AGORA,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<VerificadorDeEventosWorker>().setConstraints(rede).build(),
        )
    }

    /** Chamado pelo push: verifica os eventos agora, sem esperar os 15 minutos. */
    fun verificarAgora(contexto: Context) {
        WorkManager.getInstance(contexto).enqueueUniqueWork(
            AGORA,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<VerificadorDeEventosWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    fun parar(contexto: Context) {
        val gerente = WorkManager.getInstance(contexto)
        gerente.cancelUniqueWork(PERIODICO)
        gerente.cancelUniqueWork(AGORA)
    }
}

class VerificadorDeEventosWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result =
        // sem código = falha de rede: tenta de novo mais tarde
        if (VerificadorDeEventos.executar(applicationContext)) Result.success() else Result.retry()
}
