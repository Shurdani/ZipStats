package com.zipstats.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zipstats.app.model.Achievement
import com.zipstats.app.model.AchievementLevel
import com.zipstats.app.model.AchievementRequirementType
import com.zipstats.app.model.Scooter
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.RouteRepository
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Date
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Water
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class Statistics(
    val totalDistance: Double,
    val averageDistance: Double,
    val totalRecords: Int,
    val lastRecordDate: Date?,
    val funnyComparison: String,
    val percentageComplete: Double,
    val co2Saved: Double,
    val treesEquivalent: Double
)

data class ScooterStats(
    val model: String,
    val totalKilometers: Double
)

data class ChartDataPoint(
    val date: String,
    val value: Double
)

enum class ComparisonMetricType {
    DISTANCE, CO2, TREES, GAS
}

// Configuración de cada métrica (Icono, Color, Factor de conversión)
enum class InsightMetric(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val unit: String,
    val factor: Double // Factor para convertir KM a esta unidad (Solo se usa para los derivados del total)
) {
    // --- Métricas derivadas del Total (Factor > 0) ---
    DISTANCE("Distancia", Icons.Default.DirectionsBike, Color(0xFF2979FF), "km", 1.0),
    
    // 150g de CO2 por km (media coche)
    CO2("CO2 Ahorrado", Icons.Outlined.Cloud, Color(0xFF4CAF50), "kg", 0.15), 
    
    // 1 árbol absorbe aprox 20kg CO2/año -> simplificado: 1 árbol cada 200km
    TREES("Árboles", Icons.Outlined.Forest, Color(0xFF8BC34A), "u.", 0.005), 
    
    // 7 Litros/100km (media coche urbano) -> 0.07 L/km
    FUEL("Gasolina", Icons.Outlined.LocalGasStation, Color(0xFFFFA726), "L", 0.07),
    
    // --- Nuevas Métricas Específicas (Factor 1.0 porque pasaremos el valor directo) ---
    RAIN("Rutas con Lluvia", Icons.Filled.WaterDrop, Color(0xFF00B0FF), "km", 1.0), // Azul Cian
    WET_ROAD("Calzada Mojada", Icons.Outlined.Water, Color(0xFFFF9100), "km", 1.0), // Naranja/Ámbar
    EXTREME("Clima Extremo", Icons.Filled.Thermostat, Color(0xFFD50000), "km", 1.0) // Rojo
}

// Datos listos para pintar en la tarjeta
data class RandomInsightData(
    val metric: InsightMetric,
    val periodLabel: String,    // Ej: "vs Mes anterior"
    val currentValue: Double,   // Valor calculado (ej: 5.4 Litros)
    val previousValue: Double,
    val percentageChange: Double,
    val isPositive: Boolean     // True si has mejorado (más distancia o más ahorro)
)

data class ComparisonData(
    val currentValue: Double,
    val previousValue: Double,
    val percentageChange: Double,
    val isPositive: Boolean,
    val comparisonMonth: Int? = null,
    val comparisonYear: Int,
    val metricType: ComparisonMetricType = ComparisonMetricType.DISTANCE,
    val title: String = "",
    val unit: String = "",
    val icon: String = ""
)

data class NextAchievementData(
    val title: String,
    val emoji: String,
    val description: String,
    val progress: Float,
    val requirementText: String // Ej: "500 km", "10 viajes", "7 días consecutivos"
)

data class EnvironmentalStats(
    val co2Saved: Double,
    val treesEquivalent: Double
)

data class DistanceComparison(
    val totalKilometers: Double,
    val funnyComparison: String,
    val percentageComplete: Double
)

