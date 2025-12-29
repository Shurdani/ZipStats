package com.zipstats.app.ui.statistics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Water
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zipstats.app.model.Achievement
import com.zipstats.app.model.AchievementLevel
import com.zipstats.app.model.AchievementRequirementType
import com.zipstats.app.model.Scooter
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.RouteRepository
import com.zipstats.app.repository.UserRepository
import com.zipstats.app.repository.VehicleRepository
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

// CAUSAS ESPECÍFICAS DE CLIMA EXTREMO
enum class ExtremeCause(val label: String, val emoji: String) {
    NONE("Extremo", "⚠️"),
    WIND("Viento Fuerte", "💨"),
    GUSTS("Rachas de Viento", "🍃"),
    STORM("Tormenta", "⚡"),
    COLD("Helada", "❄️"),
    HEAT("Ola de Calor", "🔥")
}

// MODELO INTERNO PARA EL CÁLCULO DE CLIMA
data class WeatherStats(
    val rainKm: Double,
    val wetRoadKm: Double,
    val extremeKm: Double,
    val dominantExtremeCause: ExtremeCause // ¿Cuál fue la causa ganadora?
)

// Configuración de cada métrica (Icono, Color, Factor de conversión)
enum class InsightMetric(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val unit: String,
    val factor: Double // Factor para convertir KM a esta unidad (Solo se usa para los derivados del total)
) {
    // --- Métricas derivadas del Total (Factor > 0) ---
    DISTANCE("Distancia", Icons.AutoMirrored.Filled.DirectionsBike, Color(0xFF2979FF), "km", 1.0),
    
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
    
    // --- Estado de distancias con condiciones climáticas (compatibilidad) ---
    private val _weatherDistances = MutableStateFlow<Triple<Double, Double, Double>>(Triple(0.0, 0.0, 0.0))
    val weatherDistances: StateFlow<Triple<Double, Double, Double>> = _weatherDistances.asStateFlow()
    
    // --- Estado de estadísticas climáticas completas (nuevo sistema) ---
    private val _weatherStats = MutableStateFlow<WeatherStats>(WeatherStats(0.0, 0.0, 0.0, ExtremeCause.NONE))
    val weatherStats: StateFlow<WeatherStats> = _weatherStats.asStateFlow()
    
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
                        
                        // Filtrar rutas GPS por período para calcular estadísticas climáticas
                        val filteredGpsRoutes = allRoutes.filter { route ->
                            try {
                                val routeDate = java.time.Instant.ofEpochMilli(route.startTime)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                                
                                val matchesMonth = _selectedMonth.value == null || routeDate.monthValue == _selectedMonth.value
                                val matchesYear = routeDate.year == currentYear
                                
                                matchesMonth && matchesYear
                            } catch (e: Exception) {
                                false
                            }
                        }
                        
                        // Calcular estadísticas climáticas usando PROYECCIÓN HÍBRIDA
                        // Usa la distancia manual (fiable) + porcentajes del GPS (clima)
                        val manualDistance = _selectedMonth.value?.let { monthlyDistance } 
                            ?: (if (_selectedYear.value != null) yearlyDistance else totalDistance)
                        
                        val calculatedWeatherStats = calculateWeatherStats(manualDistance, filteredGpsRoutes)
                        
                        // Guardar estadísticas completas
                        _weatherStats.value = calculatedWeatherStats
                        
                        // Mantener compatibilidad con el código existente (para EcologicalImpactCard)
                        _weatherDistances.value = Triple(calculatedWeatherStats.rainKm, calculatedWeatherStats.wetRoadKm, calculatedWeatherStats.extremeKm)
                        
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
        val co2Saved = (stats.totalDistance * 0.15).toInt()
        val treesEquivalent = (stats.totalDistance * 0.005).toInt()
        val gasSaved = (stats.totalDistance * 0.07).toInt() // 0.07 litros de gasolina por km ahorrado (7L/100km)
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
        val co2Saved = (stats.monthlyDistance * 0.15).toInt()
        val treesEquivalent = (stats.monthlyDistance * 0.005).toInt()
        val gasSaved = (stats.monthlyDistance * 0.07).toInt()
        
        // Usar el mes y año seleccionados, o el actual si no hay selección
        val selectedMonth = (month ?: _selectedMonth.value ?: LocalDate.now().monthValue).coerceIn(1, 12)
        val selectedYear = year ?: _selectedYear.value ?: LocalDate.now().year
        
        // Verificar si es el mes actual (sin selección manual)
        // Si month y year son null (no hay selección manual), y el mes calculado es el actual, mostrar porcentajes
        val today = LocalDate.now()
        val hasManualSelection = (month != null || year != null)
        val isCurrentMonth = !hasManualSelection && 
                            selectedMonth == today.monthValue && selectedYear == today.year
        
        // Lista de nombres de meses en español
        val monthNames = listOf(
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        )
        val monthName = monthNames.getOrElse(selectedMonth - 1) { "Mes" }
        
        // Si es el mes actual, calcular porcentajes de variación
        val percentagesText = if (isCurrentMonth && stats.monthlyComparison != null) {
            val comparison = stats.monthlyComparison
            // Calcular porcentajes para todas las métricas basándonos en la distancia
            val currentDistance = stats.monthlyDistance
            val previousDistance = when (comparison.metricType) {
                ComparisonMetricType.DISTANCE -> comparison.previousValue
                ComparisonMetricType.CO2 -> comparison.previousValue / 0.15
                ComparisonMetricType.TREES -> comparison.previousValue / 0.005
                ComparisonMetricType.GAS -> comparison.previousValue / 0.07
            }
            
            if (previousDistance > 0.1) {
                val distancePercent = ((currentDistance - previousDistance) / previousDistance * 100).roundToInt()
                val co2Percent = distancePercent // Mismo porcentaje porque es proporcional
                val treesPercent = distancePercent // Mismo porcentaje porque es proporcional
                val gasPercent = distancePercent // Mismo porcentaje porque es proporcional
                
                val distanceSign = if (distancePercent >= 0) "+" else ""
                val co2Sign = if (co2Percent >= 0) "+" else ""
                val treesSign = if (treesPercent >= 0) "+" else ""
                val gasSign = if (gasPercent >= 0) "+" else ""
                
                """

📊 Total recorrido: ${stats.monthlyDistance} km ($distanceSign$distancePercent%)
🌱 CO₂ ahorrado: $co2Saved kg ($co2Sign$co2Percent%)
🌳 Árboles: $treesEquivalent ($treesSign$treesPercent%)
⛽ Gasolina ahorrada: $gasSaved L ($gasSign$gasPercent%)""".trimIndent()
            } else {
                """

📊 Total recorrido: ${stats.monthlyDistance} km
🌱 CO₂ ahorrado: $co2Saved kg
🌳 Árboles: $treesEquivalent
⛽ Gasolina ahorrada: $gasSaved L""".trimIndent()
            }
        } else {
            // Si no es el mes actual o no hay comparación, mostrar sin porcentajes
            """

📊 Total recorrido: ${stats.monthlyDistance} km
🌱 CO₂ ahorrado: $co2Saved kg
🌳 Árboles: $treesEquivalent
⛽ Gasolina ahorrada: $gasSaved L""".trimIndent()
        }
        
        return """
 Estadísticas de $monthName $selectedYear de ${userName.value} $percentagesText
📈 Promedio por registro: ${stats.monthlyAverageDistance} km
🏆 Mejor registro: ${stats.monthlyMaxDistance} km
📝 Total de registros: ${stats.monthlyRecords}
#ZipStats""".trimIndent()
    }

    fun getYearlyShareText(stats: StatisticsUiState.Success, year: Int? = null): String {
        val co2Saved = (stats.yearlyDistance * 0.15).toInt()
        val treesEquivalent = (stats.yearlyDistance * 0.005).toInt()
        val gasSaved = (stats.yearlyDistance * 0.07).toInt()
        
        // Usar el año seleccionado, o el actual si no hay selección
        val selectedYear = year ?: _selectedYear.value ?: LocalDate.now().year
        
        // Verificar si es el año actual (sin selección manual)
        // Si year es null (no hay selección manual), y el año calculado es el actual, mostrar porcentajes
        val today = LocalDate.now()
        val hasManualSelection = (year != null)
        val isCurrentYear = !hasManualSelection && selectedYear == today.year
        
        // Si es el año actual, calcular porcentajes de variación
        val percentagesText = if (isCurrentYear && stats.yearlyComparison != null) {
            val comparison = stats.yearlyComparison
            // Calcular porcentajes para todas las métricas basándonos en la distancia
            val currentDistance = stats.yearlyDistance
            val previousDistance = when (comparison.metricType) {
                ComparisonMetricType.DISTANCE -> comparison.previousValue
                ComparisonMetricType.CO2 -> comparison.previousValue / 0.15
                ComparisonMetricType.TREES -> comparison.previousValue / 0.005
                ComparisonMetricType.GAS -> comparison.previousValue / 0.07
            }
            
            if (previousDistance > 0.1) {
                val distancePercent = ((currentDistance - previousDistance) / previousDistance * 100).roundToInt()
                val co2Percent = distancePercent // Mismo porcentaje porque es proporcional
                val treesPercent = distancePercent // Mismo porcentaje porque es proporcional
                val gasPercent = distancePercent // Mismo porcentaje porque es proporcional
                
                val distanceSign = if (distancePercent >= 0) "+" else ""
                val co2Sign = if (co2Percent >= 0) "+" else ""
                val treesSign = if (treesPercent >= 0) "+" else ""
                val gasSign = if (gasPercent >= 0) "+" else ""
                
                """

📊 Total recorrido: ${stats.yearlyDistance} km ($distanceSign$distancePercent%)
🌱 CO₂ ahorrado: $co2Saved kg ($co2Sign$co2Percent%)
🌳 Árboles: $treesEquivalent ($treesSign$treesPercent%)
⛽ Gasolina ahorrada: $gasSaved L ($gasSign$gasPercent%)""".trimIndent()
            } else {
                """

📊 Total recorrido: ${stats.yearlyDistance} km
🌱 CO₂ ahorrado: $co2Saved kg
🌳 Árboles: $treesEquivalent
⛽ Gasolina ahorrada: $gasSaved L""".trimIndent()
            }
        } else {
            // Si no es el año actual o no hay comparación, mostrar sin porcentajes
            """

📊 Total recorrido: ${stats.yearlyDistance} km
🌱 CO₂ ahorrado: $co2Saved kg
🌳 Árboles: $treesEquivalent
⛽ Gasolina ahorrada: $gasSaved L""".trimIndent()
        }
        
        return """
 Estadísticas de $selectedYear de ${userName.value} $percentagesText
📈 Promedio por registro: ${stats.yearlyAverageDistance} km
🏆 Mejor registro: ${stats.yearlyMaxDistance} km
📝 Total de registros: ${stats.yearlyRecords}
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
        
        // Si es el mes actual, comparar hasta hoy. Si es un mes pasado, comparar el mes completo
        val currentDayOfMonth = if (isCurrentMonth) {
            today.dayOfMonth
        } else {
            // Obtener el último día del mes seleccionado
            LocalDate.of(currentYear, currentMonth, 1).lengthOfMonth()
        }
        
        // Obtener registros del mes/año seleccionado (hasta el día correspondiente)
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
        
        // Calcular el mes anterior (no el mismo mes del año anterior)
        val previousMonthDate = LocalDate.of(currentYear, currentMonth, 1).minusMonths(1)
        val previousMonth = previousMonthDate.monthValue
        val previousYear = previousMonthDate.year
        
        // IMPORTANTE: Para una comparación justa, siempre comparar períodos equivalentes:
        // - Si es el mes actual: comparar hasta hoy vs mes anterior hasta el mismo día
        // - Si es un mes pasado: comparar mes completo vs mes anterior completo (hasta el mismo día)
        val previousDayOfMonth = currentDayOfMonth.coerceAtMost(
            LocalDate.of(previousYear, previousMonth, 1).lengthOfMonth()
        )
        
        val previousMonthRecords = records.filter {
            try {
                val recordDate = LocalDate.parse(it.fecha)
                recordDate.monthValue == previousMonth && 
                recordDate.year == previousYear &&
                recordDate.dayOfMonth <= previousDayOfMonth
            } catch (e: Exception) {
                false
            }
        }
        
        val previousDistance = previousMonthRecords.sumOf { it.diferencia }
        
        // Solo comparar si hay datos del mes anterior (con un mínimo razonable para evitar porcentajes absurdos)
        // Si el mes anterior tiene menos de 0.1 km, no hacer comparación
        if (previousMonthRecords.isEmpty() || previousDistance < 0.1) return null
        
        // Validación adicional: si el porcentaje sería mayor a 10000%, probablemente hay un error
        // (por ejemplo, mes anterior con 0.1 km y mes actual con 10 km = 9900%)
        val estimatedPercentage = ((currentDistance - previousDistance) / previousDistance * 100)
        if (estimatedPercentage > 10000) {
            android.util.Log.w("MonthlyComparison", 
                "Porcentaje extremo detectado (${estimatedPercentage.roundToOneDecimal()}%). " +
                "Posible error en los datos. Mes actual: ${currentDistance.roundToOneDecimal()} km, " +
                "Mes anterior: ${previousDistance.roundToOneDecimal()} km"
            )
            // Aún así retornamos la comparación, pero el log ayudará a debuggear
        }
        
        // Debug: Log detallado para verificar los cálculos
        val diff = currentDistance - previousDistance
        val percentage = ((diff / previousDistance) * 100).roundToOneDecimal()
        
        android.util.Log.d("MonthlyComparison", 
            "═══════════════════════════════════════════════════════\n" +
            "COMPARACIÓN MENSUAL - DEBUG\n" +
            "═══════════════════════════════════════════════════════\n" +
            "MES SELECCIONADO:\n" +
            "  Mes: $currentMonth/$currentYear\n" +
            "  Día límite: $currentDayOfMonth (${if (isCurrentMonth) "hasta hoy" else "mes completo"})\n" +
            "  Registros encontrados: ${currentMonthRecords.size}\n" +
            "  Distancia total: ${currentDistance.roundToOneDecimal()} km\n" +
            "  Fechas de registros: ${currentMonthRecords.map { it.fecha }.take(5).joinToString(", ")}${if (currentMonthRecords.size > 5) "..." else ""}\n" +
            "\n" +
            "MES ANTERIOR:\n" +
            "  Mes: $previousMonth/$previousYear\n" +
            "  Día límite: $previousDayOfMonth\n" +
            "  Registros encontrados: ${previousMonthRecords.size}\n" +
            "  Distancia total: ${previousDistance.roundToOneDecimal()} km\n" +
            "  Fechas de registros: ${previousMonthRecords.map { it.fecha }.take(5).joinToString(", ")}${if (previousMonthRecords.size > 5) "..." else ""}\n" +
            "\n" +
            "RESULTADO:\n" +
            "  Diferencia: ${diff.roundToOneDecimal()} km\n" +
            "  Porcentaje: $percentage%\n" +
            "  Es positivo: ${diff >= 0}\n" +
            "═══════════════════════════════════════════════════════"
        )
        
        // Calcular todas las métricas posibles
        val allComparisons = listOf(
            createComparisonMetric(
                currentDistance = currentDistance,
                previousDistance = previousDistance,
                metricType = ComparisonMetricType.DISTANCE,
                comparisonMonth = previousMonth,
                comparisonYear = previousYear
            ),
            createComparisonMetric(
                currentDistance = currentDistance,
                previousDistance = previousDistance,
                metricType = ComparisonMetricType.CO2,
                comparisonMonth = previousMonth,
                comparisonYear = previousYear
            ),
            createComparisonMetric(
                currentDistance = currentDistance,
                previousDistance = previousDistance,
                metricType = ComparisonMetricType.TREES,
                comparisonMonth = previousMonth,
                comparisonYear = previousYear
            ),
            createComparisonMetric(
                currentDistance = currentDistance,
                previousDistance = previousDistance,
                metricType = ComparisonMetricType.GAS,
                comparisonMonth = previousMonth,
                comparisonYear = previousYear
            )
        ).filterNotNull()
        
        // Seleccionar una aleatoriamente
        return allComparisons.randomOrNull()
    }
    
    private fun calculateYearlyComparison(records: List<com.zipstats.app.model.Record>, currentYear: Int): ComparisonData? {
        val today = LocalDate.now()
        val isCurrentYear = currentYear == today.year
        
        // Si es el año actual, comparar hasta hoy. Si es un año pasado, comparar el año completo
        val currentDayOfYear = if (isCurrentYear) {
            today.dayOfYear
        } else {
            // Año pasado: usar el último día del año (365 o 366 según si es bisiesto)
            if (java.time.Year.of(currentYear).isLeap) 366 else 365
        }
        
        // Obtener registros del año seleccionado (hasta el día correspondiente)
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
            
            // Para una comparación justa:
            // - Si es el año actual: comparar hasta hoy vs año anterior hasta el mismo día
            // - Si es un año pasado: comparar año completo vs año anterior completo (hasta el mismo día)
            val previousDayOfYear = if (isCurrentYear) {
                // Año actual: comparar hasta el mismo día del año anterior
                currentDayOfYear.coerceAtMost(
                    if (java.time.Year.of(yearToCheck).isLeap) 366 else 365
                )
            } else {
                // Año pasado: comparar hasta el mismo día del año anterior
                currentDayOfYear.coerceAtMost(
                    if (java.time.Year.of(yearToCheck).isLeap) 366 else 365
                )
            }
            
            val previousYearRecords = records.filter {
                try {
                    val recordDate = LocalDate.parse(it.fecha)
                    recordDate.year == yearToCheck &&
                    recordDate.dayOfYear <= previousDayOfYear
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
        currentDistance: Double, // Valor original preciso
        previousDistance: Double, // Valor original preciso
        metricType: ComparisonMetricType,
        comparisonMonth: Int?,
        comparisonYear: Int
    ): ComparisonData? {
        
        // 1. Calcular valores RAW (sin redondear) para precisión matemática
        val (rawCurrent, rawPrevious, unit, icon) = when (metricType) {
            ComparisonMetricType.DISTANCE -> Quadruple(currentDistance, previousDistance, "km", "📏")
            ComparisonMetricType.CO2 -> Quadruple(currentDistance * 0.15, previousDistance * 0.15, "kg CO₂", "🌱")
            ComparisonMetricType.TREES -> Quadruple(currentDistance * 0.005, previousDistance * 0.005, "árboles", "🌳")
            ComparisonMetricType.GAS -> Quadruple(currentDistance * 0.07, previousDistance * 0.07, "L", "⛽")
        }

        // Si el valor anterior es insignificante, no podemos comparar porcentualmente de forma justa
        if (rawPrevious < 0.001) return null

        // 2. Calcular porcentaje con los valores PRECISOS
        val diff = rawCurrent - rawPrevious
        val rawPercentage = (diff / rawPrevious) * 100
        
        // 3. Redondear SOLO para visualización
        val displayCurrent = rawCurrent.roundToOneDecimal()
        val displayPrevious = rawPrevious.roundToOneDecimal()
        val displayPercentage = rawPercentage.roundToOneDecimal()

        // 4. Generar título
        val title = if (comparisonMonth != null) {
            val monthNames = listOf(
                "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
            )
            val metricName = when(metricType) {
                ComparisonMetricType.DISTANCE -> "Distancia recorrida"
                ComparisonMetricType.CO2 -> "CO₂ ahorrado"
                ComparisonMetricType.TREES -> "Árboles salvados"
                ComparisonMetricType.GAS -> "Gasolina ahorrada"
            }
            "$metricName vs ${monthNames[comparisonMonth - 1]} $comparisonYear"
        } else {
            val metricName = when(metricType) {
                ComparisonMetricType.DISTANCE -> "Distancia recorrida"
                ComparisonMetricType.CO2 -> "CO₂ ahorrado"
                ComparisonMetricType.TREES -> "Árboles salvados"
                ComparisonMetricType.GAS -> "Gasolina ahorrada"
            }
            "$metricName vs $comparisonYear"
        }

        return ComparisonData(
            currentValue = displayCurrent,
            previousValue = displayPrevious,
            percentageChange = kotlin.math.abs(displayPercentage),
            isPositive = diff >= 0, // Usamos la diferencia real para saber si es positivo
            comparisonMonth = comparisonMonth,
            comparisonYear = comparisonYear,
            metricType = metricType,
            title = title,
            unit = unit,
            icon = icon
        )
    }
    
    // Helper data class para retornar múltiples valores (simplificado a 4)
    private data class Quadruple<A, B, C, D>(
        val current: A,
        val previous: B,
        val unit: C,
        val icon: D
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
     * Usa LOTERÍA PONDERADA para priorizar eventos climáticos importantes.
     */
    fun generateRandomInsight(
        currentDistanceKm: Double,
        comparison: ComparisonData?,
        periodName: String,
        weatherStats: WeatherStats
    ) {
        // 1. LLENAR LA BOLSA (Lotería Ponderada)
        val lotteryBowl = mutableListOf<InsightMetric>()
        InsightMetric.values().forEach { metric ->
            val valueToCheck = when (metric) {
                InsightMetric.RAIN -> weatherStats.rainKm
                InsightMetric.WET_ROAD -> weatherStats.wetRoadKm
                InsightMetric.EXTREME -> weatherStats.extremeKm
                else -> currentDistanceKm
            }
            val weight = calculateWeight(metric, valueToCheck, currentDistanceKm)
            repeat(weight) { lotteryBowl.add(metric) }
        }

        if (lotteryBowl.isEmpty()) return

        // 2. ELEGIR GANADOR
        val selectedMetric = lotteryBowl.random()

        // 3. CALCULAR VALOR ACTUAL
        val currentVal = when (selectedMetric) {
            InsightMetric.RAIN -> weatherStats.rainKm
            InsightMetric.WET_ROAD -> weatherStats.wetRoadKm
            InsightMetric.EXTREME -> weatherStats.extremeKm
            else -> currentDistanceKm * selectedMetric.factor
        }

        // 4. CALCULAR PREVIO (Reversión aproximada)
        val prevVal = if (comparison != null) {
            when (selectedMetric) {
                InsightMetric.RAIN, InsightMetric.WET_ROAD, InsightMetric.EXTREME -> {
                    // Para métricas específicas, estimamos basado en la tendencia de distancia
                    val multiplier = if (comparison.isPositive) {
                        1 + (comparison.percentageChange / 100.0)
                    } else {
                        1 - (comparison.percentageChange / 100.0)
                    }
                    if (multiplier > 0) {
                        currentVal / multiplier
                    } else {
                        0.0
                    }
                }
                else -> {
                    // Para métricas basadas en distancia, convertimos comparison.previousValue a distancia
                    val prevDistance = when (comparison.metricType) {
                        ComparisonMetricType.DISTANCE -> comparison.previousValue
                        ComparisonMetricType.CO2 -> comparison.previousValue / 0.15
                        ComparisonMetricType.TREES -> comparison.previousValue / 0.005
                        ComparisonMetricType.GAS -> comparison.previousValue / 0.07
                    }
                    prevDistance * selectedMetric.factor
                }
            }
        } else {
            0.0
        }

        // 5. Calcular porcentaje de cambio
        val diff = currentVal - prevVal
        
        val percent = if (prevVal > 0.001) {
            kotlin.math.abs((diff / prevVal) * 100)
        } else if (currentVal > 0.001) {
            100.0 // Crecimiento infinito (de 0 a algo)
        } else {
            0.0   // Sin cambios (0 a 0)
        }

        // 6. TEXTOS PERSONALIZADOS
        val finalPeriodLabel = when (selectedMetric) {
            InsightMetric.RAIN -> "vs $periodName"
            InsightMetric.WET_ROAD -> "vs $periodName"
            InsightMetric.EXTREME -> {
                val cause = weatherStats.dominantExtremeCause
                if (cause != ExtremeCause.NONE) {
                    "vs $periodName • Alerta: ${cause.label} ${cause.emoji}"
                } else {
                    "vs $periodName"
                }
            }
            else -> "vs $periodName"
        }

        _insightState.value = RandomInsightData(
            metric = selectedMetric,
            periodLabel = finalPeriodLabel,
            currentValue = currentVal.roundToOneDecimal(),
            previousValue = prevVal.roundToOneDecimal(),
            percentageChange = percent.roundToOneDecimal(),
            isPositive = diff >= 0
        )
    }
    
    /**
     * Calcula las estadísticas climáticas:
     * - Lluvia y Calzada Mojada: Lee directamente de las rutas guardadas (sin proyección)
     * - Clima Extremo: Usa proyección híbrida (distancia manual + porcentajes GPS)
     */
    private fun calculateWeatherStats(
        manualTotalDistance: Double,
        gpsRoutes: List<com.zipstats.app.model.Route>
    ): WeatherStats {
        // Si no hay rutas GPS, devolvemos 0 en todo
        if (gpsRoutes.isEmpty()) {
            return WeatherStats(0.0, 0.0, 0.0, ExtremeCause.NONE)
        }

        var rainKm = 0.0
        var wetRoadKm = 0.0
        var gpsExtremeKm = 0.0
        val gpsTotalDistance = gpsRoutes.sumOf { it.totalDistance }
        
        // Mapa para contar qué causa extrema es la más frecuente
        val extremeCauseDistances = mutableMapOf<ExtremeCause, Double>().withDefault { 0.0 }

        gpsRoutes.forEach { route ->
            val dist = route.totalDistance

            // 1. LLUVIA: Leer directamente de las rutas guardadas (misma lógica que RouteDetailDialog)
            // Usa el flag weatherHadRain que se guardó durante la ruta
            if (route.weatherHadRain == true) {
                rainKm += dist
            }

            // 2. CALZADA MOJADA: Leer directamente usando la misma función que RouteDetailDialog
            // Esta función ya excluye rutas con lluvia activa
            if (checkWetRoadConditions(route)) {
                wetRoadKm += dist
            }

            // 3. EXTREMO: Usar proyección híbrida (porque puede haber rutas sin GPS completo)
            // Detectar la causa específica de condiciones extremas
            val cause = detectExtremeCause(route)
            if (cause != ExtremeCause.NONE) {
                gpsExtremeKm += dist
                extremeCauseDistances[cause] = extremeCauseDistances.getValue(cause) + dist
            }
        }

        // Para clima extremo, proyectar ratios GPS sobre la Distancia Manual (La fiable)
        // Solo si hay rutas GPS con distancia significativa
        val extremeKm = if (gpsTotalDistance > 0.1) {
            val extremeRatio = gpsExtremeKm / gpsTotalDistance
            manualTotalDistance * extremeRatio
        } else {
            0.0
        }

        val dominantCause = extremeCauseDistances.maxByOrNull { it.value }?.key ?: ExtremeCause.NONE

        return WeatherStats(
            rainKm = rainKm, // Directo de rutas guardadas
            wetRoadKm = wetRoadKm, // Directo de rutas guardadas
            extremeKm = extremeKm, // Proyección híbrida
            dominantExtremeCause = dominantCause
        )
    }
    
    /**
     * Detecta la causa específica de condiciones extremas.
     * PRIORIDAD: Lee directamente de weatherExtremeReason si existe (rutas nuevas),
     * si no, usa la misma lógica que RouteDetailDialog (rutas antiguas).
     */
    private fun detectExtremeCause(route: com.zipstats.app.model.Route): ExtremeCause {
        // Si no hay condiciones extremas, retornar NONE
        if (route.weatherHadExtremeConditions != true) {
            // Verificar si hay condiciones extremas por valores guardados (compatibilidad con rutas antiguas)
            val hasExtreme = (route.weatherWindSpeed != null && route.weatherWindSpeed > 40) ||
                            (route.weatherWindGusts != null && route.weatherWindGusts > 60) ||
                            (route.weatherTemperature != null && (route.weatherTemperature < 0 || route.weatherTemperature > 35)) ||
                            (route.weatherIsDay == true && route.weatherUvIndex != null && route.weatherUvIndex > 8) ||
                            (route.weatherEmoji?.let { it.contains("⛈") || it.contains("⚡") } == true) ||
                            (route.weatherDescription?.let { desc ->
                                desc.contains("Tormenta", ignoreCase = true) ||
                                desc.contains("granizo", ignoreCase = true) ||
                                desc.contains("rayo", ignoreCase = true)
                            } == true)
            
            if (!hasExtreme) return ExtremeCause.NONE
        }
        
        // 🔥 PRIORIDAD 1: Leer directamente de weatherExtremeReason si existe (rutas nuevas)
        route.weatherExtremeReason?.let { reason ->
            return when (reason.uppercase()) {
                "STORM", "TORMENTA" -> ExtremeCause.STORM
                "GUSTS", "RACHAS" -> ExtremeCause.GUSTS
                "WIND", "VIENTO" -> ExtremeCause.WIND
                "COLD", "FRÍO", "HELADA" -> ExtremeCause.COLD
                "HEAT", "CALOR" -> ExtremeCause.HEAT
                else -> ExtremeCause.NONE
            }
        }
        
        // 🔥 PRIORIDAD 2: Si no hay razón guardada, detectar usando la misma lógica que RouteDetailDialog
        // (Para compatibilidad con rutas antiguas)
        
        // 1. Tormenta (prioridad máxima)
        val isStorm = route.weatherEmoji?.let { emoji ->
            emoji.contains("⛈") || emoji.contains("⚡")
        } ?: false
        
        val isStormByDescription = route.weatherDescription?.let { desc ->
            desc.contains("Tormenta", ignoreCase = true) ||
            desc.contains("granizo", ignoreCase = true) ||
            desc.contains("rayo", ignoreCase = true)
        } ?: false
        
        if (isStorm || isStormByDescription) {
            return ExtremeCause.STORM
        }
        
        // 2. Rachas de viento muy fuertes (>60 km/h) - prioridad sobre viento normal
        if (route.weatherWindGusts != null && route.weatherWindGusts > 60) {
            return ExtremeCause.GUSTS
        }
        
        // 3. Viento fuerte (>40 km/h)
        if (route.weatherWindSpeed != null && route.weatherWindSpeed > 40) {
            return ExtremeCause.WIND
        }
        
        // 4. Temperatura extrema
        if (route.weatherTemperature != null) {
            if (route.weatherTemperature < 0) {
                return ExtremeCause.COLD
            }
            if (route.weatherTemperature > 35) {
                return ExtremeCause.HEAT
            }
        }
        
        // 5. Índice UV muy alto (>8) - solo de día (se considera como calor)
        if (route.weatherIsDay == true && route.weatherUvIndex != null && route.weatherUvIndex > 8) {
            return ExtremeCause.HEAT
        }
        
        // Si llegamos aquí, hay condiciones extremas pero no identificamos la causa específica
        return ExtremeCause.NONE
    }
    
    /**
     * Infiere el código de clima desde el emoji (para rutas antiguas sin weatherCode)
     */
    private fun inferWeatherCodeFromEmoji(emoji: String): Int {
        return when {
            emoji.contains("☀️") || emoji.contains("🌙") -> 0
            emoji.contains("🌤️") || emoji.contains("☁️🌙") -> 1
            emoji.contains("☁️") -> 3
            emoji.contains("🌫️") -> 45
            emoji.contains("🌦️") -> 61
            emoji.contains("🌧️") -> 63
            emoji.contains("🥶") -> 56
            emoji.contains("❄️") -> 71
            emoji.contains("⚡") -> 95
            emoji.contains("⛈️") -> 96
            else -> -1 // Desconocido
        }
    }
    
    /**
     * Calcula el peso de una métrica para la lotería ponderada
     * Prioriza mostrar tarjetas de clima si hubo eventos importantes
     */
    private fun calculateWeight(metric: InsightMetric, value: Double, totalDistance: Double): Int {
        if (value < 0.1) return 0 // Si no hay dato, descartada

        val percentage = if (totalDistance > 0) (value / totalDistance) * 100 else 0.0

        return when (metric) {
            InsightMetric.RAIN -> if (percentage > 20) 10 else if (value > 5.0) 5 else 1
            InsightMetric.WET_ROAD -> if (percentage > 30) 8 else 1
            InsightMetric.EXTREME -> if (value > 0.5) 25 else 0 // ¡Prioridad MÁXIMA si ocurre!
            else -> 3 // Métricas estándar tienen peso normal
        }
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