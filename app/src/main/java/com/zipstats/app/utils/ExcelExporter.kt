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
import java.util.Date
import java.util.Locale

/**
 * Exportador mejorado de Excel para registros de vehículos
 * Maneja errores, optimiza memoria y proporciona mejor formato
 */
object ExcelExporter {
    
    private const val TAG = "ExcelExporter"
    
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
        val headers = arrayOf("Vehículo", "Fecha", "Kilometraje", "Diferencia", "Notas")
        
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
            vehicleCell.setCellValue(record.patinete)
            vehicleCell.cellStyle = styles.textStyle
            
            // Fecha
            val dateCell = row.createCell(1)
            dateCell.setCellValue(record.fecha)
            dateCell.cellStyle = styles.dateStyle
            
            // Kilometraje
            val kmCell = row.createCell(2)
            kmCell.setCellValue(record.kilometraje)
            kmCell.cellStyle = styles.numberStyle
            
            // Diferencia
            val diffCell = row.createCell(3)
            diffCell.setCellValue(record.diferencia)
            diffCell.cellStyle = styles.numberStyle
            
            // Notas (si las hay)
            val notesCell = row.createCell(4)
            notesCell.setCellValue("") // Record no tiene campo notas
            notesCell.cellStyle = styles.textStyle
        }
        
        // Ajustar anchos de columna
        sheet.setColumnWidth(0, 25 * 256) // Vehículo
        sheet.setColumnWidth(1, 15 * 256) // Fecha
        sheet.setColumnWidth(2, 15 * 256) // Kilometraje
        sheet.setColumnWidth(3, 15 * 256) // Diferencia
        sheet.setColumnWidth(4, 30 * 256) // Notas
        
        return sheet
    }
    
    /**
     * Crea la hoja de estadísticas por vehículo
     */
    private fun createStatsSheet(workbook: Workbook, records: List<Record>, sheetName: String): Sheet {
        val sheet = workbook.createSheet(sheetName)
        val styles = createStyles(workbook)
        
        // Agrupar registros por vehículo
        val recordsByVehicle = records.groupBy { it.patinete }
        
        // Encabezados
        val headerRow = sheet.createRow(0)
        val headers = arrayOf("Vehículo", "Total Registros", "Kilometraje Total", "Promedio por Día", "Última Fecha")
        
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
            val totalKm = vehicleRecords.sumOf { it.kilometraje.toDouble() }
            val avgPerDay = if (totalRecords > 0) totalKm / totalRecords else 0.0
            val lastDate = vehicleRecords.maxByOrNull { it.fecha }?.fecha ?: ""
            
            row.createCell(0).setCellValue(vehicle)
            row.createCell(1).setCellValue(totalRecords.toDouble())
            row.createCell(2).setCellValue(totalKm)
            row.createCell(3).setCellValue(avgPerDay)
            row.createCell(4).setCellValue(lastDate)
            
            // Aplicar estilos
            row.getCell(0).cellStyle = styles.textStyle
            row.getCell(1).cellStyle = styles.numberStyle
            row.getCell(2).cellStyle = styles.numberStyle
            row.getCell(3).cellStyle = styles.numberStyle
            row.getCell(4).cellStyle = styles.dateStyle
            
            rowIndex++
        }
        
        // Ajustar anchos
        sheet.setColumnWidth(0, 25 * 256)
        sheet.setColumnWidth(1, 15 * 256)
        sheet.setColumnWidth(2, 15 * 256)
        sheet.setColumnWidth(3, 15 * 256)
        sheet.setColumnWidth(4, 15 * 256)
        
        return sheet
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
        val totalKm = records.sumOf { it.kilometraje }
        val totalDiff = records.sumOf { it.diferencia }
        val uniqueVehicles = records.map { it.patinete }.distinct().size
        val dateRange = if (records.isNotEmpty()) {
            val dates = records.map { it.fecha }.sorted()
            "${dates.first()} - ${dates.last()}"
        } else "N/A"
        
        val stats = listOf(
            "Total de Registros" to totalRecords.toString(),
            "Total de Kilómetros" to String.format("%.2f", totalKm),
            "Total de Diferencia" to String.format("%.2f", totalDiff),
            "Vehículos Únicos" to uniqueVehicles.toString(),
            "Rango de Fechas" to dateRange,
            "Fecha de Exportación" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )
        
        stats.forEach { (label, value) ->
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue(label)
            row.createCell(1).setCellValue(value)
            row.getCell(0).cellStyle = styles.labelStyle
            row.getCell(1).cellStyle = styles.valueStyle
        }
        
        // Ajustar anchos
        sheet.setColumnWidth(0, 25 * 256)
        sheet.setColumnWidth(1, 20 * 256)
        
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
            dataFormat = workbook.createDataFormat().getFormat("yyyy-mm-dd")
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

