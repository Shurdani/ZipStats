package com.zipstats.app.analysis

import android.location.Location
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.model.VehicleType

/**
 * Detecta automáticamente pausas en la ruta
 * Clase independiente para análisis post-ruta
 */
class PauseDetector(private val vehicleType: VehicleType) {
    
    // Configuración de detección de pausas
    private val MIN_PAUSE_DURATION = vehicleType.minPauseDuration // ms - duración mínima para considerar pausa
    private val MAX_MOVEMENT_RADIUS = vehicleType.pauseRadius // metros - movimiento máximo durante pausa
    private val MAX_TIME_GAP = 10000L // ms - tiempo máximo entre puntos en pausa
    private val SPEED_THRESHOLD = vehicleType.minSpeed // km/h - umbral de velocidad para considerar parado
    
    /**
     * Representa una pausa detectada en la ruta
     */
    data class Pause(
        val startTime: Long,
        val endTime: Long,
        val duration: Long,
        val latitude: Double,
        val longitude: Double,
        val pointCount: Int = 0 // Número de puntos GPS en la pausa
    ) {
        /**
         * Obtiene la duración en formato legible
         */
        fun getDurationFormatted(): String {
            val seconds = duration / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            
            return when {
                hours > 0 -> String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
                minutes > 0 -> String.format("%d:%02d", minutes, seconds % 60)
                else -> String.format("0:%02d", seconds)
            }
        }
        
        /**
         * Obtiene la duración en minutos
         */
        fun getDurationMinutes(): Float = duration / (1000f * 60f)
    }
    
    /**
     * Detecta pausas en una lista de puntos
     * 
     * @param points Lista de puntos GPS (ya filtrados)
     * @return Lista de pausas detectadas
     */
    fun detectPauses(points: List<RoutePoint>): List<Pause> {
        if (points.size < 2) return emptyList()
        
        val pauses = mutableListOf<Pause>()
        var pauseStart: RoutePoint? = null
        val pausePoints = mutableListOf<RoutePoint>()
        
        for (i in 0 until points.size) {
            val current = points[i]
            val speed = current.speed?.let { it * 3.6f } ?: 0f // m/s → km/h
            
            // Detectar inicio o continuación de pausa
            val isStationary = speed < SPEED_THRESHOLD
            
            if (isStationary) {
                if (pauseStart == null) {
                    // Inicio de nueva pausa
                    pauseStart = current
                    pausePoints.clear()
                    pausePoints.add(current)
                } else {
                    // Verificar que sigue siendo parte de la misma pausa
                    val timeSinceLastPoint = current.timestamp - pausePoints.last().timestamp
                    val distanceFromStart = calculateDistance(pauseStart, current)
                    
                    if (timeSinceLastPoint < MAX_TIME_GAP && 
                        distanceFromStart < MAX_MOVEMENT_RADIUS) {
                        // Continúa la pausa
                        pausePoints.add(current)
                    } else {
                        // Nueva pausa (rompe con la anterior)
                        finalizePause(pauseStart, pausePoints, pauses)
                        pauseStart = current
                        pausePoints.clear()
                        pausePoints.add(current)
                    }
                }
            } else {
                // Fin de pausa (se reanudó el movimiento)
                if (pauseStart != null && pausePoints.isNotEmpty()) {
                    finalizePause(pauseStart, pausePoints, pauses)
                    pauseStart = null
                    pausePoints.clear()
                }
            }
        }
        
        // Finalizar pausa pendiente al final de la ruta
        if (pauseStart != null && pausePoints.isNotEmpty()) {
            finalizePause(pauseStart, pausePoints, pauses)
        }
        
        return pauses
    }
    
