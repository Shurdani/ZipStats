package com.zipstats.app.utils

import com.zipstats.app.model.Record
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date

object DateUtils {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")
    private val apiDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    /** ISO local; orden lexicográfico = orden cronológico. */
    private val apiDateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")

    private val fullDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun toLocalDate(date: Date): LocalDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    
    fun toDate(localDate: LocalDate): Date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())

    // Funciones para el formato de la API
    fun formatForApi(date: LocalDate): String = date.format(apiDateFormatter)

    fun formatForApiDateTime(dateTime: LocalDateTime): String =
        dateTime.format(apiDateTimeFormatter)

    fun formatForApiFromMillis(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDateTime()
            .truncatedTo(ChronoUnit.SECONDS)
            .format(apiDateTimeFormatter)

    /** Nuevo registro manual: día elegido + hora actual (varios el mismo día se ordenan bien). */
    fun formatForApiOnDayWithCurrentTime(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): String =
        LocalDateTime.of(date, LocalTime.now(zoneId).truncatedTo(ChronoUnit.SECONDS))
            .format(apiDateTimeFormatter)

    /** Edición: cambia el día y conserva la hora del valor guardado. */
    fun mergeApiDateWithRecordTime(selectedDate: LocalDate, storedFecha: String): String {
        val original = parseApiDateTimeBestEffort(storedFecha)
        return formatForApiDateTime(LocalDateTime.of(selectedDate, original.toLocalTime()))
    }

    // 1. Para mostrar en el TextField del DatePicker
    fun formatFullDisplayDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "-"
        return try {
            val date = parseApiDate(dateStr)
            date.format(fullDateFormatter)
        } catch (e: Exception) {
            dateStr ?: "-"
        }
    }

    fun formatFullDisplayDate(date: LocalDate): String = date.format(fullDateFormatter)
    // 2. Para convertir lo que viene del DatePicker (si viene en String)
    fun parseFullDisplayDate(dateStr: String): LocalDate {
        return try {
            // Intento 1: Formato largo (dd/MM/yyyy) -> El que quieres usar ahora
            LocalDate.parse(dateStr, fullDateFormatter)
        } catch (e: Exception) {
            // Intento 2: Formato corto (dd/MM/yy) -> Por si viene de un rastro antiguo o error de UI
            try {
                LocalDate.parse(dateStr, dateFormatter)
            } catch (e2: Exception) {
                // Si ambos fallan, devolvemos hoy para que la app no explote,
                // o relanzamos una excepción controlada
                LocalDate.now()
            }
        }
    }
    /**
     * Solo la parte de calendario: admite `yyyy-MM-dd` o `yyyy-MM-dd'T'HH:mm:ss`.
     */
    fun parseApiDate(dateStr: String): LocalDate {
        val t = dateStr.trim()
        if (t.isEmpty()) throw IllegalArgumentException("fecha vacía")
        val datePart = if (t.length >= 10) t.take(10) else t
        return LocalDate.parse(datePart, apiDateFormatter)
    }

    fun parseApiDateTimeBestEffort(fecha: String): LocalDateTime {
        val t = fecha.trim()
        if (t.isEmpty()) return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        return runCatching {
            when {
                'T' in t -> LocalDateTime.parse(t, apiDateTimeFormatter)
                t.length >= 10 -> LocalDate.parse(t.take(10), apiDateFormatter).atStartOfDay()
                else -> LocalDate.parse(t, apiDateFormatter).atStartOfDay()
            }
        }.getOrElse { LocalDate.now().atStartOfDay() }
    }

    /**
     * Clave para ordenar `fecha` de registros: legacy solo día se trata como 00:00:00.
     */
    fun recordFechaSortKey(fecha: String): String {
        val t = fecha.trim()
        return when {
            t.isEmpty() -> ""
            'T' in t -> t
            t.length >= 10 -> "${t.take(10)}T00:00:00"
            else -> t
        }
    }

    fun recordComparatorNewestFirst(): Comparator<Record> =
        compareByDescending<Record> { recordFechaSortKey(it.fecha) }
            .thenByDescending { it.id }

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

    fun formatYear(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "-"
        return try {
            val date = parseApiDate(dateStr)
            date.year.toString()
        } catch (e: Exception) {
            "-"
        }
    }
    
    fun parseDisplayDate(dateStr: String): LocalDate = LocalDate.parse(dateStr, dateFormatter)
} 