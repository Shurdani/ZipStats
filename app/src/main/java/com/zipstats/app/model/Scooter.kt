package com.zipstats.app.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// Representa un Vehículo de Movilidad Personal (VMP)
data class Vehicle(
    @DocumentId
    val id: String = "",
    val nombre: String = "",
    val marca: String = "",
    val modelo: String = "",
    val fechaCompra: String = "",
    val userId: String = "",
    val velocidadMaxima: Double = 0.0,
    val vehicleType: VehicleType = VehicleType.PATINETE, // Tipo de vehículo
    val kilometrajeActual: Double? = null, // Campo calculado, no se guarda en Firestore
    val matricula: String? = null
)

// Alias para mantener compatibilidad con código existente
typealias Scooter = Vehicle

data class VehicleRecord(
    @DocumentId
    val id: String = "",
    val vehicleId: String = "",
    val date: Timestamp = Timestamp.now(),
    val kilometers: Double = 0.0,
    val difference: Double = 0.0,
    val userId: String = ""
)

// Alias para mantener compatibilidad
typealias ScooterRecord = VehicleRecord 