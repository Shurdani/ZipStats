package com.zipstats.app.model

enum class VehicleType(
    val displayName: String, 
    val emoji: String,
    val minSpeed: Float,      // km/h - velocidad mínima para contar
    val maxSpeed: Float,      // km/h - velocidad máxima razonable
    val pauseRadius: Float,   // metros - radio de movimiento en pausa
    val minPauseDuration: Long, // ms - duración mínima de pausa
    val pauseSpeedThreshold: Float, // km/h - umbral de velocidad para considerar pausa
    val minPointsForPause: Int // puntos consecutivos mínimos para confirmar pausa
) {
    PATINETE(
        displayName = "Patinete", 
        emoji = "🛴",
        minSpeed = 4f,        // Reducido de 5f a 4f para mejor detección
        maxSpeed = 35f,
        pauseRadius = 8f,
        minPauseDuration = 3000L,  // Reducido de 4000L a 3000L para detectar pausas más cortas
        pauseSpeedThreshold = 4f,  // Nuevo: umbral de velocidad para pausas
        minPointsForPause = 3      // Nuevo: puntos consecutivos para confirmar pausa
    ),
    BICICLETA(
        displayName = "Bicicleta", 
        emoji = "🚲",
        minSpeed = 3f,
        maxSpeed = 40f,
        pauseRadius = 10f,
        minPauseDuration = 3000L,  // Reducido de 5000L a 3000L
        pauseSpeedThreshold = 3f,  // Nuevo
        minPointsForPause = 3      // Nuevo
    ),
    E_BIKE(
        displayName = "E-Bike",
        emoji = "🚴",
        minSpeed = 3f,              // Igual que bici: rodaje lento sin motor
        maxSpeed = 45f,
        pauseRadius = 8f,
        minPauseDuration = 3000L,
        pauseSpeedThreshold = 3f,   // Coherente con minSpeed
        minPointsForPause = 3,
    ),
    MONOCICLO(
        displayName = "Monociclo",
        emoji = "🛞",
        minSpeed = 4f,
        maxSpeed = 40f,             // Por encima de patinete; bajadas / modelos rápidos
        pauseRadius = 8f,
        minPauseDuration = 3000L,
        pauseSpeedThreshold = 4f,
        minPointsForPause = 3,
    );
    
    companion object {
        fun fromString(value: String): VehicleType {
            return values().find { it.name == value } ?: PATINETE
        }
    }
}

