package com.zipstats.app.ui.statistics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.appcompat.content.res.AppCompatResources
import com.zipstats.app.R
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PdfReportGenerator(
    private val context: Context
) {
    fun generate(
        outputFile: File,
        stats: StatisticsUiState.Success,
        weatherStats: WeatherStats,
        userName: String,
        selectedPeriod: Int,
        selectedMonth: Int?,
        selectedYear: Int?
    ) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 28f
        val contentWidth = pageWidth - (margin * 2)
        val periodName = buildPeriodName(selectedPeriod, selectedMonth, selectedYear)
        val generatedAt = LocalDateTime.now()
        val generatedAtText = generatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("es", "ES")))

        val metrics = resolvePeriodMetrics(stats, selectedPeriod)
        val ecoCo2 = metrics.totalDistance * 0.15
        val ecoTrees = metrics.totalDistance * 0.005
        val ecoGas = metrics.totalDistance * 0.07

        val page = newPage(document, pageWidth, pageHeight, 1)
        val canvas = page.canvas
        var y = margin

        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 23f
            isFakeBoldText = true
        }
        val headerSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5ECFF")
            textSize = 12f
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 14f
            isFakeBoldText = true
        }
        val metricLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7280")
            textSize = 10f
        }
        val metricValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 14f
            isFakeBoldText = true
        }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4B5563")
            textSize = 10f
        }

        val headerRect = RectF(margin, y, margin + contentWidth, y + 132f)
        drawRoundedRect(canvas, headerRect, Color.parseColor("#1D4ED8"), 20f)

        val logo = loadLogoBitmap(54)
        logo?.let {
            canvas.drawBitmap(it, margin + 18f, y + 18f, null)
        }
        canvas.drawText("Informe ZipStats de $userName", margin + 84f, y + 46f, headerTitlePaint)
        canvas.drawText(periodName, margin + 84f, y + 70f, headerSubtitlePaint)
        y += 148f

        y = drawSectionCard(
            canvas = canvas,
            left = margin,
            top = y,
            width = contentWidth,
            title = "Resumen de actividad",
            titlePaint = sectionTitlePaint,
            labelPaint = metricLabelPaint,
            valuePaint = metricValuePaint,
            cardBackground = Color.parseColor("#EFF6FF"),
            accentColor = Color.parseColor("#2563EB"),
            metrics = listOf(
                "Km total" to "${formatNumber(metrics.totalDistance)} km",
                "Km promedio" to "${formatNumber(metrics.averageDistance)} km",
                "Km récord" to "${formatNumber(metrics.maxDistance)} km",
                "Registros" to "${metrics.totalRecords}"
            )
        )

        y = drawSectionCard(
            canvas = canvas,
            left = margin,
            top = y + 12f,
            width = contentWidth,
            title = "Impacto ecológico",
            titlePaint = sectionTitlePaint,
            labelPaint = metricLabelPaint,
            valuePaint = metricValuePaint,
            cardBackground = Color.parseColor("#ECFDF3"),
            accentColor = Color.parseColor("#16A34A"),
            metrics = listOf(
                "CO₂ ahorrado" to "${formatNumber(ecoCo2)} kg",
                "Árboles equivalentes" to formatNumber(ecoTrees),
                "Gasolina no consumida" to "${formatNumber(ecoGas)} L"
            )
        )

        if (weatherStats.gpsTotalDistance > 0.0) {
            val feelsLabel = if (stats.extremeFeelsLikeIsHot) "Sensación máx" else "Sensación mín"
            val feelsEmoji = if (stats.extremeFeelsLikeIsHot) "🥵" else "🥶"
            y = drawSectionCard(
                canvas = canvas,
                left = margin,
                top = y + 12f,
                width = contentWidth,
                title = "Condiciones climatológicas",
                titlePaint = sectionTitlePaint,
                labelPaint = metricLabelPaint,
                valuePaint = metricValuePaint,
                cardBackground = Color.parseColor("#FFF7ED"),
                accentColor = Color.parseColor("#EA580C"),
                metrics = listOfNotNull(
                    "Km bajo lluvia" to "${formatNumber(weatherStats.rainKm)} km",
                    "Calzada mojada" to "${formatNumber(weatherStats.wetRoadKm)} km",
                    "Condición extrema" to "${formatNumber(weatherStats.extremeKm)} km",
                    stats.minTemperature?.let { "Temperatura mínima" to "${formatNumber(it)} °C" },
                    stats.maxTemperature?.let { "Temperatura máxima" to "${formatNumber(it)} °C" },
                    stats.maxWindGusts?.let { "Ráfaga máxima" to "${formatNumber(it)} km/h" },
                    stats.extremeFeelsLike?.let { "$feelsLabel" to "$feelsEmoji ${formatNumber(it)} °C" }
                )
            )

            if (weatherStats.coveragePercentage < 100.0) {
                val coverageText = "Datos climáticos basados en el ${formatNumber(weatherStats.coveragePercentage)}% de tus rutas grabadas con GPS."
                val coverageRect = RectF(margin, y + 2f, margin + contentWidth, y + 24f)
                drawRoundedRect(canvas, coverageRect, Color.parseColor("#FEE2E2"), 10f)
                val coveragePaint = Paint(notePaint).apply { color = Color.parseColor("#991B1B") }
                canvas.drawText(coverageText, margin + 10f, y + 17f, coveragePaint)
                y += 24f
            }
        }

        drawFooter(canvas, pageWidth, pageHeight, generatedAtText)
        document.finishPage(page)
        outputFile.outputStream().use { stream -> document.writeTo(stream) }
        document.close()
    }

    private fun buildPeriodName(selectedPeriod: Int, selectedMonth: Int?, selectedYear: Int?): String {
        val months = listOf(
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        )
        return when {
            selectedPeriod == 0 && selectedMonth != null -> "${months[selectedMonth - 1]} ${selectedYear ?: LocalDate.now().year}"
            selectedPeriod == 1 -> "${selectedYear ?: LocalDate.now().year}"
            else -> "Historial completo"
        }
    }

    private fun resolvePeriodMetrics(stats: StatisticsUiState.Success, selectedPeriod: Int): PeriodMetrics {
        return when (selectedPeriod) {
            0 -> PeriodMetrics(stats.monthlyDistance, stats.monthlyAverageDistance, stats.monthlyMaxDistance, stats.monthlyRecords)
            1 -> PeriodMetrics(stats.yearlyDistance, stats.yearlyAverageDistance, stats.yearlyMaxDistance, stats.yearlyRecords)
            else -> PeriodMetrics(stats.totalDistance, stats.averageDistance, stats.maxDistance, stats.totalRecords)
        }
    }

    private fun loadLogoBitmap(size: Int): Bitmap? {
        val drawable = AppCompatResources.getDrawable(context, R.drawable.logo_app) ?: return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun drawSectionCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        title: String,
        titlePaint: Paint,
        labelPaint: Paint,
        valuePaint: Paint,
        cardBackground: Int,
        accentColor: Int,
        metrics: List<Pair<String, String>>
    ): Float {
        val columns = 2
        val rowCount = (metrics.size + columns - 1) / columns
        val cardHeight = 42f + (rowCount * 44f)
        val cardRect = RectF(left, top, left + width, top + cardHeight)
        drawRoundedRect(canvas, cardRect, cardBackground, 16f)
        val accentBar = RectF(left, top, left + 6f, top + cardHeight)
        drawRoundedRect(canvas, accentBar, accentColor, 8f)

        canvas.drawText(title, left + 18f, top + 24f, titlePaint)
        val cellWidth = (width - 28f) / columns
        metrics.forEachIndexed { index, pair ->
            val row = index / columns
            val col = index % columns
            val x = left + 18f + (col * cellWidth)
            val y = top + 44f + (row * 44f)
            canvas.drawText(pair.first, x, y, labelPaint)
            canvas.drawText(pair.second, x, y + 18f, valuePaint)
        }
        return top + cardHeight
    }

    private fun drawRoundedRect(canvas: Canvas, rect: RectF, color: Int, radius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private fun newPage(document: PdfDocument, width: Int, height: Int, pageNumber: Int): PdfDocument.Page {
        return document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
    }

    private fun drawFooter(canvas: Canvas, pageWidth: Int, pageHeight: Int, generatedAtText: String) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 10f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Generado con ZipStats · $generatedAtText", pageWidth / 2f, pageHeight - 20f, footerPaint)
    }

    private fun formatNumber(value: Double): String {
        return String.format(Locale("es", "ES"), "%.1f", value)
    }

    private data class PeriodMetrics(
        val totalDistance: Double,
        val averageDistance: Double,
        val maxDistance: Double,
        val totalRecords: Int
    )
}

