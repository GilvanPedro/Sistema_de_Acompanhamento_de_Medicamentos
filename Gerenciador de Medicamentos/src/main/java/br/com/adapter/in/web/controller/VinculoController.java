package br.com.adapter.in.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adapter.in.web.auth.Acesso;
import br.com.adapter.in.web.dto.Dtos.EmailRequest;
import br.com.adapter.in.web.dto.Dtos.PedidoVinculoDto;
import br.com.application.service.CriarVinculoService;
import br.com.application.service.GerenciarVinculoService;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Usuario;
import br.com.domain.port.out.SalvarUsuarioPort;
import jakarta.servlet.http.HttpServletRequest;

/** Vínculo idoso–familiar: o familiar pede, o idoso aceita (o pedido vale 24 horas). */
@RestController
@RequestMapping("/api/v1")
class VinculoController {

    private final Acesso acesso;
    private final GerenciarVinculoService gerenciar;
    private final CriarVinculoService criar;
    private final SalvarUsuarioPort usuarios;

    VinculoController(Acesso acesso, GerenciarVinculoService gerenciar, CriarVinculoService criar, SalvarUsuarioPort usuarios) {
        this.acesso = acesso;
        this.gerenciar = gerenciar;
        this.criar = criar;
        this.usuarios = usuarios;
    }

    /** Familiar pede para acompanhar um idoso, pelo e-mail dele. */
    @PostMapping("/vinculos/pedidos")
    ResponseEntity<Void> pedir(@RequestBody EmailRequest corpo, HttpServletRequest requisicao) {
        Familiar familiar = acesso.familiarLogado(requisicao);
        Usuario idoso = porEmail(corpo);
        gerenciar.solicitarVinculo(familiar.getId(), idoso.getId());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/vinculos/pedidos")
    List<PedidoVinculoDto> pedidosRecebidos(HttpServletRequest requisicao) {
        Idoso idoso = acesso.idosoLogado(requisicao);
        return gerenciar.listarPedidosPendentes(idoso.getId()).stream().map(PedidoVinculoDto::de).toList();
    }

    @PostMapping("/vinculos/pedidos/{familiarId}/aceitar")
    ResponseEntity<Void> aceitar(@PathVariable("familiarId") int familiarId, HttpServletRequest requisicao) {
        gerenciar.aceitarPedido(acesso.idosoLogado(requisicao).getId(), familiarId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/vinculos/pedidos/{familiarId}/recusar")
    ResponseEntity<Void> recusar(@PathVariable("familiarId") int familiarId, HttpServletRequest requisicao) {
        gerenciar.recusarPedido(acesso.idosoLogado(requisicao).getId(), familiarId);
        return ResponseEntity.noContent().build();
    }

    /** O idoso adiciona um familiar direto (o vínculo já nasce aceito: adicionar é o consentimento). */
    @PostMapping("/me/familiares")
    ResponseEntity<Void> adicionarFamiliar(@RequestBody EmailRequest corpo, HttpServletRequest requisicao) {
        Idoso idoso = acesso.idosoLogado(requisicao);
        Usuario familiar = porEmail(corpo);
        criar.criarVinculo(idoso.getId(), familiar.getId());
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/me/familiares/{familiarId}")
    ResponseEntity<Void> removerFamiliar(@PathVariable("familiarId") int familiarId, HttpServletRequest requisicao) {
        gerenciar.removerFamiliar(acesso.idosoLogado(requisicao).getId(), familiarId);
        return ResponseEntity.noContent().build();
    }

    private Usuario porEmail(EmailRequest corpo) {
        if (corpo == null || corpo.email() == null || corpo.email().isBlank()) {
            throw new DadosInvalidosException("Informe o e-mail.");
        }
        Usuario usuario = usuarios.buscarPorEmail(corpo.email().trim());
        if (usuario == null) {
            throw new DadosInvalidosException("Não encontramos nenhuma conta com esse e-mail.");
        }
        return usuario;
    }
}
