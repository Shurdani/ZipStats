package com.zipstats.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.zipstats.app.R

/**
 * Función adaptativa para escalar tamaños de fuente según el fontScale del sistema.
 * Limita el escalado entre minScale y maxScale para mantener la legibilidad.
 * 
 * @param baseSp Tamaño base en sp
 * @param minScale Escala mínima (por defecto 0.9f = 90%)
 * @param maxScale Escala máxima (por defecto 1.15f = 115%)
 * @return TextUnit escalado y limitado
 */
@Composable
fun adaptiveSp(
    baseSp: Float,
    minScale: Float = 0.9f,
    maxScale: Float = 1.15f
): TextUnit {
    val density = LocalDensity.current
    val scale = density.fontScale.coerceIn(minScale, maxScale)
    return (baseSp * scale).sp
}

// Familia de fuentes Montserrat
// Nota: Solo tenemos light, regular y bold disponibles.
// El sistema puede sintetizar Medium y SemiBold si es necesario.
val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_regular, FontWeight.Medium), // Mapeado a regular
    Font(R.font.montserrat_regular, FontWeight.SemiBold), // Mapeado a regular (sintetizado)
    Font(R.font.montserrat_bold, FontWeight.Bold)
)

/**
 * Tipografías adaptativas de MaterialTheme usando Montserrat.
 * Los tamaños se escalan automáticamente con adaptiveSp() cuando se usan con ZipStatsText.
 */
val ZipStatsTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp
    ),
    displayMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    ),
    displaySmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    )
)

// Mantener compatibilidad con código existente
@Deprecated("Usar ZipStatsTypography en su lugar", ReplaceWith("ZipStatsTypography"))
val AppTypography = ZipStatsTypography