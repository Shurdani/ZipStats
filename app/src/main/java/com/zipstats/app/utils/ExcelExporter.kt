package com.zipstats.app.utils

import android.content.Context
import android.util.Log
import com.zipstats.app.model.Record
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Exportador mejorado de Excel para registros de vehículos
 * Maneja errores, optimiza memoria y proporciona mejor formato
 */
object ExcelExporter {
    
    private const val TAG = "ExcelExporter"
    private val apiDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    /**
     * Exporta registros filtrados a Excel
     */
    fun exportRecords(
        context: Context,
        records: List<Record>,
        fileName: String = "registros_vehiculos_${System.currentTimeMillis()}.xlsx"
    ): Result<File> {
        return try {
            Log.d(TAG, "Iniciando exportación de ${records.size} registros")
            
            val tempFile = File(context.cacheDir, fileName)
            val workbook = XSSFWorkbook()
            
            try {
                val sheet = createRecordsSheet(workbook, records)
                
                // Escribir archivo
                FileOutputStream(tempFile).use { fileOut ->
                    workbook.write(fileOut)
                }
                
                if (tempFile.exists() && tempFile.length() > 0) {
                    Log.d(TAG, "Archivo Excel creado exitosamente: ${tempFile.absolutePath}")
                    Result.success(tempFile)
                } else {
                    Log.e(TAG, "El archivo Excel está vacío o no existe")
                    Result.failure(Exception("Error al crear el archivo Excel"))
                }
            } finally {
                workbook.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al exportar registros", e)
            Result.failure(e)
        }
    }
    
    /**
     * Exporta todos los registros con estadísticas adicionales
     */
    fun exportAllRecordsWithStats(
        context: Context,
        records: List<Record>,
        fileName: String = "todos_los_registros_${System.currentTimeMillis()}.xlsx"
    ): Result<File> {
        return try {
            Log.d(TAG, "Iniciando exportación completa de ${records.size} registros")
            
            val tempFile = File(context.cacheDir, fileName)
            val workbook = XSSFWorkbook()
            
            try {
                // Hoja 1: Registros detallados
                createRecordsSheet(workbook, records, "Registros Detallados")
                
                // Hoja 2: Estadísticas por vehículo
                createStatsSheet(workbook, records, "Estadísticas por Vehículo")
                
                // Hoja 3: Resumen general
                createSummarySheet(workbook, records, "Resumen General")
                
                // Escribir archivo
                FileOutputStream(tempFile).use { fileOut ->
                    workbook.write(fileOut)
                }
                
                if (tempFile.exists() && tempFile.length() > 0) {
                    Log.d(TAG, "Archivo Excel completo creado: ${tempFile.absolutePath}")
                    Result.success(tempFile)
                } else {
                    Log.e(TAG, "El archivo Excel está vacío")
                    Result.failure(Exception("Error al crear el archivo Excel"))
                }
            } finally {
                workbook.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al exportar todos los registros", e)
            Result.failure(e)
        }
    }
    
    /**
     * Crea la hoja principal de registros
     */
    private fun createRecordsSheet(
        workbook: Workbook,
        records: List<Record>,
        sheetName: String = "Registros"
    ): Sheet {
        val sheet = workbook.createSheet(sheetName)
        
        // Crear estilos
        val styles = createStyles(workbook)
        
        // Crear encabezados
        val headerRow = sheet.createRow(0)
        val headers = arrayOf(
            "Vehículo",
            "ID Vehículo",
            "Fecha",
            "Día",
            "Kilometraje",
            "Distancia Recorrida",
            "Tipo de Registro"
        )
        
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = styles.headerStyle
        }
        
        // Agregar datos
        records.sortedByDescending { it.fecha }.forEachIndexed { index, record ->
            val row = sheet.createRow(index + 1)
            
            // Vehículo
            val vehicleCell = row.createCell(0)
            vehicleCell.setCellValue(record.vehicleName)
            vehicleCell.cellStyle = styles.textStyle

            // ID vehículo
            val vehicleIdCell = row.createCell(1)
            vehicleIdCell.setCellValue(record.scooterId.ifBlank { "-" })
            vehicleIdCell.cellStyle = styles.textStyle
            
            // Fecha
            val dateCell = row.createCell(2)
            val localDate = parseApiDateToLocalDate(record.fecha)
            val excelDate = parseApiDateToDate(record.fecha)
            if (excelDate != null) {
                dateCell.setCellValue(excelDate)
                dateCell.cellStyle = styles.dateStyle
            } else {
                dateCell.setCellValue(record.fecha)
                dateCell.cellStyle = styles.textStyle
            }

            // Día de la semana
            val dayCell = row.createCell(3)
            dayCell.setCellValue(
                localDate?.dayOfWeek?.getDisplayName(
                    java.time.format.TextStyle.FULL,
                    Locale("es", "ES")
                ) ?: "-"
            )
            dayCell.cellStyle = styles.textStyle
            
            // Kilometraje
            val kmCell = row.createCell(4)
            kmCell.setCellValue(record.kilometraje)
            kmCell.cellStyle = styles.numberStyle
            
            // Distancia recorrida
            val diffCell = row.createCell(5)
            diffCell.setCellValue(record.diferencia)
            diffCell.cellStyle = styles.numberStyle
            
            // Tipo de registro
            val typeCell = row.createCell(6)
            typeCell.setCellValue(if (record.isInitialRecord) "Inicial" else "Normal")
            typeCell.cellStyle = styles.textStyle
        }
        
        // Ajustar anchos de columna
        sheet.setColumnWidth(0, 25 * 256) // Vehículo
        sheet.setColumnWidth(1, 18 * 256) // ID Vehículo
        sheet.setColumnWidth(2, 15 * 256) // Fecha
        sheet.setColumnWidth(3, 16 * 256) // Día
        sheet.setColumnWidth(4, 15 * 256) // Kilometraje
        sheet.setColumnWidth(5, 20 * 256) // Distancia recorrida
        sheet.setColumnWidth(6, 16 * 256) // Tipo

        sheet.createFreezePane(0, 1)
        sheet.setAutoFilter(org.apache.poi.ss.util.CellRangeAddress(0, records.size.coerceAtLeast(1), 0, headers.lastIndex))
        
        return sheet
    }

    /**
     * Crea la hoja de estadísticas por vehículo
     */
    private fun createStatsSheet(workbook: Workbook, records: List<Record>, sheetName: String): Sheet {
        val sheet = workbook.createSheet(sheetName)
        val styles = createStyles(workbook)

        // Agrupar registros por vehículo
        val recordsByVehicle = records.groupBy { it.vehicleName }

        // Encabezados CORREGIDOS
        val headerRow = sheet.createRow(0)
        val headers = arrayOf(
            "Vehículo",
            "Total Viajes",
            "Distancia Recorrida (Km)", // Antes decía Kilometraje Total y confundía
            "Promedio por Viaje",       // Antes Promedio por Día (pero divides por registros)
            "Días Activos",
            "Km/Día Activo",
            "Odómetro Actual",          // NUEVO: Para ver cuánto marca el patinete
            "Primera Fecha",
            "Última Fecha"
        )

        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = styles.headerStyle
        }

        // Datos por vehículo
        var rowIndex = 1
        recordsByVehicle.forEach { (vehicle, vehicleRecords) ->
            val row = sheet.createRow(rowIndex)

            val totalRecords = vehicleRecords.size

            // CORRECCIÓN 1: Sumamos la DIFERENCIA (lo que has andado), no el kilometraje absoluto
            val distanceTraveled = vehicleRecords.sumOf { it.diferencia }

            // CORRECCIÓN 2: Obtenemos el kilometraje MÁXIMO para saber el estado actual del patinete
            val currentOdometer = vehicleRecords.maxOfOrNull { it.kilometraje } ?: 0.0

            // Promedio real (Distancia total / número de viajes)
            val avgPerTrip = if (totalRecords > 0) distanceTraveled / totalRecords else 0.0

            val validDates = vehicleRecords.mapNotNull { parseApiDateToLocalDate(it.fecha) }
            val activeDays = validDates.distinct().size
            val avgPerActiveDay = if (activeDays > 0) distanceTraveled / activeDays else 0.0
            val firstDate = validDates.minOrNull()
            val lastDate = validDates.maxOrNull()

            // Llenamos las celdas
            row.createCell(0).setCellValue(vehicle)
            row.createCell(1).setCellValue(totalRecords.toDouble())
            row.createCell(2).setCellValue(distanceTraveled) // Ahora muestra la suma real de km recorridos
            row.createCell(3).setCellValue(avgPerTrip)
            row.createCell(4).setCellValue(activeDays.toDouble())
            row.createCell(5).setCellValue(avgPerActiveDay)
            row.createCell(6).setCellValue(currentOdometer) // El odómetro real

            val firstDateCell = row.createCell(7)
            val firstExcelDate = firstDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.let { Date.from(it) }
            if (firstExcelDate != null) {
                firstDateCell.setCellValue(firstExcelDate)
                firstDateCell.cellStyle = styles.dateStyle
            } else {
                firstDateCell.setCellValue("-")
                firstDateCell.cellStyle = styles.textStyle
            }

            val lastDateCell = row.createCell(8)
            val excelLastDate = lastDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.let { Date.from(it) }
            if (excelLastDate != null) {
                lastDateCell.setCellValue(excelLastDate)
                lastDateCell.cellStyle = styles.dateStyle
            } else {
                lastDateCell.setCellValue("-")
                lastDateCell.cellStyle = styles.textStyle
            }

            // Aplicar estilos
            row.getCell(0).cellStyle = styles.textStyle
            row.getCell(1).cellStyle = styles.numberStyle
            row.getCell(2).cellStyle = styles.numberStyle
            row.getCell(3).cellStyle = styles.numberStyle
            row.getCell(4).cellStyle = styles.numberStyle
            row.getCell(5).cellStyle = styles.numberStyle
            row.getCell(6).cellStyle = styles.numberStyle

            rowIndex++
        }

        // Ajustar anchos
        for (i in 0..8) {
            sheet.setColumnWidth(i, 20 * 256)
        }

        sheet.createFreezePane(0, 1)
        sheet.setAutoFilter(org.apache.poi.ss.util.CellRangeAddress(0, recordsByVehicle.size.coerceAtLeast(1), 0, headers.lastIndex))

        return sheet
    }

