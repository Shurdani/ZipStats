package com.zipstats.app.model

import com.google.firebase.firestore.GeoPoint

/**
 * Representa un punto GPS en una ruta
 */
data class RoutePoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val speed: Float? = null
) {
    /**
     * Convierte a GeoPoint para Firebase
     */
    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
    
    /**
     * Convierte a Map para guardar en Firebase con metadata adicional
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "latitude" to latitude,
        "longitude" to longitude,
        "timestamp" to timestamp,
        "altitude" to altitude,
        "accuracy" to accuracy,
        "speed" to speed
    )
    
    companion object {
        /**
         * Crea un RoutePoint desde un Map de Firebase
         */
        fun fromMap(map: Map<String, Any?>): RoutePoint {
            return RoutePoint(
                latitude = map["latitude"] as? Double ?: 0.0,
                longitude = map["longitude"] as? Double ?: 0.0,
                timestamp = map["timestamp"] as? Long ?: 0L,
                altitude = map["altitude"] as? Double,
                accuracy = (map["accuracy"] as? Number)?.toFloat(),
                speed = (map["speed"] as? Number)?.toFloat()
            )
        }
    }
}

