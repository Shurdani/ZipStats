package com.zipstats.app.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.zipstats.app.model.Record
import com.zipstats.app.utils.DateUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val recordsCollection = firestore.collection("registros")

    fun getRecords(): Flow<List<Record>> = callbackFlow {
        try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            
            val subscription = recordsCollection
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    // Verificar primero si el usuario sigue autenticado
                    val currentUser = auth.currentUser
                    if (currentUser == null) {
                        try {
                            trySend(emptyList())
                        } catch (_: Exception) {
                            // Ignorar errores al enviar datos si el canal está cerrado
                        }
                        try {
                            close()
                        } catch (_: Exception) {
                            // Ignorar errores si el canal ya está cerrado
                        }
                        return@addSnapshotListener
                    }
                    
                    if (error != null) {
                        // Manejar PERMISSION_DENIED silenciosamente (típico al cerrar sesión)
                        val isPermissionError = error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        
                        if (isPermissionError || auth.currentUser == null) {
                            android.util.Log.w("RecordRepository", "Permiso denegado o usuario no autenticado (probablemente durante logout). Cerrando listener silenciosamente.")
                            try {
                                trySend(emptyList())
                            } catch (_: Exception) {
                                // Ignorar errores al enviar datos si el canal está cerrado
                            }
                            try {
                                close()
                            } catch (_: Exception) {
                                // Ignorar errores si el canal ya está cerrado
                            }
                        } else {
                        close(error)
                        }
                        return@addSnapshotListener
                    }

                    val records = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            val data = doc.data
                            if (data != null) {
                                Record(
                                    id = doc.id,
                                    vehiculo = data["vehiculo"] as? String ?: "",
                                    patinete = data["patinete"] as? String ?: "",
                                    scooterId = data["scooterId"] as? String ?: "",
                                    fecha = data["fecha"] as? String ?: "",
                                    kilometraje = (data["kilometraje"] as? Number)?.toDouble() ?: 0.0,
                                    diferencia = (data["diferencia"] as? Number)?.toDouble() ?: 0.0,
                                    userId = data["userId"] as? String ?: "",
                                    isInitialRecord = data["isInitialRecord"] as? Boolean ?: false
                                )
                            } else null
                        } catch (e: Exception) {
                            println("Error al convertir documento: ${e.message}")
                            null
                        }
                    }?.sortedWith(DateUtils.recordComparatorNewestFirst()) ?: emptyList()

                    // Verificar nuevamente antes de enviar datos
                    if (auth.currentUser == null) {
                        try {
                            trySend(emptyList())
                            close()
                        } catch (_: Exception) {
                            // Ignorar errores si el canal está cerrado
                        }
                        return@addSnapshotListener
                    }
                    
                    try {
                    trySend(records)
                    } catch (e: Exception) {
                        // Ignorar errores si el canal está cerrado (puede ocurrir durante logout)
                        android.util.Log.w("RecordRepository", "Error al enviar registros (probablemente durante logout)", e)
                    }
                }

            awaitClose { subscription.remove() }
        } catch (e: Exception) {
            close(e)
        }
    }

    // Versión paginada para la UI (primera página con listener reactivo)
    fun getRecordsFlow(pageSize: Int = 20): Flow<List<Record>> = callbackFlow {
        val userId = auth.currentUser?.uid ?: run { close(Exception("Usuario no autenticado")); return@callbackFlow }

        val subscription = recordsCollection
            .whereEqualTo("userId", userId)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
            .addSnapshotListener { snapshot, error ->
                if (auth.currentUser == null) { trySend(emptyList()); close(); return@addSnapshotListener }
                if (error != null) {
                    val isPermissionError = error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    if (isPermissionError || auth.currentUser == null) {
                        trySend(emptyList()); close()
                    } else {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                val records = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        Record(
                            id = doc.id,
                            vehiculo = data["vehiculo"] as? String ?: "",
                            patinete = data["patinete"] as? String ?: "",
                            scooterId = data["scooterId"] as? String ?: "",
                            fecha = data["fecha"] as? String ?: "",
                            kilometraje = (data["kilometraje"] as? Number)?.toDouble() ?: 0.0,
                            diferencia = (data["diferencia"] as? Number)?.toDouble() ?: 0.0,
                            userId = data["userId"] as? String ?: "",
                            isInitialRecord = data["isInitialRecord"] as? Boolean ?: false
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                if (auth.currentUser != null) {
                    trySend(records.sortedWith(DateUtils.recordComparatorNewestFirst()))
                }
            }
        awaitClose { subscription.remove() }
    }

    // Carga de páginas adicionales (cursor por fecha)
    suspend fun getNextPage(lastFecha: String, pageSize: Int = 20): Result<List<Record>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))
            val snapshot = recordsCollection
                .whereEqualTo("userId", userId)
                .orderBy("fecha", Query.Direction.DESCENDING)
                .startAfter(lastFecha)
                .limit(pageSize.toLong())
                .get()
                .await()

            val records = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    Record(
                        id = doc.id,
                        vehiculo = data["vehiculo"] as? String ?: "",
                        patinete = data["patinete"] as? String ?: "",
                        scooterId = data["scooterId"] as? String ?: "",
                        fecha = data["fecha"] as? String ?: "",
                        kilometraje = (data["kilometraje"] as? Number)?.toDouble() ?: 0.0,
                        diferencia = (data["diferencia"] as? Number)?.toDouble() ?: 0.0,
                        userId = data["userId"] as? String ?: "",
                        isInitialRecord = data["isInitialRecord"] as? Boolean ?: false
                    )
                } catch (_: Exception) { null }
            }
            Result.success(records.sortedWith(DateUtils.recordComparatorNewestFirst()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Añade la distancia de una ruta al odómetro acumulado del vehículo (misma semántica que
     * introducir el odómetro total manualmente en Historial de Registros).
     */
    suspend fun addRecordFromRouteDistance(
        vehiculo: String,
        distanceKm: Double,
        fecha: String,
        scooterId: String? = null
    ): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))
        val vehicleRecords = fetchRecordsForVehicle(userId, vehiculo, scooterId)
        val latestOdometer = vehicleRecords
            .maxWithOrNull(DateUtils.recordComparatorNewestFirst())
            ?.kilometraje ?: 0.0
        return addRecord(vehiculo, latestOdometer + distanceKm, fecha, scooterId)
    }

    suspend fun addRecord(vehiculo: String, kilometraje: Double, fecha: String, scooterId: String? = null): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            val vehicleRecords = fetchRecordsForVehicle(userId, vehiculo, scooterId)

            // Registro cronológico inmediatamente anterior (misma fecha excluida: fecha < nueva)
            val previousRecord = vehicleRecords
                .filter { it.fecha < fecha }
                .maxWithOrNull(compareBy({ DateUtils.recordFechaSortKey(it.fecha) }, { it.id }))
            // Siguiente en el tiempo: debe recalcular su diferencia respecto al nuevo odómetro
            val nextRecord = vehicleRecords
                .filter { it.fecha > fecha }
                .minWithOrNull(compareBy({ DateUtils.recordFechaSortKey(it.fecha) }, { it.id }))

            val newDiferencia = if (previousRecord != null) {
                kilometraje - previousRecord.kilometraje
            } else {
                0.0
            }
            val newIsInitial = previousRecord == null

            val batch = firestore.batch()
            val newDocRef = recordsCollection.document()
            val newRecordData = hashMapOf(
                "fecha" to fecha,
                "kilometraje" to kilometraje,
                "diferencia" to newDiferencia,
                "vehiculo" to vehiculo,
                "patinete" to vehiculo,
                "scooterId" to (scooterId ?: ""),
                "userId" to userId,
                "isInitialRecord" to newIsInitial
            )
            batch.set(newDocRef, newRecordData)

            if (nextRecord != null) {
                val nextDiferencia = nextRecord.kilometraje - kilometraje
                val nextUpdates = hashMapOf<String, Any>(
                    "diferencia" to nextDiferencia
                )
                if (nextRecord.isInitialRecord) {
                    nextUpdates["isInitialRecord"] = false
                }
                batch.update(recordsCollection.document(nextRecord.id), nextUpdates)
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun documentToRecord(doc: DocumentSnapshot): Record? {
        val data = doc.data ?: return null
        return Record(
            id = doc.id,
            vehiculo = data["vehiculo"] as? String ?: "",
            patinete = data["patinete"] as? String ?: "",
            scooterId = data["scooterId"] as? String ?: "",
            fecha = data["fecha"] as? String ?: "",
            kilometraje = (data["kilometraje"] as? Number)?.toDouble() ?: 0.0,
            diferencia = (data["diferencia"] as? Number)?.toDouble() ?: 0.0,
            userId = data["userId"] as? String ?: "",
            isInitialRecord = data["isInitialRecord"] as? Boolean ?: false
        )
    }

    /** Incluye registros por scooterId y legacy sin id (mismo nombre de vehículo). */
    private suspend fun fetchRecordsForVehicle(
        userId: String,
        vehiculo: String,
        scooterId: String?
    ): List<Record> {
        return recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { documentToRecord(it) }
            .filter { record -> record.matchesVehicle(vehiculo, scooterId) }
    }

    private fun Record.matchesVehicle(vehiculo: String, scooterId: String?): Boolean {
        if (!scooterId.isNullOrEmpty()) {
            if (scooterId.isNotEmpty() && this.scooterId == scooterId) return true
            return this.scooterId.isEmpty() && this.vehicleName == vehiculo
        }
        return vehicleName == vehiculo
    }

    suspend fun deleteRecord(recordId: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            
            val recordDoc = recordsCollection.document(recordId).get().await()
            val record = recordDoc.toObject(Record::class.java)
            
            if (record?.userId != userId) {
                throw Exception("No tienes permiso para eliminar este registro")
            }
            
            // No permitir eliminar registros iniciales
            if (record.isInitialRecord) {
                throw Exception("No se puede eliminar el registro inicial de un vehículo")
            }
            
            val vehiculo = record.vehicleName.ifEmpty { record.patinete }

            recordsCollection.document(recordId).delete().await()
            repairOdometerChainForVehicleInternal(vehiculo)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRecord(record: Record): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            
            val existingRecord = recordsCollection.document(record.id).get().await()
            if (existingRecord.getString("userId") != userId) {
                throw Exception("No tienes permiso para actualizar este registro")
            }
            
            val recordData = hashMapOf(
                "fecha" to record.fecha,
                "kilometraje" to record.kilometraje,
                "diferencia" to record.diferencia,
                "vehiculo" to record.vehicleName,
                "patinete" to record.vehicleName, // Mantener compatibilidad
                "scooterId" to record.scooterId,
                "userId" to userId,
                "isInitialRecord" to record.isInitialRecord
            )
            
            recordsCollection.document(record.id).set(recordData).await()
            repairOdometerChainForVehicleInternal(record.vehicleName.ifEmpty { record.patinete })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun repairOdometerChainForVehicleInternal(vehiculo: String) {
        val vehicleRecords = getAllRecords()
            .filter { it.patinete == vehiculo || it.vehicleName == vehiculo }
            .sortedWith(compareBy({ DateUtils.recordFechaSortKey(it.fecha) }, { it.id }))

        if (vehicleRecords.isEmpty()) return

        val batch = firestore.batch()
        var hasWrites = false

        vehicleRecords.forEachIndexed { index, r ->
            val newDiferencia = if (index == 0) {
                0.0
            } else {
                r.kilometraje - vehicleRecords[index - 1].kilometraje
            }
            val newInitial = index == 0
            val difChanged = kotlin.math.abs(r.diferencia - newDiferencia) > 1e-6
            val initialChanged = r.isInitialRecord != newInitial
            if (difChanged || initialChanged) {
                val updates = hashMapOf<String, Any>(
                    "diferencia" to newDiferencia,
                    "isInitialRecord" to newInitial
                )
                batch.update(recordsCollection.document(r.id), updates)
                hasWrites = true
            }
        }

        if (hasWrites) {
            batch.commit().await()
        }
    }

    suspend fun getAllRecords(): List<Record> {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        
        return recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                try {
                    doc.toObject(Record::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    android.util.Log.e("RecordRepository", "Error de serializando documento ${doc.id}: ${e.message}", e)
                    null
                }
            }
    }

    suspend fun deleteAllUserRecords() {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        val records = recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents

        records.forEach { doc ->
            doc.reference.delete().await()
        }
    }

    suspend fun deleteScooterRecords(scooterId: String) {
        android.util.Log.d("RecordRepository", "=== INICIO deleteScooterRecords ===")
        android.util.Log.d("RecordRepository", "Nombre del vehículo: $scooterId")
        
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        
        // Buscar registros por el campo "patinete"
        android.util.Log.d("RecordRepository", "Buscando registros con patinete='$scooterId'...")
        val recordsByPatinete = recordsCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("patinete", scooterId)
            .get()
            .await()
            .documents
        
        android.util.Log.d("RecordRepository", "Registros encontrados por 'patinete': ${recordsByPatinete.size}")
        
        // También buscar por el campo "vehículo" por compatibilidad
        val recordsByVehiculo = recordsCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("vehiculo", scooterId)
            .get()
            .await()
            .documents
        
        android.util.Log.d("RecordRepository", "Registros encontrados por 'vehiculo': ${recordsByVehiculo.size}")
        
        // Combinar ambos resultados eliminando duplicados
        val allRecords = (recordsByPatinete + recordsByVehiculo).distinctBy { it.id }
        
        android.util.Log.d("RecordRepository", "Total de registros únicos a eliminar: ${allRecords.size}")
        
        // Eliminar todos los registros del patinete, incluyendo el inicial
        allRecords.forEach { doc ->
            try {
                android.util.Log.d("RecordRepository", "Eliminando registro: ${doc.id}")
                doc.reference.delete().await()
            } catch (e: Exception) {
                android.util.Log.e("RecordRepository", "Error eliminando registro ${doc.id}", e)
            }
        }
        
        android.util.Log.d("RecordRepository", "=== FIN deleteScooterRecords ===")
    }

    suspend fun getPreviousMileageForDate(patinete: String, fechaIso: String): Double? {
        return try {
            val userId = auth.currentUser?.uid ?: return null
            val repairDay = DateUtils.parseApiDate(fechaIso)
            val records = recordsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("patinete", patinete)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Record::class.java) }
                .filter { DateUtils.parseApiDate(it.fecha) <= repairDay }
                .maxWithOrNull(compareBy({ DateUtils.recordFechaSortKey(it.fecha) }, { it.id }))
            // Redondear a 1 decimal
            records?.kilometraje?.let { km ->
                (kotlin.math.round(km * 10.0) / 10.0)
            }
        } catch (_: Exception) {
            null
        }
    }

    // Función para obtener todas las estadísticas de una vez
    data class AchievementStats(
        val totalDistance: Double,
        val totalTrips: Int,
        val uniqueDays: Int,
        val uniqueScooters: Int,
        val longestTrip: Double,
        val consecutiveDays: Int,
        val uniqueWeeks: Int,
        val maintenanceCount: Int,
        val co2Saved: Double,
        val uniqueMonths: Int,
        val consecutiveMonths: Int
    )

    suspend fun getAchievementStats(): AchievementStats {
        val userId = auth.currentUser?.uid ?: return AchievementStats(0.0, 0, 0, 0, 0.0, 0, 0, 0, 0.0, 0, 0)
        
        val records = recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Record::class.java) }
        
        val totalDistance = records.sumOf { it.diferencia }
        val totalTrips = records.count { !it.isInitialRecord }
        
        val uniqueDays = records
            .filter { !it.isInitialRecord }
            .map { it.fecha.take(10) }
            .distinct()
            .size
        
        val uniqueScooters = records.map { it.patinete }.distinct().size
        
        val longestTrip = records
            .filter { !it.isInitialRecord }
            .maxOfOrNull { it.diferencia } ?: 0.0
        
        // Calcular días consecutivos
        val uniqueDates = records
            .filter { !it.isInitialRecord }
            .map { it.fecha.take(10) }
            .distinct()
            .sorted()
        
        var maxConsecutive = if (uniqueDates.isEmpty()) 0 else 1
        var currentConsecutive = 1
        
        for (i in 1 until uniqueDates.size) {
            val prevDate = java.time.LocalDate.parse(uniqueDates[i - 1])
            val currentDate = java.time.LocalDate.parse(uniqueDates[i])
            
            if (java.time.temporal.ChronoUnit.DAYS.between(prevDate, currentDate) == 1L) {
                currentConsecutive++
                maxConsecutive = maxOf(maxConsecutive, currentConsecutive)
            } else {
                currentConsecutive = 1
            }
        }
        
        // Calcular semanas únicas
        val uniqueWeeks = records
            .filter { !it.isInitialRecord }
            .map { record ->
                val date = java.time.LocalDate.parse(record.fecha.take(10))
                val weekFields = java.time.temporal.WeekFields.of(java.util.Locale.getDefault())
                val year = date.get(weekFields.weekBasedYear())
                val week = date.get(weekFields.weekOfWeekBasedYear())
                "$year-$week"
            }
            .distinct()
            .size
        
        // Calcular meses únicos
        val uniqueMonths = records
            .filter { !it.isInitialRecord }
            .map { record ->
                val date = java.time.LocalDate.parse(record.fecha.take(10))
                "${date.year}-${date.monthValue}"
            }
            .distinct()
            .size
        
        // Calcular mantenimientos
        val scooterIds = firestore.collection("patinetes")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .map { it.id } // Obtener el ID del documento del vehículo
        
        var maintenanceCount = 0
        for (scooterId in scooterIds) {
            val repairs = firestore.collection("repairs")
                .whereEqualTo("vehicleId", scooterId) // Buscar por vehicleId (ID del documento)
                .get()
                .await()
                .documents
            maintenanceCount += repairs.size
        }
        
        // Calcular CO2 ahorrado
        val co2Saved = totalDistance * 0.15
        
        // Calcular meses consecutivos
        val monthsList = records
            .filter { !it.isInitialRecord }
            .map { record ->
                val date = java.time.LocalDate.parse(record.fecha.take(10))
                date.year to date.monthValue
            }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second }))
        
        var maxConsecutiveMonths = if (monthsList.isEmpty()) 0 else 1
        var currentConsecutiveMonths = 1
        
        for (i in 1 until monthsList.size) {
            val (prevYear, prevMonth) = monthsList[i - 1]
            val (currYear, currMonth) = monthsList[i]
            
            val isConsecutive = if (prevMonth == 12) {
                currYear == prevYear + 1 && currMonth == 1
            } else {
                currYear == prevYear && currMonth == prevMonth + 1
            }
            
            if (isConsecutive) {
                currentConsecutiveMonths++
                maxConsecutiveMonths = maxOf(maxConsecutiveMonths, currentConsecutiveMonths)
            } else {
                currentConsecutiveMonths = 1
            }
        }
        
        return AchievementStats(
            totalDistance = totalDistance,
            totalTrips = totalTrips,
            uniqueDays = uniqueDays,
            uniqueScooters = uniqueScooters,
            longestTrip = longestTrip,
            consecutiveDays = maxConsecutive,
            uniqueWeeks = uniqueWeeks,
            maintenanceCount = maintenanceCount,
            co2Saved = co2Saved,
            uniqueMonths = uniqueMonths,
            consecutiveMonths = maxConsecutiveMonths
        )
    }

}