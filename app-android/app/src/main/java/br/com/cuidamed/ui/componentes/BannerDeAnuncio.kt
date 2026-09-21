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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.cuidamed.data.Anuncio
import br.com.cuidamed.data.CatalogoDeAnuncios
import br.com.cuidamed.data.linkSeguro

/** De onde os banners vêm; sem isso (por exemplo, numa prévia) nenhum banner aparece. */
val LocalAnuncios = staticCompositionLocalOf<CatalogoDeAnuncios?> { null }

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
                    .let { if (link != null) it.clickable { abrirLink(contexto, link) } else it },
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
