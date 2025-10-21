package com.zipstats.app.model

import com.google.firebase.firestore.DocumentId

data class Record(
    @DocumentId
    val id: String = "",
    val vehiculo: String = "", // Nombre del vehículo (anteriormente "patinete")
    val patinete: String = "", // Mantenido para compatibilidad con datos existentes en Firestore
    val fecha: String = "",
    val kilometraje: Double = 0.0,
    val diferencia: Double = 0.0,
    val userId: String = "",
    val isInitialRecord: Boolean = false
) {
    // Helper para obtener el nombre del vehículo, priorizando el nuevo campo
    val vehicleName: String
        get() = vehiculo.ifEmpty { patinete }
} 