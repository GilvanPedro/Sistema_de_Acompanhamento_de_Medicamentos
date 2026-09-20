package br.com.cuidamed.notificacoes

import android.content.Context
import br.com.cuidamed.CuidaMedApp
import br.com.cuidamed.data.Repositorio
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.data.UsuarioDto
import java.time.LocalDate

/**
 * A verificação de eventos: busca no servidor o que há de novo e mostra as notificações (avisos ao familiar, pedidos de
 * vínculo ao idoso) e atualiza os alarmes. Quem chama: o push do Firebase (na hora, dentro do próprio serviço, para
 * funcionar com o app fechado e o celular parado) e o trabalho periódico de 15 minutos (rede de segurança).
 */
object VerificadorDeEventos {

    /** true = concluído; false = falhou por falta de rede (vale tentar de novo mais tarde). */
    suspend fun executar(contexto: Context): Boolean {
        val app = contexto.applicationContext as CuidaMedApp
        val repositorio = app.repositorio
        if (!repositorio.temSessao()) return true
        repositorio.restaurarSessaoLocal()
        repositorio.enviarPendencias() // aproveita a verificação para mandar o que ficou na fila

        val usuario = when (val r = repositorio.eu()) {
            is Resultado.Ok -> r.valor
            is Resultado.Falha -> return r.codigo != null
        }
        if (usuario.ehIdoso) verificarIdoso(contexto, repositorio, usuario) else verificarFamiliar(contexto, repositorio)
        RegistroDePush.garantir(app) // por último: as notificações vêm antes de qualquer coisa opcional
        return true
    }

    /** O idoso: atualiza os alarmes com os remédios atuais (o familiar pode ter mudado algo) e avisa de pedidos novos. */
    private suspend fun verificarIdoso(contexto: Context, repositorio: Repositorio, usuario: UsuarioDto) {
        repositorio.medicamentos(usuario.id) // ao carregar, já reagenda os alarmes
        val pedidos = (repositorio.pedidosRecebidos() as? Resultado.Ok)?.valor ?: return
        pedidos.forEach { p ->
            val chave = "P|${p.familiar.id}|${p.solicitadoEm}"
            if (EventosJaAvisados.ehNovo(contexto, chave)) {
                Notificador.pedidoDeVinculo(contexto, chave, p.familiar.nome)
            }
        }
    }

    /** O familiar: avisa quando alguém que acompanha esqueceu ou tomou um remédio hoje. */
    private suspend fun verificarFamiliar(contexto: Context, repositorio: Repositorio) {
        val idosos = (repositorio.meusIdosos() as? Resultado.Ok)?.valor ?: return
        val hoje = LocalDate.now()
        for (idoso in idosos) {
            val avisos = (repositorio.notificacoes(idoso.id) as? Resultado.Ok)?.valor ?: continue
            for (aviso in avisos) {
                val m = aviso.medicamento
                when (aviso.tipo) {
                    "ESQUECIDO" -> {
                        val chave = "E|${idoso.id}|${m.id}|$hoje"
                        if (EventosJaAvisados.ehNovo(contexto, chave)) {
                            Notificador.avisoDeFamiliar(
                                contexto, chave, "Remédio não tomado",
                                "${idoso.nome} ainda não tomou ${m.nome}. Era para as ${m.horario}.",
                            )
                        }
                    }
                    "TOMADO" -> {
                        val chave = "T|${idoso.id}|${m.id}|$hoje"
                        if (EventosJaAvisados.ehNovo(contexto, chave)) {
                            Notificador.avisoDeFamiliar(
                                contexto, chave, "Remédio tomado", "${idoso.nome} já tomou ${m.nome}.",
                            )
                        }
                    }
                }
            }
        }
    }
}
