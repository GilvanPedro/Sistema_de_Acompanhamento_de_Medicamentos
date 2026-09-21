package br.com.adapter.in.web.metricas;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.auth.LimiteDeTentativas;
import br.com.domain.exception.DadosInvalidosException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Recebe do app as contagens dos banners (quantas vezes apareceram e foram tocados). Não exige login (a tela de entrada
 * também tem banner) e não guarda nada de quem enviou: só soma nas contagens do dia. Eventos de banner ou posição
 * desconhecidos são ignorados, e há um limite de envios por IP.
 */
@RestController
@RequestMapping("/api/v1/anuncios")
class EventosDeAnunciosController {

    static final Set<String> POSICOES = Set.of("entrada-fim", "home-topo", "home-fim", "idoso-fim", "remedios-fim",
            "perfil-topo", "vinculos-fim");
    static final Set<String> PERFIS = Set.of("IDOSO", "FAMILIAR", "VISITANTE");
    private static final int MAXIMO_DE_EVENTOS = 200;
    private static final int MAXIMO_POR_EVENTO = 100;
    private static final int DIAS_DE_ATRASO_ACEITOS = 35;

    public record EventoDto(String tipo, String anuncioId, String posicao, String perfil, String dia, int quantidade) { }

    public record EventosRequest(List<EventoDto> eventos) { }

    private final MetricasDeAnuncios metricas;
    private final CatalogoDeBanners catalogo;
    private final LimiteDeTentativas limite;

    EventosDeAnunciosController(MetricasDeAnuncios metricas, CatalogoDeBanners catalogo,
                                @Qualifier("limiteMetricas") LimiteDeTentativas limite) {
        this.metricas = metricas;
        this.catalogo = catalogo;
        this.limite = limite;
    }

    @PostMapping("/eventos")
    ResponseEntity<Void> receber(@RequestBody EventosRequest corpo, HttpServletRequest requisicao) {
        String ip = requisicao.getRemoteAddr();
        limite.verificar(ip);
        limite.registrar(ip);
        if (corpo == null || corpo.eventos() == null || corpo.eventos().size() > MAXIMO_DE_EVENTOS) {
            throw new DadosInvalidosException("Envie de 1 a " + MAXIMO_DE_EVENTOS + " eventos.");
        }
        LocalDate hoje = LocalDate.now();
        for (EventoDto e : corpo.eventos()) {
            if (e == null || e.anuncioId() == null || catalogo.buscar(e.anuncioId()).isEmpty()
                    || !POSICOES.contains(e.posicao()) || e.quantidade() < 1 || e.quantidade() > MAXIMO_POR_EVENTO) {
                continue; // banner que não existe, posição estranha ou quantidade fora do razoável: ignora
            }
            boolean exibicao = "EXIBICAO".equals(e.tipo());
            if (!exibicao && !"CLIQUE".equals(e.tipo())) {
                continue;
            }
            String perfil = PERFIS.contains(e.perfil()) ? e.perfil() : "VISITANTE";
            metricas.somar(dia(e.dia(), hoje), e.anuncioId(), e.posicao(), perfil,
                    exibicao ? e.quantidade() : 0, exibicao ? 0 : e.quantidade());
        }
        return ResponseEntity.noContent().build();
    }

    /** O dia em que o evento aconteceu (o app pode enviar depois, se estava sem internet); fora do razoável vale hoje. */
    private static LocalDate dia(String texto, LocalDate hoje) {
        try {
            LocalDate dia = LocalDate.parse(texto);
            return dia.isBefore(hoje.minusDays(DIAS_DE_ATRASO_ACEITOS)) || dia.isAfter(hoje) ? hoje : dia;
        } catch (RuntimeException e) {
            return hoje;
        }
    }
}
