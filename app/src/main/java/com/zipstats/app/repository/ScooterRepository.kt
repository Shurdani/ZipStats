package com.zipstats.app.repository

import com.zipstats.app.model.Record
import com.zipstats.app.model.Vehicle
import com.zipstats.app.model.Scooter
import com.zipstats.app.model.VehicleType
import com.zipstats.app.utils.DateUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    // Colección de vehículos (VMP - Vehículos de Movilidad Personal)
    private val vehiclesCollection = firestore.collection("patinetes") // Nombre legacy en Firestore

    fun getVehicles(): Flow<List<Vehicle>> = callbackFlow {
        try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            
            val subscription = vehiclesCollection
                .whereEqualTo("userId", userId)
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
                        val isPermissionError = error is FirebaseFirestoreException &&
                                error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        
                        if (isPermissionError || auth.currentUser == null) {
                            android.util.Log.w("VehicleRepository", "Permiso denegado o usuario no autenticado (probablemente durante logout). Cerrando listener silenciosamente.")
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
                        close(error)
                        }
                        return@addSnapshotListener
                    }

                    val vehicles = snapshot?.documents?.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val vehicleTypeStr = data["vehicleType"] as? String
                        val vehicleType = vehicleTypeStr?.let { VehicleType.fromString(it) } ?: VehicleType.PATINETE
                        
                        Vehicle(
                            id = doc.id,
                            nombre = data["nombre"] as? String ?: "",
                            marca = data["marca"] as? String ?: "",
                            modelo = data["modelo"] as? String ?: "",
                            fechaCompra = data["fechaCompra"] as? String ?: "",
                            userId = data["userId"] as? String ?: "",
                            velocidadMaxima = (data["velocidadMaxima"] as? Number)?.toDouble() ?: 0.0,
                            vehicleType = vehicleType
                        )
                    } ?: emptyList()

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
                    trySend(vehicles)
                    } catch (e: Exception) {
                        // Ignorar errores si el canal está cerrado (puede ocurrir durante logout)
                        android.util.Log.w("VehicleRepository", "Error al enviar vehículos (probablemente durante logout)", e)
                    }
                }

            awaitClose { subscription.remove() }
        } catch (e: Exception) {
            println("Error al obtener vehículos: ${e.message}")
            trySend(emptyList())
            close(e)
        }
    }
    
    // Alias para mantener compatibilidad
    fun getScooters(): Flow<List<Scooter>> = getVehicles()

    fun getRecords(): Flow<List<Record>> = callbackFlow {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        
        val subscription = firestore.collection("registros")
            .whereEqualTo("userId", userId)
            .orderBy("date", Query.Direction.DESCENDING)
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
                    val isPermissionError = error is FirebaseFirestoreException &&
                            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    
                    if (isPermissionError || auth.currentUser == null) {
                        android.util.Log.w("VehicleRepository", "Permiso denegado en getRecords o usuario no autenticado (probablemente durante logout). Cerrando listener silenciosamente.")
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
                    close(error)
                    }
                    return@addSnapshotListener
                }

                val records = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Record::class.java)?.copy(id = doc.id)
                } ?: emptyList()

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
                trySend(records)
                } catch (e: Exception) {
                    // Ignorar errores si el canal está cerrado (puede ocurrir durante logout)
                    android.util.Log.w("VehicleRepository", "Error al enviar registros (probablemente durante logout)", e)
                }
            }

        awaitClose { subscription.remove() }
    }

    suspend fun addRecord(record: Record): String {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")
        val docRef = firestore.collection("registros").document()
        val recordWithId = record.copy(userId = userId, id = docRef.id)
        
        try {
            docRef.set(recordWithId).await()
            return docRef.id
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun deleteRecord(recordId: String) {
        auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")
        try {
            firestore.collection("registros")
                .document(recordId)
                .delete()
                .await()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun addVehicle(
        nombre: String, 
        marca: String, 
        modelo: String, 
        fechaCompra: String?,
        vehicleType: VehicleType = VehicleType.PATINETE
    ) {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        
        // Si no se especifica fecha de compra, usar la fecha actual
        val fechaFinal = fechaCompra ?: DateUtils.formatForApi(LocalDate.now())
        
        val vehicle = hashMapOf(
            "nombre" to nombre,
            "marca" to marca,
            "modelo" to modelo,
            "fechaCompra" to fechaFinal,
            "userId" to userId,
            "vehicleType" to vehicleType.name
        )

        // Crear el vehículo
        vehiclesCollection
            .add(vehicle)
            .await()

        // Crear el registro inicial a 0km
        val record = Record(
            vehiculo = nombre,
            patinete = nombre, // Compatibilidad con datos legacy
            fecha = fechaFinal,
            kilometraje = 0.0,
            diferencia = 0.0,
            userId = userId,
            isInitialRecord = true
        )

        // Guardar el registro inicial
        firestore.collection("registros")
            .add(record)
            .await()
    }
    
    // Alias para compatibilidad
    suspend fun addScooter(nombre: String, marca: String, modelo: String, fechaCompra: String?) {
        addVehicle(nombre, marca, modelo, fechaCompra, VehicleType.PATINETE)
    }

    suspend fun updateVehicle(vehicle: Vehicle): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            
            val existingVehicle = vehiclesCollection.document(vehicle.id).get().await()
            if (existingVehicle.getString("userId") != userId) {
                throw Exception("No tienes permiso para actualizar este vehículo")
            }
            
            val vehicleData = hashMapOf(
                "nombre" to vehicle.nombre,
                "marca" to vehicle.marca,
                "modelo" to vehicle.modelo,
                "fechaCompra" to vehicle.fechaCompra,
                "userId" to userId,
                "vehicleType" to vehicle.vehicleType.name
            )
            
            vehiclesCollection.document(vehicle.id).set(vehicleData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Alias para compatibilidad
    suspend fun updateScooter(scooter: Scooter): Result<Unit> = updateVehicle(scooter)

    suspend fun deleteVehicle(vehicleId: String) {
        android.util.Log.d("VehicleRepository", "=== INICIO deleteVehicle ===")
        android.util.Log.d("VehicleRepository", "VehicleId: $vehicleId")
        
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        android.util.Log.d("VehicleRepository", "UserId: $userId")
        
        // Verificar que el vehículo pertenece al usuario
        android.util.Log.d("VehicleRepository", "Obteniendo documento del vehículo...")
        val vehicleDoc = vehiclesCollection.document(vehicleId).get().await()
        
        if (!vehicleDoc.exists()) {
            android.util.Log.e("VehicleRepository", "El vehículo no existe en Firestore")
            throw Exception("El vehículo no existe")
        }
        
        val vehicleUserId = vehicleDoc.getString("userId")
        android.util.Log.d("VehicleRepository", "UserId del vehículo: $vehicleUserId")
        
        if (vehicleUserId != userId) {
            android.util.Log.e("VehicleRepository", "El vehículo no pertenece al usuario")
            throw Exception("No tienes permiso para eliminar este vehículo")
        }
        
        // Obtener el nombre del vehículo antes de eliminarlo
        val vehicleNombre = vehicleDoc.getString("nombre") ?: throw Exception("Error al obtener el nombre del vehículo")
        android.util.Log.d("VehicleRepository", "Nombre del vehículo: $vehicleNombre")
        
        // NOTA: Los registros ya han sido eliminados por recordRepository.deleteScooterRecords()
        // que se ejecuta antes en ProfileViewModel.deleteScooter()
        // No necesitamos eliminar los registros aquí para evitar duplicación
        
        // Luego eliminar el vehículo
        android.util.Log.d("VehicleRepository", "Eliminando vehículo de Firestore...")
        vehiclesCollection.document(vehicleId).delete().await()
        android.util.Log.d("VehicleRepository", "Vehículo eliminado correctamente")
        android.util.Log.d("VehicleRepository", "=== FIN deleteVehicle ===")
    }
    
    // Alias para compatibilidad
    suspend fun deleteScooter(scooterId: String) = deleteVehicle(scooterId)

    suspend fun getUserVehicles(): List<Vehicle> {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        return vehiclesCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val vehicleTypeStr = data["vehicleType"] as? String
                val vehicleType = vehicleTypeStr?.let { VehicleType.fromString(it) } ?: VehicleType.PATINETE
                
                Vehicle(
                    id = doc.id,
                    nombre = data["nombre"] as? String ?: "",
                    marca = data["marca"] as? String ?: "",
                    modelo = data["modelo"] as? String ?: "",
                    fechaCompra = data["fechaCompra"] as? String ?: "",
                    userId = data["userId"] as? String ?: "",
                    velocidadMaxima = (data["velocidadMaxima"] as? Number)?.toDouble() ?: 0.0,
                    vehicleType = vehicleType
                )
            }
    }
    
    // Alias para compatibilidad
    suspend fun getUserScooters(): List<Scooter> = getUserVehicles()

    suspend fun deleteAllUserVehicles() {
        val vehicles = getUserVehicles()
        vehicles.forEach { vehicle ->
            deleteVehicle(vehicle.id)
        }
    }
    
    // Alias para compatibilidad
    suspend fun deleteAllUserScooters() = deleteAllUserVehicles()
}

// Alias para mantener compatibilidad con código existente
typealias ScooterRepository = VehicleRepository 