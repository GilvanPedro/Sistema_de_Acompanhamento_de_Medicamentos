package br.com.adapter.in.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.auth.Acesso;
import br.com.adapter.in.web.dto.Dtos.HistoricoDto;
import br.com.adapter.in.web.dto.Dtos.MedicamentoDto;
import br.com.adapter.in.web.dto.Dtos.MedicamentoRequest;
import br.com.adapter.in.web.dto.Dtos.NotificacaoDto;
import br.com.application.service.BuscarHistoricoPorIdosoService;
import br.com.application.service.EditarMedicamentoService;
import br.com.application.service.ExcluirMedicamentoService;
import br.com.application.service.RegistrarMedicamentoService;
import br.com.application.service.RegistrarTomadaService;
import br.com.application.service.VerificarNotificacoesIdosoService;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.port.out.SalvarMedicamentoPort;
import jakarta.servlet.http.HttpServletRequest;

/** Medicamentos, tomadas, histórico e notificações. O idoso vê o que é dele; o familiar, o dos idosos vinculados. */
@RestController
@RequestMapping("/api/v1")
class MedicamentoController {

    private final Acesso acesso;
    private final SalvarMedicamentoPort medicamentos;
    private final RegistrarMedicamentoService registrar;
    private final EditarMedicamentoService editar;
    private final ExcluirMedicamentoService excluir;
    private final RegistrarTomadaService tomada;
    private final BuscarHistoricoPorIdosoService historico;
    private final VerificarNotificacoesIdosoService notificacoes;

    MedicamentoController(Acesso acesso, SalvarMedicamentoPort medicamentos, RegistrarMedicamentoService registrar,
                          EditarMedicamentoService editar, ExcluirMedicamentoService excluir, RegistrarTomadaService tomada,
                          BuscarHistoricoPorIdosoService historico, VerificarNotificacoesIdosoService notificacoes) {
        this.acesso = acesso;
        this.medicamentos = medicamentos;
        this.registrar = registrar;
        this.editar = editar;
        this.excluir = excluir;
        this.tomada = tomada;
        this.historico = historico;
        this.notificacoes = notificacoes;
    }

    @GetMapping("/idosos/{idosoId}/medicamentos")
    List<MedicamentoDto> listar(@PathVariable("idosoId") int idosoId, HttpServletRequest requisicao) {
        Idoso idoso = acesso.idosoAcessivel(requisicao, idosoId);
        return medicamentos.listarTodos().stream()
                .filter(m -> m.getIdosoId() == idoso.getId())
                .map(MedicamentoDto::de)
                .toList();
    }

    @PostMapping("/idosos/{idosoId}/medicamentos")
    ResponseEntity<MedicamentoDto> cadastrar(@PathVariable("idosoId") int idosoId, @RequestBody MedicamentoRequest corpo,
                                             HttpServletRequest requisicao) {
        Idoso idoso = acesso.idosoAcessivel(requisicao, idosoId);
        exigirCorpo(corpo);
        Medicamento criado = registrar.registrarMedicamento(corpo.nome(), corpo.diaSemana(), corpo.horario(),
                corpo.tipo(), idoso.getId());
        return ResponseEntity.status(201).body(MedicamentoDto.de(criado));
    }

    /** Edição parcial: só os campos enviados mudam. */
    @PatchMapping("/medicamentos/{id}")
    MedicamentoDto editar(@PathVariable("id") int id, @RequestBody MedicamentoRequest corpo, HttpServletRequest requisicao) {
        exigirCorpo(corpo);
        acesso.idosoAcessivel(requisicao, medicamentos.buscarPorId(id).getIdosoId());
        return MedicamentoDto.de(editar.editarMedicamento(id, corpo.nome(), corpo.horario(), corpo.diaSemana(), corpo.tipo()));
    }

    @DeleteMapping("/medicamentos/{id}")
    ResponseEntity<Void> excluir(@PathVariable("id") int id, HttpServletRequest requisicao) {
        acesso.idosoAcessivel(requisicao, medicamentos.buscarPorId(id).getIdosoId());
        excluir.excluirMedicamento(id);
        return ResponseEntity.noContent().build();
    }

    /** O idoso marca que tomou o remédio (só ele, e só os próprios remédios). */
    @PostMapping("/medicamentos/{id}/tomadas")
    ResponseEntity<HistoricoDto> registrarTomada(@PathVariable("id") int id, HttpServletRequest requisicao) {
        Idoso idoso = acesso.idosoLogado(requisicao);
        Medicamento medicamento = medicamentos.buscarPorId(id);
        if (medicamento.getIdosoId() != idoso.getId()) {
            throw new br.com.adapter.in.web.erro.AcessoNegadoException("Esse remédio não é seu.");
        }
        return ResponseEntity.status(201).body(HistoricoDto.de(tomada.registrarTomada(idoso, medicamento, true)));
    }

    @GetMapping("/idosos/{idosoId}/historico")
    List<HistoricoDto> historico(@PathVariable("idosoId") int idosoId, HttpServletRequest requisicao) {
        Idoso idoso = acesso.idosoAcessivel(requisicao, idosoId);
        return historico.buscarHistoricoDoIdoso(idoso.getId()).stream().map(HistoricoDto::de).toList();
    }

    @GetMapping("/idosos/{idosoId}/notificacoes")
    List<NotificacaoDto> notificacoes(@PathVariable("idosoId") int idosoId, HttpServletRequest requisicao) {
        Idoso idoso = acesso.idosoAcessivel(requisicao, idosoId);
        return notificacoes.verificarNotificacoes(idoso).stream().map(NotificacaoDto::de).toList();
    }

    private static void exigirCorpo(MedicamentoRequest corpo) {
        if (corpo == null) {
            throw new DadosInvalidosException("Informe os dados do medicamento.");
        }
    }
}
