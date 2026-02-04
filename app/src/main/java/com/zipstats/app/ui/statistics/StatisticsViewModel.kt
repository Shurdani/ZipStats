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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Date
import javax.inject.Inject
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
    NONE("Clima extremo", "⚠️"),
    WIND("Viento intenso", "💨"),
    GUSTS("Ráfagas", "🍃"),
    STORM("Tormenta", "⚡"),
    SNOW("Nieve", "❄️"),
    COLD("Helada", "🥶"),
    HEAT("Calor intenso", "🔥"),
    VISIBILITY("Visibilidad reducida", "🌫️")
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
    RAIN("Rutas con lluvia", Icons.Filled.WaterDrop, Color(0xFF00B0FF), "km", 1.0), // Azul Cian
    WET_ROAD("Calzada Mojada", Icons.Outlined.Water, Color(0xFFFF9100), "km", 1.0), // Naranja/Ámbar
    EXTREME("Clima extremo", Icons.Filled.Thermostat, Color(0xFFD50000), "km", 1.0) // Rojo
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
    val icon: String = "",
    // Métricas de clima del período comparado (para uso en DynamicMetricCard)
    val comparisonWeatherMetrics: WeatherComparisonMetrics? = null
)

data class WeatherComparisonMetrics(
    val rainKm: Double,
    val wetRoadKm: Double,
    val extremeKm: Double
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
                // 1. Obtener rutas GPS y registros manuales
                val routesResult = routeRepository.getUserRoutes()
                val allRoutes = routesResult.getOrNull() ?: emptyList()

                scooterRepository.getScooters().collect { scooters ->
                    recordRepository.getRecords().collect { records ->

                        // --- REGENERAR LISTA DE MESES PARA EL FILTRO ---
                        val monthYears = records.mapNotNull { record ->
                            try {
                                val date = LocalDate.parse(record.fecha)
                                Pair(date.monthValue, date.year)
                            } catch (e: Exception) { null }
                        }.distinct().sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenByDescending { it.first })

                        _availableMonthYears.value = monthYears

                        // --- CONTEXTO TEMPORAL ---
                        val today = LocalDate.now()
                        val currentMonth = _selectedMonth.value ?: today.monthValue
                        val currentYear = _selectedYear.value ?: today.year

                        // --- CÁLCULOS DE DISTANCIA ---
                        val totalDistance = records.sumOf { it.diferencia }.roundToOneDecimal()

                        // Estadísticas mensuales (Mes seleccionado o actual)
                        val monthlyRecords = records.filter {
                            try {
                                val recordDate = LocalDate.parse(it.fecha)
                                recordDate.monthValue == currentMonth && recordDate.year == currentYear
                            } catch (e: Exception) { false }
                        }
                        val monthlyDistance = monthlyRecords.sumOf { it.diferencia }.roundToOneDecimal()

                        // Estadísticas anuales (Año seleccionado o actual)
                        val yearlyRecords = records.filter {
                            try {
                                val recordDate = LocalDate.parse(it.fecha)
                                recordDate.year == currentYear
                            } catch (e: Exception) { false }
                        }
                        val yearlyDistance = yearlyRecords.sumOf { it.diferencia }.roundToOneDecimal()

                        // --- FILTRADO DE CLIMA CORREGIDO ---
                        val filteredGpsRoutes = allRoutes.filter { route ->
                            try {
                                val routeDate = java.time.Instant.ofEpochMilli(route.startTime)
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()

                                when {
                                    // 1. Si hay mes seleccionado: Filtrar por ese mes y año
                                    _selectedMonth.value != null -> {
                                        routeDate.monthValue == _selectedMonth.value && routeDate.year == currentYear
                                    }
                                    // 2. Si solo hay año seleccionado: Filtrar por todo ese año
                                    _selectedYear.value != null -> {
                                        routeDate.year == _selectedYear.value
                                    }
                                    // 3. VISTA POR DEFECTO (Entrada a la App): Forzar mes y año actual
                                    // 🔥 Esto evita que 'weatherStats' use los totales históricos
                                    else -> {
                                        routeDate.monthValue == today.monthValue && routeDate.year == today.year
                                    }
                                }
                            } catch (e: Exception) { false }
                        }

                        // Actualizar métricas de clima para la UI y la Tarjeta Dinámica
                        val manualDist = when {
                            _selectedMonth.value != null -> monthlyDistance
                            _selectedYear.value != null -> yearlyDistance
                            else -> monthlyDistance // Por defecto mostramos el contexto del mes actual
                        }

                        _weatherStats.value = calculateWeatherStats(manualDist, filteredGpsRoutes)

                        // --- EMITIR RESULTADOS ---
                        _statistics.value = StatisticsUiState.Success(
                            totalDistance = totalDistance,
                            monthlyDistance = monthlyDistance,
                            yearlyDistance = yearlyDistance,
                            scooterStats = scooters.map { scooter ->
                                val sRecords = records.filter { it.patinete == scooter.nombre }
                                ScooterStats(scooter.modelo, sRecords.sumOf { it.diferencia }.roundToOneDecimal())
                            },
                            maxDistance = records.maxOfOrNull { it.diferencia }?.roundToOneDecimal() ?: 0.0,
                            averageDistance = if (records.isNotEmpty()) (records.sumOf { it.diferencia } / records.size).roundToOneDecimal() else 0.0,
                            totalRecords = records.size,
                            lastRecordDate = records.maxByOrNull { it.fecha }?.fecha ?: "No hay registros",
                            lastRecordDistance = records.maxByOrNull { it.fecha }?.diferencia?.roundToOneDecimal() ?: 0.0,
                            monthlyMaxDistance = monthlyRecords.maxOfOrNull { it.diferencia }?.roundToOneDecimal() ?: 0.0,
                            monthlyAverageDistance = if (monthlyRecords.isNotEmpty()) (monthlyRecords.sumOf { it.diferencia } / monthlyRecords.size).roundToOneDecimal() else 0.0,
                            monthlyRecords = monthlyRecords.size,
                            yearlyMaxDistance = yearlyRecords.maxOfOrNull { it.diferencia }?.roundToOneDecimal() ?: 0.0,
                            yearlyAverageDistance = if (yearlyRecords.isNotEmpty()) (yearlyRecords.sumOf { it.diferencia } / yearlyRecords.size).roundToOneDecimal() else 0.0,
                            yearlyRecords = yearlyRecords.size,
                            monthlyChartData = calculateMonthlyChartData(records),
                            yearlyChartData = calculateYearlyChartData(records, currentYear),
                            allTimeChartData = calculateAllTimeChartData(records),
                            monthlyComparison = calculateMonthlyComparison(records, allRoutes, currentMonth, currentYear),
                            yearlyComparison = calculateYearlyComparison(records, allRoutes, currentYear),
                            nextAchievement = try { calculateNextAchievement() } catch (e: Exception) { null }
                        )
                    }
                }
            } catch (e: Exception) {
                _statistics.value = StatisticsUiState.Error(e.message ?: "Error al cargar datos")
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
        val gasSaved = (stats.totalDistance * 0.07).toInt() // 0.07 litros por km (7L/100km)

        val weather = weatherStats.value
        val rainKm = weather.rainKm.roundToOneDecimal()
        val wetRoadKm = weather.wetRoadKm.roundToOneDecimal()
        val extremeKm = weather.extremeKm.roundToOneDecimal()

        val weatherLines = buildList {
            if (rainKm > 0.0) add("🌧️ Con lluvia: $rainKm km")
            if (wetRoadKm > 0.0) add("💧 Calzada húmeda: $wetRoadKm km")
            if (extremeKm > 0.0) add("⚠️ Extremo: $extremeKm km")
        }

        val lines = mutableListOf(
            "Estadísticas totales de ${userName.value}",
            
            "📊 Total: ${stats.totalDistance.roundToOneDecimal()} km | CO₂: -$co2Saved kg",
            "🌳 Árboles: $treesEquivalent | ⛽ Gasolina: $gasSaved L"
        )

        if (weatherLines.isNotEmpty()) {
            lines.add("")
            lines.addAll(weatherLines)
        }

        lines.add("")
        lines.add("#ZipStats")

        return lines.joinToString("\n")
    }

    suspend fun getMonthlyShareText(stats: StatisticsUiState.Success, month: Int? = null, year: Int? = null): String {
        // 1. Cálculos base
        val co2Saved = (stats.monthlyDistance * 0.15).toInt()
        val treesEquivalent = (stats.monthlyDistance * 0.005).toInt()
        val gasSaved = (stats.monthlyDistance * 0.07).toInt()

        // 2. Determinar mes y año exactos
        val selectedMonth = (month ?: _selectedMonth.value ?: LocalDate.now().monthValue).coerceIn(1, 12)
        val selectedYear = year ?: _selectedYear.value ?: LocalDate.now().year

        val monthNames = listOf(
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        )
        val monthName = monthNames.getOrElse(selectedMonth - 1) { "Mes" }

        // 3. 🔥 SOLUCIÓN CRÍTICA: Filtrado y cálculo de clima LOCAL
        // No usamos weatherStats.value porque puede tener datos de todo el año
        val allRoutes = routeRepository.getUserRoutes().getOrNull() ?: emptyList()
        val monthlyGpsRoutes = allRoutes.filter { route ->
            try {
                val routeDate = java.time.Instant.ofEpochMilli(route.startTime)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                routeDate.monthValue == selectedMonth && routeDate.year == selectedYear
            } catch (e: Exception) {
                false
            }
        }

        // Calculamos el clima específico para este mes usando tu función motor
        val monthlyWeather = calculateWeatherStats(stats.monthlyDistance, monthlyGpsRoutes)

        val rainKm = monthlyWeather.rainKm.roundToOneDecimal()
        val wetRoadKm = monthlyWeather.wetRoadKm.roundToOneDecimal()
        val extremeKm = monthlyWeather.extremeKm.roundToOneDecimal()

        // 4. Preparar líneas de clima
        val weatherLines = buildList {
            if (rainKm > 0.0) add("🌧️ Con lluvia: $rainKm km")
            if (wetRoadKm > 0.0) add("💧 Calzada húmeda: $wetRoadKm km")
            if (extremeKm > 0.0) add("⚠️ Extremo: $extremeKm km")
        }

        // 5. Construcción del mensaje final (Limpio y sin redundancias)
        val lines = mutableListOf(
            "Estadísticas $monthName $selectedYear de ${userName.value}",
            "",
            "📊 Total: ${stats.monthlyDistance.roundToOneDecimal()} km | 🌱 CO₂: -$co2Saved kg",
            "🌳 Árboles: $treesEquivalent | ⛽ Gasolina: $gasSaved L"
        )

        if (weatherLines.isNotEmpty()) {
            lines.add("")
            lines.addAll(weatherLines)
        }

        lines.add("")
        lines.add("#ZipStats 🛴")

        return lines.joinToString("\n")
    }

    suspend fun getYearlyShareText(stats: StatisticsUiState.Success, year: Int? = null): String {
        // 1. Cálculos de ahorro basados en la distancia anual
        val co2Saved = (stats.yearlyDistance * 0.15).toInt()
        val treesEquivalent = (stats.yearlyDistance * 0.005).toInt()
        val gasSaved = (stats.yearlyDistance * 0.07).toInt()

        // 2. Determinar el año seleccionado
        val selectedYear = year ?: _selectedYear.value ?: LocalDate.now().year

        // 3. 🔥 SOLUCIÓN LOCAL: Filtrar rutas GPS solo para este año
        val allRoutes = routeRepository.getUserRoutes().getOrNull() ?: emptyList()
        val yearlyGpsRoutes = allRoutes.filter { route ->
            try {
                val routeDate = java.time.Instant.ofEpochMilli(route.startTime)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                routeDate.year == selectedYear
            } catch (e: Exception) {
                false
            }
        }

        // Calculamos el clima específico para este año
        val yearlyWeather = calculateWeatherStats(stats.yearlyDistance, yearlyGpsRoutes)

        val rainKm = yearlyWeather.rainKm.roundToOneDecimal()
        val wetRoadKm = yearlyWeather.wetRoadKm.roundToOneDecimal()
        val extremeKm = yearlyWeather.extremeKm.roundToOneDecimal()

        // 4. Preparar líneas de clima del año
        val weatherLines = buildList {
            if (rainKm > 0.0) add("🌧️ Con lluvia: $rainKm km")
            if (wetRoadKm > 0.0) add("💧 Calzada húmeda: $wetRoadKm km")
            if (extremeKm > 0.0) add("⚠️ Extremo: $extremeKm km")
        }

        // 5. Construcción del mensaje (Limpio y profesional)
        val lines = mutableListOf(
            "Resumen Anual $selectedYear - ${userName.value}",
            "",
            "📊 Recorrido: ${stats.yearlyDistance.roundToOneDecimal()} km | 🌱 CO₂: -$co2Saved kg",
            "🌳 Árboles: $treesEquivalent | ⛽ Gasolina: $gasSaved L"
        )

        if (weatherLines.isNotEmpty()) {
            lines.add("")
            lines.addAll(weatherLines)
        }

        lines.add("")
        lines.add("#ZipStats 🛴")

        return lines.joinToString("\n")
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

    private suspend fun calculateMonthlyComparison(
        records: List<com.zipstats.app.model.Record>,
        allRoutes: List<com.zipstats.app.model.Route>,
        currentMonth: Int,
        currentYear: Int
    ): ComparisonData? {
        val today = LocalDate.now()
        val hasFilter = _selectedMonth.value != null || _selectedYear.value != null

        val (targetMonth, targetYear, comparisonMonth, comparisonYear) = if (hasFilter) {
            val filterMonth = _selectedMonth.value ?: today.monthValue
            val filterYear = _selectedYear.value ?: today.year
            // Si miramos el mes actual, comparamos con el mismo mes del año pasado
            if (filterMonth == today.monthValue && filterYear == today.year) {
                Quadruple(filterMonth, filterYear, filterMonth, filterYear - 1)
            } else {
                // Si miramos un mes pasado, lo comparamos con el mes actual (o podrías decidir mes anterior)
                Quadruple(filterMonth, filterYear, today.monthValue, today.year)
            }
        } else {
            Quadruple(today.monthValue, today.year, today.monthValue, today.year - 1)
        }

        val limitDayOfMonth = today.dayOfMonth

        // 1. Distancia actual (Target)
        val targetDistance = records.filter {
            try {
                val d = LocalDate.parse(it.fecha)
                d.monthValue == targetMonth && d.year == targetYear && d.dayOfMonth <= limitDayOfMonth
            } catch (e: Exception) { false }
        }.sumOf { it.diferencia }

        // 2. Distancia anterior (Comparison)
        val comparisonDistance = records.filter {
            try {
                val d = LocalDate.parse(it.fecha)
                d.monthValue == comparisonMonth && d.year == comparisonYear && d.dayOfMonth <= limitDayOfMonth
            } catch (e: Exception) { false }
        }.sumOf { it.diferencia }

        if (comparisonDistance < 0.1) return null

        // 3. 🔥 CLIMA ACTUAL (Target) - Esto es lo que le falta a la tarjeta para ser "Dynamic"
        val targetWeatherMetrics = getWeatherMetricsForPeriod(
            month = targetMonth,
            year = targetYear,
            allRoutes = allRoutes,
            limitDayOfMonth = limitDayOfMonth
        )

        // 4. CLIMA ANTERIOR (Comparison)
        val comparisonWeatherMetrics = getWeatherMetricsForPeriod(
            month = comparisonMonth,
            year = comparisonYear,
            allRoutes = allRoutes,
            limitDayOfMonth = limitDayOfMonth
        )

        // El objeto WeatherComparisonMetrics en tu data class suele guardar los datos del periodo anterior
        // para calcular la diferencia con el 'weatherStats' actual de la pantalla.
        val weatherMetrics = WeatherComparisonMetrics(
            rainKm = comparisonWeatherMetrics.first,
            wetRoadKm = comparisonWeatherMetrics.second,
            extremeKm = comparisonWeatherMetrics.third
        )

        return createComparisonMetric(
            currentDistance = targetDistance,
            previousDistance = comparisonDistance,
            metricType = ComparisonMetricType.DISTANCE,
            comparisonMonth = comparisonMonth,
            comparisonYear = comparisonYear,
            weatherMetrics = weatherMetrics
        )
    }

    private suspend fun calculateYearlyComparison(
        records: List<com.zipstats.app.model.Record>,
        allRoutes: List<com.zipstats.app.model.Route>,
        currentYear: Int
    ): ComparisonData? {
        val today = LocalDate.now()
        val hasFilter = _selectedYear.value != null

        val (targetYear, comparisonYear) = if (hasFilter) {
            val filterYear = _selectedYear.value ?: today.year
            val nowYear = today.year

            if (filterYear == nowYear) {
                Pair(filterYear, filterYear - 1)
            } else {
                Pair(filterYear, nowYear)
            }
        } else {
            val nowYear = today.year
            Pair(nowYear, nowYear - 1)
        }

        // --- Límite equitativo YTD (Día del año) ---
        val limitDayOfYear = today.dayOfYear

        // 1. Distancia del año objetivo (Target)
        val targetDistance = records.filter {
            try {
                val recordDate = LocalDate.parse(it.fecha)
                recordDate.year == targetYear && recordDate.dayOfYear <= limitDayOfYear
            } catch (e: Exception) { false }
        }.sumOf { it.diferencia }

        // 2. Distancia del año de comparación (Comparison)
        val comparisonDistance = records.filter {
            try {
                val recordDate = LocalDate.parse(it.fecha)
                recordDate.year == comparisonYear && recordDate.dayOfYear <= limitDayOfYear
            } catch (e: Exception) { false }
        }.sumOf { it.diferencia }

        if (comparisonDistance < 0.1) return null

        // 3. 🔥 CLIMA DEL AÑO DE COMPARACIÓN (Pasado)
        // Usamos el límite temporal para que si el año pasado llovió mucho en diciembre, no nos penalice ahora en febrero
        val comparisonWeatherMetrics = getWeatherMetricsForPeriod(
            month = null,
            year = comparisonYear,
            allRoutes = allRoutes,
            limitDayOfYear = limitDayOfYear
        )

        val weatherMetrics = WeatherComparisonMetrics(
            rainKm = comparisonWeatherMetrics.first,
            wetRoadKm = comparisonWeatherMetrics.second,
            extremeKm = comparisonWeatherMetrics.third
        )

        return createComparisonMetric(
            currentDistance = targetDistance,
            previousDistance = comparisonDistance,
            metricType = ComparisonMetricType.DISTANCE,
            comparisonMonth = null,
            comparisonYear = comparisonYear,
            weatherMetrics = weatherMetrics
        )
    }
    
    private fun createComparisonMetric(
        currentDistance: Double, // Valor original preciso
        previousDistance: Double, // Valor original preciso
        metricType: ComparisonMetricType,
        comparisonMonth: Int?,
        comparisonYear: Int,
        weatherMetrics: WeatherComparisonMetrics? = null
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
            icon = icon,
            comparisonWeatherMetrics = weatherMetrics
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
     * Obtiene las métricas de clima (lluvia, calzada mojada, condiciones extremas) desde rutas GPS
     * para un período específico (mes y año).
     */
    // Actualiza esta función en StatisticsViewModel.kt
    private suspend fun getWeatherMetricsForPeriod(
        month: Int?,
        year: Int,
        allRoutes: List<com.zipstats.app.model.Route>,
        limitDayOfYear: Int? = null,
        limitDayOfMonth: Int? = null // Añadimos este
    ): Triple<Double, Double, Double> {
        return withContext(Dispatchers.Default) {
            try {
                val filteredRoutes = allRoutes.filter { route ->
                    val routeDate = java.time.Instant.ofEpochMilli(route.startTime)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()

                    val matchesYear = routeDate.year == year
                    val matchesMonth = month == null || routeDate.monthValue == month

                    // Filtro dinámico: o por día del año (anual) o por día del mes (mensual)
                    val matchesDay = when {
                        limitDayOfYear != null -> routeDate.dayOfYear <= limitDayOfYear
                        limitDayOfMonth != null -> routeDate.dayOfMonth <= limitDayOfMonth
                        else -> true
                    }

                    matchesYear && matchesMonth && matchesDay
                }

                var rainKm = 0.0
                var wetRoadKm = 0.0
                var extremeKm = 0.0

                filteredRoutes.forEach { route ->
                    // IMPORTANTE: En tu modelo Route se llama 'totalDistance'
                    val d = route.totalDistance

                    if (route.weatherHadRain == true) {
                        rainKm += d
                    } else if (route.weatherHadWetRoad == true) {
                        wetRoadKm += d
                    }

                    if (route.weatherHadExtremeConditions == true) {
                        extremeKm += d
                    }
                }

                Triple(rainKm, wetRoadKm, extremeKm)
            } catch (e: Exception) { Triple(0.0, 0.0, 0.0) }
        }
    }
    
    /**
     * Calcula las estadísticas climáticas usando SOLO la distancia de rutas GPS.
     * 
     * 🔥 IMPORTANTE: La distancia GPS SOLO se usa para las tarjetas de clima.
     * El resto de cálculos de la app (CO2, árboles, gasolina, logros) usan la distancia de registros manuales.
     * 
     * - Lluvia, Calzada húmeda y Clima extremo: Suma directa de las distancias de rutas GPS con badges
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

            // 1. LLUVIA: Solo leer el valor guardado (weatherHadRain == true)
            // Si es null, tratar como false (no hubo lluvia)
            val hadRain = route.weatherHadRain == true
            if (hadRain) {
                rainKm += dist
            }

            // 2. CALZADA húmeda: Solo leer el valor guardado (weatherHadWetRoad == true)
            // IMPORTANTE: Calzada húmeda y lluvia son excluyentes (si hay lluvia, no hay calzada húmeda)
            // Si es null, tratar como false (no hubo calzada húmeda)
            val hasWetRoad = if (route.weatherHadRain == true) {
                false // Si hay lluvia activa, no hay calzada húmeda (excluyentes)
            } else {
                route.weatherHadWetRoad == true
            }
            if (hasWetRoad) {
                wetRoadKm += dist
            }

            // 3. CLIMA EXTREMO: Solo leer el valor guardado (weatherHadExtremeConditions == true)
            // Si es null, tratar como false (no hubo condiciones extremas)
            val hasExtreme = route.weatherHadExtremeConditions == true
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
            val cond = route.weatherCondition?.uppercase() ?: ""
            val hasExtreme = (route.weatherWindSpeed != null && route.weatherWindSpeed > 40) ||
                            (route.weatherWindGusts != null && route.weatherWindGusts > 60) ||
                            (route.weatherTemperature != null && (route.weatherTemperature < 0 || route.weatherTemperature > 35)) ||
                            (route.weatherIsDay == true && route.weatherUvIndex != null && route.weatherUvIndex > 8) ||
                            // Tormenta/Granizo: priorizar `weatherCondition` (Google)
                            (cond.contains("THUNDER") || cond.contains("HAIL")) ||
                            // Fallback legacy: emoji o descripción
                            (route.weatherEmoji?.let { it.contains("⛈") || it.contains("⚡") } == true) ||
                            (route.weatherDescription?.let { desc ->
                                desc.contains("Tormenta", ignoreCase = true) ||
                                desc.contains("granizo", ignoreCase = true) ||
                                desc.contains("rayo", ignoreCase = true)
                            } == true) ||
                            // Nieve: priorizar `weatherCondition` (Google)
                            (cond.contains("SNOW") || cond == "RAIN_AND_SNOW" || cond == "FREEZING_RAIN" || cond == "ICE") ||
                            // Fallback legacy: emoji o descripción
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
        val cond = route.weatherCondition?.uppercase() ?: ""
        val isStormByCondition = cond.contains("THUNDER") || cond.contains("HAIL")
        val isStormByEmoji = route.weatherEmoji?.let { emoji ->
            emoji.contains("⛈") || emoji.contains("⚡")
        } ?: false
        
        val isStormByDescription = route.weatherDescription?.let { desc ->
            desc.contains("Tormenta", ignoreCase = true) ||
            desc.contains("granizo", ignoreCase = true) ||
            desc.contains("rayo", ignoreCase = true)
        } ?: false
        
        if (isStormByCondition || isStormByEmoji || isStormByDescription) {
            return ExtremeCause.STORM
        }
        
        // 2. Nieve (priorizar condition, fallback emoji/descr)
        val isSnowByCondition = cond.contains("SNOW") || cond == "RAIN_AND_SNOW" || cond == "FREEZING_RAIN" || cond == "ICE"
        val isSnowByEmoji = route.weatherEmoji?.let { emoji ->
            emoji.contains("❄️")
        } ?: false
        
        val isSnowByDescription = route.weatherDescription?.let { desc ->
            desc.contains("Nieve", ignoreCase = true) ||
            desc.contains("nevada", ignoreCase = true) ||
            desc.contains("snow", ignoreCase = true)
        } ?: false
        
        if (isSnowByCondition || isSnowByEmoji || isSnowByDescription) {
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
    
    private fun Double.roundToOneDecimal(): Double {
        return (this * 10.0).roundToInt() / 10.0
    }
    
    /**
     * Cuenta el número de rutas con condiciones climáticas específicas para un período dado
     */
    private suspend fun countWeatherRoutes(
        month: Int? = null,
        year: Int? = null
    ): Triple<Int, Int, Int> { // (rutas con lluvia, rutas con calzada húmeda, rutas con condiciones extremas)
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
                // 🔥 LÓGICA: Solo leer valores guardados, sin recalcular
                // Si el valor es null, tratar como false (no hubo lluvia/calzada húmeda/condiciones extremas)
                
                // Contar rutas con lluvia: solo leer valor guardado
                // Si es null, tratar como false (no hubo lluvia)
                val hadRain = route.weatherHadRain == true
                if (hadRain) {
                    rainCount++
                }
                
                // Contar rutas con calzada húmeda: solo leer valor guardado
                // IMPORTANTE: Calzada húmeda y lluvia son excluyentes (si hay lluvia, no hay calzada húmeda)
                // Si es null, tratar como false (no hubo calzada húmeda)
                val hasWetRoad = if (route.weatherHadRain == true) {
                    false // Si hay lluvia activa, no hay calzada húmeda (excluyentes)
                } else {
                    route.weatherHadWetRoad == true
                }
                if (hasWetRoad) {
                    wetRoadCount++
                }
                
                // Contar rutas con condiciones extremas: solo leer valor guardado
                // Si es null, tratar como false (no hubo condiciones extremas)
                val hasExtreme = route.weatherHadExtremeConditions == true
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
