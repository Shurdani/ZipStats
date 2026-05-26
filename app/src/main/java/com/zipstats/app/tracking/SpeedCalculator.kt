package com.zipstats.app.tracking

import android.location.Location
import com.zipstats.app.model.VehicleType
import com.zipstats.app.utils.LocationUtils
import kotlin.math.abs

/**
 * Calculadora de velocidad en tiempo real con filtrado inteligente
 * Procesa ubicaciones GPS y devuelve velocidades suavizadas y filtradas
 */
class SpeedCalculator(private val vehicleType: VehicleType) {
    
    private val emaFilter = LocationUtils.AdaptiveVelocitySmoothing()
    
    // Configuración de filtros
    private val MAX_ACCURACY = 20f // metros - precisión máxima aceptable
    private val MIN_TIME_DELTA = 100L // ms - tiempo mínimo entre actualizaciones (más frecuente)
    private val MAX_SPEED_MULTIPLIER = 1.2f // Multiplicador de velocidad máxima razonable
    /** Filtro 4: diferencia máxima GPS vs distancia en un fix (km/h). */
    private val MAX_GPS_DISCREPANCY_KMH = 12f
    /** Filtro 5: aceleración máxima entre fixes (km/h·s²). */
    private val MAX_ACCELERATION = 30f
    
    // Estado interno
    private var lastValidLocation: Location? = null
    private var lastUpdateTime = 0L
    private var currentDisplaySpeed = 0f
    private var consecutiveRejectedUpdates = 0
    private val maxConsecutiveRejections = 5
    
    /**
     * Procesa nueva ubicación del GPS y devuelve velocidades calculadas
     * 
     * @param location Nueva ubicación del GPS
     * @return Par de (velocidad_instantanea, velocidad_suavizada) en km/h, o null si se rechaza
     */
    fun processLocation(location: Location): SpeedPair? {
        val currentTime = System.currentTimeMillis()
        
        // FILTRO 1: Validar precisión del GPS
        if (location.accuracy > MAX_ACCURACY) {
            consecutiveRejectedUpdates++
            return if (consecutiveRejectedUpdates < maxConsecutiveRejections) {
                SpeedPair(currentDisplaySpeed, currentDisplaySpeed) // Mantener velocidad actual
            } else {
                null // Demasiadas lecturas malas consecutivas
            }
        }
        
        // FILTRO 2: Validar tiempo mínimo entre actualizaciones
        if (lastUpdateTime > 0 && currentTime - lastUpdateTime < MIN_TIME_DELTA) {
            return SpeedPair(currentDisplaySpeed, currentDisplaySpeed) // Retornar velocidad actual
        }
        
        // Obtener velocidad del GPS
        val gpsSpeed = if (location.hasSpeed()) {
            location.speed * 3.6f // m/s → km/h
        } else {
            // Calcular velocidad basada en distancia si no hay velocidad GPS
            calculateSpeedFromDistance(location)
        }
        
        // FILTRO 3: Validar rango de velocidad
        if (gpsSpeed < 0 || gpsSpeed > vehicleType.maxSpeed * MAX_SPEED_MULTIPLIER) {
            consecutiveRejectedUpdates++
            return if (consecutiveRejectedUpdates < maxConsecutiveRejections) {
                SpeedPair(currentDisplaySpeed, currentDisplaySpeed)
            } else {
                null
            }
        }

        val isAcceleratingFromStop = currentDisplaySpeed < 2.0f && gpsSpeed > 5.0f
        val distanceFromLast = lastValidLocation?.distanceTo(location) ?: 0f
        val timeDeltaSec = if (lastUpdateTime > 0) (currentTime - lastUpdateTime) / 1000f else 0f
        val calculatedKmh = if (timeDeltaSec > 0) (distanceFromLast / timeDeltaSec) * 3.6f else 0f
        val stopSnapDistanceM = minOf(vehicleType.pauseRadius, 5.5f)
        val confirmStopByDistance = lastValidLocation != null &&
            !isAcceleratingFromStop &&
            distanceFromLast < stopSnapDistanceM &&
            calculatedKmh < vehicleType.pauseSpeedThreshold + 0.5f
        
        // FILTRO 4: Validar contra velocidad calculada por distancia (si hay punto anterior)
        lastValidLocation?.let { prevLocation ->
            val distance = location.distanceTo(prevLocation)
            val timeDelta = (currentTime - lastUpdateTime) / 1000f // segundos
            
            if (timeDelta > 0) {
                val calculatedSpeed = (distance / timeDelta) * 3.6f // km/h
                
                if (!isAcceleratingFromStop &&
                    abs(gpsSpeed - calculatedSpeed) > MAX_GPS_DISCREPANCY_KMH &&
                    calculatedSpeed < vehicleType.maxSpeed) {
                    consecutiveRejectedUpdates++
                    return if (consecutiveRejectedUpdates < maxConsecutiveRejections) {
                        SpeedPair(currentDisplaySpeed, currentDisplaySpeed)
                    } else {
                        null
                    }
                }
            }
        }
        
        // FILTRO 5: Validar aceleración razonable (no bloquear arranque ni parada confirmada por distancia)
        if (!isAcceleratingFromStop && !confirmStopByDistance && lastUpdateTime > 0) {
            val timeDelta = (currentTime - lastUpdateTime) / 1000f
            if (timeDelta > 0) {
                val acceleration = abs(gpsSpeed - currentDisplaySpeed) / timeDelta
                val isStartingOrStopping = currentDisplaySpeed < 3.0f || gpsSpeed < 3.0f
                val maxAccel = if (isStartingOrStopping) MAX_ACCELERATION * 2.0f else MAX_ACCELERATION
                
                if (acceleration > maxAccel) {
                    consecutiveRejectedUpdates++
                    return if (consecutiveRejectedUpdates < maxConsecutiveRejections) {
                        SpeedPair(currentDisplaySpeed, currentDisplaySpeed)
                    } else {
                        null
                    }
                }
            }
        }
        
        if (confirmStopByDistance) emaFilter.snapTo(0.0)

        val smoothedSpeed = if (confirmStopByDistance) {
            0f
        } else {
            emaFilter.updateSpeed(gpsSpeed.toDouble()).toFloat()
        }
        
        // Aplicar umbral mínimo para mostrar 0 (usar umbral de pausa para mejor detección)
        val displaySpeed = if (smoothedSpeed < vehicleType.pauseSpeedThreshold) {
            0f
        } else {
            smoothedSpeed
        }
        
        // Actualizar estado
        lastValidLocation = location
        lastUpdateTime = currentTime
        currentDisplaySpeed = displaySpeed
        consecutiveRejectedUpdates = 0 // Resetear contador de rechazos
        
        return SpeedPair(instantaneous = gpsSpeed, smoothed = displaySpeed)
    }
    
