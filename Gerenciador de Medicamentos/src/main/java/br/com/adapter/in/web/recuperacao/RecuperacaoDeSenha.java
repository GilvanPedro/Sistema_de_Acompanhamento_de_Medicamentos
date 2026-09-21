package br.com.adapter.in.web.recuperacao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.adapter.in.web.auth.RefreshTokenStore;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.Usuario;
import br.com.domain.port.out.CriptografarSenhaPort;
import br.com.domain.port.out.SalvarUsuarioPort;
import br.com.domain.validation.ValidarDadosUsuario;

/**
 * "Esqueci minha senha": manda por e-mail um link de uso único, válido por 30 minutos, que abre uma página para a pessoa
 * escolher a senha nova. O token é aleatório (256 bits) e só o hash dele é guardado.
 */
public class RecuperacaoDeSenha {

    private static final Logger LOG = Logger.getLogger(RecuperacaoDeSenha.class.getName());
    public static final Duration VALIDADE = Duration.ofMinutes(30);

    private final SecureRandom aleatorio = new SecureRandom();
    private final SalvarUsuarioPort usuarios;
    private final CriptografarSenhaPort cripto;
    private final RedefinicoesDeSenha redefinicoes;
    private final RefreshTokenStore sessoes;
    private final EnviadorDeEmail email;
    private final Executor emSegundoPlano;
    private final String urlPublica;
    private final Clock relogio;

    public RecuperacaoDeSenha(SalvarUsuarioPort usuarios, CriptografarSenhaPort cripto, RedefinicoesDeSenha redefinicoes,
                              RefreshTokenStore sessoes, EnviadorDeEmail email, Executor emSegundoPlano, String urlPublica, Clock relogio) {
        this.usuarios = usuarios;
        this.cripto = cripto;
        this.redefinicoes = redefinicoes;
        this.sessoes = sessoes;
        this.email = email;
        this.emSegundoPlano = emSegundoPlano;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
        this.relogio = relogio;
    }

    /**
     * Manda o link se o e-mail for de uma conta. Quem chama responde igual nos dois casos, para ninguém descobrir quais
     * e-mails têm conta. O envio roda em segundo plano, para a resposta também não demorar mais quando a conta existe.
     */
    public void solicitar(String enderecoDeEmail) {
        Usuario usuario = usuarios.buscarPorEmail(enderecoDeEmail.trim());
        if (usuario == null) {
            return;
        }
        String token = novoToken();
        redefinicoes.guardar(usuario.getId(), hash(token), Instant.now(relogio).plus(VALIDADE));
        String link = urlPublica + "/redefinir-senha?token=" + token;
        enviarEmSegundoPlano(usuario.getEmail(), "CuidaMed: escolha uma nova senha",
                "Olá, " + usuario.getNome() + "!\n\n"
                        + "Recebemos um pedido para criar uma nova senha no CuidaMed.\n\n"
                        + "Para escolher a nova senha, abra este link (vale por 30 minutos e só funciona uma vez):\n"
                        + link + "\n\n"
                        + "Se não foi você que pediu, pode ignorar esta mensagem: a sua senha continua a mesma.\n\n"
                        + "CuidaMed");
    }

    /** O link ainda vale? (Só para decidir se mostra o formulário.) */
    public boolean linkValido(String token) {
        return token != null && !token.isBlank() && token.length() <= 200
                && redefinicoes.consultar(hash(token), Instant.now(relogio)).isPresent();
    }

    /**
     * Troca a senha. A senha é conferida antes de gastar o link: se ela for fraca ou as duas não forem iguais, a pessoa
     * corrige e tenta de novo com o mesmo link. Depois da troca, todas as sessões abertas da conta são encerradas.
     */
    public void redefinir(String token, String senha, String confirmacao) {
        if (senha == null || !senha.equals(confirmacao)) {
            throw new DadosInvalidosException("As duas senhas não são iguais.");
        }
        new ValidarDadosUsuario().validarSenha(senha);
        if (token == null || token.length() > 200) {
            throw linkInvalido();
        }
        int usuarioId = redefinicoes.consumir(hash(token), Instant.now(relogio)).orElseThrow(RecuperacaoDeSenha::linkInvalido);
        Usuario usuario = usuarios.buscarPorId(usuarioId);
        usuario.setSenha(cripto.criptografarSenha(senha));
        usuarios.atualizar(usuario);
        sessoes.revogarTodos(usuarioId);
        enviarEmSegundoPlano(usuario.getEmail(), "CuidaMed: sua senha foi alterada",
                "Olá, " + usuario.getNome() + "!\n\n"
                        + "A senha da sua conta no CuidaMed acabou de ser alterada.\n\n"
                        + "Se foi você, não precisa fazer nada. Se não foi, peça uma nova senha agora pelo aplicativo "
                        + "(\"Esqueci minha senha\") e avise quem cuida de você.\n\n"
                        + "CuidaMed");
    }

    private static DadosInvalidosException linkInvalido() {
        return new DadosInvalidosException("Este link não vale mais (ele dura 30 minutos e só pode ser usado uma vez). "
                + "Peça um novo em \"Esqueci minha senha\", no aplicativo.");
    }

    private void enviarEmSegundoPlano(String para, String assunto, String texto) {
        emSegundoPlano.execute(() -> {
            try {
                email.enviar(para, assunto, texto);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Não foi possível enviar o e-mail \"" + assunto + "\": " + e.getMessage());
            }
        });
    }

    private String novoToken() {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
