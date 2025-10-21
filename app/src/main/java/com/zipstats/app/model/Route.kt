package com.zipstats.app.model

import com.google.firebase.firestore.DocumentId

/**
 * Representa una ruta completa con todos sus puntos GPS
 */
data class Route(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val scooterId: String = "",
    val scooterName: String = "",
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val totalDistance: Double = 0.0, // en kilómetros
    val totalDuration: Long = 0L, // en milisegundos
    val averageSpeed: Double = 0.0, // en km/h
    val maxSpeed: Double = 0.0, // en km/h
    val points: List<RoutePoint> = emptyList(),
    val isCompleted: Boolean = false,
    val notes: String = ""
) {
    /**
     * Calcula la duración en minutos
     */
    val durationInMinutes: Long
        get() = totalDuration / (1000 * 60)
    
    /**
     * Calcula la duración en formato legible (HH:MM:SS)
     */
    val durationFormatted: String
        get() {
            val seconds = totalDuration / 1000
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, secs)
            } else {
                String.format("%02d:%02d", minutes, secs)
            }
        }
    
    /**
     * Convierte a Map para guardar en Firebase
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "scooterId" to scooterId,
        "scooterName" to scooterName,
        "startTime" to startTime,
        "endTime" to endTime,
        "totalDistance" to totalDistance,
        "totalDuration" to totalDuration,
        "averageSpeed" to averageSpeed,
        "maxSpeed" to maxSpeed,
        "points" to points.map { it.toMap() },
        "isCompleted" to isCompleted,
        "notes" to notes
    )
    
    companion object {
        /**
         * Crea una Route desde un Map de Firebase
         */
        fun fromMap(id: String, map: Map<String, Any?>): Route {
            val pointsList = (map["points"] as? List<*>)?.mapNotNull { point ->
                (point as? Map<*, *>)?.let { 
                    RoutePoint.fromMap(it as Map<String, Any?>) 
                }
            } ?: emptyList()
            
            return Route(
                id = id,
                userId = map["userId"] as? String ?: "",
                scooterId = map["scooterId"] as? String ?: "",
                scooterName = map["scooterName"] as? String ?: "",
                startTime = map["startTime"] as? Long ?: 0L,
                endTime = map["endTime"] as? Long,
                totalDistance = map["totalDistance"] as? Double ?: 0.0,
                totalDuration = map["totalDuration"] as? Long ?: 0L,
                averageSpeed = map["averageSpeed"] as? Double ?: 0.0,
                maxSpeed = map["maxSpeed"] as? Double ?: 0.0,
                points = pointsList,
                isCompleted = map["isCompleted"] as? Boolean ?: false,
                notes = map["notes"] as? String ?: ""
            )
        }
    }
}

