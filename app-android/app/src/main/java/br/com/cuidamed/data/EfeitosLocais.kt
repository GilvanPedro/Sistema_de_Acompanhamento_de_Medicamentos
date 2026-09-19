package br.com.cuidamed.data

/**
 * O que o app faz no aparelho quando algo acontece (alarmes, notificações). O repositório só avisa por aqui,
 * sem conhecer Android; quem implementa é a camada de notificações.
 */
interface EfeitosLocais {
    fun aoEntrar(usuario: UsuarioDto)
    fun aoSair()

    /** Chegou a lista atual de remédios do próprio idoso: é a base dos alarmes de lembrete. */
    fun aoAtualizarRemediosDoIdoso(remedios: List<MedicamentoDto>)

    fun aoRegistrarTomada(medicamentoId: Int)

    /** Há alterações na fila: garante que serão enviadas assim que houver internet, mesmo com o app fechado. */
    fun agendarSincronizacao()
}