    private fun parseApiDateToDate(dateText: String): Date? {
        val localDate = parseApiDateToLocalDate(dateText) ?: return null
        return runCatching {
            localDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .let { Date.from(it) }
        }.getOrNull()
    }

    private fun parseApiDateToLocalDate(dateText: String): LocalDate? {
        return runCatching { LocalDate.parse(dateText, apiDateFormatter) }.getOrNull()
    }

    /**
     * Crea la hoja de resumen general
     */
    private fun createSummarySheet(workbook: Workbook, records: List<Record>, sheetName: String): Sheet {
        val sheet = workbook.createSheet(sheetName)
        val styles = createStyles(workbook)

        var rowIndex = 0

        // Título
        val titleRow = sheet.createRow(rowIndex++)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("RESUMEN GENERAL DE REGISTROS")
        titleCell.cellStyle = styles.titleStyle

        // Estadísticas generales
        val totalRecords = records.size

        // CORRECCIÓN: Ya no sumamos 'it.kilometraje'.
        // Calculamos la distancia real recorrida (suma de diferencias)
        val totalDistanciaRecorrida = records.sumOf { it.diferencia }

        // Opcional: El odómetro más alto registrado entre todos los patinetes (si tiene sentido)
        // O simplemente lo quitamos para no confundir.

        val uniqueVehicles = records.map { it.vehicleName }.distinct().size
        val activeDays = records.mapNotNull { parseApiDateToLocalDate(it.fecha) }.distinct().size
        val avgPerTrip = if (totalRecords > 0) totalDistanciaRecorrida / totalRecords else 0.0
        val avgPerActiveDay = if (activeDays > 0) totalDistanciaRecorrida / activeDays else 0.0
        val dateRange = if (records.isNotEmpty()) {
            val dates = records.map { it.fecha }.sorted()
            "${dates.first()} - ${dates.last()}"
        } else "N/A"

        val stats = listOf(
            "Total de Viajes Registrados" to totalRecords.toString(),
            // Aquí estaba el error. Cambiamos la etiqueta y el valor:
            "Distancia Total Recorrida (Km)" to String.format("%.2f", totalDistanciaRecorrida),
            "Promedio por Viaje (Km)" to String.format("%.2f", avgPerTrip),
            "Días con Actividad" to activeDays.toString(),
            "Promedio por Día Activo (Km)" to String.format("%.2f", avgPerActiveDay),
            "Vehículos Únicos" to uniqueVehicles.toString(),
            "Rango de Fechas" to dateRange,
            "Fecha de Exportación" to SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        )

        stats.forEach { (label, value) ->
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue(label)
            row.createCell(1).setCellValue(value)
            row.getCell(0).cellStyle = styles.labelStyle
            row.getCell(1).cellStyle = styles.valueStyle
        }

        // Ajustar anchos
        sheet.setColumnWidth(0, 30 * 256)
        sheet.setColumnWidth(1, 25 * 256)

        return sheet
    }
    
