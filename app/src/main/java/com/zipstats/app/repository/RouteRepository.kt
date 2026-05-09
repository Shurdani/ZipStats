package com.zipstats.app.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.zipstats.app.model.Route
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.model.RouteWeatherDecision
import com.zipstats.app.model.RouteWeatherSnapshot
import com.zipstats.app.model.SurfaceConditionType
import com.zipstats.app.model.VehicleType
import com.zipstats.app.utils.LocationUtils
import com.zipstats.app.utils.RouteAnalyzer
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
    private val auth: FirebaseAuth,
    private val weatherRepository: WeatherRepository
) {

    private val routeAnalyzer = RouteAnalyzer()

    // --- MEMORIA TEMPORAL (CAJA FUERTE) PARA EL CLIMA ---
    // Guardamos el clima aquí para que sobreviva si el usuario cambia de pantalla
    // y el ViewModel se destruye.
    private var _tempWeatherData: WeatherData? = null

    fun saveTempWeather(weather: WeatherData) {
        _tempWeatherData = weather
        Log.d(TAG, "💾 Clima guardado temporalmente en repositorio para cambio de pantalla")
    }

    fun getTempWeather(): WeatherData? {
        return _tempWeatherData
    }

    fun clearTempWeather() {
        _tempWeatherData = null
        Log.d(TAG, "🗑️ Clima temporal borrado del repositorio")
    }
    //
    
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
    fun getUserRoutesFlow(pageSize: Int = 20): Flow<List<Route>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            close(Exception("Usuario no autenticado"))
            return@callbackFlow
        }

        val listener = firestore.collection(ROUTES_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("startTime", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
            .addSnapshotListener { snapshot, error ->
                // Verificar primero si el usuario sigue autenticado
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    try {
                        trySend(emptyList())
                    } catch (e: Exception) {
                        // Ignorar errores al enviar datos si el canal está cerrado
                    }
                    try {
                        close()
                    } catch (e: Exception) {
                        // Ignorar errores si el canal ya está cerrado
                    }
                    return@addSnapshotListener
                }
                
                if (error != null) {
                    // Manejar PERMISSION_DENIED silenciosamente (típico al cerrar sesión)
                    val isPermissionError = error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    
                    if (isPermissionError || auth.currentUser == null) {
                        Log.w(TAG, "Permiso denegado o usuario no autenticado (probablemente durante logout). Cerrando listener silenciosamente.")
                        try {
                            trySend(emptyList())
                        } catch (e: Exception) {
                            // Ignorar errores al enviar datos si el canal está cerrado
                        }
                        try {
                            close()
                        } catch (e: Exception) {
                            // Ignorar errores si el canal ya está cerrado
                        }
                    } else {
                    Log.e(TAG, "Error en listener de rutas", error)
                    close(error)
                    }
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val routes = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            Route.fromMap(doc.id, data - "points")                        } catch (e: Exception) {
                            Log.e(TAG, "Error al parsear ruta ${doc.id}", e)
                            null
                        }
                    }
                    Log.d(TAG, "Rutas obtenidas (Flow): ${routes.size}")
                    
                    // Verificar nuevamente antes de enviar datos
                    if (auth.currentUser == null) {
                        try {
                            trySend(emptyList())
                            close()
                        } catch (e: Exception) {
                            // Ignorar errores si el canal está cerrado
                        }
                        return@addSnapshotListener
                    }
                    
                    try {
                    trySend(routes)
                    } catch (e: Exception) {
                        // Ignorar errores si el canal está cerrado (puede ocurrir durante logout)
                        Log.w(TAG, "Error al enviar rutas (probablemente durante logout)", e)
                    }
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
                try {
                    doc.reference.delete().await()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e // Re-lanzar cancelación
                } catch (e: Exception) {
                    Log.w(TAG, "Error al eliminar ruta individual: ${doc.id}", e)
                    // Continuar con las demás rutas
                }
            }
            
            Log.d(TAG, "Rutas eliminadas para vehículo: $scooterId (${routes.size} rutas)")
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "Eliminación de rutas cancelada para vehículo: $scooterId")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar rutas del vehículo", e)
            Result.failure(e)
        }
    }

    // Carga de páginas adicionales (cursor por startTime)
    suspend fun getNextPage(lastStartTime: Long, pageSize: Int = 20): Result<List<Route>> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.failure(Exception("Usuario no autenticado"))
            val snapshot = firestore.collection(ROUTES_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .startAfter(lastStartTime)
                .limit(pageSize.toLong())
                .get()
                .await()

            val routes = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    Route.fromMap(doc.id, data - "points")
                } catch (e: Exception) {
                    Log.e(TAG, "Error al parsear ruta ${doc.id}", e)
                    null
                }
            }
            Result.success(routes)
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar siguiente página", e)
            Result.failure(e)
        }
    }

    // Ruta completa con points (solo para el detalle)
    suspend fun getRouteWithPoints(routeId: String): Result<Route> {
        return try {
            val doc = firestore.collection(ROUTES_COLLECTION)
                .document(routeId)
                .get()
                .await()
            val data = doc.data
                ?: return Result.failure(Exception("Ruta no encontrada"))
            Result.success(Route.fromMap(doc.id, data))
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener ruta con points $routeId", e)
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
            Log.e(TAG, "Error al calcular kilometraje del vehículo", e)
            Result.failure(e)
        }
    }
    /**
     * Aplica la lógica de decisión climática y badges a una ruta base.
     * Combina los datos iniciales con los máximos detectados durante el viaje.
     */
    fun finalizeRouteWithWeather(
        baseRoute: Route,
        snap: RouteWeatherSnapshot,
        decision: RouteWeatherDecision = RouteWeatherDecision()
    ): Route {
        val adjustedSnap = when (decision.surfaceConditionType) {
            SurfaceConditionType.RAIN -> {
                if (decision.isSurfaceConditionConfirmed) {
                    snap.copy(
                        hadRain = true,
                        hadWetRoad = false
                    )
                } else {
                    snap.copy(
                        hadRain = false,
                        hadWetRoad = false
                    )
                }
            }
            SurfaceConditionType.WET_ROAD -> {
                if (decision.isSurfaceConditionConfirmed) {
                    snap.copy(
                        hadRain = false,
                        hadWetRoad = true
                    )
                } else {
                    snap.copy(
                        hadRain = false,
                        hadWetRoad = false
                    )
                }
            }
            SurfaceConditionType.NONE -> snap
        }

        // Fuente de verdad: hay datos finales solo si se fetchearon (badges + fetch exitoso)
        val hasFinalData = adjustedSnap.finalTemp != null

        // Helper: elige final si existe, si no inicial
        fun <T> pick(final: T?, initial: T?): T? =
            if (hasFinalData) final ?: initial else initial

        // Viento: siempre el máximo entre inicial, final y máximo registrado
        val windSpeed = maxOf(
            (adjustedSnap.initialWindSpeed ?: 0.0),
            (adjustedSnap.finalWindSpeed   ?: 0.0),
            adjustedSnap.maxWindSpeed
        )
        val windGusts = maxOf(
            (adjustedSnap.initialWindGusts ?: 0.0),
            (adjustedSnap.finalWindGusts   ?: 0.0),
            adjustedSnap.maxWindGusts
        )

        // UV: siempre el máximo entre inicial, final y máximo registrado
        val uvIndex = maxOf(
            adjustedSnap.initialUvIndex ?: 0.0,
            adjustedSnap.finalUvIndex   ?: 0.0,
            adjustedSnap.maxUvIndex
        )

        return baseRoute.copy(
            // --- VISUALES: final si hubo badges, inicial si ruta tranquila ---
            weatherTemperature   = pick(adjustedSnap.finalTemp, adjustedSnap.initialTemp),
            weatherEmoji         = pick(adjustedSnap.finalEmoji, adjustedSnap.initialEmoji),
            weatherDescription   = pick(adjustedSnap.finalDescription, adjustedSnap.initialDescription),
            weatherCondition     = pick(adjustedSnap.finalCondition, adjustedSnap.initialCondition),
            weatherCode          = pick(adjustedSnap.finalCode, adjustedSnap.initialCode),
            weatherIsDay         = pick(adjustedSnap.finalIsDay, adjustedSnap.initialIsDay) ?: true,

            // --- CONFORT: siempre del inicio (cómo salió el usuario) ---
            weatherFeelsLike     = adjustedSnap.initialFeelsLike,
            weatherWindChill     = adjustedSnap.initialWindChill,
            weatherHeatIndex     = adjustedSnap.initialHeatIndex,
            weatherDewPoint      = adjustedSnap.initialDewPoint,

            // --- TÉCNICOS: máximo registrado (viento) o pick (resto) ---
            weatherWindSpeed     = windSpeed,
            weatherWindGusts     = windGusts,
            weatherWindDirection = pick(adjustedSnap.finalWindDirection, adjustedSnap.initialWindDirection),
            weatherHumidity      = pick(adjustedSnap.finalHumidity, adjustedSnap.initialHumidity)?.toInt(),
            weatherVisibility    = pick(adjustedSnap.finalVisibility, adjustedSnap.initialVisibility),
            weatherRainProbability = pick(adjustedSnap.finalRainProbability, adjustedSnap.initialRainProbability)?.toInt(),
            weatherUvIndex       = uvIndex,

            // --- BADGES Y EVENTOS ---
            weatherHadRain              = adjustedSnap.hadRain,
            weatherHadWetRoad           = !adjustedSnap.hadRain && adjustedSnap.hadWetRoad,
            weatherHadExtremeConditions = adjustedSnap.hadExtreme,
            weatherExtremeReason        = adjustedSnap.extremeReason.takeIf { adjustedSnap.hadExtreme },
            weatherRainReason           = adjustedSnap.rainReason,
            weatherRainStartMinute      = adjustedSnap.rainStartMinute,
            weatherMaxPrecipitation     = adjustedSnap.maxPrecipitation
                .takeIf { adjustedSnap.hadRain && it > 0.0 }
        )
    }
}

