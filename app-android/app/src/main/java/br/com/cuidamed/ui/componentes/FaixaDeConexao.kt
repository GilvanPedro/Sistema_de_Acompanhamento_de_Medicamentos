package br.com.cuidamed.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.cuidamed.ui.LocalRepositorio
import kotlinx.coroutines.launch

/**
 * Faixa no topo das telas sobre a conexão e as alterações feitas sem internet:
 * o que está aguardando envio, e o que o servidor recusou (com o motivo).
 */
@Composable
fun FaixaDeConexao() {
    val repositorio = LocalRepositorio.current
    val estado by repositorio.sincronizacao.collectAsStateWithLifecycle()
    val escopo = rememberCoroutineScope()
    if (!estado.semConexao && estado.pendentes == 0 && estado.conflitos.isEmpty() && !estado.enviando) return

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (estado.conflitos.isNotEmpty()) {
            Cartao(Tom.ERRO) {
                Text("Algumas alterações não foram aplicadas:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                estado.conflitos.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                BotaoGrande("Entendi", { escopo.launch { repositorio.dispensarConflitos() } })
            }
        }
        if (estado.pendentes > 0) {
            val n = estado.pendentes
            val texto = when {
                estado.enviando -> "Enviando…"
                estado.semConexao ->
                    if (n == 1) "Sem conexão. 1 alteração será enviada quando a internet voltar."
                    else "Sem conexão. $n alterações serão enviadas quando a internet voltar."
                else -> if (n == 1) "1 alteração aguardando envio." else "$n alterações aguardando envio."
            }
            // Compacta: o texto e um botão pequeno na mesma linha, para a faixa não tomar a tela de um celular pequeno.
            Cartao(Tom.AVISO) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(texto, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (!estado.enviando) {
                        TextButton(onClick = { escopo.launch { repositorio.enviarPendencias() } }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Enviar agora", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        } else if (estado.semConexao) {
            Cartao(Tom.AVISO) {
                Text(
                    "Sem conexão. Você pode continuar usando o app; o que fizer será enviado quando a internet voltar.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
