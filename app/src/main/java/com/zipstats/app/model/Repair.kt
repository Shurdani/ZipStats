package com.zipstats.app.model

import java.time.LocalDate

data class Repair(
    val id: String = "",
    val vehicleId: String, // ID del vehículo al que pertenece
    val scooterId: String = "", // Mantenido para compatibilidad
    val patinete: String = "", // Nombre del vehículo (campo legacy)
    val date: LocalDate,
    val description: String,
    val mileage: Double? = null,
    val createdAt: LocalDate = LocalDate.now()
) {
    fun getFormattedDate(): String {
        return "${date.dayOfMonth}/${date.monthValue}/${date.year}"
    }
    
    // Helper para obtener el ID del vehículo
    val vehicleIdOrLegacy: String
        get() = vehicleId.ifEmpty { scooterId }
} 