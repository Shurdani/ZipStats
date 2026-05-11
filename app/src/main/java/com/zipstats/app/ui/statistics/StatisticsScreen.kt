package com.zipstats.app.ui.statistics

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.R
import com.zipstats.app.ui.components.DialogConfirmButton
import com.zipstats.app.ui.components.DialogContentText
import com.zipstats.app.ui.components.DialogTitleText
import com.zipstats.app.ui.components.ZipStatsText
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.utils.ExportUiStrings
import com.zipstats.app.utils.LocationUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import java.io.File

// Extensión para redondear a 1 decimal (igual que en StatisticsViewModel)
private fun Double.roundToOneDecimal(): Double {
    return (this * 10.0).roundToInt() / 10.0
}

enum class StatisticsPeriod {
    MONTHLY, ALL, YEARLY
}

sealed class SelectionMode {
    object Month : SelectionMode()
    object Year : SelectionMode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    navController: NavController,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val statistics by viewModel.statistics.collectAsState()
    val availableMonthYears by viewModel.availableMonthYears.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPeriod by remember { mutableIntStateOf(0) }
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val weatherStats by viewModel.weatherStats.collectAsState()
    val reportUserName by viewModel.reportUserName.collectAsState()
    val scrollState = rememberScrollState()
    var showMonthYearPicker by remember { mutableStateOf(false) }
    var isGeneratingReport by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Diálogo de selección de mes/año
    if (showMonthYearPicker) {
        MonthYearPickerDialog(
            currentMonth = selectedMonth ?: LocalDate.now().monthValue,
            currentYear = selectedYear ?: LocalDate.now().year,
            availableMonthYears = availableMonthYears,
            onDismiss = { showMonthYearPicker = false },
            onConfirm = { month, year, isYearOnly ->
                if (isYearOnly) {
                    // Si solo se selecciona año, cambiar a pestaña "Este Año" (índice 1 ahora)
                    selectedPeriod = 1
                    viewModel.setSelectedPeriod(null, year)
                } else {
                    // Si se selecciona mes, cambiar a pestaña "Este Mes" (índice 0)
                    selectedPeriod = 0
                    viewModel.setSelectedPeriod(month, year)
                }
                showMonthYearPicker = false
            }
        )
    }

