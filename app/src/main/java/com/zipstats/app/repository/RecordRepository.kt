package com.zipstats.app.repository

import com.zipstats.app.model.Record
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val recordsCollection = firestore.collection("registros")
    private val usersCollection = firestore.collection("users")

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
                            android.util.Log.w("RecordRepository", "Permiso denegado o usuario no autenticado (probablemente durante logout). Cerrando listener silenciosamente.")
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
                        try {
                            val data = doc.data
                            if (data != null) {
                                Record(
                                    id = doc.id,
                                    vehiculo = data["vehiculo"] as? String ?: "",
                                    patinete = data["patinete"] as? String ?: "",
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
                    }?.sortedByDescending { it.fecha } ?: emptyList()

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
                        android.util.Log.w("RecordRepository", "Error al enviar registros (probablemente durante logout)", e)
                    }
                }

            awaitClose { subscription.remove() }
        } catch (e: Exception) {
            close(e)
        }
    }

    suspend fun getTotalDistance(): Double {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        
        val records = recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Record::class.java) }
        
        return records.sumOf<Record> { it.diferencia }
    }

    suspend fun addRecord(vehiculo: String, kilometraje: Double, fecha: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            
            // Obtener el último registro para este vehículo
            // Buscar por ambos campos para compatibilidad
            val lastRecord = recordsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    Record(
                        id = doc.id,
                        vehiculo = data["vehiculo"] as? String ?: "",
                        patinete = data["patinete"] as? String ?: "",
                        fecha = data["fecha"] as? String ?: "",
                        kilometraje = (data["kilometraje"] as? Number)?.toDouble() ?: 0.0,
                        diferencia = (data["diferencia"] as? Number)?.toDouble() ?: 0.0,
                        userId = data["userId"] as? String ?: "",
                        isInitialRecord = data["isInitialRecord"] as? Boolean ?: false
                    )
                }
                .filter { it.vehicleName == vehiculo }
                .maxByOrNull { it.fecha }

            // Calcular la diferencia
            val diferencia = lastRecord?.let { kilometraje - it.kilometraje } ?: 0.0

            // Crear el nuevo registro
            val record = Record(
                vehiculo = vehiculo,
                patinete = vehiculo, // Mantener compatibilidad
                fecha = fecha,
                kilometraje = kilometraje,
                diferencia = diferencia,
                userId = userId,
                isInitialRecord = lastRecord == null // Es registro inicial si no hay registros previos
            )

            recordsCollection.add(record).await()
            Result.success(Unit)
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
            
            recordsCollection.document(recordId).delete().await()
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
                "userId" to userId,
                "isInitialRecord" to record.isInitialRecord
            )
            
            recordsCollection.document(record.id).set(recordData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun calcularDiferencia(
        vehiculo: String,
        fecha: String,
        kilometraje: Double
    ): Double {
        val records = recordsCollection
            .whereEqualTo("userId", auth.currentUser?.uid)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                Record(
                    id = doc.id,
                    vehiculo = data["vehiculo"] as? String ?: "",
                    patinete = data["patinete"] as? String ?: "",
                    fecha = data["fecha"] as? String ?: "",
                    kilometraje = (data["kilometraje"] as? Number)?.toDouble() ?: 0.0,
                    diferencia = (data["diferencia"] as? Number)?.toDouble() ?: 0.0,
                    userId = data["userId"] as? String ?: "",
                    isInitialRecord = data["isInitialRecord"] as? Boolean ?: false
                )
            }
            .filter { it.vehicleName == vehiculo && it.fecha < fecha }
            .maxByOrNull { it.fecha }

        return if (records != null) {
            kilometraje - records.kilometraje
        } else {
            0.0
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
                    android.util.Log.e("RecordRepository", "Error deserializando documento ${doc.id}: ${e.message}", e)
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
        
        // También buscar por el campo "vehiculo" por compatibilidad
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

    fun getRecordsForScooter(patinete: String): Flow<List<Record>> = flow {
        try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            val records = recordsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("patinete", patinete)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Record::class.java) }
                .sortedByDescending { it.fecha }
            emit(records)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    suspend fun getPreviousMileageForDate(patinete: String, fechaIso: String): Double? {
        return try {
            val userId = auth.currentUser?.uid ?: return null
            val records = recordsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("patinete", patinete)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Record::class.java) }
                .filter { it.fecha <= fechaIso }
                .maxByOrNull { it.fecha }
            // Redondear a 1 decimal
            records?.kilometraje?.let { km ->
                (kotlin.math.round(km * 10.0) / 10.0)
            }
        } catch (e: Exception) {
            null
        }
    }

    // Funciones para estadísticas de logros
    suspend fun getTotalTrips(): Int {
        val userId = auth.currentUser?.uid ?: return 0
        val records = recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Record::class.java) }
        
        // Contar solo registros que no sean iniciales
        return records.count { !it.isInitialRecord }
    }

    suspend fun getUniqueDays(): Int {
        val userId = auth.currentUser?.uid ?: return 0
        val records = recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Record::class.java) }
        
        // Contar días únicos (sin contar registros iniciales)
        return records
            .filter { !it.isInitialRecord }
            .map { it.fecha.take(10) } // Obtener solo la fecha (YYYY-MM-DD)
            .distinct()
            .size
    }

    suspend fun getUniqueScooters(): Int {
        val userId = auth.currentUser?.uid ?: return 0
        val records = recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Record::class.java) }
        
        return records.map { it.patinete }.distinct().size
    }

    suspend fun getLongestTrip(): Double {
        val userId = auth.currentUser?.uid ?: return 0.0
        val records = recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Record::class.java) }
        
        return records
            .filter { !it.isInitialRecord }
            .maxOfOrNull { it.diferencia } ?: 0.0
    }

    suspend fun getConsecutiveDays(): Int {
        val userId = auth.currentUser?.uid ?: return 0
        val records = recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Record::class.java) }
        
        // Obtener fechas únicas (sin registros iniciales), ordenadas
        val uniqueDates = records
            .filter { !it.isInitialRecord }
            .map { it.fecha.take(10) } // Solo YYYY-MM-DD
            .distinct()
            .sorted()
        
        if (uniqueDates.isEmpty()) return 0
        
        // Calcular días consecutivos
        var maxConsecutive = 1
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
        
        return maxConsecutive
    }

    suspend fun getUniqueWeeks(): Int {
        val userId = auth.currentUser?.uid ?: return 0
        val records = recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Record::class.java) }
        
        // Obtener semanas únicas (año-semana)
        return records
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
    }

    suspend fun getMaintenanceCount(): Int {
        val userId = auth.currentUser?.uid ?: return 0
        
        // Obtener todos los IDs de vehículos del usuario
        val scooterIds = firestore.collection("patinetes")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .map { it.id } // Obtener el ID del documento del vehículo
        
        if (scooterIds.isEmpty()) return 0
        
        // Contar reparaciones de todos los vehículos del usuario
        var totalRepairs = 0
        for (scooterId in scooterIds) {
            val repairs = firestore.collection("repairs")
                .whereEqualTo("vehicleId", scooterId) // Buscar por vehicleId (ID del documento)
                .get()
                .await()
                .documents
            totalRepairs += repairs.size
        }
        
        return totalRepairs
    }

    suspend fun getCO2Saved(): Double {
        val totalDistance = getTotalDistance()
        // 0.1 kg de CO2 por km ahorrado (según el código existente)
        return totalDistance * 0.1
    }

    suspend fun getUniqueMonths(): Int {
        val userId = auth.currentUser?.uid ?: return 0
        val records = recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Record::class.java) }
        
        // Obtener meses únicos (año-mes)
        return records
            .filter { !it.isInitialRecord }
            .map { record ->
                val date = java.time.LocalDate.parse(record.fecha.take(10))
                "${date.year}-${date.monthValue}"
            }
            .distinct()
            .size
    }

    suspend fun getConsecutiveMonths(): Int {
        val userId = auth.currentUser?.uid ?: return 0
        val records = recordsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Record::class.java) }
        
        // Obtener meses únicos (año-mes), ordenados
        val uniqueMonths = records
            .filter { !it.isInitialRecord }
            .map { record ->
                val date = java.time.LocalDate.parse(record.fecha.take(10))
                date.year to date.monthValue // Tupla de (año, mes)
            }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second }))
        
        if (uniqueMonths.isEmpty()) return 0
        
        // Calcular meses consecutivos
        var maxConsecutive = 1
        var currentConsecutive = 1
        
        for (i in 1 until uniqueMonths.size) {
            val (prevYear, prevMonth) = uniqueMonths[i - 1]
            val (currYear, currMonth) = uniqueMonths[i]
            
            // Verificar si son meses consecutivos
            val isConsecutive = if (prevMonth == 12) {
                // Diciembre -> Enero del año siguiente
                currYear == prevYear + 1 && currMonth == 1
            } else {
                // Mismo año, mes siguiente
                currYear == prevYear && currMonth == prevMonth + 1
            }
            
            if (isConsecutive) {
                currentConsecutive++
                maxConsecutive = maxOf(maxConsecutive, currentConsecutive)
            } else {
                currentConsecutive = 1
            }
        }
        
        return maxConsecutive
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
        val co2Saved = totalDistance * 0.1
        
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

    private suspend fun updateUserStats(userId: String) {
        try {
            println("DEBUG: Actualizando estadísticas para usuario: $userId")
            
            // Obtener todos los registros del usuario
            val records = recordsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Record::class.java) }

            println("DEBUG: Registros encontrados: ${records.size}")

            // Calcular estadísticas
            val totalDistance = records.sumOf { it.diferencia }
            val totalRecords = records.size
            val co2Saved = totalDistance * 0.1 // 0.1 kg de CO2 por km ahorrado

            println("DEBUG: Estadísticas calculadas - Distancia: $totalDistance km, Registros: $totalRecords, CO2: $co2Saved kg")

            // Actualizar documento del usuario
            usersCollection.document(userId).update(
                mapOf(
                    "totalDistance" to totalDistance,
                    "totalRecords" to totalRecords,
                    "co2Saved" to co2Saved
                )
            ).await()
            
            println("DEBUG: Estadísticas actualizadas en Firestore")
        } catch (e: Exception) {
            println("ERROR: Error al actualizar estadísticas del usuario: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun initializeAllUsersStats() {
        try {
            println("DEBUG: Iniciando inicialización de estadísticas para todos los usuarios")
            
            // Obtener todos los usuarios
            val users = usersCollection.get().await().documents
            
            println("DEBUG: Usuarios encontrados: ${users.size}")
            
            // Para cada usuario, actualizar sus estadísticas
            users.forEach { userDoc ->
                val userId = userDoc.id
                println("DEBUG: Procesando usuario: $userId")
                updateUserStats(userId)
            }
            
            println("DEBUG: Inicialización de estadísticas completada")
        } catch (e: Exception) {
            println("ERROR: Error al inicializar estadísticas: ${e.message}")
            e.printStackTrace()
        }
    }
} 