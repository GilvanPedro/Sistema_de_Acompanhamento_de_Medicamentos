package br.com.config;

import br.com.adapter.out.id.GerarIdEmMemoriaAdapter;
import br.com.application.service.RegistrarMedicamentoService;
import br.com.application.service.RegistrarUsuarioService;
import br.com.domain.port.out.GerarIdPort;

public class AppConfig {
    public static RegistrarUsuarioService criarRegistrarUsuarioService() {
        GerarIdPort gerarIdUsuario = new GerarIdEmMemoriaAdapter();
        return new RegistrarUsuarioService(gerarIdUsuario);
    }

    public static RegistrarMedicamentoService criarRegistrarMedicamentoService() {
        GerarIdPort gerarIdMedicamento = new GerarIdEmMemoriaAdapter();
        return new RegistrarMedicamentoService(gerarIdMedicamento);
    }
}
