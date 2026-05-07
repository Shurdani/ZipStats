package com.zipstats.app.ui.tracking

import android.util.Log
import java.time.LocalDate


class WeatherAdvisor {
    companion object {
        private const val TAG = "WeatherAdvisor"
    }

    private enum class ClimateProfile {
        MEDITERRANEAN_COASTAL,
        ATLANTIC_HUMID,
        CONTINENTAL_INTERIOR,
        SEMIARID_SOUTHEAST,
        MOUNTAIN,
        SUBTROPICAL_CANARY
    }

    private enum class SeasonBucket {
        COLD, SHOULDER, WARM
    }

    private data class WetRoadTuning(
        val humidityDryThreshold: Int,
        val windDryThreshold: Double,
        val persist24hMm: Double,
        val persistHumidity: Int,
        val persistSpreadMax: Double,
        val recentPrecipMainMm: Double,
        val recentPrecipHighHumidityMm: Double,
        val highHumidityThreshold: Int,
        val condenseHumidity: Int,
        val condenseSpreadMax: Double,
        val extremeHumidityThreshold: Int,
        val extremeHumiditySpreadMax: Double,
        val firstRainBoostMm: Double
    )

    private data class ProfileWeights(
        val mediterranean: Double,
        val atlantic: Double,
        val continental: Double,
        val semiarid: Double,
        val mountain: Double,
        val canary: Double
    ) {
        fun dominantProfile(): ClimateProfile {
            val weighted = listOf(
                ClimateProfile.MEDITERRANEAN_COASTAL to mediterranean,
                ClimateProfile.ATLANTIC_HUMID to atlantic,
                ClimateProfile.CONTINENTAL_INTERIOR to continental,
                ClimateProfile.SEMIARID_SOUTHEAST to semiarid,
                ClimateProfile.MOUNTAIN to mountain,
                ClimateProfile.SUBTROPICAL_CANARY to canary
            )
            return weighted.maxByOrNull { it.second }?.first ?: ClimateProfile.MEDITERRANEAN_COASTAL
        }
    }

    private fun currentSeasonBucket(month: Int = LocalDate.now().monthValue): SeasonBucket {
        return when (month) {
            12, 1, 2 -> SeasonBucket.COLD
            6, 7, 8 -> SeasonBucket.WARM
            else -> SeasonBucket.SHOULDER
        }
    }

    private fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

    private fun estimateCloudiness(condition: String, description: String): Double {
        val cond = condition.uppercase()
        val desc = description.uppercase()
        return when {
            cond.contains("OVERCAST") -> 0.95
            cond.contains("CLOUD") || cond == "FOG" || cond == "HAZE" -> 0.75
            cond.contains("RAIN") || cond.contains("SHOWER") || cond.contains("DRIZZLE") -> 0.85
            cond.contains("CLEAR") || cond.contains("SUNNY") -> 0.15
            desc.contains("NUBL") -> 0.75
            desc.contains("DESPEJ") || desc.contains("SOLEAD") -> 0.2
            else -> 0.5
        }
    }

    private fun estimateEffectiveSolar(
        isDay: Boolean,
        uvIndex: Double?,
        cloudiness: Double,
        humidity: Int
    ): Double {
        if (!isDay) return 0.0
        val uvFactor = clamp01((uvIndex ?: 4.0) / 10.0)
        val cloudFactor = clamp01(1.0 - cloudiness)
        val humidityPenalty = clamp01((humidity - 60) / 60.0) * 0.25
        return clamp01((uvFactor * 0.65 + cloudFactor * 0.35) - humidityPenalty)
    }

    private fun normalizeWeights(raw: List<Double>): List<Double> {
        val clipped = raw.map { it.coerceAtLeast(0.0) }
        val total = clipped.sum()
        if (total <= 0.0) return List(raw.size) { 1.0 / raw.size }
        return clipped.map { it / total }
    }

