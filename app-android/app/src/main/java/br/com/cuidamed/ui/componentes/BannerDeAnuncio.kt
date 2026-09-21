package br.com.cuidamed.ui.componentes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.cuidamed.data.EstadoDaSessao
import br.com.cuidamed.data.MetricasDeAnuncios
import br.com.cuidamed.data.TipoDeEvento
import br.com.cuidamed.ui.LocalRepositorio
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.cuidamed.data.Anuncio
import br.com.cuidamed.data.CatalogoDeAnuncios
import br.com.cuidamed.data.linkSeguro

/** De onde os banners vêm; sem isso (por exemplo, numa prévia) nenhum banner aparece. */
val LocalAnuncios = staticCompositionLocalOf<CatalogoDeAnuncios?> { null }

/** Quem conta exibições e toques dos banners; sem isso (numa prévia) nada é contado. */
val LocalMetricasDeAnuncios = staticCompositionLocalOf<MetricasDeAnuncios?> { null }

/** Uma exibição só vale com pelo menos metade do banner visível... */
private const val FRACAO_MINIMA_VISIVEL = 0.5f
/** ...por pelo menos um segundo (o padrão usado no mercado para anúncio de tela). */
private const val TEMPO_MINIMO_VISIVEL_MS = 1_000L

/**
 * Locais de banner que a pessoa já fechou. Fica só na memória: enquanto o app estiver aberto, um banner fechado não
 * volta naquele lugar; ao abrir o app de novo, volta a aparecer.
 */
object AnunciosFechados {
    private val locais = mutableSetOf<String>()

    @Synchronized
    fun fechado(local: String) = local in locais

    @Synchronized
    fun fechar(local: String) {
        locais += local
    }
}

/**
 * Um banner de anúncio, escolhido ao acaso do catálogo do servidor. Sempre traz o rótulo "Anúncio" e um botão grande
 * "Fechar". Se não houver banner (sem internet e sem cópia guardada), não ocupa espaço nenhum.
 * [local] identifica o lugar da tela ("home-topo", "home-fim"...), para o "Fechar" valer só para ele.
 */
@Composable
fun BannerDeAnuncio(local: String, modifier: Modifier = Modifier) {
    val catalogo = LocalAnuncios.current ?: return
    var fechado by remember(local) { mutableStateOf(AnunciosFechados.fechado(local)) }
    if (fechado) return

    val escolhido by produceState<Pair<Anuncio, ImageBitmap>?>(initialValue = null, catalogo) {
        val anuncio = catalogo.escolher()
        val imagem = anuncio?.let { catalogo.imagem(it) }
        value = if (anuncio != null && imagem != null) anuncio to imagem.asImageBitmap() else null
    }
    val (anuncio, imagem) = escolhido ?: return
    val contexto = LocalContext.current
    val link = linkSeguro(anuncio.link)

    // Contagem anônima para os relatórios dos anunciantes: exibição (metade visível por 1 s) e toque.
    val metricas = LocalMetricasDeAnuncios.current
    val sessao by LocalRepositorio.current.sessao.collectAsStateWithLifecycle()
    val perfil = (sessao as? EstadoDaSessao.Logado)?.usuario?.let { if (it.ehIdoso) "IDOSO" else "FAMILIAR" } ?: "VISITANTE"
    val janela = LocalWindowInfo.current.containerSize
    var fracaoVisivel by remember(anuncio.id) { mutableFloatStateOf(0f) }
    var exibicaoContada by remember(anuncio.id) { mutableStateOf(false) }
    val visivel = fracaoVisivel >= FRACAO_MINIMA_VISIVEL
    LaunchedEffect(visivel, exibicaoContada) {
        if (visivel && !exibicaoContada) {
            delay(TEMPO_MINIMO_VISIVEL_MS) // se sair da tela antes disso, este efeito é cancelado e não conta
            exibicaoContada = true
            metricas?.registrar(TipoDeEvento.EXIBICAO, anuncio.id, local, perfil)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Anúncio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        AnunciosFechados.fechar(local)
                        fechado = true
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Fechar anúncio" },
                ) { Text("Fechar ✕", style = MaterialTheme.typography.labelLarge) }
            }
            Image(
                bitmap = imagem,
                contentDescription = anuncio.texto,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imagem.width.toFloat() / imagem.height)
                    .onGloballyPositioned { fracaoVisivel = fracaoVisivel(it, janela) }
                    .let {
                        if (link != null) {
                            it.clickable {
                                metricas?.registrar(TipoDeEvento.CLIQUE, anuncio.id, local, perfil)
                                abrirLink(contexto, link)
                            }
                        } else it
                    },
            )
        }
    }
}

/** https abre no navegador; mailto: abre o app de e-mail já com o destinatário (e assunto) preenchidos. */
private fun abrirLink(contexto: Context, link: String) {
    val acao = if (link.startsWith("mailto:")) Intent.ACTION_SENDTO else Intent.ACTION_VIEW
    try {
        contexto.startActivity(Intent(acao, Uri.parse(link)))
    } catch (e: ActivityNotFoundException) {
        // sem navegador ou app de e-mail no aparelho: não há o que fazer
    }
}

/** Que parte (0 a 1) do banner está de fato visível na tela agora (fora da tela, ou cortado por rolagem, conta menos). */
private fun fracaoVisivel(coordenadas: LayoutCoordinates, janela: IntSize): Float {
    if (!coordenadas.isAttached || coordenadas.size.width == 0 || coordenadas.size.height == 0) return 0f
    val v = coordenadas.boundsInWindow()
    val esquerda = maxOf(v.left, 0f)
    val topo = maxOf(v.top, 0f)
    val direita = minOf(v.right, janela.width.toFloat())
    val base = minOf(v.bottom, janela.height.toFloat())
    if (direita <= esquerda || base <= topo) return 0f
    return ((direita - esquerda) * (base - topo)) / (coordenadas.size.width.toFloat() * coordenadas.size.height)
}
