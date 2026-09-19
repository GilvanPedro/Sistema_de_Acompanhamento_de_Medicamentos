package br.com.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import br.com.adapter.in.gui.api.ApiException;
import br.com.adapter.in.gui.api.Aviso;
import br.com.adapter.in.gui.api.ClienteApi;
import br.com.adapter.in.gui.api.Conta;
import br.com.adapter.in.gui.api.Remedio;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.model.TipoNotificacao;

/**
 * O cliente HTTP da interface gráfica, contra o servidor de verdade (com portas em memória no lugar do banco):
 * tudo o que a GUI faz passa por aqui.
 */
@SpringBootTest(classes = ApiApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "cuidamed.limite.cadastro-maximo=1000")
@ActiveProfiles("test")
@Import(ConfiguracaoDeTeste.class)
class ClienteDaGuiTest {

    private static final String SENHA = "senha-forte-123";

    @LocalServerPort
    int porta;

    private ClienteApi novoCliente() {
        return new ClienteApi("http://localhost:" + porta + "/api/v1");
    }

    private static String email(String prefixo) {
        return prefixo + "." + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
    }

    @Test
    void fluxoCompletoDeIdosoEFamiliarPelaApi() {
        ClienteApi idosoApi = novoCliente();
        ClienteApi familiarApi = novoCliente();
        String emailIdoso = email("idoso");
        Conta idoso = idosoApi.cadastrar("IDOSO", "Dona Marta", emailIdoso, SENHA);
        Conta familiar = familiarApi.cadastrar("FAMILIAR", "Filho Davi", email("familiar"), SENHA);
        assertTrue(idoso.ehIdoso());
        assertFalse(familiar.ehIdoso());
        assertTrue(idosoApi.politicaAceita(), "cadastrar já registra o aceite da política");
        assertEquals("Dona Marta", idosoApi.eu().nome());

        // vínculo: o familiar pede, o idoso aceita
        assertTrue(familiarApi.pedirVinculo(emailIdoso).contains("Se houver"));
        List<br.com.adapter.in.gui.api.Pedido> pedidos = idosoApi.pedidosRecebidos();
        assertEquals(1, pedidos.size());
        assertEquals(familiar.id(), pedidos.get(0).familiar().id());
        idosoApi.aceitarPedido(familiar.id());
        assertEquals(List.of(idoso.id()), familiarApi.meusIdosos().stream().map(Conta::id).toList());
        assertEquals(List.of(familiar.id()), idosoApi.meusFamiliares().stream().map(Conta::id).toList());

        // remédios: o familiar cadastra e edita, o idoso marca que tomou
        Remedio criado = familiarApi.cadastrarRemedio(idoso.id(), "Losartana", DayOfWeek.MONDAY, LocalTime.of(8, 30), TipoMedicamento.COMPRIMIDO);
        assertEquals(LocalTime.of(8, 30), criado.horario());
        Remedio editado = familiarApi.editarRemedio(criado.id(), "Losartana 50", DayOfWeek.MONDAY, LocalTime.of(9, 0), TipoMedicamento.GOTAS);
        assertEquals("Losartana 50", editado.nome());
        assertEquals(TipoMedicamento.GOTAS, editado.tipo());
        assertEquals(1, idosoApi.remedios(idoso.id()).size());

        // um remédio de hoje, para o aviso de "hoje" existir (o dia da semana é o de hoje)
        DayOfWeek hoje = java.time.LocalDate.now().getDayOfWeek();
        Remedio dehoje = idosoApi.cadastrarRemedio(idoso.id(), "Vitamina", hoje, LocalTime.of(0, 1), TipoMedicamento.OUTRO);
        idosoApi.registrarTomada(dehoje.id());
        List<Aviso> avisos = familiarApi.avisos(idoso.id());
        assertTrue(avisos.stream().anyMatch(a -> a.tipo() == TipoNotificacao.TOMADO && a.remedio().id() == dehoje.id()));
        assertEquals(1, idosoApi.historico(idoso.id()).size());
        assertEquals("Vitamina", familiarApi.historico(idoso.id()).get(0).remedioNome());

        // o familiar não pode marcar tomada (só o idoso), e a mensagem do servidor chega à tela
        ApiException recusa = assertThrows(ApiException.class, () -> familiarApi.registrarTomada(criado.id()));
        assertEquals(403, recusa.status());
        assertFalse(recusa.getMessage().isBlank());

        familiarApi.excluirRemedio(criado.id());
        assertEquals(1, idosoApi.remedios(idoso.id()).size());

        // o idoso remove o familiar
        idosoApi.removerFamiliar(familiar.id());
        assertTrue(familiarApi.meusIdosos().isEmpty());
    }

