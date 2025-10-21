package com.zipstats.app.util

import android.location.Location
import com.zipstats.app.model.RoutePoint
import kotlin.math.*

/**
 * Utilidades para cálculos de ubicación y distancia
 */
object LocationUtils {
    
    private const val EARTH_RADIUS_KM = 6371.0 // Radio de la Tierra en kilómetros
    
    /**
     * Calcula la distancia entre dos puntos usando la fórmula de Haversine
     * @param lat1 Latitud del primer punto
     * @param lon1 Longitud del primer punto
     * @param lat2 Latitud del segundo punto
     * @param lon2 Longitud del segundo punto
     * @return Distancia en kilómetros
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * 
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return EARTH_RADIUS_KM * c
    }
    
    /**
     * Calcula la distancia entre dos RoutePoint
     */
    fun calculateDistance(point1: RoutePoint, point2: RoutePoint): Double {
        return calculateDistance(
            point1.latitude, 
            point1.longitude, 
            point2.latitude, 
            point2.longitude
        )
    }
    
    /**
     * Calcula la distancia total de una lista de puntos
     * @param points Lista de puntos GPS
     * @return Distancia total en kilómetros
     */
    fun calculateTotalDistance(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            totalDistance += calculateDistance(points[i], points[i + 1])
        }
        
        return totalDistance
    }
    
    /**
     * Filtra puntos GPS que estén muy cercanos o tengan mala precisión
     * Esto ayuda a reducir el "ruido" del GPS
     * @param points Lista de puntos originales
     * @param minDistance Distancia mínima entre puntos en metros (por defecto 5m)
     * @param maxAccuracy Precisión máxima aceptable en metros (por defecto 50m)
     * @return Lista filtrada de puntos
     */
    fun filterPoints(
        points: List<RoutePoint>, 
        minDistance: Double = 0.005, // 5 metros en km
        maxAccuracy: Float = 50f
    ): List<RoutePoint> {
        if (points.isEmpty()) return emptyList()
        
        val filtered = mutableListOf<RoutePoint>()
        filtered.add(points.first())
        
        for (i in 1 until points.size) {
            val current = points[i]
            val previous = filtered.last()
            
            // Filtrar por precisión
            if (current.accuracy != null && current.accuracy > maxAccuracy) {
                continue
            }
            
            // Filtrar por distancia mínima
            val distance = calculateDistance(previous, current)
            if (distance >= minDistance) {
                filtered.add(current)
            }
        }
        
        return filtered
    }
    
    /**
     * Calcula la velocidad promedio en km/h
     * @param distanceKm Distancia en kilómetros
     * @param durationMs Duración en milisegundos
     * @return Velocidad promedio en km/h
     */
    fun calculateAverageSpeed(distanceKm: Double, durationMs: Long): Double {
        if (durationMs == 0L) return 0.0
        val durationHours = durationMs / (1000.0 * 60.0 * 60.0)
        return distanceKm / durationHours
    }
    
    /**
     * Calcula la velocidad máxima de una lista de puntos
     * @param points Lista de puntos GPS
     * @return Velocidad máxima en km/h
     */
    fun calculateMaxSpeed(points: List<RoutePoint>): Double {
        return points.mapNotNull { it.speed }
            .maxOrNull()
            ?.let { it * 3.6 } // Convertir de m/s a km/h
            ?: 0.0
    }
    
    /**
     * Convierte velocidad de m/s a km/h
     */
    fun metersPerSecondToKmPerHour(speed: Float): Double {
        return speed * 3.6
    }
    
    /**
     * Filtra la velocidad para considerar velocidades muy bajas como "parado"
     * @param speedKmh Velocidad en km/h
     * @param minSpeedThreshold Umbral mínimo de velocidad en km/h (por defecto 1.5 km/h)
     * @return Velocidad filtrada (0.0 si está por debajo del umbral)
     */
    fun filterSpeed(speedKmh: Double, minSpeedThreshold: Double = 1.5): Double {
        return if (speedKmh < minSpeedThreshold) 0.0 else speedKmh
    }
    
    /**
     * Formatea la distancia para mostrar
     * @param distanceKm Distancia en kilómetros
     * @return String formateado (ej: "1.5 km" o "250 m")
     */
    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).roundToInt()} m"
        } else {
            "%.2f km".format(distanceKm)
        }
    }
    
    /**
     * Formatea la velocidad para mostrar
     * @param speedKmh Velocidad en km/h
     * @return String formateado (ej: "15.5 km/h")
     */
    fun formatSpeed(speedKmh: Double): String {
        return "%.1f km/h".format(speedKmh)
    }
    
    /**
     * Crea un RoutePoint desde un Location de Android
     */
    fun locationToRoutePoint(location: Location): RoutePoint {
        return RoutePoint(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = location.time,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            speed = if (location.hasSpeed()) location.speed else null
        )
    }
}

