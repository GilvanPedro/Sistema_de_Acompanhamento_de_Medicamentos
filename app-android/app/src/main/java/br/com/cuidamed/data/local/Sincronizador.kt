package br.com.cuidamed.data.local

import br.com.cuidamed.data.CuidaMedApi
import br.com.cuidamed.data.MedicamentoRequest
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.data.TomadaRequest
import br.com.cuidamed.data.chamar
import br.com.cuidamed.data.chamarVazio
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed interface ResultadoDoEnvio {
    /** A fila foi enviada até o fim (ou estava vazia). [mudou]: algo foi enviado ou recusado. */
    data class Concluido(val mudou: Boolean) : ResultadoDoEnvio

    /** Sem internet (ou servidor indisponível): o que faltou continua na fila para a próxima tentativa. */
    data class SemConexao(val mudou: Boolean) : ResultadoDoEnvio

    /** O servidor recusou a renovação do login: é preciso entrar de novo. */
    data object SessaoPerdida : ResultadoDoEnvio

    /** Já existe um envio em andamento. */
    data object JaEmAndamento : ResultadoDoEnvio
}

private sealed interface Execucao {
    data object Sucesso : Execucao
    data class Recusada(val mensagem: String) : Execucao
    data object Temporaria : Execucao
    data object SessaoPerdida : Execucao
}

/**
 * Envia a fila de alterações feitas sem internet, uma por vez e na ordem em que foram feitas.
 *
 * Conflitos (regras combinadas para o app): excluir vence editar, e entre duas edições vale a que chega por último ao
 * servidor. Quem faz isso valer é o servidor: editar um remédio já excluído dá 404 aqui, e a alteração é descartada com um
 * aviso ao usuário. Falhas de rede e do servidor não descartam nada; só as recusas definitivas (400, 403, 404) descartam.
 */
class Sincronizador(private val api: CuidaMedApi, private val armazenamento: ArmazenamentoLocal) {

    private val trava = Mutex()

    /** Aumenta a cada alteração enviada; quem buscou dados antes de um envio sabe que a cópia ficou velha. */
    @Volatile
    var versao: Int = 0
        private set

    suspend fun enviar(usuarioId: Int): ResultadoDoEnvio {
        if (!trava.tryLock()) return ResultadoDoEnvio.JaEmAndamento
        try {
            var mudou = false
            while (true) {
                val operacao = armazenamento.ler(usuarioId).pendencias.firstOrNull() ?: return ResultadoDoEnvio.Concluido(mudou)
                when (val r = executar(usuarioId, operacao)) {
                    Execucao.Sucesso -> {
                        mudou = true
                        versao++
                    }
                    is Execucao.Recusada -> {
                        registrarRecusa(usuarioId, operacao, r.mensagem)
                        mudou = true
                        versao++
                    }
                    Execucao.Temporaria -> return ResultadoDoEnvio.SemConexao(mudou)
                    Execucao.SessaoPerdida -> return ResultadoDoEnvio.SessaoPerdida
                }
            }
        } finally {
            trava.unlock()
        }
    }

