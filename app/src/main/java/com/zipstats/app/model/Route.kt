package com.zipstats.app.model

import com.google.firebase.firestore.DocumentId

/**
 * Representa una ruta completa con todos sus puntos GPS
 */
data class Route(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val scooterId: String = "",
    val scooterName: String = "",
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val totalDistance: Double = 0.0, // en kilómetros
    val totalDuration: Long = 0L, // en milisegundos
    val averageSpeed: Double = 0.0, // en km/h (velocidad media general)
    val maxSpeed: Double = 0.0, // en km/h
    val points: List<RoutePoint> = emptyList(),
    val isCompleted: Boolean = false,
    val notes: String = "",
    // Nuevas métricas de análisis post-ruta
    val movingTime: Long = 0L, // tiempo en movimiento (ms)
    val pauseTime: Long = 0L, // tiempo en pausas (ms)
    val averageMovingSpeed: Double = 0.0, // velocidad media solo en movimiento (km/h)
    val pauseCount: Int = 0, // número de pausas detectadas
    val movingPercentage: Float = 0f, // porcentaje de tiempo en movimiento
    // Datos del clima al momento de la ruta
    val weatherTemperature: Double? = null, // temperatura en °C
    val weatherEmoji: String? = null, // emoji del clima (☀️, ☁️, etc.)
    val weatherDescription: String? = null, // descripción del clima
    val weatherIsDay: Boolean = true // <-- NUEVO CAMPO (Por defecto 'true' para rutas antiguas)
) {
    /**
     * Calcula la duración en minutos
     */
    val durationInMinutes: Long
        get() = totalDuration / (1000 * 60)

    /**
     * Calcula la duración en formato legible (HH:MM:SS)
     */
    val durationFormatted: String
        get() {
            val seconds = totalDuration / 1000
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, secs)
            } else {
                String.format("%02d:%02d", minutes, secs)
            }
        }

    /**
     * Convierte a Map para guardar en Firebase
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "scooterId" to scooterId,
        "scooterName" to scooterName,
        "startTime" to startTime,
        "endTime" to endTime,
        "totalDistance" to totalDistance,
        "totalDuration" to totalDuration,
        "averageSpeed" to averageSpeed,
        "maxSpeed" to maxSpeed,
        "points" to points.map { it.toMap() },
        "isCompleted" to isCompleted,
        "notes" to notes,
        "movingTime" to movingTime,
        "pauseTime" to pauseTime,
        "averageMovingSpeed" to averageMovingSpeed,
        "pauseCount" to pauseCount,
        "movingPercentage" to movingPercentage,
        "weatherTemperature" to weatherTemperature,
        "weatherEmoji" to weatherEmoji,
        "weatherDescription" to weatherDescription,
        "weatherIsDay" to weatherIsDay // <-- NUEVO CAMPO (Para escribir)
    )

    companion object {
        /**
         * Crea una Route desde un Map de Firebase
         */
        fun fromMap(id: String, map: Map<String, Any?>): Route {
            val pointsList = (map["points"] as? List<*>)?.mapNotNull { point ->
                (point as? Map<*, *>)?.let { pointMap ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        RoutePoint.fromMap(pointMap as Map<String, Any?>)
                    } catch (e: ClassCastException) {
                        null
                    }
                }
            } ?: emptyList()

            return Route(
                id = id,
                userId = map["userId"] as? String ?: "",
                scooterId = map["scooterId"] as? String ?: "",
                scooterName = map["scooterName"] as? String ?: "",
                startTime = map["startTime"] as? Long ?: 0L,
                endTime = map["endTime"] as? Long,
                totalDistance = map["totalDistance"] as? Double ?: 0.0,
                totalDuration = map["totalDuration"] as? Long ?: 0L,
                averageSpeed = map["averageSpeed"] as? Double ?: 0.0,
                maxSpeed = map["maxSpeed"] as? Double ?: 0.0,
                points = pointsList,
                isCompleted = map["isCompleted"] as? Boolean ?: false,
                notes = map["notes"] as? String ?: "",
                movingTime = map["movingTime"] as? Long ?: 0L,
                pauseTime = map["pauseTime"] as? Long ?: 0L,
                averageMovingSpeed = map["averageMovingSpeed"] as? Double ?: 0.0,
                pauseCount = map["pauseCount"] as? Int ?: 0,
                movingPercentage = (map["movingPercentage"] as? Number)?.toFloat() ?: 0f,
                // Validar clima al parsear
                weatherTemperature = (map["weatherTemperature"] as? Number)?.toDouble()?.takeIf {
                    !it.isNaN() && !it.isInfinite() && it >= -50 && it <= 60 && it != 0.0
                },
                weatherEmoji = (map["weatherEmoji"] as? String)?.takeIf { it.isNotBlank() },
                weatherDescription = map["weatherDescription"] as? String,
                // <-- NUEVO CAMPO (Para leer). Si no existe (ruta antigua), usa 'true'
                weatherIsDay = map["weatherIsDay"] as? Boolean ?: true
            )
        }
    }
}