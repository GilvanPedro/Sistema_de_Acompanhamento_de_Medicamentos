package br.com.cuidamed.notificacoes

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.com.cuidamed.CuidaMedApp
import br.com.cuidamed.MainActivity
import br.com.cuidamed.R
import br.com.cuidamed.ui.componentes.BotaoGrande
import br.com.cuidamed.ui.componentes.EstiloDoBotao
import br.com.cuidamed.ui.theme.CuidaMedTheme

const val EXTRA_TIPO_DO_ALARME = "tipo"
const val EXTRA_NOME_DO_ALARME = "nome"
const val EXTRA_HORARIO_DO_ALARME = "horario"

/**
 * O alarme do remédio: toca som de alarme (que segue tocando com o celular no silencioso, como o despertador) e vibra até
 * a pessoa tocar em "Já tomei" ou em "Parar", ou até passar um minuto. Quem só olha o relógio de vez em quando não
 * perde o horário como perderia uma notificação que toca uma vez.
 *
 * Roda como serviço em primeiro plano para o Android não o encerrar enquanto toca. Fica no mesmo processo do app, então
 * os outros pontos (botão "Já tomei", tela do alarme) falam com ele diretamente por [parar].
 */
class ServicoDoAlarme : Service() {

    private class Ativo(val medicamentoId: Int, val nome: String, val horario: String, val tipo: String)