    private suspend fun executar(usuarioId: Int, operacao: Operacao): Execucao = when (operacao) {
        is CriarRemedio -> {
            val r = chamar {
                api.cadastrarMedicamentoComChave(
                    operacao.idosoId, MedicamentoRequest(operacao.nome, operacao.diaSemana, operacao.horario, operacao.tipo), operacao.idOperacao,
                )
            }
            when (r) {
                is Resultado.Ok -> {
                    val criado = r.valor
                    armazenamento.alterar(usuarioId) { d ->
                        // do id local para o id do servidor: as próximas alterações deste remédio passam a apontar para ele
                        val restantes = d.pendencias.filter { it.idOperacao != operacao.idOperacao }.map { trocarId(it, operacao.idLocal, criado.id) }
                        val lista = d.remedios[operacao.idosoId].orEmpty()
                        val nova = if (lista.any { it.id == criado.id }) lista else lista + criado
                        d.copy(pendencias = restantes, remedios = d.remedios + (operacao.idosoId to nova)) to Unit
                    }
                    Execucao.Sucesso
                }
                is Resultado.Falha -> classificar(r, "O remédio ${operacao.nome} não pôde ser cadastrado")
            }
        }

        is EditarRemedio -> {
            val r = chamar { api.editarMedicamento(operacao.remedioId, MedicamentoRequest(operacao.nome, operacao.diaSemana, operacao.horario, operacao.tipo)) }
            when (r) {
                is Resultado.Ok -> {
                    armazenamento.alterar(usuarioId) { d ->
                        val lista = d.remedios[operacao.idosoId].orEmpty().map { if (it.id == r.valor.id) r.valor else it }
                        d.copy(pendencias = d.pendencias.filter { it.idOperacao != operacao.idOperacao }, remedios = d.remedios + (operacao.idosoId to lista)) to Unit
                    }
                    Execucao.Sucesso
                }
                is Resultado.Falha ->
                    if (r.codigo == 404) Execucao.Recusada("${operacao.nomeDoRemedio} foi excluído por outra pessoa. A sua alteração não foi aplicada.")
                    else classificar(r, "A alteração de ${operacao.nomeDoRemedio} não foi aplicada")
            }
        }

        is ExcluirRemedio -> {
            val r = chamarVazio { api.excluirMedicamento(operacao.remedioId) }
            when {
                r is Resultado.Ok || (r is Resultado.Falha && r.codigo == 404) -> { // já excluído: era o que se queria
                    armazenamento.alterar(usuarioId) { d ->
                        val lista = d.remedios[operacao.idosoId].orEmpty().filter { it.id != operacao.remedioId }
                        d.copy(pendencias = d.pendencias.filter { it.idOperacao != operacao.idOperacao }, remedios = d.remedios + (operacao.idosoId to lista)) to Unit
                    }
                    Execucao.Sucesso
                }
                else -> classificar(r as Resultado.Falha, "A exclusão de ${operacao.nomeDoRemedio} não foi aplicada")
            }
        }

        is RegistrarTomada -> {
            val r = chamar { api.registrarTomadaEm(operacao.remedioId, TomadaRequest(operacao.quando)) }
            when {
                r is Resultado.Ok -> {
                    armazenamento.alterar(usuarioId) { d ->
                        val historico = d.historicos[operacao.idosoId].orEmpty()
                        val novo = if (historico.any { it.id == r.valor.id }) historico else historico + r.valor
                        d.copy(pendencias = d.pendencias.filter { it.idOperacao != operacao.idOperacao }, historicos = d.historicos + (operacao.idosoId to novo)) to Unit
                    }
                    Execucao.Sucesso
                }
                // "já registrou nesse dia": a tomada já estava lá (por exemplo, de um envio que a resposta se perdeu)
                r is Resultado.Falha && r.codigo == 400 && r.mensagem.contains("já registrou") -> {
                    armazenamento.alterar(usuarioId) { d -> d.copy(pendencias = d.pendencias.filter { it.idOperacao != operacao.idOperacao }) to Unit }
                    Execucao.Sucesso
                }
                r is Resultado.Falha && r.codigo == 404 ->
                    Execucao.Recusada("${operacao.nomeDoRemedio} foi excluído. A tomada que você marcou não foi registrada.")
                else -> classificar(r as Resultado.Falha, "A tomada de ${operacao.nomeDoRemedio} (${horaCurta(operacao.quando)}) não foi registrada")
            }
        }
    }

    /** Sem código = falha de rede; 401 = sessão acabou; 429/408/5xx = tente de novo depois; o resto é recusa definitiva. */
    private fun classificar(falha: Resultado.Falha, prefixo: String): Execucao = when (falha.codigo) {
        null -> Execucao.Temporaria
        401 -> Execucao.SessaoPerdida
        408, 429 -> Execucao.Temporaria
        in 500..599 -> Execucao.Temporaria
        else -> Execucao.Recusada("$prefixo: ${falha.mensagem}")
    }

    /** Descarta a alteração recusada (e, se era um cadastro, as que dependiam dele) e guarda o aviso para o usuário. */
    private suspend fun registrarRecusa(usuarioId: Int, operacao: Operacao, mensagem: String) {
        armazenamento.alterar(usuarioId) { d ->
            val dependentes: (Operacao) -> Boolean = { op ->
                op.idOperacao == operacao.idOperacao || (operacao is CriarRemedio && referencia(op) == operacao.idLocal)
            }
            d.copy(pendencias = d.pendencias.filterNot(dependentes), conflitos = (d.conflitos + mensagem).takeLast(20)) to Unit
        }
    }

    private fun referencia(op: Operacao): Int? = when (op) {
        is EditarRemedio -> op.remedioId
        is ExcluirRemedio -> op.remedioId
        is RegistrarTomada -> op.remedioId
        is CriarRemedio -> null
    }

    private fun trocarId(op: Operacao, de: Int, para: Int): Operacao = when (op) {
        is EditarRemedio -> if (op.remedioId == de) op.copy(remedioId = para) else op
        is ExcluirRemedio -> if (op.remedioId == de) op.copy(remedioId = para) else op
        is RegistrarTomada -> if (op.remedioId == de) op.copy(remedioId = para) else op
        is CriarRemedio -> op
    }

    private fun horaCurta(iso: String): String = try {
        LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    } catch (e: Exception) {
        iso
    }
}
