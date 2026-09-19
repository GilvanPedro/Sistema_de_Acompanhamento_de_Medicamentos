package br.com.config;

import br.com.adapter.out.id.GerarIdPorArquivoAdapter;
import br.com.adapter.out.id.GerarIdPostgresAdapter;
import br.com.adapter.out.notification.ConsoleNotificationAdapter;
import br.com.adapter.out.persistence.HistoricoCsvAdapter;
import br.com.adapter.out.persistence.MedicamentoCsvAdapter;
import br.com.adapter.out.persistence.UsuarioCsvAdapter;
import br.com.adapter.out.persistence.postgres.ConexaoPostgres;
import br.com.adapter.out.persistence.postgres.HistoricoPostgresAdapter;
import br.com.adapter.out.persistence.postgres.MedicamentoPostgresAdapter;
import br.com.adapter.out.persistence.postgres.UsuarioPostgresAdapter;
import br.com.adapter.out.security.BcryptSenhaAdapter;
import br.com.application.service.*;
import br.com.domain.port.out.*;

public class AppConfig {

    // Com DATABASE_URL (ou DB_URL) definida, usa o PostgreSQL; sem ela, continua nos arquivos CSV.
    private static final boolean USAR_POSTGRES = ConexaoPostgres.configurada();

    static {
        System.err.println("[CuidaMed] Persistência: "
                + (USAR_POSTGRES ? "PostgreSQL (DATABASE_URL definida)" : "arquivos CSV (DATABASE_URL não definida)"));
    }

    private static final SalvarUsuarioPort usuarioCsvAdapter = USAR_POSTGRES
            ? new UsuarioPostgresAdapter(ConexaoPostgres.dataSource()) : new UsuarioCsvAdapter();
    private static final SalvarMedicamentoPort medicamentoCsvAdapter = USAR_POSTGRES
            ? new MedicamentoPostgresAdapter(ConexaoPostgres.dataSource()) : new MedicamentoCsvAdapter();
    private static final SalvarHistoricoPort historicoCsvAdapter = USAR_POSTGRES
            ? new HistoricoPostgresAdapter(ConexaoPostgres.dataSource()) : new HistoricoCsvAdapter();
    private static final GerarIdPort gerarIdHistorico = criarGerarId("historico");
    private static final CriptografarSenhaPort criptografarSenhaPort = new BcryptSenhaAdapter();


    private static GerarIdPort criarGerarId(String nome) {
        if (USAR_POSTGRES) {
            String tabela = nome.equals("usuarios") ? "usuario" : nome.equals("medicamentos") ? "medicamento" : "historico";
            return new GerarIdPostgresAdapter(ConexaoPostgres.dataSource(), tabela);
        }
        return new GerarIdPorArquivoAdapter("arquivos/" + nome + ".csv");
    }

    public static RealizarLoginService criarRealizarLoginService() {
        return new RealizarLoginService(usuarioCsvAdapter, criptografarSenhaPort);
    }

    public static VerificarNotificacoesIdosoService criarVerificarNotificacoesIdosoService() {
        return new VerificarNotificacoesIdosoService(medicamentoCsvAdapter, historicoCsvAdapter);
    }

    public static CriarVinculoService criarCriarVinculoService() {
        return new CriarVinculoService(usuarioCsvAdapter);
    }

    public static GerenciarVinculoService criarGerenciarVinculoService() {
        return new GerenciarVinculoService(usuarioCsvAdapter);
    }

    public static RegistrarUsuarioService criarRegistrarUsuarioService() {
        GerarIdPort gerarIdUsuario = criarGerarId("usuarios");
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
        GerarIdPort gerarIdMedicamento = criarGerarId("medicamentos");
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