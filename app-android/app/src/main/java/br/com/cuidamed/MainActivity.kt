package br.com.cuidamed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.cuidamed.data.EstadoDaSessao
import br.com.cuidamed.data.Preferencias
import br.com.cuidamed.data.Repositorio
import br.com.cuidamed.notificacoes.PedirPermissaoDeNotificacoes
import br.com.cuidamed.notificacoes.RegistroDePush
import br.com.cuidamed.ui.LocalRepositorio
import br.com.cuidamed.ui.NavegacaoDeEntrada
import br.com.cuidamed.ui.NavegacaoDoApp
import br.com.cuidamed.ui.componentes.BotaoGrande
import br.com.cuidamed.ui.componentes.Carregando
import br.com.cuidamed.ui.componentes.ErroComTentarDeNovo
import br.com.cuidamed.ui.componentes.EstiloDoBotao
import br.com.cuidamed.ui.componentes.LocalAnuncios
import br.com.cuidamed.ui.componentes.LocalPreferencias
import br.com.cuidamed.ui.componentes.Tela
import br.com.cuidamed.ui.telas.PortaoDaPolitica
import br.com.cuidamed.ui.theme.CuidaMedTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CuidaMedApp
        setContent {
            CuidaMedRaiz(app, app.repositorio, app.preferencias)
        }
    }
}

/** Escolhe a tela pelo estado do login: verificando, sem login, logado ou sem conexão. */
@Composable
private fun CuidaMedRaiz(app: CuidaMedApp, repositorio: Repositorio, preferencias: Preferencias) {
    val estado by repositorio.sessao.collectAsStateWithLifecycle()
    val escopo = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        launch { repositorio.acordarServidor() }
        repositorio.restaurarSessao()
    }

    CuidaMedTheme(escuro = preferencias.escuro, escalaDaLetra = preferencias.escala) {
        CompositionLocalProvider(
            LocalPreferencias provides preferencias,
            LocalRepositorio provides repositorio,
            LocalAnuncios provides app.anuncios,
        ) {
            when (val e = estado) {
                EstadoDaSessao.Verificando -> Tela("CuidaMed") { Carregando("Entrando…") }
                EstadoDaSessao.SemLogin -> NavegacaoDeEntrada()
                is EstadoDaSessao.Logado -> {
                    // Sempre que o app abre com alguém logado, confere se o servidor tem o código de push atual.
                    LaunchedEffect(e.usuario.id) { RegistroDePush.garantir(app, forcar = true) }
                    PortaoDaPolitica(e.usuario) {
                        PedirPermissaoDeNotificacoes()
                        NavegacaoDoApp(e.usuario)
                    }
                }
                is EstadoDaSessao.SemConexao -> Tela("CuidaMed") {
                    ErroComTentarDeNovo(e.mensagem) { escopo.launch { repositorio.restaurarSessao() } }
                    BotaoGrande("Entrar com outra conta", { escopo.launch { repositorio.sair() } }, estilo = EstiloDoBotao.SECUNDARIO)
                }
            }
        }
    }
}
