package br.com.cuidamed.ui.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.cuidamed.data.PedidoVinculoDto
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.data.UsuarioDto
import br.com.cuidamed.ui.Carregado
import br.com.cuidamed.ui.CarregadorViewModel
import br.com.cuidamed.ui.Destinos
import br.com.cuidamed.ui.LocalRepositorio
import br.com.cuidamed.ui.componentes.AvisoDaTela
import br.com.cuidamed.ui.componentes.BotaoGrande
import br.com.cuidamed.ui.componentes.CampoDeTexto
import br.com.cuidamed.ui.componentes.Cartao
import br.com.cuidamed.ui.componentes.Confirmacao
import br.com.cuidamed.ui.componentes.EstiloDoBotao
import br.com.cuidamed.ui.componentes.Mensagem
import br.com.cuidamed.ui.componentes.Secao
import br.com.cuidamed.ui.componentes.Tela
import br.com.cuidamed.ui.componentes.TextoSuave
import br.com.cuidamed.ui.componentes.Tom
import br.com.cuidamed.ui.rememberCarregador
import kotlinx.coroutines.launch

private data class DadosDeVinculos(val pedidos: List<PedidoVinculoDto>, val pessoas: List<UsuarioDto>)

/**
 * Vínculo entre idoso e familiar. O familiar pede e o idoso precisa aceitar (o pedido vale 24 horas).
 * O idoso também pode adicionar um familiar direto e remover quem já o acompanha.
 */
@Composable
fun TelaVinculos(usuario: UsuarioDto, destinos: Destinos) {
    val repo = LocalRepositorio.current
    val escopo = rememberCoroutineScope()
    val souIdoso = usuario.ehIdoso
    val carregador = rememberCarregador("vinculos-${usuario.id}") {
        if (souIdoso) {
            when (val pedidos = repo.pedidosRecebidos()) {
                is Resultado.Falha -> pedidos
                is Resultado.Ok -> when (val familiares = repo.meusFamiliares()) {
                    is Resultado.Falha -> familiares
                    is Resultado.Ok -> Resultado.Ok(DadosDeVinculos(pedidos.valor, familiares.valor))
                }
            }
        } else {
            when (val idosos = repo.meusIdosos()) {
                is Resultado.Falha -> idosos
                is Resultado.Ok -> Resultado.Ok(DadosDeVinculos(emptyList(), idosos.valor))
            }
        }
    }
    var mensagem by remember { mutableStateOf<Mensagem?>(null) }
    var email by rememberSaveable { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var paraRemover by remember { mutableStateOf<UsuarioDto?>(null) }

    fun responder(pedido: PedidoVinculoDto, aceitar: Boolean) {
        escopo.launch {
            val r = if (aceitar) repo.aceitarPedido(pedido.familiar.id) else repo.recusarPedido(pedido.familiar.id)
            mensagem = when (r) {
                is Resultado.Ok -> Mensagem(
                    if (aceitar) "${pedido.familiar.nome} agora acompanha você." else "Pedido de ${pedido.familiar.nome} recusado.", Tom.OK,
                )
                is Resultado.Falha -> Mensagem(r.mensagem, Tom.ERRO)
            }
            carregador.carregar(silencioso = true)
        }
    }

    fun enviar() {
        if (email.isBlank()) {
            mensagem = Mensagem("Digite o e-mail ${if (souIdoso) "do familiar" else "do idoso"}.", Tom.AVISO)
            return
        }
        enviando = true
        escopo.launch {
            val r = if (souIdoso) repo.adicionarFamiliar(email) else repo.pedirVinculo(email)
            when (r) {
                is Resultado.Ok -> {
                    mensagem = Mensagem(r.valor.mensagem + if (souIdoso) "" else " Ele vale por 24 horas.", Tom.OK)
                    email = ""
                    carregador.carregar(silencioso = true)
                }
                is Resultado.Falha -> mensagem = Mensagem(r.mensagem, Tom.ERRO)
            }
            enviando = false
        }
    }

    Tela(if (souIdoso) "Meus familiares" else "Pessoas que eu acompanho", aoVoltar = destinos::voltar) {
        AvisoDaTela(mensagem)
        Carregado(carregador) { dados ->
            if (souIdoso && dados.pedidos.isNotEmpty()) {
                Secao("Pedidos para acompanhar você")
                dados.pedidos.forEach { p ->
                    Cartao(Tom.AVISO) {
                        Text("${p.familiar.nome} quer acompanhar você.", style = MaterialTheme.typography.titleMedium)
                        TextoSuave("${p.familiar.email}  ·  pedido de ${dataHoraCurta(p.solicitadoEm)}")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BotaoGrande("Aceitar", { responder(p, true) }, Modifier.weight(1f), EstiloDoBotao.SUCESSO)
                            BotaoGrande("Recusar", { responder(p, false) }, Modifier.weight(1f), EstiloDoBotao.PERIGO)
                        }
                    }
                }
            }

            if (souIdoso) Secao("Quem acompanha você")
            if (dados.pessoas.isEmpty()) {
                Cartao {
                    Text(
                        if (souIdoso) "Nenhum familiar vinculado ainda." else "Você ainda não acompanha ninguém.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            dados.pessoas.forEach { pessoa ->
                Cartao {
                    Text(pessoa.nome, style = MaterialTheme.typography.titleLarge)
                    if (pessoa.email.isNotEmpty()) TextoSuave(pessoa.email)
                    if (souIdoso) BotaoGrande("Remover", { paraRemover = pessoa }, estilo = EstiloDoBotao.PERIGO)
                }
            }
        }

        Secao(if (souIdoso) "Adicionar um familiar" else "Pedir para acompanhar um idoso")
        Cartao {
            Text("A pessoa precisa ter uma conta no CuidaMed. Digite o e-mail dela:", style = MaterialTheme.typography.bodyLarge)
            if (!souIdoso) TextoSuave("O idoso vai receber o pedido e precisa aceitar. Ele vale por 24 horas.")
            CampoDeTexto(email, { email = it }, "E-mail", tipoDeTeclado = KeyboardType.Email)
            BotaoGrande(if (souIdoso) "Adicionar familiar" else "Pedir para acompanhar", ::enviar, carregando = enviando)
        }
    }

    paraRemover?.let { pessoa ->
        Confirmacao(
            titulo = "Remover este familiar?",
            mensagem = "${pessoa.nome} deixará de ver seus remédios e avisos.",
            textoSim = "Sim, remover",
            textoNao = "Não, manter",
            perigoso = true,
            aoCancelar = { paraRemover = null },
            aoConfirmar = {
                paraRemover = null
                escopo.launch {
                    mensagem = when (val r = repo.removerFamiliar(pessoa.id)) {
                        is Resultado.Ok -> Mensagem("${pessoa.nome} foi removido.", Tom.OK)
                        is Resultado.Falha -> Mensagem(r.mensagem, Tom.ERRO)
                    }
                    carregador.carregar(silencioso = true)
                }
            },
        )
    }
}
