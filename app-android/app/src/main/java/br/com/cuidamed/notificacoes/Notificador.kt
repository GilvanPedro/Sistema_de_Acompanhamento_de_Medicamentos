package br.com.cuidamed.notificacoes

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.cuidamed.MainActivity
import br.com.cuidamed.R

/**
 * Monta e mostra as notificações. O nome do remédio só aparece com o celular desbloqueado: na tela de bloqueio
 * a notificação mostra um texto genérico (dado de saúde não deve ficar à vista de qualquer um).
 */
object Notificador {

    private const val ESCOPO_IDS_LEMBRETE = 1
    private const val ESCOPO_IDS_ATRASO = 2
    private const val ESCOPO_IDS_ALARME = 3

    fun idDoLembrete(medicamentoId: Int) = medicamentoId * 10 + ESCOPO_IDS_LEMBRETE
    fun idDoAtraso(medicamentoId: Int) = medicamentoId * 10 + ESCOPO_IDS_ATRASO
    fun idDoAlarme(medicamentoId: Int) = medicamentoId * 10 + ESCOPO_IDS_ALARME

    fun lembrete(contexto: Context, medicamentoId: Int, nome: String, horario: String) = mostrar(
        contexto, idDoLembrete(medicamentoId), Canais.LEMBRETES,
        titulo = "Hora de tomar o remédio", texto = "Está na hora de tomar $nome ($horario).",
        textoPublico = "Está na hora de tomar um remédio.", medicamentoParaTomei = medicamentoId,
    )

    fun atraso(contexto: Context, medicamentoId: Int, nome: String, horario: String) = mostrar(
        contexto, idDoAtraso(medicamentoId), Canais.LEMBRETES,
        titulo = "Você ainda não tomou o remédio", texto = "Você ainda não tomou o remédio $nome das $horario.",
        textoPublico = "Você ainda não tomou um remédio.", medicamentoParaTomei = medicamentoId,
    )

    fun avisoDeFamiliar(contexto: Context, chave: String, titulo: String, texto: String) = mostrar(
        contexto, idPorChave(chave), Canais.AVISOS,
        titulo = titulo, texto = texto, textoPublico = "Há um aviso sobre alguém que você acompanha.",
    )

    fun pedidoDeVinculo(contexto: Context, chave: String, nomeDoFamiliar: String) = mostrar(
        contexto, idPorChave(chave), Canais.PEDIDOS,
        titulo = "Novo pedido", texto = "$nomeDoFamiliar quer acompanhar você. Abra o app para aceitar ou recusar.",
        textoPublico = "Alguém quer acompanhar você.",
    )

    /** Tira as notificações de lembrete e de atraso de um remédio (por exemplo, depois de "Já tomei"). */
    fun cancelar(contexto: Context, medicamentoId: Int) {
        val gerente = NotificationManagerCompat.from(contexto)
        gerente.cancel(idDoLembrete(medicamentoId))
        gerente.cancel(idDoAtraso(medicamentoId))
        gerente.cancel(idDoAlarme(medicamentoId))
    }

    fun estaoLigadas(contexto: Context): Boolean = NotificationManagerCompat.from(contexto).areNotificationsEnabled()

    private fun idPorChave(chave: String) = 1_000_000 + (chave.hashCode() and 0x7fffffff) % 1_000_000

    @SuppressLint("MissingPermission") // areNotificationsEnabled() já considera a permissão POST_NOTIFICATIONS
    private fun mostrar(
        contexto: Context,
        id: Int,
        canal: String,
        titulo: String,
        texto: String,
        textoPublico: String,
        medicamentoParaTomei: Int? = null,
    ) {
        if (!estaoLigadas(contexto)) return

        val versaoPublica = NotificationCompat.Builder(contexto, canal)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle("CuidaMed")
            .setContentText(textoPublico)
            .build()

        val abrirApp = PendingIntent.getActivity(
            contexto, 0,
            Intent(contexto, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notificacao = NotificationCompat.Builder(contexto, canal)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(versaoPublica)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(abrirApp)
            .apply {
                if (medicamentoParaTomei != null) {
                    setCategory(NotificationCompat.CATEGORY_REMINDER)
                    val tomei = PendingIntent.getBroadcast(
                        contexto, medicamentoParaTomei * 10 + 5,
                        Intent(contexto, TomeiReceiver::class.java).putExtra(EXTRA_MEDICAMENTO, medicamentoParaTomei),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    addAction(0, "Já tomei", tomei)
                }
            }
            .build()

        NotificationManagerCompat.from(contexto).notify(id, notificacao)
    }
}

const val EXTRA_MEDICAMENTO = "medicamentoId"
