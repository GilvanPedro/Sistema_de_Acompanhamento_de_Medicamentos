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
const val URL_DO_CATALOGO_ESTATICO = "https://sistema-de-acompanhamento-de-medicamentos.onrender.com/anuncios/anuncios.json"

/**
 * A lista que o app usa de preferência: a mesma dos banners, mas com o peso de cada um calculado pelo servidor
 * (rodízio justo: quem está abaixo da sua parte de exibições ganha mais chance). Se falhar, o app cai no arquivo
 * estático acima (sorteio simples) e, por último, na cópia guardada no aparelho.
 */
const val URL_DOS_ANUNCIOS = "https://sistema-de-acompanhamento-de-medicamentos.onrender.com/api/v1/anuncios"

/**
 * Um banner: a imagem, para onde ele leva ao ser tocado (opcional) e um texto para leitores de tela.
 * [peso] é a chance relativa de ser sorteado (1 = normal; 0 = pausado); o servidor o ajusta para igualar as exibições.
 */
@Serializable
data class Anuncio(
    val id: String,
    val imagem: String,
    val link: String? = null,
    val texto: String = "Anúncio",
    val peso: Double = 1.0,
    /** Vídeo curto opcional (MP4): toca sem som no lugar da imagem, quando pode. A imagem continua sendo o plano B. */
    val video: String? = null,
)

@Serializable
private data class ArquivoDeAnuncios(val anuncios: List<Anuncio> = emptyList())

/** Endereço completo da imagem (relativo ao catálogo ou absoluto). Só aceita https. */
fun enderecoDaImagem(urlDoCatalogo: String, imagem: String): String? = runCatching {
    val url = URI(urlDoCatalogo).resolve(imagem.trim())
    url.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() }?.toString()
}.getOrNull()

private val LINK_DE_EMAIL = Regex(
    "^mailto:[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(\\?(subject|body)=[^&\\s]*(&(subject|body)=[^&\\s]*)?)?$",
)

/**
 * O link do anúncio só vale se for https ou um e-mail (mailto: só com destinatário, assunto e mensagem). Nada de outros
 * esquemas (tel:, intent:, file:...) nem de campos de e-mail que possam anexar arquivos ou copiar terceiros.
 */
fun linkSeguro(link: String?): String? = link?.trim()?.takeIf {
    (it.startsWith("https://") && runCatching { URI(it).host }.getOrNull()?.isNotBlank() == true) || LINK_DE_EMAIL.matches(it)
}

/** Lê o JSON do catálogo; texto inválido vira lista vazia (nunca derruba o app). */
fun lerCatalogo(texto: String): List<Anuncio> =
    runCatching { jsonDaApi.decodeFromString<ArquivoDeAnuncios>(texto).anuncios }.getOrDefault(emptyList())

/** Sorteia um banner com chance proporcional ao peso. Peso zero, negativo ou inválido tira o banner do sorteio. */
fun List<Anuncio>.escolher(sorteio: Random = Random.Default): Anuncio? {
    if (isEmpty()) return null
    val pesos = map { if (it.peso.isFinite() && it.peso > 0) it.peso else 0.0 }
    val total = pesos.sum()
    if (total <= 0) return random(sorteio) // nenhum peso válido: todos valem igual, em vez de não mostrar nada
    var restante = sorteio.nextDouble() * total
    forEachIndexed { i, anuncio ->
        restante -= pesos[i]
        if (restante < 0) return anuncio
    }
    return last { it.peso.isFinite() && it.peso > 0 }
}

/** Põe o endereço completo (https) em cada imagem e vídeo, relativo ao catálogo de onde a lista veio, e tira as inválidas. */
fun normalizar(urlDoCatalogo: String, anuncios: List<Anuncio>): List<Anuncio> =
    anuncios.mapNotNull { a ->
        enderecoDaImagem(urlDoCatalogo, a.imagem)?.let { imagem ->
            // vídeo com endereço inválido (ou sem https) só perde o vídeo: o banner continua, com a imagem
            a.copy(imagem = imagem, video = a.video?.let { enderecoDaImagem(urlDoCatalogo, it) })
        }
    }