    /**
     * Calcula velocidad basada en distancia desde la última ubicación válida
     */
    private fun calculateSpeedFromDistance(location: Location): Float {
        return lastValidLocation?.let { prevLocation ->
            val distance = location.distanceTo(prevLocation)
            val timeDelta = (System.currentTimeMillis() - lastUpdateTime) / 1000f
            if (timeDelta > 0) (distance / timeDelta) * 3.6f else 0f
        } ?: 0f
    }
    
    /**
     * Reinicia el calculador (usar al iniciar nueva ruta)
     */
    fun reset() {
        emaFilter.reset()
        lastValidLocation = null
        lastUpdateTime = 0L
        currentDisplaySpeed = 0f
        consecutiveRejectedUpdates = 0
    }
    
    /**
     * Obtiene la última velocidad válida
     */
    fun getCurrentSpeed(): Float = currentDisplaySpeed
    
    /**
     * Obtiene la velocidad instantánea sin suavizar
     */
    fun getInstantaneousSpeed(): Float = lastValidLocation?.let { location ->
        if (location.hasSpeed()) {
            location.speed * 3.6f
        } else {
            calculateSpeedFromDistance(location)
        }
    } ?: 0f
    
    /**
     * Verifica si el calculador está funcionando correctamente
     */
    fun isHealthy(): Boolean = consecutiveRejectedUpdates < maxConsecutiveRejections
    
    /**
     * Obtiene estadísticas del calculador
     */
    fun getStats(): SpeedCalculatorStats = SpeedCalculatorStats(
        currentSpeed = currentDisplaySpeed,
        instantaneousSpeed = getInstantaneousSpeed(),
        isHealthy = isHealthy(),
        consecutiveRejectedUpdates = consecutiveRejectedUpdates,
        lastUpdateTime = lastUpdateTime
    )
}

/**
 * Par de velocidades: instantánea y suavizada
 */
data class SpeedPair(
    val instantaneous: Float, // Velocidad sin filtrar (km/h)
    val smoothed: Float       // Velocidad suavizada (km/h)
)

/**
 * Estadísticas del calculador de velocidad
 */
data class SpeedCalculatorStats(
    val currentSpeed: Float,
    val instantaneousSpeed: Float,
    val isHealthy: Boolean,
    val consecutiveRejectedUpdates: Int,
    val lastUpdateTime: Long
)
