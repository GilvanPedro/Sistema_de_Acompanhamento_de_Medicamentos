package br.com.cuidamed.ui.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.cuidamed.data.DIAS_DA_SEMANA
import br.com.cuidamed.data.MedicamentoDto
import br.com.cuidamed.data.MedicamentoRequest
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.data.TIPOS_MEDICAMENTO
import br.com.cuidamed.data.UsuarioDto
import br.com.cuidamed.data.jsonDaApi
import br.com.cuidamed.ui.REMEDIO_SALVO
import br.com.cuidamed.ui.Carregado
import br.com.cuidamed.ui.Destinos
import br.com.cuidamed.ui.LocalRepositorio
import br.com.cuidamed.ui.componentes.BannerDeAnuncio
import br.com.cuidamed.ui.componentes.AvisoDaTela
import br.com.cuidamed.ui.componentes.BotaoGrande
import br.com.cuidamed.ui.componentes.Cartao
import br.com.cuidamed.ui.componentes.CampoDeTexto
import br.com.cuidamed.ui.componentes.Confirmacao
import br.com.cuidamed.ui.componentes.Contador
import br.com.cuidamed.ui.componentes.Escolhas
import br.com.cuidamed.ui.componentes.EstiloDoBotao
import br.com.cuidamed.ui.componentes.Mensagem
import br.com.cuidamed.ui.componentes.Tela
import br.com.cuidamed.ui.componentes.TextoSuave
import br.com.cuidamed.ui.componentes.Tom
import br.com.cuidamed.ui.rememberCarregador
import kotlinx.coroutines.launch

/** Lista de remédios de um idoso (dele mesmo ou, para o familiar, de quem ele acompanha), com editar e excluir. */
@Composable
fun TelaMedicamentos(idosoId: Int, nomeDoIdoso: String, usuario: UsuarioDto, destinos: Destinos, estadoSalvo: SavedStateHandle) {
    val repo = LocalRepositorio.current
    val escopo = rememberCoroutineScope()
    val carregador = rememberCarregador("remedios-$idosoId", buscarLocal = { repo.remediosLocais(idosoId) }) { repo.medicamentos(idosoId) }
    var mensagem by remember { mutableStateOf<Mensagem?>(null) }
    var paraExcluir by remember { mutableStateOf<MedicamentoDto?>(null) }
    val meus = usuario.id == idosoId

    // O formulário entrega o remédio que acabou de salvar: aparece na hora, e a busca em seguida só confirma.
    val salvo by estadoSalvo.getStateFlow<String?>(REMEDIO_SALVO, null).collectAsStateWithLifecycle()
    LaunchedEffect(salvo) {
        val json = salvo ?: return@LaunchedEffect
        estadoSalvo[REMEDIO_SALVO] = null
        val remedio = try {
            jsonDaApi.decodeFromString<MedicamentoDto>(json)
        } catch (e: Exception) {
            null
        } ?: return@LaunchedEffect
        carregador.atualizarLocalmente { lista -> lista.filter { it.id != remedio.id } + remedio }
        mensagem = Mensagem("${remedio.nome} foi salvo.", Tom.OK)
    }

    Tela(if (meus) "Meus remédios" else "Remédios de ${primeiroNome(nomeDoIdoso)}", aoVoltar = destinos::voltar) {
        AvisoDaTela(mensagem)
        BotaoGrande("Adicionar remédio", { destinos.formMedicamento(idosoId, 0) })
        Carregado(carregador) { lista ->
            if (lista.isEmpty()) {
                Cartao { Text("Nenhum remédio cadastrado ainda. Toque em \"Adicionar remédio\" para começar.", style = MaterialTheme.typography.bodyLarge) }
            }
            remediosEmOrdem(lista).forEach { m ->
                Cartao {
                    Text(m.nome, style = MaterialTheme.typography.titleLarge)
                    TextoSuave(detalheDoRemedio(m))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BotaoGrande("Editar", { destinos.formMedicamento(idosoId, m.id) }, Modifier.weight(1f), EstiloDoBotao.SECUNDARIO)
                        BotaoGrande("Excluir", { paraExcluir = m }, Modifier.weight(1f), EstiloDoBotao.PERIGO)
                    }
                }
            }
        }
        BannerDeAnuncio("remedios-fim")
    }

    paraExcluir?.let { m ->
        Confirmacao(
            titulo = "Excluir este remédio?",
            mensagem = "Você vai excluir ${m.nome}. Isso não pode ser desfeito.",
            textoSim = "Sim, excluir",
            textoNao = "Não, manter",
            perigoso = true,
            aoCancelar = { paraExcluir = null },
            aoConfirmar = {
                paraExcluir = null
                escopo.launch {
                    when (val r = repo.excluirMedicamento(m.id)) {
                        is Resultado.Ok -> {
                            mensagem = Mensagem("${m.nome} foi excluído.", Tom.OK)
                            carregador.atualizarLocalmente { lista -> lista.filter { it.id != m.id } }
                            carregador.carregar(silencioso = true)
                        }
                        is Resultado.Falha -> mensagem = Mensagem(r.mensagem, Tom.ERRO)
                    }
                }
            },
        )
    }
}

