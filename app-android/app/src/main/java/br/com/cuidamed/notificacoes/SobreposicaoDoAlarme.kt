package br.com.cuidamed.notificacoes

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import br.com.cuidamed.CuidaMedApp
import br.com.cuidamed.ui.theme.CuidaMedTheme

/**
 * Com o celular desbloqueado e o app só minimizado (não em primeiro plano), o Android não deixa mais abrir uma tela
 * do app por cima do que a pessoa está usando — só sobre a tela de bloqueio (ver ADR sobre o alarme). A saída, usada
 * por apps de despertador e de chamada, é desenhar a própria tela do alarme como uma **janela flutuante**, por cima
 * de qualquer app: isso não depende de abrir uma Activity, só da permissão "Exibir sobre outros apps".
 *
 * Como uma janela do WindowManager não tem o ciclo de vida de uma Activity, este objeto monta um dono de ciclo de
 * vida mínimo só para o Compose funcionar dentro dela (é a receita padrão para "Compose fora de uma Activity").
 */
object SobreposicaoDoAlarme {

    /** Se a pessoa já ligou a permissão. Sem ela, o alarme ainda toca e vibra normalmente — só a janela não aparece. */
    fun permitido(contexto: Context): Boolean = Settings.canDrawOverlays(contexto)

    private class DonoDoCicloDeVida : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registroDoCiclo = LifecycleRegistry(this)
        private val controladorDeEstado = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registroDoCiclo
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry get() = controladorDeEstado.savedStateRegistry

        fun iniciar() {
            controladorDeEstado.performRestore(null)
            registroDoCiclo.currentState = Lifecycle.State.RESUMED
        }

        fun destruir() {
            registroDoCiclo.currentState = Lifecycle.State.DESTROYED
        }
    }

    private var view: ComposeView? = null
    private var dono: DonoDoCicloDeVida? = null

    /**
     * Mostra a janela do alarme, se ainda não estiver na tela (chamar de novo com ela já aberta não faz nada: o
     * conteúdo lê [EstadoDoAlarme.itens] direto, então já acompanha sozinho quantos remédios estão tocando).
     * Silencioso se faltar a permissão.
     */
    fun mostrar(contexto: Context, aoTomar: (Int) -> Unit, aoParar: () -> Unit) {
        if (view != null || !permitido(contexto)) return

        val aplicativo = contexto.applicationContext as CuidaMedApp
        val donoNovo = DonoDoCicloDeVida().apply { iniciar() }
        val janela = ComposeView(contexto).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(donoNovo)
            setViewTreeViewModelStoreOwner(donoNovo)
            setViewTreeSavedStateRegistryOwner(donoNovo)
            setContent {
                CuidaMedTheme(escuro = aplicativo.preferencias.escuro, escalaDaLetra = aplicativo.preferencias.escala) {
                    ConteudoDoAlarme(itens = EstadoDoAlarme.itens, aoTomar = aoTomar, aoParar = aoParar)
                }
            }
        }
        val gerenteDeJanelas = ContextCompat.getSystemService(contexto, WindowManager::class.java) ?: return
        val parametros = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        )
        try {
            gerenteDeJanelas.addView(janela, parametros)
            view = janela
            dono = donoNovo
        } catch (e: Exception) {
            // sem permissão de verdade, ou recusado pelo fabricante: o alarme continua tocando e vibrando do mesmo jeito
            donoNovo.destruir()
        }
    }

    /** Tira a janela da tela (chamado sempre que o alarme daquele remédio é silenciado, de qualquer jeito). */
    fun remover(contexto: Context) {
        val janela = view ?: return
        view = null
        val gerenteDeJanelas = ContextCompat.getSystemService(contexto, WindowManager::class.java)
        try {
            gerenteDeJanelas?.removeView(janela)
        } catch (e: Exception) {
            // já não estava mais na tela
        }
        dono?.destruir()
        dono = null
    }
}
