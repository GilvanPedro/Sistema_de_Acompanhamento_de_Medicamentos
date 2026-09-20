package br.com.cuidamed.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Onde fica a lista de banners (um JSON no servidor). Para trocar os anúncios basta mudar esse arquivo e as imagens
 * ao lado dele no servidor: o app não precisa de atualização. Os endereços das imagens são relativos a este arquivo.
 */
const val URL_DOS_ANUNCIOS = "https://sistema-de-acompanhamento-de-medicamentos.onrender.com/anuncios/anuncios.json"

/** Um banner: a imagem, para onde ele leva ao ser tocado (opcional) e um texto para leitores de tela. */
@Serializable
data class Anuncio(val id: String, val imagem: String, val link: String? = null, val texto: String = "Anúncio")

@Serializable
private data class ArquivoDeAnuncios(val anuncios: List<Anuncio> = emptyList())

/** Endereço completo da imagem (relativo ao catálogo ou absoluto). Só aceita https. */
fun enderecoDaImagem(urlDoCatalogo: String, imagem: String): String? = runCatching {
    val url = URI(urlDoCatalogo).resolve(imagem.trim())
    url.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() }?.toString()
}.getOrNull()

/** O link do anúncio só vale se for https: nada de outros esquemas (tel:, intent:, file:...). */
fun linkSeguro(link: String?): String? = link?.trim()?.takeIf {
    it.startsWith("https://") && runCatching { URI(it).host }.getOrNull()?.isNotBlank() == true
}

/** Lê o JSON do catálogo; texto inválido vira lista vazia (nunca derruba o app). */
fun lerCatalogo(texto: String): List<Anuncio> =
    runCatching { jsonDaApi.decodeFromString<ArquivoDeAnuncios>(texto).anuncios }.getOrDefault(emptyList())

fun List<Anuncio>.escolher(sorteio: Random = Random.Default): Anuncio? = randomOrNull(sorteio)

/**
 * Catálogo de banners: baixa a lista do servidor, guarda uma cópia (para aparecer também sem internet) e carrega as
 * imagens com cache em memória e em disco. Usa um cliente HTTP próprio, sem o token de login: nada da conta da pessoa
 * vai junto nesses pedidos.
 */
class CatalogoDeAnuncios(
    private val contexto: Context,
    private val urlDoCatalogo: String = URL_DOS_ANUNCIOS,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build(),
) {
    private companion object {
        const val LIMITE_DA_IMAGEM = 3L * 1024 * 1024
        const val LARGURA_MAXIMA = 1280
        const val VALIDADE_DA_IMAGEM_MS = 24L * 60 * 60 * 1000
        const val ESPERA_APOS_FALHA_MS = 60_000L
    }

    private val prefs by lazy { contexto.getSharedPreferences("anuncios", Context.MODE_PRIVATE) }
    private val imagens = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(chave: String, valor: Bitmap) = valor.byteCount
    }
    private val trava = Mutex()
    @Volatile private var lista: List<Anuncio>? = null
    @Volatile private var ultimaFalha = 0L

    /** Um banner ao acaso, ou null se não houver nenhum (sem internet e sem cópia guardada). */
    suspend fun escolher(sorteio: Random = Random.Default): Anuncio? = anuncios().escolher(sorteio)

    private suspend fun anuncios(): List<Anuncio> {
        lista?.let { return it }
        return trava.withLock {
            lista ?: run {
                if (System.currentTimeMillis() - ultimaFalha < ESPERA_APOS_FALHA_MS) return@run emptyList()
                val carregada = carregarLista()
                if (carregada.isNotEmpty()) lista = carregada else ultimaFalha = System.currentTimeMillis()
                carregada
            }
        }
    }

    private suspend fun carregarLista(): List<Anuncio> = withContext(Dispatchers.IO) {
        val doServidor = runCatching {
            http.newCall(Request.Builder().url(urlDoCatalogo).build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body.string().also { prefs.edit().putString("lista", it).apply() }
            }
        }.getOrNull()
        (doServidor ?: prefs.getString("lista", null))?.let(::lerCatalogo).orEmpty()
            .filter { enderecoDaImagem(urlDoCatalogo, it.imagem) != null }
    }

    /** A imagem do banner (memória, depois disco, depois rede), ou null se não deu para obter. */
    suspend fun imagem(anuncio: Anuncio): Bitmap? = withContext(Dispatchers.IO) {
        val url = enderecoDaImagem(urlDoCatalogo, anuncio.imagem) ?: return@withContext null
        imagens.get(url)?.let { return@withContext it }
        val arquivo = File(contexto.cacheDir, "anuncios/${Integer.toHexString(url.hashCode())}.img")
        val velho = arquivo.exists() && System.currentTimeMillis() - arquivo.lastModified() > VALIDADE_DA_IMAGEM_MS
        val bytes = when {
            arquivo.exists() && !velho -> arquivo.readBytes()
            else -> baixar(url)?.also { arquivo.parentFile?.mkdirs(); arquivo.writeBytes(it) }
                ?: arquivo.takeIf { it.exists() }?.readBytes() // sem rede: a cópia velha ainda serve
        }
        bytes?.let(::decodificar)?.also { imagens.put(url, it) }
    }

    private fun baixar(url: String): ByteArray? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val corpo = r.body
            if (!r.isSuccessful || corpo.contentLength() > LIMITE_DA_IMAGEM) return@use null
            corpo.bytes().takeIf { it.size <= LIMITE_DA_IMAGEM }
        }
    }.getOrNull()

    /** Decodifica reduzindo imagens muito grandes, para um banner não gastar memória à toa. */
    private fun decodificar(bytes: ByteArray): Bitmap? {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null
        var reducao = 1
        while (medidas.outWidth / (reducao * 2) >= LARGURA_MAXIMA) reducao *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = reducao })
    }
}
