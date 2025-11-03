package com.zipstats.app.utils

import android.location.Location
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.model.VehicleType
import kotlin.math.abs

/**
 * Analizador de rutas para cálculo preciso de estadísticas post-grabación
 * Incluye detección de pausas, filtrado de outliers y análisis de segmentos en movimiento
 */
class RouteAnalyzer {
    
    data class RoutePoint(
        val latitude: Double,
        val longitude: Double,
        val speed: Float,        // m/s
        val accuracy: Float,     // metros
        val timestamp: Long,     // milisegundos
        val altitude: Double? = null
    )
    
    data class RouteStatistics(
        val totalDistance: Double,      // metros
        val movingTime: Long,           // milisegundos
        val totalTime: Long,            // milisegundos
        val averageSpeed: Float,        // km/h
        val maxSpeed: Float,            // km/h
        val movingPercentage: Float     // %
    )
    
    data class MovingSegment(val points: MutableList<RoutePoint>)
    
    data class Pause(
        val startTime: Long,
        val endTime: Long,
        val duration: Long,
        val location: Location
    )
    
    data class RouteSummary(
        val totalDistance: Double,        // km
        val movingTime: Long,             // ms
        val totalTime: Long,              // ms
        val pauseTime: Long,              // ms
        val averageMovingSpeed: Float,    // km/h (sin pausas)
        val averageOverallSpeed: Float,   // km/h (con pausas)
        val maxSpeed: Float,              // km/h
        val pauseCount: Int,
        val movingPercentage: Float       // %
    )
    
    /**
     * Analiza una ruta completa y devuelve estadísticas precisas
     */
    fun analyzeRoute(points: List<RoutePoint>, vehicleType: VehicleType): RouteStatistics {
        if (points.size < 2) return RouteStatistics(0.0, 0L, 0L, 0f, 0f, 0f)
        
        // PASO 1: Filtrar puntos con mala precisión
        val accuratePoints = points.filter { it.accuracy < 20f }
        
        // PASO 2: Detectar y marcar segmentos en movimiento
        val movingSegments = detectMovingSegments(accuratePoints, vehicleType)
        
        // PASO 3: Calcular estadísticas solo de segmentos en movimiento
        return calculateStatistics(movingSegments)
    }
    
    /**
     * Detecta segmentos donde el vehículo está realmente en movimiento
     */
    private fun detectMovingSegments(
        points: List<RoutePoint>, 
        vehicleType: VehicleType
    ): List<MovingSegment> {
        val segments = mutableListOf<MovingSegment>()
        var currentSegment: MutableList<RoutePoint>? = null
        
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val current = points[i]
            
            val timeDiff = current.timestamp - prev.timestamp
            if (timeDiff > 30000) { // 30 segundos = nueva sesión
                currentSegment = null
                continue
            }
            
            val distance = calculateDistance(prev, current)
            val speed = (distance / (timeDiff / 1000f)) * 3.6f // km/h
            
            // CRITERIOS DE MOVIMIENTO REAL
            val isMoving = speed >= vehicleType.minSpeed && 
                          speed <= vehicleType.maxSpeed &&
                          distance > 3f && // mínimo 3 metros
                          current.accuracy < 15f
            
            if (isMoving) {
                if (currentSegment == null) {
                    currentSegment = mutableListOf(prev)
                    segments.add(MovingSegment(currentSegment))
                }
                currentSegment.add(current)
            } else {
                // Fin del segmento en movimiento
                currentSegment = null
            }
        }
        
