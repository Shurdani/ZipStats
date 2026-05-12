package com.zipstats.app.model

enum class SurfaceConditionType {
    NONE,
    RAIN,
    WET_ROAD
}

data class RouteWeatherDecision(
    val surfaceConditionType: SurfaceConditionType = SurfaceConditionType.NONE,
    val isSurfaceConditionConfirmed: Boolean = true,
    /** Si el usuario respondió el bloque «Condiciones del asfalto» al finalizar la ruta */
    val userAnsweredSurfaceQuestions: Boolean = false
)

/**
 * Representa una "foto fija" de todas las condiciones climáticas
 * capturadas y detectadas durante una ruta.
 */
data class RouteWeatherSnapshot(

    // ─────────────────────────────────────────────
    // INICIO DE LA RUTA (siempre presentes)
    // ─────────────────────────────────────────────
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
    val initialWindDirection: Int? = null,
    val initialWindGusts: Double? = null,
    val initialRainProbability: Double? = null,
    val initialVisibility: Double? = null,
    val initialDewPoint: Double? = null,

    // ─────────────────────────────────────────────
    // FINAL DE LA RUTA (null si ruta sin badges)
    // ─────────────────────────────────────────────
    val finalTemp: Double? = null,
    val finalEmoji: String? = null,
    val finalCode: Int? = null,
    val finalCondition: String? = null,
    val finalDescription: String? = null,
    val finalIsDay: Boolean? = null,
    val finalFeelsLike: Double? = null,
    val finalWindChill: Double? = null,
    val finalHeatIndex: Double? = null,
    val finalHumidity: Double? = null,
    val finalWindSpeed: Double? = null,
    val finalUvIndex: Double? = null,
    val finalWindDirection: Int? = null,
    val finalWindGusts: Double? = null,
    val finalRainProbability: Double? = null,
    val finalVisibility: Double? = null,
    val finalDewPoint: Double? = null,

    // ─────────────────────────────────────────────
    // EXTREMOS DEL MONITOREO CONTINUO
    // ─────────────────────────────────────────────
    val maxWindSpeed: Double = 0.0,
    val maxWindGusts: Double = 0.0,
    val minTemp: Double = Double.MAX_VALUE,
    val maxTemp: Double = Double.MIN_VALUE,
    val maxUvIndex: Double = 0.0,
    val maxPrecipitation: Double = 0.0,

    // ─────────────────────────────────────────────
    // BADGES Y LÓGICA DE ESTADO
    // ─────────────────────────────────────────────
    val hadRain: Boolean = false,
    val hadWetRoad: Boolean = false,
    val hadExtreme: Boolean = false,
    val extremeReason: String? = null,
    val rainReason: String? = null,
    val rainStartMinute: Int? = null
)