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
import br.com.cuidamed.ui.componentes.EscolhasMultiplas
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
    var paraExcluir by remember { mutableStateOf<GrupoDeRemedio?>(null) }
    val meus = usuario.id == idosoId

    // O formulário avisa qual remédio acabou de salvar: a lista relê o que está no aparelho (o salvo já está lá) e mostra a mensagem.
    val salvo by estadoSalvo.getStateFlow<String?>(REMEDIO_SALVO, null).collectAsStateWithLifecycle()
    LaunchedEffect(salvo) {
        val nome = salvo ?: return@LaunchedEffect
        estadoSalvo[REMEDIO_SALVO] = null
        carregador.carregarLocal()
        mensagem = Mensagem("$nome foi salvo.", Tom.OK)
    }

    Tela(if (meus) "Meus remédios" else "Remédios de ${primeiroNome(nomeDoIdoso)}", aoVoltar = destinos::voltar) {
        AvisoDaTela(mensagem)
        BotaoGrande("Adicionar remédio", { destinos.formMedicamento(idosoId, 0) })
        Carregado(carregador) { lista ->
            if (lista.isEmpty()) {
                Cartao { Text("Nenhum remédio cadastrado ainda. Toque em \"Adicionar remédio\" para começar.", style = MaterialTheme.typography.bodyLarge) }
            }
            agruparRemedios(lista).forEach { g ->
                Cartao {
                    Text(g.nome, style = MaterialTheme.typography.titleLarge)
                    TextoSuave(detalheDoGrupo(g))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BotaoGrande("Editar", { destinos.formMedicamento(idosoId, g.remedios.first().id) }, Modifier.weight(1f), EstiloDoBotao.SECUNDARIO)
                        BotaoGrande("Excluir", { paraExcluir = g }, Modifier.weight(1f), EstiloDoBotao.PERIGO)
                    }
                }
            }
        }
        BannerDeAnuncio("remedios-fim")
    }

    paraExcluir?.let { g ->
        Confirmacao(
            titulo = "Excluir este remédio?",
            mensagem = "Você vai excluir ${g.nome} (${rotuloDosDias(g.dias).lowercase()}). Isso não pode ser desfeito.",
            textoSim = "Sim, excluir",
            textoNao = "Não, manter",
            perigoso = true,
            aoCancelar = { paraExcluir = null },
            aoConfirmar = {
                paraExcluir = null
                escopo.launch {
                    val falha = g.remedios.map { repo.excluirMedicamento(it.id) }.filterIsInstance<Resultado.Falha>().firstOrNull()
                    if (falha == null) {
                        mensagem = Mensagem("${g.nome} foi excluído.", Tom.OK)
                        carregador.atualizarLocalmente { lista -> lista.filter { m -> g.remedios.none { it.id == m.id } } }
                        carregador.carregar(silencioso = true)
                    } else {
                        mensagem = Mensagem(falha.mensagem, Tom.ERRO)
                        carregador.carregarLocal()
                    }
                }
            },
        )
    }
}

/** Cadastrar ou editar um remédio: tipo, dias da semana (um ou vários) e horário são escolhidos por botões, sem digitar formatos. */
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
                val grupo = grupoDoRemedio(lista, medicamentoId)
                if (grupo == null) TextoSuave("Este remédio não existe mais.") else FormularioDeMedicamento(idosoId, grupo, destinos)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormularioDeMedicamento(idosoId: Int, existente: GrupoDeRemedio?, destinos: Destinos) {
    val repo = LocalRepositorio.current
    val escopo = rememberCoroutineScope()
    var nome by rememberSaveable { mutableStateOf(existente?.nome.orEmpty()) }
    var tipo by rememberSaveable { mutableStateOf(existente?.tipo ?: "COMPRIMIDO") }
    // guardado como texto ("MONDAY,FRIDAY") para sobreviver a girar a tela
    var diasTexto by rememberSaveable { mutableStateOf(existente?.dias?.joinToString(",").orEmpty()) }
    val dias = diasTexto.split(',').filter { it.isNotEmpty() }.toSet()
    var hora by rememberSaveable { mutableIntStateOf(existente?.horario?.substringBefore(':')?.toIntOrNull() ?: 8) }
    var minuto by rememberSaveable { mutableIntStateOf(existente?.horario?.substringAfter(':')?.toIntOrNull() ?: 0) }
    var carregando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<Mensagem?>(null) }

    fun salvar() {
        when {
            nome.isBlank() -> mensagem = Mensagem("Escreva o nome do remédio.", Tom.AVISO)
            dias.isEmpty() -> mensagem = Mensagem("Escolha pelo menos um dia da semana em que você toma este remédio.", Tom.AVISO)
            else -> {
                carregando = true
                mensagem = null
                escopo.launch {
                    val falha = salvarRemedio(repo, idosoId, existente, nome.trim(), tipo, "%02d:%02d".format(hora, minuto), dias)
                    if (falha == null) {
                        destinos.voltarComRemedioSalvo(nome.trim())
                    } else {
                        mensagem = Mensagem(falha, Tom.ERRO)
                        carregando = false
                    }
                }
            }
        }
    }

    AvisoDaTela(mensagem)
    CampoDeTexto(nome, { nome = it }, "Nome do remédio")
    Text("Como é o remédio?", style = MaterialTheme.typography.titleMedium)
    Escolhas(TIPOS_MEDICAMENTO, tipo) { tipo = it }
    Text("Em quais dias da semana?", style = MaterialTheme.typography.titleMedium)
    TextoSuave("Toque em todos os dias em que você toma este remédio.")
    EscolhasMultiplas(DIAS_DA_SEMANA, dias, "Todos os dias") { novos -> diasTexto = novos.joinToString(",") }
    Text("A que horas?", style = MaterialTheme.typography.titleMedium)
    // Quebra em duas linhas quando a letra está grande e os dois contadores não cabem lado a lado.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Contador("Hora", hora, 0, 23, 1) { hora = it }
        Contador("Minutos", minuto, 0, 59, 5) { minuto = it }
    }
    BotaoGrande(if (existente == null) "Salvar remédio" else "Salvar mudanças", ::salvar, carregando = carregando)
    BotaoGrande("Cancelar", destinos::voltar, estilo = EstiloDoBotao.SECUNDARIO)
}

