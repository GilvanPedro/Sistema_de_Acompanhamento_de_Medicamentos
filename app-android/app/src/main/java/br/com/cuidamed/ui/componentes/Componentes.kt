package br.com.cuidamed.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.cuidamed.data.Preferencias
import kotlinx.coroutines.delay

val LocalPreferencias = compositionLocalOf<Preferencias> { error("Preferências não fornecidas") }

/** Como uma mensagem aparece: verde (deu certo), âmbar (atenção), vermelho (erro) ou neutra. */
enum class Tom { OK, AVISO, ERRO, NEUTRO }

data class Mensagem(val texto: String, val tom: Tom)

@Composable
private fun coresDo(tom: Tom): Pair<Color, Color> = when (tom) {
    Tom.OK -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    Tom.AVISO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    Tom.ERRO -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    Tom.NEUTRO -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
}

/**
 * Estrutura padrão de uma tela: barra de aparência (letra e modo escuro), botão de voltar, título e conteúdo rolável.
 * O conteúdo respeita as barras do sistema (nada fica escondido embaixo delas).
 */
@Composable
fun Tela(
    titulo: String,
    subtitulo: String? = null,
    aoVoltar: (() -> Unit)? = null,
    topo: @Composable () -> Unit = {},
    conteudo: @Composable () -> Unit,
) {
    // Texto sem cor própria usa esta cor: sem isso ele sai preto (o padrão do Compose fora de um Surface) e some no modo escuro.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        BarraDeAparencia(aoVoltar)
        FaixaDeConexao()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            topo()
            Text(titulo, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            if (subtitulo != null) {
                Text(subtitulo, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            conteudo()
            Spacer(Modifier.height(24.dp))
        }
    }
    }
}

