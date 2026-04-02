package com.zipstats.app.utils

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
     * Esto ayuda a reducir el "ruido" del GPS y puntos parados
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
            
            // Calcular distancia y velocidad
            val distance = calculateDistance(previous, current)
            val timeDiff = (current.timestamp - previous.timestamp) / 1000.0 // segundos
            val speed = if (timeDiff > 0) (distance / timeDiff) * 3.6 else 0.0 // km/h
            
            // FILTROS MEJORADOS:
            // 1. Distancia mínima
            // 2. Tiempo máximo entre puntos (evitar gaps largos)
            // 3. Velocidad mínima solo para puntos muy lentos (más permisivo)
            val isValidPoint = distance >= minDistance && 
                              timeDiff <= 30.0 && // Máximo 30 segundos entre puntos
                              (speed >= 0.5 || timeDiff <= 5.0) // Mínimo 0.5 km/h o puntos muy cercanos en tiempo
            
            if (isValidPoint) {
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
     * @param minSpeedThreshold Umbral mínimo de velocidad en km/h (por defecto 3.0 km/h)
     * @return Velocidad filtrada (0.0 si está por debajo del umbral)
     */
    fun filterSpeed(speedKmh: Double, minSpeedThreshold: Double = 3.0): Double {
        return if (speedKmh < minSpeedThreshold) 0.0 else speedKmh
    }
    
    /**
     * Clase para suavizado adaptativo de velocidad con EMA dinámico
     * Usa un α dinámico: agresivo cuando hay cambios bruscos, suave cuando es estable
     */
    class AdaptiveVelocitySmoothing {
        private var smoothedSpeed = 0.0
        private var previousSpeed = 0.0
        private var isInitialized = false
        
        /**
         * Actualiza la velocidad con suavizado adaptativo
         * @param newSpeed Velocidad nueva en km/h
         * @return Velocidad suavizada en km/h
         */
        fun updateSpeed(newSpeed: Double): Double {
            if (!isInitialized) {
                smoothedSpeed = newSpeed
                previousSpeed = newSpeed
                isInitialized = true
                return newSpeed
            }
            
            val speedChange = kotlin.math.abs(newSpeed - previousSpeed)
            
            // α agresivo (0.75-0.98) para cambios bruscos o arranque desde parado
            // α suave (0.35-0.4) para velocidad estable
            val alpha = when {
                // Arrancando desde parado o parando - respuesta casi instantánea
                (smoothedSpeed < 1.5 && newSpeed > 1.5) || 
                (smoothedSpeed > 1.5 && newSpeed < 1.5) -> 0.98
                // Cambio muy brusco (>5 km/h)
                speedChange > 5.0 -> 0.85
                // Cambio moderado (2-5 km/h)
                speedChange > 2.0 -> 0.65
                // Velocidad estable (<2 km/h de cambio)
                else -> 0.35
            }
            
            smoothedSpeed = alpha * newSpeed + (1.0 - alpha) * smoothedSpeed
            previousSpeed = newSpeed
            
            return smoothedSpeed
        }
        
        /**
         * Resetea el estado del suavizador
         */
        fun reset() {
            smoothedSpeed = 0.0
            previousSpeed = 0.0
            isInitialized = false
        }
        
        /**
         * Obtiene la velocidad suavizada actual
         */
        fun getCurrentSmoothedSpeed(): Double = smoothedSpeed
        
        /**
         * Verifica si está inicializado
         */
        fun isInitialized(): Boolean = isInitialized
    }
    
    /**
     * Clase para suavizado de velocidad con Media Móvil Exponencial (EMA) - DEPRECATED
     * Mantenida para compatibilidad, usar AdaptiveVelocitySmoothing en su lugar
     */
    @Deprecated("Usar AdaptiveVelocitySmoothing en su lugar")
    class SpeedSmoother(private val alpha: Double = 0.2) {
        private var emaValue: Double? = null
        private var isInitialized = false
        
        fun addSpeed(speedKmh: Double): Double {
            if (!isInitialized) {
                emaValue = speedKmh
                isInitialized = true
                return speedKmh
            }
            
            emaValue = alpha * speedKmh + (1.0 - alpha) * emaValue!!
            return emaValue!!
        }
        
        fun reset() {
            emaValue = null
            isInitialized = false
        }
        
        fun getCurrentEma(): Double? = emaValue
        fun isInitialized(): Boolean = isInitialized
    }
    
    /**
     * Formatea la distancia para mostrar (formato español: punto para miles, coma para decimales)
     * @param distanceKm Distancia en kilómetros
     * @return String formateado (ej: "1,5 km" o "250 m")
     */
    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).roundToInt()} m"
        } else {
            // Formato español: coma para decimales
            // 🔥 CORRECCIÓN: Usar formatNumberSpanish para consistencia (siempre muestra 1 decimal)
            "${formatNumberSpanish(distanceKm, 1)} km"
        }
    }
    
    /**
     * Formatea la velocidad para mostrar (formato español: punto para miles, coma para decimales)
     * @param speedKmh Velocidad en km/h
     * @return String formateado (ej: "15,5 km/h")
     */
    fun formatSpeed(speedKmh: Double): String {
        // Formato español: coma para decimales
        // 🔥 CORRECCIÓN: Usar formatNumberSpanish para consistencia (siempre muestra 1 decimal)
        return "${formatNumberSpanish(speedKmh, 1)} km/h"
    }
    
    /**
     * Formatea un número en formato español (punto para miles, coma para decimales)
     * @param value Número a formatear
     * @param decimals Número de decimales (por defecto 1)
     * @return String formateado (ej: "23.525,25" o "15,5" o "1,0")
     * 
     * 🔥 IMPORTANTE: Cuando decimals=1, siempre muestra el decimal para mantener consistencia visual
     * (ej: "1,0" en lugar de "1" para que coincida con "0,1", "0,7", etc.)
     */
    fun formatNumberSpanish(value: Double, decimals: Int = 1): String {
        return try {
            val pattern = when (decimals) {
                0 -> "#,##0"
                1 -> "#,##0.0"
                2 -> "#,##0.00"
                else -> "#,##0.${"0".repeat(decimals)}"
            }
            val formatter = java.text.DecimalFormat(pattern, java.text.DecimalFormatSymbols(java.util.Locale("es", "ES")))
            val formatted = formatter.format(value)
            // 🔥 CORRECCIÓN: Cuando decimals=1, siempre mostrar el decimal para consistencia visual
            // No quitar ",0" para que "1,0" se muestre igual que "0,1" o "0,7"
            formatted
        } catch (e: Exception) {
            // Fallback: formatear manualmente
            val formatPattern = "%.${decimals}f"
            val parts = String.format(formatPattern, value).split(".")
            if (parts.size == 2) {
                val integerPart = parts[0].reversed().chunked(3).joinToString(".").reversed()
                "$integerPart,${parts[1]}"
            } else {
                parts[0].reversed().chunked(3).joinToString(".").reversed()
            }
        }
    }

    /**
     * Convierte un número con formato "es-ES" a Double.
     * Soporta:
     * - "1.234,5"  -> 1234.5
     * - "1250.5"   -> 1250.5
     * - "1.234"     -> 1234
     */
    fun parseNumberSpanish(input: String): Double? {
        val raw = input.trim()
        if (raw.isEmpty()) return null

        // Quitar espacios (por si el IME añade separadores raros)
        val s = raw.replace("\\s".toRegex(), "")

        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        val dotCount = s.count { it == '.' }

        val normalized = when {
            lastComma != -1 && lastDot != -1 -> {
                if (lastComma > lastDot) {
                    // "1.234,5" (decimal coma)
                    s.replace(".", "").replace(',', '.')
                } else {
                    // "1,234.5" (decimal punto)
                    s.replace(",", "")
                }
            }
            lastComma != -1 -> {
                // Decimal coma, quitar miles si hubiera
                s.replace(".", "").replace(',', '.')
            }
            lastDot != -1 -> {
                // Si solo hay un punto y justo hay 3 dígitos al final, interpretarlo como miles ("1.234")
                val digitsAfterLastDot = s.length - lastDot - 1
                if (dotCount == 1 && digitsAfterLastDot == 3) {
                    s.replace(".", "")
                } else {
                    // Decimal punto ("1250.5") -> dejar tal cual
                    s
                }
            }
            else -> s
        }

        return normalized.toDoubleOrNull()
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

