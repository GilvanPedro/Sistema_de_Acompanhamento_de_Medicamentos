package br.com.cuidamed.ui.componentes

import android.content.Context
import android.net.ConnectivityManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import java.io.File

/**
 * O vídeo só toca se não gastar dados móveis do usuário e se ele não desligou as animações do Android. Em qualquer outro
 * caso o banner fica só com a imagem (que sempre existe).
 */
fun videoPermitido(contexto: Context): Boolean {
    val naRedeMedida = contexto.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
    val comAnimacoes = Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    return !naRedeMedida && comAnimacoes
}

/** Guarda os vídeos já vistos no aparelho (até 40 MB), para não baixar de novo a cada repetição ou visita. */
@UnstableApi
object CacheDeVideos {
    private var cache: SimpleCache? = null

    @Synchronized
    fun fabrica(contexto: Context): DataSource.Factory {
        val guardado = cache ?: SimpleCache(
            File(contexto.applicationContext.cacheDir, "videos"),
            LeastRecentlyUsedCacheEvictor(40L * 1024 * 1024),
            StandaloneDatabaseProvider(contexto.applicationContext),
        ).also { cache = it }
        return CacheDataSource.Factory()
            .setCache(guardado)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(contexto.applicationContext))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}

/**
 * O vídeo do banner: toca **sem som**, em repetição, e **sem pedir o foco de áudio** (quem está ouvindo música ou
 * uma ligação não é interrompido). Só toca enquanto [tocando] for verdadeiro (banner visível). Fica transparente até o
 * primeiro quadro aparecer (ou se der erro), deixando a imagem à mostra por baixo.
 */
@UnstableApi
@Composable
fun VideoDoBanner(url: String, tocando: Boolean, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    var comPrimeiroQuadro by remember(url) { mutableStateOf(false) }
    val player = remember(url) {
        ExoPlayer.Builder(contexto)
            .setMediaSourceFactory(DefaultMediaSourceFactory(CacheDeVideos.fabrica(contexto)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ false,
            )
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(url))
                prepare()
            }
    }
    DisposableEffect(player) {
        val ouvinte = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                comPrimeiroQuadro = true
            }

            override fun onPlayerError(error: PlaybackException) {
                comPrimeiroQuadro = false // deu erro (sem rede, arquivo ruim...): a imagem continua à mostra
            }
        }
        player.addListener(ouvinte)
        onDispose {
            player.removeListener(ouvinte)
            player.release()
        }
    }
    LaunchedEffect(tocando, player) { player.playWhenReady = tocando }
    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW, // TextureView: acompanha a rolagem da tela sem falhas
        modifier = modifier.alpha(if (comPrimeiroQuadro) 1f else 0f),
    )
}
