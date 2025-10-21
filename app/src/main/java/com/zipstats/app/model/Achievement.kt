package com.zipstats.app.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.ui.graphics.vector.ImageVector

enum class AchievementLevel {
    NOVATO,      // Logros para principiantes
    EXPLORADOR,  // Logros intermedios
    MAESTRO,     // Logros avanzados
    SECRETO      // Logros secretos (ocultos en pestañas)
}

// Tipos de requisitos para los logros
enum class AchievementRequirementType {
    DISTANCE,           // Distancia total recorrida
    TRIPS,              // Número de viajes/registros
    CONSECUTIVE_DAYS,   // Días consecutivos de uso
    UNIQUE_SCOOTERS,    // Vehículos diferentes usados (VMP)
    LONGEST_TRIP,       // Viaje más largo
    UNIQUE_WEEKS,       // Semanas diferentes con viajes
    MAINTENANCE_COUNT,  // Mantenimientos registrados
    CO2_SAVED,          // CO2 ahorrado en kg
    UNIQUE_MONTHS,      // Meses diferentes con viajes
    CONSECUTIVE_MONTHS, // Meses consecutivos con viajes
    ALL_OTHERS,         // Requiere todos los demás logros desbloqueados
    MULTIPLE            // Requisitos múltiples
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector = Icons.Default.EmojiEvents,
    val level: AchievementLevel,
    val emoji: String,
    val hashtag: String,
    val isUnlocked: Boolean = false,
    val progress: Double = 0.0,
    val shareMessage: String = "",
    
    // Requisitos (al menos uno debe estar presente)
    val requiredDistance: Double? = null,
    val requiredTrips: Int? = null,
    val requiredConsecutiveDays: Int? = null,
    val requiredUniqueScooters: Int? = null, // Vehículos únicos requeridos
    val requiredLongestTrip: Double? = null,
    val requiredUniqueWeeks: Int? = null,
    val requiredMaintenanceCount: Int? = null,
    val requiredCO2Saved: Double? = null,
    val requiredUniqueMonths: Int? = null,
    val requiredConsecutiveMonths: Int? = null,
    
    // Tipo de requisito
    val requirementType: AchievementRequirementType = AchievementRequirementType.DISTANCE
) 