    private fun computeProfileWeights(
        condition: String,
        description: String,
        humidity: Int,
        recentPrecipitation3h: Double,
        precip24h: Double,
        temperature: Double?,
        dewSpread: Double?,
        windSpeedKmh: Double,
        weatherEmoji: String?,
        uvIndex: Double?,
        isDay: Boolean
    ): ProfileWeights {
        val cond = condition.uppercase()
        val desc = description.uppercase()
        val spread = dewSpread ?: 10.0
        val cloudiness = estimateCloudiness(cond, desc)
        val effectiveSolar = estimateEffectiveSolar(isDay, uvIndex, cloudiness, humidity)
        val totalPrecipSignal = recentPrecipitation3h + precip24h * 0.25

        val hasSnowSignals = cond.contains("SNOW") ||
            cond.contains("SLEET") ||
            desc.contains("NIEVE") ||
            desc.contains("AGUANIEVE") ||
            weatherEmoji?.contains("❄️") == true

        val mountainScore =
            (if (hasSnowSignals) 0.75 else 0.0) +
                (if ((temperature ?: 99.0) <= 4.0 && spread <= 3.0) 0.35 else 0.0) +
                clamp01((85 - (temperature ?: 20.0)) / 85.0) * 0.15

        val atlanticScore =
            clamp01((humidity - 78) / 22.0) * 0.45 +
                clamp01((totalPrecipSignal - 0.2) / 2.0) * 0.35 +
                clamp01((20.0 - windSpeedKmh) / 20.0) * 0.20

        val semiaridScore =
            clamp01((70 - humidity) / 35.0) * 0.40 +
                clamp01((1.0 - totalPrecipSignal) / 1.0) * 0.30 +
                clamp01((windSpeedKmh - 8.0) / 25.0) * 0.15 +
                effectiveSolar * 0.15

        val canaryScore =
            clamp01((humidity - 75) / 25.0) * 0.35 +
                clamp01((1.5 - totalPrecipSignal) / 1.5) * 0.30 +
                clamp01((2.8 - spread) / 2.8) * 0.20 +
                clamp01((28.0 - kotlin.math.abs((temperature ?: 21.0) - 21.0)) / 28.0) * 0.15

        val continentalScore =
            clamp01((35.0 - humidity) / 35.0) * 0.25 +
                clamp01(((temperature ?: 20.0) - 28.0) / 12.0) * 0.20 +
                clamp01((windSpeedKmh - 6.0) / 24.0) * 0.20 +
                effectiveSolar * 0.20 +
                clamp01((3.0 - totalPrecipSignal) / 3.0) * 0.15

        // Priorizamos mediterráneo para uso principal actual (Barcelona/alrededores),
        // pero sin bloquear que otros perfiles ganen cuando las señales lo justifiquen.
        val mediterraneanScore =
            0.22 +
                effectiveSolar * 0.30 +
                clamp01((humidity - 58) / 35.0) * 0.15 +
                clamp01((2.5 - kotlin.math.abs(totalPrecipSignal - 1.0)) / 2.5) * 0.18 +
                clamp01((22.0 - windSpeedKmh) / 22.0) * 0.15

        val normalized = normalizeWeights(
            listOf(
                mediterraneanScore,
                atlanticScore,
                continentalScore,
                semiaridScore,
                mountainScore,
                canaryScore
            )
        )

        return ProfileWeights(
            mediterranean = normalized[0],
            atlantic = normalized[1],
            continental = normalized[2],
            semiarid = normalized[3],
            mountain = normalized[4],
            canary = normalized[5]
        )
    }

