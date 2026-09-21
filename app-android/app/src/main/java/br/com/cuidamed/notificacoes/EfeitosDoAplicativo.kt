package br.com.cuidamed.notificacoes

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import br.com.cuidamed.data.EfeitosLocais
import br.com.cuidamed.data.MedicamentoDto
import br.com.cuidamed.data.UsuarioDto

/** Liga o repositório aos alarmes e às notificações do aparelho. */
class EfeitosDoAplicativo(private val contexto: Context) : EfeitosLocais {

    override fun aoEntrar(usuario: UsuarioDto) {
        Eventos.iniciar(contexto)
        if (usuario.ehIdoso) AgendadorDeLembretes.restaurar(contexto)
    }

    override fun aoSair() {
        Eventos.parar(contexto)
        RegistroDePush.esquecer(contexto)
        AgendadorDeLembretes.cancelarTudo(contexto)
        ServicoDoAlarme.pararTudo()
        EventosJaAvisados.limpar(contexto)
        Confirmacoes.limpar(contexto)
        // As notificações que já estão na gaveta são da conta que saiu: não podem ficar para a próxima pessoa ver.
        NotificationManagerCompat.from(contexto).cancelAll()
    }

    override fun aoAtualizarRemediosDoIdoso(remedios: List<MedicamentoDto>) {
        AgendadorDeLembretes.reagendar(contexto, remedios)
    }

    override fun agendarSincronizacao() {
        Sincronizacao.agendar(contexto)
    }

    override fun aoRegistrarTomada(medicamentoId: Int) {
        Confirmacoes.marcar(contexto, medicamentoId)
        Notificador.cancelar(contexto, medicamentoId)
        AgendadorDeLembretes.cancelarAtraso(contexto, medicamentoId)
        ServicoDoAlarme.parar(medicamentoId)
    }
}
