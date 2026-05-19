package com.zipstats.app.ui.tracking

import android.util.Log
import java.time.LocalDate
import java.time.Month

class WeatherAdvisor {

    // ─────────────────────────────────────────────────────────────
    // TIPOS PÚBLICOS: Estaciones, regiones y perfil de umbrales
    // ─────────────────────────────────────────────────────────────

    enum class Season { WINTER, SPRING, SUMMER, AUTUMN }

    /**
     * Regiones climáticas para Iberia. El valor por defecto es
     * [MEDITERRANEAN_COAST] porque la app se diseñó priorizando
     * Barcelona y la costa este (Tarragona–Girona–Valencia).
     */
    enum class ClimateRegion {
        MEDITERRANEAN_COAST,  // Barcelona, Tarragona, Girona, Valencia, Castellón, Alicante, Baleares, Ceuta
        INLAND_DRY,           // Madrid, Aragón sur, Castilla-La Mancha, Extremadura norte (continental seco)
        ATLANTIC_HUMID,       // Galicia, Asturias, Cantabria, País Vasco, norte de Navarra
        SOUTHERN_HOT,         // Andalucía, Murcia interior, Extremadura sur, Melilla (semi-árido cálido)
        CONTINENTAL_COLD,     // Castilla y León, Pirineos, La Rioja alta (frío de altitud)
        SUBTROPICAL_OCEANIC,  // Canarias (clima suave, vientos alisios, baja oscilación térmica)
        GENERIC               // Sin clasificar / perfil moderado
    }

    /**
     * Preferencia de región climática del usuario (ajustes).
     * [AUTOMATIC] delega en el GPS; el resto fuerza un perfil fijo en toda la ruta.
     */
    enum class ClimateRegionPreference(val displayName: String) {
        AUTOMATIC("Automático (GPS)"),
        MEDITERRANEAN_COAST("Costa mediterránea"),
        INLAND_DRY("Interior seco"),
        ATLANTIC_HUMID("Atlántico húmedo"),
        SOUTHERN_HOT("Sur cálido"),
        CONTINENTAL_COLD("Continental frío"),
        SUBTROPICAL_OCEANIC("Canarias"),
        GENERIC("Genérica");

        /** null si es automático; en caso contrario la región forzada. */
        fun toClimateRegionOrNull(): ClimateRegion? = when (this) {
            AUTOMATIC -> null
            else -> ClimateRegion.valueOf(name)
        }

        companion object {
            fun fromName(name: String?): ClimateRegionPreference =
                entries.find { it.name == name } ?: AUTOMATIC
        }
    }

    /**
     * Umbrales que controlan cuándo se considera la calzada mojada.
     * Cada par (región, estación) define su propio perfil para
     * minimizar falsos positivos.
     */
    data class WetRoadThresholds(
        // Condensación / rocío (situación nocturna húmeda con dewpoint cercano).
        val condensationHumidity: Int,
        val condensationDewSpread: Double,

        // Humedad extrema (saturación efectiva del ambiente).
        val extremeHumidity: Int,
        val extremeDewSpread: Double,

        // Persistencia tras lluvia (acumulado 24 h).
        val precip24hThreshold: Double,
        val precip24hHumidity: Int,

        // Lluvia reciente (acumulado 3 h).
        val recent3hThreshold: Double,
        val recent3hHumidity: Int,
        val recent3hStrongThreshold: Double,   // umbral más bajo si hay mucha humedad
        val recent3hStrongHumidity: Int,

        // Autosecado del asfalto.
        val autoDryHumidityDay: Int,
        val autoDryHumidityWind: Int,
        val autoDryWindKmh: Double,
        val warmAsphaltTemp: Double,           // ≥ esta temp → asfalto cálido seca incluso de noche
        val coldAsphaltTemp: Double            // ≤ esta temp → asfalto frío retiene agua mucho más
    )

    // ─────────────────────────────────────────────────────────────
    // 1. LLUVIA ACTIVA (sin cambios funcionales)
    // ─────────────────────────────────────────────────────────────

