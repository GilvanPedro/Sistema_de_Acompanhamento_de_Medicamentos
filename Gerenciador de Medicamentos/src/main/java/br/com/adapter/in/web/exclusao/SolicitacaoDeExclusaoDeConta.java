package br.com.adapter.in.web.exclusao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.adapter.in.web.auth.RefreshTokenStore;
import br.com.adapter.in.web.privacidade.Consentimentos;
import br.com.adapter.in.web.push.Dispositivos;
import br.com.adapter.in.web.recuperacao.EnviadorDeEmail;
import br.com.adapter.in.web.recuperacao.RedefinicoesDeSenha;
import br.com.application.service.ExcluirUsuarioService;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.Usuario;
import br.com.domain.port.out.SalvarUsuarioPort;

/**
 * "Excluir minha conta", pedida numa página pública, sem precisar abrir o app nem lembrar a senha: quem perdeu o
 * acesso à conta também precisa conseguir apagar os próprios dados (LGPD, art. 18, VI). Manda por e-mail um link de
 * uso único, válido por 30 minutos; abrir o link mostra uma confirmação antes de apagar de verdade.
 */
public class SolicitacaoDeExclusaoDeConta {

    private static final Logger LOG = Logger.getLogger(SolicitacaoDeExclusaoDeConta.class.getName());
    public static final Duration VALIDADE = Duration.ofMinutes(30);

    /** Nome e e-mail de quando o pedido foi feito (a exclusão apaga esses dados; usados para a confirmação e o e-mail final). */
    public record Confirmacao(String nome, String email) {
    }

    private final SecureRandom aleatorio = new SecureRandom();
    private final SalvarUsuarioPort usuarios;
    private final ExcluirUsuarioService excluirUsuario;
    private final ExclusoesDeConta exclusoes;
    private final RedefinicoesDeSenha redefinicoesDeSenha;
    private final RefreshTokenStore sessoes;
    private final Dispositivos dispositivos;
    private final Consentimentos consentimentos;
    private final EnviadorDeEmail email;
    private final Executor emSegundoPlano;
    private final String urlPublica;
    private final Clock relogio;

    public SolicitacaoDeExclusaoDeConta(SalvarUsuarioPort usuarios, ExcluirUsuarioService excluirUsuario, ExclusoesDeConta exclusoes,
                                        RedefinicoesDeSenha redefinicoesDeSenha, RefreshTokenStore sessoes, Dispositivos dispositivos,
                                        Consentimentos consentimentos, EnviadorDeEmail email, Executor emSegundoPlano,
                                        String urlPublica, Clock relogio) {
        this.usuarios = usuarios;
        this.excluirUsuario = excluirUsuario;
        this.exclusoes = exclusoes;
        this.redefinicoesDeSenha = redefinicoesDeSenha;
        this.sessoes = sessoes;
        this.dispositivos = dispositivos;
        this.consentimentos = consentimentos;
        this.email = email;
        this.emSegundoPlano = emSegundoPlano;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
        this.relogio = relogio;
    }

    /** Manda o link se o e-mail for de uma conta; responde igual nos dois casos (quem chama não revela quais e-mails têm conta). */
    public void solicitar(String enderecoDeEmail) {
        Usuario usuario = usuarios.buscarPorEmail(enderecoDeEmail.trim());
        if (usuario == null) {
            return;
        }
        String token = novoToken();
        exclusoes.guardar(usuario.getId(), hash(token), Instant.now(relogio).plus(VALIDADE));
        String link = urlPublica + "/excluir-conta?token=" + token;
        enviarEmSegundoPlano(usuario.getEmail(), "CuidaMed: confirme a exclusão da sua conta",
                "Olá, " + usuario.getNome() + "!\n\n"
                        + "Recebemos um pedido para excluir a sua conta no CuidaMed e apagar os seus dados.\n\n"
                        + "Isso é IRREVERSÍVEL: os remédios, o histórico e os vínculos são apagados para sempre.\n\n"
                        + "Para confirmar, abra este link (vale por 30 minutos e só funciona uma vez):\n"
                        + link + "\n\n"
                        + "Se não foi você que pediu, ignore esta mensagem: a sua conta continua exatamente como está.\n\n"
                        + "CuidaMed");
    }

    /** O nome e o e-mail de quem seria excluído, só para mostrar na confirmação; não gasta o link. */
    public Optional<Confirmacao> dadosParaConfirmar(String token) {
        if (token == null || token.isBlank() || token.length() > 200) {
            return Optional.empty();
        }
        return exclusoes.consultar(hash(token), Instant.now(relogio)).map(usuarios::buscarPorId)
                .map(u -> new Confirmacao(u.getNome(), u.getEmail()));
    }

    /** Apaga a conta de verdade. Gasta o link primeiro (uma corrida entre dois cliques só exclui uma vez). */
    public Confirmacao excluir(String token) {
        int usuarioId = exclusoes.consumir(hash(token), Instant.now(relogio)).orElseThrow(SolicitacaoDeExclusaoDeConta::linkInvalido);
        Usuario usuario = usuarios.buscarPorId(usuarioId);
        String nome = usuario.getNome();
        String emailAntigo = usuario.getEmail();

        sessoes.revogarTodos(usuarioId);
        dispositivos.removerTodos(usuarioId);
        consentimentos.removerDe(usuarioId);
        redefinicoesDeSenha.removerDe(usuarioId);
        exclusoes.removerDe(usuarioId);
        excluirUsuario.excluirUsuario(usuarioId);

        enviarEmSegundoPlano(emailAntigo, "CuidaMed: sua conta foi excluída",
                "Olá, " + nome + "!\n\n"
                        + "A sua conta e os seus dados no CuidaMed foram excluídos, como você pediu.\n\n"
                        + "Se não foi você, escreva para gilvanpedro2006@gmail.com o quanto antes.\n\n"
                        + "CuidaMed");
        return new Confirmacao(nome, emailAntigo);
    }

    private static DadosInvalidosException linkInvalido() {
        return new DadosInvalidosException("Este link não vale mais (ele dura 30 minutos e só pode ser usado uma vez). "
                + "Peça um novo em \"Excluir minha conta\".");
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
