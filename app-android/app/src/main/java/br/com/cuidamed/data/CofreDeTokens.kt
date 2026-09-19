package br.com.cuidamed.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Tokens(val acesso: String, val renovacao: String)

/**
 * Guarda os tokens de login criptografados (AES-GCM) com uma chave que fica no Android Keystore e nunca sai do aparelho.
 * Se a chave sumir (por exemplo, depois de restaurar de backup ou trocar o bloqueio de tela), a leitura falha e o app
 * simplesmente pede o login de novo.
 */
/** Onde ficam os tokens de login. Existe como interface para os testes usarem uma versão em memória. */
interface GuardaDeTokens {
    fun tokens(): Tokens?
    fun salvar(tokens: Tokens)
    fun limpar()
}

class CofreDeTokens(contexto: Context) : GuardaDeTokens {

    private val prefs = contexto.getSharedPreferences("cofre", Context.MODE_PRIVATE)

    @Volatile
    private var emMemoria: Tokens? = lerDoDisco()

    override fun tokens(): Tokens? = emMemoria

    @Synchronized
    override fun salvar(tokens: Tokens) {
        emMemoria = tokens
        val texto = tokens.acesso + SEPARADOR + tokens.renovacao
        prefs.edit().putString(CHAVE_PREFS, criptografar(texto)).apply()
    }

    @Synchronized
    override fun limpar() {
        emMemoria = null
        prefs.edit().remove(CHAVE_PREFS).apply()
    }

    private fun lerDoDisco(): Tokens? {
        val guardado = prefs.getString(CHAVE_PREFS, null) ?: return null
        return try {
            val partes = descriptografar(guardado).split(SEPARADOR)
            if (partes.size == 2) Tokens(partes[0], partes[1]) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun chave(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gerador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gerador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gerador.generateKey()
    }

    private fun criptografar(texto: String): String {
        val cifra = Cipher.getInstance(TRANSFORMACAO)
        cifra.init(Cipher.ENCRYPT_MODE, chave())
        val resultado = cifra.iv + cifra.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(resultado, Base64.NO_WRAP)
    }

    private fun descriptografar(base64: String): String {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, TAMANHO_IV)
        val cifra = Cipher.getInstance(TRANSFORMACAO)
        cifra.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, iv))
        return String(cifra.doFinal(bytes, TAMANHO_IV, bytes.size - TAMANHO_IV), Charsets.UTF_8)
    }

    private companion object {
        const val ALIAS = "cuidamed_tokens"
        const val CHAVE_PREFS = "tokens"
        const val TRANSFORMACAO = "AES/GCM/NoPadding"
        const val TAMANHO_IV = 12
        const val SEPARADOR = "\n"
    }
}