    /**
     * Determina si hay lluvia activa confiando completamente en Google Weather.
     * Como fallback, usa la precipitación real si la condición/descripción no la detectan.
     */
    fun checkActiveRain(
        condition: String,
        description: String,
        precipitation: Double
    ): Pair<Boolean, String> {
        val cond = condition.uppercase()
        val desc = description.uppercase()

        val rainTerms = listOf(
            "LLUVIA", "RAIN", "CHUBASCO", "TORMENTA", "DRIZZLE",
            "LLOVIZNA", "THUNDERSTORM", "SHOWER"
        )
        val rainConditions = listOf(
            "RAIN", "LIGHT_RAIN", "HEAVY_RAIN", "THUNDERSTORM", "DRIZZLE",
            "LIGHT_RAIN_SHOWERS", "RAIN_SHOWERS", "HEAVY_RAIN_SHOWERS",
            "CHANCE_OF_SHOWERS", "SCATTERED_SHOWERS"
        )

        if (rainConditions.any { cond.contains(it) }) {
            return true to "Lluvia detectada por Google"
        }
        if (rainTerms.any { desc.contains(it) }) {
            return true to "Lluvia detectada por Google"
        }
        if (precipitation >= 0.15 && desc.isBlank()) {
            return true to "Precipitación activa detectada (${precipitation}mm)"
        }

        return false to "No se detectó lluvia"
    }

    // ─────────────────────────────────────────────────────────────
    // 2. CALZADA MOJADA (refactor con perfiles por región / estación)
    // ─────────────────────────────────────────────────────────────

