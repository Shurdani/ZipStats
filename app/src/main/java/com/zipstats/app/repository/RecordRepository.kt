package com.zipstats.app.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.zipstats.app.model.Record
import com.zipstats.app.utils.DateUtils
import com.zipstats.app.utils.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val recordsCollection = firestore.collection("registros")

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        private const val ODOMETER_EPSILON = 0.05
    }

    @Volatile
    private var cachedFirstPageRecords: List<Record>? = null

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

    suspend fun preloadFirstPageRecords(pageSize: Int = DEFAULT_PAGE_SIZE): Result<List<Record>> =
        withContext(Dispatchers.IO) {
            fetchFirstPageRecords(pageSize).also { result ->
                result.onSuccess { cachedFirstPageRecords = it }
            }
        }

    fun peekFirstPageCache(): List<Record>? = cachedFirstPageRecords

    fun clearFirstPageCache() {
        cachedFirstPageRecords = null
    }

    private suspend fun fetchFirstPageRecords(pageSize: Int): Result<List<Record>> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.failure(Exception("Usuario no autenticado"))
            val snapshot = recordsCollection
                .whereEqualTo("userId", userId)
                .orderBy("fecha", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())
                .get()
                .await()
            val records = snapshot.documents
                .mapNotNull { documentToRecord(it) }
                .sortedWith(DateUtils.recordComparatorNewestFirst())
            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Versión paginada para la UI (primera página con listener reactivo)
    fun getRecordsFlow(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<List<Record>> = callbackFlow {
        val userId = auth.currentUser?.uid ?: run { close(Exception("Usuario no autenticado")); return@callbackFlow }

        peekFirstPageCache()?.let { cached ->
            trySend(cached)
        }

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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Usuario no autenticado"))
        val vehicleRecords = fetchRecordsForVehicle(userId, vehiculo, scooterId)
        val resolvedVehiculo = resolveVehicleName(vehiculo, vehicleRecords)
        val targetKilometraje = resolveTargetOdometerForRouteInsert(vehicleRecords, fecha, distanceKm)
        addRecordInternal(
            vehiculo = resolvedVehiculo,
            kilometraje = targetKilometraje,
            fecha = fecha,
            scooterId = scooterId
        )
    }

    suspend fun addRecord(vehiculo: String, kilometraje: Double, fecha: String, scooterId: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            addRecordInternal(vehiculo, kilometraje, fecha, scooterId)
        }

    private suspend fun addRecordInternal(
        vehiculo: String,
        kilometraje: Double,
        fecha: String,
        scooterId: String?
    ): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            val vehicleRecords = fetchRecordsForVehicle(userId, vehiculo, scooterId)
            val resolvedVehiculo = resolveVehicleName(vehiculo, vehicleRecords)
            val newFechaKey = DateUtils.recordFechaSortKey(fecha)

            validateOdometerReading(kilometraje, fecha, vehicleRecords)
                .getOrElse { return Result.failure(it) }

            // Registro cronológico inmediatamente anterior (fecha estrictamente menor)
            val previousRecord = vehicleRecords
                .filter { DateUtils.recordFechaSortKey(it.fecha) < newFechaKey }
                .maxWithOrNull(compareBy({ DateUtils.recordFechaSortKey(it.fecha) }, { it.id }))
            // Siguiente en el tiempo: debe recalcular su diferencia respecto al nuevo odómetro
            val nextRecord = vehicleRecords
                .filter { DateUtils.recordFechaSortKey(it.fecha) > newFechaKey }
                .minWithOrNull(compareBy({ DateUtils.recordFechaSortKey(it.fecha) }, { it.id }))

            val newDiferencia = when {
                previousRecord != null -> kilometraje - previousRecord.kilometraje
                nextRecord != null -> {
                    val odometerBeforeNext = nextRecord.kilometraje - nextRecord.diferencia
                    kilometraje - odometerBeforeNext
                }
                else -> 0.0
            }
            val newIsInitial = previousRecord == null && nextRecord == null

            val batch = firestore.batch()
            val newDocRef = recordsCollection.document()
            val newRecordData = hashMapOf(
                "fecha" to fecha,
                "kilometraje" to kilometraje,
                "diferencia" to newDiferencia,
                "vehiculo" to resolvedVehiculo,
                "patinete" to resolvedVehiculo,
                "scooterId" to (scooterId ?: ""),
                "userId" to userId,
                "isInitialRecord" to newIsInitial
            )
            batch.set(newDocRef, newRecordData)

            batch.commit().await()
            repairOdometerChainForVehicleInternal(resolvedVehiculo, scooterId)
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

    /**
     * Registros del vehículo: unión por id, nombre y reglas legacy.
     * Los registros manuales suelen tener [Record.scooterId] vacío y solo [Record.patinete].
     */
    private suspend fun fetchRecordsForVehicle(
        userId: String,
        vehiculo: String,
        scooterId: String?
    ): List<Record> {
        val allRecords = loadAllUserRecordsFromFirestore(userId)
        val byMatch = allRecords.filter { it.matchesVehicle(vehiculo, scooterId) }
        val byId = if (!scooterId.isNullOrEmpty()) {
            allRecords.filter { it.scooterId == scooterId }
        } else {
            emptyList()
        }
        val byName = if (vehiculo.isNotEmpty()) {
            allRecords.filter { it.patinete == vehiculo || it.vehiculo == vehiculo }
        } else {
            emptyList()
        }
        return (byMatch + byId + byName).distinctBy { it.id }
    }

    private suspend fun loadAllUserRecordsFromFirestore(userId: String): List<Record> =
        withContext(Dispatchers.IO) {
            recordsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .mapNotNull { documentToRecord(it) }
        }

    /** Coincide por id permanente o por nombre (registros sin scooterId). */
    private fun Record.matchesVehicle(vehiculo: String, scooterId: String?): Boolean {
        if (!scooterId.isNullOrEmpty() && scooterId.isNotEmpty() && this.scooterId == scooterId) {
            return true
        }
        if (vehiculo.isNotEmpty()) {
            return patinete == vehiculo || this.vehiculo == vehiculo
        }
        return false
    }

    /**
     * Odómetro total tras sumar [distanceKm] de una ruta, respetando la fecha de la ruta
     * aunque ya existan registros posteriores (p. ej. tarde añadida antes que mañana).
     */
    private fun resolveTargetOdometerForRouteInsert(
        vehicleRecords: List<Record>,
        fecha: String,
        distanceKm: Double
    ): Double {
        if (vehicleRecords.isEmpty()) return distanceKm
        val newFechaKey = DateUtils.recordFechaSortKey(fecha)
        val previousRecord = vehicleRecords
            .filter { DateUtils.recordFechaSortKey(it.fecha) < newFechaKey }
            .maxWithOrNull(compareBy({ DateUtils.recordFechaSortKey(it.fecha) }, { it.id }))
        if (previousRecord != null) {
            return previousRecord.kilometraje + distanceKm
        }
        val nextRecord = vehicleRecords
            .filter { DateUtils.recordFechaSortKey(it.fecha) > newFechaKey }
            .minWithOrNull(compareBy({ DateUtils.recordFechaSortKey(it.fecha) }, { it.id }))
        if (nextRecord != null) {
            val odometerBeforeNext = nextRecord.kilometraje - nextRecord.diferencia
            return odometerBeforeNext + distanceKm
        }
        return distanceKm
    }

    private fun resolveVehicleName(vehiculo: String, vehicleRecords: List<Record>): String {
        if (vehiculo.isNotEmpty()) return vehiculo
        return vehicleRecords
            .maxWithOrNull(DateUtils.recordComparatorNewestFirst())
            ?.vehicleName
            ?.takeIf { it.isNotEmpty() }
            ?: vehiculo
    }

    /**
     * Asigna [scooterId] a registros que solo tienen nombre (p. ej. "Mi Burra" con id vacío).
     * Misma intención que al crear vehículo o editar en perfil; recupera el formato legacy con id.
     *
     * @param vehiclesByName mapa nombre del vehículo → id en Firestore
     */
    suspend fun backfillMissingScooterIds(vehiclesByName: Map<String, String>): Result<Int> {
        if (vehiclesByName.isEmpty()) return Result.success(0)
        return withContext(Dispatchers.IO) {
            runBackfillMissingScooterIds(vehiclesByName)
        }
    }

    private suspend fun runBackfillMissingScooterIds(vehiclesByName: Map<String, String>): Result<Int> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))
            val allRecords = loadAllUserRecordsFromFirestore(userId)
            var batch = firestore.batch()
            var opsInBatch = 0
            var total = 0

            for ((nombre, scooterId) in vehiclesByName) {
                if (scooterId.isEmpty()) continue
                val toUpdate = allRecords.filter { record ->
                    record.scooterId.isEmpty() &&
                        (record.patinete == nombre || record.vehiculo == nombre)
                }
                for (record in toUpdate) {
                    batch.update(
                        recordsCollection.document(record.id),
                        mapOf("scooterId" to scooterId)
                    )
                    opsInBatch++
                    total++
                    if (opsInBatch >= 450) {
                        batch.commit().await()
                        batch = firestore.batch()
                        opsInBatch = 0
                    }
                }
            }
            if (opsInBatch > 0) {
                batch.commit().await()
            }
            Result.success(total)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
            val scooterId = record.scooterId.takeIf { it.isNotEmpty() }

            recordsCollection.document(recordId).delete().await()
            repairOdometerChainForVehicleInternal(vehiculo, scooterId)
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

            val vehiculo = record.vehicleName.ifEmpty { record.patinete }
            val scooterId = record.scooterId.takeIf { it.isNotEmpty() }
            val vehicleRecords = fetchRecordsForVehicle(userId, vehiculo, scooterId)
            validateOdometerReading(
                kilometraje = record.kilometraje,
                fecha = record.fecha,
                vehicleRecords = vehicleRecords,
                excludeRecordId = record.id
            ).getOrElse { return Result.failure(it) }
            
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
            repairOdometerChainForVehicleInternal(vehiculo, scooterId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Comprueba que el odómetro no baje respecto al registro cronológico anterior
     * ni al último del mismo día, y que no supere al siguiente si se inserta en medio.
     */
    private fun validateOdometerReading(
        kilometraje: Double,
        fecha: String,
        vehicleRecords: List<Record>,
        excludeRecordId: String? = null
    ): Result<Unit> {
        val context = odometerContextFor(fecha, vehicleRecords, excludeRecordId)
        context.previousRecord?.let { previous ->
            if (kilometraje < previous.kilometraje - ODOMETER_EPSILON) {
                return Result.failure(
                    Exception(
                        "El kilometraje no puede ser inferior al registro anterior " +
                            "(${LocationUtils.formatNumberSpanish(previous.kilometraje, 1)} km)"
                    )
                )
            }
        }
        context.lastOnSameDay?.let { lastSameDay ->
            if (kilometraje < lastSameDay.kilometraje - ODOMETER_EPSILON) {
                return Result.failure(
                    Exception(
                        "El kilometraje no puede ser inferior al último registro de ese día " +
                            "(${LocationUtils.formatNumberSpanish(lastSameDay.kilometraje, 1)} km)"
                    )
                )
            }
        }
        context.nextRecord?.let { next ->
            if (kilometraje > next.kilometraje + ODOMETER_EPSILON) {
                return Result.failure(
                    Exception(
                        "El kilometraje no puede ser superior al siguiente registro " +
                            "(${LocationUtils.formatNumberSpanish(next.kilometraje, 1)} km)"
                    )
                )
            }
        }
        return Result.success(Unit)
    }

    private data class OdometerContext(
        val previousRecord: Record?,
        val nextRecord: Record?,
        val lastOnSameDay: Record?
    )

    private fun odometerContextFor(
        fecha: String,
        vehicleRecords: List<Record>,
        excludeRecordId: String? = null
    ): OdometerContext {
        val records = vehicleRecords.filter { excludeRecordId == null || it.id != excludeRecordId }
        val newFechaKey = DateUtils.recordFechaSortKey(fecha)
        val newDay = DateUtils.parseApiDate(fecha)
        val previousRecord = records
            .filter { DateUtils.recordFechaSortKey(it.fecha) < newFechaKey }
            .maxWithOrNull(compareBy({ DateUtils.recordFechaSortKey(it.fecha) }, { it.id }))
        val nextRecord = records
            .filter { DateUtils.recordFechaSortKey(it.fecha) > newFechaKey }
            .minWithOrNull(compareBy({ DateUtils.recordFechaSortKey(it.fecha) }, { it.id }))
        val lastOnSameDay = records
            .filter { DateUtils.parseApiDate(it.fecha) == newDay }
            .maxWithOrNull(compareBy({ DateUtils.recordFechaSortKey(it.fecha) }, { it.id }))
        return OdometerContext(previousRecord, nextRecord, lastOnSameDay)
    }

    /**
     * Reconstruye odómetro acumulado y km de viaje en orden cronológico.
     * Tras insertar un registro intermedio, los posteriores deben subir su total.
     */
    private suspend fun repairOdometerChainForVehicleInternal(
        vehiculo: String,
        scooterId: String? = null
    ) {
        val userId = auth.currentUser?.uid ?: return
        val sorted = fetchRecordsForVehicle(userId, vehiculo, scooterId)
            .sortedWith(compareBy({ DateUtils.recordFechaSortKey(it.fecha) }, { it.id }))

        if (sorted.isEmpty()) return

        val batch = firestore.batch()
        var hasWrites = false
        var runningKm = sorted.first().kilometraje

        sorted.forEachIndexed { index, record ->
            val (newKilometraje, newDiferencia, newIsInitial) = if (index == 0) {
                runningKm = record.kilometraje
                Triple(record.kilometraje, 0.0, true)
            } else {
                // Siempre derivar del odómetro guardado; no reutilizar diferencia antigua si contradice el total.
                val tripKm = (record.kilometraje - runningKm).coerceAtLeast(0.0)
                runningKm = record.kilometraje
                Triple(record.kilometraje, tripKm, false)
            }

            val kmChanged = kotlin.math.abs(record.kilometraje - newKilometraje) > 1e-6
            val difChanged = kotlin.math.abs(record.diferencia - newDiferencia) > 1e-6
            val initialChanged = record.isInitialRecord != newIsInitial
            if (kmChanged || difChanged || initialChanged) {
                val updates = hashMapOf<String, Any>(
                    "kilometraje" to newKilometraje,
                    "diferencia" to newDiferencia,
                    "isInitialRecord" to newIsInitial
                )
                batch.update(recordsCollection.document(record.id), updates)
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
            .map { DateUtils.parseApiDate(it.fecha) }
            .distinct()
            .size
        
        val uniqueScooters = records.map { it.patinete }.distinct().size
        
        val longestTrip = records
            .filter { !it.isInitialRecord }
            .maxOfOrNull { it.diferencia } ?: 0.0
        
        // Calcular días consecutivos
        val uniqueDates = records
            .filter { !it.isInitialRecord }
            .map { DateUtils.parseApiDate(it.fecha) }
            .distinct()
            .sorted()
        
        var maxConsecutive = if (uniqueDates.isEmpty()) 0 else 1
        var currentConsecutive = 1
        
        for (i in 1 until uniqueDates.size) {
            val prevDate = uniqueDates[i - 1]
            val currentDate = uniqueDates[i]
            
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
                val date = DateUtils.parseApiDate(record.fecha)
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
                val date = DateUtils.parseApiDate(record.fecha)
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
                val date = DateUtils.parseApiDate(record.fecha)
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