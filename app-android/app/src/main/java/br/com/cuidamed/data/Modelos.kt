package br.com.cuidamed.data

import kotlinx.serialization.Serializable

// Formatos JSON da API (ver ADR-0047).

@Serializable
data class UsuarioDto(val id: Int, val tipo: String, val nome: String, val email: String) {
    val ehIdoso: Boolean get() = tipo == "IDOSO"
}

@Serializable
data class LoginResposta(val accessToken: String, val refreshToken: String, val expiresIn: Long, val usuario: UsuarioDto)

@Serializable
data class TokensResposta(val accessToken: String, val refreshToken: String, val expiresIn: Long)

@Serializable
data class MedicamentoDto(
    val id: Int,
    val idosoId: Int,
    val nome: String,
    val horario: String,
    val diaSemana: String,
    val tipo: String,
)

@Serializable
data class HistoricoDto(val id: Int, val medicamentoId: Int, val medicamentoNome: String, val dataHora: String, val foiTomado: Boolean)

@Serializable
data class NotificacaoDto(val tipo: String, val medicamento: MedicamentoDto)

@Serializable
data class PedidoVinculoDto(val familiar: UsuarioDto, val solicitadoEm: String)

@Serializable
data class MensagemDto(val mensagem: String)

@Serializable
data class ErroDto(val erro: String)

// ---- corpos enviados

@Serializable
data class RegistroRequest(
    val tipo: String,
    val nome: String,
    val email: String,
    val senha: String,
    val aceitouPolitica: Boolean,
    val versaoPolitica: String,
)

@Serializable
data class ConsentimentoDto(val versaoAtual: String, val versaoAceita: String? = null)

@Serializable
data class ConsentimentoRequest(val versao: String)

@Serializable
data class LoginRequest(val email: String, val senha: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

/** Edição parcial: só os campos preenchidos são enviados. Trocar senha ou e-mail exige a senha atual. */
@Serializable
data class EditarUsuarioRequest(
    val nome: String? = null,
    val email: String? = null,
    val senha: String? = null,
    val senhaAtual: String? = null,
)

@Serializable
data class ExcluirContaRequest(val senha: String)

@Serializable
data class DispositivoRequest(val token: String)

@Serializable
data class EmailRequest(val email: String)

/** A hora em que o idoso tocou em "já tomei" (o pedido pode chegar horas depois, se estava sem internet). */
@Serializable
data class TomadaRequest(val dataHora: String)

/** Cadastro e edição parcial: na edição, os campos nulos ficam como estão. */
@Serializable
data class MedicamentoRequest(
    val nome: String? = null,
    val diaSemana: String? = null,
    val horario: String? = null,
    val tipo: String? = null,
)

val TIPOS_MEDICAMENTO = listOf("COMPRIMIDO" to "Comprimido", "GOTAS" to "Gotas", "INJECAO" to "Injeção", "OUTRO" to "Outro")

val DIAS_DA_SEMANA = listOf(
    "MONDAY" to "Segunda", "TUESDAY" to "Terça", "WEDNESDAY" to "Quarta", "THURSDAY" to "Quinta",
    "FRIDAY" to "Sexta", "SATURDAY" to "Sábado", "SUNDAY" to "Domingo",
)

fun rotuloDoTipo(tipo: String) = TIPOS_MEDICAMENTO.firstOrNull { it.first == tipo }?.second ?: tipo

fun rotuloDoDia(dia: String) = DIAS_DA_SEMANA.firstOrNull { it.first == dia }?.second ?: dia
