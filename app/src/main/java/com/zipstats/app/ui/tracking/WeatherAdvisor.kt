package com.zipstats.app.ui.tracking

import android.util.Log


class WeatherAdvisor {
    /**
 * Determina si hay lluvia activa confiando completamente en Google Weather
 *
 * Si Google dice que hay lluvia (en descripción o condición), activamos el badge/preaviso.
 * No importa si es ligera o intensa - confiamos en la decisión de Google.
 *
 * @return Pair<Boolean, String> donde el Boolean indica si hay lluvia activa y el String es la razón amigable para el usuario
 */
    fun checkActiveRain(
    condition: String, // Condition string de Google (ej: "RAIN", "CLOUDY")
    description: String, // Descripción de Google (ej: "Lluvia", "Nublado")
    precipitation: Double
): Pair<Boolean, String> {
    val cond = condition.uppercase()
    val desc = description.uppercase()

    // Términos que indican lluvia en la descripción de Google
    val rainTerms = listOf("LLUVIA", "RAIN", "CHUBASCO", "TORMENTA", "DRIZZLE", "LLOVIZNA", "THUNDERSTORM", "SHOWER")

    // Condiciones de Google que indican lluvia
    val rainConditions = listOf("RAIN", "LIGHT_RAIN", "HEAVY_RAIN", "THUNDERSTORM", "DRIZZLE",
        "LIGHT_RAIN_SHOWERS", "RAIN_SHOWERS", "HEAVY_RAIN_SHOWERS",
        "CHANCE_OF_SHOWERS", "SCATTERED_SHOWERS")

    // Si la condición contiene lluvia, es lluvia activa
    if (rainConditions.any { cond.contains(it) }) {
        return true to "Lluvia detectada por Google"
    }

    // Si la descripción menciona lluvia, es lluvia activa (incluso si es débil)
    if (rainTerms.any { desc.contains(it) }) {
        return true to "Lluvia detectada por Google"
    }

    // Si no hay indicación de lluvia en Google, no es lluvia activa
    // (podría ser calzada húmeda si ha parado de llover o hay mucha humedad)
    return false to "No se detectó lluvia"
}


/**
 * Verifica si hay calzada húmeda cuando NO hay lluvia activa
 *
 * Se activa por señales físicas basadas en datos de Google:
 * - Lluvia reciente (histórico últimas 3h con precipitación acumulada > 0)
 * - Alta humedad + cielo nublado/niebla (condensación)
 * - Persistencia por temporal: humedad >= 80% + precipitación 24h > 0 + (temperature - dewPoint) <= 3°C
 * - Humedad extrema
 * - Nieve / aguanieve
 *
 * IMPORTANTE: Si hay lluvia activa (detectada por checkActiveRain), esta función siempre retorna false
 */
fun checkWetRoadConditions(
    condition: String,
    humidity: Int,
    recentPrecipitation3h: Double,
    precip24h: Double,
    hasActiveRain: Boolean,
    isDay: Boolean,
    temperature: Double?,
    dewPoint: Double?,
    weatherEmoji: String? = null,
    weatherDescription: String? = null,
    windSpeed: Double? = null
): Boolean {

    val cond = condition.uppercase()
    val desc = weatherDescription?.uppercase().orEmpty()
    val windSpeedKmh = (windSpeed ?: 0.0) * 3.6
    val dewSpread = if (temperature != null && dewPoint != null) temperature - dewPoint else null

    // 1. CORTE RÁPIDO
    if (hasActiveRain) return false

    // 2. FILTRO DE AUTOSECADO MEJORADO
// El viento seca aunque sea de noche, especialmente con humedad baja.
    val canAutoDry = (isDay && humidity < 65) || (humidity < 50 && windSpeedKmh > 20.0)

    if (canAutoDry && recentPrecipitation3h < 0.1) {
        Log.d("WeatherAdvisor", "Autosecado detectado: Viento o baja humedad evaporando agua.")
        return false
    }

    // ──────────────────────────────────────────────────────────
    // LÓGICA DE PERSISTENCIA (El fallo de tu versión anterior)
    // ──────────────────────────────────────────────────────────

    // 3. PERSISTENCIA TRAS LLUVIA DIURNA/NOCTURNA (24h)
    // Si llovió > 1mm y la humedad es > 70%, el suelo no se seca rápido, menos con poco viento.
    val isStillWetFromPastRain = precip24h >= 2.0 && humidity > 75 && !isDay
    // 4. LLUVIA RECIENTE (3h)
    // Umbrales más sensibles si la humedad es alta.
    val hasRecentPrecipitation = (recentPrecipitation3h >= 0.4 && humidity > 70) ||
            (recentPrecipitation3h > 0.1 && humidity > 85)

    // 5. CONDENSACIÓN Y ROCÍO
    val isCondensing = humidity >= 90 && (
            (cond == "FOG" || cond == "HAZE") ||
                    (!isDay && (dewSpread ?: 10.0) <= 1.5)
            )

    // 6. HUMEDAD EXTREMA (Saturación del ambiente)
    val isExtremelyHumid = humidity >= 95

    // 7. NIEVE / AGUANIEVE
    val hasSnowOrSleet = cond.contains("SNOW") || cond.contains("SLEET") ||
            desc.contains("NIEVE") || desc.contains("AGUANIEVE") ||
            weatherEmoji?.contains("❄️") == true

    // ──────────────────────────────────────────────────────────
    // RESULTADO FINAL
    // ──────────────────────────────────────────────────────────
    val isWet = isCondensing ||
            isExtremelyHumid ||
            hasSnowOrSleet ||
            hasRecentPrecipitation ||
            isStillWetFromPastRain

    if (isWet) {
        val motivo = when {
            isCondensing -> "Condensación/Niebla"
            isStillWetFromPastRain -> "Persistencia de lluvia previa (${precip24h}mm en 24h)"
            hasRecentPrecipitation -> "Lluvia reciente (${recentPrecipitation3h}mm)"
            isExtremelyHumid -> "Humedad extrema ($humidity%)"
            hasSnowOrSleet -> "Nieve/Aguanieve"
            else -> "Desconocido"
        }
        Log.d("WeatherAdvisor", "Calzada MOJADA. Motivo: $motivo")
    }

    return isWet
}

