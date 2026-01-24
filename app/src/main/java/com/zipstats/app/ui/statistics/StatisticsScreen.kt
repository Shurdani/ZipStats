package com.zipstats.app.ui.statistics

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
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
import com.zipstats.app.ui.components.ZipStatsText
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.utils.LocationUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// Extensión para redondear a 1 decimal (igual que en StatisticsViewModel)
private fun Double.roundToOneDecimal(): Double {
    return (this * 10.0).roundToInt() / 10.0
}

/**
 * Métricas disponibles para la tarjeta dinámica (en orden fijo)
 */
enum class DynamicMetricType {
    DISTANCE,      // Distancia
    TREES,         // Árboles
    CO2,           // CO2
    WET_ROAD,      // Calzada Mojada
    RAIN,          // Bajo la lluvia
    EXTREME        // Cond. Extremas
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
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val availableMonthYears by viewModel.availableMonthYears.collectAsState()
    val periodTitle by viewModel.selectedPeriodTitle.collectAsState()
    // Recoge las distancias con condiciones climáticas
    val weatherDistances by viewModel.weatherDistances.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

            // ZONA DE CHIPS DE FILTRO
            // Solo mostramos filtros si NO estamos en la pestaña "Todo" (index 2)
            if (selectedPeriod != 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
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

                        // WeatherStats para la tarjeta dinámica
                        val weatherStats by viewModel.weatherStats.collectAsState()

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
                                showMaxDistance = false,
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

                          // 3. >>> TARJETA DINÁMICA DE MÉTRICAS <<<
                         if (selectedPeriod != 2 && displayData.totalDistance > 0) {
                           // Usamos el operador safe call (?.) y let para asegurar que comparison no sea null
                            displayData.comparison?.let { comparisonInfo ->
                                DynamicMetricCard(
                                 totalDistance = displayData.totalDistance,
                                  weatherStats = weatherStats,
                                  comparison = comparisonInfo,
                                  // Extraemos dinámicamente el periodo (ej: "Enero 2025" o "2025") 
                                 // del título que ya genera el ViewModel
                                 periodLabel = "vs ${comparisonInfo.title.substringAfter(" vs ")}",
                                  selectedPeriod = selectedPeriod,
                                  horizontalPadding = 16.dp
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
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
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
                if (showMaxDistance) {
                    StatMetric(
                        value = formatNumberSpanish(periodData.maxDistance),
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
fun SmartInsightCard(
    data: RandomInsightData,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val themeColor = data.metric.color
    // Fondo muy suave basado en el color de la métrica
    val containerColor = themeColor.copy(alpha = 0.12f) 
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 20.dp)
        ) {
            // HEADER: Icono circular y Título
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(themeColor.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = data.metric.icon,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    ZipStatsText(
                        text = data.metric.label, // Ej: "Gasolina"
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    ZipStatsText(
                        text = data.periodLabel, // Ej: "vs Mes anterior"
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DATOS: Valor grande y Porcentaje
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Columna Izquierda: Valor numérico grande
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        ZipStatsText(
                            text = LocationUtils.formatNumberSpanish(data.currentValue, 1), 
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = themeColor, // El número toma el color del tema
                            autoResize = true // Ajusta el tamaño automáticamente para que no se corte
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        ZipStatsText(
                            text = data.metric.unit, // Ej: "L"
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = themeColor.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                    
                    // Texto diferencial (ej: "+2,4 L extra")
                    val diff = abs(data.currentValue - data.previousValue)
                    val diffFormatted = LocationUtils.formatNumberSpanish(diff, 1)
                    val diffText = if (data.isPositive) 
                        "+$diffFormatted ${data.metric.unit} extra" 
                    else 
                        "$diffFormatted ${data.metric.unit} menos"

                    ZipStatsText(
                        text = diffText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Columna Derecha: Chip de Porcentaje
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (data.isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (data.isPositive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        ZipStatsText(
                            text = "${data.percentageChange.roundToInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (data.isPositive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta dinámica que muestra métricas en orden fijo
 * Cambia automáticamente cada 3 segundos o manualmente al pulsar
 */
@Composable
fun DynamicMetricCard(
    totalDistance: Double,
    weatherStats: WeatherStats,
    comparison: ComparisonData?,
    periodLabel: String,
    selectedPeriod: Int, // Para detectar cambios de pestaña
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp
) {
    // Estado: índice de la métrica actual
    var currentMetricIndex by remember { mutableIntStateOf(0) }
    // Estado: si el cambio automático está activo
    var isAutoMode by remember { mutableStateOf(true) }
    
    // Reactivar modo automático cuando cambia de pestaña
    LaunchedEffect(selectedPeriod) {
        isAutoMode = true
        currentMetricIndex = 0 // Resetear al inicio
    }
    
    // Lista de métricas disponibles (filtrar las que no tienen datos)
    val availableMetrics = remember(weatherStats) {
        val metrics = mutableListOf<DynamicMetricType>()
        
        // Siempre incluir Distancia, Árboles y CO2 (basadas en distancia total)
        metrics.add(DynamicMetricType.DISTANCE)
        metrics.add(DynamicMetricType.TREES)
        metrics.add(DynamicMetricType.CO2)
        
        // Incluir métricas de clima solo si hay datos
        if (weatherStats.wetRoadKm > 0.0) {
            metrics.add(DynamicMetricType.WET_ROAD)
        }
        if (weatherStats.rainKm > 0.0) {
            metrics.add(DynamicMetricType.RAIN)
        }
        if (weatherStats.extremeKm > 0.0) {
            metrics.add(DynamicMetricType.EXTREME)
        }
        
        metrics
    }
    
    // Si no hay métricas disponibles, no mostrar nada
    if (availableMetrics.isEmpty()) return
    
    // Asegurar que el índice esté dentro del rango
    if (currentMetricIndex >= availableMetrics.size) {
        currentMetricIndex = 0
    }
    
    val currentMetric = availableMetrics[currentMetricIndex]
    
    // Cambio automático cada 3 segundos (solo si está en modo automático)
    LaunchedEffect(isAutoMode, availableMetrics.size) {
        if (isAutoMode && availableMetrics.isNotEmpty()) {
            while (isAutoMode) {
                delay(3000) // 3 segundos
                if (isAutoMode) {
                    currentMetricIndex = (currentMetricIndex + 1) % availableMetrics.size
                }
            }
        }
    }
    
    // Calcular valores según la métrica actual
    val (currentValue, previousValue, metricInfo) = remember(currentMetric, totalDistance, weatherStats, comparison) {
        when (currentMetric) {
            DynamicMetricType.DISTANCE -> {
                val prev = comparison?.previousValue ?: 0.0
                Triple(
                    totalDistance,
                    prev,
                    InsightMetric.DISTANCE
                )
            }
            DynamicMetricType.TREES -> {
                val current = totalDistance * 0.005
                val prev = (comparison?.previousValue ?: 0.0) * 0.005
                Triple(
                    current,
                    prev,
                    InsightMetric.TREES
                )
            }
            DynamicMetricType.CO2 -> {
                val current = totalDistance * 0.15
                val prev = (comparison?.previousValue ?: 0.0) * 0.15
                Triple(
                    current,
                    prev,
                    InsightMetric.CO2
                )
            }
            DynamicMetricType.WET_ROAD -> {
                val current = weatherStats.wetRoadKm
                // Usar métricas reales del período comparado (sin estimaciones)
                val prev = comparison?.comparisonWeatherMetrics?.wetRoadKm ?: 0.0
                Triple(
                    current,
                    prev,
                    InsightMetric.WET_ROAD
                )
            }
            DynamicMetricType.RAIN -> {
                val current = weatherStats.rainKm
                // Usar métricas reales del período comparado (sin estimaciones)
                val prev = comparison?.comparisonWeatherMetrics?.rainKm ?: 0.0
                Triple(
                    current,
                    prev,
                    InsightMetric.RAIN
                )
            }
            DynamicMetricType.EXTREME -> {
                val current = weatherStats.extremeKm
                // Usar métricas reales del período comparado (sin estimaciones)
                val prev = comparison?.comparisonWeatherMetrics?.extremeKm ?: 0.0
                Triple(
                    current,
                    prev,
                    InsightMetric.EXTREME
                )
            }
        }
    }
    
    // Calcular porcentaje de cambio
    val diff = currentValue - previousValue
    val percentageChange = if (previousValue > 0.001) {
        kotlin.math.abs((diff / previousValue) * 100)
    } else if (currentValue > 0.001) {
        100.0
    } else {
        0.0
    }
    val isPositive = diff >= 0
    
    // Crear datos para mostrar (similar a RandomInsightData)
    val insightData = RandomInsightData(
        metric = metricInfo,
        periodLabel = periodLabel,
        currentValue = currentValue.roundToOneDecimal(),
        previousValue = previousValue.roundToOneDecimal(),
        percentageChange = percentageChange.roundToOneDecimal(),
        isPositive = isPositive
    )
    
    // Mostrar la tarjeta con click para cambiar manualmente
    SmartInsightCard(
        data = insightData,
        horizontalPadding = horizontalPadding,
        modifier = Modifier.clickable {
            // Al pulsar, desactivar modo automático y avanzar manualmente
            isAutoMode = false
            currentMetricIndex = (currentMetricIndex + 1) % availableMetrics.size
        }
    )
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
            title = { ZipStatsText("Sin datos") },
            text = { ZipStatsText("No hay registros disponibles para consultar.") },
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
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        ),
                    shape = MaterialTheme.shapes.medium
                )

                ExposedDropdownMenu(
                    expanded = showModeDropdown,
                    onDismissRequest = { showModeDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { ZipStatsText("Mes") },
                        onClick = {
                            selectedMode = SelectionMode.Month
                            showModeDropdown = false
                        }
                    )
                    DropdownMenuItem(
                        text = { ZipStatsText("Año") },
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
                            type = MenuAnchorType.PrimaryNotEditable,
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
                            text = { ZipStatsText(year.toString()) },
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
                                type = MenuAnchorType.PrimaryNotEditable,
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
                                text = { ZipStatsText(monthNames[monthNumber - 1]) },
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