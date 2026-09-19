package br.com.cuidamed.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Guarda os [DadosLocais] de cada pessoa num arquivo criptografado (um por pessoa). A gravação é atômica (escreve num
 * arquivo temporário e troca de nome), então uma queda no meio não corrompe o que já estava salvo. Se o arquivo não puder
 * ser lido (chave perdida, arquivo danificado), começa vazio: os dados do servidor voltam na próxima busca.
 */
class ArmazenamentoLocal(private val pasta: File, private val cifra: Cifra) {

    private val trava = Mutex()
    private val memoria = mutableMapOf<Int, DadosLocais>()

    suspend fun ler(usuarioId: Int): DadosLocais = trava.withLock { carregar(usuarioId) }

    /** Lê, altera e grava sem que outra alteração se meta no meio. Devolve o segundo valor do par, se a mudança quiser. */
    suspend fun <R> alterar(usuarioId: Int, mudanca: (DadosLocais) -> Pair<DadosLocais, R>): R = trava.withLock {
        val (novo, resultado) = mudanca(carregar(usuarioId))
        memoria[usuarioId] = novo
        gravar(usuarioId, novo)
        resultado
    }

    suspend fun apagar(usuarioId: Int) = trava.withLock {
        memoria.remove(usuarioId)
        withContext(Dispatchers.IO) { arquivo(usuarioId).delete() }
        Unit
    }

    private fun arquivo(usuarioId: Int) = File(pasta, "dados-$usuarioId.bin")

    private suspend fun carregar(usuarioId: Int): DadosLocais {
        memoria[usuarioId]?.let { return it }
        val lidos = withContext(Dispatchers.IO) {
            val arquivo = arquivo(usuarioId)
            if (!arquivo.exists()) return@withContext DadosLocais()
            try {
                jsonLocal.decodeFromString<DadosLocais>(cifra.decifrar(arquivo.readText()))
            } catch (e: Exception) {
                DadosLocais()
            }
        }
        memoria[usuarioId] = lidos
        return lidos
    }

    private suspend fun gravar(usuarioId: Int, dados: DadosLocais) = withContext(Dispatchers.IO) {
        pasta.mkdirs()
        val destino = arquivo(usuarioId)
        val temporario = File(pasta, destino.name + ".tmp")
        temporario.writeText(cifra.cifrar(jsonLocal.encodeToString(DadosLocais.serializer(), dados)))
        if (!temporario.renameTo(destino)) {
            destino.delete()
            temporario.renameTo(destino)
        }
    }
}
