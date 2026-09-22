package br.com.cuidamed.notificacoes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.cuidamed.CuidaMedApp
import br.com.cuidamed.data.MedicamentoDto
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.data.jsonDaApi
import kotlinx.serialization.builtins.ListSerializer
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/** A cópia local dos remédios do idoso: é o que dá para agendar mesmo sem internet, e depois de reiniciar o celular. */
object PlanoLocal {
    private const val PREFS = "plano_lembretes"
    private val serializador = ListSerializer(MedicamentoDto.serializer())

    fun salvar(contexto: Context, remedios: List<MedicamentoDto>) {
        contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("remedios", jsonDaApi.encodeToString(serializador, remedios)).apply()
    }

    fun ler(contexto: Context): List<MedicamentoDto> {
        val texto = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("remedios", null) ?: return emptyList()
        return try {
            jsonDaApi.decodeFromString(serializador, texto)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun limpar(contexto: Context) {
        contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/** Os "já tomei" deste aparelho, para não avisar de atraso de um remédio que já foi tomado (funciona sem internet). */
object Confirmacoes {
    private const val PREFS = "confirmacoes"

    private fun prefs(contexto: Context) = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun marcar(contexto: Context, medicamentoId: Int, dia: LocalDate = LocalDate.now()) {
        val hoje = LocalDate.now()
        val atuais = prefs(contexto).getStringSet("marcas", emptySet()).orEmpty()
            .filter { LocalDate.parse(it.substringAfter('|')).isAfter(hoje.minusDays(3)) }
            .toMutableSet()
        atuais.add("$medicamentoId|$dia")
        prefs(contexto).edit().putStringSet("marcas", atuais).apply()
    }

    fun tomouEm(contexto: Context, medicamentoId: Int, dia: LocalDate): Boolean =
        prefs(contexto).getStringSet("marcas", emptySet()).orEmpty().contains("$medicamentoId|$dia")

    fun tomouHoje(contexto: Context, medicamentoId: Int) = tomouEm(contexto, medicamentoId, LocalDate.now())

    fun limpar(contexto: Context) {
        prefs(contexto).edit().clear().apply()
    }
}

/**
 * Marca no Android os alarmes dos remédios: um no horário de cada remédio (repetindo toda semana) e, enquanto não
 * marcar "já tomei", um alarme de atraso a cada 10 minutos. Passada 1 hora do horário, para de insistir até a semana
 * que vem, mesmo sem marcar (ADR-0065). Tudo local, então funciona sem internet.
 */
object AgendadorDeLembretes {

    /** Depois disso sem marcar, os alarmes de atraso param de insistir por esse dia. */
    const val TOLERANCIA_MINUTOS = 60L

    /** De quanto em quanto tempo o alarme de atraso repete, enquanto ainda estiver dentro da tolerância. */
    const val INTERVALO_DE_REPETICAO_MINUTOS = 10L
    private const val EXTRA_TIPO = "tipo"
    private const val EXTRA_NOME = "nome"
    private const val EXTRA_HORARIO = "horario"
    private const val EXTRA_PREVISTO_EPOCH = "previsto_epoch"
    const val TIPO_LEMBRETE = "LEMBRETE"
    const val TIPO_ATRASO = "ATRASO"

    private fun codigoDoLembrete(medicamentoId: Int) = medicamentoId * 10
    private fun codigoDoAtraso(medicamentoId: Int) = medicamentoId * 10 + 1

    /** Troca todos os alarmes pelos da lista atual de remédios (chamado quando a lista é carregada do servidor). */
    fun reagendar(contexto: Context, remedios: List<MedicamentoDto>) {
        PlanoLocal.ler(contexto).forEach { cancelar(contexto, it.id) }
        PlanoLocal.salvar(contexto, remedios)
        remedios.forEach { agendarProximo(contexto, it) }
    }

    /** Depois de reiniciar o celular ou atualizar o app, os alarmes somem: marca de novo a partir da cópia local. */
    fun restaurar(contexto: Context) {
        PlanoLocal.ler(contexto).forEach { agendarProximo(contexto, it) }
    }

    fun cancelarTudo(contexto: Context) {
        PlanoLocal.ler(contexto).forEach { cancelar(contexto, it.id) }
        PlanoLocal.limpar(contexto)
    }

    fun agendarProximo(contexto: Context, remedio: MedicamentoDto, depoisDe: ZonedDateTime = ZonedDateTime.now()) {
        val proxima = proximaOcorrencia(remedio, depoisDe) ?: return
        marcar(contexto, codigoDoLembrete(remedio.id), proxima, TIPO_LEMBRETE, remedio, proxima.toInstant().toEpochMilli())

        // Se o horário acabou de passar (dentro da tolerância) e ainda não foi tomado, os avisos de atraso ainda valem:
        // marca o próximo já (a checagem de "passou 1h?" acontece de novo quando ele tocar, com base no horário previsto).
        val agora = ZonedDateTime.now()
        val anterior = proxima.minusWeeks(1)
        val limite = anterior.plusMinutes(TOLERANCIA_MINUTOS)
        if (!anterior.isAfter(agora) && limite.isAfter(agora) && !Confirmacoes.tomouEm(contexto, remedio.id, anterior.toLocalDate())) {
            agendarAtraso(contexto, remedio, agora.plusMinutes(1), anterior.toInstant().toEpochMilli())
        }
    }

    fun agendarAtraso(contexto: Context, remedio: MedicamentoDto, quando: ZonedDateTime, previstoEpoch: Long) =
        marcar(contexto, codigoDoAtraso(remedio.id), quando, TIPO_ATRASO, remedio, previstoEpoch)

    /** O remédio foi tomado: o segundo alarme (o de atraso) não precisa mais tocar, e some da barra de status. */
    fun cancelarAtraso(contexto: Context, medicamentoId: Int) {
        contexto.getSystemService(AlarmManager::class.java)
            .cancel(intencao(contexto, codigoDoAtraso(medicamentoId), TIPO_ATRASO, "", ""))
    }

    private fun cancelar(contexto: Context, medicamentoId: Int) {
        val alarmes = contexto.getSystemService(AlarmManager::class.java)
        alarmes.cancel(intencao(contexto, codigoDoLembrete(medicamentoId), TIPO_LEMBRETE, "", ""))
        alarmes.cancel(intencao(contexto, codigoDoAtraso(medicamentoId), TIPO_ATRASO, "", ""))
    }

    /** O próximo horário (hoje ou na semana que vem) em que este remédio deve ser tomado. */
    fun proximaOcorrencia(remedio: MedicamentoDto, depoisDe: ZonedDateTime): ZonedDateTime? = try {
        val dia = DayOfWeek.valueOf(remedio.diaSemana)
        val hora = LocalTime.parse(remedio.horario)
        val data = depoisDe.toLocalDate().with(TemporalAdjusters.nextOrSame(dia))
        val candidato = data.atTime(hora).atZone(depoisDe.zone)
        if (candidato.isAfter(depoisDe)) candidato else data.plusWeeks(1).atTime(hora).atZone(depoisDe.zone)
    } catch (e: Exception) {
        null
    }

    private fun intencao(
        contexto: Context, codigo: Int, tipo: String, nome: String, horario: String,
        medicamentoId: Int = codigo / 10, previstoEpoch: Long = 0L,
    ): PendingIntent {
        val intent = Intent(contexto, LembreteReceiver::class.java)
            .putExtra(EXTRA_MEDICAMENTO, medicamentoId)
            .putExtra(EXTRA_TIPO, tipo)
            .putExtra(EXTRA_NOME, nome)
            .putExtra(EXTRA_HORARIO, horario)
            .putExtra(EXTRA_PREVISTO_EPOCH, previstoEpoch)
        return PendingIntent.getBroadcast(contexto, codigo, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun marcar(contexto: Context, codigo: Int, quando: ZonedDateTime, tipo: String, remedio: MedicamentoDto, previstoEpoch: Long) {
        val alarmes = contexto.getSystemService(AlarmManager::class.java)
        val intencao = intencao(contexto, codigo, tipo, remedio.nome, remedio.horario, remedio.id, previstoEpoch)
        val momento = quando.toInstant().toEpochMilli()
        try {
            // Como o despertador do celular: exato, dispara mesmo em modo de economia e pode ligar a tela.
            // (Aparece o ícone de alarme na barra de status enquanto houver um marcado.)
            val abrirApp = PendingIntent.getActivity(
                contexto, 0, Intent(contexto, br.com.cuidamed.MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarmes.setAlarmClock(AlarmManager.AlarmClockInfo(momento, abrirApp), intencao)
        } catch (e: SecurityException) {
            alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, intencao)
        }
    }
}

/**
 * Toca quando chega a hora de um lembrete ou de um aviso de atraso: dispara o alarme e, se ainda não passou 1 hora do
 * horário previsto, marca o próximo atraso para 10 minutos depois. Passada a hora, simplesmente para de reagendar —
 * o alarme não toca mais por esse remédio até o horário da semana que vem.
 */
class LembreteReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val medicamentoId = intent.getIntExtra(EXTRA_MEDICAMENTO, -1)
        if (medicamentoId < 0) return
        val nome = intent.getStringExtra("nome").orEmpty()
        val horario = intent.getStringExtra("horario").orEmpty()

        when (intent.getStringExtra("tipo")) {
            AgendadorDeLembretes.TIPO_LEMBRETE -> {
                if (!Confirmacoes.tomouHoje(contexto, medicamentoId)) ServicoDoAlarme.tocar(contexto, medicamentoId, nome, horario, AgendadorDeLembretes.TIPO_LEMBRETE)
                // Se o remédio foi removido da lista, para de repetir; senão, marca o primeiro atraso (10 min) e o horário da semana que vem.
                val remedio = PlanoLocal.ler(contexto).firstOrNull { it.id == medicamentoId } ?: return
                val agora = ZonedDateTime.now()
                AgendadorDeLembretes.agendarAtraso(
                    contexto, remedio, agora.plusMinutes(AgendadorDeLembretes.INTERVALO_DE_REPETICAO_MINUTOS), agora.toInstant().toEpochMilli(),
                )
                AgendadorDeLembretes.agendarProximo(contexto, remedio, agora.plusMinutes(1))
            }
            AgendadorDeLembretes.TIPO_ATRASO -> {
                if (Confirmacoes.tomouHoje(contexto, medicamentoId)) return
                val previstoEpoch = intent.getLongExtra("previsto_epoch", 0L)
                val agora = Instant.now()
                if (previstoEpoch > 0 && Duration.between(Instant.ofEpochMilli(previstoEpoch), agora) >= Duration.ofMinutes(AgendadorDeLembretes.TOLERANCIA_MINUTOS)) {
                    return // passou 1h do horário: não toca mais nem reagenda, até a semana que vem
                }
                ServicoDoAlarme.tocar(contexto, medicamentoId, nome, horario, AgendadorDeLembretes.TIPO_ATRASO)
                val remedio = PlanoLocal.ler(contexto).firstOrNull { it.id == medicamentoId } ?: return
                AgendadorDeLembretes.agendarAtraso(
                    contexto, remedio, ZonedDateTime.now().plusMinutes(AgendadorDeLembretes.INTERVALO_DE_REPETICAO_MINUTOS), previstoEpoch,
                )
            }
        }
    }
}

/** "Já tomei" (da notificação ou da tela do alarme): marca na hora no aparelho e registra no servidor assim que houver internet. */
object TomeiNoAparelho {
    fun registrar(contexto: Context, medicamentoId: Int) {
        Confirmacoes.marcar(contexto, medicamentoId)
        Notificador.cancelar(contexto, medicamentoId)
        AgendadorDeLembretes.cancelarAtraso(contexto, medicamentoId)
        ServicoDoAlarme.parar(medicamentoId)
        WorkManager.getInstance(contexto).enqueue(
            OneTimeWorkRequestBuilder<TomadaWorker>()
                .setInputData(workDataOf(EXTRA_MEDICAMENTO to medicamentoId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        )
    }
}

/** O botão "Já tomei" da notificação. */
class TomeiReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val medicamentoId = intent.getIntExtra(EXTRA_MEDICAMENTO, -1)
        if (medicamentoId < 0) return
        TomeiNoAparelho.registrar(contexto, medicamentoId)
    }
}

/** Registra a tomada no servidor; se não houver internet, o Android tenta de novo mais tarde. */
class TomadaWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val medicamentoId = inputData.getInt(EXTRA_MEDICAMENTO, -1)
        if (medicamentoId < 0) return Result.failure()
        val repositorio = (applicationContext as CuidaMedApp).repositorio
        if (!repositorio.temSessao()) return Result.success()
        repositorio.restaurarSessaoLocal()
        // Entra na fila do aparelho (vale mesmo sem internet) e sobe assim que possível. Se já estava registrado, tudo bem.
        repositorio.registrarTomada(medicamentoId)
        return Result.success()
    }
}

/** Depois de reiniciar o celular ou de atualizar o app, marca os alarmes de novo. */
class ReinicioReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        AgendadorDeLembretes.restaurar(contexto)
    }
}
