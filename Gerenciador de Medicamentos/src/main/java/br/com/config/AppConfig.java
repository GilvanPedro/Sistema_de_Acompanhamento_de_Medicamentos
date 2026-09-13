package br.com.config;

import br.com.adapter.out.id.GerarIdPorArquivoAdapter;
import br.com.adapter.out.persistence.HistoricoCsvAdapter;
import br.com.adapter.out.persistence.MedicamentoCsvAdapter;
import br.com.adapter.out.persistence.UsuarioCsvAdapter;
import br.com.application.service.RegistrarMedicamentoService;
import br.com.application.service.RegistrarUsuarioService;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.port.out.SalvarUsuarioPort;

public class AppConfig {

    private static final SalvarUsuarioPort usuarioCsvAdapter = new UsuarioCsvAdapter();
    private static final SalvarMedicamentoPort medicamentoCsvAdapter = new MedicamentoCsvAdapter();
    private static final SalvarHistoricoPort historicoCsvAdapter = new HistoricoCsvAdapter();
    private static final GerarIdPort gerarIdHistorico = new GerarIdPorArquivoAdapter("historico.csv");

    public static RegistrarUsuarioService criarRegistrarUsuarioService() {
        GerarIdPort gerarIdUsuario = new GerarIdPorArquivoAdapter("usuarios.csv");
        return new RegistrarUsuarioService(gerarIdUsuario, usuarioCsvAdapter);
    }

    public static RegistrarMedicamentoService criarRegistrarMedicamentoService() {
        GerarIdPort gerarIdMedicamento = new GerarIdPorArquivoAdapter("medicamentos.csv");
        return new RegistrarMedicamentoService(gerarIdMedicamento, medicamentoCsvAdapter);
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