    @Test
    void errosDaApiChegamComAMensagemEOStatus() {
        ClienteApi api = novoCliente();
        ApiException senhaErrada = assertThrows(ApiException.class, () -> api.login("naoexiste@teste.com", "qualquer-senha-1"));
        assertEquals(401, senhaErrada.status());
        assertFalse(senhaErrada.semConexao());

        // sem login, uma chamada protegida devolve 401 = sessão perdida
        assertTrue(assertThrows(ApiException.class, api::eu).sessaoPerdida());

        // senha curta no cadastro: o servidor explica
        ApiException fraca = assertThrows(ApiException.class, () -> api.cadastrar("IDOSO", "Fulano", email("fraca"), "123"));
        assertEquals(400, fraca.status());

        // servidor fora do ar: status 0, com mensagem para a pessoa
        ClienteApi foraDoAr = new ClienteApi("http://localhost:1/api/v1");
        ApiException semRede = assertThrows(ApiException.class, () -> foraDoAr.login("a@b.com", "senha-forte-123"));
        assertTrue(semRede.semConexao());
        assertTrue(semRede.getMessage().contains("conexão"));
    }

    @Test
    void oTokenVencidoEhRenovadoSozinhoEUmaSessaoPerdidaAvisa() throws Exception {
        ClienteApi api = novoCliente();
        api.cadastrar("FAMILIAR", "Tia Nena", email("nena"), SENHA);
        assertEquals("Tia Nena", api.eu().nome());

        // estraga o token de acesso: a próxima chamada leva 401, renova com o token de renovação e repete
        var campo = ClienteApi.class.getDeclaredField("acesso");
        campo.setAccessible(true);
        campo.set(api, "token-invalido");
        assertEquals("Tia Nena", api.eu().nome());

        // estraga também o de renovação: não há como continuar, a sessão está perdida
        campo.set(api, "token-invalido");
        var renovacao = ClienteApi.class.getDeclaredField("renovacao");
        renovacao.setAccessible(true);
        renovacao.set(api, "renovacao-invalida");
        assertTrue(assertThrows(ApiException.class, api::eu).sessaoPerdida());
    }

    @Test
    void privacidadeExportarEExcluirAConta() {
        ClienteApi api = novoCliente();
        Conta conta = api.cadastrar("IDOSO", "Seu Ivo", email("ivo"), SENHA);
        api.cadastrarRemedio(conta.id(), "Insulina", DayOfWeek.FRIDAY, LocalTime.of(7, 0), TipoMedicamento.INJECAO);

        String copia = api.exportarDados(SENHA);
        assertTrue(copia.contains("Insulina") && copia.contains("Seu Ivo"));
        assertFalse(copia.toLowerCase().contains("senha"));
        assertThrows(ApiException.class, () -> api.exportarDados("senha-errada-123"));

        assertTrue(api.urlDaPolitica().endsWith("/politica-de-privacidade.html"));

        api.excluirConta(SENHA);
        assertFalse(api.logado());
        assertThrows(ApiException.class, () -> novoCliente().login(conta.email(), SENHA));
    }

    @Test
    void editarPerfilExigeSenhaAtualParaTrocarASenha() {
        ClienteApi api = novoCliente();
        Conta conta = api.cadastrar("IDOSO", "Dona Lia", email("lia"), SENHA);
        assertEquals("Dona Lia Silva", api.editarPerfil("Dona Lia Silva", null, null, null).nome());
        assertEquals(403, assertThrows(ApiException.class, () -> api.editarPerfil(null, null, "nova-senha-456", "errada-123")).status());
        api.editarPerfil(null, null, "nova-senha-456", SENHA);
        // a senha antiga deixa de entrar e a nova entra
        assertEquals(401, assertThrows(ApiException.class, () -> novoCliente().login(conta.email(), SENHA)).status());
        assertEquals("Dona Lia Silva", novoCliente().login(conta.email(), "nova-senha-456").nome());
    }
}
