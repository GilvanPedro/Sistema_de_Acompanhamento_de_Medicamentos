package br.com.adapter.in.web.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.adapter.in.web.auth.LimiteDeTentativas;

/** Limites de tentativas (em memória, valem para uma instância da API). */
@Configuration
class LimitesConfig {

    private static final int CAPACIDADE = 20_000;

    /** Senhas erradas por e-mail + IP: só bloqueia quem está errando, e não a dona da conta em outro lugar. */
    @Bean
    LimiteDeTentativas limiteLoginPorConta() {
        return new LimiteDeTentativas(5, Duration.ofMinutes(15), CAPACIDADE);
    }

    /** Falhas de login somadas por IP, contra quem testa muitos e-mails diferentes. */
    @Bean
    LimiteDeTentativas limiteLoginPorIp() {
        return new LimiteDeTentativas(30, Duration.ofMinutes(15), CAPACIDADE);
    }

    /** Senha atual errada ao trocar senha, e-mail ou excluir a conta. */
    @Bean
    LimiteDeTentativas limiteSenhaAtual() {
        return new LimiteDeTentativas(5, Duration.ofMinutes(15), CAPACIDADE);
    }

    /** Segredo errado na rota do agendador, por IP. */
    @Bean
    LimiteDeTentativas limiteAgendador() {
        return new LimiteDeTentativas(5, Duration.ofMinutes(15), CAPACIDADE);
    }

    /** Envios de contagens de banner por IP (o app envia em lotes, então o limite é folgado). */
    @Bean
    LimiteDeTentativas limiteMetricas() {
        return new LimiteDeTentativas(120, Duration.ofHours(1), CAPACIDADE);
    }

    /** Senha errada no painel de anúncios, por IP. */
    @Bean
    LimiteDeTentativas limitePainel() {
        return new LimiteDeTentativas(5, Duration.ofMinutes(15), CAPACIDADE);
    }

    /** Contas criadas por IP, para ninguém encher o banco gratuito. */
    @Bean
    LimiteDeTentativas limiteCadastro(@Value("${cuidamed.limite.cadastro-maximo:10}") int maximo) {
        return new LimiteDeTentativas(maximo, Duration.ofHours(1), CAPACIDADE);
    }

    /** Pedidos de "esqueci minha senha" por IP. */
    @Bean
    LimiteDeTentativas limiteEsqueciPorIp() {
        return new LimiteDeTentativas(5, Duration.ofHours(1), CAPACIDADE);
    }

    /** Pedidos de "esqueci minha senha" por e-mail: ninguém consegue encher a caixa de outra pessoa. */
    @Bean
    LimiteDeTentativas limiteEsqueciPorEmail() {
        return new LimiteDeTentativas(3, Duration.ofHours(1), CAPACIDADE);
    }

    /** Envios do formulário de nova senha, por IP. */
    @Bean
    LimiteDeTentativas limiteRedefinicao() {
        return new LimiteDeTentativas(20, Duration.ofHours(1), CAPACIDADE);
    }
}
