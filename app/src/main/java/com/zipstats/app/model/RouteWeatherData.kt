package com.zipstats.app.model

/**
 * Representa una "foto fija" de todas las condiciones climáticas
 * capturadas y detectadas durante una ruta.
 */
data class RouteWeatherSnapshot(
    val initialTemp: Double?,
    val initialEmoji: String?,
    val initialCode: Int?,
    val initialCondition: String?,
    val initialDescription: String?,
    val initialIsDay: Boolean = true,
    val initialFeelsLike: Double? = null,
    val initialWindChill: Double? = null,
    val initialHeatIndex: Double? = null,
    val initialHumidity: Double? = null,
    val initialWindSpeed: Double? = null,
    val initialUvIndex: Double? = null,
    val initialWindDirection: String? = null,
    val initialWindGusts: Double? = null,
    val initialRainProbability: Double? = null,
    val initialVisibility: Double? = null,
    val initialDewPoint: Double? = null,

    // Estadísticas acumuladas (Cálculos del monitoreo continuo)
    val maxWindSpeed: Double = 0.0,
    val maxWindGusts: Double = 0.0,
    val minTemp: Double = Double.MAX_VALUE,
    val maxTemp: Double = Double.MIN_VALUE,
    val maxUvIndex: Double = 0.0,
    val maxPrecipitation: Double = 0.0,

    // Badges y lógica de estado
    val hadRain: Boolean = false,
    val hadWetRoad: Boolean = false,
    val hadExtreme: Boolean = false,
    val extremeReason: String? = null,
    val rainReason: String? = null,
    val rainStartMinute: Int? = null
)