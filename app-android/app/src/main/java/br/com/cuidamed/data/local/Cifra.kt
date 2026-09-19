package br.com.cuidamed.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Criptografia dos dados guardados no aparelho (são dados de saúde). Interface para os testes não precisarem do Android. */
interface Cifra {
    fun cifrar(texto: String): String
    fun decifrar(texto: String): String
}

/** Só para testes. */
object SemCifra : Cifra {
    override fun cifrar(texto: String) = texto
    override fun decifrar(texto: String) = texto
}

/** AES-GCM com uma chave do Android Keystore, que nunca sai do aparelho. */
class CifraKeystore : Cifra {

    override fun cifrar(texto: String): String {
        val cifra = Cipher.getInstance(TRANSFORMACAO)
        cifra.init(Cipher.ENCRYPT_MODE, chave())
        return Base64.encodeToString(cifra.iv + cifra.doFinal(texto.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    override fun decifrar(texto: String): String {
        val bytes = Base64.decode(texto, Base64.NO_WRAP)
        val cifra = Cipher.getInstance(TRANSFORMACAO)
        cifra.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, bytes.copyOfRange(0, TAMANHO_IV)))
        return String(cifra.doFinal(bytes, TAMANHO_IV, bytes.size - TAMANHO_IV), Charsets.UTF_8)
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

    private companion object {
        const val ALIAS = "cuidamed_dados"
        const val TRANSFORMACAO = "AES/GCM/NoPadding"
        const val TAMANHO_IV = 12
    }
}