    /**
     * Verifica si hay calzada húmeda cuando NO hay lluvia activa.
     *
     * Se activa por señales físicas basadas en datos meteorológicos:
     *  - Lluvia reciente (acumulado 3 h)
     *  - Persistencia por temporal (acumulado 24 h)
     *  - Alta humedad + cielo nublado/niebla (condensación)
     *  - Humedad extrema con dewpoint pegado a la temperatura
     *  - Nieve / aguanieve
     *
     * Los umbrales se ajustan automáticamente según la estación del año
     * y la [ClimateRegion]. Por defecto se usa [ClimateRegion.MEDITERRANEAN_COAST]
     * (Barcelona y costa este), y la estación se infiere del mes actual y
     * de la temperatura (si está disponible).
     *
     * IMPORTANTE: Si hay lluvia activa, siempre retorna false.
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
        windSpeed: Double? = null,
        climateRegion: ClimateRegion = ClimateRegion.MEDITERRANEAN_COAST,
        season: Season? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): Boolean {

        // 0. CORTE RÁPIDO
        if (hasActiveRain) return false

        val cond = condition.uppercase()
        val desc = weatherDescription?.uppercase().orEmpty()
        val windSpeedKmh = windSpeed ?: 0.0
        val dewSpread = if (temperature != null && dewPoint != null) temperature - dewPoint else null

        val effectiveSeason = season ?: detectSeason(temperature)
        val t = getThresholds(climateRegion, effectiveSeason)

        val coordsTag = if (latitude != null && longitude != null) {
            " coords=(%.4f, %.4f)".format(latitude, longitude)
        } else ""

        // ──────────────────────────────────────────────────────────
        // NIEVE / AGUANIEVE (señal categórica, no depende de umbrales)
        // ──────────────────────────────────────────────────────────
        val hasSnowOrSleet = cond.contains("SNOW") || cond.contains("SLEET") ||
                desc.contains("NIEVE") || desc.contains("AGUANIEVE") ||
                weatherEmoji?.contains("❄️") == true
        if (hasSnowOrSleet) {
            Log.d(TAG, "Calzada MOJADA. Motivo: Nieve/Aguanieve [$climateRegion/$effectiveSeason]$coordsTag")
            return true
        }

        // ──────────────────────────────────────────────────────────
        // FILTRO DE AUTOSECADO
        // ──────────────────────────────────────────────────────────
        // a) De día con humedad baja → seca rápido.
        // b) Viento fuerte con humedad moderada → seca rápido.
        // c) Asfalto cálido (verano nocturno o tras día caluroso) → seca aunque sea de noche.
        val isWarmAsphalt = (temperature ?: -100.0) >= t.warmAsphaltTemp
        val isColdAsphalt = (temperature ?: 100.0) <= t.coldAsphaltTemp

        val canAutoDryByDay = isDay && humidity < t.autoDryHumidityDay
        val canAutoDryByWind = humidity < t.autoDryHumidityWind && windSpeedKmh > t.autoDryWindKmh
        // El asfalto caliente seca de noche siempre que no haya llovido fuerte hace poco.
        val canAutoDryByWarmAsphalt = isWarmAsphalt && recentPrecipitation3h < 1.0

        val canAutoDry = canAutoDryByDay || canAutoDryByWind || canAutoDryByWarmAsphalt

        // Si el ambiente puede secar y no hubo lluvia significativa en 3 h → seguro seco.
        if (canAutoDry && recentPrecipitation3h < 0.15) {
            Log.d(
                TAG,
                "Autosecado [$climateRegion/$effectiveSeason]$coordsTag: day=$canAutoDryByDay, " +
                        "wind=$canAutoDryByWind, warmAsphalt=$canAutoDryByWarmAsphalt"
            )
            return false
        }

        // ──────────────────────────────────────────────────────────
        // CONDICIONES DE CALZADA HÚMEDA (todas con umbrales del perfil)
        // ──────────────────────────────────────────────────────────

        // 1. PERSISTENCIA TRAS LLUVIA (24h)
        // Solo cuenta de noche o con cielo cubierto. En asfalto cálido se descarta
        // porque el agua acumulada ya se habrá evaporado.
        val isCloudy = cond.contains("CLOUD") || cond.contains("OVERCAST") ||
                cond == "FOG" || cond == "HAZE"
        val isStillWetFromPastRain = precip24h >= t.precip24hThreshold &&
                humidity >= t.precip24hHumidity &&
                (!isDay || isCloudy) &&
                !isWarmAsphalt

        // 2. LLUVIA RECIENTE (3h). Doble umbral: ligera con humedad muy alta o
        // moderada con humedad alta. En asfalto cálido se exige más cantidad.
        val effectiveRecent3hThreshold = if (isWarmAsphalt) t.recent3hThreshold * 2.0
            else t.recent3hThreshold
        val effectiveRecent3hStrong = if (isWarmAsphalt) t.recent3hStrongThreshold * 2.0
            else t.recent3hStrongThreshold

        val hasRecentPrecipitation =
            (recentPrecipitation3h >= effectiveRecent3hThreshold && humidity >= t.recent3hHumidity) ||
                    (recentPrecipitation3h >= effectiveRecent3hStrong && humidity >= t.recent3hStrongHumidity)

        // 3. CONDENSACIÓN / ROCÍO
        // Requiere dewpoint MUY cercano a la temperatura. En verano (asfalto cálido)
        // se descarta porque la superficie no llega al punto de rocío.
        val isCondensing = !isWarmAsphalt &&
                humidity >= t.condensationHumidity && (
                (cond == "FOG" || cond == "HAZE") ||
                        (!isDay && (dewSpread ?: 10.0) <= t.condensationDewSpread)
                )

        // 4. HUMEDAD EXTREMA (saturación nocturna)
        // Limitada a noche y a asfalto NO cálido para evitar falsos positivos de verano.
        val isExtremelyHumid = !isWarmAsphalt &&
                !isDay &&
                humidity >= t.extremeHumidity &&
                (dewSpread ?: 10.0) <= t.extremeDewSpread

        // ──────────────────────────────────────────────────────────
        // RESULTADO FINAL
        // ──────────────────────────────────────────────────────────
        val isWet = isCondensing ||
                isExtremelyHumid ||
                hasRecentPrecipitation ||
                isStillWetFromPastRain

        if (isWet) {
            val motivo = when {
                isCondensing -> "Condensación/Niebla"
                isStillWetFromPastRain -> "Persistencia de lluvia previa (${precip24h}mm en 24h)"
                hasRecentPrecipitation -> "Lluvia reciente (${recentPrecipitation3h}mm)"
                isExtremelyHumid -> "Humedad extrema ($humidity%)"
                else -> "Desconocido"
            }
            Log.d(
                TAG,
                "Calzada MOJADA [$climateRegion/$effectiveSeason]$coordsTag. Motivo: $motivo " +
                        "(T=$temperature, H=$humidity%, viento=${windSpeedKmh}km/h, " +
                        "warmAsphalt=$isWarmAsphalt, coldAsphalt=$isColdAsphalt)"
            )
        }

        return isWet
    }

    // ─────────────────────────────────────────────────────────────
    // DETECCIÓN AUTOMÁTICA DE ESTACIÓN Y REGIÓN
    // ─────────────────────────────────────────────────────────────

    /**
     * Deduce la estación a partir del mes actual del sistema.
     * Si se proporciona [temperature], puede corregir el resultado
     * en olas de calor (≥ 28 °C en cualquier mes ⇒ veraniego) o
     * de frío extremo (≤ 6 °C ⇒ invernal).
     */
    fun detectSeason(temperature: Double? = null): Season {
        val month = LocalDate.now().month
        val baseSeason = when (month) {
            Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> Season.WINTER
            Month.MARCH, Month.APRIL, Month.MAY -> Season.SPRING
            Month.JUNE, Month.JULY, Month.AUGUST -> Season.SUMMER
            Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> Season.AUTUMN
            else -> Season.SPRING
        }
        if (temperature != null) {
            if (temperature >= 28.0) return Season.SUMMER
            if (temperature <= 6.0) return Season.WINTER
        }
        return baseSeason
    }

