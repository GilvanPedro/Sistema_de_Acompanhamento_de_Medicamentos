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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
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
import br.com.cuidamed.ui.componentes.Cartao
import br.com.cuidamed.ui.componentes.EstiloDoBotao
import br.com.cuidamed.ui.componentes.Tom
import br.com.cuidamed.ui.theme.CuidaMedTheme

const val EXTRA_TIPO_DO_ALARME = "tipo"
const val EXTRA_NOME_DO_ALARME = "nome"
const val EXTRA_HORARIO_DO_ALARME = "horario"

/**
 * Os alarmes tocando agora — pode ser mais de um, se dois remédios coincidem no horário, ou se um segundo alarme
 * (de outro remédio, ou o aviso de atraso de um terceiro) começa antes do primeiro ser resolvido. É a única fonte da
 * verdade: a tela do alarme e a janela flutuante leem esta lista direto (é observável pelo Compose), então as duas
 * sempre mostram exatamente o que está tocando, com os botões certos — mesmo quando a lista muda enquanto a tela já
 * está aberta (o que antes deixava um botão "preso" ao remédio errado).
 */
object EstadoDoAlarme {
    data class Item(val medicamentoId: Int, val nome: String, val horario: String, val atraso: Boolean)

    val itens = mutableStateListOf<Item>()

    /** Adiciona (ou atualiza, se já existir) o alarme deste remédio. */
    fun definir(item: Item) {
        val indice = itens.indexOfFirst { it.medicamentoId == item.medicamentoId }
        if (indice >= 0) itens[indice] = item else itens.add(item)
    }

    /** Tira da lista; devolve true se ele estava lá. */
    fun remover(medicamentoId: Int): Boolean = itens.removeAll { it.medicamentoId == medicamentoId }

    fun limpar() = itens.clear()
}

/**
 * O alarme do remédio: toca som de alarme (que segue tocando com o celular no silencioso, como o despertador) e vibra até
 * a pessoa tocar em "Já tomei" ou em "Parar", ou até passar um minuto. Quem só olha o relógio de vez em quando não
 * perde o horário como perderia uma notificação que toca uma vez.
 *
 * Roda como serviço em primeiro plano para o Android não o encerrar enquanto toca. Fica no mesmo processo do app, então
 * os outros pontos (botão "Já tomei", tela do alarme) falam com ele diretamente por [parar].
 */
