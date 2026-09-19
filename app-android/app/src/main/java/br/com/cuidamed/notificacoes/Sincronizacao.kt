package br.com.cuidamed.notificacoes

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.cuidamed.CuidaMedApp
import br.com.cuidamed.data.local.ResultadoDoEnvio
import java.util.concurrent.TimeUnit

/** Garante o envio da fila de alterações feitas sem internet, mesmo com o app fechado. */
object Sincronizacao {
    private const val TRABALHO = "sincronizar"

    fun agendar(contexto: Context) {
        WorkManager.getInstance(contexto).enqueueUniqueWork(
            TRABALHO,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SincronizadorWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }
}

class SincronizadorWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val repositorio = (applicationContext as CuidaMedApp).repositorio
        if (!repositorio.temSessao()) return Result.success()
        repositorio.restaurarSessaoLocal()
        return when (repositorio.enviarPendencias()) {
            // sem internet ou servidor fora: o Android tenta de novo mais tarde, com espera crescente
            is ResultadoDoEnvio.SemConexao -> Result.retry()
            else -> Result.success()
        }
    }
}
