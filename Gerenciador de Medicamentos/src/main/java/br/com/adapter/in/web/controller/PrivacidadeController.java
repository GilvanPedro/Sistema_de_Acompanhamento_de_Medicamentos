package br.com.adapter.in.web.controller;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.auth.Acesso;
import br.com.adapter.in.web.auth.ConfirmacaoDeSenha;
import br.com.adapter.in.web.dto.Dtos.ConsentimentoRequest;
import br.com.adapter.in.web.dto.Dtos.ExcluirContaRequest;
import br.com.adapter.in.web.dto.Dtos.HistoricoDto;
import br.com.adapter.in.web.dto.Dtos.MedicamentoDto;
import br.com.adapter.in.web.privacidade.Consentimentos;
import br.com.adapter.in.web.privacidade.PoliticaDePrivacidade;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.Familiar;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.Usuario;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.port.out.SalvarMedicamentoPort;
import jakarta.servlet.http.HttpServletRequest;

/** LGPD: aceite da política de privacidade e cópia dos dados da própria conta. */
@RestController
@RequestMapping("/api/v1/me")
class PrivacidadeController {

    private final Acesso acesso;
    private final Consentimentos consentimentos;
    private final ConfirmacaoDeSenha confirmacao;
    private final SalvarMedicamentoPort medicamentos;
    private final SalvarHistoricoPort historico;

    PrivacidadeController(Acesso acesso, Consentimentos consentimentos, ConfirmacaoDeSenha confirmacao,
                          SalvarMedicamentoPort medicamentos, SalvarHistoricoPort historico) {
        this.acesso = acesso;
        this.consentimentos = consentimentos;
        this.confirmacao = confirmacao;
        this.medicamentos = medicamentos;
        this.historico = historico;
    }

    /** Qual é a versão atual da política e qual (se alguma) esta pessoa já aceitou. */
    @GetMapping("/consentimento")
    Map<String, Object> consentimento(HttpServletRequest requisicao) {
        Usuario usuario = acesso.logado(requisicao);
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("versaoAtual", PoliticaDePrivacidade.VERSAO_ATUAL);
        consentimentos.versaoAceita(usuario.getId()).ifPresent(v -> resposta.put("versaoAceita", v));
        return resposta;
    }

    /** Quem já tinha conta antes da política aceita a versão atual por aqui. */
    @PostMapping("/consentimento")
    ResponseEntity<Void> aceitar(@RequestBody ConsentimentoRequest corpo, HttpServletRequest requisicao) {
        Usuario usuario = acesso.logado(requisicao);
        if (corpo == null || !PoliticaDePrivacidade.VERSAO_ATUAL.equals(corpo.versao())) {
            throw new DadosInvalidosException("Essa versão da política não é a atual. Atualize o app.");
        }
        consentimentos.registrar(usuario.getId(), corpo.versao());
        return ResponseEntity.noContent().build();
    }

    /**
     * Cópia de tudo o que o sistema guarda sobre a conta (direito de acesso, LGPD art. 18). Pede a senha, como as outras
     * ações sensíveis. Traz só dados da própria pessoa: de outras pessoas vinculadas vai apenas o nome.
     */
    @PostMapping("/exportar")
    Map<String, Object> exportar(@RequestBody(required = false) ExcluirContaRequest corpo, HttpServletRequest requisicao) {
        Usuario usuario = acesso.logado(requisicao);
        confirmacao.exigir(usuario, corpo == null ? null : corpo.senha());

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("geradoEm", OffsetDateTime.now().toString());
        dados.put("conta", Map.of("id", usuario.getId(), "tipo", usuario instanceof Idoso ? "IDOSO" : "FAMILIAR",
                "nome", usuario.getNome(), "email", usuario.getEmail()));
        dados.put("aceitesDaPolitica", consentimentos.todos(usuario.getId()).stream()
                .map(a -> Map.of("versao", a.versao(), "aceitoEm", String.valueOf(a.aceitoEm()))).toList());

        if (usuario instanceof Idoso idoso) {
            List<Medicamento> lista = medicamentos.listarPorIdoso(idoso.getId());
            Map<Integer, Medicamento> porId = new HashMap<>();
            lista.forEach(m -> porId.put(m.getId(), m));
            dados.put("medicamentos", lista.stream().map(MedicamentoDto::de).toList());
            List<HistoricoMedicamento> tomadas = historico.listarHistoricoPorIdoso(idoso.getId(), Map.of(idoso.getId(), idoso), porId);
            dados.put("historico", tomadas.stream().map(HistoricoDto::de).toList());
            dados.put("familiaresVinculados", idoso.getFamiliares().stream().map(Familiar::getNome).toList());
        } else if (usuario instanceof Familiar familiar) {
            dados.put("idososQueAcompanha", familiar.getIdosos().stream().map(Usuario::getNome).toList());
        }
        return dados;
    }
}
