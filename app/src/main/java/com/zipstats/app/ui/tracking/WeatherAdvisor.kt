package com.zipstats.app.ui.tracking

import android.util.Log


class WeatherAdvisor {

    /**
     * Determina si hay lluvia activa confiando completamente en Google Weather.
     * Como fallback, usa la precipitación real si la condición/descripción no la detectan.
     *
     * @return Pair<Boolean, String> donde el Boolean indica si hay lluvia activa
     *         y el String es la razón amigable para el usuario
     */
    fun checkActiveRain(
        condition: String,
        description: String,
        precipitation: Double
    ): Pair<Boolean, String> {
        val cond = condition.uppercase()
        val desc = description.uppercase()

        // Términos que indican lluvia en la descripción de Google
        val rainTerms = listOf(
            "LLUVIA", "RAIN", "CHUBASCO", "TORMENTA", "DRIZZLE",
            "LLOVIZNA", "THUNDERSTORM", "SHOWER"
        )

        // Condiciones de Google que indican lluvia
        val rainConditions = listOf(
            "RAIN", "LIGHT_RAIN", "HEAVY_RAIN", "THUNDERSTORM", "DRIZZLE",
            "LIGHT_RAIN_SHOWERS", "RAIN_SHOWERS", "HEAVY_RAIN_SHOWERS",
            "CHANCE_OF_SHOWERS", "SCATTERED_SHOWERS"
        )

        // 1. Condición de Google indica lluvia
        if (rainConditions.any { cond.contains(it) }) {
            return true to "Lluvia detectada por Google"
        }

        // 2. Descripción de Google menciona lluvia
        if (rainTerms.any { desc.contains(it) }) {
            return true to "Lluvia detectada por Google"
        }

        // 3. Fallback: precipitación real aunque la condición no lo refleje
        // (Google a veces reporta PARTLY_CLOUDY con precipitación activa)
        if (precipitation >= 0.15 && desc.isBlank()) {
            return true to "Precipitación activa detectada (${precipitation}mm)"
        }

        return false to "No se detectó lluvia"
    }


    /**
     * Verifica si hay calzada húmeda cuando NO hay lluvia activa.
     *
     * Se activa por señales físicas basadas en datos de Google:
     * - Lluvia reciente (histórico últimas 3h con precipitación acumulada > 0)
     * - Alta humedad + cielo nublado/niebla (condensación)
     * - Persistencia por temporal: humedad >= 80% + precipitación 24h > 0 + noche o nublado
     * - Humedad extrema
     * - Nieve / aguanieve
     *
     * IMPORTANTE: Si hay lluvia activa (detectada por checkActiveRain), siempre retorna false.
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
        val windSpeedKmh = windSpeed ?: 0.0
        val dewSpread = if (temperature != null && dewPoint != null) temperature - dewPoint else null

        // 1. CORTE RÁPIDO
        if (hasActiveRain) return false

        // 2. FILTRO DE AUTOSECADO
        // El viento seca aunque sea de noche, especialmente con humedad baja.
        val canAutoDry = (isDay && humidity < 65) || (humidity < 45 && windSpeedKmh > 25.0)
        if (canAutoDry && recentPrecipitation3h < 0.1) {
            Log.d("WeatherAdvisor", "Autosecado detectado: viento o baja humedad evaporando agua.")
            return false
        }

        // ──────────────────────────────────────────────────────────
        // CONDICIONES DE CALZADA HÚMEDA
        // ──────────────────────────────────────────────────────────

        // 3. PERSISTENCIA TRAS LLUVIA (24h)
        // De noche seca más lento. De día con cielo cubierto también puede persistir.
        val isCloudy = cond.contains("CLOUD") || cond.contains("OVERCAST") ||
                cond == "FOG" || cond == "HAZE"
        val isStillWetFromPastRain = precip24h >= 2.0 && humidity > 75 && (!isDay || isCloudy)

        // 4. LLUVIA RECIENTE (3h)
        // Umbrales más sensibles si la humedad es alta.
        val hasRecentPrecipitation = (recentPrecipitation3h >= 0.5 && humidity > 70) ||
                (recentPrecipitation3h >= 0.25 && humidity > 88)

        // 5. CONDENSACIÓN Y ROCÍO
        val isCondensing = humidity >= 90 && (
                (cond == "FOG" || cond == "HAZE") ||
                        (!isDay && (dewSpread ?: 10.0) <= 2.0)
                )

        // 6. HUMEDAD EXTREMA (saturación del ambiente)
        val isExtremelyHumid =
            humidity >= 97 &&
                    !isDay &&
                    (dewSpread ?: 10.0) <= 2.5

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
     * Verifica si hay condiciones extremas.
     * Delega en detectExtremeCause para evitar duplicar lógica.
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
    ): Boolean = detectExtremeCause(
        windSpeed = windSpeed,
        windGusts = windGusts,
        temperature = temperature,
        uvIndex = uvIndex,
        isDay = isDay,
        weatherEmoji = weatherEmoji,
        weatherDescription = weatherDescription,
        weatherCode = weatherCode,
        visibility = visibility
    ) != null

    /**
     * Detecta la causa específica de condiciones extremas.
     * Retorna: "STORM", "SNOW", "GUSTS", "WIND", "COLD", "HEAT", "UV", "VISIBILITY" o null.
     * Prioridad: Tormenta > Nieve > Ráfagas > Viento > Temperatura > UV > Visibilidad
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

        // 1. Tormenta (prioridad máxima)
        val isStorm = weatherEmoji?.let { it.contains("⛈") || it.contains("⚡") } ?: false
        val isStormByDescription = weatherDescription?.let {
            it.contains("Tormenta", ignoreCase = true) ||
                    it.contains("granizo", ignoreCase = true) ||
                    it.contains("rayo", ignoreCase = true)
        } ?: false
        if (isStorm || isStormByDescription) return "STORM"

        // 2. Nieve (muy peligrosa en patinete por riesgo de resbalar)
        val isSnowByCode = weatherCode?.let { it in listOf(71, 73, 75, 77, 85, 86) } ?: false
        val isSnowByEmoji = weatherEmoji?.contains("❄️") ?: false
        val isSnowByDescription = weatherDescription?.let {
            it.contains("Nieve", ignoreCase = true) ||
                    it.contains("nevada", ignoreCase = true) ||
                    it.contains("snow", ignoreCase = true)
        } ?: false
        if (isSnowByCode || isSnowByEmoji || isSnowByDescription) return "SNOW"

        // 3. Ráfagas muy fuertes (>60 km/h)
        if ((windGusts ?: 0.0) > 60) return "GUSTS"

        // 4. Viento fuerte (>35 km/h)
        if ((windSpeed ?: 0.0) > 35) return "WIND"

        // 5. Temperatura extrema
        if (temperature != null) {
            if (temperature < 2) return "COLD"
            if (temperature > 33) return "HEAT"
        }

        // 6. Visibilidad reducida
        if (visibility != null) {
            val (isLowVisibility, _) = checkLowVisibility(visibility)
            if (isLowVisibility) return "VISIBILITY"
        }

        // 7. Índice UV muy alto (>=8, solo de día)
        if (isDay && uvIndex != null && uvIndex >= 8) return "UV"

        return null
    }
}
