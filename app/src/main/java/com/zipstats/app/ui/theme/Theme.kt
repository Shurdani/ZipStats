package com.zipstats.app.ui.theme

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

// ============================================
// PALETA 1: RIDE BLUE
// ============================================
private val RideBlueLight = lightColorScheme(
    primary = Color(0xFF2979FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF82B1FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF4DD0E1),
    onSecondaryContainer = Color(0xFF001F26),
    tertiary = Color(0xFF82B1FF),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF0D0D0D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFFE3F2FD),
    onSurfaceVariant = Color(0xFF1E3A5F),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF)
)

private val RideBlueDark = darkColorScheme(
    primary = Color(0xFF82B1FF),
    onPrimary = Color(0xFF001B3D),
    primaryContainer = Color(0xFF2979FF),
    onPrimaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFF00BCD4),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF006978),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF00E5FF),
    onTertiary = Color(0xFF003544),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB0BEC5),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

private val RideBlueOled = darkColorScheme(
    primary = Color(0xFF82B1FF),
    onPrimary = Color(0xFF001B3D),
    primaryContainer = Color(0xFF2979FF),
    onPrimaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFF00BCD4),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF006978),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF00E5FF),
    onTertiary = Color(0xFF003544),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFFB0BEC5),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

// ============================================
// PALETA 2: ECO GREEN
// ============================================
private val EcoGreenLight = lightColorScheme(
    primary = Color(0xFF43A047),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF81C784),
    onPrimaryContainer = Color(0xFF0A0A0A),
    secondary = Color(0xFFAED581),
    onSecondary = Color(0xFF0A0A0A),
    secondaryContainer = Color(0xFFC5E1A5),
    onSecondaryContainer = Color(0xFF0A0A0A),
    tertiary = Color(0xFF66BB6A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF9FFF9),
    onBackground = Color(0xFF0A0A0A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFE8F5E9),
    onSurfaceVariant = Color(0xFF1B5E20),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF)
)

private val EcoGreenDark = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF0A0A0A),
    primaryContainer = Color(0xFF43A047),
    onPrimaryContainer = Color(0xFFEAEAEA),
    secondary = Color(0xFF66BB6A),
    onSecondary = Color(0xFF0A0A0A),
    secondaryContainer = Color(0xFF388E3C),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = Color(0xFFAED581),
    onTertiary = Color(0xFF0A0A0A),
    background = Color(0xFF101410),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF1A1F1A),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF252A25),
    onSurfaceVariant = Color(0xFFA5D6A7),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

private val EcoGreenOled = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF0A0A0A),
    primaryContainer = Color(0xFF43A047),
    onPrimaryContainer = Color(0xFFEAEAEA),
    secondary = Color(0xFF66BB6A),
    onSecondary = Color(0xFF0A0A0A),
    secondaryContainer = Color(0xFF388E3C),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = Color(0xFFAED581),
    onTertiary = Color(0xFF0A0A0A),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFFA5D6A7),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

// ============================================
// PALETA 3: ENERGY RED
// ============================================
private val EnergyRedLight = lightColorScheme(
    primary = Color(0xFFE53935),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFF6F60),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFFFFCA28),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFFFFF59D),
    onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFFFFC107),
    onTertiary = Color(0xFF111111),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFFFEBEE),
    onSurfaceVariant = Color(0xFFB71C1C),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF)
)

private val EnergyRedDark = darkColorScheme(
    primary = Color(0xFFFF6F60),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFFE53935),
    onPrimaryContainer = Color(0xFFEAEAEA),
    secondary = Color(0xFFFFC107),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFFFFA000),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = Color(0xFFFFCA28),
    onTertiary = Color(0xFF111111),
    background = Color(0xFF181010),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF201515),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF2A1A1A),
    onSurfaceVariant = Color(0xFFFFCDD2),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