 fun checkLowVisibility(visibility: Double?): Pair<Boolean, String?> {
    if (visibility == null) return false to null

    return when {
        visibility < 1000 -> true to "Niebla cerrada: Visibilidad < 1km"
        visibility < 3000 -> true to "Visibilidad reducida por neblina"
        else -> false to null
    }
}

/**
 * Verifica si hay condiciones extremas (replica lógica de RouteDetailDialog.checkExtremeConditions)
 */
 fun checkExtremeConditions(
    windSpeed: Double?,
    windGusts: Double?,
    temperature: Double?,
    uvIndex: Double?,
    isDay: Boolean,
    weatherEmoji: String?,
    weatherDescription: String?,
    weatherCode: Int? = null,
    visibility: Double? = null
): Boolean {
    // Viento fuerte (>40 km/h) - convertir de m/s a km/h
    val windSpeedKmh = (windSpeed ?: 0.0) * 3.6
    if (windSpeedKmh > 40) {
        return true
    }

    // Ráfagas de viento muy fuertes (>60 km/h) - convertir de m/s a km/h
    val windGustsKmh = (windGusts ?: 0.0) * 3.6
    if (windGustsKmh > 60) {
        return true
    }

    // Temperatura extrema (<0°C o >35°C)
    if (temperature != null) {
        if (temperature < 0 || temperature > 35) {
            return true
        }
    }

    // Índice UV muy alto (>8) - solo de día
    if (isDay && uvIndex != null && uvIndex > 8) {
        return true
    }

    // Tormenta (detectada por emoji o descripción)
    val isStorm = weatherEmoji?.let { emoji ->
        emoji.contains("⛈") || emoji.contains("⚡")
    } ?: false

    val isStormByDescription = weatherDescription?.let { desc ->
        desc.contains("Tormenta", ignoreCase = true) ||
                desc.contains("granizo", ignoreCase = true) ||
                desc.contains("rayo", ignoreCase = true)
    } ?: false

    if (isStorm || isStormByDescription) {
        return true
    }

    // Nieve (weatherCode 71, 73, 75, 77, 85, 86 o emoji ❄️)
    // La nieve es muy peligrosa en patinete por el riesgo de resbalar
    val isSnowByCode = weatherCode?.let { code ->
        code in listOf(71, 73, 75, 77, 85, 86)
    } ?: false

    val isSnowByEmoji = weatherEmoji?.let { emoji ->
        emoji.contains("❄️")
    } ?: false

    val isSnowByDescription = weatherDescription?.let { desc ->
        desc.contains("Nieve", ignoreCase = true) ||
                desc.contains("nevada", ignoreCase = true) ||
                desc.contains("snow", ignoreCase = true)
    } ?: false

    if (isSnowByCode || isSnowByEmoji || isSnowByDescription) {
        return true
    }

    // Visibilidad reducida (crítico para Barcelona - niebla/talaia)
    if (visibility != null) {
        val (isLowVisibility, _) = checkLowVisibility(visibility)
        if (isLowVisibility) {
            return true
        }
    }

    return false
}

/**
 * Detecta la causa específica de condiciones extremas (misma lógica que StatisticsViewModel)
 * Retorna: "STORM", "SNOW", "GUSTS", "WIND", "COLD", "HEAT", "VISIBILITY" o null
 */
 fun detectExtremeCause(
    windSpeed: Double?,
    windGusts: Double?,
    temperature: Double?,
    uvIndex: Double?,
    isDay: Boolean,
    weatherEmoji: String?,
    weatherDescription: String?,
    weatherCode: Int? = null,
    visibility: Double? = null
): String? {
    // Prioridad: Tormenta > Nieve > Rachas > Viento > Temperatura

    // 1. Tormenta (prioridad máxima)
    val isStorm = weatherEmoji?.let { emoji ->
        emoji.contains("⛈") || emoji.contains("⚡")
    } ?: false

    val isStormByDescription = weatherDescription?.let { desc ->
        desc.contains("Tormenta", ignoreCase = true) ||
                desc.contains("granizo", ignoreCase = true) ||
                desc.contains("rayo", ignoreCase = true)
    } ?: false

    if (isStorm || isStormByDescription) {
        return "STORM"
    }

    // 2. Nieve (weatherCode 71, 73, 75, 77, 85, 86 o emoji ❄️)
    // La nieve es muy peligrosa en patinete por el riesgo de resbalar
    val isSnowByCode = weatherCode?.let { code ->
        code in listOf(71, 73, 75, 77, 85, 86)
    } ?: false

    val isSnowByEmoji = weatherEmoji?.let { emoji ->
        emoji.contains("❄️")
    } ?: false

    val isSnowByDescription = weatherDescription?.let { desc ->
        desc.contains("Nieve", ignoreCase = true) ||
                desc.contains("nevada", ignoreCase = true) ||
                desc.contains("snow", ignoreCase = true)
    } ?: false

    if (isSnowByCode || isSnowByEmoji || isSnowByDescription) {
        return "SNOW"
    }

    // 3. Rachas de viento muy fuertes (>60 km/h) - prioridad sobre viento normal
    val windGustsKmh = (windGusts ?: 0.0) * 3.6
    if (windGustsKmh > 60) {
        return "GUSTS"
    }

    // 4. Viento fuerte (>40 km/h)
    val windSpeedKmh = (windSpeed ?: 0.0) * 3.6
    if (windSpeedKmh > 40) {
        return "WIND"
    }

    // 5. Temperatura extrema
    if (temperature != null) {
        if (temperature < 0) {
            return "COLD"
        }
        if (temperature > 35) {
            return "HEAT"
        }
    }

    // 6. Índice UV muy alto (>8) - solo de día (se considera como calor)
    if (isDay && uvIndex != null && uvIndex > 8) {
        return "HEAT"
    }

    // 7. Visibilidad reducida (crítico para Barcelona - niebla/talaia)
    if (visibility != null) {
        val (isLowVisibility, _) = checkLowVisibility(visibility)
        if (isLowVisibility) {
            return "VISIBILITY"
        }
    }

    return null
}
}
