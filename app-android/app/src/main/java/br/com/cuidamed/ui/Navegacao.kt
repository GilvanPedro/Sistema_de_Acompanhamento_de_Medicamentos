package br.com.cuidamed.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.cuidamed.data.MedicamentoDto
import br.com.cuidamed.data.UsuarioDto
import br.com.cuidamed.data.jsonDaApi
import br.com.cuidamed.ui.telas.TelaCadastro
import br.com.cuidamed.ui.telas.TelaFormMedicamento
import br.com.cuidamed.ui.telas.TelaHistorico
import br.com.cuidamed.ui.telas.TelaHomeFamiliar
import br.com.cuidamed.ui.telas.TelaHomeIdoso
import br.com.cuidamed.ui.telas.TelaIdosoDoFamiliar
import br.com.cuidamed.ui.telas.TelaInicio
import br.com.cuidamed.ui.telas.TelaLogin
import br.com.cuidamed.ui.telas.TelaMedicamentos
import br.com.cuidamed.ui.telas.TelaPerfil
import br.com.cuidamed.ui.telas.TelaPolitica
import br.com.cuidamed.ui.telas.TelaEsqueciSenha
import br.com.cuidamed.ui.telas.TelaTomeiUmRemedio
import br.com.cuidamed.ui.telas.TelaVinculos

const val REMEDIO_SALVO = "remedio_salvo"

/** Para onde cada tela pode ir. As telas não conhecem o NavController: só chamam estas funções. */
class Destinos(private val nav: NavHostController) {
    fun voltar() { nav.popBackStack() }

    /** Volta para a lista avisando o nome do remédio que acabou de ser salvo, para ela reler o que está no aparelho. */
    fun voltarComRemedioSalvo(nome: String) {
        nav.previousBackStackEntry?.savedStateHandle?.set(REMEDIO_SALVO, nome)
        nav.popBackStack()
    }
    fun entrar() = nav.navigate("login")
    fun esqueciSenha() = nav.navigate("esqueci")
    fun criarConta(tipo: String) = nav.navigate("cadastro/$tipo")
    fun medicamentos(idosoId: Int, nome: String) = nav.navigate("remedios/$idosoId/${Uri.encode(nome)}")
    fun formMedicamento(idosoId: Int, medicamentoId: Int) = nav.navigate("remedio/$idosoId/$medicamentoId")
    fun tomei(idosoId: Int) = nav.navigate("tomei/$idosoId")
    fun historico(idosoId: Int, nome: String) = nav.navigate("historico/$idosoId/${Uri.encode(nome)}")
    fun vinculos() = nav.navigate("vinculos")
    fun perfil() = nav.navigate("perfil")
    fun politica() = nav.navigate("politica")
    fun idosoDoFamiliar(idosoId: Int, nome: String) = nav.navigate("idoso/$idosoId/${Uri.encode(nome)}")
}

/** Telas de quem ainda não entrou. Quando o login dá certo, a sessão muda e o app troca para a navegação principal. */
@Composable
fun NavegacaoDeEntrada() {
    val nav = rememberNavController()
    val destinos = Destinos(nav)
    NavHost(navController = nav, startDestination = "inicio") {
        composable("inicio") { TelaInicio(destinos) }
        composable("login") { TelaLogin(destinos) }
        composable("esqueci") { TelaEsqueciSenha(destinos) }
        composable("politica") { TelaPolitica(destinos) }
        composable("cadastro/{tipo}", arguments = listOf(navArgument("tipo") { type = NavType.StringType })) {
            TelaCadastro(it.arguments!!.getString("tipo")!!, destinos)
        }
    }
}

@Composable
fun NavegacaoDoApp(usuario: UsuarioDto) {
    val nav = rememberNavController()
    val destinos = Destinos(nav)
    val idoso = navArgument("idosoId") { type = NavType.IntType }
    val nome = navArgument("nome") { type = NavType.StringType }
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            if (usuario.ehIdoso) TelaHomeIdoso(usuario, destinos) else TelaHomeFamiliar(usuario, destinos)
        }
        composable("remedios/{idosoId}/{nome}", arguments = listOf(idoso, nome)) {
            val a = it.arguments!!
            TelaMedicamentos(a.getInt("idosoId"), a.getString("nome").orEmpty(), usuario, destinos, it.savedStateHandle)
        }
        composable(
            "remedio/{idosoId}/{medicamentoId}",
            arguments = listOf(idoso, navArgument("medicamentoId") { type = NavType.IntType }),
        ) {
            val a = it.arguments!!
            TelaFormMedicamento(a.getInt("idosoId"), a.getInt("medicamentoId"), destinos)
        }
        composable("tomei/{idosoId}", arguments = listOf(idoso)) {
            TelaTomeiUmRemedio(it.arguments!!.getInt("idosoId"), destinos)
        }
        composable("historico/{idosoId}/{nome}", arguments = listOf(idoso, nome)) {
            val a = it.arguments!!
            TelaHistorico(a.getInt("idosoId"), a.getString("nome").orEmpty(), usuario, destinos)
        }
        composable("idoso/{idosoId}/{nome}", arguments = listOf(idoso, nome)) {
            val a = it.arguments!!
            TelaIdosoDoFamiliar(a.getInt("idosoId"), a.getString("nome").orEmpty(), destinos)
        }
        composable("vinculos") { TelaVinculos(usuario, destinos) }
        composable("perfil") { TelaPerfil(usuario, destinos) }
        composable("politica") { TelaPolitica(destinos) }
    }
}
