package com.zipstats.app.tracking

import android.location.Location
import com.zipstats.app.model.VehicleType
import com.zipstats.app.utils.LocationUtils
import kotlin.math.abs
import kotlin.math.max
import java.util.ArrayDeque

/**
 * Calculadora de velocidad en tiempo real con filtrado inteligente.
 * Ajustada para patinete eléctrico: arranque/frenada bruscos, velocidad crucero 20-25 km/h.
 *
 * CAMBIOS RESPECTO A LA VERSIÓN ORIGINAL:
 *
 * 1. DISPLAY_RAW_WEIGHT 0.72 → 0.45
 *    El GPS crudo tenía demasiado peso en la mezcla final. Con 0.72 cualquier
 *    spike del chip (p.ej. 27 km/h un instante) se trasladaba directamente al
 *    display. Con 0.45 el EMA suavizado manda más y los saltos desaparecen.
 *
 * 2. MAX_SPEED_DISCREPANCY 30 → 12 km/h
 *    Con 30 km/h de margen se aceptaban fixes donde el GPS decía 27 pero la
 *    distancia real solo justificaba 15. Bajarlo a 12 filtra esos spikes sin
 *    afectar la aceleración real del patinete (que es continua, no puntual).
 *
 * 3. MAX_ACCELERATION 30 → 18 km/h·s² (crucero); arranque/parada sin cambio
 *    Un patinete eléctrico acelera ~4-6 km/h por segundo de forma sostenida.
 *    30 era tan permisivo que dejaba pasar spikes. 18 sigue cubriendo cualquier
 *    aceleración real y rechaza saltos fantasma de un solo fix.
 *    El factor de arranque/parada se mantiene en 2.5× para no bloquear el
 *    primer fix válido tras un semáforo.
 *
 * 4. STOP_DISTANCE_METERS 2.5 → 5.5 m
 *    El drift GPS urbano en Android llega a 4-5 m parado. Con 5.5 m como
 *    umbral, barelyMoved es true aunque haya deriva moderada.
 *
 * 5. STOP_RECENT_DISTANCE_METERS 9 → 14 m (ventana = 5 fixes)
 *    14 m en 5 fixes ≈ ~10 km/h sostenido; por debajo se considera parado.
 *
 * 6. STOP_CALCULATED_KMH 3.0 → 4.5 km/h
 *    Con pauseSpeedThreshold=4 km/h el chip reporta 2-4 km/h de fantasma
 *    parado. Subir el umbral a 4.5 supera ese ruido.
 *
 * 7. isEffectivelyStopped reescrita: árbitro = distancia, no chip GPS
 *    Con pauseSpeedThreshold=4 el chip siempre reporta algo en ese rango
 *    parado → no es fiable. Ahora la detección se basa en distancia/tiempo.
 *
 * 7. RECENT_DISTANCE_WINDOW 4 → 5
 *    Ventana ligeramente mayor para que la suma de distancias recientes sea
 *    más representativa en velocidades bajas (5-10 km/h).
 *
 * 8. blendForDisplay: eliminado el corte duro a 0f cuando ambos < threshold
 *    Ese corte causaba el salto más visible: el EMA bajaba despacio y mientras
 *    el raw ya era < threshold → resultado 0, luego el EMA subía un tick →
 *    resultado != 0 → oscilación. Ahora se deja que applyDisplayThreshold
 *    sea el único punto de decisión de parada.
 */
class SpeedCalculator(private val vehicleType: VehicleType) {

    private val emaFilter = LocationUtils.AdaptiveVelocitySmoothing()

    private val MAX_ACCURACY = 20f
    private val MIN_TIME_DELTA = 50L
    private val MAX_SPEED_MULTIPLIER = 1.2f

    // [FIX #2] Bajado de 30 a 12: evita aceptar spikes puntuales del chip GPS
    private val MAX_SPEED_DISCREPANCY = 12f

    // [FIX #3] Bajado de 30 a 18: un patinete no acelera 30 km/h en 1 segundo en crucero
    private val MAX_ACCELERATION = 18f

    // [FIX #1] Bajado de 0.72 a 0.45: el suavizado manda más, menos saltos en display
    private val DISPLAY_RAW_WEIGHT = 0.45f