    /**
     * Estima la región climática a partir de coordenadas. Cubre todo el
     * territorio español: Península, Islas Baleares, Islas Canarias,
     * Ceuta y Melilla. Para coordenadas fuera de España devuelve
     * [ClimateRegion.GENERIC].
     *
     * Reparto por climas:
     *  - Mediterránea: Cataluña costera, Valencia, Murcia y Almería costera,
     *    Baleares y Ceuta.
     *  - Atlántica: Galicia, Asturias, Cantabria, País Vasco y norte de Navarra.
     *  - Continental fría: Castilla y León, Pirineos y La Rioja alta.
     *  - Sur cálido: Andalucía, Extremadura sur, Murcia interior y Melilla.
     *  - Subtropical oceánico: Islas Canarias.
     *  - Interior seco: Madrid, Castilla-La Mancha, Aragón sur y Extremadura norte.
     */
    fun inferClimateRegion(latitude: Double?, longitude: Double?): ClimateRegion {
        if (latitude == null || longitude == null) return ClimateRegion.MEDITERRANEAN_COAST

        val lat = latitude
        val lon = longitude

        // ─── 1. Casos especiales fuera de la península ───
        // Islas Canarias (lat 27.5°–29.5°, lon −18.5° a −13.0°)
        if (lat in 27.0..29.6 && lon in -18.5..-13.0) return ClimateRegion.SUBTROPICAL_OCEANIC

        // Ceuta (~35.89°N, −5.32°O) — clima mediterráneo suave
        if (lat in 35.85..35.95 && lon in -5.40..-5.25) return ClimateRegion.MEDITERRANEAN_COAST

        // Melilla (~35.29°N, −2.94°O) — clima semiárido cálido
        if (lat in 35.20..35.40 && lon in -3.05..-2.85) return ClimateRegion.SOUTHERN_HOT

        // ─── 2. Fuera de España (Península, Baleares, Canarias, plazas norteafricanas) ───
        if (lat !in 36.0..44.5 || lon !in -10.0..5.0) return ClimateRegion.GENERIC

        // ─── 3. Islas Baleares (Mallorca, Menorca, Ibiza, Formentera) ───
        if (lat in 38.5..40.30 && lon in 1.0..4.5) return ClimateRegion.MEDITERRANEAN_COAST

        // ─── 4. Norte atlántico/cantábrico ───
        // Galicia (lat 41.8–43.85, lon −9.3 a −6.7)
        if (lat in 41.80..43.85 && lon in -9.30..-6.70) return ClimateRegion.ATLANTIC_HUMID
        // Cordillera Cantábrica + País Vasco (lat ≥ 43.0, lon −7.0 a −1.4)
        if (lat >= 43.0 && lon in -7.0..-1.4) return ClimateRegion.ATLANTIC_HUMID
        // Norte de Navarra (lat 42.6–43.3, lon −2.5 a −0.7) — vertiente cantábrica
        if (lat in 42.60..43.30 && lon in -2.50..-0.70) return ClimateRegion.ATLANTIC_HUMID

        // ─── 5. Pirineos y altiplano interior frío ───
        // Pirineos centrales y orientales (lat ≥ 42.3, lon −1.4 a 2.3)
        if (lat >= 42.30 && lon in -1.40..2.30) return ClimateRegion.CONTINENTAL_COLD
        // Castilla y León + La Rioja alta + Soria (lat 40.5–43.0, lon −7.2 a −1.7)
        if (lat in 40.50..43.00 && lon in -7.20..-1.70) return ClimateRegion.CONTINENTAL_COLD

        // ─── 6. Costa mediterránea peninsular ───
        // Costa este desde Girona hasta Alicante (lat 36.7–42.5, lon ≥ −0.6)
        if (lat in 36.70..42.50 && lon >= -0.60) return ClimateRegion.MEDITERRANEAN_COAST
        // Costa de Murcia y Almería (lat 36.6–38.5, lon −2.7 a −0.6)
        if (lat in 36.60..38.50 && lon in -2.70..-0.60) return ClimateRegion.MEDITERRANEAN_COAST

        // ─── 7. Sur peninsular cálido ───
        // Andalucía (lat 36.0–38.5, lon −7.5 a −1.6)
        if (lat in 36.00..38.50 && lon in -7.50..-1.60) return ClimateRegion.SOUTHERN_HOT
        // Extremadura sur — Badajoz y bajo Guadiana (lat 38.0–39.5, lon −7.5 a −5.0)
        if (lat in 38.00..39.50 && lon in -7.50..-5.00) return ClimateRegion.SOUTHERN_HOT

        // ─── 8. Resto interior peninsular: Madrid, Castilla-La Mancha, Aragón sur, Extremadura norte ───
        return ClimateRegion.INLAND_DRY
    }

