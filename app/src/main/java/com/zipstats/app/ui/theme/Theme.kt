package com.zipstats.app.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1976D2),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFFB8C00),
    onSecondaryContainer = Color(0xFFFFF3E0),
    tertiary = Color(0xFF90CAF9),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF42A5F5),
    onTertiaryContainer = Color(0xFFE3F2FD),
    error = Color(0xFFCF6679),
    errorContainer = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFFFFFFF),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF424242),
    inverseOnSurface = Color(0xFF121212),
    inverseSurface = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFF2196F3),
    surfaceTint = Color(0xFF64B5F6),
    outlineVariant = Color(0xFF2D2D2D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2A2A),
    surfaceDim = Color(0xFF1A1A1A)
)

// Esquema de colores con negro puro para pantallas OLED
private val PureBlackOledColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1976D2),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFFB8C00),
    onSecondaryContainer = Color(0xFFFFF3E0),
    tertiary = Color(0xFF90CAF9),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF42A5F5),
    onTertiaryContainer = Color(0xFFE3F2FD),
    error = Color(0xFFCF6679),
    errorContainer = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFFFFFFF),
    background = Color(0xFF000000),  // Negro puro para OLED
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF000000),  // Negro puro para OLED
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1A1A1A),  // Ligeramente más claro para diferenciación
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF424242),
    inverseOnSurface = Color(0xFF000000),
    inverseSurface = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFF2196F3),
    surfaceTint = Color(0xFF64B5F6),
    outlineVariant = Color(0xFF1A1A1A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF1A1A1A),  // Más oscuro para mejor contraste
    surfaceDim = Color(0xFF000000)  // Negro puro
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF1976D2),
    secondary = Color(0xFFFF9800),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFCD80),
    onSecondaryContainer = Color(0xFFE65100),
    tertiary = Color(0xFF64B5F6),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF90CAF9),
    onTertiaryContainer = Color(0xFF1976D2),
    error = Color(0xFFB00020),
    errorContainer = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFFFFFFF),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF424242),
    outline = Color(0xFFBDBDBD),
    inverseOnSurface = Color(0xFFF5F5F5),
    inverseSurface = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFF64B5F6),
    surfaceTint = Color(0xFF2196F3),
    outlineVariant = Color(0xFFE0E0E0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF8F8F8),
    surfaceDim = Color(0xFFE6E6E6)
)

@Composable
fun PatinetatrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlackOled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme && pureBlackOled -> PureBlackOledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            
            // Hacer que la barra de estado tenga el color primario
            window.statusBarColor = colorScheme.primary.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

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