    /** Más bajo = velocímetro sale de 0 antes (semáforo, arranque). */
    private val displayStartThreshold: Float
        get() = minOf(1.2f, vehicleType.pauseSpeedThreshold * 0.3f)

    // [FIX #4] Subido de 2.5 a 4.0: el GPS drift normal es ~3 m, no confundir con movimiento
    // [FIX v3] Subido de 4.0 a 5.5: cubre drift GPS urbano de 4-5 m sin confundir movimiento real
    private val STOP_DISTANCE_METERS = 5.5f

    // [FIX #5] Subido de 9 a 14: 14 m en 5 fixes ≈ limite ~10 km/h sostenido
    private val STOP_RECENT_DISTANCE_METERS = 14f

    // [FIX v3] Subido de 3.0 a 4.5: con pauseSpeedThreshold=4 km/h el chip reporta
    // 2-4 km/h de deriva; 4.5 supera ese ruido y aún distingue parada de movimiento lento
    private val STOP_CALCULATED_KMH = 4.5f

    // [FIX #7] Subido de 4 a 5 para ventana más representativa en velocidades bajas
    private val RECENT_DISTANCE_WINDOW = 5

    private var lastValidLocation: Location? = null
    private var lastUpdateTime = 0L
    private var lastAcceptedGpsSpeed = 0f
    private var currentDisplaySpeed = 0f
    private var consecutiveRejectedUpdates = 0
    private val maxConsecutiveRejections = 5
    private var consecutiveStoppedSamples = 0
    private val recentDistancesM = ArrayDeque<Float>(RECENT_DISTANCE_WINDOW)

    fun processLocation(location: Location): SpeedPair? {
        val currentTime = System.currentTimeMillis()

        val calculatedSpeed = computeSpeedFromDistance(location, currentTime)
        val distanceSinceLastM = lastValidLocation?.distanceTo(location) ?: 0f

        if (location.accuracy > MAX_ACCURACY) {
            consecutiveRejectedUpdates++
            return rejectOrHold(calculatedSpeed, distanceSinceLastM)
        }
        trackRecentDistance(distanceSinceLastM)

        val launching = isLaunchingFromStop(
            resolveGpsSpeed(location, calculatedSpeed, distanceSinceLastM),
            calculatedSpeed,
            distanceSinceLastM,
        )

        if (lastUpdateTime > 0 && currentTime - lastUpdateTime < MIN_TIME_DELTA && !launching) {
            return SpeedPair(currentDisplaySpeed, currentDisplaySpeed)
        }

        val gpsSpeed = resolveGpsSpeed(location, calculatedSpeed, distanceSinceLastM)
        val launchingNow = launching || isLaunchingFromStop(gpsSpeed, calculatedSpeed, distanceSinceLastM)

        if (!launchingNow && isEffectivelyStopped(location, calculatedSpeed, gpsSpeed, distanceSinceLastM)) {
            return applyStoppedState(location, currentTime)
        }
        consecutiveStoppedSamples = 0

        if (gpsSpeed < 0 || gpsSpeed > vehicleType.maxSpeed * MAX_SPEED_MULTIPLIER) {
            consecutiveRejectedUpdates++
            return rejectOrHold(calculatedSpeed, distanceSinceLastM)
        }

        if (!launchingNow) {
            lastValidLocation?.let { prevLocation ->
                val timeDelta = (currentTime - lastUpdateTime) / 1000f
                if (timeDelta > 0) {
                    val speedFromDistance = (location.distanceTo(prevLocation) / timeDelta) * 3.6f
                    val isAcceleratingFromStop =
                        currentDisplaySpeed < 2.0f && gpsSpeed > 5.0f

                    if (!isAcceleratingFromStop &&
                        abs(gpsSpeed - speedFromDistance) > MAX_SPEED_DISCREPANCY &&
                        speedFromDistance < vehicleType.maxSpeed
                    ) {
                        consecutiveRejectedUpdates++
                        return rejectOrHold(calculatedSpeed, distanceSinceLastM)
                    }
                }
            }
        }

        if (!launchingNow && lastUpdateTime > 0) {
            val timeDelta = (currentTime - lastUpdateTime) / 1000f
            if (timeDelta > 0) {
                val acceleration = abs(gpsSpeed - lastAcceptedGpsSpeed) / timeDelta
                val isStartingOrStopping = lastAcceptedGpsSpeed < 3.0f || gpsSpeed < 3.0f
                // Factor 2.5× en arranque/parada sin cambio: permite reactividad desde semáforo
                val maxAccel = if (isStartingOrStopping) {
                    MAX_ACCELERATION * 2.5f
                } else {
                    MAX_ACCELERATION * 1.15f
                }

                if (acceleration > maxAccel) {
                    consecutiveRejectedUpdates++
                    return rejectOrHold(calculatedSpeed, distanceSinceLastM)
                }
            }
        }

        val displaySpeed = if (launchingNow) {
            val instant = max(gpsSpeed, calculatedSpeed)
            emaFilter.snapTo(instant.toDouble())
            applyDisplayThreshold(instant, launching = true)
        } else {
            val smoothedSpeed = emaFilter.updateSpeed(gpsSpeed.toDouble()).toFloat()
            val reactiveSpeed = blendForDisplay(gpsSpeed, smoothedSpeed, launching = false)
            applyDisplayThreshold(reactiveSpeed, launching = false)
        }

        lastValidLocation = location
        lastUpdateTime = currentTime
        lastAcceptedGpsSpeed = gpsSpeed
        currentDisplaySpeed = displaySpeed
        consecutiveRejectedUpdates = 0

        return SpeedPair(instantaneous = gpsSpeed, smoothed = displaySpeed)
    }

