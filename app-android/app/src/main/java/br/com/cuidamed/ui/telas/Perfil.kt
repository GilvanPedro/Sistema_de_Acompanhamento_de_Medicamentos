package br.com.cuidamed.ui.telas

import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.cuidamed.data.EditarUsuarioRequest
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.data.UsuarioDto
import br.com.cuidamed.notificacoes.abrirConfiguracoesDeBateria
import br.com.cuidamed.notificacoes.abrirConfiguracoesDeNotificacao
import br.com.cuidamed.ui.Destinos
import br.com.cuidamed.ui.LocalRepositorio
import br.com.cuidamed.ui.componentes.AvisoDaTela
import br.com.cuidamed.ui.componentes.BotaoGrande
import br.com.cuidamed.ui.componentes.CampoDeTexto
import br.com.cuidamed.ui.componentes.Confirmacao
import br.com.cuidamed.ui.componentes.Cartao
import br.com.cuidamed.ui.componentes.EstiloDoBotao
import br.com.cuidamed.ui.componentes.Mensagem
import br.com.cuidamed.ui.componentes.Secao
import br.com.cuidamed.ui.componentes.Tela
import br.com.cuidamed.ui.componentes.TextoSuave
import br.com.cuidamed.ui.componentes.Tom
import kotlinx.coroutines.launch

/**
 * Editar nome, e-mail e senha (só o que mudar é enviado), sair da conta e excluir a conta.
 * Trocar a senha ou o e-mail e excluir a conta pedem a senha atual: o servidor exige isso para proteger a conta.
 */
