package com.example.patineta.ui.achievements

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.patineta.model.Achievement
import com.example.patineta.model.AchievementRequirementType
import com.example.patineta.repository.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

sealed class AchievementsUiState {
    object Loading : AchievementsUiState()
    data class Success(val achievements: List<Achievement>) : AchievementsUiState()
    data class Error(val message: String) : AchievementsUiState()
}

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val recordRepository: RecordRepository,
    private val achievementsService: com.example.patineta.service.AchievementsService,
    private val context: Context
) : ViewModel() {

    private val _achievements = MutableStateFlow<AchievementsUiState>(AchievementsUiState.Loading)
    val achievements: StateFlow<AchievementsUiState> = _achievements.asStateFlow()

    // Delegar al servicio centralizado
    val newAchievementMessage = achievementsService.newAchievementMessage
    val lastUnlockedAchievementId = achievementsService.lastUnlockedAchievementId
    val shouldRefreshAchievements = achievementsService.shouldRefreshAchievements

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("achievements_prefs", Context.MODE_PRIVATE)
    }

    // Obtener la lista de logros del servicio centralizado
    private val allAchievements get() = achievementsService.allAchievements

    init {
        loadAchievements()
    }

    fun loadAchievements() {
        viewModelScope.launch {
            try {
                val stats = recordRepository.getAchievementStats()
                
                val unlockedAchievements = allAchievements.map { achievement ->
                    val isUnlocked = when (achievement.requirementType) {
                        AchievementRequirementType.DISTANCE -> 
                            stats.totalDistance >= (achievement.requiredDistance ?: 0.0)
                        AchievementRequirementType.TRIPS -> 
                            stats.totalTrips >= (achievement.requiredTrips ?: 0)
                        AchievementRequirementType.CONSECUTIVE_DAYS -> 
                            stats.consecutiveDays >= (achievement.requiredConsecutiveDays ?: 0)
                        AchievementRequirementType.UNIQUE_SCOOTERS -> 
                            stats.uniqueScooters >= (achievement.requiredUniqueScooters ?: 0)
                        AchievementRequirementType.LONGEST_TRIP -> 
                            stats.longestTrip >= (achievement.requiredLongestTrip ?: 0.0)
                        AchievementRequirementType.UNIQUE_WEEKS -> 
                            stats.uniqueWeeks >= (achievement.requiredUniqueWeeks ?: 0)
                        AchievementRequirementType.MAINTENANCE_COUNT -> 
                            stats.maintenanceCount >= (achievement.requiredMaintenanceCount ?: 0)
                        AchievementRequirementType.CO2_SAVED -> 
                            stats.co2Saved >= (achievement.requiredCO2Saved ?: 0.0)
                        AchievementRequirementType.UNIQUE_MONTHS -> 
                            stats.uniqueMonths >= (achievement.requiredUniqueMonths ?: 0)
                        AchievementRequirementType.CONSECUTIVE_MONTHS -> 
                            stats.consecutiveMonths >= (achievement.requiredConsecutiveMonths ?: 0)
                        AchievementRequirementType.ALL_OTHERS -> {
                            // Verificar si todos los demás logros están desbloqueados
                            val otherAchievements = allAchievements.filter { it.id != achievement.id }
                            otherAchievements.all { other ->
                                when (other.requirementType) {
                                    AchievementRequirementType.DISTANCE -> 
                                        stats.totalDistance >= (other.requiredDistance ?: 0.0)
                                    AchievementRequirementType.TRIPS -> 
                                        stats.totalTrips >= (other.requiredTrips ?: 0)
                                    AchievementRequirementType.CONSECUTIVE_DAYS -> 
                                        stats.consecutiveDays >= (other.requiredConsecutiveDays ?: 0)
                                    AchievementRequirementType.UNIQUE_SCOOTERS -> 
                                        stats.uniqueScooters >= (other.requiredUniqueScooters ?: 0)
                                    AchievementRequirementType.LONGEST_TRIP -> 
                                        stats.longestTrip >= (other.requiredLongestTrip ?: 0.0)
                                    AchievementRequirementType.UNIQUE_WEEKS -> 
                                        stats.uniqueWeeks >= (other.requiredUniqueWeeks ?: 0)
                                    AchievementRequirementType.MAINTENANCE_COUNT -> 
                                        stats.maintenanceCount >= (other.requiredMaintenanceCount ?: 0)
                                    AchievementRequirementType.CO2_SAVED -> 
                                        stats.co2Saved >= (other.requiredCO2Saved ?: 0.0)
                                    AchievementRequirementType.UNIQUE_MONTHS -> 
                                        stats.uniqueMonths >= (other.requiredUniqueMonths ?: 0)
                                    AchievementRequirementType.CONSECUTIVE_MONTHS -> 
                                        stats.consecutiveMonths >= (other.requiredConsecutiveMonths ?: 0)
                                    else -> false
                                }
                            }
                        }
                        AchievementRequirementType.MULTIPLE -> false // Por ahora no implementado
                    }
                    
                    val progress = when (achievement.requirementType) {
                        AchievementRequirementType.DISTANCE -> {
                            val required = achievement.requiredDistance ?: 1.0
                            (stats.totalDistance / required * 100).coerceIn(0.0, 100.0)
                        }
                        AchievementRequirementType.TRIPS -> {
                            val required = achievement.requiredTrips ?: 1
                            (stats.totalTrips.toDouble() / required * 100).coerceIn(0.0, 100.0)
                        }
                        AchievementRequirementType.CONSECUTIVE_DAYS -> {
                            val required = achievement.requiredConsecutiveDays ?: 1
                            (stats.consecutiveDays.toDouble() / required * 100).coerceIn(0.0, 100.0)
                        }
                        AchievementRequirementType.UNIQUE_SCOOTERS -> {
                            val required = achievement.requiredUniqueScooters ?: 1
                            (stats.uniqueScooters.toDouble() / required * 100).coerceIn(0.0, 100.0)
                        }
                        AchievementRequirementType.LONGEST_TRIP -> {
                            val required = achievement.requiredLongestTrip ?: 1.0
                            (stats.longestTrip / required * 100).coerceIn(0.0, 100.0)
                        }
                        AchievementRequirementType.UNIQUE_WEEKS -> {
                            val required = achievement.requiredUniqueWeeks ?: 1
                            (stats.uniqueWeeks.toDouble() / required * 100).coerceIn(0.0, 100.0)
                        }
                        AchievementRequirementType.MAINTENANCE_COUNT -> {
                            val required = achievement.requiredMaintenanceCount ?: 1
                            (stats.maintenanceCount.toDouble() / required * 100).coerceIn(0.0, 100.0)
                        }
                        AchievementRequirementType.CO2_SAVED -> {
                            val required = achievement.requiredCO2Saved ?: 1.0
                            (stats.co2Saved / required * 100).coerceIn(0.0, 100.0)
                        }
                        AchievementRequirementType.UNIQUE_MONTHS -> {
                            val required = achievement.requiredUniqueMonths ?: 1
                            (stats.uniqueMonths.toDouble() / required * 100).coerceIn(0.0, 100.0)
                        }
                        AchievementRequirementType.CONSECUTIVE_MONTHS -> {
                            val required = achievement.requiredConsecutiveMonths ?: 1
                            (stats.consecutiveMonths.toDouble() / required * 100).coerceIn(0.0, 100.0)
                        }
                        AchievementRequirementType.ALL_OTHERS -> {
                            // Calcular porcentaje de logros desbloqueados
                            val otherAchievements = allAchievements.filter { it.id != achievement.id }
                            val unlockedCount = otherAchievements.count { other ->
                                when (other.requirementType) {
                                    AchievementRequirementType.DISTANCE -> 
                                        stats.totalDistance >= (other.requiredDistance ?: 0.0)
                                    AchievementRequirementType.TRIPS -> 
                                        stats.totalTrips >= (other.requiredTrips ?: 0)
                                    AchievementRequirementType.CONSECUTIVE_DAYS -> 
                                        stats.consecutiveDays >= (other.requiredConsecutiveDays ?: 0)
                                    AchievementRequirementType.UNIQUE_SCOOTERS -> 
                                        stats.uniqueScooters >= (other.requiredUniqueScooters ?: 0)
                                    AchievementRequirementType.LONGEST_TRIP -> 
                                        stats.longestTrip >= (other.requiredLongestTrip ?: 0.0)
                                    AchievementRequirementType.UNIQUE_WEEKS -> 
                                        stats.uniqueWeeks >= (other.requiredUniqueWeeks ?: 0)
                                    AchievementRequirementType.MAINTENANCE_COUNT -> 
                                        stats.maintenanceCount >= (other.requiredMaintenanceCount ?: 0)
                                    AchievementRequirementType.CO2_SAVED -> 
                                        stats.co2Saved >= (other.requiredCO2Saved ?: 0.0)
                                    AchievementRequirementType.UNIQUE_MONTHS -> 
                                        stats.uniqueMonths >= (other.requiredUniqueMonths ?: 0)
                                    AchievementRequirementType.CONSECUTIVE_MONTHS -> 
                                        stats.consecutiveMonths >= (other.requiredConsecutiveMonths ?: 0)
                                    else -> false
                                }
                            }
                            (unlockedCount.toDouble() / otherAchievements.size * 100).coerceIn(0.0, 100.0)
                        }
                        AchievementRequirementType.MULTIPLE -> 0.0
                    }
                    
                    achievement.copy(
                        isUnlocked = isUnlocked,
                        progress = if (isUnlocked) 100.0 else progress
                    )
                }
                _achievements.value = AchievementsUiState.Success(unlockedAchievements)
            } catch (e: Exception) {
                _achievements.value = AchievementsUiState.Error("Error al cargar los logros: ${e.message}")
            }
        }
    }

    fun clearSnackbarMessage() {
        achievementsService.clearNotificationMessage()
    }
    
    fun clearLastUnlockedAchievementId() {
        achievementsService.clearLastUnlockedAchievementId()
    }

    /**
     * Verifica si hay nuevos logros desbloqueados.
     * Este método delega al servicio centralizado.
     */
    fun checkNewAchievements() {
        viewModelScope.launch {
            achievementsService.checkAndNotifyNewAchievements()
            // Después de verificar, recargar los logros para actualizar la UI
            loadAchievements()
        }
    }

    fun shareAchievement(achievement: Achievement) {
        viewModelScope.launch {
            try {
                // Obtener el recurso de la imagen del logro
                val imageResId = getAchievementImageResId(achievement.id)
                
                withContext(Dispatchers.IO) {
                    // Convertir drawable a bitmap
                    val drawable = ContextCompat.getDrawable(context, imageResId)
                    val bitmap = drawable?.toBitmap() ?: return@withContext
                    
                    // Crear directorio temporal para compartir
                    val shareDir = File(context.cacheDir, "shared_achievements")
                    if (!shareDir.exists()) {
                        shareDir.mkdirs()
                    }
                    
                    // Crear archivo temporal
                    val imageFile = File(shareDir, "achievement_${achievement.id}.png")
                    
                    // Guardar bitmap como PNG
                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    
                    // Crear URI con FileProvider
                    val imageUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        imageFile
                    )
                    
                    // Crear intent para compartir
                    withContext(Dispatchers.Main) {
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, imageUri)
                            putExtra(Intent.EXTRA_TEXT, achievement.shareMessage)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        val shareIntent = Intent.createChooser(intent, "Compartir logro")
                        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(shareIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("AchievementsVM", "Error al compartir: ${e.message}", e)
            }
        }
    }
    
    private fun getAchievementImageResId(achievementId: String): Int {
        return try {
            // Intentar con el ID del logro (ej: achievement_novato_1)
            var resId = context.resources.getIdentifier(
                "achievement_$achievementId",
                "drawable",
                context.packageName
            )
            
            if (resId == 0) {
                // Intentar sin el prefijo de categoría (ej: achievement_1)
                val shortId = achievementId.substringAfterLast('_')
                resId = context.resources.getIdentifier(
                    "achievement_$shortId",
                    "drawable",
                    context.packageName
                )
            }
            
            if (resId == 0) {
                // Si no se encuentra, usar imagen por defecto
                context.resources.getIdentifier(
                    "ic_achievement",
                    "drawable",
                    context.packageName
                )
            } else {
                resId
            }
        } catch (e: Exception) {
            Log.e("AchievementsVM", "Error al obtener imagen: ${e.message}")
            context.resources.getIdentifier(
                "ic_achievement",
                "drawable",
                context.packageName
            )
        }
    }
}