/**
 * Grava o remédio nos dias escolhidos. Cada dia é um registro; ao editar, o dia que saiu dá lugar ao dia que entrou
 * (o registro é reaproveitado, então o histórico dele não se perde), o que sobra é excluído e o que falta é criado.
 * Devolve a mensagem de erro, ou null se tudo deu certo.
 */
private suspend fun salvarRemedio(
    repo: br.com.cuidamed.data.Repositorio, idosoId: Int, existente: GrupoDeRemedio?,
    nome: String, tipo: String, horario: String, dias: Set<String>,
): String? {
    val atuais = existente?.remedios.orEmpty()
    val diasOrdenados = DIAS_DA_SEMANA.map { it.first }.filter { it in dias }
    val mantidos = atuais.filter { it.diaSemana in dias }
    val sobrando = atuais.filter { it.diaSemana !in dias }.toMutableList()
    val faltando = diasOrdenados.filter { dia -> mantidos.none { it.diaSemana == dia } }

    for (m in mantidos) {
        if (m.nome == nome && m.tipo == tipo && m.horario == horario) continue
        val r = repo.editarMedicamento(m.id, MedicamentoRequest(nome, m.diaSemana, horario, tipo))
        if (r is Resultado.Falha) return r.mensagem
    }
    for (dia in faltando) {
        val reaproveitado = sobrando.removeFirstOrNull()
        val pedido = MedicamentoRequest(nome, dia, horario, tipo)
        val r = if (reaproveitado != null) repo.editarMedicamento(reaproveitado.id, pedido) else repo.cadastrarMedicamento(idosoId, pedido)
        if (r is Resultado.Falha) return r.mensagem
    }
    for (m in sobrando) {
        val r = repo.excluirMedicamento(m.id)
        if (r is Resultado.Falha) return r.mensagem
    }
    return null
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
            agruparRemedios(lista).forEach { g ->
                Cartao {
                    Text(g.nome, style = MaterialTheme.typography.titleLarge)
                    TextoSuave(detalheDoGrupo(g))
                    BotaoGrande("Tomei este remédio", {
                        emAndamento = g.remedios.first().id
                        escopo.launch {
                            mensagem = when (val r = repo.registrarTomada(g.paraTomar().id)) {
                                is Resultado.Ok -> Mensagem("Anotado! Você tomou ${g.nome} às ${horaDe(r.valor.dataHora)}.", Tom.OK)
                                is Resultado.Falha -> Mensagem(r.mensagem, Tom.AVISO)
                            }
                            emAndamento = -1
                        }
                    }, estilo = EstiloDoBotao.SUCESSO, carregando = emAndamento == g.remedios.first().id)
                }
            }
        }
    }
}

/** As tomadas registradas e os horários esquecidos ("Não tomou"), do mais recente para o mais antigo. O estado aparece escrito, não só em cor. */
@Composable
fun TelaHistorico(idosoId: Int, nomeDoIdoso: String, usuario: UsuarioDto, destinos: Destinos) {
    val repo = LocalRepositorio.current
    val carregador = rememberCarregador("historico-$idosoId", buscarLocal = { repo.historicoLocal(idosoId) }) { repo.historico(idosoId) }
    val meu = usuario.id == idosoId
    Tela(if (meu) "Meu histórico" else "Histórico de ${primeiroNome(nomeDoIdoso)}", aoVoltar = destinos::voltar) {
        BaixarHistoricoEmPdf(idosoId, nomeDoIdoso)
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