    LaunchedEffect(selectedPeriod, selectedMonth, selectedYear) {
        viewModel.loadStatistics(selectedPeriod)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    ZipStatsText(
                        text = "Estadísticas",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val isSmallScreen = screenWidthDp < 360
        val horizontalPadding = if (isSmallScreen) 8.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pestañas - REORDENADAS: MES -> AÑO -> TODO
            PrimaryTabRow(selectedTabIndex = selectedPeriod) {
                Tab(
                    selected = selectedPeriod == 0,
                    onClick = { 
                        selectedPeriod = 0
                        // Si hay filtro de solo año (sin mes), limpiarlo al cambiar a "Este Mes"
                        if (selectedYear != null && selectedMonth == null) {
                            viewModel.clearSelectedPeriod()
                        }
                    },
                    text = {
                        ZipStatsText(
                            "Este Mes",
                            fontWeight = if (selectedPeriod == 0) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                )
                Tab(
                    selected = selectedPeriod == 1,
                    onClick = { 
                        selectedPeriod = 1
                        // Si hay filtro de mes, limpiar el mes pero mantener el año si existe
                        if (selectedMonth != null) {
                            viewModel.setSelectedPeriod(null, selectedYear)
                        }
                    },
                    text = {
                        ZipStatsText(
                            "Este Año",
                            fontWeight = if (selectedPeriod == 1) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                )
                Tab(
                    selected = selectedPeriod == 2,
                    onClick = { 
                        selectedPeriod = 2
                        // Al cambiar a "Todo", limpiar todos los filtros
                        viewModel.clearSelectedPeriod()
                    },
                    text = {
                        ZipStatsText(
                            "Todo",
                            fontWeight = if (selectedPeriod == 2) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ZONA DE CHIPS DE FILTRO + INFORME
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = false,
                    onClick = {
                        if (isGeneratingReport) return@FilterChip
                        val stats = statistics as? StatisticsUiState.Success
                        if (stats == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Espera a que carguen las estadísticas")
                            }
                            return@FilterChip
                        }
                        scope.launch {
                            isGeneratingReport = true
                            val fileName =
                                buildStatisticsPdfFileName(selectedPeriod, selectedMonth, selectedYear)
                            val notificationId = fileName.hashCode()
                            showPdfProgressNotification(context, notificationId)
                            val saveResult = withContext(Dispatchers.IO) {
                                runCatching {
                                    val tempFile = File(context.cacheDir, fileName)
                                    val generator = PdfReportGenerator(context)
                                    generator.generate(
                                        outputFile = tempFile,
                                        stats = stats,
                                        weatherStats = weatherStats,
                                        userName = reportUserName,
                                        selectedPeriod = selectedPeriod,
                                        selectedMonth = selectedMonth,
                                        selectedYear = selectedYear
                                    )

                                    val savedUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        val values = ContentValues().apply {
                                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                                            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                                            put(MediaStore.Downloads.IS_PENDING, 1)
                                        }
                                        val resolver = context.contentResolver
                                        val uri = resolver.insert(
                                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                            values
                                        )
                                            ?: throw IllegalStateException("No se pudo crear el archivo en Descargas")

                                        resolver.openOutputStream(uri)?.use { output ->
                                            tempFile.inputStream().use { input -> input.copyTo(output) }
                                        }
                                        values.clear()
                                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                                        resolver.update(uri, values, null, null)
                                        uri
                                    } else {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (!hasPermission) {
                                            if (context is android.app.Activity) {
                                                ActivityCompat.requestPermissions(
                                                    context,
                                                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                                    2002
                                                )
                                            }
                                            throw SecurityException("Falta permiso de almacenamiento")
                                        }
                                        val downloadsDir =
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                        val destination = File(downloadsDir, fileName)
                                        tempFile.inputStream().use { input ->
                                            destination.outputStream().use { output -> input.copyTo(output) }
                                        }
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            destination
                                        )
                                    }
                                    tempFile.delete()
                                    fileName to savedUri
                                }
                            }

                            saveResult.onSuccess { (savedFileName, savedUri) ->
                                showPdfSavedNotification(
                                    context,
                                    savedUri,
                                    savedFileName,
                                    notificationId
                                )
                                snackbarHostState.showSnackbar(
                                    ExportUiStrings.savedToDownloadsRelative(savedFileName)
                                )
                            }.onFailure {
                                cancelPdfNotification(context, notificationId)
                                snackbarHostState.showSnackbar(ExportUiStrings.ERROR_PDF_SAVE)
                            }
                            isGeneratingReport = false
                        }
                    },
                    enabled = !isGeneratingReport,
                    label = { ZipStatsText("Generar informe") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = CircleShape
                )

                if (selectedPeriod != 2) {
                    // Texto del filtro actual
                    val monthNames = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
                    val filterLabel = if (selectedMonth != null && selectedYear != null) {
                        "${monthNames[selectedMonth!! - 1]} ${selectedYear}"
                    } else if (selectedYear != null) {
                        "${selectedYear}"
                    } else {
                        "Filtrar fecha"
                    }

                    val isFilterActive = selectedYear != null

                    FilterChip(
                        selected = isFilterActive,
                        onClick = { showMonthYearPicker = true },
                        label = { ZipStatsText(filterLabel) },
                        leadingIcon = {
                            if (isFilterActive) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        trailingIcon = if (isFilterActive) {
                            {
                                IconButton(
                                    onClick = {
                                        viewModel.clearSelectedPeriod()
                                        // Volver a la pestaña por defecto (Este Mes)
                                        selectedPeriod = 0
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Borrar filtro",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = CircleShape
                    )
                }
            }

            // Contenido
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when (statistics) {
                    is StatisticsUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is StatisticsUiState.Success -> {
                        val stats = (statistics as StatisticsUiState.Success)

                        // Determinar qué datos mostrar según la pestaña (INDICES ACTUALIZADOS)
                        val currentPeriod = when (selectedPeriod) {
                            0 -> StatisticsPeriod.MONTHLY
                            1 -> StatisticsPeriod.YEARLY
                            else -> StatisticsPeriod.ALL
                        }

                        // Determinar el título según el período y selección
                        val currentSelectedMonth = selectedMonth
                        val currentSelectedYear = selectedYear

                        val monthlyTitle = if (currentSelectedMonth != null && currentSelectedYear != null) {
                            val monthNames = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                                "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
                            "${monthNames[currentSelectedMonth - 1]} $currentSelectedYear has recorrido:"
                        } else {
                            "Este mes has recorrido:"
                        }

                        val yearlyTitle = if (currentSelectedYear != null && currentSelectedMonth == null) {
                            "$currentSelectedYear has recorrido:"
                        } else {
                            "Este año has recorrido:"
                        }

                        val displayData = when (currentPeriod) {
                            StatisticsPeriod.MONTHLY -> PeriodData(
                                totalDistance = stats.monthlyDistance,
                                averageDistance = stats.monthlyAverageDistance,
                                maxDistance = stats.monthlyMaxDistance,
                                totalRecords = stats.monthlyRecords,
                                title = monthlyTitle,
                                chartData = stats.monthlyChartData,
                                comparison = stats.monthlyComparison
                            )
                            StatisticsPeriod.YEARLY -> PeriodData(
                                totalDistance = stats.yearlyDistance,
                                averageDistance = stats.yearlyAverageDistance,
                                maxDistance = stats.yearlyMaxDistance,
                                totalRecords = stats.yearlyRecords,
                                title = yearlyTitle,
                                chartData = stats.yearlyChartData,
                                comparison = stats.yearlyComparison
                            )
                            StatisticsPeriod.ALL -> PeriodData(
                                totalDistance = stats.totalDistance,
                                averageDistance = stats.averageDistance,
                                maxDistance = stats.maxDistance,
                                totalRecords = stats.totalRecords,
                                title = "En tu vida has recorrido:",
                                chartData = stats.allTimeChartData,
                                comparison = null
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = horizontalPadding),
                            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 8.dp else 16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // 1. Impacto Ecológico (Rediseñado)
                            // 🔥 CORRECCIÓN: Usar el mismo método de redondeo que las tarjetas de insight (roundToOneDecimal)
                            // para que los valores coincidan exactamente. Mostramos 1 decimal para mayor claridad.
                            val co2Value = (displayData.totalDistance * 0.15).roundToOneDecimal()
                            val treesValue = (displayData.totalDistance * 0.005).roundToOneDecimal()
                            val gasValue = (displayData.totalDistance * 0.07).roundToOneDecimal()
                            EcologicalImpactCardEnhanced(
                                co2Saved = co2Value,
                                treesEquivalent = treesValue,
                                gasSaved = gasValue
                            )

                            // 2. Tarjetas de Resumen
                            SummaryStatsCard(
                                periodData = displayData,
                                horizontalPadding = 16.dp, // Padding interno de la tarjeta
                                onShare = {
                                    scope.launch {
                                        val shareText = when (currentPeriod) {
                                            StatisticsPeriod.MONTHLY -> viewModel.getMonthlyShareText(stats, currentSelectedMonth, currentSelectedYear)
                                            StatisticsPeriod.ALL -> viewModel.getShareText(stats)
                                            StatisticsPeriod.YEARLY -> viewModel.getYearlyShareText(stats, currentSelectedYear)
                                        }
                                        val intent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        val title = when (currentPeriod) {
                                            StatisticsPeriod.MONTHLY -> "Compartir estadísticas mensuales"
                                            StatisticsPeriod.ALL -> "Compartir estadísticas totales"
                                            StatisticsPeriod.YEARLY -> "Compartir estadísticas anuales"
                                        }
                                        context.startActivity(Intent.createChooser(intent, title))
                                    }
                                }
                            )

                            if (selectedPeriod != 2 && displayData.totalDistance > 0) {
                                val barChartData = when (currentPeriod) {
                                    StatisticsPeriod.MONTHLY -> displayData.chartData
                                    StatisticsPeriod.YEARLY -> displayData.chartData
                                    StatisticsPeriod.ALL -> emptyList()
                                }

                                if (barChartData.isNotEmpty()) {
                                    DistanceBarChartCard(
                                        title = if (currentPeriod == StatisticsPeriod.YEARLY) {
                                            "Distancia por mes"
                                        } else {
                                            "Distancia por semana"
                                        },
                                        chartData = barChartData,
                                        isYearly = currentPeriod == StatisticsPeriod.YEARLY
                                    )
                                }

                                val hasWeatherData = weatherStats.rainKm > 0.0 ||
                                    weatherStats.wetRoadKm > 0.0 ||
                                    weatherStats.extremeKm > 0.0

                                if (hasWeatherData) {
                                    WeatherConditionsCard(
                                        weatherStats = weatherStats,
                                        comparison = displayData.comparison,
                                        minTemperature = stats.minTemperature,
                                        maxTemperature = stats.maxTemperature,
                                        maxWindGusts = stats.maxWindGusts,
                                        extremeFeelsLike = stats.extremeFeelsLike,
                                        extremeFeelsLikeIsHot = stats.extremeFeelsLikeIsHot
                                    )
                                }
                            }
                            // 4. Tarjeta "Tu Próximo Logro" (Rediseñada)
                            stats.nextAchievement?.let { nextAchievement ->
                                NextAchievementCard(
                                    nextAchievement = nextAchievement
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                    is StatisticsUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            ZipStatsText(
                                text = (statistics as StatisticsUiState.Error).message,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

}

private fun buildStatisticsPdfFileName(
    selectedPeriod: Int,
    selectedMonth: Int?,
    selectedYear: Int?
): String {
    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    val today = LocalDate.now()
    return when (selectedPeriod) {
        0 -> {
            // Misma resolución que StatisticsViewModel.loadStatistics: sin filtro explícito = mes actual
            val month = (selectedMonth ?: today.monthValue).coerceIn(1, 12)
            val year = selectedYear ?: today.year
            "ZipStats_${monthNames[month - 1]}_${year}.pdf"
        }
        1 -> "ZipStats_${selectedYear ?: today.year}.pdf"
        else -> "ZipStats_Historial_Completo.pdf"
    }
}

private fun showPdfProgressNotification(context: android.content.Context, notificationId: Int) {
    if (!hasPostNotificationPermission(context)) return

    val notification = NotificationCompat.Builder(context, ExportUiStrings.NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_download)
        .setContentTitle(ExportUiStrings.PROGRESS_TITLE_PDF)
        .setContentText(ExportUiStrings.PROGRESS_SUBTITLE)
        .setProgress(0, 0, true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

private fun showPdfSavedNotification(context: android.content.Context, uri: Uri, fileName: String, notificationId: Int) {
    if (!hasPostNotificationPermission(context)) return

    val openIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        fileName.hashCode(),
        openIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notification = NotificationCompat.Builder(context, ExportUiStrings.NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_download_done)
        .setContentTitle(ExportUiStrings.COMPLETION_TITLE)
        .setContentText(fileName)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

private fun cancelPdfNotification(context: android.content.Context, notificationId: Int) {
    if (!hasPostNotificationPermission(context)) return
    NotificationManagerCompat.from(context).cancel(notificationId)
}

private fun hasPostNotificationPermission(context: android.content.Context): Boolean {
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    return hasNotificationPermission
}

data class PeriodData(
    val totalDistance: Double,
    val averageDistance: Double,
    val maxDistance: Double,
    val totalRecords: Int,
    val title: String,
    val chartData: List<ChartDataPoint>,
    val comparison: ComparisonData?
)

// ===============================================================
// COMPONENTES UI REDISEÑADOS
// ===============================================================

@Composable
fun EcologicalImpactCardEnhanced(
    co2Saved: Double,
    treesEquivalent: Double,
    gasSaved: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Título con icono
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Eco,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                ZipStatsText(
                    text = "Impacto Ecológico",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Los 3 Pilares de datos con divisores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ImpactItem(
                    value = LocationUtils.formatNumberSpanish(co2Saved, 1),
                    unit = "kg CO₂",
                    icon = Icons.Outlined.Cloud,
                    color = Color(0xFF65C466), // Verde suave
                    modifier = Modifier.weight(1f)
                )
                
                VerticalDivider(
                    modifier = Modifier.height(60.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                ImpactItem(
                    value = LocationUtils.formatNumberSpanish(treesEquivalent, 1),
                    unit = "Árboles",
                    icon = Icons.Outlined.Forest,
                    color = Color(0xFF4CAF50), // Verde más intenso
                    modifier = Modifier.weight(1f)
                )

                VerticalDivider(
                    modifier = Modifier.height(60.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                ImpactItem(
                    value = LocationUtils.formatNumberSpanish(gasSaved, 1),
                    unit = "L Gasolina",
                    icon = Icons.Outlined.LocalGasStation,
                    color = Color(0xFFFFA726), // Naranja
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ImpactItem(
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        // Círculo de fondo para el icono con color personalizado
        Box(
            modifier = Modifier
                .size(56.dp) // Más grande para mejor visibilidad
                .background(
                    color.copy(alpha = 0.2f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }

        // El número grande - HeadlineMedium para más impacto
        ZipStatsText(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            autoResize = true // Ajusta el tamaño automáticamente para que no se corte
        )

        // La unidad pequeña
        ZipStatsText(
            text = unit,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun SummaryStatsCard(
    periodData: PeriodData,
    onShare: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZipStatsText(
                    text = periodData.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Compartir"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMetric(
                    value = formatNumberSpanish(periodData.totalDistance),
                    unit = "km",
                    label = "Total",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                StatMetricWithDrawable(
                    value = formatNumberSpanish(periodData.averageDistance),
                    unit = "km",
                    label = "Promedio",
                    iconPainter = painterResource(id = R.drawable.distancia),
                    modifier = Modifier.weight(1f)
                )
                StatMetric(
                    value = formatNumberSpanish(periodData.maxDistance),
                    unit = "km",
                    label = "Récord",
                    icon = Icons.Outlined.EmojiEvents,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatMetric(
    value: String,
    unit: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp), // Padding horizontal para balance visual
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        ZipStatsText(
            text = value + (if (unit.isNotEmpty()) " $unit" else ""),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center, // Asegurar centrado del texto
            autoResize = true // Ajusta el tamaño automáticamente para que no se corte
        )
        ZipStatsText(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            maxLines = 1,
            textAlign = TextAlign.Center // Asegurar centrado del label
        )
    }
}

@Composable
fun StatMetricWithDrawable(
    value: String,
    unit: String,
    label: String,
    iconPainter: Painter,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp), // Padding horizontal para balance visual
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = iconPainter,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(24.dp)
        )
        ZipStatsText(
            text = value + (if (unit.isNotEmpty()) " $unit" else ""),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center, // Asegurar centrado del texto
            autoResize = true // Ajusta el tamaño automáticamente para que no se corte
        )
        ZipStatsText(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            maxLines = 1,
            textAlign = TextAlign.Center // Asegurar centrado del label
        )
    }
}

@Composable
fun ComparisonCard(
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    comparison: ComparisonData
) {
    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    val comparisonText = if (comparison.comparisonMonth != null) {
        "${monthNames[comparison.comparisonMonth - 1]} ${comparison.comparisonYear}"
    } else {
        "${comparison.comparisonYear}"
    }

    val colorScheme = MaterialTheme.colorScheme
    
    // Colores adaptados a Material 3
    val accentColor = if (comparison.isPositive) {
        colorScheme.primary
    } else {
        colorScheme.error
    }
    
    val accentContainerColor = if (comparison.isPositive) {
        colorScheme.primaryContainer
    } else {
        colorScheme.errorContainer
    }
    
    val onAccentColor = if (comparison.isPositive) {
        colorScheme.onPrimaryContainer
    } else {
        colorScheme.onErrorContainer
    }
    
    // Color de fondo suave
    val backgroundColor = if (comparison.isPositive) {
        colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        colorScheme.errorContainer.copy(alpha = 0.2f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Título con icono de métrica
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Icono de la métrica (emoji)
                    ZipStatsText(
                        text = comparison.icon,
                        style = MaterialTheme.typography.headlineMedium,
                        fontSize = 28.sp
                    )
                    ZipStatsText(
                        text = comparison.title.ifEmpty { "Comparación con $comparisonText" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                }
                
                // Badge con porcentaje
                Box(
                    modifier = Modifier
                        .background(
                            accentContainerColor,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (comparison.isPositive) 
                                Icons.AutoMirrored.Filled.TrendingUp 
                            else 
                                Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        ZipStatsText(
                            text = "${if (comparison.isPositive) "+" else ""}${comparison.percentageChange.roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = onAccentColor
                        )
                    }
                }
            }

            // Comparación visual: Actual vs Anterior
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Columna Actual
                ComparisonValueColumn(
                    label = "Actual",
                    value = formatNumberSpanish(comparison.currentValue),
                    unit = comparison.unit.ifEmpty { "km" },
                    isHighlighted = true,
                    accentColor = accentColor,
                    accentContainerColor = accentContainerColor,
                    modifier = Modifier.weight(1f)
                )
                
                // Divisor vertical
                VerticalDivider(
                    modifier = Modifier.height(80.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                
                // Columna Anterior
                ComparisonValueColumn(
                    label = "Anterior",
                    value = formatNumberSpanish(comparison.previousValue),
                    unit = comparison.unit.ifEmpty { "km" },
                    isHighlighted = false,
                    accentColor = accentColor,
                    accentContainerColor = accentContainerColor,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Barra de diferencia visual
            val maxValue = maxOf(comparison.currentValue, comparison.previousValue)
            val currentRatio = if (maxValue > 0) comparison.currentValue / maxValue else 0.0
            val previousRatio = if (maxValue > 0) comparison.previousValue / maxValue else 0.0
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ZipStatsText(
                    text = "Diferencia visual",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    // Barra del valor anterior (gris)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(previousRatio.toFloat())
                            .fillMaxHeight()
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(4.dp)
                            )
                    )
                    
                    // Barra del valor actual (color destacado)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(currentRatio.toFloat())
                            .fillMaxHeight()
                            .background(
                                accentColor.copy(alpha = 0.7f),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonValueColumn(
    label: String,
    value: String,
    unit: String,
    isHighlighted: Boolean,
    accentColor: Color,
    accentContainerColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ZipStatsText(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
        )
        
        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .background(
                        accentContainerColor,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ZipStatsText(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        autoResize = true // Ajusta el tamaño automáticamente para que no se corte
                    )
                    ZipStatsText(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ZipStatsText(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    autoResize = true // Ajusta el tamaño automáticamente para que no se corte
                )
                ZipStatsText(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DistanceBarChartCard(
    title: String,
    chartData: List<ChartDataPoint>,
    isYearly: Boolean
) {
    val groupedData = remember(chartData, isYearly) {
        if (isYearly) {
            chartData
        } else {
            chartData
                .takeLast(28)
                .chunked(7)
                .mapIndexed { index, week ->
                    ChartDataPoint(
                        date = "Sem ${index + 1}",
                        value = week.sumOf { it.value }
                    )
                }
        }
    }

    if (groupedData.isEmpty()) return

    val maxValue = groupedData.maxOfOrNull { it.value } ?: 0.0
    val roundedMax = (kotlin.math.ceil(maxValue / 5.0) * 5.0).coerceAtLeast(5.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ZipStatsText(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            SimpleBarChart(groupedData = groupedData, maxValue = roundedMax)
        }
    }
}

private val DistanceBarChartBarAreaHeight = 72.dp

@Composable
private fun SimpleBarChart(
    groupedData: List<ChartDataPoint>,
    maxValue: Double
) {
    val maxIndex = groupedData.indices.maxByOrNull { groupedData[it].value } ?: -1

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        groupedData.forEachIndexed { index, point ->
            val barHeightRatio =
                if (maxValue > 0.0) (point.value / maxValue).toFloat() else 0f
            val barColor = if (index == maxIndex) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                ZipStatsText(
                    text = "${formatNumberSpanish(point.value)} km",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    autoResize = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DistanceBarChartBarAreaHeight),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                (DistanceBarChartBarAreaHeight * barHeightRatio).coerceAtLeast(2.dp)
                            )
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(barColor)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                ZipStatsText(
                    text = point.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun WeatherConditionsCard(
    weatherStats: WeatherStats,
    comparison: ComparisonData?,
    minTemperature: Double?,
    maxTemperature: Double?,
    maxWindGusts: Double?,
    extremeFeelsLike: Double?,
    extremeFeelsLikeIsHot: Boolean
) {
    val previous = comparison?.comparisonWeatherMetrics
    val coverage = weatherStats.coveragePercentage

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ZipStatsText(
                text = "Condiciones de tus rutas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeatherMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Lluvia",
                    valueKm = weatherStats.rainKm,
                    previousKm = previous?.rainKm ?: 0.0,
                    icon = Icons.Filled.WaterDrop,
                    containerColor = Color(0xFFE6F1FB),
                    contentColor = Color(0xFF0C447C)
                )
                WeatherMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Húmedo",
                    valueKm = weatherStats.wetRoadKm,
                    previousKm = previous?.wetRoadKm ?: 0.0,
                    icon = Icons.Filled.Water,
                    containerColor = Color(0xFFE6F1FB),
                    contentColor = Color(0xFF0C447C)
                )
                WeatherMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Extremo",
                    valueKm = weatherStats.extremeKm,
                    previousKm = previous?.extremeKm ?: 0.0,
                    icon = Icons.Filled.Thermostat,
                    containerColor = Color(0xFFFAEEDA),
                    contentColor = Color(0xFF633806)
                )
            }

            if (minTemperature != null || maxTemperature != null || maxWindGusts != null || extremeFeelsLike != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ZipStatsText(
                    text = "Récords del período",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (minTemperature != null || maxTemperature != null) {
                        WeatherExtremeInlineItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Thermostat,
                            iconTint = Color(0xFF1976D2),
                            iconBackground = Color(0xFFE3F2FD),
                            value = "${formatNumberSpanish(minTemperature ?: 0.0)}°C · ${formatNumberSpanish(maxTemperature ?: 0.0)}°C",
                            label = "Temp. mín / máx"
                        )
                    }
                    maxWindGusts?.let {
                        WeatherExtremeInlineItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Air,
                            iconTint = Color(0xFF9A6700),
                            iconBackground = Color(0xFFFFF3E0),
                            value = "${it.roundToInt()} km/h",
                            label = "Ráfaga máxima"
                        )
                    }
                }

                extremeFeelsLike?.let {
                    WeatherExtremeInlineItem(
                        modifier = Modifier.fillMaxWidth(),
                        icon = if (extremeFeelsLikeIsHot) Icons.Filled.Whatshot else Icons.Filled.AcUnit,
                        iconTint = if (extremeFeelsLikeIsHot) Color(0xFFD97706) else Color(0xFFC2185B),
                        iconBackground = if (extremeFeelsLikeIsHot) Color(0xFFFFF3E0) else Color(0xFFFCE4EC),
                        value = "${formatNumberSpanish(it)}°C",
                        label = if (extremeFeelsLikeIsHot) {
                            "Sensación máx (venció el calor)"
                        } else {
                            "Sensación mín (venció el frío)"
                        },
                        centered = true
                    )
                }
            }

            if (coverage in 0.1..29.9) {
                ZipStatsText(
                    text = "Basado en el ${coverage.roundToInt()}% de tus rutas grabadas con GPS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeatherMetricTile(
    title: String,
    valueKm: Double,
    previousKm: Double,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val delta = valueKm - previousKm
    val isUp = delta >= 0
    val percentage = if (previousKm > 0.001) {
        kotlin.math.abs((delta / previousKm) * 100.0)
    } else if (valueKm > 0.001) {
        100.0
    } else {
        0.0
    }
    val diffText = if (delta >= 0) {
        "+${formatNumberSpanish(delta)} km extra"
    } else {
        "${formatNumberSpanish(kotlin.math.abs(delta))} km menos"
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            ZipStatsText(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
            ZipStatsText(
                text = "${formatNumberSpanish(valueKm)} km",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            ZipStatsText(
                text = diffText,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.9f)
            )
            Surface(
                color = Color.White.copy(alpha = 0.72f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(1.dp, contentColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    ZipStatsText(
                        text = "${percentage.roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherExtremeInlineItem(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    value: String,
    label: String,
    centered: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            ZipStatsText(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            ZipStatsText(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NextAchievementCard(
    nextAchievement: NextAchievementData
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer // Color distintivo
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lado Izquierdo: Textos y Barra
            Column(modifier = Modifier.weight(1f)) {
                ZipStatsText(
                    text = "Próximo Logro",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
                ZipStatsText(
                    text = nextAchievement.title, // Ej: "El Trotamundos"
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Barra de progreso más gruesa y moderna
                LinearProgressIndicator(
                    progress = { nextAchievement.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ZipStatsText(
                        text = "Objetivo: ${nextAchievement.requirementText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    ZipStatsText(
                        text = "${(nextAchievement.progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // Lado Derecho: Icono Vectorial (Trofeo) en lugar de Emoji Texto
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                imageVector = Icons.Outlined.EmojiEvents, // Trofeo nativo
                contentDescription = "Logro",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthYearPickerDialog(
    currentMonth: Int,
    currentYear: Int,
    availableMonthYears: List<Pair<Int, Int>>,
    onDismiss: () -> Unit,
    onConfirm: (month: Int, year: Int, isYearOnly: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    if (availableMonthYears.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { DialogTitleText("Sin datos") },
            text = { DialogContentText("No hay registros disponibles para consultar.") },
            confirmButton = {
                DialogConfirmButton(
                    text = "Aceptar",
                    onClick = onDismiss
                )
            },
            shape = DialogShape
        )
        return
    }

    var selectedMode by remember { mutableStateOf<SelectionMode?>(null) }
    var selectedMonth by remember { mutableIntStateOf(currentMonth) }
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var showModeDropdown by remember { mutableStateOf(false) }
    var showMonthDropdown by remember { mutableStateOf(false) }
    var showYearDropdown by remember { mutableStateOf(false) }

    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    val availableYears = availableMonthYears.map { it.second }.distinct().sortedDescending()

    val availableMonthsForYear = remember(selectedYear, availableMonthYears) {
        availableMonthYears
            .filter { it.second == selectedYear }
            .map { it.first }
            .distinct()
            .sorted()
    }

    LaunchedEffect(selectedYear, availableMonthsForYear) {
        if (selectedMonth !in availableMonthsForYear && availableMonthsForYear.isNotEmpty()) {
            selectedMonth = availableMonthsForYear.first()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Título
            ZipStatsText(
                text = "Seleccionar Período",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tipo de período
            ExposedDropdownMenuBox(
                expanded = showModeDropdown,
                onExpandedChange = { showModeDropdown = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = when (selectedMode) {
                        is SelectionMode.Month -> "Mes"
                        is SelectionMode.Year -> "Año"
                        null -> "Seleccionar tipo"
                        else -> "Seleccionar tipo"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { ZipStatsText("Tipo de período") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModeDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        ),
                    shape = MaterialTheme.shapes.medium
                )

                ExposedDropdownMenu(
                    expanded = showModeDropdown,
                    onDismissRequest = { showModeDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { ZipStatsText("Mes", maxLines = 2) },
                        onClick = {
                            selectedMode = SelectionMode.Month
                            showModeDropdown = false
                        }
                    )
                    DropdownMenuItem(
                        text = { ZipStatsText("Año", maxLines = 2) },
                        onClick = {
                            selectedMode = SelectionMode.Year
                            showModeDropdown = false
                        }
                    )
                }
            }

            // Selector de año (siempre visible)
            ExposedDropdownMenuBox(
                expanded = showYearDropdown,
                onExpandedChange = { showYearDropdown = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedYear.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { ZipStatsText("Año") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showYearDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        ),
                    shape = MaterialTheme.shapes.medium
                )

                ExposedDropdownMenu(
                    expanded = showYearDropdown,
                    onDismissRequest = { showYearDropdown = false }
                ) {
                    availableYears.forEach { year ->
                        DropdownMenuItem(
                            text = { ZipStatsText(year.toString(), maxLines = 2) },
                            onClick = {
                                selectedYear = year
                                showYearDropdown = false
                            }
                        )
                    }
                }
            }

            // Selector de mes (solo si el modo es Month)
            if (selectedMode is SelectionMode.Month) {
                ExposedDropdownMenuBox(
                    expanded = showMonthDropdown,
                    onExpandedChange = { showMonthDropdown = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = monthNames[selectedMonth - 1],
                        onValueChange = {},
                        readOnly = true,
                        label = { ZipStatsText("Mes") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMonthDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            ),
                        shape = MaterialTheme.shapes.medium
                    )

                    ExposedDropdownMenu(
                        expanded = showMonthDropdown,
                        onDismissRequest = { showMonthDropdown = false }
                    ) {
                        availableMonthsForYear.forEach { monthNumber ->
                            DropdownMenuItem(
                                text = { ZipStatsText(monthNames[monthNumber - 1], maxLines = 2) },
                                onClick = {
                                    selectedMonth = monthNumber
                                    showMonthDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botón de acción principal
            val isEnabled = selectedMode != null
            Button(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        if (!sheetState.isVisible) {
                            when (selectedMode) {
                                is SelectionMode.Month -> onConfirm(selectedMonth, selectedYear, false)
                                is SelectionMode.Year -> onConfirm(selectedMonth, selectedYear, true)
                                null -> {}
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
                enabled = isEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                ZipStatsText(
                    "Aplicar",
                    fontSize = 16.sp,
                    color = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Helper para formatear números en formato español
 * Formato español: punto (.) para miles, coma (,) para decimales
 * Ejemplo: 23.525,25
 * 
 * 🔥 CORRECCIÓN: Usa LocationUtils.formatNumberSpanish() para consistencia
 * (siempre muestra 1 decimal cuando se especifica, ej: "1,0" en lugar de "1")
 */
private fun formatNumberSpanish(value: Double): String {
    return LocationUtils.formatNumberSpanish(value, 1)
}