package com.zipstats.app.ui.statistics

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.R
import com.zipstats.app.ui.components.DialogApplyButton
import com.zipstats.app.ui.components.DialogCancelButton
import com.zipstats.app.ui.components.DialogConfirmButton
import com.zipstats.app.ui.theme.DialogShape
import java.time.LocalDate
import kotlin.math.roundToInt


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
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val availableMonthYears by viewModel.availableMonthYears.collectAsState()
    val periodTitle by viewModel.selectedPeriodTitle.collectAsState()
    val context = LocalContext.current
    var selectedPeriod by remember { mutableIntStateOf(0) }
    var showMonthYearPicker by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Estadísticas", fontWeight = FontWeight.Bold)
                        periodTitle?.let { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showMonthYearPicker = true }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Seleccionar período"
                        )
                    }
                    if ((selectedMonth != null && selectedYear != null) || (selectedYear != null && selectedMonth == null)) {
                        IconButton(onClick = {
                            viewModel.clearSelectedPeriod()
                            // Volver a la pestaña por defecto (Este Mes)
                            selectedPeriod = 0
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar filtro"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
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
                    onClick = { selectedPeriod = 0 },
                    text = {
                        Text(
                            "Este Mes",
                            fontWeight = if (selectedPeriod == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedPeriod == 1,
                    onClick = { selectedPeriod = 1 },
                    text = {
                        Text(
                            "Este Año",
                            fontWeight = if (selectedPeriod == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedPeriod == 2,
                    onClick = { selectedPeriod = 2 },
                    text = {
                        Text(
                            "Todo",
                            fontWeight = if (selectedPeriod == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
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
                            EcologicalImpactCardEnhanced(
                                co2Saved = (displayData.totalDistance * 0.1).toInt(),
                                treesEquivalent = (displayData.totalDistance * 0.005).toInt(),
                                gasSaved = (displayData.totalDistance * 0.04).toInt()
                            )

                            // 2. Tarjetas de Resumen
                            SummaryStatsCard(
                                periodData = displayData,
                                showMaxDistance = false,
                                horizontalPadding = 16.dp, // Padding interno de la tarjeta
                                onShare = {
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
                            )

                            // 3. Tarjeta de Comparación (si existe)
                            displayData.comparison?.let { comparison ->
                                ComparisonCard(
                                    horizontalPadding = 16.dp,
                                    comparison = comparison
                                )
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
                            Text(
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
    co2Saved: Int,
    treesEquivalent: Int,
    gasSaved: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // Fondo con opacidad para que no sea tan pesado
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(24.dp) // Más redondeado estilo M3
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Título con icono
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Eco,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Impacto Ecológico",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Los 3 Datos en Fila
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ImpactItem(
                    value = "$co2Saved",
                    unit = "kg CO₂",
                    icon = Icons.Outlined.Cloud
                )
                ImpactItem(
                    value = "$treesEquivalent",
                    unit = "Árboles",
                    icon = Icons.Outlined.Forest // O Park
                )
                ImpactItem(
                    value = "$gasSaved",
                    unit = "L Gasolina",
                    icon = Icons.Outlined.LocalGasStation
                )
            }
        }
    }
}

@Composable
fun ImpactItem(
    value: String,
    unit: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        // Círculo decorativo para el icono
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // EL NÚMERO GRANDE
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall, // Más grande
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // La unidad pequeña
        Text(
            text = unit,
            style = MaterialTheme.typography.labelMedium, // Pequeño y legible
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SummaryStatsCard(
    periodData: PeriodData,
    showMaxDistance: Boolean,
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
                Text(
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
                    value = String.format("%.1f", periodData.totalDistance),
                    unit = "km",
                    label = "Total",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                StatMetricWithDrawable(
                    value = String.format("%.1f", periodData.averageDistance),
                    unit = "km",
                    label = "Promedio",
                    iconPainter = painterResource(id = R.drawable.distancia),
                    modifier = Modifier.weight(1f)
                )
                if (showMaxDistance) {
                    StatMetric(
                        value = String.format("%.1f", periodData.maxDistance),
                        unit = "km",
                        label = "Máximo",
                        icon = Icons.Filled.BarChart,
                        modifier = Modifier.weight(1f)
                    )
                }
                StatMetric(
                    value = periodData.totalRecords.toString(),
                    unit = "",
                    label = "Registros",
                    icon = Icons.AutoMirrored.Filled.ListAlt,
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
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = value + (if (unit.isNotEmpty()) " $unit" else ""),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = iconPainter,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = value + (if (unit.isNotEmpty()) " $unit" else ""),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    // Usamos colores semánticos más suaves pero claros
    val containerColor = if (comparison.isPositive) colorScheme.tertiaryContainer else colorScheme.errorContainer
    val contentColor = if (comparison.isPositive) colorScheme.onTertiaryContainer else colorScheme.onErrorContainer
    val iconColor = if (comparison.isPositive) colorScheme.tertiary else colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Comparación con $comparisonText",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (comparison.isPositive) "Mejor rendimiento" else "Menos rendimiento",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    imageVector = if (comparison.isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "${if (comparison.isPositive) "+" else ""}${comparison.percentageChange.roundToInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black, // Extra negrita
                    color = iconColor
                )
            }
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
                Text(
                    text = "Próximo Logro",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
                Text(
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
                    Text(
                        text = "Objetivo: ${nextAchievement.requirementText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
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
    if (availableMonthYears.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Sin datos") },
            text = { Text("No hay registros disponibles para consultar.") },
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar Período") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = showModeDropdown,
                    onExpandedChange = { showModeDropdown = it }
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
                        label = { Text("Tipo de período") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModeDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = showModeDropdown,
                        onDismissRequest = { showModeDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Mes") },
                            onClick = {
                                selectedMode = SelectionMode.Month
                                showModeDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Año") },
                            onClick = {
                                selectedMode = SelectionMode.Year
                                showModeDropdown = false
                            }
                        )
                    }
                }

                if (selectedMode is SelectionMode.Month) {
                    ExposedDropdownMenuBox(
                        expanded = showMonthDropdown,
                        onExpandedChange = { showMonthDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = monthNames[selectedMonth - 1],
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Mes") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMonthDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = showMonthDropdown,
                            onDismissRequest = { showMonthDropdown = false }
                        ) {
                            availableMonthsForYear.forEach { monthNumber ->
                                DropdownMenuItem(
                                    text = { Text(monthNames[monthNumber - 1]) },
                                    onClick = {
                                        selectedMonth = monthNumber
                                        showMonthDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = showYearDropdown,
                    onExpandedChange = { showYearDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedYear.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Año") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showYearDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = showYearDropdown,
                        onDismissRequest = { showYearDropdown = false }
                    ) {
                        availableYears.forEach { year ->
                            DropdownMenuItem(
                                text = { Text(year.toString()) },
                                onClick = {
                                    selectedYear = year
                                    showYearDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogApplyButton(
                text = "Aplicar",
                onClick = {
                    when (selectedMode) {
                        is SelectionMode.Month -> onConfirm(selectedMonth, selectedYear, false)
                        is SelectionMode.Year -> onConfirm(selectedMonth, selectedYear, true)
                        null -> {}
                    }
                },
                enabled = selectedMode != null
            )
        },
        dismissButton = {
            DialogCancelButton(
                text = "Cancelar",
                onClick = onDismiss
            )
        },
        shape = DialogShape
    )
}