@Composable
fun TelaPerfil(usuario: UsuarioDto, destinos: Destinos) {
    val repo = LocalRepositorio.current
    val escopo = rememberCoroutineScope()
    val contexto = LocalContext.current
    var nome by rememberSaveable { mutableStateOf(usuario.nome) }
    var email by rememberSaveable { mutableStateOf(usuario.email) }
    var novaSenha by rememberSaveable { mutableStateOf("") }
    var senhaAtual by rememberSaveable { mutableStateOf("") }
    var salvando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<Mensagem?>(null) }
    var confirmandoExclusao by rememberSaveable { mutableStateOf(false) }
    var senhaDaExclusao by rememberSaveable { mutableStateOf("") }
    var excluindo by remember { mutableStateOf(false) }
    val estadoDeEnvio by repo.sincronizacao.collectAsStateWithLifecycle()
    var confirmandoSaida by remember { mutableStateOf(false) }

    fun salvar() {
        val novoNome = nome.trim().takeIf { it != usuario.nome }
        val novoEmail = email.trim().takeIf { !it.equals(usuario.email, ignoreCase = true) }
        val senha = novaSenha.takeIf { it.isNotEmpty() }
        when {
            novoNome == null && novoEmail == null && senha == null -> mensagem = Mensagem("Você não mudou nada.", Tom.AVISO)
            senha != null && senha.length < 8 -> mensagem = Mensagem("A nova senha precisa ter pelo menos 8 caracteres.", Tom.AVISO)
            (senha != null || novoEmail != null) && senhaAtual.isEmpty() ->
                mensagem = Mensagem("Para trocar o e-mail ou a senha, digite a sua senha atual.", Tom.AVISO)
            else -> {
                salvando = true
                mensagem = null
                escopo.launch {
                    val pedido = EditarUsuarioRequest(novoNome, novoEmail, senha, if (senha != null || novoEmail != null) senhaAtual else null)
                    when (val r = repo.editarPerfil(pedido)) {
                        is Resultado.Ok -> {
                            senhaAtual = ""
                            novaSenha = ""
                            if (senha != null) {
                                // O servidor encerra todas as sessões ao trocar a senha: é preciso entrar de novo.
                                Toast.makeText(contexto, "Senha alterada. Entre de novo com a nova senha.", Toast.LENGTH_LONG).show()
                                repo.sair()
                            } else {
                                mensagem = Mensagem("Seus dados foram atualizados.", Tom.OK)
                            }
                        }
                        is Resultado.Falha -> mensagem = Mensagem(r.mensagem, Tom.ERRO)
                    }
                    salvando = false
                }
            }
        }
    }

    Tela("Meus dados", "Mude só o que precisar. O resto fica como está.", destinos::voltar) {
        AvisoDaTela(mensagem)
        CampoDeTexto(nome, { nome = it }, "Nome")
        CampoDeTexto(email, { email = it }, "E-mail", tipoDeTeclado = KeyboardType.Email)
        CampoDeTexto(novaSenha, { novaSenha = it }, "Nova senha", senha = true, dica = "Deixe em branco para manter a senha atual.")
        CampoDeTexto(
            senhaAtual, { senhaAtual = it }, "Sua senha atual", senha = true,
            dica = "Só é pedida para trocar o e-mail ou a senha.",
        )
        BotaoGrande("Salvar", ::salvar, carregando = salvando)

        Secao("Lembretes e notificações")
        TextoSuave("Para os lembretes tocarem sempre, deixe as notificações ligadas e a bateria do CuidaMed sem restrições.")
        BotaoGrande("Configurações de notificação", { abrirConfiguracoesDeNotificacao(contexto) }, estilo = EstiloDoBotao.SECUNDARIO)
        BotaoGrande("Bateria sem restrições", { abrirConfiguracoesDeBateria(contexto) }, estilo = EstiloDoBotao.SECUNDARIO)

        Secao("Sair")
        BotaoGrande("Sair da conta", {
            if (estadoDeEnvio.pendentes > 0) confirmandoSaida = true else escopo.launch { repo.sair() }
        }, estilo = EstiloDoBotao.SECUNDARIO)

        Secao("Excluir minha conta")
        Cartao(Tom.ERRO) {
            Text(
                "Isso apaga para sempre os seus remédios, o histórico e os vínculos. Não dá para desfazer.",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!confirmandoExclusao) {
                BotaoGrande("Excluir minha conta", { confirmandoExclusao = true }, estilo = EstiloDoBotao.PERIGO)
            } else {
                CampoDeTexto(senhaDaExclusao, { senhaDaExclusao = it }, "Digite a sua senha para confirmar", senha = true)
                BotaoGrande("Sim, excluir para sempre", {
                    if (senhaDaExclusao.isEmpty()) {
                        mensagem = Mensagem("Digite a sua senha para confirmar.", Tom.AVISO)
                        return@BotaoGrande
                    }
                    excluindo = true
                    escopo.launch {
                        when (val r = repo.excluirConta(senhaDaExclusao)) {
                            is Resultado.Ok -> Toast.makeText(contexto, "Sua conta foi excluída.", Toast.LENGTH_LONG).show()
                            is Resultado.Falha -> {
                                mensagem = Mensagem(r.mensagem, Tom.ERRO)
                                excluindo = false
                            }
                        }
                    }
                }, estilo = EstiloDoBotao.PERIGO, carregando = excluindo)
                BotaoGrande("Não, manter a minha conta", { confirmandoExclusao = false; senhaDaExclusao = "" })
            }
        }
        TextoSuave("Versão 1.0")
    }

    if (confirmandoSaida) {
        val n = estadoDeEnvio.pendentes
        Confirmacao(
            titulo = "Sair mesmo assim?",
            mensagem = (if (n == 1) "Há 1 alteração" else "Há $n alterações") +
                " que ainda não foi enviada por falta de internet. Se você sair agora, ela será perdida.",
            textoSim = "Sair e perder",
            textoNao = "Não, ficar",
            perigoso = true,
            aoCancelar = { confirmandoSaida = false },
            aoConfirmar = {
                confirmandoSaida = false
                escopo.launch { repo.sair() }
            },
        )
    }
}
