package com.zipstats.app.repository

import com.zipstats.app.model.Record
import com.zipstats.app.model.Vehicle
import com.zipstats.app.model.Scooter
import com.zipstats.app.model.VehicleType
import com.zipstats.app.utils.DateUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
                    if (error != null) {
                        close(error)
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

                    trySend(vehicles)
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
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val records = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Record::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(records)
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
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        
        // Verificar que el vehículo pertenece al usuario
        val vehicleDoc = vehiclesCollection.document(vehicleId).get().await()
        if (vehicleDoc.getString("userId") != userId) {
            throw Exception("No tienes permiso para eliminar este vehículo")
        }
        
        // Obtener el nombre del vehículo antes de eliminarlo
        val vehicleNombre = vehicleDoc.getString("nombre") ?: throw Exception("Error al obtener el nombre del vehículo")
        
        // Eliminar todos los registros asociados, incluyendo el inicial
        // Buscar por ambos campos para compatibilidad
        firestore.collection("registros")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .filter { doc ->
                val patinete = doc.getString("patinete") ?: ""
                val vehiculo = doc.getString("vehiculo") ?: ""
                patinete == vehicleNombre || vehiculo == vehicleNombre
            }
            .forEach { doc ->
                doc.reference.delete().await()
            }
        
        // Luego eliminar el vehículo
        vehiclesCollection.document(vehicleId).delete().await()
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