/** Cadastrar ou editar um remédio: tipo, dia e horário são escolhidos por botões, sem digitar formatos. */
@Composable
fun TelaFormMedicamento(idosoId: Int, medicamentoId: Int, destinos: Destinos) {
    val repo = LocalRepositorio.current
    val novo = medicamentoId == 0
    Tela(if (novo) "Novo remédio" else "Editar remédio", aoVoltar = destinos::voltar) {
        if (novo) {
            FormularioDeMedicamento(idosoId, null, destinos)
        } else {
            val carregador = rememberCarregador("remedios-$idosoId", buscarLocal = { repo.remediosLocais(idosoId) }) { repo.medicamentos(idosoId) }
            Carregado(carregador) { lista ->
                val existente = lista.firstOrNull { it.id == medicamentoId }
                if (existente == null) TextoSuave("Este remédio não existe mais.") else FormularioDeMedicamento(idosoId, existente, destinos)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormularioDeMedicamento(idosoId: Int, existente: MedicamentoDto?, destinos: Destinos) {
    val repo = LocalRepositorio.current
    val escopo = rememberCoroutineScope()
    var nome by rememberSaveable { mutableStateOf(existente?.nome.orEmpty()) }
    var tipo by rememberSaveable { mutableStateOf(existente?.tipo ?: "COMPRIMIDO") }
    var dia by rememberSaveable { mutableStateOf(existente?.diaSemana) }
    var hora by rememberSaveable { mutableIntStateOf(existente?.horario?.substringBefore(':')?.toIntOrNull() ?: 8) }
    var minuto by rememberSaveable { mutableIntStateOf(existente?.horario?.substringAfter(':')?.toIntOrNull() ?: 0) }
    var carregando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<Mensagem?>(null) }

    fun salvar() {
        val diaEscolhido = dia
        when {
            nome.isBlank() -> mensagem = Mensagem("Escreva o nome do remédio.", Tom.AVISO)
            diaEscolhido == null -> mensagem = Mensagem("Escolha o dia da semana em que você toma este remédio.", Tom.AVISO)
            else -> {
                carregando = true
                mensagem = null
                val pedido = MedicamentoRequest(nome.trim(), diaEscolhido, "%02d:%02d".format(hora, minuto), tipo)
                escopo.launch {
                    val r = if (existente == null) repo.cadastrarMedicamento(idosoId, pedido) else repo.editarMedicamento(existente.id, pedido)
                    when (r) {
                        is Resultado.Ok -> destinos.voltarComRemedioSalvo(r.valor)
                        is Resultado.Falha -> {
                            mensagem = Mensagem(r.mensagem, Tom.ERRO)
                            carregando = false
                        }
                    }
                }
            }
        }
    }

    AvisoDaTela(mensagem)
    CampoDeTexto(nome, { nome = it }, "Nome do remédio")
    Text("Como é o remédio?", style = MaterialTheme.typography.titleMedium)
    Escolhas(TIPOS_MEDICAMENTO, tipo) { tipo = it }
    Text("Em que dia da semana?", style = MaterialTheme.typography.titleMedium)
    Escolhas(DIAS_DA_SEMANA, dia) { dia = it }
    Text("A que horas?", style = MaterialTheme.typography.titleMedium)
    // Quebra em duas linhas quando a letra está grande e os dois contadores não cabem lado a lado.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Contador("Hora", hora, 0, 23, 1) { hora = it }
        Contador("Minutos", minuto, 0, 59, 5) { minuto = it }
    }
    BotaoGrande(if (existente == null) "Salvar remédio" else "Salvar mudanças", ::salvar, carregando = carregando)
    BotaoGrande("Cancelar", destinos::voltar, estilo = EstiloDoBotao.SECUNDARIO)
}

/** O idoso escolhe qual remédio acabou de tomar. */
@Composable
fun TelaTomeiUmRemedio(idosoId: Int, destinos: Destinos) {
    val repo = LocalRepositorio.current
    val escopo = rememberCoroutineScope()
    val carregador = rememberCarregador("remedios-$idosoId", buscarLocal = { repo.remediosLocais(idosoId) }) { repo.medicamentos(idosoId) }
    var mensagem by remember { mutableStateOf<Mensagem?>(null) }
    var emAndamento by remember { mutableIntStateOf(-1) }

    Tela("Qual remédio você tomou?", "Toque no botão verde do remédio que você tomou agora.", destinos::voltar) {
        AvisoDaTela(mensagem)
        Carregado(carregador) { lista ->
            if (lista.isEmpty()) {
                Cartao { Text("Você ainda não cadastrou nenhum remédio.", style = MaterialTheme.typography.bodyLarge) }
                BotaoGrande("Cadastrar um remédio", { destinos.formMedicamento(idosoId, 0) })
            }
            remediosEmOrdem(lista).forEach { m ->
                Cartao {
                    Text(m.nome, style = MaterialTheme.typography.titleLarge)
                    TextoSuave(detalheDoRemedio(m))
                    BotaoGrande("Tomei este remédio", {
                        emAndamento = m.id
                        escopo.launch {
                            mensagem = when (val r = repo.registrarTomada(m.id)) {
                                is Resultado.Ok -> Mensagem("Anotado! Você tomou ${m.nome} às ${horaDe(r.valor.dataHora)}.", Tom.OK)
                                is Resultado.Falha -> Mensagem(r.mensagem, Tom.AVISO)
                            }
                            emAndamento = -1
                        }
                    }, estilo = EstiloDoBotao.SUCESSO, carregando = emAndamento == m.id)
                }
            }
        }
    }
}

/** As tomadas registradas, da mais recente para a mais antiga. O estado aparece escrito, não só em cor. */
@Composable
fun TelaHistorico(idosoId: Int, nomeDoIdoso: String, usuario: UsuarioDto, destinos: Destinos) {
    val repo = LocalRepositorio.current
    val carregador = rememberCarregador("historico-$idosoId", buscarLocal = { repo.historicoLocal(idosoId) }) { repo.historico(idosoId) }
    val meu = usuario.id == idosoId
    Tela(if (meu) "Meu histórico" else "Histórico de ${primeiroNome(nomeDoIdoso)}", aoVoltar = destinos::voltar) {
        Carregado(carregador) { lista ->
            if (lista.isEmpty()) {
                Cartao { Text("Nenhuma tomada registrada ainda.", style = MaterialTheme.typography.bodyLarge) }
            }
            lista.sortedByDescending { it.dataHora }.forEach { h ->
                Cartao(if (h.foiTomado) Tom.OK else Tom.ERRO) {
                    Text(h.medicamentoNome, style = MaterialTheme.typography.titleLarge)
                    Text(dataHoraCurta(h.dataHora), style = MaterialTheme.typography.bodyLarge)
                    Text(if (h.foiTomado) "Tomou" else "Não tomou", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