@Composable
private fun BarraDeAparencia(aoVoltar: (() -> Unit)?) {
    val preferencias = LocalPreferencias.current
    val sistemaEscuro = isSystemInDarkTheme()
    val escuroAgora = preferencias.escuro ?: sistemaEscuro
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (aoVoltar != null) {
            TextButton(onClick = aoVoltar, modifier = Modifier.heightIn(min = 52.dp)) {
                Text("← Voltar", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = { preferencias.diminuirLetra() },
            enabled = preferencias.nivelDaLetra > 0,
            modifier = Modifier.heightIn(min = 52.dp),
        ) { Text("A−", style = MaterialTheme.typography.labelLarge) }
        TextButton(
            onClick = { preferencias.aumentarLetra() },
            enabled = preferencias.nivelDaLetra < Preferencias.ESCALAS.lastIndex,
            modifier = Modifier.heightIn(min = 52.dp),
        ) { Text("A+", style = MaterialTheme.typography.labelLarge) }
        TextButton(onClick = { preferencias.alternarModoEscuro(sistemaEscuro) }, modifier = Modifier.heightIn(min = 52.dp)) {
            Text(if (escuroAgora) "Claro" else "Escuro", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

enum class EstiloDoBotao { PRIMARIO, SECUNDARIO, SUCESSO, PERIGO }

@Composable
fun BotaoGrande(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    estilo: EstiloDoBotao = EstiloDoBotao.PRIMARIO,
    habilitado: Boolean = true,
    carregando: Boolean = false,
) {
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = 64.dp)
    val conteudo: @Composable () -> Unit = {
        if (carregando) {
            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
        } else {
            Text(texto, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
    val ativo = habilitado && !carregando
    when (estilo) {
        EstiloDoBotao.PRIMARIO -> Button(onClick = aoClicar, modifier = base, enabled = ativo) { conteudo() }
        EstiloDoBotao.SUCESSO -> Button(
            onClick = aoClicar, modifier = base, enabled = ativo,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
        ) { conteudo() }
        EstiloDoBotao.PERIGO -> OutlinedButton(
            onClick = aoClicar, modifier = base, enabled = ativo,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { conteudo() }
        EstiloDoBotao.SECUNDARIO -> OutlinedButton(
            onClick = aoClicar, modifier = base, enabled = ativo,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        ) { conteudo() }
    }
}

@Composable
fun Cartao(tom: Tom = Tom.NEUTRO, conteudo: @Composable () -> Unit) {
    val (fundo, texto) = coresDo(tom)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = fundo, contentColor = texto),
        border = if (tom == Tom.NEUTRO) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)) else null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { conteudo() }
    }
}

/** Mensagem de resultado (sucesso, aviso ou erro) no topo da tela. Nunca depende só da cor: sempre tem texto. */
@Composable
fun AvisoDaTela(mensagem: Mensagem?) {
    if (mensagem != null) {
        Cartao(mensagem.tom) { Text(mensagem.texto, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun Secao(titulo: String) {
    Text(titulo, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun TextoSuave(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun CampoDeTexto(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    senha: Boolean = false,
    tipoDeTeclado: KeyboardType = KeyboardType.Text,
    dica: String? = null,
) {
    var mostrar by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(rotulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        OutlinedTextField(
            value = valor,
            onValueChange = aoMudar,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            visualTransformation = if (senha && !mostrar) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (senha) KeyboardType.Password else tipoDeTeclado),
        )
        if (senha) {
            TextButton(onClick = { mostrar = !mostrar }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (mostrar) "Esconder a senha" else "Mostrar a senha", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (dica != null) TextoSuave(dica)
    }
}

/** Botões de escolha (um só marcado), em linhas que quebram quando não cabem. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Escolhas(opcoes: List<Pair<String, String>>, selecionada: String?, aoEscolher: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opcoes.forEach { (chave, rotulo) ->
            val marcada = chave == selecionada
            // O texto é o mesmo marcado ou não: assim o botão não muda de largura e os outros não se mexem de lugar.
            // A marcação é o azul cheio (contra o contorno dos demais), e o leitor de tela anuncia "selecionado".
            val modificador = Modifier
                .heightIn(min = 56.dp)
                .semantics {
                    selected = marcada
                    role = Role.RadioButton
                }
            if (marcada) {
                Button(onClick = { aoEscolher(chave) }, modifier = modificador) {
                    Text(rotulo, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                OutlinedButton(onClick = { aoEscolher(chave) }, modifier = modificador) {
                    Text(rotulo, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * Vários botões que se marcam e desmarcam (dias da semana). O botão "Todos os dias" marca ou desmarca todos de uma vez.
 * A marcação é o azul cheio; o leitor de tela anuncia "marcado" (Checkbox).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EscolhasMultiplas(opcoes: List<Pair<String, String>>, marcadas: Set<String>, textoDeTodos: String, aoMudar: (Set<String>) -> Unit) {
    val todasMarcadas = opcoes.all { it.first in marcadas }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opcoes.forEach { (chave, rotulo) ->
            val marcada = chave in marcadas
            val modificador = Modifier
                .heightIn(min = 56.dp)
                .semantics {
                    selected = marcada
                    role = Role.Checkbox
                }
            val alternar = { aoMudar(if (marcada) marcadas - chave else marcadas + chave) }
            if (marcada) {
                Button(onClick = alternar, modifier = modificador) { Text(rotulo, style = MaterialTheme.typography.bodyLarge) }
            } else {
                OutlinedButton(onClick = alternar, modifier = modificador) { Text(rotulo, style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
    OutlinedButton(
        onClick = { aoMudar(if (todasMarcadas) emptySet() else opcoes.map { it.first }.toSet()) },
        modifier = Modifier
            .heightIn(min = 56.dp)
            .semantics { role = Role.Button },
    ) {
        Text(if (todasMarcadas) "Desmarcar todos" else textoDeTodos, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Número com botões − e + (hora e minutos sem digitar). */
@Composable
fun Contador(rotulo: String, valor: Int, minimo: Int, maximo: Int, passo: Int, aoMudar: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(rotulo, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { aoMudar(if (valor - passo < minimo) (maximo / passo) * passo else valor - passo) },
                modifier = Modifier.size(60.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("−", style = MaterialTheme.typography.headlineMedium) }
            Text(
                "%02d".format(valor),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.widthIn(min = 56.dp),
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = { aoMudar(if (valor + passo > maximo) minimo else valor + passo) },
                modifier = Modifier.size(60.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("+", style = MaterialTheme.typography.headlineMedium) }
        }
    }
}

@Composable
fun Carregando(texto: String = "Carregando…") {
    // O servidor gratuito hiberna: depois de alguns segundos, explica a demora em vez de parecer travado.
    var demorou by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(6_000)
        demorou = true
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(48.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge)
        if (demorou) {
            TextoSuave("O servidor estava descansando e está acordando. Isso pode levar até um minuto, é só esperar um pouco.")
        }
    }
}

@Composable
fun ErroComTentarDeNovo(mensagem: String, aoTentarDeNovo: () -> Unit) {
    Cartao(Tom.ERRO) {
        Text(mensagem, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        BotaoGrande("Tentar de novo", aoTentarDeNovo, estilo = EstiloDoBotao.SECUNDARIO)
    }
}

@Composable
fun Confirmacao(
    titulo: String,
    mensagem: String,
    textoSim: String,
    textoNao: String,
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
    perigoso: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo, style = MaterialTheme.typography.titleLarge) },
        text = { Text(mensagem, style = MaterialTheme.typography.bodyLarge) },
        // O botão de manter/cancelar fica em destaque: quem toca sem ler não perde nada.
        confirmButton = { Button(onClick = aoCancelar, modifier = Modifier.heightIn(min = 56.dp)) { Text(textoNao, style = MaterialTheme.typography.labelLarge) } },
        dismissButton = {
            TextButton(onClick = aoConfirmar, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(
                    textoSim,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (perigoso) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}