class ServicoDoAlarme : Service() {

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
            val vazio = EstadoDoAlarme.Item(0, "", "", tipo == AgendadorDeLembretes.TIPO_ATRASO)
            iniciarEmPrimeiroPlano(Notificador.idDoAlarme(0), notificacao(vazio, tocando = false))
            encerrar()
            return START_NOT_STICKY
        }
        val item = EstadoDoAlarme.Item(id, nome, horario, tipo == AgendadorDeLembretes.TIPO_ATRASO)
        EstadoDoAlarme.definir(item)
        val idDaNotificacao = Notificador.idDoAlarme(id)
        val notificacao = notificacao(item, tocando = true)
        if (idEmPrimeiroPlano == null) iniciarEmPrimeiroPlano(idDaNotificacao, notificacao) else notificar(idDaNotificacao, notificacao)

        segurarACpu()
        tocarSomEVibrar()
        handler.removeCallbacks(aoAcabarOTempo)
        handler.postDelayed(aoAcabarOTempo, DURACAO_MS)
        abrirATelaDoAlarme()
        mostrarSobreposicao()
        return START_NOT_STICKY
    }

    /**
     * Com a permissão "Exibir sobre outros apps" ligada, desenha a tela do alarme por cima de qualquer app — é o que
     * funciona quando o celular está desbloqueado e o CuidaMed só está minimizado (ver [SobreposicaoDoAlarme]).
     * Sem a permissão, não faz nada: o alarme continua tocando e vibrando do mesmo jeito. Chamar de novo com a janela
     * já aberta não faz nada (o conteúdo dela já acompanha [EstadoDoAlarme.itens] sozinho).
     */
    private fun mostrarSobreposicao() {
        SobreposicaoDoAlarme.mostrar(
            this,
            aoTomar = { id -> TomeiNoAparelho.registrar(applicationContext, id) },
            // "Parar" cala todo mundo que estiver tocando agora, não só quem apareceu primeiro na tela
            aoParar = { pararTudo() },
        )
    }

    /** A pessoa tratou este remédio (tomou ou mandou parar): tira a notificação e, se não sobrou alarme, cala tudo. */
    private fun silenciar(medicamentoId: Int) {
        if (!EstadoDoAlarme.remover(medicamentoId)) return
        NotificationManagerCompat.from(this).cancel(Notificador.idDoAlarme(medicamentoId))
        if (EstadoDoAlarme.itens.isEmpty()) {
            encerrar()
        } else if (idEmPrimeiroPlano == Notificador.idDoAlarme(medicamentoId)) {
            // a notificação que segurava o serviço saiu, mas ainda há alarme tocando: passa a segurar com a de outro remédio
            val proximo = EstadoDoAlarme.itens.first()
            iniciarEmPrimeiroPlano(Notificador.idDoAlarme(proximo.medicamentoId), notificacao(proximo, tocando = true))
        }
    }

    /** Passou o tempo sem resposta: o som para, as notificações ficam (e o aviso de atraso vem depois). */
    private fun silenciarPorTempo() {
        val restantes = EstadoDoAlarme.itens.toList()
        EstadoDoAlarme.limpar()
        pararSomEVibracao()
        SobreposicaoDoAlarme.remover(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        idEmPrimeiroPlano = null
        restantes.forEach { notificar(Notificador.idDoAlarme(it.medicamentoId), notificacao(it, tocando = false)) }
        stopSelf()
    }

    private fun silenciarTudo() {
        EstadoDoAlarme.itens.map { it.medicamentoId }.forEach { silenciar(it) }
        if (EstadoDoAlarme.itens.isEmpty()) encerrar()
    }

    private fun encerrar() {
        handler.removeCallbacks(aoAcabarOTempo)
        pararSomEVibracao()
        EstadoDoAlarme.limpar()
        SobreposicaoDoAlarme.remover(this)
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

    /** Não carrega mais dados de um remédio específico: a tela lê todos os alarmes ativos direto de [EstadoDoAlarme]. */
    private fun intentDaTela() = Intent(this, AlarmeActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Com o app à frente, o Android deixa abrir a tela na hora; em segundo plano ele recusa, e vale a notificação de tela cheia. */
    private fun abrirATelaDoAlarme() {
        try {
            startActivity(intentDaTela())
        } catch (e: Exception) {
            // bloqueado pelo sistema: a notificação de tela cheia (ou a janela flutuante) cuida
        }
    }

    private fun notificacao(item: EstadoDoAlarme.Item, tocando: Boolean): android.app.Notification {
        val titulo = if (item.atraso) "Você ainda não tomou o remédio" else "Hora de tomar o remédio"
        val texto = if (item.atraso) "Você ainda não tomou o remédio ${item.nome} das ${item.horario}." else "Está na hora de tomar ${item.nome} (${item.horario})."
        val versaoPublica = NotificationCompat.Builder(this, Canais.ALARMES)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle("CuidaMed")
            .setContentText(if (item.atraso) "Você ainda não tomou um remédio." else "Está na hora de tomar um remédio.")
            .build()

        val telaCheia = PendingIntent.getActivity(
            this, Notificador.idDoAlarme(item.medicamentoId), intentDaTela(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val abrirApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val tomei = PendingIntent.getBroadcast(
            this, item.medicamentoId * 10 + 5,
            Intent(this, TomeiReceiver::class.java).putExtra(EXTRA_MEDICAMENTO, item.medicamentoId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val parar = PendingIntent.getBroadcast(
            this, item.medicamentoId * 10 + 6,
            Intent(this, PararAlarmeReceiver::class.java).putExtra(EXTRA_MEDICAMENTO, item.medicamentoId),
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
        /** Quanto tempo o alarme toca sem resposta. Depois disso, ele silencia; o próximo aviso de atraso vem em até 10 minutos. */
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

        /** Cala o alarme deste remédio (se estiver tocando). Os outros, se houver, continuam. */
        fun parar(medicamentoId: Int) {
            instancia?.handler?.post { instancia?.silenciar(medicamentoId) }
        }

        /** Cala todos os alarmes tocando agora de uma vez (o botão "Parar o alarme" quando há mais de um remédio). */
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
 * A tela que aparece por cima da tela de bloqueio quando o alarme toca: letras enormes e só os botões necessários.
 * O conteúdo vem de [EstadoDoAlarme.itens] — se dois remédios tocam juntos, aparecem os dois nesta mesma tela.
 */
class AlarmeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val app = application as CuidaMedApp
        setContent {
            CuidaMedTheme(escuro = app.preferencias.escuro, escalaDaLetra = app.preferencias.escala) {
                // Tudo resolvido (ou o alarme acabou sozinho): fecha a tela sozinha, sem precisar de um botão para isso.
                LaunchedEffect(EstadoDoAlarme.itens.size) {
                    if (EstadoDoAlarme.itens.isEmpty()) finish()
                }
                ConteudoDoAlarme(
                    itens = EstadoDoAlarme.itens,
                    aoTomar = { id -> TomeiNoAparelho.registrar(applicationContext, id) },
                    aoParar = { ServicoDoAlarme.pararTudo() },
                )
            }
        }
    }
}

/**
 * O conteúdo da tela do alarme: usado tanto na [AlarmeActivity] (quando o celular está bloqueado, ou o app já está
 * à frente) quanto na janela flutuante de [SobreposicaoDoAlarme] (quando o app só está minimizado, com a tela ligada).
 * Com um remédio só, mostra ele em destaque; com mais de um tocando junto, todos numa lista, cada um com o seu
 * "Já tomei", e um "Parar o alarme" só, que cala o som para todos de uma vez.
 */
@Composable
fun ConteudoDoAlarme(itens: List<EstadoDoAlarme.Item>, aoTomar: (Int) -> Unit, aoParar: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (itens.isEmpty()) return@Surface
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (itens.size == 1) {
                val item = itens.first()
                Text(
                    if (item.atraso) "Você ainda não tomou o remédio" else "Hora de tomar o remédio",
                    style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Text(item.nome, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Text(
                    if (item.atraso) "das ${item.horario}" else "às ${item.horario}",
                    style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(36.dp))
                BotaoGrande("Já tomei", { aoTomar(item.medicamentoId) }, estilo = EstiloDoBotao.SUCESSO)
                Spacer(Modifier.height(14.dp))
                BotaoGrande("Parar o alarme", aoParar, estilo = EstiloDoBotao.SECUNDARIO)
                if (!item.atraso) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Se você não marcar que tomou, o alarme avisa de novo a cada 10 minutos, por até 1 hora. Depois disso, ele não insiste mais hoje, mas você ainda pode marcar como tomado a qualquer hora do dia.",
                        style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    "Você tem ${itens.size} remédios para tomar agora",
                    style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                itens.forEach { item ->
                    Cartao(if (item.atraso) Tom.ERRO else Tom.AVISO) {
                        Text(item.nome, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (item.atraso) "Ainda não tomou · das ${item.horario}" else "às ${item.horario}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(10.dp))
                        BotaoGrande("Já tomei", { aoTomar(item.medicamentoId) }, Modifier.fillMaxWidth(), EstiloDoBotao.SUCESSO)
                    }
                    Spacer(Modifier.height(14.dp))
                }
                BotaoGrande("Parar o alarme", aoParar, estilo = EstiloDoBotao.SECUNDARIO)
            }
        }
    }
}
