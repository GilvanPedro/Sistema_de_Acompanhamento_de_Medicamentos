package br.com.cuidamed.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Escolhas de aparência: tamanho da letra (5 níveis) e modo claro/escuro. Ficam salvas no aparelho. */
class Preferencias(contexto: Context) {

    private val prefs = contexto.getSharedPreferences("aparencia", Context.MODE_PRIVATE)

    /** Índice de 0 a 4 em [ESCALAS]; o padrão (1) já é maior que o texto normal do sistema. */
    var nivelDaLetra by mutableIntStateOf(prefs.getInt("nivel_letra", 1).coerceIn(0, ESCALAS.lastIndex))
        private set

    /** null = seguir o sistema; true = escuro; false = claro. */
    var escuro by mutableStateOf(if (prefs.contains("escuro")) prefs.getBoolean("escuro", false) else null)
        private set

    val escala: Float get() = ESCALAS[nivelDaLetra]

    fun aumentarLetra() = definirNivel(nivelDaLetra + 1)

    fun diminuirLetra() = definirNivel(nivelDaLetra - 1)

    fun alternarModoEscuro(sistemaEscuro: Boolean) {
        val novo = !(escuro ?: sistemaEscuro)
        escuro = novo
        prefs.edit().putBoolean("escuro", novo).apply()
    }

    private fun definirNivel(nivel: Int) {
        nivelDaLetra = nivel.coerceIn(0, ESCALAS.lastIndex)
        prefs.edit().putInt("nivel_letra", nivelDaLetra).apply()
    }

    companion object {
        val ESCALAS = listOf(0.9f, 1.05f, 1.25f, 1.5f, 1.8f)
    }
}
