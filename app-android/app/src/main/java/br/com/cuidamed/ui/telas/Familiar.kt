package br.com.cuidamed.ui.telas

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import br.com.cuidamed.data.NotificacaoDto
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.data.UsuarioDto
import br.com.cuidamed.notificacoes.AvisoDeNotificacoesDesligadas
import br.com.cuidamed.ui.BarraDeAtualizacao
import br.com.cuidamed.ui.Carregado
import br.com.cuidamed.ui.Destinos
import br.com.cuidamed.ui.LocalRepositorio
import br.com.cuidamed.ui.componentes.BotaoGrande
import br.com.cuidamed.ui.componentes.Cartao
import br.com.cuidamed.ui.componentes.EstiloDoBotao
import br.com.cuidamed.ui.componentes.Secao
import br.com.cuidamed.ui.componentes.Tela
import br.com.cuidamed.ui.componentes.Tom
import br.com.cuidamed.ui.rememberCarregador

private data class AvisosDoIdoso(val idoso: UsuarioDto, val avisos: List<NotificacaoDto>)

/** Cartões de aviso para o familiar: só o que importa (não tomou / já tomou). O lembrete de horário é do idoso. */
@Composable
private fun CartoesDeAvisoParaFamiliar(idoso: UsuarioDto, avisos: List<NotificacaoDto>): Boolean {
    var algum = false
    avisos.forEach { aviso ->
        val m = aviso.medicamento
        when (aviso.tipo) {
            "ESQUECIDO" -> {
                algum = true
                Cartao(Tom.ERRO) {
                    Text("Atenção: ${idoso.nome} ainda não tomou ${m.nome}. Era para as ${m.horario}.", style = MaterialTheme.typography.titleMedium)
                }
            }
            "TOMADO" -> {
                algum = true
                Cartao(Tom.OK) { Text("${idoso.nome} já tomou ${m.nome} hoje.", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
    return algum
}

/** Tela inicial do familiar: situação de hoje de cada idoso e acesso a cada um. */
@Composable
fun TelaHomeFamiliar(usuario: UsuarioDto, destinos: Destinos) {
    val repo = LocalRepositorio.current
    val carregador = rememberCarregador(
        "home-${usuario.id}",
        atualizarACadaSegundos = 60,
        buscarLocal = { repo.idososLocais()?.map { AvisosDoIdoso(it, repo.avisosLocais(it.id).orEmpty()) } },
    ) {
        when (val idosos = repo.meusIdosos()) {
            is Resultado.Falha -> idosos
            is Resultado.Ok -> {
                val lista = mutableListOf<AvisosDoIdoso>()
                var falha: Resultado.Falha? = null
                for (idoso in idosos.valor) {
                    when (val avisos = repo.notificacoes(idoso.id)) {
                        is Resultado.Ok -> lista.add(AvisosDoIdoso(idoso, avisos.valor))
                        is Resultado.Falha -> { falha = avisos; break }
                    }
                }
                falha ?: Resultado.Ok(lista.toList())
            }
        }
    }

    Tela("Olá, ${primeiroNome(usuario.nome)}!", "Veja como estão as pessoas que você acompanha.") {
        AvisoDeNotificacoesDesligadas()
        BarraDeAtualizacao(carregador)
        Carregado(carregador) { dados ->
            Secao("Avisos de hoje")
            var algum = false
            dados.forEach { d -> if (CartoesDeAvisoParaFamiliar(d.idoso, d.avisos)) algum = true }
            if (!algum) Cartao { Text("Nenhum aviso por enquanto.", style = MaterialTheme.typography.bodyLarge) }

            Secao("Pessoas que você acompanha")
            if (dados.isEmpty()) {
                Cartao {
                    Text("Você ainda não acompanha ninguém. Use o botão \"Vincular um idoso\" abaixo.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            dados.forEach { d ->
                Cartao {
                    Text(d.idoso.nome, style = MaterialTheme.typography.titleLarge)
                    BotaoGrande("Abrir", { destinos.idosoDoFamiliar(d.idoso.id, d.idoso.nome) })
                }
            }
        }
        BotaoGrande("Vincular um idoso", destinos::vinculos, estilo = EstiloDoBotao.SECUNDARIO)
        BotaoGrande("Meus dados", destinos::perfil, estilo = EstiloDoBotao.SECUNDARIO)
    }
}

/** O que o familiar pode fazer com um idoso que acompanha: ver os avisos de hoje, os remédios e o histórico. */
@Composable
fun TelaIdosoDoFamiliar(idosoId: Int, nome: String, destinos: Destinos) {
    val repo = LocalRepositorio.current
    val carregador = rememberCarregador("avisos-idoso-$idosoId", atualizarACadaSegundos = 60, buscarLocal = { repo.avisosLocais(idosoId) }) { repo.notificacoes(idosoId) }
    val idoso = UsuarioDto(idosoId, "IDOSO", nome, "")

    Tela(nome, "Você acompanha esta pessoa.", destinos::voltar) {
        BarraDeAtualizacao(carregador)
        Carregado(carregador) { avisos ->
            Secao("Hoje")
            if (!CartoesDeAvisoParaFamiliar(idoso, avisos)) {
                Cartao { Text("Nenhum aviso por enquanto.", style = MaterialTheme.typography.bodyLarge) }
            }
        }
        BotaoGrande("Ver e mudar os remédios", { destinos.medicamentos(idosoId, nome) })
        BotaoGrande("Ver o histórico", { destinos.historico(idosoId, nome) }, estilo = EstiloDoBotao.SECUNDARIO)
    }
}
