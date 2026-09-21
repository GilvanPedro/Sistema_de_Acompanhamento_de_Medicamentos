package br.com.cuidamed.notificacoes

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import br.com.cuidamed.ui.componentes.BotaoGrande
import br.com.cuidamed.ui.componentes.Cartao
import br.com.cuidamed.ui.componentes.Tom

/** No Android 13 ou mais novo, é preciso pedir licença para mostrar notificações. Pede uma vez, logo depois de entrar. */
@Composable
fun PedirPermissaoDeNotificacoes() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val contexto = LocalContext.current
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pedir.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Cartão no topo das telas iniciais quando as notificações estão desligadas: sem elas os lembretes não aparecem. */
@Composable
fun AvisoDeNotificacoesDesligadas() {
    val contexto = LocalContext.current
    var ligadas by remember { mutableStateOf(Notificador.estaoLigadas(contexto)) }
    // Confere de novo ao voltar das configurações do Android.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ligadas = Notificador.estaoLigadas(contexto) }
    if (!ligadas) {
        Cartao(Tom.AVISO) {
            Text(
                "As notificações estão desligadas. Sem elas você não recebe os lembretes de remédio nem os avisos.",
                style = MaterialTheme.typography.bodyLarge,
            )
            BotaoGrande("Ligar as notificações", { abrirConfiguracoesDeNotificacao(contexto) })
        }
    }
}

/**
 * No Android 14 ou mais novo, abrir a tela do alarme por cima da tela de bloqueio depende de uma permissão que a pessoa
 * pode ter desligado. Sem ela o alarme ainda toca e a notificação aparece, mas a tela grande não abre sozinha.
 */
@Composable
fun AvisoDeAlarmeEmTelaCheia() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val contexto = LocalContext.current
    var permitido by remember { mutableStateOf(podeAbrirTelaCheia(contexto)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permitido = podeAbrirTelaCheia(contexto) }
    if (!permitido) {
        Cartao(Tom.AVISO) {
            Text(
                "Para o alarme do remédio aparecer na tela com o celular bloqueado, permita \"Notificações em tela cheia\" para o CuidaMed.",
                style = MaterialTheme.typography.bodyLarge,
            )
            BotaoGrande("Permitir", {
                contexto.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(android.net.Uri.parse("package:${contexto.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            })
        }
    }
}

private fun podeAbrirTelaCheia(contexto: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        contexto.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()

fun abrirConfiguracoesDeNotificacao(contexto: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    contexto.startActivity(intent)
}

/** Nem todo celular tem esta tela; se não tiver, abre as configurações gerais do app. */
fun abrirConfiguracoesDeBateria(contexto: Context) {
    try {
        contexto.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        contexto.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:${contexto.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
