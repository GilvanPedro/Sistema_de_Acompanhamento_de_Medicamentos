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
import br.com.adapter.in.web.dto.Dtos.MensagemDto;
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
    private final br.com.adapter.in.web.push.NotificadorPush push;

    VinculoController(Acesso acesso, GerenciarVinculoService gerenciar, CriarVinculoService criar, SalvarUsuarioPort usuarios,
                       br.com.adapter.in.web.push.NotificadorPush push) {
        this.acesso = acesso;
        this.gerenciar = gerenciar;
        this.criar = criar;
        this.usuarios = usuarios;
        this.push = push;
    }

    /**
     * Familiar pede para acompanhar um idoso, pelo e-mail dele. A resposta é sempre a mesma, exista a conta ou não
     * (e já haja pedido ou não), para esta rota não servir para descobrir quem usa o sistema.
     */
    @PostMapping("/vinculos/pedidos")
    ResponseEntity<MensagemDto> pedir(@RequestBody EmailRequest corpo, HttpServletRequest requisicao) {
        Familiar familiar = acesso.familiarLogado(requisicao);
        Usuario alvo = procurar(corpo);
        if (alvo instanceof Idoso idoso) {
            try {
                gerenciar.solicitarVinculo(familiar.getId(), idoso.getId());
                push.avisarNovidade(idoso.getId());
            } catch (DadosInvalidosException e) {
                // já vinculado ou já pedido: não conta ao cliente
            }
        }
        return ResponseEntity.accepted().body(new MensagemDto("Se houver um idoso com esse e-mail, ele recebeu o pedido."));
    }

    @GetMapping("/vinculos/pedidos")
    List<PedidoVinculoDto> pedidosRecebidos(HttpServletRequest requisicao) {
        Idoso idoso = acesso.idosoLogado(requisicao);
        return gerenciar.listarPedidosPendentes(idoso.getId()).stream().map(PedidoVinculoDto::de).toList();
    }

    @PostMapping("/vinculos/pedidos/{familiarId}/aceitar")
    ResponseEntity<Void> aceitar(@PathVariable("familiarId") int familiarId, HttpServletRequest requisicao) {
        gerenciar.aceitarPedido(acesso.idosoLogado(requisicao).getId(), familiarId);
        push.avisarNovidade(familiarId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/vinculos/pedidos/{familiarId}/recusar")
    ResponseEntity<Void> recusar(@PathVariable("familiarId") int familiarId, HttpServletRequest requisicao) {
        gerenciar.recusarPedido(acesso.idosoLogado(requisicao).getId(), familiarId);
        return ResponseEntity.noContent().build();
    }

    /**
     * O idoso adiciona um familiar direto (o vínculo já nasce aceito: adicionar é o consentimento).
     * A resposta não revela se a conta existe.
     */
    @PostMapping("/me/familiares")
    ResponseEntity<MensagemDto> adicionarFamiliar(@RequestBody EmailRequest corpo, HttpServletRequest requisicao) {
        Idoso idoso = acesso.idosoLogado(requisicao);
        Usuario alvo = procurar(corpo);
        if (alvo instanceof Familiar familiar) {
            try {
                criar.criarVinculo(idoso.getId(), familiar.getId());
                push.avisarNovidade(familiar.getId());
            } catch (DadosInvalidosException e) {
                // já vinculado: não conta ao cliente
            }
        }
        return ResponseEntity.accepted().body(new MensagemDto("Se houver um familiar com esse e-mail, ele foi adicionado."));
    }

    @DeleteMapping("/me/familiares/{familiarId}")
    ResponseEntity<Void> removerFamiliar(@PathVariable("familiarId") int familiarId, HttpServletRequest requisicao) {
        gerenciar.removerFamiliar(acesso.idosoLogado(requisicao).getId(), familiarId);
        return ResponseEntity.noContent().build();
    }

    /** Procura a conta pelo e-mail; devolve null se não existir (quem chama não deve contar isso ao cliente). */
    private Usuario procurar(EmailRequest corpo) {
        if (corpo == null || corpo.email() == null || corpo.email().isBlank()) {
            throw new DadosInvalidosException("Informe o e-mail.");
        }
        return usuarios.buscarPorEmail(corpo.email().trim());
    }
}