    // ─────────────────────────────────────────────────────────────
    // PERFILES DE UMBRALES POR REGIÓN × ESTACIÓN
    // ─────────────────────────────────────────────────────────────

    /**
     * Devuelve el perfil de umbrales calibrado para la región y estación dadas.
     * Los valores están ajustados para reducir falsos positivos:
     *  - Verano: asfalto cálido seca rápido → umbrales más estrictos.
     *  - Invierno: asfalto frío retiene agua → umbrales más laxos.
     *  - Atlántico húmedo: el aire está casi siempre saturado → muy estricto.
     */
    fun getThresholds(region: ClimateRegion, season: Season): WetRoadThresholds = when (region) {
        ClimateRegion.MEDITERRANEAN_COAST -> mediterraneanCoast(season)
        ClimateRegion.INLAND_DRY -> inlandDry(season)
        ClimateRegion.ATLANTIC_HUMID -> atlanticHumid(season)
        ClimateRegion.SOUTHERN_HOT -> southernHot(season)
        ClimateRegion.CONTINENTAL_COLD -> continentalCold(season)
        ClimateRegion.SUBTROPICAL_OCEANIC -> subtropicalOceanic(season)
        ClimateRegion.GENERIC -> generic(season)
    }

    // ─── Mediterránea (Barcelona / costa este) ─────────────────────
    private fun mediterraneanCoast(season: Season): WetRoadThresholds = when (season) {
        Season.SUMMER -> WetRoadThresholds(
            condensationHumidity = 95, condensationDewSpread = 1.0,
            extremeHumidity = 99, extremeDewSpread = 1.5,
            precip24hThreshold = 4.0, precip24hHumidity = 85,
            recent3hThreshold = 1.0, recent3hHumidity = 80,
            recent3hStrongThreshold = 0.5, recent3hStrongHumidity = 92,
            autoDryHumidityDay = 78, autoDryHumidityWind = 65,
            autoDryWindKmh = 18.0,
            warmAsphaltTemp = 24.0, coldAsphaltTemp = 14.0
        )
        Season.WINTER -> WetRoadThresholds(
            condensationHumidity = 88, condensationDewSpread = 2.5,
            extremeHumidity = 95, extremeDewSpread = 3.0,
            precip24hThreshold = 1.5, precip24hHumidity = 75,
            recent3hThreshold = 0.4, recent3hHumidity = 72,
            recent3hStrongThreshold = 0.2, recent3hStrongHumidity = 85,
            autoDryHumidityDay = 55, autoDryHumidityWind = 45,
            autoDryWindKmh = 25.0,
            warmAsphaltTemp = 18.0, coldAsphaltTemp = 6.0
        )
        Season.SPRING, Season.AUTUMN -> WetRoadThresholds(
            condensationHumidity = 92, condensationDewSpread = 2.0,
            extremeHumidity = 97, extremeDewSpread = 2.5,
            precip24hThreshold = 2.5, precip24hHumidity = 78,
            recent3hThreshold = 0.6, recent3hHumidity = 75,
            recent3hStrongThreshold = 0.3, recent3hStrongHumidity = 88,
            autoDryHumidityDay = 65, autoDryHumidityWind = 50,
            autoDryWindKmh = 22.0,
            warmAsphaltTemp = 21.0, coldAsphaltTemp = 9.0
        )
    }

