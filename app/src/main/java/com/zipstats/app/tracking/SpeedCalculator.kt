package com.zipstats.app.tracking

import android.location.Location
import com.zipstats.app.model.VehicleType
import com.zipstats.app.utils.LocationUtils
import kotlin.math.abs
import kotlin.math.max

/**
 * Calculadora de velocidad en tiempo real con filtrado inteligente.
 * Prioriza reactividad al arrancar (histéresis de umbral + velocidad por distancia).
 */
class SpeedCalculator(private val vehicleType: VehicleType) {

    private val emaFilter = LocationUtils.AdaptiveVelocitySmoothing()

    private val MAX_ACCURACY = 20f
    private val MIN_TIME_DELTA = 50L
    private val MAX_SPEED_MULTIPLIER = 1.2f
    private val MAX_SPEED_DISCREPANCY = 30f
    private val MAX_ACCELERATION = 30f
    private val DISPLAY_RAW_WEIGHT = 0.62f

    /** Umbral más bajo para salir de 0 en pantalla (el de parada sigue siendo [VehicleType.pauseSpeedThreshold]). */
    private val displayStartThreshold: Float
        get() = minOf(2.5f, vehicleType.pauseSpeedThreshold * 0.55f)

    private var lastValidLocation: Location? = null
    private var lastUpdateTime = 0L
    private var lastAcceptedGpsSpeed = 0f
    private var currentDisplaySpeed = 0f
    private var consecutiveRejectedUpdates = 0
    private val maxConsecutiveRejections = 5

    fun processLocation(location: Location): SpeedPair? {
        val currentTime = System.currentTimeMillis()

        if (location.accuracy > MAX_ACCURACY) {
            consecutiveRejectedUpdates++
            return rejectOrHold()
        }

        if (lastUpdateTime > 0 && currentTime - lastUpdateTime < MIN_TIME_DELTA) {
            return SpeedPair(currentDisplaySpeed, currentDisplaySpeed)
        }

        val calculatedSpeed = computeSpeedFromDistance(location, currentTime)
        val gpsSpeed = resolveGpsSpeed(location, calculatedSpeed)
        val launching = isLaunchingFromStop(gpsSpeed, calculatedSpeed)

        if (gpsSpeed < 0 || gpsSpeed > vehicleType.maxSpeed * MAX_SPEED_MULTIPLIER) {
            consecutiveRejectedUpdates++
            return rejectOrHold()
        }

        if (!launching) {
            lastValidLocation?.let { prevLocation ->
                val distance = location.distanceTo(prevLocation)
                val timeDelta = (currentTime - lastUpdateTime) / 1000f

                if (timeDelta > 0) {
                    val speedFromDistance = (distance / timeDelta) * 3.6f
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

        val smoothedSpeed = emaFilter.updateSpeed(gpsSpeed.toDouble()).toFloat()
        val reactiveSpeed = blendForDisplay(gpsSpeed, smoothedSpeed, launching)
        val displaySpeed = applyDisplayThreshold(reactiveSpeed)

        lastValidLocation = location
        lastUpdateTime = currentTime
        lastAcceptedGpsSpeed = gpsSpeed
        currentDisplaySpeed = displaySpeed
        consecutiveRejectedUpdates = 0

        return SpeedPair(instantaneous = gpsSpeed, smoothed = displaySpeed)
    }

    /**
     * Al arrancar, el GPS del móvil suele reportar speed=0 aunque ya hay desplazamiento.
     * Priorizamos distancia/tiempo en esa fase.
     */
    private fun resolveGpsSpeed(location: Location, calculatedSpeed: Float): Float {
        if (!location.hasSpeed()) return calculatedSpeed

        val hardwareKmh = location.speed * 3.6f
        val launching = isLaunchingFromStop(hardwareKmh, calculatedSpeed)

        if (launching && calculatedSpeed >= displayStartThreshold && hardwareKmh < vehicleType.pauseSpeedThreshold) {
            return calculatedSpeed
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

    private fun isLaunchingFromStop(gpsSpeed: Float, calculatedSpeed: Float): Boolean {
        return currentDisplaySpeed < 2.5f &&
            (gpsSpeed >= displayStartThreshold || calculatedSpeed >= displayStartThreshold)
    }

    private fun blendForDisplay(rawKmh: Float, smoothedKmh: Float, launching: Boolean): Float {
        if (launching) {
            return max(rawKmh, smoothedKmh)
        }
        val threshold = vehicleType.pauseSpeedThreshold
        if (rawKmh < threshold && smoothedKmh < threshold) return 0f
        if (smoothedKmh < threshold) return rawKmh
        if (rawKmh < threshold) return smoothedKmh
        return DISPLAY_RAW_WEIGHT * rawKmh + (1f - DISPLAY_RAW_WEIGHT) * smoothedKmh
    }

    /** Histéresis: más fácil mostrar velocidad al arrancar, más estricto al parar. */
    private fun applyDisplayThreshold(reactiveSpeed: Float): Float {
        val stopThreshold = vehicleType.pauseSpeedThreshold
        return if (currentDisplaySpeed <= 0f) {
            if (reactiveSpeed >= displayStartThreshold) reactiveSpeed else 0f
        } else {
            if (reactiveSpeed < stopThreshold) 0f else reactiveSpeed
        }
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