    private val ativos = LinkedHashMap<Int, Ativo>()
    private val handler = Handler(Looper.getMainLooper())
    private val aoAcabarOTempo = Runnable { silenciarPorTempo() }
    private var tocador: MediaPlayer? = null
    private var toque: Ringtone? = null
    private var travaDeCpu: PowerManager.WakeLock? = null
    private var idEmPrimeiroPlano: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instancia = this
    }

    override fun onDestroy() {
        instancia = null
        handler.removeCallbacks(aoAcabarOTempo)
        pararSomEVibracao()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(EXTRA_MEDICAMENTO, -1) ?: -1
        val nome = intent?.getStringExtra(EXTRA_NOME_DO_ALARME).orEmpty()
        val horario = intent?.getStringExtra(EXTRA_HORARIO_DO_ALARME).orEmpty()
        val tipo = intent?.getStringExtra(EXTRA_TIPO_DO_ALARME) ?: AgendadorDeLembretes.TIPO_LEMBRETE
        if (id < 0) {
            // chamado sem dados (não deveria acontecer): quem inicia serviço em primeiro plano precisa mostrar algo antes de sair
            iniciarEmPrimeiroPlano(Notificador.idDoAlarme(0), notificacao(Ativo(0, "", "", tipo), tocando = false))
            encerrar()
            return START_NOT_STICKY
        }
        val ativo = Ativo(id, nome, horario, tipo)
        ativos[id] = ativo
        val idDaNotificacao = Notificador.idDoAlarme(id)
        val notificacao = notificacao(ativo, tocando = true)
        if (idEmPrimeiroPlano == null) iniciarEmPrimeiroPlano(idDaNotificacao, notificacao) else notificar(idDaNotificacao, notificacao)

        segurarACpu()
        tocarSomEVibrar()
        handler.removeCallbacks(aoAcabarOTempo)
        handler.postDelayed(aoAcabarOTempo, DURACAO_MS)
        abrirATelaDoAlarme(ativo)
        return START_NOT_STICKY
    }

    /** A pessoa tratou este remédio (tomou ou mandou parar): tira a notificação e, se não sobrou alarme, cala tudo. */
    private fun silenciar(medicamentoId: Int) {
        if (ativos.remove(medicamentoId) == null) return
        NotificationManagerCompat.from(this).cancel(Notificador.idDoAlarme(medicamentoId))
        if (ativos.isEmpty()) {
            encerrar()
        } else if (idEmPrimeiroPlano == Notificador.idDoAlarme(medicamentoId)) {
            // a notificação que segurava o serviço saiu, mas ainda há alarme tocando: passa a segurar com a de outro remédio
            val proximo = ativos.values.first()
            iniciarEmPrimeiroPlano(Notificador.idDoAlarme(proximo.medicamentoId), notificacao(proximo, tocando = true))
        }
    }

    /** Passou o tempo sem resposta: o som para, as notificações ficam (e o aviso de atraso vem depois). */
    private fun silenciarPorTempo() {
        val restantes = ativos.values.toList()
        ativos.clear()
        pararSomEVibracao()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        idEmPrimeiroPlano = null
        restantes.forEach { notificar(Notificador.idDoAlarme(it.medicamentoId), notificacao(it, tocando = false)) }
        stopSelf()
    }

    private fun silenciarTudo() {
        ativos.keys.toList().forEach { silenciar(it) }
        if (ativos.isEmpty()) encerrar()
    }

    private fun encerrar() {
        handler.removeCallbacks(aoAcabarOTempo)
        pararSomEVibracao()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        idEmPrimeiroPlano = null
        stopSelf()
    }

    private fun iniciarEmPrimeiroPlano(id: Int, notificacao: android.app.Notification) {
        ServiceCompat.startForeground(this, id, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        idEmPrimeiroPlano = id
    }

    @android.annotation.SuppressLint("MissingPermission") // sem a permissão de notificações o serviço ainda toca; só não há o que mostrar
    private fun notificar(id: Int, notificacao: android.app.Notification) {
        if (Notificador.estaoLigadas(this)) NotificationManagerCompat.from(this).notify(id, notificacao)
    }

    private fun intentDaTela(ativo: Ativo) = Intent(this, AlarmeActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(EXTRA_MEDICAMENTO, ativo.medicamentoId)
        .putExtra(EXTRA_NOME_DO_ALARME, ativo.nome)
        .putExtra(EXTRA_HORARIO_DO_ALARME, ativo.horario)
        .putExtra(EXTRA_TIPO_DO_ALARME, ativo.tipo)

    /** Com o app à frente, o Android deixa abrir a tela na hora; em segundo plano ele recusa, e vale a notificação de tela cheia. */
    private fun abrirATelaDoAlarme(ativo: Ativo) {
        try {
            startActivity(intentDaTela(ativo))
        } catch (e: Exception) {
            // bloqueado pelo sistema: a notificação de tela cheia cuida
        }
    }

    private fun notificacao(ativo: Ativo, tocando: Boolean): android.app.Notification {
        val atraso = ativo.tipo == AgendadorDeLembretes.TIPO_ATRASO
        val titulo = if (atraso) "Você ainda não tomou o remédio" else "Hora de tomar o remédio"
        val texto = if (atraso) "Você ainda não tomou o remédio ${ativo.nome} das ${ativo.horario}." else "Está na hora de tomar ${ativo.nome} (${ativo.horario})."
        val versaoPublica = NotificationCompat.Builder(this, Canais.ALARMES)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle("CuidaMed")
            .setContentText(if (atraso) "Você ainda não tomou um remédio." else "Está na hora de tomar um remédio.")
            .build()

        val telaCheia = PendingIntent.getActivity(
            this, Notificador.idDoAlarme(ativo.medicamentoId), intentDaTela(ativo),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val abrirApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val tomei = PendingIntent.getBroadcast(
            this, ativo.medicamentoId * 10 + 5,
            Intent(this, TomeiReceiver::class.java).putExtra(EXTRA_MEDICAMENTO, ativo.medicamentoId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val parar = PendingIntent.getBroadcast(
            this, ativo.medicamentoId * 10 + 6,
            Intent(this, PararAlarmeReceiver::class.java).putExtra(EXTRA_MEDICAMENTO, ativo.medicamentoId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, Canais.ALARMES)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(versaoPublica)
            .setContentIntent(if (tocando) telaCheia else abrirApp)
            .addAction(0, "Já tomei", tomei)
            .apply {
                if (tocando) {
                    setOngoing(true)
                    setFullScreenIntent(telaCheia, true)
                    addAction(0, "Parar o alarme", parar)
                } else {
                    setAutoCancel(true)
                }
            }
            .build()
    }

    private fun segurarACpu() {
        if (travaDeCpu?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        travaDeCpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cuidamed:alarme").apply { acquire(DURACAO_MS + 5_000) }
    }

    /** O alarme toca no volume máximo do canal de alarme; o volume que a pessoa tinha volta quando o alarme acaba. */
    private fun volumeNoMaximo() {
        val audio = getSystemService(AudioManager::class.java)
        val prefs = getSharedPreferences(PREFS_DO_VOLUME, Context.MODE_PRIVATE)
        // já guardado = outro alarme (ou um alarme interrompido) já subiu o volume: não guarda o volume alto como se fosse o original
        if (!prefs.contains(CHAVE_VOLUME_ORIGINAL)) {
            prefs.edit().putInt(CHAVE_VOLUME_ORIGINAL, audio.getStreamVolume(AudioManager.STREAM_ALARM)).apply()
        }
        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (e: SecurityException) {
            // o aparelho não deixou mexer no volume: toca no que estiver
        }
    }

    private fun restaurarVolume() {
        val prefs = getSharedPreferences(PREFS_DO_VOLUME, Context.MODE_PRIVATE)
        if (!prefs.contains(CHAVE_VOLUME_ORIGINAL)) return
        val original = prefs.getInt(CHAVE_VOLUME_ORIGINAL, -1)
        prefs.edit().remove(CHAVE_VOLUME_ORIGINAL).apply()
        if (original < 0) return
        try {
            getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
        } catch (e: SecurityException) {
            // sem permissão para mexer no volume: fica como está
        }
    }

    private fun tocarSomEVibrar() {
        volumeNoMaximo()
        val atributos = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (tocador == null && toque == null) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            try {
                tocador = MediaPlayer().apply {
                    setAudioAttributes(atributos)
                    setDataSource(this@ServicoDoAlarme, uri)
                    setVolume(1f, 1f)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                tocador?.release()
                tocador = null
                toque = RingtoneManager.getRingtone(this, uri)?.apply {
                    audioAttributes = atributos
                    isLooping = true
                    play()
                }
            }
        }
        @Suppress("DEPRECATION")
        val vibrador = ContextCompat.getSystemService(this, Vibrator::class.java)
        @Suppress("DEPRECATION")
        vibrador?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 700), 0), atributos)
    }

    private fun pararSomEVibracao() {
        try {
            tocador?.stop()
        } catch (e: IllegalStateException) {
            // já estava parado
        }
        tocador?.release()
        tocador = null
        toque?.stop()
        toque = null
        ContextCompat.getSystemService(this, Vibrator::class.java)?.cancel()
        restaurarVolume()
        if (travaDeCpu?.isHeld == true) travaDeCpu?.release()
        travaDeCpu = null
    }

    companion object {
        /** Quanto tempo o alarme toca sem resposta. Depois disso vem o aviso de atraso, 10 minutos após o horário. */
        const val DURACAO_MS = 60_000L

        private const val PREFS_DO_VOLUME = "alarme_volume"
        private const val CHAVE_VOLUME_ORIGINAL = "original"

        @Volatile
        private var instancia: ServicoDoAlarme? = null

        fun tocar(contexto: Context, medicamentoId: Int, nome: String, horario: String, tipo: String) {
            val intent = Intent(contexto, ServicoDoAlarme::class.java)
                .putExtra(EXTRA_MEDICAMENTO, medicamentoId)
                .putExtra(EXTRA_NOME_DO_ALARME, nome)
                .putExtra(EXTRA_HORARIO_DO_ALARME, horario)
                .putExtra(EXTRA_TIPO_DO_ALARME, tipo)
            ContextCompat.startForegroundService(contexto, intent)
        }

        /** Cala o alarme deste remédio (se estiver tocando). */
        fun parar(medicamentoId: Int) {
            instancia?.handler?.post { instancia?.silenciar(medicamentoId) }
        }

        fun pararTudo() {
            instancia?.handler?.post { instancia?.silenciarTudo() }
        }
    }
}

/** O botão "Parar o alarme" da notificação. */
class PararAlarmeReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val medicamentoId = intent.getIntExtra(EXTRA_MEDICAMENTO, -1)
        if (medicamentoId >= 0) ServicoDoAlarme.parar(medicamentoId)
    }
}

/**
 * A tela que aparece por cima da tela de bloqueio quando o alarme toca: letras enormes, o nome do remédio e só dois botões.
 */
class AlarmeActivity : ComponentActivity() {

    private var medicamentoId by mutableStateOf(-1)
    private var nome by mutableStateOf("")
    private var horario by mutableStateOf("")
    private var atraso by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ler(intent)
        val app = application as CuidaMedApp
        setContent {
            CuidaMedTheme(escuro = app.preferencias.escuro, escalaDaLetra = app.preferencias.escala) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (atraso) "Você ainda não tomou o remédio" else "Hora de tomar o remédio",
                            style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(nome, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                        Text(
                            if (atraso) "das $horario" else "às $horario",
                            style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(36.dp))
                        BotaoGrande("Já tomei", {
                            TomeiNoAparelho.registrar(applicationContext, medicamentoId)
                            finish()
                        }, estilo = EstiloDoBotao.SUCESSO)
                        Spacer(Modifier.height(14.dp))
                        BotaoGrande("Parar o alarme", {
                            ServicoDoAlarme.parar(medicamentoId)
                            finish()
                        }, estilo = EstiloDoBotao.SECUNDARIO)
                        if (!atraso) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Se você não marcar que tomou, o alarme avisa de novo daqui a ${AgendadorDeLembretes.TOLERANCIA_MINUTOS} minutos.",
                                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ler(intent)
    }

    private fun ler(intent: android.content.Intent) {
        medicamentoId = intent.getIntExtra(EXTRA_MEDICAMENTO, -1)
        nome = intent.getStringExtra(EXTRA_NOME_DO_ALARME).orEmpty()
        horario = intent.getStringExtra(EXTRA_HORARIO_DO_ALARME).orEmpty()
        atraso = intent.getStringExtra(EXTRA_TIPO_DO_ALARME) == AgendadorDeLembretes.TIPO_ATRASO
    }
}
