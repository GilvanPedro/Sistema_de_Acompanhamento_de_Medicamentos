package br.com.cuidamed.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private val EsquemaClaro = lightColorScheme(
    primary = AzulClaro,
    onPrimary = Color.White,
    primaryContainer = AzulContainerClaro,
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF6B4E00),
    onSecondary = Color.White,
    secondaryContainer = AmareloContainerClaro,
    onSecondaryContainer = Color(0xFF241A00),
    tertiary = VerdeClaro,
    onTertiary = Color.White,
    tertiaryContainer = VerdeContainerClaro,
    onTertiaryContainer = Color(0xFF00210F),
    error = VermelhoClaro,
    onError = Color.White,
    errorContainer = VermelhoContainerClaro,
    onErrorContainer = Color(0xFF410002),
    background = FundoClaro,
    onBackground = Color(0xFF13161B),
    surface = Color.White,
    onSurface = Color(0xFF13161B),
    surfaceVariant = Color(0xFFE4E8F0),
    onSurfaceVariant = Color(0xFF3B4250),
    outline = Color(0xFF6B7385),
)

private val EsquemaEscuro = darkColorScheme(
    primary = AzulEscuro,
    onPrimary = Color(0xFF002C6B),
    primaryContainer = AzulContainerEscuro,
    onPrimaryContainer = Color(0xFFD7E4FF),
    secondary = Color(0xFFF2C24B),
    onSecondary = Color(0xFF3B2A00),
    secondaryContainer = AmareloContainerEscuro,
    onSecondaryContainer = Color(0xFFFFEBB8),
    tertiary = VerdeEscuro,
    onTertiary = Color(0xFF00391B),
    tertiaryContainer = VerdeContainerEscuro,
    onTertiaryContainer = Color(0xFFD5F2DF),
    error = VermelhoEscuro,
    onError = Color(0xFF601410),
    errorContainer = VermelhoContainerEscuro,
    onErrorContainer = Color(0xFFFFDAD6),
    background = FundoEscuro,
    onBackground = Color(0xFFE6E9F0),
    surface = SuperficieEscura,
    onSurface = Color(0xFFE6E9F0),
    surfaceVariant = Color(0xFF2B303B),
    onSurfaceVariant = Color(0xFFC3C9D6),
    outline = Color(0xFF8B93A5),
)

/**
 * @param escuro null segue o modo do sistema.
 * @param escalaDaLetra multiplica o tamanho de todos os textos (escolhido pelo usuário nos botões A+ / A−).
 */
@Composable
fun CuidaMedTheme(
    escuro: Boolean? = null,
    escalaDaLetra: Float = 1f,
    content: @Composable () -> Unit,
) {
    val usarEscuro = escuro ?: isSystemInDarkTheme()
    val esquema = if (usarEscuro) EsquemaEscuro else EsquemaClaro

    // O modo escuro é escolhido no app, e não só pelo sistema: os ícones da barra de cima (hora, bateria) e o fundo
    // atrás das barras do sistema precisam acompanhar, senão ficam escuros sobre escuro e somem.
    val visao = LocalView.current
    if (!visao.isInEditMode) {
        SideEffect {
            val janela = (visao.context as? Activity)?.window ?: return@SideEffect
            janela.decorView.setBackgroundColor(esquema.background.toArgb())
            WindowCompat.getInsetsController(janela, visao).apply {
                isAppearanceLightStatusBars = !usarEscuro
                isAppearanceLightNavigationBars = !usarEscuro
            }
        }
    }
    val densidade = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(densidade.density, densidade.fontScale * escalaDaLetra)) {
        MaterialTheme(
            colorScheme = esquema,
            typography = Typography,
            content = content,
        )
    }
}
