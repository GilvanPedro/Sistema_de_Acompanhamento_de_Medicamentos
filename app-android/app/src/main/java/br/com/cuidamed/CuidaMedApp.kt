package br.com.cuidamed

import android.app.Application
import br.com.cuidamed.data.CatalogoDeAnuncios
import br.com.cuidamed.data.EventosDeAnunciosRequest
import br.com.cuidamed.data.GuardaDeContagens
import br.com.cuidamed.data.MetricasDeAnuncios
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.data.chamarVazio
import br.com.cuidamed.data.CofreDeTokens
import br.com.cuidamed.data.Preferencias
import br.com.cuidamed.data.UltimaSessao
import br.com.cuidamed.data.local.ArmazenamentoLocal
import br.com.cuidamed.data.local.CifraKeystore
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import br.com.cuidamed.data.Repositorio
import br.com.cuidamed.data.criarApi
import br.com.cuidamed.notificacoes.AparelhoFirebase
import br.com.cuidamed.notificacoes.Canais
import br.com.cuidamed.notificacoes.EfeitosDoAplicativo

/** Guarda as peças compartilhadas do app (cofre de tokens, API, repositório e aparência) enquanto ele está aberto. */
class CuidaMedApp : Application() {

    lateinit var repositorio: Repositorio
        private set
    lateinit var preferencias: Preferencias
        private set
    lateinit var anuncios: CatalogoDeAnuncios
        private set
    lateinit var metricasDeAnuncios: MetricasDeAnuncios
        private set

    override fun onCreate() {
        super.onCreate()
        val cofre = CofreDeTokens(this)
        preferencias = Preferencias(this)
        anuncios = CatalogoDeAnuncios(this)
        Canais.criar(this)
        // O renovador precisa avisar o repositório quando a sessão acaba, e o repositório precisa da API: por isso o atraso.
        lateinit var repo: Repositorio
        val api = criarApi(cofre) { repo.sessaoPerdida() }
        val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val armazenamento = ArmazenamentoLocal(File(filesDir, "dados"), CifraKeystore())
        val sessaoGuardada = object : UltimaSessao {
            private val prefs = getSharedPreferences("ultima_sessao", MODE_PRIVATE)
            override fun lerId(): Int? = if (prefs.contains("id")) prefs.getInt("id", -1) else null
            override fun salvarId(id: Int?) {
                prefs.edit().apply { if (id == null) remove("id") else putInt("id", id) }.apply()
            }
        }
        repo = Repositorio(api, cofre, EfeitosDoAplicativo(this), armazenamento, sessaoGuardada, escopo, aparelho = AparelhoFirebase(this))
        repositorio = repo
        metricasDeAnuncios = MetricasDeAnuncios(
            guarda = object : GuardaDeContagens {
                private val prefs = getSharedPreferences("metricas_de_anuncios", MODE_PRIVATE)
                override fun ler(): String? = prefs.getString("pendentes", null)
                override fun salvar(texto: String) = prefs.edit().putString("pendentes", texto).apply()
            },
            enviar = { eventos -> chamarVazio { api.enviarEventosDeAnuncios(EventosDeAnunciosRequest(eventos)) } is Resultado.Ok },
            escopo = escopo,
        )

        // A internet voltou (com o app aberto): manda na hora o que ficou na fila.
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                escopo.launch { repo.aoVoltarAInternet() }
            }
        })
    }
}