/**
 * Catálogo de banners: baixa a lista do servidor, guarda uma cópia (para aparecer também sem internet) e carrega as
 * imagens com cache em memória e em disco. Usa um cliente HTTP próprio, sem o token de login: nada da conta da pessoa
 * vai junto nesses pedidos.
 */
class CatalogoDeAnuncios(
    private val contexto: Context,
    private val urlsDoCatalogo: List<String> = listOf(URL_DOS_ANUNCIOS, URL_DO_CATALOGO_ESTATICO),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build(),
) {
    private companion object {
        const val LIMITE_DA_IMAGEM = 3L * 1024 * 1024
        const val LARGURA_MAXIMA = 1280
        const val VALIDADE_DA_IMAGEM_MS = 24L * 60 * 60 * 1000
        const val ESPERA_APOS_FALHA_MS = 60_000L
        /** A lista (e os pesos) são pedidos de novo depois disso, para o rodízio acompanhar o servidor. */
        const val VALIDADE_DA_LISTA_MS = 15L * 60 * 1000
    }

    private val prefs by lazy { contexto.getSharedPreferences("anuncios", Context.MODE_PRIVATE) }
    private val imagens = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(chave: String, valor: Bitmap) = valor.byteCount
    }
    private val trava = Mutex()
    @Volatile private var lista: List<Anuncio>? = null
    @Volatile private var listaObtidaEm = 0L
    @Volatile private var ultimaFalha = 0L

    /** Um banner ao acaso, ou null se não houver nenhum (sem internet e sem cópia guardada). */
    suspend fun escolher(sorteio: Random = Random.Default): Anuncio? = anuncios().escolher(sorteio)

    private suspend fun anuncios(): List<Anuncio> {
        lista?.let { if (System.currentTimeMillis() - listaObtidaEm < VALIDADE_DA_LISTA_MS) return it }
        return trava.withLock {
            val atual = lista
            if (atual != null && System.currentTimeMillis() - listaObtidaEm < VALIDADE_DA_LISTA_MS) return@withLock atual
            if (System.currentTimeMillis() - ultimaFalha < ESPERA_APOS_FALHA_MS) return@withLock atual.orEmpty()
            val nova = carregarLista()
            when {
                nova != null -> { lista = nova; listaObtidaEm = System.currentTimeMillis(); nova }
                atual != null -> { ultimaFalha = System.currentTimeMillis(); atual } // mantém a última boa e tenta de novo em 1 min
                else -> {
                    val guardada = lerDaCopia()
                    if (guardada.isEmpty()) ultimaFalha = System.currentTimeMillis()
                    else { lista = guardada; listaObtidaEm = System.currentTimeMillis() - VALIDADE_DA_LISTA_MS + ESPERA_APOS_FALHA_MS }
                    guardada
                }
            }
        }
    }

    /** Baixa a lista (primeiro a com pesos, depois a estática). null se nenhuma respondeu com algum banner. */
    private suspend fun carregarLista(): List<Anuncio>? = withContext(Dispatchers.IO) {
        for (url in urlsDoCatalogo) {
            val texto = runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body.string() else null }
            }.getOrNull() ?: continue
            val anuncios = normalizar(url, lerCatalogo(texto))
            if (anuncios.isNotEmpty()) {
                prefs.edit().putString("lista", jsonDaApi.encodeToString(ArquivoDeAnuncios(anuncios))).apply()
                return@withContext anuncios
            }
        }
        null
    }

    private fun lerDaCopia(): List<Anuncio> =
        prefs.getString("lista", null)?.let(::lerCatalogo).orEmpty().filter { enderecoDaImagem(urlsDoCatalogo.last(), it.imagem) != null }

    /** A imagem do banner (memória, depois disco, depois rede), ou null se não deu para obter. */
    suspend fun imagem(anuncio: Anuncio): Bitmap? = withContext(Dispatchers.IO) {
        val url = enderecoDaImagem(urlsDoCatalogo.last(), anuncio.imagem) ?: return@withContext null
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
