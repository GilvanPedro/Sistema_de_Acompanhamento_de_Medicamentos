package br.com.cuidamed.ui.telas

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.cuidamed.data.INTRODUCAO_DA_POLITICA
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.data.SECOES_DA_POLITICA
import br.com.cuidamed.data.UsuarioDto
import br.com.cuidamed.data.VERSAO_DA_POLITICA
import br.com.cuidamed.data.VIGENCIA_DA_POLITICA
import br.com.cuidamed.ui.Destinos
import br.com.cuidamed.ui.LocalRepositorio
import br.com.cuidamed.ui.componentes.AvisoDaTela
import br.com.cuidamed.ui.componentes.BotaoGrande
import br.com.cuidamed.ui.componentes.Carregando
import br.com.cuidamed.ui.componentes.EstiloDoBotao
import br.com.cuidamed.ui.componentes.Mensagem
import br.com.cuidamed.ui.componentes.Secao
import br.com.cuidamed.ui.componentes.Tela
import br.com.cuidamed.ui.componentes.Tom
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** O texto inteiro da política (o mesmo da página do servidor). */
@Composable
private fun TextoDaPolitica() {
    Text(INTRODUCAO_DA_POLITICA, style = MaterialTheme.typography.bodyLarge)
    SECOES_DA_POLITICA.forEach { (titulo, paragrafos) ->
        Secao(titulo)
        paragrafos.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
fun TelaPolitica(destinos: Destinos) {
    Tela("Política de privacidade", VIGENCIA_DA_POLITICA, destinos::voltar) {
        TextoDaPolitica()
    }
}

/** "Li e aceito" no cadastro; o link abre o texto completo. */
@Composable
fun CaixaDeAceite(marcado: Boolean, aoMudar: (Boolean) -> Unit, aoLer: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = marcado, onCheckedChange = aoMudar)
        Text("Li e aceito as políticas de privacidade", style = MaterialTheme.typography.bodyLarge)
    }
    BotaoGrande("Ler a política de privacidade", aoLer, estilo = EstiloDoBotao.SECUNDARIO)
}

/** Para quem já tinha conta antes da política (ou quando o texto mudar): o app só segue depois do aceite. */
@Composable
private fun TelaAceiteDaPolitica(aoAceitar: suspend () -> Resultado<Unit>, aoSair: () -> Unit) {
    val escopo = rememberCoroutineScope()
    var mensagem by remember { mutableStateOf<Mensagem?>(null) }
    var enviando by remember { mutableStateOf(false) }
    Tela("Antes de continuar", "Leia a política de privacidade do CuidaMed e, se estiver de acordo, aceite.") {
        AvisoDaTela(mensagem)
        TextoDaPolitica()
        BotaoGrande("Li e aceito", {
            enviando = true
            escopo.launch {
                val r = aoAceitar()
                if (r is Resultado.Falha) mensagem = Mensagem(r.mensagem, Tom.ERRO)
                enviando = false
            }
        }, carregando = enviando)
        BotaoGrande("Não aceito: sair da conta", aoSair, estilo = EstiloDoBotao.SECUNDARIO)
    }
}

/**
 * Deixa o app abrir só se a conta já aceitou a versão atual da política. Guarda no aparelho que já conferiu, para não
 * perguntar ao servidor toda vez; sem internet (ou servidor lento) o app abre normalmente e confere na próxima.
 */
@Composable
fun PortaoDaPolitica(usuario: UsuarioDto, conteudo: @Composable () -> Unit) {
    val repo = LocalRepositorio.current
    val escopo = rememberCoroutineScope()
    val prefs = LocalContext.current.getSharedPreferences("politica", Context.MODE_PRIVATE)
    val chave = "aceita_${usuario.id}"
    var liberado by remember(usuario.id) { mutableStateOf(prefs.getString(chave, null) == VERSAO_DA_POLITICA) }
    var precisaAceitar by remember(usuario.id) { mutableStateOf(false) }

    fun liberar() {
        prefs.edit().putString(chave, VERSAO_DA_POLITICA).apply()
        liberado = true
    }

    LaunchedEffect(usuario.id) {
        if (liberado) return@LaunchedEffect
        when (val r = withTimeoutOrNull(8_000) { repo.consentimento() }) {
            is Resultado.Ok -> if (r.valor.versaoAceita == VERSAO_DA_POLITICA) liberar() else precisaAceitar = true
            else -> liberado = true // sem internet ou servidor lento: não trava o app
        }
    }

    when {
        liberado -> conteudo()
        precisaAceitar -> TelaAceiteDaPolitica(
            aoAceitar = { repo.aceitarPolitica().also { if (it is Resultado.Ok) liberar() } },
            aoSair = { escopo.launch { repo.sair() } },
        )
        else -> Tela("CuidaMed") { Carregando("Entrando…") }
    }
}