    // ─── Interior seco (Madrid / Aragón) ───────────────────────────
    private fun inlandDry(season: Season): WetRoadThresholds = when (season) {
        Season.SUMMER -> WetRoadThresholds(
            condensationHumidity = 95, condensationDewSpread = 1.0,
            extremeHumidity = 99, extremeDewSpread = 1.5,
            precip24hThreshold = 5.0, precip24hHumidity = 80,
            recent3hThreshold = 1.2, recent3hHumidity = 75,
            recent3hStrongThreshold = 0.6, recent3hStrongHumidity = 90,
            autoDryHumidityDay = 70, autoDryHumidityWind = 55,
            autoDryWindKmh = 18.0,
            warmAsphaltTemp = 25.0, coldAsphaltTemp = 14.0
        )
        Season.WINTER -> WetRoadThresholds(
            condensationHumidity = 90, condensationDewSpread = 2.0,
            extremeHumidity = 96, extremeDewSpread = 2.5,
            precip24hThreshold = 1.2, precip24hHumidity = 75,
            recent3hThreshold = 0.4, recent3hHumidity = 72,
            recent3hStrongThreshold = 0.2, recent3hStrongHumidity = 88,
            autoDryHumidityDay = 50, autoDryHumidityWind = 40,
            autoDryWindKmh = 22.0,
            warmAsphaltTemp = 16.0, coldAsphaltTemp = 4.0
        )
        Season.SPRING, Season.AUTUMN -> WetRoadThresholds(
            condensationHumidity = 92, condensationDewSpread = 1.8,
            extremeHumidity = 97, extremeDewSpread = 2.2,
            precip24hThreshold = 2.5, precip24hHumidity = 76,
            recent3hThreshold = 0.6, recent3hHumidity = 72,
            recent3hStrongThreshold = 0.3, recent3hStrongHumidity = 88,
            autoDryHumidityDay = 60, autoDryHumidityWind = 45,
            autoDryWindKmh = 20.0,
            warmAsphaltTemp = 20.0, coldAsphaltTemp = 8.0
        )
    }

    // ─── Atlántico húmedo (Galicia / Cantábrico) ───────────────────
    private fun atlanticHumid(season: Season): WetRoadThresholds = when (season) {
        Season.SUMMER -> WetRoadThresholds(
            condensationHumidity = 93, condensationDewSpread = 1.5,
            extremeHumidity = 98, extremeDewSpread = 2.0,
            precip24hThreshold = 3.0, precip24hHumidity = 82,
            recent3hThreshold = 0.7, recent3hHumidity = 78,
            recent3hStrongThreshold = 0.4, recent3hStrongHumidity = 90,
            autoDryHumidityDay = 70, autoDryHumidityWind = 60,
            autoDryWindKmh = 20.0,
            warmAsphaltTemp = 22.0, coldAsphaltTemp = 13.0
        )
        Season.WINTER -> WetRoadThresholds(
            condensationHumidity = 90, condensationDewSpread = 3.0,
            extremeHumidity = 96, extremeDewSpread = 3.5,
            precip24hThreshold = 1.0, precip24hHumidity = 80,
            recent3hThreshold = 0.3, recent3hHumidity = 78,
            recent3hStrongThreshold = 0.15, recent3hStrongHumidity = 88,
            autoDryHumidityDay = 50, autoDryHumidityWind = 42,
            autoDryWindKmh = 28.0,
            warmAsphaltTemp = 16.0, coldAsphaltTemp = 5.0
        )
        Season.SPRING, Season.AUTUMN -> WetRoadThresholds(
            condensationHumidity = 91, condensationDewSpread = 2.5,
            extremeHumidity = 97, extremeDewSpread = 3.0,
            precip24hThreshold = 1.8, precip24hHumidity = 80,
            recent3hThreshold = 0.5, recent3hHumidity = 78,
            recent3hStrongThreshold = 0.25, recent3hStrongHumidity = 88,
            autoDryHumidityDay = 60, autoDryHumidityWind = 50,
            autoDryWindKmh = 24.0,
            warmAsphaltTemp = 19.0, coldAsphaltTemp = 8.0
        )
    }

