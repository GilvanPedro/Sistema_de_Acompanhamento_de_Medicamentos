package br.com.adapter.in.web.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.adapter.in.web.auth.LimiteDeTentativas;
import br.com.adapter.in.web.metricas.CatalogoDeBanners;
import br.com.adapter.in.web.painel.AcessoAoPainel;
import br.com.adapter.in.web.painel.SenhaDoPainel;
import br.com.adapter.in.web.painel.TokenDeRelatorio;

/** Peças do painel de anúncios que são iguais em produção e nos testes. */
@Configuration
class PainelConfig {

    @Bean
    CatalogoDeBanners catalogoDeBanners() {
        return new CatalogoDeBanners();
    }

    @Bean
    TokenDeRelatorio tokenDeRelatorio(SenhaDoPainel senha) {
        return new TokenDeRelatorio(senha);
    }

    @Bean
    AcessoAoPainel acessoAoPainel(SenhaDoPainel senha, @Qualifier("limitePainel") LimiteDeTentativas limite) {
        return new AcessoAoPainel(senha, limite);
    }
}