    /**
     * Árbitro principal de parada. Con pauseSpeedThreshold=4 km/h el chip GPS
     * reporta 3-6 km/h de fantasma incluso quieto, así que NO se usa el hardware
     * speed como señal primaria. La distancia entre fixes es el árbitro real.
     *
     * Tres niveles de certeza:
     *
     * NIVEL A – parada inmediata:
     *   La distancia calculada (metros/tiempo) < 4.5 km/h Y los fixes son
     *   cercanos (< 5.5 m). Ambas métricas son independientes del chip.
     *   → cae a 0 en el primer fix. Cubre semáforos normales.
     *
     * NIVEL B – parada en 1 fix adicional:
     *   La distancia no justifica movimiento Y la ventana reciente tampoco.
     *   Cubre paradas donde el primer fix llega algo tarde (p. ej. túnel).
     *
     * NIVEL C – certeza baja, 2 fixes:
     *   Solo velocidad calculada baja pero algo de desplazamiento reciente.
     *   Evita falsos ceros en curvas lentas o bajadas suaves.
     */
    private fun isEffectivelyStopped(
        location: Location,
        calculatedSpeed: Float,
        gpsSpeed: Float,
        distanceM: Float,
    ): Boolean {
        val recentPathM = recentDistancesM.sum()
        val rawHardwareKmh = if (location.hasSpeed()) location.speed * 3.6f else gpsSpeed

        // Métricas basadas en distancia (no en el chip, que miente con pauseSpeedThreshold=4)
        val barelyMoved   = distanceM    < STOP_DISTANCE_METERS        // < 5.5 m en este fix
        val lowCalculated = calculatedSpeed < STOP_CALCULATED_KMH      // < 4.5 km/h distancia/tiempo
        val recentSlow    = recentPathM  < STOP_RECENT_DISTANCE_METERS  // < 14 m en ventana de 5 fixes

        // Chip creep: hardware reporta 4-10 km/h pero la distancia real no acompaña nada
        val chipCreep = rawHardwareKmh in 2f..10f &&
            calculatedSpeed < STOP_CALCULATED_KMH &&
            barelyMoved

        // ── NIVEL A ───────────────────────────────────────────────────────────
        // Distancia Y velocidad calculada confirman parada. Sin ambigüedad.
        if (barelyMoved && lowCalculated) {
            consecutiveStoppedSamples++
            return true
        }

        // ── NIVEL B ───────────────────────────────────────────────────────────
        // Fix individual cerca + ventana reciente también baja, o chip creep claro.
        val mediumCertainty = (barelyMoved && recentSlow) || chipCreep

        if (mediumCertainty) {
            consecutiveStoppedSamples++
            return consecutiveStoppedSamples >= 1
        }

        // ── NIVEL C ───────────────────────────────────────────────────────────
        // Solo velocidad calculada baja pero algo de desplazamiento reciente.
        if (lowCalculated && currentDisplaySpeed > 0f) {
            consecutiveStoppedSamples++
            return consecutiveStoppedSamples >= 2
        }

        consecutiveStoppedSamples = 0
        return false
    }