    // ─── Sur cálido (Andalucía / Murcia) ───────────────────────────
    private fun southernHot(season: Season): WetRoadThresholds = when (season) {
        Season.SUMMER -> WetRoadThresholds(
            condensationHumidity = 96, condensationDewSpread = 0.8,
            extremeHumidity = 99, extremeDewSpread = 1.2,
            precip24hThreshold = 5.0, precip24hHumidity = 82,
            recent3hThreshold = 1.5, recent3hHumidity = 75,
            recent3hStrongThreshold = 0.7, recent3hStrongHumidity = 90,
            autoDryHumidityDay = 80, autoDryHumidityWind = 65,
            autoDryWindKmh = 16.0,
            warmAsphaltTemp = 26.0, coldAsphaltTemp = 15.0
        )
        Season.WINTER -> WetRoadThresholds(
            condensationHumidity = 88, condensationDewSpread = 2.5,
            extremeHumidity = 95, extremeDewSpread = 3.0,
            precip24hThreshold = 1.5, precip24hHumidity = 75,
            recent3hThreshold = 0.4, recent3hHumidity = 72,
            recent3hStrongThreshold = 0.2, recent3hStrongHumidity = 85,
            autoDryHumidityDay = 55, autoDryHumidityWind = 45,
            autoDryWindKmh = 22.0,
            warmAsphaltTemp = 19.0, coldAsphaltTemp = 8.0
        )
        Season.SPRING, Season.AUTUMN -> WetRoadThresholds(
            condensationHumidity = 93, condensationDewSpread = 1.8,
            extremeHumidity = 97, extremeDewSpread = 2.2,
            precip24hThreshold = 3.0, precip24hHumidity = 78,
            recent3hThreshold = 0.7, recent3hHumidity = 75,
            recent3hStrongThreshold = 0.4, recent3hStrongHumidity = 88,
            autoDryHumidityDay = 68, autoDryHumidityWind = 52,
            autoDryWindKmh = 20.0,
            warmAsphaltTemp = 22.0, coldAsphaltTemp = 10.0
        )
    }

    // ─── Continental frío (Castilla y León, interior alto) ─────────
    private fun continentalCold(season: Season): WetRoadThresholds = when (season) {
        Season.SUMMER -> WetRoadThresholds(
            condensationHumidity = 92, condensationDewSpread = 1.5,
            extremeHumidity = 98, extremeDewSpread = 2.0,
            precip24hThreshold = 4.0, precip24hHumidity = 82,
            recent3hThreshold = 0.9, recent3hHumidity = 78,
            recent3hStrongThreshold = 0.5, recent3hStrongHumidity = 90,
            autoDryHumidityDay = 70, autoDryHumidityWind = 58,
            autoDryWindKmh = 18.0,
            warmAsphaltTemp = 23.0, coldAsphaltTemp = 12.0
        )
        Season.WINTER -> WetRoadThresholds(
            condensationHumidity = 87, condensationDewSpread = 2.5,
            extremeHumidity = 94, extremeDewSpread = 3.0,
            precip24hThreshold = 1.0, precip24hHumidity = 70,
            recent3hThreshold = 0.3, recent3hHumidity = 70,
            recent3hStrongThreshold = 0.15, recent3hStrongHumidity = 85,
            autoDryHumidityDay = 50, autoDryHumidityWind = 40,
            autoDryWindKmh = 25.0,
            warmAsphaltTemp = 14.0, coldAsphaltTemp = 3.0
        )
        Season.SPRING, Season.AUTUMN -> WetRoadThresholds(
            condensationHumidity = 90, condensationDewSpread = 2.0,
            extremeHumidity = 96, extremeDewSpread = 2.5,
            precip24hThreshold = 2.0, precip24hHumidity = 75,
            recent3hThreshold = 0.5, recent3hHumidity = 73,
            recent3hStrongThreshold = 0.25, recent3hStrongHumidity = 87,
            autoDryHumidityDay = 60, autoDryHumidityWind = 47,
            autoDryWindKmh = 22.0,
            warmAsphaltTemp = 18.0, coldAsphaltTemp = 6.0
        )
    }

