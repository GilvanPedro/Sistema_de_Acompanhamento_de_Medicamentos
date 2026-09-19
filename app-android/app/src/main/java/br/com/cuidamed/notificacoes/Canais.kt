package br.com.cuidamed.notificacoes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Canais de notificação (o usuário pode ajustar cada um nas configurações do Android). */
object Canais {
    const val LEMBRETES = "lembretes"
    const val AVISOS = "avisos"
    const val PEDIDOS = "pedidos"

    fun criar(contexto: Context) {
        val gerente = contexto.getSystemService(NotificationManager::class.java)
        gerente.createNotificationChannels(
            listOf(
                NotificationChannel(LEMBRETES, "Lembretes de remédio", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Avisa na hora de tomar cada remédio."
                    enableVibration(true)
                },
                NotificationChannel(AVISOS, "Avisos sobre quem você acompanha", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Avisa quando alguém que você acompanha tomou ou esqueceu um remédio."
                },
                NotificationChannel(PEDIDOS, "Pedidos de vínculo", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Avisa quando um familiar pede para acompanhar você."
                },
            )
        )
    }
}
