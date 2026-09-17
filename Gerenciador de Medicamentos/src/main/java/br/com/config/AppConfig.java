package br.com.config;

import br.com.adapter.out.id.GerarIdPorArquivoAdapter;
import br.com.adapter.out.notification.ConsoleNotificationAdapter;
import br.com.adapter.out.persistence.HistoricoCsvAdapter;
import br.com.adapter.out.persistence.MedicamentoCsvAdapter;
import br.com.adapter.out.persistence.UsuarioCsvAdapter;
import br.com.adapter.out.security.BcryptSenhaAdapter;
import br.com.application.service.*;
import br.com.domain.port.out.*;

public class AppConfig {

    private static final SalvarUsuarioPort usuarioCsvAdapter = new UsuarioCsvAdapter();
    private static final SalvarMedicamentoPort medicamentoCsvAdapter = new MedicamentoCsvAdapter();
    private static final SalvarHistoricoPort historicoCsvAdapter = new HistoricoCsvAdapter();
    private static final GerarIdPort gerarIdHistorico = new GerarIdPorArquivoAdapter("arquivos/historico.csv");
    private static final CriptografarSenhaPort criptografarSenhaPort = new BcryptSenhaAdapter();


    public static RealizarLoginService criarRealizarLoginService() {
        return new RealizarLoginService(usuarioCsvAdapter, criptografarSenhaPort);
    }

    public static VerificarNotificacoesIdosoService criarVerificarNotificacoesIdosoService() {
        return new VerificarNotificacoesIdosoService(medicamentoCsvAdapter, historicoCsvAdapter);
    }

    public static CriarVinculoService criarCriarVinculoService() {
        return new CriarVinculoService(usuarioCsvAdapter);
    }

    public static RegistrarUsuarioService criarRegistrarUsuarioService() {
        GerarIdPort gerarIdUsuario = new GerarIdPorArquivoAdapter("arquivos/usuarios.csv");
        return new RegistrarUsuarioService(gerarIdUsuario, usuarioCsvAdapter, criptografarSenhaPort);
    }

    public static RegistrarTomadaService criarRegistrarTomadaService() {
        return new RegistrarTomadaService(gerarIdHistorico, historicoCsvAdapter);
    }

    public static EditarMedicamentoService criarEditarMedicamentoService() {
        return new EditarMedicamentoService(medicamentoCsvAdapter);
    }

    public static EditarUsuarioService criarEditarUsuarioService() {
        return new EditarUsuarioService(usuarioCsvAdapter, criptografarSenhaPort);
    }

    public static VerificarAtrasoMedicamentoService criarVerificarAtrasoMedicamentoService() {
        return new VerificarAtrasoMedicamentoService(usuarioCsvAdapter, medicamentoCsvAdapter, historicoCsvAdapter, new ConsoleNotificationAdapter());
    }

    public static RegistrarMedicamentoService criarRegistrarMedicamentoService() {
        GerarIdPort gerarIdMedicamento = new GerarIdPorArquivoAdapter("arquivos/medicamentos.csv");
        return new RegistrarMedicamentoService(gerarIdMedicamento, medicamentoCsvAdapter, usuarioCsvAdapter);
    }

    public static ExcluirUsuarioService criarExcluirUsuarioService() {
        return new ExcluirUsuarioService(usuarioCsvAdapter);
    }

    public static ExcluirMedicamentoService criarExcluirMedicamentoService() {
        return new ExcluirMedicamentoService(medicamentoCsvAdapter);
    }

    public static BuscarHistoricoPorIdosoService criarBuscarHistoricoPorIdosoService() {
        return new BuscarHistoricoPorIdosoService(usuarioCsvAdapter, medicamentoCsvAdapter, historicoCsvAdapter);
    }

    public static SalvarUsuarioPort getUsuarioPort() {
        return usuarioCsvAdapter;
    }

    public static SalvarMedicamentoPort getMedicamentoPort() {
        return medicamentoCsvAdapter;
    }

    public static SalvarHistoricoPort getHistoricoPort() {
        return historicoCsvAdapter;
    }

    public static GerarIdPort getGerarIdHistorico() {
        return gerarIdHistorico;
    }
}