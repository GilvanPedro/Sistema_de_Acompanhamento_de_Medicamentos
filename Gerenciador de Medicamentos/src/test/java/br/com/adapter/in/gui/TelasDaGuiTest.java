package br.com.adapter.in.gui;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.com.adapter.in.gui.api.Aviso;
import br.com.adapter.in.gui.api.Conta;
import br.com.adapter.in.gui.api.Pedido;
import br.com.adapter.in.gui.api.Remedio;
import br.com.adapter.in.gui.api.Tomada;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.model.TipoNotificacao;

/**
 * Monta cada tela com dados de exemplo (sem abrir janela nem falar com o servidor) para garantir que nenhuma quebra ao
 * ser construída. Com -Dcuidamed.fotos=PASTA também desenha cada tela num PNG, para conferir o visual.
 */
class TelasDaGuiTest {

    private static final Conta IDOSO = new Conta(1, "IDOSO", "Dona Maria Souza", "maria@exemplo.com");
    private static final Conta FAMILIAR = new Conta(2, "FAMILIAR", "João Souza", "joao@exemplo.com");
    private static final Remedio LOSARTANA = new Remedio(10, 1, "Losartana", LocalTime.of(8, 0), DayOfWeek.MONDAY, TipoMedicamento.COMPRIMIDO);
    private static final Remedio GOTAS = new Remedio(11, 1, "Colírio", LocalTime.of(21, 30), DayOfWeek.FRIDAY, TipoMedicamento.GOTAS);

    private Navegador nav;

    @BeforeAll
    static void semJanela() {
        System.setProperty("java.awt.headless", "true");
    }

    private Navegador navegadorCom(Conta usuario) throws Exception {
        Navegador n = new Navegador();
        var campo = Navegador.class.getDeclaredField("usuario");
        campo.setAccessible(true);
        campo.set(n, usuario);
        return n;
    }

    private void desenhar(String nome, Pagina pagina) throws Exception {
        assertNotNull(pagina);
        String pasta = System.getProperty("cuidamed.fotos");
        Tema.aplicar(pagina);
        pagina.setSize(new Dimension(1000, 900));
        pagina.doLayout();
        forcarLayout(pagina);
        if (pasta != null) {
            BufferedImage img = new BufferedImage(1000, 900, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            pagina.paint(g);
            g.dispose();
            ImageIO.write(img, "png", new File(pasta, nome + ".png"));
        }
    }

    private static void forcarLayout(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component f : c.getComponents()) {
            if (f instanceof java.awt.Container filho) {
                forcarLayout(filho);
            }
        }
    }

    @Test
    void todasAsTelasSaoConstruidasComDadosDeExemplo() throws Exception {
        nav = navegadorCom(IDOSO);
        Runnable voltar = () -> { };
        java.util.function.Consumer<String> aoVoltar = m -> { };

        desenhar("01-inicio", new TelaInicio(nav));
        desenhar("02-cadastro", new TelaCadastro(nav, true));
        desenhar("03-aceite", new TelaAceiteDaPolitica(nav));

        var avisos = List.of(
                new Aviso(TipoNotificacao.ESQUECIDO, LOSARTANA),
                new Aviso(TipoNotificacao.LEMBRETE, GOTAS),
                new Aviso(TipoNotificacao.TOMADO, new Remedio(12, 1, "Vitamina D", LocalTime.of(7, 0), DayOfWeek.MONDAY, TipoMedicamento.OUTRO)));
        desenhar("04-home-idoso", new TelaHomeIdoso(nav, new TelaHomeIdoso.Dados(IDOSO, avisos, 1)));

        Navegador navFamiliar = navegadorCom(FAMILIAR);
        desenhar("05-home-familiar", new TelaHomeFamiliar(navFamiliar,
                new TelaHomeFamiliar.Dados(FAMILIAR, List.of(IDOSO), Map.of(IDOSO.id(), avisos))));
        desenhar("06-home-familiar-vazia", new TelaHomeFamiliar(navFamiliar,
                new TelaHomeFamiliar.Dados(FAMILIAR, List.of(), Map.of())));

        desenhar("07-medicamentos", new TelaMedicamentos(nav, IDOSO, List.of(LOSARTANA, GOTAS), voltar, "Losartana foi salvo."));
        desenhar("08-form-novo", new TelaFormMedicamento(nav, IDOSO, null, aoVoltar));
        desenhar("09-form-editar", new TelaFormMedicamento(nav, IDOSO, GOTAS, aoVoltar));

        var historico = List.of(
                new Tomada(1, 10, "Losartana", LocalDateTime.of(2026, 9, 21, 8, 5), true),
                new Tomada(2, 11, "Colírio", LocalDateTime.of(2026, 9, 20, 21, 30), false));
        desenhar("10-historico", new TelaHistorico(IDOSO, historico, voltar));

        desenhar("10b-marcar-tomado", new TelaMarcarTomado(nav, IDOSO, List.of(LOSARTANA, GOTAS), voltar));
        desenhar("10c-idoso-do-familiar", new TelaIdosoDoFamiliar(navFamiliar, IDOSO, avisos, voltar));
        desenhar("11-perfil", new TelaPerfil(nav, IDOSO, voltar));
        desenhar("12-vinculo-idoso", new TelaVinculo(nav, IDOSO,
                new TelaVinculo.Dados(List.of(new Pedido(FAMILIAR, LocalDateTime.of(2026, 9, 21, 9, 0))), List.of(FAMILIAR)), voltar, null));
        desenhar("13-vinculo-familiar", new TelaVinculo(navFamiliar, FAMILIAR,
                new TelaVinculo.Dados(List.of(), List.of(IDOSO)), voltar, "Pedido enviado."));
    }
}
