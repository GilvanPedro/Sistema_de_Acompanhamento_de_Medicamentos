package br.com.adapter.in.web.painel;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import br.com.adapter.in.web.metricas.BancoDeBanners;
import br.com.adapter.in.web.metricas.BancoDeBanners.Registro;
import br.com.adapter.in.web.metricas.CatalogoDeBanners;
import br.com.adapter.in.web.metricas.PesosDosBanners;
import br.com.adapter.in.web.metricas.ValidacaoDeBanner;
import br.com.adapter.in.web.painel.PaginasDeBanners.Preenchido;
import br.com.domain.exception.DadosInvalidosException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Cadastro dos banners pelo painel (mesma senha do painel): adicionar, editar (inclusive a imagem), pausar e remover.
 * Os formulários levam um código escondido e o servidor confere também a origem do pedido, para outro site não conseguir
 * enviar um formulário em nome de quem está logado no painel.
 */
@RestController
class GerenciadorDeBannersController {

    private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);
    private static final java.util.regex.Pattern ID_VALIDO = java.util.regex.Pattern.compile("[a-z0-9-]{1,64}");
    private static final int MAXIMO_DE_BANNERS = 50;
    /** Cada vídeo pode ter 4 MB e o banco gratuito é pequeno: limita quantos banners podem ter vídeo. */
    private static final int MAXIMO_DE_BANNERS_COM_VIDEO = 15;

    private final AcessoAoPainel acesso;
    private final BancoDeBanners banco;
    private final CatalogoDeBanners catalogo;
    private final PesosDosBanners pesos;
    private final TokenDeRelatorio tokens;
    private final br.com.adapter.in.web.metricas.MetricasDeAnuncios metricas;

    GerenciadorDeBannersController(AcessoAoPainel acesso, BancoDeBanners banco, CatalogoDeBanners catalogo,
                                   PesosDosBanners pesos, TokenDeRelatorio tokens,
                                   br.com.adapter.in.web.metricas.MetricasDeAnuncios metricas) {
        this.acesso = acesso;
        this.banco = banco;
        this.catalogo = catalogo;
        this.pesos = pesos;
        this.tokens = tokens;
        this.metricas = metricas;
    }

    @GetMapping("/painel/banners")
    ResponseEntity<String> lista(@RequestParam(value = "ok", required = false) String ok, HttpServletRequest requisicao) {
        ResponseEntity<String> negado = acesso.verificar(requisicao);
        return negado != null ? negado : pagina(HttpStatus.OK, listaHtml(ok, null, Preenchido.vazio()));
    }

    @GetMapping("/painel/banners/{id}/editar")
    ResponseEntity<String> editar(@PathVariable("id") String id, HttpServletRequest requisicao) {
        ResponseEntity<String> negado = acesso.verificar(requisicao);
        if (negado != null) {
            return negado;
        }
        return banco.buscar(id).filter(b -> ID_VALIDO.matcher(id).matches())
                .map(b -> pagina(HttpStatus.OK, PaginasDeBanners.edicao(b, tokens.paraFormularios(), null, preenchido(b))))
                .orElseGet(() -> pagina(HttpStatus.NOT_FOUND, PaginasDoPainel.naoEncontrada()));
    }

    @PostMapping(value = "/painel/banners", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<String> criar(@RequestParam(value = "csrf", required = false) String csrf,
                                 @RequestParam(value = "empresa", required = false) String empresa,
                                 @RequestParam(value = "texto", required = false) String texto,
                                 @RequestParam(value = "link", required = false) String link,
                                 @RequestParam(value = "peso", required = false) String peso,
                                 @RequestParam(value = "imagem", required = false) MultipartFile imagem,
                                 @RequestParam(value = "video", required = false) MultipartFile video,
                                 HttpServletRequest requisicao) {
        ResponseEntity<String> negado = verificarEnvio(requisicao, csrf);
        if (negado != null) {
            return negado;
        }
        Preenchido digitado = new Preenchido(nulo(empresa), nulo(texto), nulo(link), peso == null ? "1" : peso);
        try {
            String nomeDaEmpresa = ValidacaoDeBanner.empresa(empresa);
            String descricao = ValidacaoDeBanner.texto(texto, nomeDaEmpresa);
            String endereco = ValidacaoDeBanner.link(link);
            double pesoValido = ValidacaoDeBanner.peso(peso);
            byte[] bytes = bytesDe(imagem);
            var valida = ValidacaoDeBanner.imagem(bytes);
            byte[] bytesDoVideo = null;
            ValidacaoDeBanner.VideoValido videoValido = null;
            if (video != null && !video.isEmpty()) {
                bytesDoVideo = bytesDe(video);
                videoValido = ValidacaoDeBanner.video(bytesDoVideo);
                ValidacaoDeBanner.videoCombinaComAImagem(videoValido, valida.largura(), valida.altura());
                exigirEspacoParaVideo(null);
            }
            Set<String> existentes = banco.listar().stream().map(Registro::id).collect(Collectors.toSet());
            if (existentes.size() >= MAXIMO_DE_BANNERS) {
                throw new DadosInvalidosException("Já existem " + MAXIMO_DE_BANNERS + " banners. Remova algum antes de adicionar outro.");
            }
            String id = ValidacaoDeBanner.identificacao(nomeDaEmpresa);
            for (int n = 2; existentes.contains(id); n++) {
                id = ValidacaoDeBanner.identificacao(nomeDaEmpresa) + "-" + n;
            }
            metricas.apagarDoBanner(id); // números de um banner antigo com o mesmo nome não podem "ressuscitar" no novo
            banco.salvar(new Registro(id, nomeDaEmpresa, descricao, endereco, pesoValido, valida.tipo(), 0,
                    videoValido != null, videoValido == null ? 0 : videoValido.duracaoMs()), bytes, bytesDoVideo, false);
            atualizarCaches();
            return voltar("Banner de " + nomeDaEmpresa + " adicionado" + (videoValido != null ? " com vídeo" : "") + ".");
        } catch (DadosInvalidosException e) {
            return pagina(HttpStatus.BAD_REQUEST, listaHtml(null, e.getMessage(), digitado));
        }
    }

    @PostMapping(value = "/painel/banners/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<String> atualizar(@PathVariable("id") String id,
                                     @RequestParam(value = "csrf", required = false) String csrf,
                                     @RequestParam(value = "empresa", required = false) String empresa,
                                     @RequestParam(value = "texto", required = false) String texto,
                                     @RequestParam(value = "link", required = false) String link,
                                     @RequestParam(value = "peso", required = false) String peso,
                                     @RequestParam(value = "imagem", required = false) MultipartFile imagem,
                                     @RequestParam(value = "video", required = false) MultipartFile video,
                                     @RequestParam(value = "removerVideo", required = false) String removerVideo,
                                     HttpServletRequest requisicao) {
        ResponseEntity<String> negado = verificarEnvio(requisicao, csrf);
        if (negado != null) {
            return negado;
        }
        Registro atual = ID_VALIDO.matcher(id).matches() ? banco.buscar(id).orElse(null) : null;
        if (atual == null) {
            return pagina(HttpStatus.NOT_FOUND, PaginasDoPainel.naoEncontrada());
        }
        Preenchido digitado = new Preenchido(nulo(empresa), nulo(texto), nulo(link), peso == null ? "1" : peso);
        try {
            String nomeDaEmpresa = ValidacaoDeBanner.empresa(empresa);
            String descricao = ValidacaoDeBanner.texto(texto, nomeDaEmpresa);
            String endereco = ValidacaoDeBanner.link(link);
            double pesoValido = ValidacaoDeBanner.peso(peso);
            byte[] bytes = imagem == null || imagem.isEmpty() ? null : bytesDe(imagem);
            String tipo = atual.tipoImagem();
            ValidacaoDeBanner.ImagemValida medidas;
            if (bytes != null) {
                medidas = ValidacaoDeBanner.imagem(bytes);
                tipo = medidas.tipo();
            } else {
                medidas = ValidacaoDeBanner.medirImagem(banco.imagem(id).orElseThrow().bytes());
            }
            boolean tirarVideo = removerVideo != null && !removerVideo.isBlank();
            byte[] bytesDoVideo = video == null || video.isEmpty() ? null : bytesDe(video);
            ValidacaoDeBanner.VideoValido videoValido = null;
            if (bytesDoVideo != null) {
                videoValido = ValidacaoDeBanner.video(bytesDoVideo);
                ValidacaoDeBanner.videoCombinaComAImagem(videoValido, medidas.largura(), medidas.altura());
                exigirEspacoParaVideo(atual);
                tirarVideo = false;
            } else if (bytes != null && atual.temVideo() && !tirarVideo) {
                // trocou só a imagem: o vídeo que já existe precisa continuar combinando com ela
                ValidacaoDeBanner.videoCombinaComAImagem(ValidacaoDeBanner.video(banco.video(id).orElseThrow()), medidas.largura(), medidas.altura());
            }
            int duracao = videoValido != null ? videoValido.duracaoMs() : atual.duracaoVideoMs();
            banco.salvar(new Registro(id, nomeDaEmpresa, descricao, endereco, pesoValido, tipo, 0, videoValido != null || (atual.temVideo() && !tirarVideo), duracao),
                    bytes, bytesDoVideo, tirarVideo);
            atualizarCaches();
            return voltar("Banner de " + nomeDaEmpresa + " atualizado.");
        } catch (DadosInvalidosException e) {
            return pagina(HttpStatus.BAD_REQUEST, PaginasDeBanners.edicao(atual, tokens.paraFormularios(), e.getMessage(), digitado));
        }
    }

    @PostMapping("/painel/banners/{id}/remover")
    ResponseEntity<String> remover(@PathVariable("id") String id, @RequestParam(value = "csrf", required = false) String csrf,
                                   HttpServletRequest requisicao) {
        ResponseEntity<String> negado = verificarEnvio(requisicao, csrf);
        if (negado != null) {
            return negado;
        }
        boolean existia = ID_VALIDO.matcher(id).matches() && banco.remover(id);
        atualizarCaches();
        return existia ? voltar("Banner removido.") : pagina(HttpStatus.NOT_FOUND, PaginasDoPainel.naoEncontrada());
    }

    @PostMapping("/painel/banners/{id}/pausar")
    ResponseEntity<String> pausar(@PathVariable("id") String id, @RequestParam(value = "csrf", required = false) String csrf,
                                  HttpServletRequest requisicao) {
        return mudarPeso(id, csrf, requisicao, 0.0, "Banner pausado.");
    }

    @PostMapping("/painel/banners/{id}/ativar")
    ResponseEntity<String> ativar(@PathVariable("id") String id, @RequestParam(value = "csrf", required = false) String csrf,
                                  HttpServletRequest requisicao) {
        return mudarPeso(id, csrf, requisicao, 1.0, "Banner ativado com peso 1. Ajuste em Editar, se quiser outro peso.");
    }

    /** Arquivo grande demais para o servidor aceitar (o limite de envio é 5 MB por arquivo). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<String> grandeDemais() {
        return pagina(HttpStatus.PAYLOAD_TOO_LARGE, listaHtml(null, "Um dos arquivos é grande demais: a imagem pode ter até 1 MB e o vídeo até 4 MB.", Preenchido.vazio()));
    }

    // ------------------------------------------------------------------ por dentro

    private ResponseEntity<String> mudarPeso(String id, String csrf, HttpServletRequest requisicao, double novoPeso, String mensagem) {
        ResponseEntity<String> negado = verificarEnvio(requisicao, csrf);
        if (negado != null) {
            return negado;
        }
        Registro b = ID_VALIDO.matcher(id).matches() ? banco.buscar(id).orElse(null) : null;
        if (b == null) {
            return pagina(HttpStatus.NOT_FOUND, PaginasDoPainel.naoEncontrada());
        }
        banco.salvar(new Registro(id, b.empresa(), b.texto(), b.link(), novoPeso, b.tipoImagem(), 0), null);
        atualizarCaches();
        return voltar(mensagem);
    }

    /** Senha do painel, código do formulário e origem do pedido: os três precisam estar certos. */
    private ResponseEntity<String> verificarEnvio(HttpServletRequest requisicao, String csrf) {
        ResponseEntity<String> negado = acesso.verificar(requisicao);
        if (negado != null) {
            return negado;
        }
        if (!tokens.formularioConfere(csrf) || !origemEhEsteSite(requisicao)) {
            return pagina(HttpStatus.FORBIDDEN, PaginasDoPainel.naoEncontrada().replace("Relatório não encontrado", "Pedido recusado")
                    .replace("O endereço está incorreto ou não vale mais. Peça um link novo.", "Abra a página de banners de novo e tente outra vez."));
        }
        return null;
    }

    private static boolean origemEhEsteSite(HttpServletRequest requisicao) {
        String origem = requisicao.getHeader(HttpHeaders.ORIGIN);
        if (origem == null || origem.isBlank()) {
            return true; // alguns navegadores não mandam Origin em formulários do mesmo site
        }
        try {
            return requisicao.getServerName().equalsIgnoreCase(URI.create(origem).getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Dá erro se já existem banners demais com vídeo (o banner que está sendo editado, se já tem vídeo, não conta). */
    private void exigirEspacoParaVideo(Registro editado) {
        long comVideo = banco.listar().stream().filter(Registro::temVideo).filter(r -> editado == null || !r.id().equals(editado.id())).count();
        if (comVideo >= MAXIMO_DE_BANNERS_COM_VIDEO) {
            throw new DadosInvalidosException("Já existem " + MAXIMO_DE_BANNERS_COM_VIDEO + " banners com vídeo. Remova o vídeo de algum antes de acrescentar outro.");
        }
    }

    private String listaHtml(String ok, String erro, Preenchido preenchido) {
        return PaginasDeBanners.lista(banco.listar(), catalogo.somenteExemplos(), tokens.paraFormularios(), ok, erro, preenchido);
    }

    private void atualizarCaches() {
        catalogo.esquecer();
        pesos.esquecer();
    }

    private static Preenchido preenchido(Registro b) {
        return new Preenchido(b.empresa(), b.texto(), b.link() == null ? "" : b.link(), String.valueOf(b.peso()));
    }

    private static byte[] bytesDe(MultipartFile arquivo) {
        try {
            return arquivo == null ? null : arquivo.getBytes();
        } catch (IOException e) {
            throw new DadosInvalidosException("Não foi possível ler o arquivo enviado. Tente de novo.");
        }
    }

    private static String nulo(String texto) {
        return texto == null ? "" : texto;
    }

    private static ResponseEntity<String> voltar(String mensagem) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/painel/banners?ok=" + URLEncoder.encode(mensagem, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore()).build();
    }

    private static ResponseEntity<String> pagina(HttpStatus status, String html) {
        return ResponseEntity.status(status).contentType(HTML).cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow").body(html);
    }
}