    private fun getWetRoadTuning(profile: ClimateProfile, season: SeasonBucket): WetRoadTuning {
        return when (profile) {
            ClimateProfile.MEDITERRANEAN_COASTAL -> when (season) {
                SeasonBucket.WARM -> WetRoadTuning(78, 12.0, 8.0, 90, 2.4, 1.0, 0.5, 94, 95, 1.4, 99, 1.7, 0.30)
                SeasonBucket.COLD -> WetRoadTuning(68, 16.0, 5.2, 85, 3.1, 0.65, 0.30, 91, 92, 2.1, 97, 2.3, 0.20)
                SeasonBucket.SHOULDER -> WetRoadTuning(72, 14.0, 6.0, 88, 3.0, 0.8, 0.35, 92, 93, 1.8, 98, 2.0, 0.25)
            }
            ClimateProfile.ATLANTIC_HUMID -> when (season) {
                SeasonBucket.WARM -> WetRoadTuning(74, 14.0, 3.0, 82, 3.8, 0.35, 0.15, 88, 89, 2.6, 95, 2.9, 0.15)
                SeasonBucket.COLD -> WetRoadTuning(70, 16.0, 2.2, 80, 4.0, 0.25, 0.10, 86, 87, 2.8, 94, 3.0, 0.10)
                SeasonBucket.SHOULDER -> WetRoadTuning(72, 15.0, 2.6, 81, 3.9, 0.30, 0.12, 87, 88, 2.7, 94, 3.0, 0.12)
            }
            ClimateProfile.CONTINENTAL_INTERIOR -> when (season) {
                SeasonBucket.WARM -> WetRoadTuning(74, 13.0, 7.0, 88, 2.6, 0.9, 0.4, 93, 94, 1.6, 98, 1.9, 0.22)
                SeasonBucket.COLD -> WetRoadTuning(66, 17.0, 4.2, 84, 3.4, 0.55, 0.25, 90, 91, 2.2, 97, 2.4, 0.15)
                SeasonBucket.SHOULDER -> WetRoadTuning(70, 15.0, 5.4, 86, 3.1, 0.7, 0.3, 91, 92, 1.9, 98, 2.2, 0.18)
            }
            ClimateProfile.SEMIARID_SOUTHEAST -> when (season) {
                SeasonBucket.WARM -> WetRoadTuning(82, 11.0, 10.0, 91, 2.2, 1.2, 0.55, 95, 96, 1.3, 99, 1.5, 0.45)
                SeasonBucket.COLD -> WetRoadTuning(72, 15.0, 6.5, 87, 2.8, 0.8, 0.35, 92, 93, 1.8, 98, 2.1, 0.30)
                SeasonBucket.SHOULDER -> WetRoadTuning(77, 13.0, 8.0, 89, 2.5, 1.0, 0.45, 94, 95, 1.6, 99, 1.8, 0.35)
            }
            ClimateProfile.MOUNTAIN -> when (season) {
                SeasonBucket.WARM -> WetRoadTuning(72, 16.0, 4.5, 83, 3.5, 0.55, 0.25, 90, 91, 2.3, 96, 2.6, 0.12)
                SeasonBucket.COLD -> WetRoadTuning(66, 18.0, 3.2, 80, 4.2, 0.35, 0.15, 88, 89, 2.8, 95, 3.0, 0.08)
                SeasonBucket.SHOULDER -> WetRoadTuning(69, 17.0, 3.8, 82, 3.8, 0.45, 0.20, 89, 90, 2.5, 95, 2.8, 0.10)
            }
            ClimateProfile.SUBTROPICAL_CANARY -> when (season) {
                SeasonBucket.WARM -> WetRoadTuning(80, 12.0, 7.5, 90, 2.1, 1.0, 0.45, 94, 95, 1.4, 99, 1.7, 0.20)
                SeasonBucket.COLD -> WetRoadTuning(74, 14.0, 5.5, 87, 2.7, 0.75, 0.30, 92, 93, 1.9, 98, 2.2, 0.15)
                SeasonBucket.SHOULDER -> WetRoadTuning(77, 13.0, 6.2, 88, 2.4, 0.85, 0.35, 93, 94, 1.7, 98, 2.0, 0.18)
            }
        }
    }

    private fun blendWetRoadTuning(weights: ProfileWeights, season: SeasonBucket): WetRoadTuning {
        val med = getWetRoadTuning(ClimateProfile.MEDITERRANEAN_COASTAL, season)
        val atl = getWetRoadTuning(ClimateProfile.ATLANTIC_HUMID, season)
        val con = getWetRoadTuning(ClimateProfile.CONTINENTAL_INTERIOR, season)
        val sem = getWetRoadTuning(ClimateProfile.SEMIARID_SOUTHEAST, season)
        val mou = getWetRoadTuning(ClimateProfile.MOUNTAIN, season)
        val can = getWetRoadTuning(ClimateProfile.SUBTROPICAL_CANARY, season)

        fun mixDouble(selector: (WetRoadTuning) -> Double): Double {
            return selector(med) * weights.mediterranean +
                selector(atl) * weights.atlantic +
                selector(con) * weights.continental +
                selector(sem) * weights.semiarid +
                selector(mou) * weights.mountain +
                selector(can) * weights.canary
        }

        fun mixInt(selector: (WetRoadTuning) -> Int): Int = mixDouble { selector(it).toDouble() }.toInt()

        return WetRoadTuning(
            humidityDryThreshold = mixInt { it.humidityDryThreshold },
            windDryThreshold = mixDouble { it.windDryThreshold },
            persist24hMm = mixDouble { it.persist24hMm },
            persistHumidity = mixInt { it.persistHumidity },
            persistSpreadMax = mixDouble { it.persistSpreadMax },
            recentPrecipMainMm = mixDouble { it.recentPrecipMainMm },
            recentPrecipHighHumidityMm = mixDouble { it.recentPrecipHighHumidityMm },
            highHumidityThreshold = mixInt { it.highHumidityThreshold },
            condenseHumidity = mixInt { it.condenseHumidity },
            condenseSpreadMax = mixDouble { it.condenseSpreadMax },
            extremeHumidityThreshold = mixInt { it.extremeHumidityThreshold },
            extremeHumiditySpreadMax = mixDouble { it.extremeHumiditySpreadMax },
            firstRainBoostMm = mixDouble { it.firstRainBoostMm }
        )
    }