    // ─── Subtropical oceánico (Canarias) ───────────────────────────
    // Características: temperatura suave todo el año (15–30 °C), humedad
    // alta por vientos alisios, lluvias escasas y muy concentradas en
    // los meses fríos. El asfalto rara vez se enfría por debajo de 12 °C
    // ni supera los 30 °C, así que los umbrales son intermedios y la
    // estación influye menos que en la Península.
    private fun subtropicalOceanic(season: Season): WetRoadThresholds = when (season) {
        Season.SUMMER -> WetRoadThresholds(
            condensationHumidity = 94, condensationDewSpread = 1.2,
            extremeHumidity = 98, extremeDewSpread = 1.8,
            precip24hThreshold = 3.5, precip24hHumidity = 82,
            recent3hThreshold = 0.8, recent3hHumidity = 78,
            recent3hStrongThreshold = 0.4, recent3hStrongHumidity = 90,
            autoDryHumidityDay = 72, autoDryHumidityWind = 60,
            autoDryWindKmh = 18.0,
            warmAsphaltTemp = 22.0, coldAsphaltTemp = 14.0
        )
        Season.WINTER -> WetRoadThresholds(
            condensationHumidity = 89, condensationDewSpread = 2.5,
            extremeHumidity = 96, extremeDewSpread = 3.0,
            precip24hThreshold = 1.5, precip24hHumidity = 75,
            recent3hThreshold = 0.4, recent3hHumidity = 72,
            recent3hStrongThreshold = 0.2, recent3hStrongHumidity = 86,
            autoDryHumidityDay = 58, autoDryHumidityWind = 48,
            autoDryWindKmh = 22.0,
            warmAsphaltTemp = 19.0, coldAsphaltTemp = 10.0
        )
        Season.SPRING, Season.AUTUMN -> WetRoadThresholds(
            condensationHumidity = 91, condensationDewSpread = 2.0,
            extremeHumidity = 97, extremeDewSpread = 2.5,
            precip24hThreshold = 2.5, precip24hHumidity = 78,
            recent3hThreshold = 0.6, recent3hHumidity = 75,
            recent3hStrongThreshold = 0.3, recent3hStrongHumidity = 88,
            autoDryHumidityDay = 65, autoDryHumidityWind = 52,
            autoDryWindKmh = 22.0,
            warmAsphaltTemp = 20.0, coldAsphaltTemp = 12.0
        )
    }

    // ─── Genérico (cuando no hay info de ubicación) ────────────────
    private fun generic(season: Season): WetRoadThresholds = when (season) {
        Season.SUMMER -> WetRoadThresholds(
            condensationHumidity = 94, condensationDewSpread = 1.2,
            extremeHumidity = 98, extremeDewSpread = 1.8,
            precip24hThreshold = 3.5, precip24hHumidity = 82,
            recent3hThreshold = 0.9, recent3hHumidity = 78,
            recent3hStrongThreshold = 0.5, recent3hStrongHumidity = 90,
            autoDryHumidityDay = 72, autoDryHumidityWind = 60,
            autoDryWindKmh = 20.0,
            warmAsphaltTemp = 23.0, coldAsphaltTemp = 12.0
        )
        Season.WINTER -> WetRoadThresholds(
            condensationHumidity = 89, condensationDewSpread = 2.5,
            extremeHumidity = 95, extremeDewSpread = 3.0,
            precip24hThreshold = 1.3, precip24hHumidity = 75,
            recent3hThreshold = 0.35, recent3hHumidity = 73,
            recent3hStrongThreshold = 0.2, recent3hStrongHumidity = 86,
            autoDryHumidityDay = 52, autoDryHumidityWind = 42,
            autoDryWindKmh = 25.0,
            warmAsphaltTemp = 17.0, coldAsphaltTemp = 5.0
        )
        Season.SPRING, Season.AUTUMN -> WetRoadThresholds(
            condensationHumidity = 91, condensationDewSpread = 2.0,
            extremeHumidity = 96, extremeDewSpread = 2.5,
            precip24hThreshold = 2.2, precip24hHumidity = 77,
            recent3hThreshold = 0.55, recent3hHumidity = 75,
            recent3hStrongThreshold = 0.28, recent3hStrongHumidity = 88,
            autoDryHumidityDay = 62, autoDryHumidityWind = 48,
            autoDryWindKmh = 22.0,
            warmAsphaltTemp = 20.0, coldAsphaltTemp = 8.0
        )
    }

    // ─────────────────────────────────────────────────────────────
    // 3. VISIBILIDAD Y CONDICIONES EXTREMAS (sin cambios)
    // ─────────────────────────────────────────────────────────────

    fun checkLowVisibility(visibility: Double?): Pair<Boolean, String?> {
        if (visibility == null) return false to null

        return when {
            visibility < 1000 -> true to "Niebla cerrada: Visibilidad < 1km"
            visibility < 3000 -> true to "Visibilidad reducida por neblina"
            else -> false to null
        }
    }

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

        // 2. Nieve
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

    companion object {
        private const val TAG = "WeatherAdvisor"
    }
}
