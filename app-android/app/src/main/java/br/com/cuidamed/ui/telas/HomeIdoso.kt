package br.com.cuidamed.ui.telas

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import br.com.cuidamed.data.NotificacaoDto
import br.com.cuidamed.data.PedidoVinculoDto
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.data.UsuarioDto
import br.com.cuidamed.notificacoes.AvisoDeNotificacoesDesligadas
import br.com.cuidamed.ui.BarraDeAtualizacao
import br.com.cuidamed.ui.Carregado
import br.com.cuidamed.ui.Destinos
import br.com.cuidamed.ui.LocalRepositorio
import br.com.cuidamed.ui.componentes.AvisoDaTela
import br.com.cuidamed.ui.componentes.BotaoGrande
import br.com.cuidamed.ui.componentes.Cartao
import br.com.cuidamed.ui.componentes.EstiloDoBotao
import br.com.cuidamed.ui.componentes.Mensagem
import br.com.cuidamed.ui.componentes.Secao
import br.com.cuidamed.ui.componentes.Tela
import br.com.cuidamed.ui.componentes.Tom
import br.com.cuidamed.ui.rememberCarregador
import kotlinx.coroutines.launch

private data class DadosDaHomeIdoso(val avisos: List<NotificacaoDto>, val pedidos: List<PedidoVinculoDto>)

private fun ordemDoAviso(tipo: String) = when (tipo) {
    "ESQUECIDO" -> 0
    "LEMBRETE" -> 1
    else -> 2
}

/** Tela inicial do idoso: avisos de hoje e um menu com poucas opções grandes. */
@Composable
fun TelaHomeIdoso(usuario: UsuarioDto, destinos: Destinos) {
    val repo = LocalRepositorio.current
    val escopo = rememberCoroutineScope()
    val carregador = rememberCarregador(
        "home-${usuario.id}",
        atualizarACadaSegundos = 60,
        buscarLocal = { repo.avisosLocais(usuario.id)?.let { DadosDaHomeIdoso(it, emptyList()) } },
    ) {
        when (val avisos = repo.notificacoes(usuario.id)) {
            is Resultado.Falha -> avisos
            // os pedidos de vínculo precisam de internet: sem ela, a tela segue funcionando sem eles
            is Resultado.Ok -> Resultado.Ok(DadosDaHomeIdoso(avisos.valor, (repo.pedidosRecebidos() as? Resultado.Ok)?.valor.orEmpty()))
        }
    }
    var mensagem by remember { mutableStateOf<Mensagem?>(null) }
    var emAndamento by remember { mutableIntStateOf(-1) }

    Tela("Olá, ${primeiroNome(usuario.nome)}!", "Hoje é ${dataDeHoje()}.") {
        AvisoDeNotificacoesDesligadas()
        AvisoDaTela(mensagem)
        BarraDeAtualizacao(carregador)
        Carregado(carregador) { dados ->
            if (dados.pedidos.isNotEmpty()) {
                Cartao(Tom.AVISO) {
                    Text(
                        if (dados.pedidos.size == 1) "Um familiar quer acompanhar você." else "${dados.pedidos.size} familiares querem acompanhar você.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    BotaoGrande("Ver pedidos", destinos::vinculos)
                }
            }

            Secao("Avisos de hoje")
            if (dados.avisos.isEmpty()) {
                Cartao { Text("Nenhum remédio para tomar agora. Está tudo em dia!", style = MaterialTheme.typography.bodyLarge) }
            }
            dados.avisos.sortedWith(compareBy({ ordemDoAviso(it.tipo) }, { it.medicamento.horario })).forEach { aviso ->
                val m = aviso.medicamento
                val tom = when (aviso.tipo) {
                    "TOMADO" -> Tom.OK
                    "ESQUECIDO" -> Tom.ERRO
                    else -> Tom.AVISO
                }
                Cartao(tom) {
                    Text(
                        when (aviso.tipo) {
                            "TOMADO" -> "Você já tomou ${m.nome} hoje."
                            "ESQUECIDO" -> "Atenção: você ainda não tomou ${m.nome}. Era para as ${m.horario}."
                            else -> "Está na hora de tomar ${m.nome} (${m.horario})."
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (aviso.tipo != "TOMADO") {
                        BotaoGrande("Já tomei", {
                            emAndamento = m.id
                            escopo.launch {
                                mensagem = when (val r = repo.registrarTomada(m.id)) {
                                    is Resultado.Ok -> Mensagem("Anotado! Você tomou ${m.nome}.", Tom.OK)
                                    is Resultado.Falha -> Mensagem(r.mensagem, Tom.AVISO)
                                }
                                emAndamento = -1
                                carregador.carregar(silencioso = true)
                            }
                        }, estilo = EstiloDoBotao.SUCESSO, carregando = emAndamento == m.id)
                    }
                }
            }
        }

        Secao("O que você quer fazer?")
        BotaoGrande("Tomei um remédio", { destinos.tomei(usuario.id) })
        BotaoGrande("Meus remédios", { destinos.medicamentos(usuario.id, usuario.nome) }, estilo = EstiloDoBotao.SECUNDARIO)
        BotaoGrande("Meu histórico", { destinos.historico(usuario.id, usuario.nome) }, estilo = EstiloDoBotao.SECUNDARIO)
        BotaoGrande("Meus familiares", destinos::vinculos, estilo = EstiloDoBotao.SECUNDARIO)
        BotaoGrande("Meus dados", destinos::perfil, estilo = EstiloDoBotao.SECUNDARIO)
    }
}
