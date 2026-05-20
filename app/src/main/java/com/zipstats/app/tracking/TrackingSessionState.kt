package com.zipstats.app.tracking

/**
 * Estado del seguimiento de rutas
 */
sealed class TrackingState {
    object Idle : TrackingState()
    object Tracking : TrackingState()
    object Paused : TrackingState()
    object Saving : TrackingState()
    data class Error(val message: String) : TrackingState()
}

/**
 * Estado del clima para notificaciones (simplificado)
 */
enum class WeatherBadgeState {
    SECO,
    CALZADA_HUMEDA,
    LLUVIA,
    EXTREMO
}

/**
 * Estado de la captura del clima
 */
sealed class WeatherStatus {
    object Idle : WeatherStatus()
    object Loading : WeatherStatus()
    data class Success(
        val temperature: Double,
        val feelsLike: Double,
        val windChill: Double?,
        val heatIndex: Double?,
        val description: String,
        val icon: String,
        val humidity: Int,
        val windSpeed: Double,
        val weatherEmoji: String,
        val weatherCode: Int,
        val isDay: Boolean,
        val uvIndex: Double?,
        val windDirection: Int?,
        val windGusts: Double?,
        val rainProbability: Int?,
        val precipitation: Double,
        val rain: Double?,
        val showers: Double?,
        val dewPoint: Double?,
        val visibility: Double?
    ) : WeatherStatus()
    data class Error(val message: String) : WeatherStatus()
    object NotAvailable : WeatherStatus()
}

/**
 * Estado del GPS previo al iniciar una ruta (precarga de señal).
 */
sealed class GpsPreLocationState {
    object Searching : GpsPreLocationState()
    data class Found(val accuracy: Float) : GpsPreLocationState()
    object Ready : GpsPreLocationState()
}
