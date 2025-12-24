package com.zipstats.app.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

object DateUtils {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")
    private val apiDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    fun toLocalDate(date: Date): LocalDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    
    fun toDate(localDate: LocalDate): Date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())

    // Funciones para el formato de la API
    fun formatForApi(date: LocalDate): String = date.format(apiDateFormatter)
    
    fun parseApiDate(dateStr: String): LocalDate = LocalDate.parse(dateStr, apiDateFormatter)

    // Funciones para el formato de visualización
    fun formatForDisplay(date: LocalDate): String = date.format(dateFormatter)
    
    fun formatForDisplayWithTime(timestampMs: Long): String {
        val dateTime = java.time.Instant.ofEpochMilli(timestampMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        return dateTime.format(dateTimeFormatter)
    }
    
    /**
     * Formatea la fecha de forma más humana: "Hoy, 09:32", "Ayer, 15:41" o "23/12/25 09:32"
     */
    fun formatHumanDateWithTime(timestampMs: Long): String {
        val dateTime = java.time.Instant.ofEpochMilli(timestampMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        val today = LocalDate.now()
        val date = dateTime.toLocalDate()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val time = dateTime.format(timeFormatter)
        
        return when {
            date == today -> "Hoy, $time"
            date == today.minusDays(1) -> "Ayer, $time"
            else -> dateTime.format(dateTimeFormatter)
        }
    }
    
    fun parseDisplayDate(dateStr: String): LocalDate = LocalDate.parse(dateStr, dateFormatter)
} 