    private fun trackRecentDistance(distanceM: Float) {
        if (lastValidLocation == null) return
        recentDistancesM.addLast(distanceM)
        while (recentDistancesM.size > RECENT_DISTANCE_WINDOW) {
            recentDistancesM.removeFirst()
        }
    }

    private fun applyStoppedState(location: Location, currentTime: Long): SpeedPair {
        emaFilter.snapTo(0.0)
        lastValidLocation = location
        lastUpdateTime = currentTime
        lastAcceptedGpsSpeed = 0f
        currentDisplaySpeed = 0f
        consecutiveRejectedUpdates = 0
        consecutiveStoppedSamples = 0   // reset: no contaminar el arranque siguiente
        return SpeedPair(instantaneous = 0f, smoothed = 0f)
    }

    private fun resolveGpsSpeed(
        location: Location,
        calculatedSpeed: Float,
        distanceM: Float,
    ): Float {
        if (!location.hasSpeed()) return calculatedSpeed

        val hardwareKmh = location.speed * 3.6f
        val launching = isLaunchingFromStop(hardwareKmh, calculatedSpeed, distanceM)

        if (!launching && lastValidLocation != null && currentDisplaySpeed > 0f &&
            calculatedSpeed < STOP_CALCULATED_KMH && hardwareKmh > calculatedSpeed + 2f
        ) {
            return calculatedSpeed
        }
        if (!launching && currentDisplaySpeed > displayStartThreshold &&
            recentDistancesM.size >= 2 &&
            recentDistancesM.sum() < STOP_RECENT_DISTANCE_METERS &&
            hardwareKmh < vehicleType.pauseSpeedThreshold + 4f
        ) {
            return minOf(calculatedSpeed, hardwareKmh, vehicleType.pauseSpeedThreshold - 0.5f)
                .coerceAtLeast(0f)
        }
        if (lastValidLocation != null && currentDisplaySpeed > 0f &&
            distanceM < STOP_DISTANCE_METERS && hardwareKmh > vehicleType.pauseSpeedThreshold
        ) {
            return calculatedSpeed.coerceAtMost(1.5f)
        }
        if (launching && calculatedSpeed >= displayStartThreshold && hardwareKmh < vehicleType.pauseSpeedThreshold) {
            return max(hardwareKmh, calculatedSpeed)
        }
        if (hardwareKmh < 1f && calculatedSpeed > hardwareKmh) {
            return calculatedSpeed
        }
        return hardwareKmh
    }

    private fun computeSpeedFromDistance(location: Location, currentTime: Long): Float {
        return lastValidLocation?.let { prevLocation ->
            val timeDelta = (currentTime - lastUpdateTime) / 1000f
            if (timeDelta > 0) {
                (location.distanceTo(prevLocation) / timeDelta) * 3.6f
            } else {
                0f
            }
        } ?: 0f
    }

    private fun isLaunchingFromStop(
        gpsSpeed: Float,
        calculatedSpeed: Float,
        distanceM: Float,
    ): Boolean {
        if (lastValidLocation == null) return false

        val softLaunch = (currentDisplaySpeed < 1f || lastAcceptedGpsSpeed < 2f) &&
            gpsSpeed >= displayStartThreshold &&
            gpsSpeed < vehicleType.pauseSpeedThreshold &&
            calculatedSpeed < displayStartThreshold &&
            distanceM < STOP_DISTANCE_METERS
        val realMovement = calculatedSpeed >= displayStartThreshold ||
            distanceM >= STOP_DISTANCE_METERS ||
            (lastAcceptedGpsSpeed < 2f && gpsSpeed >= vehicleType.pauseSpeedThreshold * 1.5f) ||
            softLaunch

        if (!realMovement) return false
        if (softLaunch) return true

        // Fantasma en pantalla: no arrancar hasta desplazamiento real
        if (recentDistancesM.sum() < STOP_RECENT_DISTANCE_METERS &&
            calculatedSpeed < STOP_CALCULATED_KMH &&
            distanceM < STOP_DISTANCE_METERS
        ) {
            return false
        }

        return true
    }

