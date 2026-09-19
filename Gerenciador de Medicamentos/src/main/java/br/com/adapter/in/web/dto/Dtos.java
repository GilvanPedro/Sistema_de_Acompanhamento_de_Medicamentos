package br.com.adapter.in.web.dto;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import br.com.adapter.in.web.auth.TokenService.Tokens;
import br.com.domain.model.Familiar;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.NotificacaoMedicamento;
import br.com.domain.model.PedidoVinculo;
import br.com.domain.model.TipoMedicamento;
import br.com.domain.model.Usuario;

/** Formatos JSON da API. A senha (nem o hash) nunca sai daqui. */
public final class Dtos {

    private Dtos() {
    }

    // ---- entrada
    public record RegistroRequest(String tipo, String nome, String email, String senha) { }

    public record LoginRequest(String email, String senha) { }

    public record RefreshRequest(String refreshToken) { }

    /** {@code senhaAtual} é obrigatória para trocar a senha ou o e-mail. */
    public record EditarUsuarioRequest(String nome, String email, String senha, String senhaAtual) { }

    public record ExcluirContaRequest(String senha) { }

    public record MensagemDto(String mensagem) { }

    public record MedicamentoRequest(String nome, DayOfWeek diaSemana, LocalTime horario, TipoMedicamento tipo) { }

    public record EmailRequest(String email) { }

    // ---- saída
    public record UsuarioDto(int id, String tipo, String nome, String email) {
        public static UsuarioDto de(Usuario u) {
            String tipo = u instanceof Idoso ? "IDOSO" : "FAMILIAR";
            return new UsuarioDto(u.getId(), tipo, u.getNome(), u.getEmail());
        }
    }

    public record LoginResponse(String accessToken, String refreshToken, long expiresIn, UsuarioDto usuario) {
        public static LoginResponse de(Tokens tokens, Usuario usuario) {
            return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(), UsuarioDto.de(usuario));
        }
    }

    public record MedicamentoDto(int id, int idosoId, String nome, String horario, DayOfWeek diaSemana, TipoMedicamento tipo) {
        public static MedicamentoDto de(Medicamento m) {
            return new MedicamentoDto(m.getId(), m.getIdosoId(), m.getNome(), m.getHorarioMedicamento().toString(),
                    m.getDiaSemana(), m.getTipoMedicamento());
        }
    }

    public record HistoricoDto(int id, int medicamentoId, String medicamentoNome, LocalDateTime dataHora, boolean foiTomado) {
        public static HistoricoDto de(HistoricoMedicamento h) {
            return new HistoricoDto(h.getId(), h.getMedicamento().getId(), h.getMedicamento().getNome(),
                    h.getDataHoraTomada(), h.isFoiTomado());
        }
    }

    public record NotificacaoDto(String tipo, MedicamentoDto medicamento) {
        public static NotificacaoDto de(NotificacaoMedicamento n) {
            return new NotificacaoDto(n.getTipo().name(), MedicamentoDto.de(n.getMedicamento()));
        }
    }

    public record PedidoVinculoDto(UsuarioDto familiar, LocalDateTime solicitadoEm) {
        public static PedidoVinculoDto de(PedidoVinculo p) {
            Familiar f = p.getFamiliar();
            return new PedidoVinculoDto(UsuarioDto.de(f), p.getSolicitadoEm());
        }
    }
}
