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
import br.com.cuidamed.CuidaMedApp
import br.com.cuidamed.data.Repositorio
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.data.UsuarioDto
import br.com.cuidamed.data.jsonDaApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate
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

    override suspend fun doWork(): Result {
        val repositorio = (applicationContext as CuidaMedApp).repositorio
        if (!repositorio.temSessao()) return Result.success()
        repositorio.restaurarSessaoLocal()
        repositorio.enviarPendencias() // aproveita a verificação para mandar o que ficou na fila

        RegistroDePush.garantir(applicationContext as CuidaMedApp)

        val usuario = when (val r = repositorio.eu()) {
            is Resultado.Ok -> r.valor
            // sem código = falha de rede: tenta de novo mais tarde
            is Resultado.Falha -> return if (r.codigo == null) Result.retry() else Result.success()
        }
        if (usuario.ehIdoso) verificarIdoso(repositorio, usuario) else verificarFamiliar(repositorio, usuario)
        return Result.success()
    }

    /** O idoso: atualiza os alarmes com os remédios atuais (o familiar pode ter mudado algo) e avisa de pedidos novos. */
    private suspend fun verificarIdoso(repositorio: Repositorio, usuario: UsuarioDto) {
        repositorio.medicamentos(usuario.id) // ao carregar, já reagenda os alarmes
        val pedidos = (repositorio.pedidosRecebidos() as? Resultado.Ok)?.valor ?: return
        pedidos.forEach { p ->
            val chave = "P|${p.familiar.id}|${p.solicitadoEm}"
            if (EventosJaAvisados.ehNovo(applicationContext, chave)) {
                Notificador.pedidoDeVinculo(applicationContext, chave, p.familiar.nome)
            }
        }
    }

    /** O familiar: avisa quando alguém que acompanha esqueceu ou tomou um remédio hoje. */
    private suspend fun verificarFamiliar(repositorio: Repositorio, usuario: UsuarioDto) {
        val idosos = (repositorio.meusIdosos() as? Resultado.Ok)?.valor ?: return
        val hoje = LocalDate.now()
        for (idoso in idosos) {
            val avisos = (repositorio.notificacoes(idoso.id) as? Resultado.Ok)?.valor ?: continue
            for (aviso in avisos) {
                val m = aviso.medicamento
                when (aviso.tipo) {
                    "ESQUECIDO" -> {
                        val chave = "E|${idoso.id}|${m.id}|$hoje"
                        if (EventosJaAvisados.ehNovo(applicationContext, chave)) {
                            Notificador.avisoDeFamiliar(
                                applicationContext, chave, "Remédio não tomado",
                                "${idoso.nome} ainda não tomou ${m.nome}. Era para as ${m.horario}.",
                            )
                        }
                    }
                    "TOMADO" -> {
                        val chave = "T|${idoso.id}|${m.id}|$hoje"
                        if (EventosJaAvisados.ehNovo(applicationContext, chave)) {
                            Notificador.avisoDeFamiliar(
                                applicationContext, chave, "Remédio tomado", "${idoso.nome} já tomou ${m.nome}.",
                            )
                        }
                    }
                }
            }
        }
    }
}
