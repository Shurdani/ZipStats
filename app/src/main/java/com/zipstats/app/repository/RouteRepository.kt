package com.zipstats.app.repository

import android.util.Log
import com.zipstats.app.model.Route
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.util.LocationUtils
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
     * Crea una ruta a partir de una lista de puntos GPS
     */
    fun createRouteFromPoints(
        points: List<RoutePoint>,
        scooterId: String,
        scooterName: String,
        startTime: Long,
        endTime: Long,
        notes: String = "",
        timeInMotion: Long? = null
    ): Route {
        // Filtrar puntos para eliminar ruido GPS
        val filteredPoints = LocationUtils.filterPoints(points)
        
        // Calcular estadísticas
        val totalDistance = LocationUtils.calculateTotalDistance(filteredPoints)
        val totalDuration = endTime - startTime
        
        // Usar el nuevo cálculo de velocidad en movimiento si está disponible
        val averageSpeed = if (timeInMotion != null && timeInMotion > 0) {
            // Velocidad media en movimiento (excluye tiempo parado)
            val timeInMotionHours = timeInMotion / (1000.0 * 60.0 * 60.0)
            if (timeInMotionHours > 0) totalDistance / timeInMotionHours else 0.0
        } else {
            // Fallback al cálculo tradicional si no hay tiempo en movimiento
            LocationUtils.calculateAverageSpeed(totalDistance, totalDuration)
        }
        
        val maxSpeed = LocationUtils.calculateMaxSpeed(filteredPoints)
        
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
            points = filteredPoints,
            isCompleted = true,
            notes = notes
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