    /**
     * [FIX #8] Eliminado el corte duro a 0f cuando ambos < threshold.
     *
     * Original:
     *   if (rawKmh < threshold && smoothedKmh < threshold) return 0f
     *
     * Ese corte producía oscilaciones: el EMA descendía despacio cruzando el
     * umbral un tick sí, uno no → el display saltaba entre 0 y != 0.
     * Ahora blendForDisplay solo mezcla; applyDisplayThreshold es el único
     * punto donde se decide si mostrar 0.
     */
    private fun blendForDisplay(rawKmh: Float, smoothedKmh: Float, launching: Boolean): Float {
        if (launching) return max(rawKmh, smoothedKmh)
        val threshold = vehicleType.pauseSpeedThreshold
        // Al frenar: no rescatar el raw fantasma si el suavizado ya está por debajo del umbral
        if (smoothedKmh < threshold) {
            return if (rawKmh >= threshold) rawKmh else smoothedKmh
        }
        if (rawKmh < threshold) return smoothedKmh
        return DISPLAY_RAW_WEIGHT * rawKmh + (1f - DISPLAY_RAW_WEIGHT) * smoothedKmh
    }

    private fun applyDisplayThreshold(reactiveSpeed: Float, launching: Boolean = false): Float {
        val stopThreshold = vehicleType.pauseSpeedThreshold
        if (launching) {
            return if (reactiveSpeed >= displayStartThreshold * 0.85f) reactiveSpeed else 0f
        }
        return if (reactiveSpeed < stopThreshold) 0f else reactiveSpeed
    }

    private fun rejectOrHold(
        calculatedSpeed: Float = 0f,
        distanceM: Float = 0f,
    ): SpeedPair? {
        if (consecutiveRejectedUpdates >= maxConsecutiveRejections) return null
        val creepingStopped = calculatedSpeed < STOP_CALCULATED_KMH &&
            distanceM < STOP_DISTANCE_METERS &&
            currentDisplaySpeed > 0f &&
            currentDisplaySpeed < vehicleType.pauseSpeedThreshold + 3f
        if (creepingStopped) {
            emaFilter.snapTo(0.0)
            currentDisplaySpeed = 0f
            return SpeedPair(0f, 0f)
        }
        return SpeedPair(currentDisplaySpeed, currentDisplaySpeed)
    }

    private fun calculateSpeedFromDistance(location: Location): Float {
        return computeSpeedFromDistance(location, System.currentTimeMillis())
    }

    fun reset() {
        emaFilter.reset()
        lastValidLocation = null
        lastUpdateTime = 0L
        lastAcceptedGpsSpeed = 0f
        currentDisplaySpeed = 0f
        consecutiveRejectedUpdates = 0
        consecutiveStoppedSamples = 0
        recentDistancesM.clear()
    }

    fun getCurrentSpeed(): Float = currentDisplaySpeed

    fun getInstantaneousSpeed(): Float = lastValidLocation?.let { loc ->
        if (loc.hasSpeed()) loc.speed * 3.6f else calculateSpeedFromDistance(loc)
    } ?: 0f

    fun isHealthy(): Boolean = consecutiveRejectedUpdates < maxConsecutiveRejections

    fun getStats(): SpeedCalculatorStats = SpeedCalculatorStats(
        currentSpeed = currentDisplaySpeed,
        instantaneousSpeed = getInstantaneousSpeed(),
        isHealthy = isHealthy(),
        consecutiveRejectedUpdates = consecutiveRejectedUpdates,
        lastUpdateTime = lastUpdateTime,
    )
}

data class SpeedPair(
    val instantaneous: Float,
    val smoothed: Float,
)

data class SpeedCalculatorStats(
    val currentSpeed: Float,
    val instantaneousSpeed: Float,
    val isHealthy: Boolean,
    val consecutiveRejectedUpdates: Int,
    val lastUpdateTime: Long,
)
