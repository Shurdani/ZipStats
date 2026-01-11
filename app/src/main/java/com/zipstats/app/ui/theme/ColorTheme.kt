package com.zipstats.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paletas de color disponibles en la aplicación.
 * Cada paleta tiene colores específicos para modo Claro, Oscuro y OLED.
 * Los esquemas de color completos están definidos en Theme.kt.
 */
enum class ColorTheme(
    val displayName: String,
    val description: String,
    val primaryLight: Color  // Solo usado para mostrar el color en el selector de temas
) {
    /**
     * PALETA 1 — "Ride Blue" (moderna y tecnológica)
     * Inspirada en apps de movilidad tipo Google Maps o Tesla
     */
    RIDE_BLUE(
        displayName = "Ride Blue",
        description = "Moderna",
        primaryLight = Color(0xFF2979FF)    // Azul eléctrico
    ),
    
    /**
     * PALETA 2 — "Eco Green" (natural y sostenible)
     * Ideal si la app destaca el ahorro de CO₂ y energía limpia
     */
    ECO_GREEN(
        displayName = "Eco Green",
        description = "Natural",
        primaryLight = Color(0xFF43A047)    // Verde hoja
    ),
    
    /**
     * PALETA 3 — "Energy Red" (deportiva y potente)
     * Perfecta para usuarios de patinetes rápidos o e-bikes
     */
    ENERGY_RED(
        displayName = "Energy Red",
        description = "Deportiva",
        primaryLight = Color(0xFFE53935)    // Rojo energía
    ),
    
    /**
     * PALETA 4 — "Urban Purple" (moderna y elegante)
     * Inspirada en el branding de apps urbanas y tecnológicas
     */
    URBAN_PURPLE(
        displayName = "Urban Purple",
        description = "Elegante",
        primaryLight = Color(0xFF9C27B0)    // Púrpura intenso
    ),
    
    /**
     * PALETA 5 — "Steel Gray" (minimalista y profesional)
     * Ideal para usuarios que prefieren un look sobrio tipo dashboard
     */
    STEEL_GRAY(
        displayName = "Steel Gray",
        description = "Profesional",
        primaryLight = Color(0xFF546E7A)    // Gris azulado
    ),
    
    /**
     * PALETA 6 — "Solar Flare" (cálida y energética)
     * Inspiración: Amaneceres, energía y vitalidad
     */
    SOLAR_FLARE(
        displayName = "Solar Flare",
        description = "Cálida",
        primaryLight = Color(0xFFFF9800)    // Naranja brillante
    );
    
    companion object {
        fun fromString(value: String): ColorTheme {
            return entries.find { it.name == value } ?: RIDE_BLUE
        }
    }
}

