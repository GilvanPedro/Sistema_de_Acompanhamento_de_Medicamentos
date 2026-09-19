package br.com.cuidamed.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/**
 * Quando a API responde 401 (token de acesso vencido), troca o token de renovação por um par novo e repete o pedido.
 *
 * Como o servidor troca o token de renovação a cada uso (e derruba a sessão se um já usado aparecer de novo), só uma
 * renovação pode acontecer por vez: se dois pedidos falharem juntos, o segundo espera e usa o token que o primeiro obteve.
 */
class RenovadorDeToken(
    private val cofre: GuardaDeTokens,
    private val clienteSimples: OkHttpClient,
    private val urlRenovar: String,
    private val json: Json,
    private val aoPerderASessao: () -> Unit,
) : Authenticator {

    private val trava = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val enviado = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null // não era pedido autenticado
        if (tentativas(response) >= 2) return null

        synchronized(trava) {
            val atual = cofre.tokens() ?: return null
            if (atual.acesso != enviado) {
                // outro pedido já renovou enquanto este esperava
                return refazer(response, atual.acesso)
            }
            val novos = renovar(atual.renovacao)
            if (novos == null) {
                cofre.limpar()
                aoPerderASessao()
                return null
            }
            cofre.salvar(novos)
            return refazer(response, novos.acesso)
        }
    }

    /** Devolve o par novo, ou null se o servidor recusou (a sessão acabou). Sem internet, lança IOException e mantém a sessão. */
    private fun renovar(renovacao: String): Tokens? {
        val corpo = json.encodeToString(RefreshRequest(renovacao)).toRequestBody("application/json; charset=utf-8".toMediaType())
        val pedido = Request.Builder().url(urlRenovar).post(corpo).build()
        clienteSimples.newCall(pedido).execute().use { resposta ->
            return when {
                resposta.code == 401 || resposta.code == 403 -> null
                !resposta.isSuccessful -> throw java.io.IOException("Falha ao renovar: ${resposta.code}")
                else -> {
                    val t = json.decodeFromString<TokensResposta>(resposta.body.string())
                    Tokens(t.accessToken, t.refreshToken)
                }
            }
        }
    }

    private fun refazer(resposta: Response, acesso: String): Request =
        resposta.request.newBuilder().header("Authorization", "Bearer $acesso").build()

    private fun tentativas(resposta: Response): Int {
        var n = 1
        var anterior = resposta.priorResponse
        while (anterior != null) {
            n++
            anterior = anterior.priorResponse
        }
        return n
    }
}
