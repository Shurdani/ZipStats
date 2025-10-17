package com.example.patineta.ui.statistics

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Co2
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.OilBarrel
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import java.time.LocalDate
import kotlin.math.roundToInt

enum class StatisticsPeriod {
    MONTHLY, ALL, YEARLY
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
            onConfirm = { month, year ->
                viewModel.setSelectedPeriod(month, year)
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
                        Text("Estadísticas")
                        periodTitle?.let { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall
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
                    if (selectedMonth != null && selectedYear != null) {
                        IconButton(onClick = { viewModel.clearSelectedPeriod() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar filtro"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pestañas
            TabRow(selectedTabIndex = selectedPeriod) {
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
                            "Todo",
                            fontWeight = if (selectedPeriod == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedPeriod == 2,
                    onClick = { selectedPeriod = 2 },
                    text = { 
                        Text(
                            "Este Año",
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
                        
                        // Determinar qué datos mostrar según la pestaña
                        val currentPeriod = when (selectedPeriod) {
                            0 -> StatisticsPeriod.MONTHLY
                            1 -> StatisticsPeriod.ALL
                            else -> StatisticsPeriod.YEARLY
                        }
                        
                        val displayData = when (currentPeriod) {
                            StatisticsPeriod.MONTHLY -> PeriodData(
                            totalDistance = stats.monthlyDistance,
                            averageDistance = stats.monthlyAverageDistance,
                            maxDistance = stats.monthlyMaxDistance,
                            totalRecords = stats.monthlyRecords,
                                title = "Este mes has recorrido:",
                                chartData = stats.monthlyChartData,
                                comparison = stats.monthlyComparison
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
                            StatisticsPeriod.YEARLY -> PeriodData(
                                totalDistance = stats.yearlyDistance,
                                averageDistance = stats.yearlyAverageDistance,
                                maxDistance = stats.yearlyMaxDistance,
                                totalRecords = stats.yearlyRecords,
                                title = "Este año has recorrido:",
                                chartData = stats.yearlyChartData,
                                comparison = stats.yearlyComparison
                            )
                        }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Impacto Ecológico (Destacado y grande)
                            EcologicalImpactCardEnhanced(
                                co2Saved = (displayData.totalDistance * 0.1).toInt(),
                                treesEquivalent = (displayData.totalDistance * 0.005).toInt(),
                                gasSaved = (displayData.totalDistance * 0.04).toInt()
                            )

                            // Tarjetas de Resumen
                            SummaryStatsCard(
                                periodData = displayData,
                                showMaxDistance = currentPeriod != StatisticsPeriod.ALL,
                                onShare = {
                                    val shareText = when (currentPeriod) {
                                        StatisticsPeriod.MONTHLY -> viewModel.getMonthlyShareText(stats)
                                        StatisticsPeriod.ALL -> viewModel.getShareText(stats)
                                        StatisticsPeriod.YEARLY -> viewModel.getYearlyShareText(stats)
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

                            // Tarjeta de Comparación (si existe)
                            displayData.comparison?.let { comparison ->
                                ComparisonCard(
                                    comparison = comparison
                                )
                            }

                            // Tarjeta "Tu Próximo Logro"
                            stats.nextAchievement?.let { nextAchievement ->
                                NextAchievementCard(
                                    nextAchievement = nextAchievement
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
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

@Composable
fun EcologicalImpactCardEnhanced(
    co2Saved: Int,
    treesEquivalent: Int,
    gasSaved: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Impacto Ecológico",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Icon(
                    imageVector = Icons.Filled.Park,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ImpactMetricEnhanced(
                    value = "$co2Saved",
                    unit = "kg CO₂",
                    description = "ahorrados",
                    icon = Icons.Outlined.Co2,
                    modifier = Modifier.weight(1f)
                )
                ImpactMetricEnhanced(
                    value = "$treesEquivalent",
                    unit = "árboles",
                    description = "equiv.",
                    icon = Icons.Outlined.Forest,
                    modifier = Modifier.weight(1f)
                )
                ImpactMetricEnhanced(
                    value = "$gasSaved",
                    unit = "L",
                    description = "gasolina",
                    icon = Icons.Outlined.OilBarrel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ImpactMetricEnhanced(
    value: String,
    unit: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SummaryStatsCard(
    periodData: PeriodData,
    showMaxDistance: Boolean,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = periodData.title,
                    style = MaterialTheme.typography.titleLarge,
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
                StatMetric(
                    value = String.format("%.1f", periodData.averageDistance),
                    unit = "km",
                    label = "Promedio",
                    icon = Icons.Filled.Speed,
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ComparisonCard(
    comparison: ComparisonData
) {
    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    
    val comparisonText = if (comparison.comparisonMonth != null) {
        // Comparación mensual
        "${monthNames[comparison.comparisonMonth - 1]} del año ${comparison.comparisonYear}"
    } else {
        // Comparación anual
        "el año ${comparison.comparisonYear}"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (comparison.isPositive) 
                MaterialTheme.colorScheme.tertiaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = "Comparación con $comparisonText",
                    style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (comparison.isPositive) {
                        "¡Vas ${comparison.percentageChange.roundToInt()}% mejor!"
                    } else {
                        "${comparison.percentageChange.roundToInt()}% menos que $comparisonText"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    imageVector = if (comparison.isPositive) 
                        Icons.AutoMirrored.Filled.TrendingUp 
                    else 
                        Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = if (comparison.isPositive) 
                        MaterialTheme.colorScheme.tertiary 
                    else 
                        MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "${if (comparison.isPositive) "+" else ""}${comparison.percentageChange.roundToInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (comparison.isPositive) 
                        MaterialTheme.colorScheme.tertiary 
                    else 
                        MaterialTheme.colorScheme.error
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
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tu Próximo Logro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = nextAchievement.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = nextAchievement.requirementText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
                // Mostrar el emoji del logro
                Text(
                    text = nextAchievement.emoji,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            // Descripción del logro
            Text(
                text = nextAchievement.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            
            // Barra de progreso
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(nextAchievement.progress)
                            .height(12.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                ),
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (nextAchievement.progress < 1f) {
                            "Objetivo: ${nextAchievement.requirementText}"
                        } else {
                            "¡Logro completado! 🎉"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${(nextAchievement.progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
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
    onConfirm: (month: Int, year: Int) -> Unit
) {
    // Validar que hay períodos disponibles
    if (availableMonthYears.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Sin datos") },
            text = { Text("No hay registros disponibles para consultar.") },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Aceptar")
                }
            }
        )
        return
    }
    
    var selectedMonth by remember { mutableIntStateOf(currentMonth) }
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var showMonthDropdown by remember { mutableStateOf(false) }
    var showYearDropdown by remember { mutableStateOf(false) }
    
    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    
    // Obtener años únicos de los registros disponibles
    val availableYears = availableMonthYears.map { it.second }.distinct().sortedDescending()
    
    // Obtener meses disponibles para el año seleccionado
    val availableMonthsForYear = remember(selectedYear, availableMonthYears) {
        availableMonthYears
            .filter { it.second == selectedYear }
            .map { it.first }
            .distinct()
            .sorted()
    }
    
    // Validar que el mes seleccionado existe en el año seleccionado
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
                // Selector de mes
                ExposedDropdownMenuBox(
                    expanded = showMonthDropdown,
                    onExpandedChange = { showMonthDropdown = it }
                ) {
                    OutlinedTextField(
                        value = monthNames[selectedMonth - 1],
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Mes") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMonthDropdown) },
            modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
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
                
                // Selector de año
                ExposedDropdownMenuBox(
                    expanded = showYearDropdown,
                    onExpandedChange = { showYearDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedYear.toString(),
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Año") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showYearDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
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
            TextButton(
                onClick = { onConfirm(selectedMonth, selectedYear) }
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