    /**
     * Crea los estilos para el Excel
     */
    private fun createStyles(workbook: Workbook): ExcelStyles {
        // Fuente para encabezados
        val headerFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 12
        }
        
        // Fuente para títulos
        val titleFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 14
        }
        
        // Fuente para etiquetas
        val labelFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 10
        }
        
        // Estilo de encabezado
        val headerStyle = workbook.createCellStyle().apply {
            setFont(headerFont)
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderTop = BorderStyle.THIN
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }
        
        // Estilo de título
        val titleStyle = workbook.createCellStyle().apply {
            setFont(titleFont)
            alignment = HorizontalAlignment.CENTER
        }
        
        // Estilo de etiqueta
        val labelStyle = workbook.createCellStyle().apply {
            setFont(labelFont)
            alignment = HorizontalAlignment.LEFT
        }
        
        // Estilo de valor
        val valueStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.LEFT
        }
        
        // Estilo de texto
        val textStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.LEFT
        }
        
        // Estilo de fecha
        val dateStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            dataFormat = workbook.createDataFormat().getFormat("dd/mm/yyyy")
        }
        
        // Estilo de número
        val numberStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.RIGHT
            dataFormat = workbook.createDataFormat().getFormat("#,##0.00")
        }
        
        return ExcelStyles(
            headerStyle = headerStyle,
            titleStyle = titleStyle,
            labelStyle = labelStyle,
            valueStyle = valueStyle,
            textStyle = textStyle,
            dateStyle = dateStyle,
            numberStyle = numberStyle
        )
    }
    
    /**
     * Clase para contener los estilos del Excel
     */
    private data class ExcelStyles(
        val headerStyle: CellStyle,
        val titleStyle: CellStyle,
        val labelStyle: CellStyle,
        val valueStyle: CellStyle,
        val textStyle: CellStyle,
        val dateStyle: CellStyle,
        val numberStyle: CellStyle
    )
}