    /**
     * Detecta pausas con configuración personalizada
     * 
     * @param points Lista de puntos GPS
     * @param minPauseDuration Duración mínima personalizada
     * @param maxMovementRadius Radio máximo personalizado
     * @param speedThreshold Umbral de velocidad personalizado
     * @return Lista de pausas detectadas
     */
    fun detectPausesWithCustomParams(
        points: List<RoutePoint>,
        minPauseDuration: Long = MIN_PAUSE_DURATION,
        maxMovementRadius: Float = MAX_MOVEMENT_RADIUS,
        speedThreshold: Float = SPEED_THRESHOLD
    ): List<Pause> {
        if (points.size < 2) return emptyList()
        
        val pauses = mutableListOf<Pause>()
        var pauseStart: RoutePoint? = null
        val pausePoints = mutableListOf<RoutePoint>()
        
        for (i in 0 until points.size) {
            val current = points[i]
            val speed = current.speed?.let { it * 3.6f } ?: 0f
            
            val isStationary = speed < speedThreshold
            
            if (isStationary) {
                if (pauseStart == null) {
                    pauseStart = current
                    pausePoints.clear()
                    pausePoints.add(current)
                } else {
                    val timeSinceLastPoint = current.timestamp - pausePoints.last().timestamp
                    val distanceFromStart = calculateDistance(pauseStart, current)
                    
                    if (timeSinceLastPoint < MAX_TIME_GAP && 
                        distanceFromStart < maxMovementRadius) {
                        pausePoints.add(current)
                    } else {
                        finalizePauseWithParams(pauseStart, pausePoints, pauses, minPauseDuration)
                        pauseStart = current
                        pausePoints.clear()
                        pausePoints.add(current)
                    }
                }
            } else {
                if (pauseStart != null && pausePoints.isNotEmpty()) {
                    finalizePauseWithParams(pauseStart, pausePoints, pauses, minPauseDuration)
                    pauseStart = null
                    pausePoints.clear()
                }
            }
        }
        
        if (pauseStart != null && pausePoints.isNotEmpty()) {
            finalizePauseWithParams(pauseStart, pausePoints, pauses, minPauseDuration)
        }
        
        return pauses
    }
    
    /**
     * Finaliza una pausa y la añade a la lista si cumple criterios
     */
    private fun finalizePause(
        pauseStart: RoutePoint,
        pausePoints: List<RoutePoint>,
        pauses: MutableList<Pause>
    ) {
        finalizePauseWithParams(pauseStart, pausePoints, pauses, MIN_PAUSE_DURATION)
    }
    
    /**
     * Finaliza una pausa con parámetros personalizados
     */
    private fun finalizePauseWithParams(
        pauseStart: RoutePoint,
        pausePoints: List<RoutePoint>,
        pauses: MutableList<Pause>,
        minPauseDuration: Long
    ) {
        if (pausePoints.isEmpty()) return
        
        val pauseEnd = pausePoints.last()
        val duration = pauseEnd.timestamp - pauseStart.timestamp
        
        // Solo agregar si supera duración mínima
        if (duration >= minPauseDuration) {
            pauses.add(Pause(
                startTime = pauseStart.timestamp,
                endTime = pauseEnd.timestamp,
                duration = duration,
                latitude = pauseStart.latitude,
                longitude = pauseStart.longitude,
                pointCount = pausePoints.size
            ))
        }
    }
    
    /**
     * Calcula la distancia entre dos puntos GPS
     */
    private fun calculateDistance(p1: RoutePoint, p2: RoutePoint): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            p1.latitude, p1.longitude,
            p2.latitude, p2.longitude,
            results
        )
        return results[0]
    }
    
    /**
     * Obtiene estadísticas de las pausas detectadas
     */
    fun getPauseStats(pauses: List<Pause>): PauseStats {
        val totalPauseTime = pauses.sumOf { it.duration }
        val averagePauseDuration = if (pauses.isNotEmpty()) {
            totalPauseTime / pauses.size.toFloat()
        } else 0f
        
        val longestPause = pauses.maxByOrNull { it.duration }?.duration ?: 0L
        val shortestPause = pauses.minByOrNull { it.duration }?.duration ?: 0L
        
        return PauseStats(
            pauseCount = pauses.size,
            totalPauseTime = totalPauseTime,
            averagePauseDuration = averagePauseDuration,
            longestPause = longestPause,
            shortestPause = shortestPause
        )
    }
}

/**
 * Estadísticas de las pausas detectadas
 */
data class PauseStats(
    val pauseCount: Int,
    val totalPauseTime: Long,
    val averagePauseDuration: Float,
    val longestPause: Long,
    val shortestPause: Long
)
