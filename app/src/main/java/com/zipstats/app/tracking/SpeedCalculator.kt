package com.zipstats.app.tracking

import android.location.Location
import com.zipstats.app.model.VehicleType
import com.zipstats.app.utils.LocationUtils
import kotlin.math.abs
import kotlin.math.max
import java.util.ArrayDeque

/**
 * Calculadora de velocidad en tiempo real con filtrado inteligente.
 * Prioriza reactividad al arrancar y caída rápida a 0 al parar (anti-fantasma GPS).
 */
class SpeedCalculator(private val vehicleType: VehicleType) {

    private val emaFilter = LocationUtils.AdaptiveVelocitySmoothing()

    private val MAX_ACCURACY = 20f
    private val MIN_TIME_DELTA = 50L
    private val MAX_SPEED_MULTIPLIER = 1.2f
    private val MAX_SPEED_DISCREPANCY = 30f
    private val MAX_ACCELERATION = 30f
    private val DISPLAY_RAW_WEIGHT = 0.72f

    /** Más bajo = velocímetro sale de 0 antes (semáforo, arranque). */
    private val displayStartThreshold: Float
        get() = minOf(1.2f, vehicleType.pauseSpeedThreshold * 0.3f)

    /** Por distancia/tiempo: casi sin desplazamiento real en un fix. */
    private val STOP_DISTANCE_METERS = 2.5f

    /** Suma de desplazamiento en ventana corta → parada con deriva GPS. */
    private val STOP_RECENT_DISTANCE_METERS = 9f

    /** Velocidad por distancia que indica parada real. */
    private val STOP_CALCULATED_KMH = 3.0f

    private val RECENT_DISTANCE_WINDOW = 4

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

        if (location.accuracy > MAX_ACCURACY) {
            consecutiveRejectedUpdates++
            return rejectOrHold()
        }

        val calculatedSpeed = computeSpeedFromDistance(location, currentTime)
        val distanceSinceLastM = lastValidLocation?.distanceTo(location) ?: 0f
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

        if (isEffectivelyStopped(location, calculatedSpeed, gpsSpeed, distanceSinceLastM)) {
            return applyStoppedState(location, currentTime)
        }
        consecutiveStoppedSamples = 0

        if (gpsSpeed < 0 || gpsSpeed > vehicleType.maxSpeed * MAX_SPEED_MULTIPLIER) {
            consecutiveRejectedUpdates++
            return rejectOrHold()
        }

        if (!launching) {
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
                        return rejectOrHold()
                    }
                }
            }
        }

        if (!launching && lastUpdateTime > 0) {
            val timeDelta = (currentTime - lastUpdateTime) / 1000f
            if (timeDelta > 0) {
                val acceleration = abs(gpsSpeed - lastAcceptedGpsSpeed) / timeDelta
                val isStartingOrStopping = lastAcceptedGpsSpeed < 3.0f || gpsSpeed < 3.0f
                val maxAccel = if (isStartingOrStopping) {
                    MAX_ACCELERATION * 2.5f
                } else {
                    MAX_ACCELERATION * 1.15f
                }

                if (acceleration > maxAccel) {
                    consecutiveRejectedUpdates++
                    return rejectOrHold()
                }
            }
        }

        val displaySpeed = if (launching) {
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
     * Parado en semáforo: el chip suele inventar 4–8 km/h; la distancia entre fixes no miente tanto.
     */
    private fun isEffectivelyStopped(
        location: Location,
        calculatedSpeed: Float,
        gpsSpeed: Float,
        distanceM: Float,
    ): Boolean {
        val rawHardwareKmh = if (location.hasSpeed()) location.speed * 3.6f else gpsSpeed
        val lowCalculated = calculatedSpeed < STOP_CALCULATED_KMH
        val barelyMoved = distanceM < STOP_DISTANCE_METERS
        val phantomGps = rawHardwareKmh > calculatedSpeed + 2.5f &&
            rawHardwareKmh < vehicleType.pauseSpeedThreshold + 5f
        val recentPathM = recentDistancesM.sum()
        val chipCreepStopped = recentPathM < STOP_RECENT_DISTANCE_METERS &&
            rawHardwareKmh in 4f..8f &&
            abs(rawHardwareKmh - calculatedSpeed) > 6f
        val bothBelowPause = calculatedSpeed < vehicleType.pauseSpeedThreshold &&
            gpsSpeed < vehicleType.pauseSpeedThreshold &&
            recentPathM < STOP_RECENT_DISTANCE_METERS

        if (lowCalculated || chipCreepStopped || (phantomGps && barelyMoved) || bothBelowPause) {
            if (barelyMoved || phantomGps || chipCreepStopped || bothBelowPause) {
                consecutiveStoppedSamples++
            }
        } else {
            consecutiveStoppedSamples = 0
        }

        if (chipCreepStopped) return true

        return consecutiveStoppedSamples >= 1 &&
            currentDisplaySpeed > 0f &&
            (barelyMoved || phantomGps || bothBelowPause)
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
            distanceM < STOP_DISTANCE_METERS && hardwareKmh > 4f
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
        if (lastValidLocation == null || currentDisplaySpeed >= 5f) return false
        if (currentDisplaySpeed > 0f &&
            recentDistancesM.sum() < STOP_RECENT_DISTANCE_METERS &&
            calculatedSpeed < STOP_CALCULATED_KMH
        ) {
            return false
        }
        val distanceLaunch = calculatedSpeed >= displayStartThreshold ||
            (gpsSpeed >= vehicleType.pauseSpeedThreshold && distanceM >= STOP_DISTANCE_METERS)
        val hardwareLaunch = lastAcceptedGpsSpeed < 2f &&
            gpsSpeed >= vehicleType.pauseSpeedThreshold * 1.5f
        val softLaunch = currentDisplaySpeed < 1f &&
            gpsSpeed >= displayStartThreshold &&
            gpsSpeed < vehicleType.pauseSpeedThreshold
        return distanceLaunch || hardwareLaunch || softLaunch
    }

    private fun blendForDisplay(rawKmh: Float, smoothedKmh: Float, launching: Boolean): Float {
        if (launching) return max(rawKmh, smoothedKmh)
        val threshold = vehicleType.pauseSpeedThreshold
        if (rawKmh < threshold && smoothedKmh < threshold) return 0f
        if (smoothedKmh < threshold) return rawKmh
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

    private fun rejectOrHold(): SpeedPair? {
        return if (consecutiveRejectedUpdates < maxConsecutiveRejections) {
            SpeedPair(currentDisplaySpeed, currentDisplaySpeed)
        } else {
            null
        }
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