sealed class StatisticsUiState {
    object Loading : StatisticsUiState()
    data class Success(
        val totalDistance: Double,
        val maxDistance: Double,
        val averageDistance: Double,
        val totalRecords: Int,
        val lastRecordDate: String,
        val lastRecordDistance: Double,
        val scooterStats: List<ScooterStats>,
        val monthlyDistance: Double,
        val monthlyMaxDistance: Double,
        val monthlyAverageDistance: Double,
        val monthlyRecords: Int,
        val yearlyDistance: Double,
        val yearlyMaxDistance: Double,
        val yearlyAverageDistance: Double,
        val yearlyRecords: Int,
        val monthlyChartData: List<ChartDataPoint>,
        val yearlyChartData: List<ChartDataPoint>,
        val allTimeChartData: List<ChartDataPoint>,
        val monthlyComparison: ComparisonData?,
        val yearlyComparison: ComparisonData?,
        val nextAchievement: NextAchievementData?
    ) : StatisticsUiState()
    data class Error(val message: String) : StatisticsUiState()
}

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val recordRepository: RecordRepository,
    private val routeRepository: RouteRepository,
    private val scooterRepository: VehicleRepository,
    private val userRepository: UserRepository,
    private val achievementsService: com.zipstats.app.service.AchievementsService
) : ViewModel() {

    private val _statistics = MutableStateFlow<StatisticsUiState>(StatisticsUiState.Loading)
    val statistics: StateFlow<StatisticsUiState> = _statistics.asStateFlow()

    private val _scooters = MutableStateFlow<List<Scooter>>(emptyList())
    val scooters: StateFlow<List<Scooter>> = _scooters.asStateFlow()

    private val _userName = MutableStateFlow<String>("Mi Vehículo")
    private val userName: StateFlow<String> = _userName.asStateFlow()
    
    private val _selectedMonth = MutableStateFlow<Int?>(null)
    val selectedMonth: StateFlow<Int?> = _selectedMonth.asStateFlow()
    
    private val _selectedYear = MutableStateFlow<Int?>(null)
    val selectedYear: StateFlow<Int?> = _selectedYear.asStateFlow()
    
    private val _availableMonthYears = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val availableMonthYears: StateFlow<List<Pair<Int, Int>>> = _availableMonthYears.asStateFlow()
    
    val selectedPeriodTitle: StateFlow<String?> = combine(
        _selectedMonth,
        _selectedYear
    ) { month, year ->
        when {
            month != null && year != null -> {
                val monthNames = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
                "${monthNames[month - 1]} $year"
            }
            year != null -> year.toString()
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Obtener la lista de logros del servicio centralizado
    private val allAchievements get() = achievementsService.allAchievements
    
    // --- NUEVO: Estado del Insight Aleatorio ---
    private val _insightState = MutableStateFlow<RandomInsightData?>(null)
    val insightState: StateFlow<RandomInsightData?> = _insightState.asStateFlow()
    
    // --- Estado de distancias con condiciones climáticas ---
    private val _weatherDistances = MutableStateFlow<Triple<Double, Double, Double>>(Triple(0.0, 0.0, 0.0))
    val weatherDistances: StateFlow<Triple<Double, Double, Double>> = _weatherDistances.asStateFlow()
    
    init {
        loadStatistics()
        loadScooters()
        loadUserName()
    }

    private fun loadUserName() {
        viewModelScope.launch {
            userRepository.getUserProfile().collect { user ->
                _userName.value = user.name
            }
        }
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            _statistics.value = StatisticsUiState.Loading
            try {
                // Obtener rutas para calcular distancias con condiciones climáticas
                val routesResult = routeRepository.getUserRoutes()
                val allRoutes = routesResult.getOrNull() ?: emptyList()
                
                scooterRepository.getScooters().collect { scooters ->
                    recordRepository.getRecords().collect { records ->
                        // Calcular los meses/años disponibles
                        val monthYears = records.mapNotNull { record ->
                            try {
                                val date = LocalDate.parse(record.fecha)
                                Pair(date.monthValue, date.year)
                            } catch (e: Exception) {
                                null
                            }
                        }.distinct().sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenByDescending { it.first })
                        
                        _availableMonthYears.value = monthYears
                        
                        val totalDistance = records.sumOf { it.diferencia }.roundToOneDecimal()
                        val maxDistance = records.maxOfOrNull { it.diferencia }?.roundToOneDecimal() ?: 0.0
                        val averageDistance = if (records.isNotEmpty()) {
                            (records.sumOf { it.diferencia } / records.size).roundToOneDecimal()
                        } else {
                            0.0
                        }
                        val totalRecords = records.size
                        val lastRecord = records.maxByOrNull { it.fecha }

                        // Si solo hay año seleccionado (sin mes), usar el mes actual para cálculos internos
                        // pero las estadísticas mensuales solo se mostrarán si hay mes seleccionado
                        val currentMonth = _selectedMonth.value ?: LocalDate.now().monthValue
                        val currentYear = _selectedYear.value ?: LocalDate.now().year
                        
                        // Estadísticas mensuales (solo si hay mes seleccionado, o del mes actual si no hay selección)
                        val monthlyRecords = records.filter {
                            try {
                                val recordDate = LocalDate.parse(it.fecha)
                                // Si hay mes seleccionado, usar ese mes; si no, usar el mes actual
                                val targetMonth = _selectedMonth.value ?: LocalDate.now().monthValue
                                recordDate.monthValue == targetMonth && recordDate.year == currentYear
                            } catch (e: Exception) {
                                false
                            }
                        }

                        val monthlyDistance = monthlyRecords.sumOf { it.diferencia }.roundToOneDecimal()
                        val monthlyMaxDistance = monthlyRecords.maxOfOrNull { it.diferencia }?.roundToOneDecimal() ?: 0.0
                        val monthlyAverageDistance = if (monthlyRecords.isNotEmpty()) {
                            (monthlyRecords.sumOf { it.diferencia } / monthlyRecords.size).roundToOneDecimal()
                        } else {
                            0.0
                        }
                        val monthlyRecordsCount = monthlyRecords.size

                        // Estadísticas anuales
                        val yearlyRecords = records.filter {
                            try {
                                val recordDate = LocalDate.parse(it.fecha)
                                recordDate.year == currentYear
                            } catch (e: Exception) {
                                false
                            }
                        }

                        val yearlyDistance = yearlyRecords.sumOf { it.diferencia }.roundToOneDecimal()
                        val yearlyMaxDistance = yearlyRecords.maxOfOrNull { it.diferencia }?.roundToOneDecimal() ?: 0.0
                        val yearlyAverageDistance = if (yearlyRecords.isNotEmpty()) {
                            (yearlyRecords.sumOf { it.diferencia } / yearlyRecords.size).roundToOneDecimal()
                        } else {
                            0.0
                        }
                        val yearlyRecordsCount = yearlyRecords.size

                        val scooterStats = scooters.map { scooter ->
                            val scooterRecords = records.filter { it.patinete == scooter.nombre }
                            ScooterStats(
                                model = scooter.modelo,
                                totalKilometers = scooterRecords.sumOf { it.diferencia }.roundToOneDecimal()
                            )
                        }

                        // Datos del gráfico mensual (últimos 30 días)
                        val monthlyChartData = calculateMonthlyChartData(records)
                        
                        // Datos del gráfico anual (por mes)
                        val yearlyChartData = calculateYearlyChartData(records, currentYear)
                        
                        // Datos del gráfico de todo el tiempo (por mes)
                        val allTimeChartData = calculateAllTimeChartData(records)
                        
                        // Comparación mensual (mes actual vs mes anterior)
                        val monthlyComparison = calculateMonthlyComparison(records, currentMonth, currentYear)
                        
                        // Comparación anual (año actual vs año anterior)
                        val yearlyComparison = calculateYearlyComparison(records, currentYear)
                        
                        // Calcular distancias con condiciones climáticas
                        val (rainKm, wetRoadKm, extremeKm) = calculateWeatherDistances(
                            routes = allRoutes,
                            currentMonth = _selectedMonth.value,
                            currentYear = currentYear
                        )
                        _weatherDistances.value = Triple(rainKm, wetRoadKm, extremeKm)
                        
                        // Calcular el siguiente logro (ahora basado en múltiples métricas)
                        val nextAchievement = try {
                            calculateNextAchievement()
                        } catch (e: Exception) {
                            null
                        }

                        _statistics.value = StatisticsUiState.Success(
                            totalDistance = totalDistance,
                            maxDistance = maxDistance,
                            averageDistance = averageDistance,
                            totalRecords = totalRecords,
                            lastRecordDate = lastRecord?.fecha ?: "No hay registros",
                            lastRecordDistance = lastRecord?.diferencia?.roundToOneDecimal() ?: 0.0,
                            scooterStats = scooterStats,
                            monthlyDistance = monthlyDistance,
                            monthlyMaxDistance = monthlyMaxDistance,
                            monthlyAverageDistance = monthlyAverageDistance,
                            monthlyRecords = monthlyRecordsCount,
                            yearlyDistance = yearlyDistance,
                            yearlyMaxDistance = yearlyMaxDistance,
                            yearlyAverageDistance = yearlyAverageDistance,
                            yearlyRecords = yearlyRecordsCount,
                            monthlyChartData = monthlyChartData,
                            yearlyChartData = yearlyChartData,
                            allTimeChartData = allTimeChartData,
                            monthlyComparison = monthlyComparison,
                            yearlyComparison = yearlyComparison,
                            nextAchievement = nextAchievement
                        )
                    }
                }
            } catch (e: Exception) {
                _statistics.value = StatisticsUiState.Error(e.message ?: "Error al cargar las estadísticas")
            }
        }
    }

    private fun loadScooters() {
        viewModelScope.launch {
            try {
                scooterRepository.getScooters().collect { scooters ->
                    _scooters.value = scooters
                }
            } catch (e: Exception) {
                // Manejar error si es necesario
            }
        }
    }

    fun refreshStatistics() {
        loadStatistics()
    }
    
    fun setSelectedPeriod(month: Int?, year: Int?) {
        _selectedMonth.value = month
        _selectedYear.value = year
        loadStatistics()
    }
    
    fun clearSelectedPeriod() {
        _selectedMonth.value = null
        _selectedYear.value = null
        loadStatistics()
    }

    fun getShareText(stats: StatisticsUiState.Success): String {
        val co2Saved = (stats.totalDistance * 0.1).toInt()
        val treesEquivalent = (stats.totalDistance * 0.005).toInt()
        val gasSaved = (stats.totalDistance * 0.04).toInt() // 0.04 litros de gasolina por km ahorrado
        val topScooters = stats.scooterStats.sortedByDescending { it.totalKilometers }.take(2)
        
        val medals = listOf("🥇", "🥈")
        val scooterTexts = topScooters.mapIndexed { index, scooter ->
            "${medals[index]} ${scooter.model}: ${scooter.totalKilometers} km"
        }
        
        return """ Estadísticas totales de ${userName.value} 

📊 Total recorrido: ${stats.totalDistance} km
🌱 CO₂ ahorrado: $co2Saved kg ≈ $treesEquivalent árboles 🌳
⛽ Gasolina ahorrada: $gasSaved Litros
🏆 Top Vehículos:
${scooterTexts.joinToString("\n")}
#ZipStats""".trimIndent()
    }

    fun getMonthlyShareText(stats: StatisticsUiState.Success, month: Int? = null, year: Int? = null): String {
        val co2Saved = (stats.monthlyDistance * 0.1).toInt()
        val treesEquivalent = (stats.monthlyDistance * 0.005).toInt()
        val gasSaved = (stats.monthlyDistance * 0.04).toInt()
        
        // Usar el mes y año seleccionados, o el actual si no hay selección
        val selectedMonth = (month ?: _selectedMonth.value ?: LocalDate.now().monthValue).coerceIn(1, 12)
        val selectedYear = year ?: _selectedYear.value ?: LocalDate.now().year
        
        // Lista de nombres de meses en español
        val monthNames = listOf(
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        )
        val monthName = monthNames.getOrElse(selectedMonth - 1) { "Mes" }
        
        return """
 Estadísticas de $monthName $selectedYear de ${userName.value} 

📊 Total recorrido: ${stats.monthlyDistance} km
📈 Promedio por registro: ${stats.monthlyAverageDistance} km
🏆 Mejor registro: ${stats.monthlyMaxDistance} km
📝 Total de registros: ${stats.monthlyRecords}
🌱 CO₂ ahorrado: $co2Saved kg ≈ $treesEquivalent árboles 🌳
⛽ Gasolina ahorrada: $gasSaved Litros 
#ZipStats""".trimIndent()
    }

    fun getYearlyShareText(stats: StatisticsUiState.Success, year: Int? = null): String {
        val co2Saved = (stats.yearlyDistance * 0.1).toInt()
        val treesEquivalent = (stats.yearlyDistance * 0.005).toInt()
        val gasSaved = (stats.yearlyDistance * 0.04).toInt()
        
        // Usar el año seleccionado, o el actual si no hay selección
        val selectedYear = year ?: _selectedYear.value ?: LocalDate.now().year
        
        return """
 Estadísticas de $selectedYear de ${userName.value}

📊 Total recorrido: ${stats.yearlyDistance} km
📈 Promedio por registro: ${stats.yearlyAverageDistance} km
🏆 Mejor registro: ${stats.yearlyMaxDistance} km
📝 Total de registros: ${stats.yearlyRecords}
🌱 CO₂ ahorrado: $co2Saved kg ≈ $treesEquivalent árboles 🌳
⛽ Gasolina ahorrada: $gasSaved Litros 
#ZipStats""".trimIndent()
    }

    private fun calculateMonthlyChartData(records: List<com.zipstats.app.model.Record>): List<ChartDataPoint> {
        val now = LocalDate.now()
        val last30Days = (0..29).map { now.minusDays(it.toLong()) }.reversed()
        
        return last30Days.map { date ->
            val dailyDistance = records
                .filter { 
                    try {
                        LocalDate.parse(it.fecha) == date
                    } catch (e: Exception) {
                        false
                    }
                }
                .sumOf { it.diferencia }
            ChartDataPoint(
                date = "${date.dayOfMonth}/${date.monthValue}",
                value = dailyDistance.roundToOneDecimal()
            )
        }
    }
    
    private fun calculateYearlyChartData(records: List<com.zipstats.app.model.Record>, year: Int): List<ChartDataPoint> {
        val monthNames = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
        
        return (1..12).map { month ->
            val monthlyDistance = records
                .filter {
                    try {
                        val recordDate = LocalDate.parse(it.fecha)
                        recordDate.year == year && recordDate.monthValue == month
                    } catch (e: Exception) {
                        false
                    }
                }
                .sumOf { it.diferencia }
            ChartDataPoint(
                date = monthNames[month - 1],
                value = monthlyDistance.roundToOneDecimal()
            )
        }
    }
    
    private fun calculateAllTimeChartData(records: List<com.zipstats.app.model.Record>): List<ChartDataPoint> {
        if (records.isEmpty()) return emptyList()
        
        val monthNames = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
        
        // Agrupar por año-mes
        val groupedByMonth = records.groupBy {
            try {
                val date = LocalDate.parse(it.fecha)
                "${date.year}-${String.format("%02d", date.monthValue)}"
            } catch (e: Exception) {
                "error-00"
            }
        }.filterKeys { it != "error-00" }
        
        // Ordenar y tomar los últimos 12 meses
        val sortedMonths = groupedByMonth.keys.sorted().takeLast(12)
        
        return sortedMonths.map { yearMonth ->
            val (year, month) = yearMonth.split("-")
            val monthlyDistance = groupedByMonth[yearMonth]?.sumOf { it.diferencia } ?: 0.0
            ChartDataPoint(
                date = "${monthNames[month.toInt() - 1]} ${year.takeLast(2)}",
                value = monthlyDistance.roundToOneDecimal()
            )
        }
    }
    
    private fun calculateMonthlyComparison(records: List<com.zipstats.app.model.Record>, currentMonth: Int, currentYear: Int): ComparisonData? {
        val today = LocalDate.now()
        val isCurrentMonth = currentMonth == today.monthValue && currentYear == today.year
        val currentDayOfMonth = if (isCurrentMonth) today.dayOfMonth else 31
        
        // Obtener registros del mes/año actual
        val currentMonthRecords = records.filter {
            try {
                val recordDate = LocalDate.parse(it.fecha)
                recordDate.monthValue == currentMonth && 
                recordDate.year == currentYear &&
                recordDate.dayOfMonth <= currentDayOfMonth
            } catch (e: Exception) {
                false
            }
        }
        
        val currentDistance = currentMonthRecords.sumOf { it.diferencia }
        
        // Buscar el mismo mes en años anteriores
        var comparisonYear: Int? = null
        var previousDistance = 0.0
        
        for (yearOffset in 1..10) {
            val yearToCheck = currentYear - yearOffset
            val previousYearRecords = records.filter {
                try {
                    val recordDate = LocalDate.parse(it.fecha)
                    recordDate.monthValue == currentMonth && 
                    recordDate.year == yearToCheck &&
                    recordDate.dayOfMonth <= currentDayOfMonth
                } catch (e: Exception) {
                    false
                }
            }
            
            if (previousYearRecords.isNotEmpty()) {
                previousDistance = previousYearRecords.sumOf { it.diferencia }
                comparisonYear = yearToCheck
                break
            }
        }
        
        if (comparisonYear == null || previousDistance == 0.0) return null
        
        // Calcular todas las métricas posibles
        val allComparisons = listOf(
            createComparisonMetric(
                currentDistance = currentDistance,
                previousDistance = previousDistance,
                metricType = ComparisonMetricType.DISTANCE,
                comparisonMonth = currentMonth,
                comparisonYear = comparisonYear
            ),
            createComparisonMetric(
                currentDistance = currentDistance,
                previousDistance = previousDistance,
                metricType = ComparisonMetricType.CO2,
                comparisonMonth = currentMonth,
                comparisonYear = comparisonYear
            ),
            createComparisonMetric(
                currentDistance = currentDistance,
                previousDistance = previousDistance,
                metricType = ComparisonMetricType.TREES,
                comparisonMonth = currentMonth,
                comparisonYear = comparisonYear
            ),
            createComparisonMetric(
                currentDistance = currentDistance,
                previousDistance = previousDistance,
                metricType = ComparisonMetricType.GAS,
                comparisonMonth = currentMonth,
                comparisonYear = comparisonYear
            )
        ).filterNotNull()
        
        // Seleccionar una aleatoriamente
        return allComparisons.randomOrNull()
    }
    
    private fun calculateYearlyComparison(records: List<com.zipstats.app.model.Record>, currentYear: Int): ComparisonData? {
        val today = LocalDate.now()
        val isCurrentYear = currentYear == today.year
        val currentDayOfYear = if (isCurrentYear) today.dayOfYear else 366
        
        // Obtener registros del año actual
        val currentYearRecords = records.filter {
            try {
                val recordDate = LocalDate.parse(it.fecha)
                recordDate.year == currentYear &&
                recordDate.dayOfYear <= currentDayOfYear
            } catch (e: Exception) {
                false
            }
        }
        
        val currentDistance = currentYearRecords.sumOf { it.diferencia }
        
        // Buscar el año más próximo con datos
        var comparisonYear: Int? = null
        var previousDistance = 0.0
        
        for (yearOffset in 1..10) {
            val yearToCheck = currentYear - yearOffset
            val previousYearRecords = records.filter {
                try {
                    val recordDate = LocalDate.parse(it.fecha)
                    recordDate.year == yearToCheck &&
                    recordDate.dayOfYear <= currentDayOfYear
                } catch (e: Exception) {
                    false
                }
            }
            
            if (previousYearRecords.isNotEmpty()) {
                previousDistance = previousYearRecords.sumOf { it.diferencia }
                comparisonYear = yearToCheck
                break
            }
        }
        
        if (comparisonYear == null || previousDistance == 0.0) return null
        
        // Calcular todas las métricas posibles
        val allComparisons = listOf(
            createComparisonMetric(
                currentDistance = currentDistance,
                previousDistance = previousDistance,
                metricType = ComparisonMetricType.DISTANCE,
                comparisonMonth = null,
                comparisonYear = comparisonYear
            ),
            createComparisonMetric(
                currentDistance = currentDistance,
                previousDistance = previousDistance,
                metricType = ComparisonMetricType.CO2,
                comparisonMonth = null,
                comparisonYear = comparisonYear
            ),
            createComparisonMetric(
                currentDistance = currentDistance,
                previousDistance = previousDistance,
                metricType = ComparisonMetricType.TREES,
                comparisonMonth = null,
                comparisonYear = comparisonYear
            ),
            createComparisonMetric(
                currentDistance = currentDistance,
                previousDistance = previousDistance,
                metricType = ComparisonMetricType.GAS,
                comparisonMonth = null,
                comparisonYear = comparisonYear
            )
        ).filterNotNull()
        
        // Seleccionar una aleatoriamente
        return allComparisons.randomOrNull()
    }
    
    private fun createComparisonMetric(
        currentDistance: Double,
        previousDistance: Double,
        metricType: ComparisonMetricType,
        comparisonMonth: Int?,
        comparisonYear: Int
    ): ComparisonData? {
        if (previousDistance == 0.0) return null
        
        val (currentValue, previousValue, title, unit, icon) = when (metricType) {
            ComparisonMetricType.DISTANCE -> {
                val curr = currentDistance.roundToOneDecimal()
                val prev = previousDistance.roundToOneDecimal()
                val title = if (comparisonMonth != null) {
                    val monthNames = listOf(
                        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
                    )
                    "Distancia recorrida vs ${monthNames[comparisonMonth - 1]} $comparisonYear"
                } else {
                    "Distancia recorrida vs $comparisonYear"
                }
                Quintuple(curr, prev, title, "km", "📏")
            }
            ComparisonMetricType.CO2 -> {
                val curr = (currentDistance * 0.1).roundToOneDecimal()
                val prev = (previousDistance * 0.1).roundToOneDecimal()
                val title = if (comparisonMonth != null) {
                    val monthNames = listOf(
                        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
                    )
                    "CO₂ ahorrado vs ${monthNames[comparisonMonth - 1]} $comparisonYear"
                } else {
                    "CO₂ ahorrado vs $comparisonYear"
                }
                Quintuple(curr, prev, title, "kg CO₂", "🌱")
            }
            ComparisonMetricType.TREES -> {
                val curr = (currentDistance * 0.005).roundToOneDecimal()
                val prev = (previousDistance * 0.005).roundToOneDecimal()
                val title = if (comparisonMonth != null) {
                    val monthNames = listOf(
                        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
                    )
                    "Árboles salvados vs ${monthNames[comparisonMonth - 1]} $comparisonYear"
                } else {
                    "Árboles salvados vs $comparisonYear"
                }
                Quintuple(curr, prev, title, "árboles", "🌳")
            }
            ComparisonMetricType.GAS -> {
                val curr = (currentDistance * 0.04).roundToOneDecimal()
                val prev = (previousDistance * 0.04).roundToOneDecimal()
                val title = if (comparisonMonth != null) {
                    val monthNames = listOf(
                        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
                    )
                    "Gasolina ahorrada vs ${monthNames[comparisonMonth - 1]} $comparisonYear"
                } else {
                    "Gasolina ahorrada vs $comparisonYear"
                }
                Quintuple(curr, prev, title, "L", "⛽")
            }
        }
        
        val percentageChange = ((currentValue - previousValue) / previousValue * 100).roundToOneDecimal()
        
        return ComparisonData(
            currentValue = currentValue,
            previousValue = previousValue,
            percentageChange = kotlin.math.abs(percentageChange),
            isPositive = percentageChange >= 0,
            comparisonMonth = comparisonMonth,
            comparisonYear = comparisonYear,
            metricType = metricType,
            title = title,
            unit = unit,
            icon = icon
        )
    }
    
    // Helper data class para retornar múltiples valores
    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
    
    private suspend fun calculateNextAchievement(): NextAchievementData? {
        return try {
            val stats = recordRepository.getAchievementStats()
            
            // Calcular progreso de cada logro no desbloqueado
            data class AchievementProgress(
                val achievement: Achievement,
                val progress: Double,
                val isUnlocked: Boolean
            )
            
            val achievementsWithProgress = allAchievements
                .filter { it.level != AchievementLevel.SECRETO } // Excluir secretos
                .filter { it.requirementType != AchievementRequirementType.ALL_OTHERS } // Excluir "Maestro Absoluto"
                .map { achievement ->
                    val (isUnlocked, progress) = when (achievement.requirementType) {
                        AchievementRequirementType.DISTANCE -> {
                            val required = achievement.requiredDistance ?: 1.0
                            val unlocked = stats.totalDistance >= required
                            val prog = (stats.totalDistance / required * 100).coerceIn(0.0, 100.0)
                            unlocked to prog
                        }
                        AchievementRequirementType.TRIPS -> {
                            val required = achievement.requiredTrips ?: 1
                            val unlocked = stats.totalTrips >= required
                            val prog = (stats.totalTrips.toDouble() / required * 100).coerceIn(0.0, 100.0)
                            unlocked to prog
                        }
                        AchievementRequirementType.CONSECUTIVE_DAYS -> {
                            val required = achievement.requiredConsecutiveDays ?: 1
                            val unlocked = stats.consecutiveDays >= required
                            val prog = (stats.consecutiveDays.toDouble() / required * 100).coerceIn(0.0, 100.0)
                            unlocked to prog
                        }
                        AchievementRequirementType.UNIQUE_WEEKS -> {
                            val required = achievement.requiredUniqueWeeks ?: 1
                            val unlocked = stats.uniqueWeeks >= required
                            val prog = (stats.uniqueWeeks.toDouble() / required * 100).coerceIn(0.0, 100.0)
                            unlocked to prog
                        }
                        AchievementRequirementType.MAINTENANCE_COUNT -> {
                            val required = achievement.requiredMaintenanceCount ?: 1
                            val unlocked = stats.maintenanceCount >= required
                            val prog = (stats.maintenanceCount.toDouble() / required * 100).coerceIn(0.0, 100.0)
                            unlocked to prog
                        }
                        AchievementRequirementType.CO2_SAVED -> {
                            val required = achievement.requiredCO2Saved ?: 1.0
                            val unlocked = stats.co2Saved >= required
                            val prog = (stats.co2Saved / required * 100).coerceIn(0.0, 100.0)
                            unlocked to prog
                        }
                        AchievementRequirementType.UNIQUE_MONTHS -> {
                            val required = achievement.requiredUniqueMonths ?: 1
                            val unlocked = stats.uniqueMonths >= required
                            val prog = (stats.uniqueMonths.toDouble() / required * 100).coerceIn(0.0, 100.0)
                            unlocked to prog
                        }
                        AchievementRequirementType.CONSECUTIVE_MONTHS -> {
                            val required = achievement.requiredConsecutiveMonths ?: 1
                            val unlocked = stats.consecutiveMonths >= required
                            val prog = (stats.consecutiveMonths.toDouble() / required * 100).coerceIn(0.0, 100.0)
                            unlocked to prog
                        }
                        else -> false to 0.0
                    }
                    AchievementProgress(achievement, progress, isUnlocked)
                }
            
            // Encontrar el logro no desbloqueado con mayor progreso
            val nextAchievement = achievementsWithProgress
                .filter { !it.isUnlocked }
                .maxByOrNull { it.progress }
            
            nextAchievement?.let { achProg ->
                val ach = achProg.achievement
                val requirementText = when (ach.requirementType) {
                    AchievementRequirementType.DISTANCE -> "${ach.requiredDistance?.toInt()} km"
                    AchievementRequirementType.TRIPS -> "${ach.requiredTrips} viajes"
                    AchievementRequirementType.CONSECUTIVE_DAYS -> "${ach.requiredConsecutiveDays} días seguidos"
                    AchievementRequirementType.UNIQUE_WEEKS -> "${ach.requiredUniqueWeeks} semanas"
                    AchievementRequirementType.MAINTENANCE_COUNT -> "${ach.requiredMaintenanceCount} mantenimientos"
                    AchievementRequirementType.CO2_SAVED -> "${ach.requiredCO2Saved?.toInt()} kg CO2"
                    AchievementRequirementType.UNIQUE_MONTHS -> "${ach.requiredUniqueMonths} meses"
                    AchievementRequirementType.CONSECUTIVE_MONTHS -> "${ach.requiredConsecutiveMonths} meses seguidos"
                    else -> ""
                }
                
                NextAchievementData(
                    title = ach.title,
                    emoji = ach.emoji,
                    description = ach.description,
                    progress = (achProg.progress / 100.0).toFloat().coerceIn(0f, 1f),
                    requirementText = requirementText
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Genera una métrica aleatoria basada en la distancia actual y la comparativa histórica.
     */
    fun generateRandomInsight(
        currentDistanceKm: Double,
        comparison: ComparisonData?,
        periodName: String,
        // Nuevos parámetros opcionales (pásalos desde tu DB si los tienes, si no 0.0)
        rainKm: Double = 0.0,
        wetRoadKm: Double = 0.0,
        extremeKm: Double = 0.0
    ) {
        // 1. Filtrar métricas válidas (para no mostrar "0 km de lluvia")
        val validMetrics = InsightMetric.values().filter { metric ->
            when (metric) {
                InsightMetric.RAIN -> rainKm > 0.1
                InsightMetric.WET_ROAD -> wetRoadKm > 0.1
                InsightMetric.EXTREME -> extremeKm > 0.1
                else -> true // Las basadas en distancia total siempre son válidas si hay distancia
            }
        }

        // Si no hay ninguna válida (ej: usuario nuevo con 0km), no hacemos nada o default
        if (validMetrics.isEmpty()) return

        // 2. Elegir métrica al azar de las válidas
        val randomMetric = validMetrics.random()

        // 3. Determinar el Valor Actual
        val currentVal = when (randomMetric) {
            InsightMetric.RAIN -> rainKm
            InsightMetric.WET_ROAD -> wetRoadKm
            InsightMetric.EXTREME -> extremeKm
            else -> currentDistanceKm * randomMetric.factor // Caso normal (CO2, Gasolina...)
        }

        // 4. Calcular el Valor Previo (Estimación inversa)
        // NOTA: Para Lluvia/Extremo, si no guardas el histórico específico,
        // la comparación será aproximada basada en la tendencia general de la distancia.
        val prevVal = if (comparison != null) {
            val multiplier = 1 + (comparison.percentageChange / 100.0 * (if (comparison.isPositive) 1 else -1))
            if (multiplier > 0) {
                when (randomMetric) {
                    InsightMetric.RAIN, InsightMetric.WET_ROAD, InsightMetric.EXTREME -> {
                        // Para métricas específicas, estimamos basado en la tendencia de distancia
                        currentVal / multiplier
                    }
                    else -> {
                        val prevDistance = comparison.previousValue
                        prevDistance * randomMetric.factor
                    }
                }
            } else {
                0.0
            }
        } else {
            0.0
        }

        // 5. Calcular porcentaje de cambio
        val diff = currentVal - prevVal
        
        val percent = if (prevVal > 0.001) {
            (diff / prevVal) * 100
        } else if (currentVal > 0.001) {
            100.0 // Crecimiento infinito (de 0 a algo)
        } else {
            0.0   // Sin cambios (0 a 0)
        }

        // 6. Mensaje personalizado para las nuevas métricas
        // Podemos sobreescribir el label del periodo si queremos un mensaje más épico
        val finalPeriodLabel = if (randomMetric in listOf(InsightMetric.RAIN, InsightMetric.EXTREME)) {
            "vs $periodName • ¡Espíritu aventurero!" 
        } else {
            "vs $periodName"
        }

        _insightState.value = RandomInsightData(
            metric = randomMetric,
            periodLabel = finalPeriodLabel,
            currentValue = currentVal.roundToOneDecimal(),
            previousValue = prevVal.roundToOneDecimal(),
            percentageChange = abs(percent).roundToOneDecimal(),
            isPositive = diff >= 0
        )
    }
    
    /**
     * Calcula las distancias con condiciones climáticas específicas para un período
     */
    private suspend fun calculateWeatherDistances(
        routes: List<com.zipstats.app.model.Route>,
        currentMonth: Int?,
        currentYear: Int
    ): Triple<Double, Double, Double> {
        val filteredRoutes = routes.filter { route ->
            try {
                val routeDate = java.time.Instant.ofEpochMilli(route.startTime)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                
                val matchesMonth = currentMonth == null || routeDate.monthValue == currentMonth
                val matchesYear = routeDate.year == currentYear
                
                matchesMonth && matchesYear
            } catch (e: Exception) {
                false
            }
        }
        
        var rainDistance = 0.0
        var wetRoadDistance = 0.0
        var extremeDistance = 0.0
        
        filteredRoutes.forEach { route ->
            // Rutas con lluvia
            if (route.weatherHadRain == true) {
                rainDistance += route.totalDistance
            }
            
            // Calzada mojada (usando la misma lógica que RouteDetailDialog)
            if (checkWetRoadConditions(route)) {
                wetRoadDistance += route.totalDistance
            }
            
            // Clima extremo
            if (route.weatherHadExtremeConditions == true) {
                extremeDistance += route.totalDistance
            }
        }
        
        return Triple(rainDistance, wetRoadDistance, extremeDistance)
    }
    
    /**
     * Función auxiliar para verificar condiciones de calzada mojada
     */
    private fun checkWetRoadConditions(route: com.zipstats.app.model.Route): Boolean {
        // 1. EXCLUSIÓN: Si llovió durante la ruta, NO contamos como "Calzada Mojada"
        if (route.weatherHadRain == true) {
            return false
        }
        
        val isDay = route.weatherIsDay ?: true
        
        // Verificar si el cielo está despejado
        val isClearSky = route.weatherEmoji?.let { emoji ->
            emoji == "☀️" || emoji == "🌙"
        } ?: false
        
        // Calzada mojada considerando día/noche
        if (!isClearSky && route.weatherHumidity != null) {
            if (isDay) {
                if (route.weatherHumidity >= 90) return true
                if (route.weatherRainProbability != null && route.weatherRainProbability > 40) return true
            } else {
                if (route.weatherHumidity >= 85) return true
                if (route.weatherRainProbability != null && route.weatherRainProbability > 35) return true
            }
        }
        
        // Si hay precipitación máxima registrada pero no se detectó como "Lluvia activa"
        if (route.weatherMaxPrecipitation != null && route.weatherMaxPrecipitation > 0.1) {
            return true
        }
        
        return false
    }

    private fun Double.roundToOneDecimal(): Double {
        return (this * 10.0).roundToInt() / 10.0
    }
} 