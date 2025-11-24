package com.zipstats.app.model

import java.time.LocalDate

data class MonthlyStatistics(
    val month: String, // Formato: "YYYY-MM"
    val year: Int,
    val monthNumber: Int,
    val totalDistance: Double,
    val averageDistance: Double,
    val maxDistance: Double,
    val totalRecords: Int,
    val createdAt: LocalDate = LocalDate.now()
) {
    fun getMonthDisplayName(): String {
        return when (monthNumber) {
            1 -> "Enero"
            2 -> "Febrero"
            3 -> "Marzo"
            4 -> "Abril"
            5 -> "Mayo"
            6 -> "Junio"
            7 -> "Julio"
            8 -> "Agosto"
            9 -> "Septiembre"
            10 -> "Octubre"
            11 -> "Noviembre"
            12 -> "Diciembre"
            else -> "Desconocido"
        }
    }
    
    fun getYearMonthDisplay(): String {
        return "${getMonthDisplayName()} $year"
    }
} 