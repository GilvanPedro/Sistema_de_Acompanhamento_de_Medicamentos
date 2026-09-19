package br.com.cuidamed.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import br.com.cuidamed.ui.componentes.TextoSuave
import kotlinx.coroutines.delay
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.cuidamed.data.Repositorio
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.ui.componentes.Carregando
import br.com.cuidamed.ui.componentes.ErroComTentarDeNovo
import kotlinx.coroutines.launch

val LocalRepositorio = compositionLocalOf<Repositorio> { error("Repositório não fornecido") }

sealed interface Estado<out T> {
    data object EmAndamento : Estado<Nothing>
    data class Pronto<T>(val valor: T) : Estado<T>
    data class Erro(val mensagem: String) : Estado<Nothing>
}

/** Busca dados da API e guarda o resultado (sobrevive a girar a tela). */
class CarregadorViewModel<T>(
    private val buscar: suspend () -> Resultado<T>,
    private val agora: () -> Long = System::nanoTime,
    /** O que já está guardado no aparelho: aparece na hora, enquanto o servidor responde (ou se não houver internet). */
    private val buscarLocal: (suspend () -> T?)? = null,
) : ViewModel() {

    private var ultimoInicio = agora()


    var estado: Estado<T> by mutableStateOf(Estado.EmAndamento)
        private set

    /** Quando os dados foram atualizados com sucesso pela última vez (para mostrar "Atualizado às 13:10"). */
    var atualizadoEm: java.time.LocalTime? by mutableStateOf(null)
        private set

    var atualizando: Boolean by mutableStateOf(false)
        private set

    /** Relê só o que está no aparelho (sem servidor): chamado quando algo mudou localmente, como um remédio salvo. */
    fun carregarLocal() {
        val local = buscarLocal ?: return
        viewModelScope.launch {
            local.invoke()?.let { estado = Estado.Pronto(it) }
        }
    }

    /**
     * Aplica já uma mudança conhecida (um remédio que acabou de ser salvo ou excluído) sobre o que está na tela, sem esperar
     * o servidor. O carregamento normal que vem em seguida confirma e corrige, se preciso.
     */
    fun atualizarLocalmente(mudanca: (T) -> T) {
        val atual = estado
        if (atual is Estado.Pronto) estado = Estado.Pronto(mudanca(atual.valor))
    }

    fun segundosDesdeOUltimoCarregamento(): Long = (agora() - ultimoInicio) / 1_000_000_000L

    init {
        carregar()
    }

    /** [silencioso]: atualiza sem trocar a tela por "carregando", e mantém o que já estava se falhar. */
    fun carregar(silencioso: Boolean = false) {
        ultimoInicio = agora()
        if (!silencioso && buscarLocal == null) estado = Estado.EmAndamento
        atualizando = true
        viewModelScope.launch {
            if (!silencioso && buscarLocal != null) {
                // mostra já o que está no aparelho; só cai em "carregando" se não houver nada guardado ainda
                val local = buscarLocal.invoke()
                estado = if (local != null) Estado.Pronto(local) else Estado.EmAndamento
            }
            when (val r = buscar()) {
                is Resultado.Ok -> {
                    estado = Estado.Pronto(r.valor)
                    atualizadoEm = java.time.LocalTime.now()
                }
                is Resultado.Falha -> if (!silencioso || estado !is Estado.Pronto) estado = Estado.Erro(r.mensagem)
            }
            atualizando = false
        }
    }
}

/**
 * A tela voltou a ser a que está à frente (por exemplo, ao voltar do formulário de remédio): atualiza os dados, para o que
 * foi salvo lá aparecer na hora. Não repete se acabou de carregar (a própria abertura da tela também "retoma").
 */
fun CarregadorViewModel<*>.aoRetomar() {
    if (segundosDesdeOUltimoCarregamento() >= INTERVALO_MINIMO_AO_RETOMAR_S) carregar(silencioso = true)
}

private const val INTERVALO_MINIMO_AO_RETOMAR_S = 2

/** Um carregador por tela (identificado por [chave]) que também se atualiza sozinho ao voltar para a tela. */
@Composable
fun <T> rememberCarregador(
    chave: String,
    atualizarACadaSegundos: Int = 0,
    buscarLocal: (suspend () -> T?)? = null,
    buscar: suspend () -> Resultado<T>,
): CarregadorViewModel<T> {
    val carregador = viewModel<CarregadorViewModel<T>>(
        key = chave,
        factory = viewModelFactory { initializer { CarregadorViewModel(buscar, buscarLocal = buscarLocal) } },
    )
    // Algo mudou no aparelho (remédio salvo, tomada marcada, envio concluído): a tela relê o que está guardado, na hora.
    val conexoes by LocalRepositorio.current.conexoesRestabelecidas.collectAsStateWithLifecycle()
    var conexoesVistas by remember { mutableStateOf(conexoes) }
    LaunchedEffect(conexoes) {
        // a internet voltou: busca de novo no servidor, sem trocar a tela por "carregando"
        if (conexoes != conexoesVistas) {
            conexoesVistas = conexoes
            carregador.carregar(silencioso = true)
        }
    }
    val mudancas by LocalRepositorio.current.mudancasLocais.collectAsStateWithLifecycle()
    var mudancasVistas by remember { mutableStateOf(mudancas) }
    LaunchedEffect(mudancas) {
        if (mudancas != mudancasVistas) {
            mudancasVistas = mudancas
            carregador.carregarLocal()
        }
    }
    // Ao voltar para esta tela (ex.: depois de salvar um remédio no formulário), busca de novo. O controle de "acabou de
    // carregar" fica no carregador, e não na tela: a tela é recriada toda vez que reaparece, e o carregador continua o mesmo.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { carregador.aoRetomar() }
    // Telas com dados que mudam sozinhos (avisos de horário) se atualizam enquanto estão na tela e o app está aberto.
    if (atualizarACadaSegundos > 0) {
        val dono = LocalLifecycleOwner.current
        LaunchedEffect(carregador, dono) {
            dono.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(atualizarACadaSegundos * 1000L)
                    carregador.carregar(silencioso = true)
                }
            }
        }
    }
    return carregador
}

/** "Atualizado às 13:10" e um botão para atualizar na hora. */
@Composable
fun BarraDeAtualizacao(carregador: CarregadorViewModel<*>) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        val hora = carregador.atualizadoEm?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        TextoSuave(
            when {
                carregador.atualizando -> "Atualizando…"
                hora != null -> "Atualizado às $hora"
                else -> ""
            }
        )
        androidx.compose.material3.TextButton(
            onClick = { carregador.carregar(silencioso = true) },
            enabled = !carregador.atualizando,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { androidx.compose.material3.Text("Atualizar", style = androidx.compose.material3.MaterialTheme.typography.labelLarge) }
    }
}

/** Mostra "carregando", o erro (com botão de tentar de novo) ou o conteúdo pronto. */
@Composable
fun <T> Carregado(carregador: CarregadorViewModel<T>, conteudo: @Composable (T) -> Unit) {
    when (val e = carregador.estado) {
        Estado.EmAndamento -> Carregando()
        is Estado.Erro -> ErroComTentarDeNovo(e.mensagem) { carregador.carregar() }
        is Estado.Pronto -> conteudo(e.valor)
    }
}
