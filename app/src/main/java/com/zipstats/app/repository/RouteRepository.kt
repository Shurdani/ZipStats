package com.zipstats.app.repository

import android.util.Log
import com.zipstats.app.model.Route
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.model.VehicleType
import com.zipstats.app.util.LocationUtils
import com.zipstats.app.util.RouteAnalyzer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio para gestionar rutas GPS en Firebase
 */
@Singleton
class RouteRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    
    private val routeAnalyzer = RouteAnalyzer()
    
    companion object {
        private const val TAG = "RouteRepository"
        private const val ROUTES_COLLECTION = "routes"
    }
    
    /**
     * Guarda una ruta completa en Firebase
     */
    suspend fun saveRoute(route: Route): Result<String> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(
                Exception("Usuario no autenticado")
            )
            
            // Crear referencia al documento
            val docRef = firestore.collection(ROUTES_COLLECTION).document()
            
            // Guardar la ruta
            val routeData = route.copy(
                id = docRef.id,
                userId = userId
            ).toMap()
            
            docRef.set(routeData).await()
            
            Log.d(TAG, "Ruta guardada exitosamente: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar ruta", e)
            Result.failure(e)
        }
    }
    
    /**
     * Crea una ruta a partir de una lista de puntos GPS con análisis post-ruta y clima
     */
    suspend fun createRouteWithWeather(
        points: List<RoutePoint>,
        scooterId: String,
        scooterName: String,
        startTime: Long,
        endTime: Long,
        notes: String = "",
        timeInMotion: Long? = null,
        vehicleType: VehicleType = VehicleType.PATINETE
    ): Route {
        // Crear la ruta base
        val route = createRouteFromPoints(
            points = points,
            scooterId = scooterId,
            scooterName = scooterName,
            startTime = startTime,
            endTime = endTime,
            notes = notes,
            timeInMotion = timeInMotion,
            vehicleType = vehicleType
        )
        
        // Intentar obtener el clima actual
        if (points.isNotEmpty()) {
            try {
                val weatherRepository = WeatherRepository()
                val firstPoint = points.first()
                
                Log.d(TAG, "Obteniendo clima para ruta en lat=${firstPoint.latitude}, lon=${firstPoint.longitude}")
                
                val result = weatherRepository.getCurrentWeather(
                    latitude = firstPoint.latitude,
                    longitude = firstPoint.longitude
                )
                
                result.onSuccess { weather ->
                    Log.d(TAG, "Clima obtenido y guardado: ${weather.temperature}°C, ${weather.weatherEmoji}")
                    return route.copy(
                        weatherTemperature = weather.temperature,
                        weatherEmoji = weather.weatherEmoji,
                        weatherDescription = weather.description
                    )
                }.onFailure { error ->
                    Log.e(TAG, "Error al obtener clima: ${error.message}", error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción al obtener clima: ${e.message}", e)
            }
        }
        
        // Si no se pudo obtener el clima, devolver la ruta sin él
        return route
    }
    
    /**
     * Crea una ruta a partir de una lista de puntos GPS con análisis post-ruta
     */
    fun createRouteFromPoints(
        points: List<RoutePoint>,
        scooterId: String,
        scooterName: String,
        startTime: Long,
        endTime: Long,
        notes: String = "",
        timeInMotion: Long? = null,
        vehicleType: VehicleType = VehicleType.PATINETE
    ): Route {
        // Filtrar puntos para eliminar ruido GPS
        val filteredPoints = LocationUtils.filterPoints(points)
        
        // Asegurar que tenemos al menos 2 puntos para el mapa
        val finalPoints = if (filteredPoints.size < 2 && points.size >= 2) {
            // Si el filtrado eliminó demasiados puntos, usar los originales
            Log.w(TAG, "Filtrado eliminó demasiados puntos (${filteredPoints.size}/${points.size}), usando puntos originales")
            points
        } else {
            filteredPoints
        }
        
        // Calcular estadísticas básicas
        val totalDistance = LocationUtils.calculateTotalDistance(finalPoints)
        val totalDuration = endTime - startTime
        
        // Convertir RoutePoint a RouteAnalyzer.RoutePoint para análisis
        val analyzerPoints = finalPoints.map { point ->
            RouteAnalyzer.RoutePoint(
                latitude = point.latitude,
                longitude = point.longitude,
                speed = point.speed ?: 0f,
                accuracy = point.accuracy ?: 100f,
                timestamp = point.timestamp,
                altitude = point.altitude
            )
        }
        
        // Realizar análisis post-ruta completo
        val routeSummary = routeAnalyzer.generateSummary(analyzerPoints, vehicleType)
        
        // Usar el análisis post-ruta para métricas más precisas
        val averageSpeed = if (routeSummary.averageOverallSpeed > 0) {
            routeSummary.averageOverallSpeed.toDouble()
        } else if (timeInMotion != null && timeInMotion > 0) {
            // Fallback al cálculo en tiempo real si está disponible
            val timeInMotionHours = timeInMotion / (1000.0 * 60.0 * 60.0)
            if (timeInMotionHours > 0) totalDistance / timeInMotionHours else 0.0
        } else {
            // Fallback al cálculo tradicional
            LocationUtils.calculateAverageSpeed(totalDistance, totalDuration)
        }
        
        val maxSpeed = if (routeSummary.maxSpeed > 0) {
            routeSummary.maxSpeed.toDouble()
        } else {
            LocationUtils.calculateMaxSpeed(finalPoints)
        }
        
        return Route(
            userId = auth.currentUser?.uid ?: "",
            scooterId = scooterId,
            scooterName = scooterName,
            startTime = startTime,
            endTime = endTime,
            totalDistance = totalDistance,
            totalDuration = totalDuration,
            averageSpeed = averageSpeed,
            maxSpeed = maxSpeed,
            points = finalPoints,
            isCompleted = true,
            notes = notes,
            // Nuevas métricas de análisis post-ruta
            movingTime = routeSummary.movingTime,
            pauseTime = routeSummary.pauseTime,
            averageMovingSpeed = routeSummary.averageMovingSpeed.toDouble(),
            pauseCount = routeSummary.pauseCount,
            movingPercentage = routeSummary.movingPercentage
        )
    }
    
    /**
     * Obtiene todas las rutas del usuario actual
     */
    suspend fun getUserRoutes(): Result<List<Route>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(
                Exception("Usuario no autenticado")
            )
            
            val snapshot = firestore.collection(ROUTES_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val routes = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    Route.fromMap(doc.id, data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al parsear ruta ${doc.id}", e)
                    null
                }
            }
            
            Log.d(TAG, "Rutas obtenidas: ${routes.size}")
            Result.success(routes)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener rutas", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene todas las rutas del usuario actual como Flow (reactivo)
     */
    fun getUserRoutesFlow(): Flow<List<Route>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            close(Exception("Usuario no autenticado"))
            return@callbackFlow
        }
        
        val listener = firestore.collection(ROUTES_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("startTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error en listener de rutas", error)
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val routes = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            Route.fromMap(doc.id, data)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al parsear ruta ${doc.id}", e)
                            null
                        }
                    }
                    Log.d(TAG, "Rutas obtenidas (Flow): ${routes.size}")
                    trySend(routes)
                }
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Obtiene las rutas de un patinete específico
     */
    suspend fun getScooterRoutes(scooterId: String): Result<List<Route>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(
                Exception("Usuario no autenticado")
            )
            
            val snapshot = firestore.collection(ROUTES_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("scooterId", scooterId)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val routes = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    Route.fromMap(doc.id, data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al parsear ruta ${doc.id}", e)
                    null
                }
            }
            
            Result.success(routes)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener rutas del patinete", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene una ruta específica por ID
     */
    suspend fun getRoute(routeId: String): Result<Route> {
        return try {
            val doc = firestore.collection(ROUTES_COLLECTION)
                .document(routeId)
                .get()
                .await()
            
            val data = doc.data ?: return Result.failure(
                Exception("Ruta no encontrada")
            )
            
            val route = Route.fromMap(doc.id, data)
            Result.success(route)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener ruta", e)
            Result.failure(e)
        }
    }
    
    /**
     * Elimina una ruta
     */
    suspend fun deleteRoute(routeId: String): Result<Unit> {
        return try {
            firestore.collection(ROUTES_COLLECTION)
                .document(routeId)
                .delete()
                .await()
            
            Log.d(TAG, "Ruta eliminada: $routeId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar ruta", e)
            Result.failure(e)
        }
    }
    
    /**
     * Elimina todas las rutas de un vehículo específico
     */
    suspend fun deleteScooterRoutes(scooterId: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            val routes = firestore.collection(ROUTES_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("scooterId", scooterId)
                .get()
                .await()
                .documents

            // Eliminar todas las rutas del vehículo
            routes.forEach { doc ->
                doc.reference.delete().await()
            }
            
            Log.d(TAG, "Rutas eliminadas para vehículo: $scooterId (${routes.size} rutas)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar rutas del vehículo", e)
            Result.failure(e)
        }
    }
    
    /**
     * Elimina todas las rutas del usuario actual
     */
    suspend fun deleteAllUserRoutes(): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            val routes = firestore.collection(ROUTES_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents

            // Eliminar todas las rutas del usuario
            routes.forEach { doc ->
                doc.reference.delete().await()
            }
            
            Log.d(TAG, "Todas las rutas eliminadas para usuario: $userId (${routes.size} rutas)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar todas las rutas del usuario", e)
            Result.failure(e)
        }
    }
    
    /**
     * Actualiza las notas de una ruta
     */
    suspend fun updateRouteNotes(routeId: String, notes: String): Result<Unit> {
        return try {
            firestore.collection(ROUTES_COLLECTION)
                .document(routeId)
                .update("notes", notes)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar notas", e)
            Result.failure(e)
        }
    }
    
    /**
     * Actualiza los datos del clima de una ruta
     */
    suspend fun updateRouteWeather(
        routeId: String,
        temperature: Double,
        emoji: String,
        description: String
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "weatherTemperature" to temperature,
                "weatherEmoji" to emoji,
                "weatherDescription" to description
            )
            
            firestore.collection(ROUTES_COLLECTION)
                .document(routeId)
                .update(updates)
                .await()
            
            Log.d(TAG, "Clima actualizado para ruta $routeId: $temperature°C, $emoji")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar clima de la ruta", e)
            Result.failure(e)
        }
    }
    
    /**
     * Intenta obtener y actualizar el clima para una ruta específica
     * Útil para rutas guardadas sin datos meteorológicos
     */
    suspend fun fetchAndUpdateWeather(routeId: String, latitude: Double, longitude: Double): Result<Unit> {
        return try {
            val weatherRepository = WeatherRepository()
            
            Log.d(TAG, "Obteniendo clima para actualizar ruta $routeId")
            
            val result = weatherRepository.getCurrentWeather(latitude, longitude)
            
            result.onSuccess { weather ->
                updateRouteWeather(
                    routeId = routeId,
                    temperature = weather.temperature,
                    emoji = weather.weatherEmoji,
                    description = weather.description
                )
                Log.d(TAG, "✅ Clima actualizado exitosamente para ruta $routeId")
            }.onFailure { error ->
                Log.e(TAG, "❌ Error al obtener clima para actualizar: ${error.message}", error)
                return Result.failure(error)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al obtener y actualizar clima", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene el kilometraje total de todas las rutas del usuario
     */
    suspend fun getTotalKilometers(): Result<Double> {
        return try {
            val routesResult = getUserRoutes()
            if (routesResult.isFailure) {
                return Result.failure(routesResult.exceptionOrNull()!!)
            }
            
            val totalKm = routesResult.getOrNull()
                ?.sumOf { it.totalDistance }
                ?: 0.0
            
            Result.success(totalKm)
        } catch (e: Exception) {
            Log.e(TAG, "Error al calcular kilometraje total", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene el kilometraje total de un patinete específico
     */
    suspend fun getScooterTotalKilometers(scooterId: String): Result<Double> {
        return try {
            val routesResult = getScooterRoutes(scooterId)
            if (routesResult.isFailure) {
                return Result.failure(routesResult.exceptionOrNull()!!)
            }
            
            val totalKm = routesResult.getOrNull()
                ?.sumOf { it.totalDistance }
                ?: 0.0
            
            Result.success(totalKm)
        } catch (e: Exception) {
            Log.e(TAG, "Error al calcular kilometraje del patinete", e)
            Result.failure(e)
        }
    }
}

