package com.zipstats.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paletas de color disponibles en la aplicación.
 * Cada paleta tiene colores específicos para modo Claro, Oscuro y OLED.
 * Incluye parámetros de corrección de contraste para pantallas OLED.
 */
enum class ColorTheme(
    val displayName: String,
    val description: String,
    val primaryLight: Color,
    val secondaryLight: Color,
    val primaryDark: Color,
    val secondaryDark: Color,
    // Parámetros de corrección para OLED
    val onPrimaryDark: Color = Color(0xFF000000),
    val onSurfaceVariantDark: Color = Color(0xFFD1D1D1),
    val outlineDark: Color = Color(0xFF636363)
) {
    /**
     * PALETA 1 — "Ride Blue" (moderna y tecnológica)
     * Inspirada en apps de movilidad tipo Google Maps o Tesla
     */
    RIDE_BLUE(
        displayName = "Ride Blue",
        description = "Moderna",
        primaryLight = Color(0xFF2979FF),    // Azul eléctrico
        secondaryLight = Color(0xFF00E5FF),  // Cian brillante
        primaryDark = Color(0xFF82B1FF),     // Azul claro vibrante
        secondaryDark = Color(0xFF00BCD4),   // Cian
        onPrimaryDark = Color(0xFF000000),
        onSurfaceVariantDark = Color(0xFFB0C4DE),
        outlineDark = Color(0xFF465A7A)
    ),
    
    /**
     * PALETA 2 — "Eco Green" (natural y sostenible)
     * Ideal si la app destaca el ahorro de CO₂ y energía limpia
     */
    ECO_GREEN(
        displayName = "Eco Green",
        description = "Natural",
        primaryLight = Color(0xFF43A047),    // Verde hoja
        secondaryLight = Color(0xFFAED581),  // Verde lima suave
        primaryDark = Color(0xFF81C784),     // Verde suave
        secondaryDark = Color(0xFF66BB6A),   // Verde brillante
        onPrimaryDark = Color(0xFF002300),
        onSurfaceVariantDark = Color(0xFFB2CBB2),
        outlineDark = Color(0xFF4D634D)
    ),
    
    /**
     * PALETA 3 — "Energy Red" (deportiva y potente)
     * Perfecta para usuarios de patinetes rápidos o e-bikes
     */
    ENERGY_RED(
        displayName = "Energy Red",
        description = "Deportiva",
        primaryLight = Color(0xFFE53935),    // Rojo energía
        secondaryLight = Color(0xFFFFCA28),  // Amarillo acento
        primaryDark = Color(0xFFFF8A80),     // Rojo coral
        secondaryDark = Color(0xFFFFC107),   // Amarillo dorado
        onPrimaryDark = Color(0xFF000000),
        onSurfaceVariantDark = Color(0xFFD7B9B9),
        outlineDark = Color(0xFF754D4D)
    ),
    
    /**
     * PALETA 4 — "Urban Purple" (moderna y elegante)
     * Inspirada en el branding de apps urbanas y tecnológicas
     */
    URBAN_PURPLE(
        displayName = "Urban Purple",
        description = "Elegante",
        primaryLight = Color(0xFF9C27B0),    // Púrpura intenso
        secondaryLight = Color(0xFF7C4DFF),  // Violeta neón
        primaryDark = Color(0xFFCE93D8),     // Púrpura claro
        secondaryDark = Color(0xFFB388FF),   // Violeta suave
        onPrimaryDark = Color(0xFF21002D),
        onSurfaceVariantDark = Color(0xFFC8B3CE),
        outlineDark = Color(0xFF5E4564)
    ),
    
    /**
     * PALETA 5 — "Steel Gray" (minimalista y profesional)
     * Ideal para usuarios que prefieren un look sobrio tipo dashboard
     */
    STEEL_GRAY(
        displayName = "Steel Gray",
        description = "Profesional",
        primaryLight = Color(0xFF546E7A),    // Gris azulado
        secondaryLight = Color(0xFF00ACC1),  // Azul acero
        primaryDark = Color(0xFFB0BEC5),     // Gris claro azulado
        secondaryDark = Color(0xFF4DD0E1),   // Azul acero claro
        onPrimaryDark = Color(0xFF1B272C),
        onSurfaceVariantDark = Color(0xFFBCC6CC),
        outlineDark = Color(0xFF546E7A)
    ),
    
    /**
     * PALETA 6 — "Solar Flare" (cálida y energética)
     * Inspiración: Amaneceres, energía y vitalidad
     */
    SOLAR_FLARE(
        displayName = "Solar Flare",
        description = "Cálida",
        primaryLight = Color(0xFFFF9800),    // Naranja brillante
        secondaryLight = Color(0xFFFFC107),  // Amarillo dorado
        primaryDark = Color(0xFFFFA726),     // Naranja claro
        secondaryDark = Color(0xFFFFCC80),   // Naranja suave
        onPrimaryDark = Color(0xFF000000),
        onSurfaceVariantDark = Color(0xFFD1D1D1),
        outlineDark = Color(0xFF636363)
    );
    
    companion object {
        fun fromString(value: String): ColorTheme {
            return entries.find { it.name == value } ?: RIDE_BLUE
        }
    }
}

