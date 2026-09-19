package br.com.cuidamed.data.local

import br.com.cuidamed.data.HistoricoDto
import br.com.cuidamed.data.MedicamentoDto
import br.com.cuidamed.data.NotificacaoDto
import br.com.cuidamed.data.UsuarioDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Uma alteração feita no aparelho que ainda não chegou ao servidor. Ficam numa fila, na ordem em que foram feitas, e
 * são enviadas quando houver internet. Cada uma tem um [idOperacao] que também serve de chave de idempotência.
 */
@Serializable
sealed interface Operacao {
    val idOperacao: String
    val idosoId: Int
    val criadaEm: String
}

/** Remédio cadastrado sem internet. Enquanto não sobe, ele existe só aqui, com um [idLocal] negativo. */
@Serializable
@SerialName("criar")
data class CriarRemedio(
    override val idOperacao: String,
    override val idosoId: Int,
    val idLocal: Int,
    val nome: String,
    val diaSemana: String,
    val horario: String,
    val tipo: String,
    override val criadaEm: String,
) : Operacao

/** Edição parcial: os campos nulos não mudam. */
@Serializable
@SerialName("editar")
data class EditarRemedio(
    override val idOperacao: String,
    override val idosoId: Int,
    val remedioId: Int,
    val nomeDoRemedio: String,
    val nome: String? = null,
    val diaSemana: String? = null,
    val horario: String? = null,
    val tipo: String? = null,
    override val criadaEm: String,
) : Operacao

@Serializable
@SerialName("excluir")
data class ExcluirRemedio(
    override val idOperacao: String,
    override val idosoId: Int,
    val remedioId: Int,
    val nomeDoRemedio: String,
    override val criadaEm: String,
) : Operacao

/** O idoso tocou em "já tomei". [quando] é a hora do toque (hora local do aparelho), e não a hora do envio. */
@Serializable
@SerialName("tomada")
data class RegistrarTomada(
    override val idOperacao: String,
    override val idosoId: Int,
    val remedioId: Int,
    val nomeDoRemedio: String,
    val quando: String,
    override val criadaEm: String,
) : Operacao

/** Tudo o que o aparelho guarda de uma pessoa: a última cópia vinda do servidor e as alterações ainda não enviadas. */
@Serializable
data class DadosLocais(
    val usuario: UsuarioDto? = null,
    /** Só para familiar: os idosos que ele acompanha. */
    val idosos: List<UsuarioDto>? = null,
    /** A última cópia do servidor, por idoso. A tela mostra esta cópia com as [pendencias] aplicadas por cima. */
    val remedios: Map<Int, List<MedicamentoDto>> = emptyMap(),
    val historicos: Map<Int, List<HistoricoDto>> = emptyMap(),
    /** Últimos avisos vistos de cada idoso (o familiar sem internet mostra estes, marcados como antigos). */
    val avisos: Map<Int, List<NotificacaoDto>> = emptyMap(),
    val pendencias: List<Operacao> = emptyList(),
    /** Alterações que o servidor recusou (por exemplo, o remédio foi excluído por outra pessoa): avisadas ao usuário. */
    val conflitos: List<String> = emptyList(),
    val proximoIdLocal: Int = -1,
)

/** Como os dados são gravados no aparelho. */
val jsonLocal = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
