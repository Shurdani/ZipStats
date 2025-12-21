package com.zipstats.app.repository

import com.zipstats.app.model.Repair
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import com.google.firebase.firestore.Query

@Singleton
class RepairRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    
    fun getRepairsForVehicle(vehicleId: String): Flow<List<Repair>> = callbackFlow {
        val registration = firestore.collection("repairs")
            .whereEqualTo("vehicleId", vehicleId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    // Manejar PERMISSION_DENIED silenciosamente (típico al cerrar sesión)
                    val isPermissionError = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    
                    if (isPermissionError) {
                        android.util.Log.w("RepairRepository", "Permiso denegado (probablemente durante logout). Cerrando listener silenciosamente.")
                        try {
                            trySend(emptyList())
                        } catch (ex: Exception) {
                            // Ignorar errores al enviar datos si el canal está cerrado
                        }
                        try {
                            close()
                        } catch (ex: Exception) {
                            // Ignorar errores si el canal ya está cerrado
                        }
                    } else {
                    close(e)
                    }
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val repairs = snapshot.documents.mapNotNull { document ->
                        try {
                            val dateString = document.getString("date") ?: ""
                            val createdAtString = document.getString("createdAt") ?: ""
                            val mileageNumber = document.getDouble("mileage")
                            
                            val date = if (dateString.isNotEmpty()) {
                                LocalDate.parse(dateString)
                            } else {
                                LocalDate.now()
                            }
                            
                            val createdAt = if (createdAtString.isNotEmpty()) {
                                LocalDate.parse(createdAtString)
                            } else {
                                LocalDate.now()
                            }
                            
                            Repair(
                                id = document.id,
                                vehicleId = document.getString("vehicleId") ?: "",
                                scooterId = document.getString("scooterId") ?: document.getString("vehicleId") ?: "",
                                patinete = document.getString("patinete") ?: "",
                                date = date,
                                description = document.getString("description") ?: "",
                                mileage = mileageNumber,
                                createdAt = createdAt
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    // Ordenar en memoria por fecha descendente para evitar índice compuesto
                    val sorted = repairs.sortedByDescending { it.date }
                    try {
                        trySend(sorted)
                    } catch (ex: Exception) {
                        // Ignorar errores si el canal está cerrado (puede ocurrir durante logout)
                        android.util.Log.w("RepairRepository", "Error al enviar reparaciones (probablemente durante logout)", ex)
                    }
                }
            }
        awaitClose { registration.remove() }
    }
    
    // Alias para compatibilidad
    fun getRepairsForScooter(scooterId: String): Flow<List<Repair>> = getRepairsForVehicle(scooterId)
    
    suspend fun addRepair(repair: Repair): Result<String> {
        return try {
            val repairData = mutableMapOf<String, Any>(
                "vehicleId" to repair.vehicleIdOrLegacy,
                "scooterId" to repair.vehicleIdOrLegacy, // Compatibilidad
                "date" to repair.date.toString(),
                "description" to repair.description,
                "createdAt" to repair.createdAt.toString()
            )
            repair.mileage?.let { km ->
                repairData["mileage"] = km
            }
            
            val docRef = firestore.collection("repairs").add(repairData).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteRepair(repairId: String): Result<Unit> {
        return try {
            firestore.collection("repairs").document(repairId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateRepair(repair: Repair): Result<Unit> {
        return try {
            val repairData = mutableMapOf<String, Any>(
                "vehicleId" to repair.vehicleIdOrLegacy,
                "scooterId" to repair.vehicleIdOrLegacy, // Compatibilidad
                "date" to repair.date.toString(),
                "description" to repair.description,
                "createdAt" to repair.createdAt.toString()
            )
            repair.mileage?.let { km ->
                repairData["mileage"] = km
            }
            firestore.collection("repairs").document(repair.id).set(repairData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 