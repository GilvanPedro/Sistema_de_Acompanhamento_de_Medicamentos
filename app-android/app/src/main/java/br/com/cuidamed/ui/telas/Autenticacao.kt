package br.com.cuidamed.ui.telas

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.ui.Destinos
import br.com.cuidamed.ui.LocalRepositorio
import br.com.cuidamed.ui.componentes.AvisoDaTela
import br.com.cuidamed.ui.componentes.BotaoGrande
import br.com.cuidamed.ui.componentes.CampoDeTexto
import br.com.cuidamed.ui.componentes.EstiloDoBotao
import br.com.cuidamed.ui.componentes.Mensagem
import br.com.cuidamed.ui.componentes.Tela
import br.com.cuidamed.ui.componentes.TextoSuave
import br.com.cuidamed.ui.componentes.Tom
import kotlinx.coroutines.launch

@Composable
fun TelaInicio(destinos: Destinos) {
    Tela("CuidaMed", "Acompanhe os remédios de quem você ama, ou os seus, sem esquecer nenhum.") {
        Spacer(Modifier.height(8.dp))
        BotaoGrande("Entrar", destinos::entrar)
        Spacer(Modifier.height(4.dp))
        TextoSuave("Ainda não tem conta? Crie a sua:")
        BotaoGrande("Criar conta de idoso", { destinos.criarConta("IDOSO") }, estilo = EstiloDoBotao.SECUNDARIO)
        BotaoGrande("Criar conta de familiar", { destinos.criarConta("FAMILIAR") }, estilo = EstiloDoBotao.SECUNDARIO)
    }
}

@Composable
fun TelaLogin(destinos: Destinos) {
    val repo = LocalRepositorio.current
    val escopo = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var senha by rememberSaveable { mutableStateOf("") }
    var carregando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<Mensagem?>(null) }

    fun entrar() {
        if (email.isBlank() || senha.isEmpty()) {
            mensagem = Mensagem("Digite o seu e-mail e a sua senha.", Tom.AVISO)
            return
        }
        carregando = true
        mensagem = null
        escopo.launch {
            val r = repo.entrar(email, senha)
            if (r is Resultado.Falha) {
                mensagem = Mensagem(r.mensagem, Tom.ERRO)
                carregando = false
            }
            // Se deu certo, a sessão muda e o app troca de tela sozinho.
        }
    }

    Tela("Entrar", aoVoltar = destinos::voltar) {
        AvisoDaTela(mensagem)
        CampoDeTexto(email, { email = it }, "Seu e-mail", tipoDeTeclado = KeyboardType.Email)
        CampoDeTexto(senha, { senha = it }, "Sua senha", senha = true)
        BotaoGrande("Entrar", ::entrar, carregando = carregando)
        if (carregando) TextoSuave("Entrando… se o servidor estiver descansando, pode levar até um minuto.")
    }
}

@Composable
fun TelaCadastro(tipo: String, destinos: Destinos) {
    val repo = LocalRepositorio.current
    val escopo = rememberCoroutineScope()
    val ehIdoso = tipo == "IDOSO"
    var nome by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var senha by rememberSaveable { mutableStateOf("") }
    var aceitou by rememberSaveable { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<Mensagem?>(null) }

    fun criar() {
        when {
            nome.isBlank() -> mensagem = Mensagem("Escreva o seu nome.", Tom.AVISO)
            email.isBlank() -> mensagem = Mensagem("Escreva o seu e-mail.", Tom.AVISO)
            senha.length < 8 -> mensagem = Mensagem("A senha precisa ter pelo menos 8 caracteres.", Tom.AVISO)
            !aceitou -> mensagem = Mensagem("Para criar a conta, leia e aceite a política de privacidade.", Tom.AVISO)
            else -> {
                carregando = true
                mensagem = null
                escopo.launch {
                    val r = repo.cadastrar(tipo, nome, email, senha)
                    if (r is Resultado.Falha) {
                        mensagem = Mensagem(r.mensagem, Tom.ERRO)
                        carregando = false
                    }
                }
            }
        }
    }

    Tela(
        if (ehIdoso) "Criar conta de idoso" else "Criar conta de familiar",
        if (ehIdoso) "Você vai cadastrar e acompanhar os seus remédios." else "Você vai acompanhar os remédios das pessoas que ama.",
        aoVoltar = destinos::voltar,
    ) {
        AvisoDaTela(mensagem)
        CampoDeTexto(nome, { nome = it }, "Seu nome")
        CampoDeTexto(email, { email = it }, "Seu e-mail", tipoDeTeclado = KeyboardType.Email)
        CampoDeTexto(senha, { senha = it }, "Escolha uma senha", senha = true, dica = "Pelo menos 8 caracteres.")
        CaixaDeAceite(aceitou, { aceitou = it }, destinos::politica)
        BotaoGrande("Criar minha conta", ::criar, carregando = carregando)
        if (carregando) TextoSuave("Criando a conta… se o servidor estiver descansando, pode levar até um minuto.")
    }
}