private val EnergyRedOled = darkColorScheme(
    primary = Color(0xFFFF6F60),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFFE53935),
    onPrimaryContainer = Color(0xFFEAEAEA),
    secondary = Color(0xFFFFC107),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFFFFA000),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = Color(0xFFFFCA28),
    onTertiary = Color(0xFF111111),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFFFFCDD2),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

// ============================================
// PALETA 4: URBAN PURPLE
// ============================================
private val UrbanPurpleLight = lightColorScheme(
    primary = Color(0xFF9C27B0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBA68C8),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF7C4DFF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB388FF),
    onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFFB388FF),
    onTertiary = Color(0xFF111111),
    background = Color(0xFFFBF7FF),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF3E5F5),
    onSurfaceVariant = Color(0xFF4A148C),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF)
)

private val UrbanPurpleDark = darkColorScheme(
    primary = Color(0xFFBA68C8),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF9C27B0),
    onPrimaryContainer = Color(0xFFEAEAEA),
    secondary = Color(0xFFB388FF),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF7C4DFF),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = Color(0xFF7C4DFF),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFF151015),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF1C1A1E),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF252025),
    onSurfaceVariant = Color(0xFFCE93D8),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

private val UrbanPurpleOled = darkColorScheme(
    primary = Color(0xFFBA68C8),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF9C27B0),
    onPrimaryContainer = Color(0xFFEAEAEA),
    secondary = Color(0xFFB388FF),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF7C4DFF),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = Color(0xFF7C4DFF),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFFCE93D8),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

// ============================================
// PALETA 5: STEEL GRAY
// ============================================
private val SteelGrayLight = lightColorScheme(
    primary = Color(0xFF546E7A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF90A4AE),
    onPrimaryContainer = Color(0xFF0D0D0D),
    secondary = Color(0xFF00ACC1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF4DD0E1),
    onSecondaryContainer = Color(0xFF0D0D0D),
    tertiary = Color(0xFF4DD0E1),
    onTertiary = Color(0xFF0D0D0D),
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF0D0D0D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFFECEFF1),
    onSurfaceVariant = Color(0xFF37474F),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF)
)

private val SteelGrayDark = darkColorScheme(
    primary = Color(0xFF90A4AE),
    onPrimary = Color(0xFF0D0D0D),
    primaryContainer = Color(0xFF546E7A),
    onPrimaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFF4DD0E1),
    onSecondary = Color(0xFF0D0D0D),
    secondaryContainer = Color(0xFF00ACC1),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF00ACC1),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF263238),
    onSurfaceVariant = Color(0xFFB0BEC5),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

private val SteelGrayOled = darkColorScheme(
    primary = Color(0xFF90A4AE),
    onPrimary = Color(0xFF0D0D0D),
    primaryContainer = Color(0xFF546E7A),
    onPrimaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFF4DD0E1),
    onSecondary = Color(0xFF0D0D0D),
    secondaryContainer = Color(0xFF00ACC1),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF00ACC1),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFFB0BEC5),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

// ============================================
// PALETA 6: SOLAR FLARE
// ============================================
private val SolarFlareLight = lightColorScheme(
    primary = Color(0xFFFF9800),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFA726),
    onPrimaryContainer = Color(0xFF212121),
    secondary = Color(0xFFFFC107),
    onSecondary = Color(0xFF212121),
    secondaryContainer = Color(0xFFFFD54F),
    onSecondaryContainer = Color(0xFF212121),
    tertiary = Color(0xFFFF5722),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8E1),
    onBackground = Color(0xFF212121),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFFFECB3),
    onSurfaceVariant = Color(0xFFE65100),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF)
)

