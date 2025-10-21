package com.zipstats.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zipstats.app.model.Achievement
import com.zipstats.app.model.AchievementLevel
import com.zipstats.app.model.AchievementRequirementType
import com.zipstats.app.model.Scooter
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.ScooterRepository
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

data class ComparisonData(
    val currentValue: Double,
    val previousValue: Double,
    val percentageChange: Double,
    val isPositive: Boolean,
    val comparisonMonth: Int? = null,
    val comparisonYear: Int
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
    private val scooterRepository: ScooterRepository,
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
        if (month != null && year != null) {
            val monthNames = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
            "${monthNames[month - 1]} $year"
        } else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Obtener la lista de logros del servicio centralizado
    private val allAchievements get() = achievementsService.allAchievements
    
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
                scooterRepository.getScooters().collect { scooters ->
                    recordRepository.getRecords().collect { records ->
                        // Calcular los meses/años disponibles
                        val monthYears = records.map { record ->
                            val date = LocalDate.parse(record.fecha)
                            Pair(date.monthValue, date.year)
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

                        val currentMonth = _selectedMonth.value ?: LocalDate.now().monthValue
                        val currentYear = _selectedYear.value ?: LocalDate.now().year
                        
                        // Estadísticas mensuales (o del período seleccionado)
                        val monthlyRecords = records.filter {
                            val recordDate = LocalDate.parse(it.fecha)
                            recordDate.monthValue == currentMonth && recordDate.year == currentYear
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
                            val recordDate = LocalDate.parse(it.fecha)
                            recordDate.year == currentYear
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
                        
                        // Calcular el siguiente logro (ahora basado en múltiples métricas)
                        val nextAchievement = calculateNextAchievement()

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
        
        return """🛴 Estadísticas totales de ${userName.value} 

📊 Total recorrido: ${stats.totalDistance} km

🌱 CO₂ ahorrado: $co2Saved kg (≈ $treesEquivalent árboles que están ahí, aplaudiendo mi eco-héroe anónimo 🌳👏)
⛽ Gasolina ahorrada: $gasSaved Litros

🏆 Top Vehículos:
${scooterTexts.joinToString("\n")}""".trimIndent()
    }

    fun getMonthlyShareText(stats: StatisticsUiState.Success): String {
        val co2Saved = (stats.monthlyDistance * 0.1).toInt()
        val treesEquivalent = (stats.monthlyDistance * 0.005).toInt()
        val gasSaved = (stats.monthlyDistance * 0.04).toInt()
        return """
🛴 Estadísticas mensuales de ${userName.value} 🛴

📊 Total recorrido: ${stats.monthlyDistance} km
📈 Promedio por registro: ${stats.monthlyAverageDistance} km
🏆 Mejor registro: ${stats.monthlyMaxDistance} km
📝 Total de registros: ${stats.monthlyRecords}

🌱 CO₂ ahorrado: $co2Saved kg (≈ $treesEquivalent árboles que están ahí, aplaudiendo mi eco-héroe anónimo 🌳👏)
⛽ Gasolina ahorrada: $gasSaved Litros 
""".trimIndent()
    }

    fun getYearlyShareText(stats: StatisticsUiState.Success): String {
        val co2Saved = (stats.yearlyDistance * 0.1).toInt()
        val treesEquivalent = (stats.yearlyDistance * 0.005).toInt()
        val gasSaved = (stats.yearlyDistance * 0.04).toInt()
        return """
🛴 Estadísticas de ${LocalDate.now().year} de ${userName.value} 🛴

📊 Total recorrido: ${stats.yearlyDistance} km
📈 Promedio por registro: ${stats.yearlyAverageDistance} km
🏆 Mejor registro: ${stats.yearlyMaxDistance} km
📝 Total de registros: ${stats.yearlyRecords}

🌱 CO₂ ahorrado: $co2Saved kg (≈ $treesEquivalent árboles que están ahí, aplaudiendo mi eco-héroe anónimo 🌳👏)
⛽ Gasolina ahorrada: $gasSaved Litros 
""".trimIndent()
    }

    private fun calculateMonthlyChartData(records: List<com.zipstats.app.model.Record>): List<ChartDataPoint> {
        val now = LocalDate.now()
        val last30Days = (0..29).map { now.minusDays(it.toLong()) }.reversed()
        
        return last30Days.map { date ->
            val dailyDistance = records
                .filter { LocalDate.parse(it.fecha) == date }
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
                    val recordDate = LocalDate.parse(it.fecha)
                    recordDate.year == year && recordDate.monthValue == month
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
            val date = LocalDate.parse(it.fecha)
            "${date.year}-${String.format("%02d", date.monthValue)}"
        }
        
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
        val currentDayOfMonth = if (isCurrentMonth) today.dayOfMonth else 31 // Si es histórico, tomar el mes completo
        
        // Obtener registros del mes/año actual (hasta hoy si es el mes en curso, todo el mes si es histórico)
        val currentMonthRecords = records.filter {
            val recordDate = LocalDate.parse(it.fecha)
            recordDate.monthValue == currentMonth && 
            recordDate.year == currentYear &&
            recordDate.dayOfMonth <= currentDayOfMonth
        }
        
        val currentValue = currentMonthRecords.sumOf { it.diferencia }
        
        // Buscar el mismo mes en años anteriores hasta el mismo día (empezando por el más cercano)
        var comparisonYear: Int? = null
        var previousValue = 0.0
        
        for (yearOffset in 1..10) {
            val yearToCheck = currentYear - yearOffset
            val previousYearRecords = records.filter {
                val recordDate = LocalDate.parse(it.fecha)
                recordDate.monthValue == currentMonth && 
                recordDate.year == yearToCheck &&
                recordDate.dayOfMonth <= currentDayOfMonth
            }
            
            if (previousYearRecords.isNotEmpty()) {
                previousValue = previousYearRecords.sumOf { it.diferencia }
                comparisonYear = yearToCheck
                break
            }
        }
        
        // Solo retornar si encontramos datos para comparar
        if (comparisonYear == null || previousValue == 0.0) return null
        
        val percentageChange = ((currentValue - previousValue) / previousValue * 100).roundToOneDecimal()
        
        return ComparisonData(
            currentValue = currentValue.roundToOneDecimal(),
            previousValue = previousValue.roundToOneDecimal(),
            percentageChange = kotlin.math.abs(percentageChange),
            isPositive = percentageChange >= 0,
            comparisonMonth = currentMonth,
            comparisonYear = comparisonYear
        )
    }
    
    private fun calculateYearlyComparison(records: List<com.zipstats.app.model.Record>, currentYear: Int): ComparisonData? {
        val today = LocalDate.now()
        val isCurrentYear = currentYear == today.year
        val currentDayOfYear = if (isCurrentYear) today.dayOfYear else 366 // Si es histórico, tomar el año completo
        
        // Obtener registros del año actual (hasta hoy si es el año en curso, todo el año si es histórico)
        val currentYearRecords = records.filter {
            val recordDate = LocalDate.parse(it.fecha)
            recordDate.year == currentYear &&
            recordDate.dayOfYear <= currentDayOfYear
        }
        
        val currentValue = currentYearRecords.sumOf { it.diferencia }
        
        // Buscar el año más próximo con datos hasta el mismo día del año
        var comparisonYear: Int? = null
        var previousValue = 0.0
        
        for (yearOffset in 1..10) {
            val yearToCheck = currentYear - yearOffset
            val previousYearRecords = records.filter {
                val recordDate = LocalDate.parse(it.fecha)
                recordDate.year == yearToCheck &&
                recordDate.dayOfYear <= currentDayOfYear
            }
            
            if (previousYearRecords.isNotEmpty()) {
                previousValue = previousYearRecords.sumOf { it.diferencia }
                comparisonYear = yearToCheck
                break
            }
        }
        
        // Solo retornar si encontramos datos para comparar
        if (comparisonYear == null || previousValue == 0.0) return null
        
        val percentageChange = ((currentValue - previousValue) / previousValue * 100).roundToOneDecimal()
        
        return ComparisonData(
            currentValue = currentValue.roundToOneDecimal(),
            previousValue = previousValue.roundToOneDecimal(),
            percentageChange = kotlin.math.abs(percentageChange),
            isPositive = percentageChange >= 0,
            comparisonMonth = null,
            comparisonYear = comparisonYear
        )
    }
    
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

    private fun Double.roundToOneDecimal(): Double {
        return (this * 10.0).roundToInt() / 10.0
    }
} 