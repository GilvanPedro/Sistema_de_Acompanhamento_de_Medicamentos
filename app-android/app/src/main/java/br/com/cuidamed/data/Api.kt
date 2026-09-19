package br.com.cuidamed.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import java.util.concurrent.TimeUnit

/** A API no Render (plano gratuito): hiberna quando fica parada, e a primeira resposta pode levar cerca de um minuto. */
const val URL_DA_API = "https://sistema-de-acompanhamento-de-medicamentos.onrender.com/api/v1/"

interface CuidaMedApi {
    /** Rota pública e leve: serve para acordar o servidor gratuito (que hiberna) antes de o usuário entrar. */
    @GET("saude")
    suspend fun saude(): Response<Unit>

    @POST("auth/registro")
    suspend fun registro(@Body corpo: RegistroRequest): UsuarioDto

    @POST("auth/login")
    suspend fun login(@Body corpo: LoginRequest): LoginResposta

    @POST("auth/sair")
    suspend fun sair(@Body corpo: RefreshRequest): Response<Unit>

    @GET("me")
    suspend fun eu(): UsuarioDto

    @PATCH("me")
    suspend fun editarEu(@Body corpo: EditarUsuarioRequest): UsuarioDto

    @HTTP(method = "DELETE", path = "me", hasBody = true)
    suspend fun excluirConta(@Body corpo: ExcluirContaRequest): Response<Unit>

    @PUT("me/dispositivos")
    suspend fun registrarDispositivo(@Body corpo: DispositivoRequest): Response<Unit>

    @HTTP(method = "DELETE", path = "me/dispositivos", hasBody = true)
    suspend fun removerDispositivo(@Body corpo: DispositivoRequest): Response<Unit>

    @GET("me/idosos")
    suspend fun meusIdosos(): List<UsuarioDto>

    @GET("me/familiares")
    suspend fun meusFamiliares(): List<UsuarioDto>

    @POST("me/familiares")
    suspend fun adicionarFamiliar(@Body corpo: EmailRequest): MensagemDto

    @DELETE("me/familiares/{id}")
    suspend fun removerFamiliar(@Path("id") familiarId: Int): Response<Unit>

    @GET("idosos/{id}/medicamentos")
    suspend fun medicamentos(@Path("id") idosoId: Int): List<MedicamentoDto>

    @POST("idosos/{id}/medicamentos")
    suspend fun cadastrarMedicamento(@Path("id") idosoId: Int, @Body corpo: MedicamentoRequest): MedicamentoDto

    /** Com chave de idempotência: reenviar o mesmo pedido devolve o remédio já criado, sem duplicar. */
    @POST("idosos/{id}/medicamentos")
    suspend fun cadastrarMedicamentoComChave(
        @Path("id") idosoId: Int,
        @Body corpo: MedicamentoRequest,
        @retrofit2.http.Header("Idempotency-Key") chave: String,
    ): MedicamentoDto

    @PATCH("medicamentos/{id}")
    suspend fun editarMedicamento(@Path("id") id: Int, @Body corpo: MedicamentoRequest): MedicamentoDto

    @DELETE("medicamentos/{id}")
    suspend fun excluirMedicamento(@Path("id") id: Int): Response<Unit>

    @POST("medicamentos/{id}/tomadas")
    suspend fun registrarTomada(@Path("id") id: Int): HistoricoDto

    @POST("medicamentos/{id}/tomadas")
    suspend fun registrarTomadaEm(@Path("id") id: Int, @Body corpo: TomadaRequest): HistoricoDto

    @GET("idosos/{id}/historico")
    suspend fun historico(@Path("id") idosoId: Int): List<HistoricoDto>

    @GET("idosos/{id}/notificacoes")
    suspend fun notificacoes(@Path("id") idosoId: Int): List<NotificacaoDto>

    @POST("vinculos/pedidos")
    suspend fun pedirVinculo(@Body corpo: EmailRequest): MensagemDto

    @GET("vinculos/pedidos")
    suspend fun pedidosRecebidos(): List<PedidoVinculoDto>

    @POST("vinculos/pedidos/{id}/aceitar")
    suspend fun aceitarPedido(@Path("id") familiarId: Int): Response<Unit>

    @POST("vinculos/pedidos/{id}/recusar")
    suspend fun recusarPedido(@Path("id") familiarId: Int): Response<Unit>
}

val jsonDaApi = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun criarApi(cofre: GuardaDeTokens, aoPerderASessao: () -> Unit): CuidaMedApi {
    // A API pode estar acordando (hiberna sem uso), então os tempos de espera são generosos.
    val simples = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val cliente = simples.newBuilder()
        .addInterceptor { corrente ->
            val pedido = corrente.request()
            val tokens = cofre.tokens()
            val precisaToken = !pedido.url.encodedPath.contains("/auth/")
            corrente.proceed(
                if (tokens != null && precisaToken) pedido.newBuilder().header("Authorization", "Bearer ${tokens.acesso}").build() else pedido
            )
        }
        .authenticator(RenovadorDeToken(cofre, simples, URL_DA_API + "auth/renovar", jsonDaApi, aoPerderASessao))
        .build()

    return Retrofit.Builder()
        .baseUrl(URL_DA_API)
        .client(cliente)
        .addConverterFactory(jsonDaApi.asConverterFactory("application/json; charset=utf-8".toMediaType()))
        .build()
        .create(CuidaMedApi::class.java)
}
