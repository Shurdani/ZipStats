package com.zipstats.app.analysis

import android.location.Location
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.model.VehicleType
import kotlin.math.abs

/**
 * Filtro para eliminar puntos GPS claramente erróneos
 * Clase independiente para análisis post-ruta
 */
class OutlierFilter(private val vehicleType: VehicleType) {
    
    // Configuración de filtros
    private val MAX_ACCURACY = 25f           // metros - precisión máxima aceptable
    private val MAX_REASONABLE_DISTANCE = 200f // metros entre puntos consecutivos
    private val MAX_ACCELERATION = 30f        // km/h de cambio máximo entre puntos
    private val MAX_TIME_GAP = 30000L         // ms - tiempo máximo entre puntos (30 segundos)
    private val MIN_TIME_GAP = 100L           // ms - tiempo mínimo entre puntos (0.1 segundos)
    
    /**
     * Filtra puntos GPS eliminando outliers
     * 
     * @param points Lista de puntos originales
     * @return Lista de puntos limpios
     */
    fun filter(points: List<RoutePoint>): List<RoutePoint> {
        if (points.size < 3) return points
        
        val filtered = mutableListOf<RoutePoint>()
        
        // Siempre mantener primer punto
        filtered.add(points.first())
        
        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val current = points[i]
            val next = points[i + 1]
            
            if (isValidPoint(prev, current, next)) {
                filtered.add(current)
            }
            // Si no es válido, simplemente no se añade (se descarta)
        }
        
        // Siempre mantener último punto
        filtered.add(points.last())
        
        return filtered
    }
    
    /**
     * Filtra puntos GPS con configuración personalizada
     * 
     * @param points Lista de puntos originales
     * @param maxAccuracy Precisión máxima personalizada
     * @param maxDistance Distancia máxima personalizada
     * @param maxAcceleration Aceleración máxima personalizada
     * @return Lista de puntos limpios
     */
    fun filterWithCustomParams(
        points: List<RoutePoint>,
        maxAccuracy: Float = MAX_ACCURACY,
        maxDistance: Float = MAX_REASONABLE_DISTANCE,
        maxAcceleration: Float = MAX_ACCELERATION
    ): List<RoutePoint> {
        if (points.size < 3) return points
        
        val filtered = mutableListOf<RoutePoint>()
        filtered.add(points.first())
        
        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val current = points[i]
            val next = points[i + 1]
            
            if (isValidPointWithParams(prev, current, next, maxAccuracy, maxDistance, maxAcceleration)) {
                filtered.add(current)
            }
        }
        
        filtered.add(points.last())
        return filtered
    }
    
    /**
     * Valida un punto usando los parámetros por defecto
     */
    private fun isValidPoint(
        prev: RoutePoint,
        current: RoutePoint,
        next: RoutePoint
    ): Boolean {
        return isValidPointWithParams(prev, current, next, MAX_ACCURACY, MAX_REASONABLE_DISTANCE, MAX_ACCELERATION)
    }
    
    /**
     * Valida un punto usando parámetros personalizados
     */
    private fun isValidPointWithParams(
        prev: RoutePoint,
        current: RoutePoint,
        next: RoutePoint,
        maxAccuracy: Float,
        maxDistance: Float,
        maxAcceleration: Float
    ): Boolean {
        // CRITERIO 1: Precisión GPS aceptable
        if (current.accuracy != null && current.accuracy > maxAccuracy) {
            return false
        }
        
        // CRITERIO 2: Velocidad dentro de rango razonable
        val speed = current.speed?.let { it * 3.6f } ?: 0f // m/s → km/h
        if (speed > vehicleType.maxSpeed * 1.5f) {
            return false
        }
        
        // CRITERIO 3: Distancia razonable desde punto anterior
        val distanceFromPrev = calculateDistance(prev, current)
        if (distanceFromPrev > maxDistance) {
            return false
        }
        
        // CRITERIO 4: Aceleración razonable
        val speedIn = calculateSpeed(prev, current)
        val speedOut = calculateSpeed(current, next)
        val acceleration = abs(speedOut - speedIn)
        
        if (acceleration > maxAcceleration) {
            return false
        }
        
        // CRITERIO 5: No saltos temporales grandes
        val timeDelta = current.timestamp - prev.timestamp
        if (timeDelta > MAX_TIME_GAP || timeDelta < MIN_TIME_GAP) {
            return false
        }
        
        // CRITERIO 6: Velocidad mínima razonable (evitar drift GPS)
        if (speed > 0 && speed < vehicleType.minSpeed * 0.5f) {
            // Solo rechazar si hay movimiento muy lento consistente
            val nextSpeed = next.speed?.let { it * 3.6f } ?: 0f
            if (nextSpeed < vehicleType.minSpeed * 0.5f) {
                return false
            }
        }
        
        return true
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
     * Calcula la velocidad entre dos puntos GPS
     */
    private fun calculateSpeed(p1: RoutePoint, p2: RoutePoint): Float {
        val distance = calculateDistance(p1, p2)
        val timeDiff = (p2.timestamp - p1.timestamp) / 1000f // segundos
        return if (timeDiff > 0) (distance / timeDiff) * 3.6f else 0f // km/h
    }
    
    /**
     * Obtiene estadísticas del filtrado
     */
    fun getFilteringStats(originalPoints: List<RoutePoint>, filteredPoints: List<RoutePoint>): FilteringStats {
        val removedCount = originalPoints.size - filteredPoints.size
        val removalPercentage = if (originalPoints.isNotEmpty()) {
            (removedCount.toFloat() / originalPoints.size.toFloat()) * 100f
        } else 0f
        
        return FilteringStats(
            originalCount = originalPoints.size,
            filteredCount = filteredPoints.size,
            removedCount = removedCount,
            removalPercentage = removalPercentage
        )
    }
}

/**
 * Estadísticas del proceso de filtrado
 */
data class FilteringStats(
    val originalCount: Int,
    val filteredCount: Int,
    val removedCount: Int,
    val removalPercentage: Float
)