    fun detectClimateProfileTag(
        condition: String,
        description: String,
        humidity: Int,
        recentPrecipitation3h: Double,
        precip24h: Double,
        temperature: Double?,
        dewPoint: Double?,
        windSpeed: Double?,
        weatherEmoji: String?,
        uvIndex: Double? = null,
        isDay: Boolean = true
    ): String {
        val dewSpread = if (temperature != null && dewPoint != null) temperature - dewPoint else null
        val weights = computeProfileWeights(
            condition = condition,
            description = description,
            humidity = humidity,
            recentPrecipitation3h = recentPrecipitation3h,
            precip24h = precip24h,
            temperature = temperature,
            dewSpread = dewSpread,
            windSpeedKmh = windSpeed ?: 0.0,
            weatherEmoji = weatherEmoji,
            uvIndex = uvIndex,
            isDay = isDay
        )
        return weights.dominantProfile().name
    }

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
     * Perfil ajustado para clima mediterráneo (Barcelona):
     * - Prioriza lluvia reciente (3h) y señales claras de condensación real.
     * - Reduce falsos positivos por lluvia antigua (24h) si hay condiciones de secado.
     * - Aplica autosecado más agresivo en horas diurnas con humedad moderada y/o viento.
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
        uvIndex: Double? = null,
        weatherEmoji: String? = null,
        weatherDescription: String? = null,
        windSpeed: Double? = null
    ): Boolean {

        val cond = condition.uppercase()
        val desc = weatherDescription?.uppercase().orEmpty()
        val windSpeedKmh = windSpeed ?: 0.0
        val dewSpread = if (temperature != null && dewPoint != null) temperature - dewPoint else null
        val profileWeights = computeProfileWeights(
            condition = cond,
            description = desc,
            humidity = humidity,
            recentPrecipitation3h = recentPrecipitation3h,
            precip24h = precip24h,
            temperature = temperature,
            dewSpread = dewSpread,
            windSpeedKmh = windSpeedKmh,
            weatherEmoji = weatherEmoji,
            uvIndex = uvIndex,
            isDay = isDay
        )
        val dominantProfile = profileWeights.dominantProfile()
        val tuning = blendWetRoadTuning(profileWeights, currentSeasonBucket())
        Log.d(
            TAG,
            "WetRoadInput: profile=$dominantProfile " +
                "weights=[med=${"%.2f".format(profileWeights.mediterranean)}, " +
                "atl=${"%.2f".format(profileWeights.atlantic)}, " +
                "con=${"%.2f".format(profileWeights.continental)}, " +
                "sem=${"%.2f".format(profileWeights.semiarid)}, " +
                "mou=${"%.2f".format(profileWeights.mountain)}, " +
                "can=${"%.2f".format(profileWeights.canary)}] " +
                "cond=$cond desc=$desc isDay=$isDay humidity=$humidity " +
                "recent3h=${"%.2f".format(recentPrecipitation3h)} " +
                "precip24h=${"%.2f".format(precip24h)} " +
                "temp=${temperature ?: "null"} dewPoint=${dewPoint ?: "null"} " +
                "wind=${"%.2f".format(windSpeedKmh)} uv=${uvIndex ?: "null"}"
        )

        // 1. CORTE RÁPIDO
        if (hasActiveRain) {
            Log.d(TAG, "WetRoadSkip[$dominantProfile]: salto calzada mojada porque hay lluvia activa.")
            return false
        }

        // 2. FILTRO DE AUTOSECADO (mediterráneo)
        // Con sol y brisa moderada, el asfalto suele secar relativamente rápido.
        val canAutoDry = (
            (isDay && humidity <= tuning.humidityDryThreshold && recentPrecipitation3h < 0.3) ||
            (isDay && humidity < (tuning.humidityDryThreshold + 8) && windSpeedKmh >= tuning.windDryThreshold && recentPrecipitation3h < 0.5) ||
            (!isDay && humidity < 60 && windSpeedKmh > 20.0)
        )
        if (canAutoDry && recentPrecipitation3h < 0.1) {
            Log.d(
                TAG,
                "WetRoadSkip[$dominantProfile]: autosecado detectado " +
                    "(isDay=$isDay, humidity=$humidity, wind=${"%.2f".format(windSpeedKmh)}, " +
                    "recent3h=${"%.2f".format(recentPrecipitation3h)})."
            )
            return false
        }

        // ──────────────────────────────────────────────────────────
        // CONDICIONES DE CALZADA HÚMEDA
        // ──────────────────────────────────────────────────────────

        // 3. PERSISTENCIA TRAS LLUVIA (24h)
        // Más estricta para evitar arrastrar "mojado" toda la tarde tras lluvia matinal.
        val isCloudy = cond.contains("CLOUD") || cond.contains("OVERCAST") ||
                cond == "FOG" || cond == "HAZE"
        val spread = dewSpread ?: 10.0
        val low24hContext = precip24h < 1.0
        val adjustedRecentPrecipMain = if (low24hContext) {
            (tuning.recentPrecipMainMm - tuning.firstRainBoostMm).coerceAtLeast(0.2)
        } else {
            tuning.recentPrecipMainMm
        }
        val isStillWetFromPastRain =
            (
                precip24h >= tuning.persist24hMm &&
                    humidity >= tuning.persistHumidity &&
                    (!isDay || isCloudy) &&
                    spread <= tuning.persistSpreadMax &&
                    windSpeedKmh < 18.0
                ) ||
            (
                precip24h >= 10.0 &&
                    !isDay &&
                    humidity >= 82 &&
                    windSpeedKmh < 22.0
                )

        // 4. LLUVIA RECIENTE (3h)
        // Umbrales menos sensibles para reducir falsos positivos.
        val hasRecentPrecipitation =
            (recentPrecipitation3h >= adjustedRecentPrecipMain && humidity >= 75) ||
                (recentPrecipitation3h >= tuning.recentPrecipHighHumidityMm && humidity >= tuning.highHumidityThreshold)

        // 5. CONDENSACIÓN Y ROCÍO
        val isCondensing = humidity >= tuning.condenseHumidity && (
                (cond == "FOG" || cond == "HAZE") ||
                        (!isDay && spread <= tuning.condenseSpreadMax)
                )

        // 6. HUMEDAD EXTREMA (saturación del ambiente)
        val isExtremelyHumid =
            humidity >= tuning.extremeHumidityThreshold &&
                    !isDay &&
                    spread <= tuning.extremeHumiditySpreadMax &&
                    windSpeedKmh < 15.0

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
            Log.d(
                TAG,
                "WetRoadResult[$dominantProfile]: MOJADA. motivo=$motivo " +
                    "flags=[condensing=$isCondensing, extremeHumid=$isExtremelyHumid, " +
                    "snowOrSleet=$hasSnowOrSleet, recentPrecip=$hasRecentPrecipitation, " +
                    "pastRain=$isStillWetFromPastRain]"
            )
        } else {
            Log.d(
                TAG,
                "WetRoadResult[$dominantProfile]: NO_MOJADA. " +
                    "flags=[condensing=$isCondensing, extremeHumid=$isExtremelyHumid, " +
                    "snowOrSleet=$hasSnowOrSleet, recentPrecip=$hasRecentPrecipitation, " +
                    "pastRain=$isStillWetFromPastRain] " +
                    "thresholds=[recentMain=${"%.2f".format(adjustedRecentPrecipMain)}, " +
                    "recentHighHum=${"%.2f".format(tuning.recentPrecipHighHumidityMm)}, " +
                    "persist24h=${"%.2f".format(tuning.persist24hMm)}, " +
                    "persistHumidity=${tuning.persistHumidity}]"
            )
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