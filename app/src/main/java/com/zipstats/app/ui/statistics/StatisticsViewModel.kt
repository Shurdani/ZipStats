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
    SNOW("Nieve", "❄️"),
    COLD("Helada", "🥶"),
    HEAT("Ola de Calor", "🔥"),
    VISIBILITY("Visibilidad Reducida", "🌫️")
}

// MODELO INTERNO PARA EL CÁLCULO DE CLIMA
data class WeatherStats(
    val rainKm: Double,
    val wetRoadKm: Double,
    val extremeKm: Double,
    val gpsTotalDistance: Double = 0.0, // Distancia total de rutas GPS (para contexto)
    val manualTotalDistance: Double = 0.0 // Distancia total de registros manuales (para contexto)
) {
    /**
     * Porcentaje de cobertura: qué porcentaje de la distancia manual está cubierta por rutas GPS
     * Útil para mostrar al usuario qué tan representativas son las estadísticas de clima
     */
    val coveragePercentage: Double
        get() = if (manualTotalDistance > 0.0) {
            (gpsTotalDistance / manualTotalDistance * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }
    
    /**
     * Indica si hay suficiente cobertura para mostrar estadísticas de clima
     * Se considera suficiente si hay al menos una ruta GPS con datos de clima
     */
    val hasClimateData: Boolean
        get() = gpsTotalDistance > 0.0 && (rainKm > 0.0 || wetRoadKm > 0.0 || extremeKm > 0.0)
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
    private val _weatherStats = MutableStateFlow<WeatherStats>(WeatherStats(0.0, 0.0, 0.0))
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
                        // 🔥 CORRECCIÓN: Usar la misma lógica de filtrado que los registros manuales
                        // para asegurar consistencia entre estadísticas de distancia y clima
                        val filteredGpsRoutes = allRoutes.filter { route ->
                            try {
                                val routeDate = java.time.Instant.ofEpochMilli(route.startTime)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                                
                                // Si hay mes seleccionado, filtrar por mes y año
                                // Si solo hay año seleccionado, filtrar solo por año
                                // Si no hay selección, usar el mes y año actuales
                                val targetMonth = _selectedMonth.value
                                val targetYear = _selectedYear.value ?: currentYear
                                
                                val matchesMonth = targetMonth == null || routeDate.monthValue == targetMonth
                                val matchesYear = routeDate.year == targetYear
                                
                                matchesMonth && matchesYear
                            } catch (e: Exception) {
                                false
                            }
                        }
                        
                        // Calcular estadísticas climáticas
                        // 🔥 IMPORTANTE: La distancia GPS SOLO se usa para las tarjetas de clima
                        // El resto de cálculos (CO2, árboles, gasolina, logros) usan la distancia de registros manuales
                        // La distancia manual se pasa solo para contexto, pero los cálculos de clima usan directamente
                        // la distancia real de las rutas GPS (sin proyección)
                        val manualDistance = when {
                            _selectedMonth.value != null -> monthlyDistance // Mes seleccionado: usar distancia mensual
                            _selectedYear.value != null -> yearlyDistance // Solo año seleccionado: usar distancia anual
                            else -> totalDistance // Sin selección: usar distancia total
                        }
                        
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

    suspend fun getShareText(stats: StatisticsUiState.Success): String {
        val co2Saved = (stats.totalDistance * 0.15).toInt()
        val treesEquivalent = (stats.totalDistance * 0.005).toInt()
        val gasSaved = (stats.totalDistance * 0.07).toInt() // 0.07 litros de gasolina por km ahorrado (7L/100km)
        val topScooters = stats.scooterStats.sortedByDescending { it.totalKilometers }.take(2)
        
        // Contar rutas con condiciones climáticas (sin filtro de mes/año para "Todo")
        val (rainRoutes, wetRoadRoutes, extremeRoutes) = countWeatherRoutes(null, null)
        
        val medals = listOf("🥇", "🥈")
        val scooterTexts = topScooters.mapIndexed { index, scooter ->
            "${medals[index]} ${scooter.model}: ${scooter.totalKilometers} km"
        }
        
        // Construir texto de métricas meteorológicas solo para valores > 0
        val weatherLines = mutableListOf<String>()
        if (rainRoutes > 0) {
            weatherLines.add("🌧️ Rutas con lluvia: $rainRoutes")
        }
        if (wetRoadRoutes > 0) {
            weatherLines.add("💧 Rutas con calzada mojada: $wetRoadRoutes")
        }
        if (extremeRoutes > 0) {
            weatherLines.add("⚠️ Rutas con condiciones extremas: $extremeRoutes")
        }
        val weatherText = if (weatherLines.isNotEmpty()) {
            "\n${weatherLines.joinToString("\n")}"
        } else {
            ""
        }
        
        return """ Estadísticas totales de ${userName.value} 

📊 Total recorrido: ${stats.totalDistance} km
🌱 CO₂ ahorrado: $co2Saved kg ≈ $treesEquivalent árboles 🌳
⛽ Gasolina ahorrada: $gasSaved Litros$weatherText
🏆 Top Vehículos:
${scooterTexts.joinToString("\n")}
#ZipStats""".trimIndent()
    }

    suspend fun getMonthlyShareText(stats: StatisticsUiState.Success, month: Int? = null, year: Int? = null): String {
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
                val distanceSign = if (distancePercent >= 0) "+" else ""
                
                """

📊 Total recorrido: ${stats.monthlyDistance} km ($distanceSign$distancePercent%)
🌱 CO₂ ahorrado: $co2Saved kg
🌳 Árboles: $treesEquivalent
⛽ Gasolina ahorrada: $gasSaved L""".trimIndent()
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
        
        // Contar rutas con condiciones climáticas para este mes
        val (rainRoutes, wetRoadRoutes, extremeRoutes) = countWeatherRoutes(selectedMonth, selectedYear)
        
        // Construir texto de métricas meteorológicas solo para valores > 0
        val weatherLines = mutableListOf<String>()
        if (rainRoutes > 0) {
            weatherLines.add("🌧️ Rutas con lluvia: $rainRoutes")
        }
        if (wetRoadRoutes > 0) {
            weatherLines.add("💧 Rutas con calzada mojada: $wetRoadRoutes")
        }
        if (extremeRoutes > 0) {
            weatherLines.add("⚠️ Rutas con condiciones extremas: $extremeRoutes")
        }
        val weatherText = if (weatherLines.isNotEmpty()) {
            "\n${weatherLines.joinToString("\n")}"
        } else {
            ""
        }
        
        return """
 Estadísticas de $monthName $selectedYear de ${userName.value} $percentagesText$weatherText
#ZipStats""".trimIndent()
    }

    suspend fun getYearlyShareText(stats: StatisticsUiState.Success, year: Int? = null): String {
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
                val distanceSign = if (distancePercent >= 0) "+" else ""
                
                """

📊 Total recorrido: ${stats.yearlyDistance} km ($distanceSign$distancePercent%)
🌱 CO₂ ahorrado: $co2Saved kg
🌳 Árboles: $treesEquivalent
⛽ Gasolina ahorrada: $gasSaved L""".trimIndent()
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
        
        // Contar rutas con condiciones climáticas para este año
        val (rainRoutes, wetRoadRoutes, extremeRoutes) = countWeatherRoutes(null, selectedYear)
        
        // Construir texto de métricas meteorológicas solo para valores > 0
        val weatherLines = mutableListOf<String>()
        if (rainRoutes > 0) {
            weatherLines.add("🌧️ Rutas con lluvia: $rainRoutes")
        }
        if (wetRoadRoutes > 0) {
            weatherLines.add("💧 Rutas con calzada mojada: $wetRoadRoutes")
        }
        if (extremeRoutes > 0) {
            weatherLines.add("⚠️ Rutas con condiciones extremas: $extremeRoutes")
        }
        val weatherText = if (weatherLines.isNotEmpty()) {
            "\n${weatherLines.joinToString("\n")}"
        } else {
            ""
        }
        
        return """
 Estadísticas de $selectedYear de ${userName.value} $percentagesText$weatherText
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
        // 🔥 CORRECCIÓN: Solo incluir métricas de clima si hay datos de clima disponibles
        val lotteryBowl = mutableListOf<InsightMetric>()
        InsightMetric.values().forEach { metric ->
            // Para métricas de clima, verificar si hay datos disponibles
            val hasData = when (metric) {
                InsightMetric.RAIN -> weatherStats.rainKm > 0.0
                InsightMetric.WET_ROAD -> weatherStats.wetRoadKm > 0.0
                InsightMetric.EXTREME -> weatherStats.extremeKm > 0.0
                else -> true // Métricas de distancia siempre tienen datos
            }
            
            // Solo agregar a la lotería si hay datos
            if (hasData) {
                val valueToCheck = when (metric) {
                    InsightMetric.RAIN -> weatherStats.rainKm
                    InsightMetric.WET_ROAD -> weatherStats.wetRoadKm
                    InsightMetric.EXTREME -> weatherStats.extremeKm
                    else -> currentDistanceKm
                }
                val weight = calculateWeight(metric, valueToCheck, currentDistanceKm)
                repeat(weight) { lotteryBowl.add(metric) }
            }
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
            InsightMetric.EXTREME -> "vs $periodName"
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
     * Calcula las estadísticas climáticas usando SOLO la distancia de rutas GPS.
     * 
     * 🔥 IMPORTANTE: La distancia GPS SOLO se usa para las tarjetas de clima.
     * El resto de cálculos de la app (CO2, árboles, gasolina, logros) usan la distancia de registros manuales.
     * 
     * - Lluvia, Calzada Mojada y Clima Extremo: Suma directa de las distancias de rutas GPS con badges
     * - manualTotalDistance: Solo se guarda para contexto (no se usa en los cálculos)
     */
    private fun calculateWeatherStats(
        manualTotalDistance: Double, // Solo para contexto, no se usa en cálculos
        gpsRoutes: List<com.zipstats.app.model.Route>
    ): WeatherStats {
        // Si no hay rutas GPS, devolvemos 0 en todo
        if (gpsRoutes.isEmpty()) {
            return WeatherStats(
                rainKm = 0.0,
                wetRoadKm = 0.0,
                extremeKm = 0.0,
                gpsTotalDistance = 0.0,
                manualTotalDistance = manualTotalDistance
            )
        }

        var rainKm = 0.0
        var wetRoadKm = 0.0
        var extremeKm = 0.0
        val gpsTotalDistance = gpsRoutes.sumOf { it.totalDistance }

        // 🔥 SIMPLIFICACIÓN: Solo contar km basándonos en los badges guardados
        // No necesitamos saber los motivos específicos, solo si el badge está activo
        gpsRoutes.forEach { route ->
            val dist = route.totalDistance

            // 1. LLUVIA: Solo contar si el badge está activo (weatherHadRain == true)
            // Para rutas antiguas sin badge (null), recalcular como fallback
            val hadRain = when (route.weatherHadRain) {
                true -> true
                false -> false
                null -> isStrictRain(route) // Solo recalcular para rutas antiguas
            }
            if (hadRain) {
                rainKm += dist
            }

            // 2. CALZADA MOJADA: Solo contar si el badge está activo (weatherHadWetRoad == true)
            // IMPORTANTE: Calzada mojada y lluvia son excluyentes (si hay lluvia, no hay calzada mojada)
            // Para rutas antiguas sin badge (null), recalcular como fallback
            val hasWetRoad = if (route.weatherHadRain == true) {
                false // Si hay lluvia activa, no hay calzada mojada (excluyentes)
            } else {
                when (route.weatherHadWetRoad) {
                    true -> true
                    false -> false
                    null -> checkWetRoadConditions(route) // Solo recalcular para rutas antiguas
                }
            }
            if (hasWetRoad) {
                wetRoadKm += dist
            }

            // 3. CLIMA EXTREMO: Solo contar si el badge está activo (weatherHadExtremeConditions == true)
            // No necesitamos saber qué causa específica lo disparó, solo si está activo
            // Para rutas antiguas sin badge (null), recalcular como fallback
            val hasExtreme = when (route.weatherHadExtremeConditions) {
                true -> true
                false -> false
                null -> checkExtremeConditions(route) // Solo recalcular para rutas antiguas
            }
            if (hasExtreme) {
                extremeKm += dist
            }
        }

        return WeatherStats(
            rainKm = rainKm, // Directo de rutas GPS guardadas (suma real)
            wetRoadKm = wetRoadKm, // Directo de rutas GPS guardadas (suma real)
            extremeKm = extremeKm, // Directo de rutas GPS guardadas (suma real)
            gpsTotalDistance = gpsTotalDistance, // Distancia total de rutas GPS (para contexto)
            manualTotalDistance = manualTotalDistance // Distancia total de registros manuales (para contexto)
        )
    }
    
    /**
     * Verifica si hay condiciones extremas en la ruta (sin calcular la causa específica)
     * ⚠️ SOLO para compatibilidad con rutas antiguas que no tienen weatherHadExtremeConditions
     * 
     * Para rutas nuevas, SIEMPRE usar route.weatherHadExtremeConditions directamente
     */
    private fun checkExtremeConditions(route: com.zipstats.app.model.Route): Boolean {
        // Usar los mismos factores que TrackingScreen.kt (líneas 473-496)
        // Viento fuerte (>40 km/h)
        if (route.weatherWindSpeed != null && route.weatherWindSpeed > 40) {
            return true
        }
        
        // Ráfagas (>60 km/h)
        if (route.weatherWindGusts != null && route.weatherWindGusts > 60) {
            return true
        }
        
        // Temperatura extrema (<0°C o >35°C)
        if (route.weatherTemperature != null) {
            if (route.weatherTemperature < 0 || route.weatherTemperature > 35) {
                return true
            }
        }
        
        // UV alto (>8, solo de día)
        if (route.weatherIsDay == true && route.weatherUvIndex != null && route.weatherUvIndex > 8) {
            return true
        }
        
        // Tormenta
        val isStorm = route.weatherEmoji?.let { emoji ->
            emoji.contains("⛈") || emoji.contains("⚡")
        } ?: false
        val isStormByDescription = route.weatherDescription?.let { desc ->
            desc.contains("Tormenta", ignoreCase = true) ||
            desc.contains("granizo", ignoreCase = true) ||
            desc.contains("rayo", ignoreCase = true)
        } ?: false
        if (isStorm || isStormByDescription) {
            return true
        }
        
        // Nieve
        val isSnow = route.weatherEmoji?.let { emoji ->
            emoji.contains("❄️")
        } ?: false
        val isSnowByDescription = route.weatherDescription?.let { desc ->
            desc.contains("Nieve", ignoreCase = true) ||
            desc.contains("nevada", ignoreCase = true) ||
            desc.contains("snow", ignoreCase = true)
        } ?: false
        if (isSnow || isSnowByDescription) {
            return true
        }
        
        return false
    }
    
    /**
     * Detecta la causa específica de condiciones extremas.
     * ⚠️ DEPRECADO: Ya no se usa en las estadísticas simplificadas.
     * Mantenido solo para compatibilidad si se necesita en el futuro.
     */
    @Deprecated("Ya no se necesita calcular la causa específica, solo verificar si hay condiciones extremas")
    private fun detectExtremeCause(route: com.zipstats.app.model.Route): ExtremeCause {
        // Si no hay condiciones extremas, retornar NONE
        if (route.weatherHadExtremeConditions != true) {
            // Verificar si hay condiciones extremas por valores guardados (compatibilidad con rutas antiguas)
            // IMPORTANTE: Debe incluir TODAS las causas que activan el badge, incluyendo visibilidad
            val hasExtreme = (route.weatherWindSpeed != null && route.weatherWindSpeed > 40) ||
                            (route.weatherWindGusts != null && route.weatherWindGusts > 60) ||
                            (route.weatherTemperature != null && (route.weatherTemperature < 0 || route.weatherTemperature > 35)) ||
                            (route.weatherIsDay == true && route.weatherUvIndex != null && route.weatherUvIndex > 8) ||
                            (route.weatherEmoji?.let { it.contains("⛈") || it.contains("⚡") } == true) ||
                            (route.weatherDescription?.let { desc ->
                                desc.contains("Tormenta", ignoreCase = true) ||
                                desc.contains("granizo", ignoreCase = true) ||
                                desc.contains("rayo", ignoreCase = true)
                            } == true) ||
                            // Nieve: emoji o descripción (Route no tiene weatherCode)
                            (route.weatherEmoji?.let { it.contains("❄️") } == true) ||
                            (route.weatherDescription?.let { desc ->
                                desc.contains("Nieve", ignoreCase = true) ||
                                desc.contains("nevada", ignoreCase = true) ||
                                desc.contains("snow", ignoreCase = true)
                            } == true) ||
                            // Visibilidad reducida (crítico para Barcelona - niebla/talaia)
                            (route.weatherVisibility != null && route.weatherVisibility < 3000)
            
            if (!hasExtreme) return ExtremeCause.NONE
        }
        
        // 🔥 PRIORIDAD 1: Leer directamente de weatherExtremeReason si existe (rutas nuevas)
        route.weatherExtremeReason?.let { reason ->
            return when (reason.uppercase()) {
                "STORM", "TORMENTA" -> ExtremeCause.STORM
                "GUSTS", "RACHAS" -> ExtremeCause.GUSTS
                "WIND", "VIENTO" -> ExtremeCause.WIND
                "SNOW", "NIEVE" -> ExtremeCause.SNOW
                "COLD", "FRÍO", "HELADA" -> ExtremeCause.COLD
                "HEAT", "CALOR" -> ExtremeCause.HEAT
                "VISIBILITY", "VISIBILIDAD" -> ExtremeCause.VISIBILITY
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
        
        // 2. Nieve (emoji ❄️ o descripción)
        // Nota: Route no tiene weatherCode, así que detectamos por emoji y descripción
        val isSnowByEmoji = route.weatherEmoji?.let { emoji ->
            emoji.contains("❄️")
        } ?: false
        
        val isSnowByDescription = route.weatherDescription?.let { desc ->
            desc.contains("Nieve", ignoreCase = true) ||
            desc.contains("nevada", ignoreCase = true) ||
            desc.contains("snow", ignoreCase = true)
        } ?: false
        
        if (isSnowByEmoji || isSnowByDescription) {
            return ExtremeCause.SNOW
        }
        
        // 3. Rachas de viento muy fuertes (>60 km/h) - prioridad sobre viento normal
        if (route.weatherWindGusts != null && route.weatherWindGusts > 60) {
            return ExtremeCause.GUSTS
        }
        
        // 4. Viento fuerte (>40 km/h)
        if (route.weatherWindSpeed != null && route.weatherWindSpeed > 40) {
            return ExtremeCause.WIND
        }
        
        // 5. Temperatura extrema
        if (route.weatherTemperature != null) {
            if (route.weatherTemperature < 0) {
                return ExtremeCause.COLD
            }
            if (route.weatherTemperature > 35) {
                return ExtremeCause.HEAT
            }
        }
        
        // 6. Índice UV muy alto (>8) - solo de día (se considera como calor)
        if (route.weatherIsDay == true && route.weatherUvIndex != null && route.weatherUvIndex > 8) {
            return ExtremeCause.HEAT
        }
        
        // 7. Visibilidad reducida (crítico para Barcelona - niebla/talaia)
        if (route.weatherVisibility != null && route.weatherVisibility < 3000) {
            return ExtremeCause.VISIBILITY
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
     * Todas las métricas con datos válidos tienen la misma probabilidad
     */
    private fun calculateWeight(metric: InsightMetric, value: Double, totalDistance: Double): Int {
        // Si no hay dato válido, descartar
        if (value < 0.1) return 0
        
        // Todas las métricas válidas tienen el mismo peso
        return 1
    }
    
    /**
     * Verifica si realmente hubo lluvia activa durante la ruta
     * 🔒 IMPORTANTE: Esta función garantiza que los umbrales sean idénticos entre preavisos y badges
     * 
     * Implementa el "Filtro de Corte Barcelona": 
     * Solo considera lluvia activa si la precipitación es >= 0.15mm
     * Esto evita falsos positivos por humedad alta en Barcelona.
     */
    private fun isStrictRain(route: com.zipstats.app.model.Route): Boolean {
        val description = route.weatherDescription?.uppercase() ?: ""
        val precip = route.weatherMaxPrecipitation ?: 0.0

        // Condiciones que Google considera lluvia real
        // Google usa visión artificial y radares para decidir si es "Lluvia" o solo "Nubes que gotean"
        val rainTerms = listOf("LLUVIA", "RAIN", "CHUBASCO", "TORMENTA", "DRIZZLE", "LLOVIZNA", "THUNDERSTORM", "SHOWER")
        
        // Solo es "Ruta con Lluvia" si Google dice que llueve Y hay agua medible (>= 0.15mm)
        // Esto evita falsos positivos cuando solo hay humedad alta (típico de Barcelona)
        val isRainyCondition = rainTerms.any { description.contains(it) }
        
        return isRainyCondition && precip >= 0.15
    }
    
    /**
     * Verifica si hay condiciones de calzada mojada (SIN lluvia activa real).
     * 🔒 IMPORTANTE: Esta función garantiza que los umbrales sean idénticos entre preavisos y badges
     * 
     * Implementa el "Filtro de Humedad Mediterránea" para Barcelona:
     * - Detecta llovizna fina que no llega a ser lluvia activa (< 0.15mm pero > 0.0mm)
     * - Detecta condensación por humedad extrema (típico de costa mediterránea)
     * - Corrige datos guardados incorrectamente (si fue marcado como lluvia pero no hubo >= 0.15mm)
     */
    private fun checkWetRoadConditions(route: com.zipstats.app.model.Route): Boolean {
        val savedAsRain = route.weatherHadRain == true
        val isStrictRainResult = isStrictRain(route)
        
        // 1. EXCLUSIÓN: Si realmente llovió (precipitación >= 0.15mm), NO es calzada mojada (es lluvia real)
        if (isStrictRainResult) {
            return false
        }
        
        // 2. Si fue guardado como lluvia pero NO hubo precipitación real (>= 0.15mm),
        // se degrada a calzada mojada (esto corrige datos guardados incorrectamente)
        if (savedAsRain && !isStrictRainResult) {
            return true
        }
        
        val precip = route.weatherMaxPrecipitation ?: 0.0
        val humidity = route.weatherHumidity ?: 0
        
        // Lógica Pro para Barcelona:
        val isVeryHumid = humidity > 85
        val hadRecentTrace = precip > 0.0 && precip < 0.2
        
        // Caso A: Hay trazas de precipitación (0.0mm < precip < 0.2mm) con humedad muy alta
        // Esto indica llovizna fina ("meona") que moja el suelo pero no es lluvia activa
        val isDrizzling = hadRecentTrace && isVeryHumid
        
        // Caso B: No llueve, pero la humedad es tan alta (85%+) que el asfalto condensa
        // En Barcelona, especialmente de noche, el asfalto puede estar mojado por rocío o humedad marina
        // Usamos el emoji o descripción como indicador si está disponible
        val weatherDesc = route.weatherDescription?.uppercase() ?: ""
        val isCondensing = isVeryHumid && (
            weatherDesc.contains("NUBLADO") || 
            weatherDesc.contains("CLOUDY") ||
            route.weatherEmoji == "☁️"
        )
        
        // Caso C: Niebla con alta humedad también moja el suelo
        val isFogWetting = isVeryHumid && (
            weatherDesc.contains("NIEBLA") || 
            weatherDesc.contains("FOG") ||
            route.weatherEmoji == "🌫️"
        )
        
        return isDrizzling || isCondensing || isFogWetting
    }

    private fun Double.roundToOneDecimal(): Double {
        return (this * 10.0).roundToInt() / 10.0
    }
    
    /**
     * Cuenta el número de rutas con condiciones climáticas específicas para un período dado
     */
    private suspend fun countWeatherRoutes(
        month: Int? = null,
        year: Int? = null
    ): Triple<Int, Int, Int> { // (rutas con lluvia, rutas con calzada mojada, rutas con condiciones extremas)
        return try {
            val routesResult = routeRepository.getUserRoutes()
            val allRoutes = routesResult.getOrNull() ?: emptyList()
            
            // Si ambos parámetros son null y no hay selección en el estado, no filtrar (caso "Todo")
            val shouldFilter = !(month == null && year == null && _selectedMonth.value == null && _selectedYear.value == null)
            
            val filteredRoutes = if (shouldFilter) {
                val today = LocalDate.now()
                val targetMonth = month ?: _selectedMonth.value
                val targetYear = year ?: _selectedYear.value ?: today.year
                
                // Filtrar rutas por período
                allRoutes.filter { route ->
                    try {
                        val routeDate = java.time.Instant.ofEpochMilli(route.startTime)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        
                        val matchesMonth = targetMonth == null || routeDate.monthValue == targetMonth
                        val matchesYear = routeDate.year == targetYear
                        
                        matchesMonth && matchesYear
                    } catch (e: Exception) {
                        false
                    }
                }
            } else {
                // Sin filtros: todas las rutas
                allRoutes
            }
            
            var rainCount = 0
            var wetRoadCount = 0
            var extremeCount = 0
            
            filteredRoutes.forEach { route ->
                // 🔥 LÓGICA: Confiar COMPLETAMENTE en los datos guardados durante el tracking
                // No recalcular - usar solo lo que TrackingViewModel ya detectó y guardó
                // Las funciones de recálculo solo se usan como fallback para rutas antiguas (null)
                
                // Contar rutas con lluvia: confiar en weatherHadRain
                // 🔥 CORRECCIÓN: Solo recalcular para rutas antiguas (null), no para rutas verificadas como false
                val hadRain = when (route.weatherHadRain) {
                    true -> true
                    false -> false
                    null -> isStrictRain(route) // Solo recalcular para rutas antiguas
                }
                if (hadRain) {
                    rainCount++
                }
                
                // Contar rutas con calzada mojada: confiar en weatherHadWetRoad
                // 🔥 CORRECCIÓN: Solo recalcular para rutas antiguas (null), no para rutas verificadas como false
                // IMPORTANTE: Calzada mojada y lluvia son excluyentes (si hay lluvia, no hay calzada mojada)
                val hasWetRoad = if (route.weatherHadRain == true) {
                    false // Si hay lluvia activa, no hay calzada mojada (excluyentes)
                } else {
                    when (route.weatherHadWetRoad) {
                        true -> true
                        false -> false
                        null -> checkWetRoadConditions(route) // Solo recalcular para rutas antiguas
                    }
                }
                if (hasWetRoad) {
                    wetRoadCount++
                }
                
                // Contar rutas con condiciones extremas: confiar en weatherHadExtremeConditions
                // 🔥 CORRECCIÓN: Solo recalcular para rutas antiguas (null), no para rutas verificadas como false
                val hasExtreme = when (route.weatherHadExtremeConditions) {
                    true -> true
                    false -> false
                    null -> checkExtremeConditions(route) // Solo recalcular para rutas antiguas
                }
                if (hasExtreme) {
                    extremeCount++
                }
            }
            
            Triple(rainCount, wetRoadCount, extremeCount)
        } catch (e: Exception) {
            Triple(0, 0, 0)
        }
    }
} 