private val SolarFlareDark = darkColorScheme(
    primary = Color(0xFFFFA726),
    onPrimary = Color(0xFF212121),
    primaryContainer = Color(0xFFFF9800),
    onPrimaryContainer = Color(0xFFFDF3E0),
    secondary = Color(0xFFFFD54F),
    onSecondary = Color(0xFF212121),
    secondaryContainer = Color(0xFFFFC107),
    onSecondaryContainer = Color(0xFFFDF3E0),
    tertiary = Color(0xFFFF7043),
    onTertiary = Color(0xFF212121),
    background = Color(0xFF1C140A),
    onBackground = Color(0xFFFDF3E0),
    surface = Color(0xFF2A1E0F),
    onSurface = Color(0xFFFDF3E0),
    surfaceVariant = Color(0xFF3A2A15),
    onSurfaceVariant = Color(0xFFFFCC80),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

private val SolarFlareOled = darkColorScheme(
    primary = Color(0xFFFFA726),
    onPrimary = Color(0xFF212121),
    primaryContainer = Color(0xFFFF9800),
    onPrimaryContainer = Color(0xFFFFE8C2),
    secondary = Color(0xFFFFCC80),
    onSecondary = Color(0xFF212121),
    secondaryContainer = Color(0xFFFFA726),
    onSecondaryContainer = Color(0xFFFFE8C2),
    tertiary = Color(0xFFFF8A65),
    onTertiary = Color(0xFF212121),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFE8C2),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFFFE8C2),
    surfaceVariant = Color(0xFF0F0F0F),
    onSurfaceVariant = Color(0xFFFFCC80),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

// ============================================
// FUNCIÓN PARA OBTENER EL ESQUEMA DE COLORES
// ============================================
private fun getColorScheme(
    colorTheme: ColorTheme,
    darkTheme: Boolean,
    pureBlackOled: Boolean
): ColorScheme {
    // Si es tema oscuro con OLED, devolver la variante OLED
    if (darkTheme && pureBlackOled) {
        return when (colorTheme) {
            ColorTheme.RIDE_BLUE -> RideBlueOled
            ColorTheme.ECO_GREEN -> EcoGreenOled
            ColorTheme.ENERGY_RED -> EnergyRedOled
            ColorTheme.URBAN_PURPLE -> UrbanPurpleOled
            ColorTheme.STEEL_GRAY -> SteelGrayOled
            ColorTheme.SOLAR_FLARE -> SolarFlareOled
        }
    }
    
    // De lo contrario, devolver el tema normal (claro u oscuro)
    return when (colorTheme) {
        ColorTheme.RIDE_BLUE -> if (darkTheme) RideBlueDark else RideBlueLight
        ColorTheme.ECO_GREEN -> if (darkTheme) EcoGreenDark else EcoGreenLight
        ColorTheme.ENERGY_RED -> if (darkTheme) EnergyRedDark else EnergyRedLight
        ColorTheme.URBAN_PURPLE -> if (darkTheme) UrbanPurpleDark else UrbanPurpleLight
        ColorTheme.STEEL_GRAY -> if (darkTheme) SteelGrayDark else SteelGrayLight
        ColorTheme.SOLAR_FLARE -> if (darkTheme) SolarFlareDark else SolarFlareLight
    }
}

@Composable
fun PatinetatrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorTheme = ColorTheme.RIDE_BLUE,
    dynamicColor: Boolean = true,
    pureBlackOled: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // Lógica de prioridad:
    // 1. Colores dinámicos (Material You) si están activados
    // 2. Paleta seleccionada si no hay colores dinámicos
    // 3. Modo OLED si está activo y es tema oscuro
    val colorScheme = when {
        // Colores dinámicos tienen prioridad si están disponibles y activados
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Paleta personalizada seleccionada
        else -> getColorScheme(colorTheme, darkTheme, pureBlackOled)
    }
    
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            
            // Hacer que la barra de estado tenga el color primario
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.primary.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            
            // Configurar el sistema de barras para Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                window.isStatusBarContrastEnforced = false
                @Suppress("DEPRECATION")
                window.isNavigationBarContrastEnforced = false
            }

            // Configurar el sistema para pantalla completa y edge-to-edge
            WindowCompat.setDecorFitsSystemWindows(window, true)
            
            // Configurar el color de los iconos
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme

            // Asegurarse de que la ventana tenga el fondo correcto
            window.setFlags(
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