        return segments
    }
    
    /**
     * Calcula estadísticas de los segmentos en movimiento
     */
    private fun calculateStatistics(segments: List<MovingSegment>): RouteStatistics {
        var totalDistance = 0.0
        var movingTime = 0L
        var maxSpeed = 0f
        
        for (segment in segments) {
            val points = segment.points
            
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val current = points[i]
                
                val distance = calculateDistance(prev, current)
                val timeDiff = current.timestamp - prev.timestamp
                val speed = (distance / (timeDiff / 1000f)) * 3.6f
                
                totalDistance += distance
                movingTime += timeDiff
                
                if (speed > maxSpeed && speed < 100f) {
                    maxSpeed = speed
                }
            }
        }
        
        val averageSpeed = if (movingTime > 0) {
            ((totalDistance / (movingTime / 1000f)) * 3.6f).toFloat()
        } else 0f
        
        val firstPoint = segments.firstOrNull()?.points?.firstOrNull()
        val lastPoint = segments.lastOrNull()?.points?.lastOrNull()
        val totalTime = if (firstPoint != null && lastPoint != null) {
            lastPoint.timestamp - firstPoint.timestamp
        } else 0L
        
        val movingPercentage = if (totalTime > 0) {
            (movingTime.toFloat() / totalTime.toFloat()) * 100f
        } else 0f
        
        return RouteStatistics(
            totalDistance = totalDistance,
            movingTime = movingTime,
            totalTime = totalTime,
            averageSpeed = averageSpeed,
            maxSpeed = maxSpeed,
            movingPercentage = movingPercentage
        )
    }
    
    /**
     * Detecta pausas en la ruta con algoritmo mejorado
     */
    fun detectPauses(
        points: List<RoutePoint>,
        vehicleType: VehicleType
    ): List<Pause> {
        val pauses = mutableListOf<Pause>()
        var pauseStart: RoutePoint? = null
        var pausePoints = mutableListOf<RoutePoint>()
        var consecutiveSlowPoints = 0
        
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val current = points[i]
            
            val distance = calculateDistance(prev, current)
            val timeDiff = current.timestamp - prev.timestamp
            
            // Calcular velocidad entre puntos
            val speed = if (timeDiff > 0) {
                (distance / (timeDiff / 1000f)) * 3.6f // km/h
            } else {
                0f
            }
            
            // CRITERIOS MEJORADOS DE PAUSA:
            // 1. Velocidad muy baja (bajo umbral del vehículo)
            // 2. Movimiento mínimo en radio pequeño
            // 3. Tiempo entre puntos razonable (no muy largo)
            val isStationary = speed <= vehicleType.pauseSpeedThreshold && 
                              distance < vehicleType.pauseRadius &&
                              timeDiff < 15000L // máximo 15s entre puntos
            
            if (isStationary) {
                consecutiveSlowPoints++
                
                if (pauseStart == null) {
                    pauseStart = prev
                    pausePoints.clear()
                }
                pausePoints.add(current)
                
                // Confirmar pausa solo si hay suficientes puntos consecutivos lentos
                if (consecutiveSlowPoints >= vehicleType.minPointsForPause) {
                    // La pausa ya está confirmada, seguir acumulando puntos
                }
            } else {
                // Fin de pausa potencial
                if (pauseStart != null && pausePoints.isNotEmpty() && 
                    consecutiveSlowPoints >= vehicleType.minPointsForPause) {
                    
                    val pauseEnd = pausePoints.last()
                    val duration = pauseEnd.timestamp - pauseStart.timestamp
                    
                    // Verificar duración mínima de pausa
                    if (duration >= vehicleType.minPauseDuration) {
                        pauses.add(Pause(
                            startTime = pauseStart.timestamp,
                            endTime = pauseEnd.timestamp,
                            duration = duration,
                            location = Location("").apply {
                                latitude = pauseStart.latitude
                                longitude = pauseStart.longitude
                            }
                        ))
                    }
                }
                
                // Resetear estado de pausa
                pauseStart = null
                pausePoints.clear()
                consecutiveSlowPoints = 0
            }
        }
        
        // Procesar última pausa si termina en pausa
        if (pauseStart != null && pausePoints.isNotEmpty() && 
            consecutiveSlowPoints >= vehicleType.minPointsForPause) {
            val pauseEnd = pausePoints.last()
            val duration = pauseEnd.timestamp - pauseStart.timestamp
            
            if (duration >= vehicleType.minPauseDuration) {
                pauses.add(Pause(
                    startTime = pauseStart.timestamp,
                    endTime = pauseEnd.timestamp,
                    duration = duration,
                    location = Location("").apply {
                        latitude = pauseStart.latitude
                        longitude = pauseStart.longitude
                    }
                ))
            }
        }
        
        return pauses
    }
    
    /**
     * Filtra outliers (puntos GPS claramente incorrectos)
     */
    fun filterOutliers(points: List<RoutePoint>): List<RoutePoint> {
        if (points.size < 3) return points
        
        val filtered = mutableListOf(points.first())
        
        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val current = points[i]
            val next = points[i + 1]
            
            // Calcular velocidad hacia este punto y desde este punto
            val speedIn = calculateSpeed(prev, current)
            val speedOut = calculateSpeed(current, next)
            
            // FILTROS
            val hasGoodAccuracy = current.accuracy < 20f
            val hasReasonableSpeed = speedIn < 80f && speedOut < 80f
            val hasReasonableAcceleration = abs(speedOut - speedIn) < 30f
            val notTooFar = calculateDistance(prev, current) < 200f // 200m entre puntos
            
            if (hasGoodAccuracy && hasReasonableSpeed && 
                hasReasonableAcceleration && notTooFar) {
                filtered.add(current)
            }
            // Si no cumple criterios, este punto es un outlier y se descarta
        }
        
        filtered.add(points.last())
        return filtered
    }
    
    /**
     * Genera un resumen completo de la ruta
     */
    fun generateSummary(
        rawPoints: List<RoutePoint>,
        vehicleType: VehicleType
    ): RouteSummary {
        
        // PASO 1: Limpiar datos erróneos
        val cleanPoints = filterOutliers(rawPoints)
        
        // PASO 2: Detectar pausas con algoritmo mejorado
        val pauses = detectPauses(cleanPoints, vehicleType)
        
        // PASO 3: Analizar ruta (solo segmentos en movimiento)
        val stats = analyzeRoute(cleanPoints, vehicleType)
        
        // PASO 4: Calcular métricas adicionales
        val totalPauseTime = pauses.sumOf { it.duration }
        
        return RouteSummary(
            totalDistance = stats.totalDistance / 1000.0, // km
            movingTime = stats.movingTime,
            totalTime = stats.totalTime,
            pauseTime = totalPauseTime,
            averageMovingSpeed = stats.averageSpeed, // km/h solo en movimiento
            averageOverallSpeed = calculateOverallSpeed(stats), // km/h con pausas
            maxSpeed = stats.maxSpeed,
            pauseCount = pauses.size,
            movingPercentage = stats.movingPercentage
        )
    }
    
    private fun calculateOverallSpeed(stats: RouteStatistics): Float {
        return if (stats.totalTime > 0) {
            ((stats.totalDistance / (stats.totalTime / 1000f)) * 3.6f).toFloat()
        } else 0f
    }
    
    private fun calculateDistance(p1: RoutePoint, p2: RoutePoint): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            p1.latitude, p1.longitude,
            p2.latitude, p2.longitude,
            results
        )
        return results[0]
    }
    
    private fun calculateSpeed(p1: RoutePoint, p2: RoutePoint): Float {
        val distance = calculateDistance(p1, p2)
        val timeDiff = (p2.timestamp - p1.timestamp) / 1000f
        return if (timeDiff > 0) (distance / timeDiff) * 3.6f else 0f
    }
}

