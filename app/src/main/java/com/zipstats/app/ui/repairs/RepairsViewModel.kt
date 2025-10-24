package com.zipstats.app.ui.repairs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zipstats.app.model.Repair
import com.zipstats.app.model.Scooter
import com.zipstats.app.repository.RepairRepository
import com.zipstats.app.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import com.zipstats.app.repository.RecordRepository
import javax.inject.Inject

sealed class RepairsUiState {
    object Loading : RepairsUiState()
    data class Success(val repairs: List<Repair>) : RepairsUiState()
    data class Error(val message: String) : RepairsUiState()
}

@HiltViewModel
class RepairsViewModel @Inject constructor(
    private val repairRepository: RepairRepository,
    private val recordRepository: RecordRepository,
    private val scooterRepository: VehicleRepository,
    private val achievementsService: com.zipstats.app.service.AchievementsService
) : ViewModel() {

    private val _uiState = MutableStateFlow<RepairsUiState>(RepairsUiState.Loading)
    val uiState: StateFlow<RepairsUiState> = _uiState.asStateFlow()
    
    private val _scooterState = MutableStateFlow<Scooter?>(null)
    val scooterState: StateFlow<Scooter?> = _scooterState.asStateFlow()

    private var currentScooterId: String? = null

    fun loadScooterAndRepairs(scooterId: String) {
        currentScooterId = scooterId
        viewModelScope.launch {
            try {
                // Cargar el scooter completo
                val scooters = scooterRepository.getUserScooters()
                val scooter = scooters.find { it.id == scooterId }
                if (scooter != null) {
                    _scooterState.value = scooter
                    // Cargar reparaciones
                    loadRepairs(scooterId)
                } else {
                    _uiState.value = RepairsUiState.Error("Vehículo no encontrado")
                }
            } catch (e: Exception) {
                _uiState.value = RepairsUiState.Error(e.message ?: "Error al cargar el vehículo")
            }
        }
    }

    fun loadRepairs(scooterId: String) {
        currentScooterId = scooterId
        viewModelScope.launch {
            _uiState.value = RepairsUiState.Loading
            try {
                repairRepository.getRepairsForScooter(scooterId).collect { repairs ->
                    _uiState.value = RepairsUiState.Success(repairs)
                }
            } catch (e: Exception) {
                _uiState.value = RepairsUiState.Error(e.message ?: "Error al cargar las reparaciones")
            }
        }
    }

    fun addRepair(date: LocalDate, description: String, mileage: Double?, scooterName: String, scooterId: String) {
        viewModelScope.launch {
            try {
                val resolvedMileage = mileage ?: recordRepository.getPreviousMileageForDate(
                    patinete = scooterName,
                    fechaIso = date.toString()
                )
                val repair = Repair(
                    vehicleId = scooterId,
                    scooterId = scooterId,
                    patinete = scooterName,
                    date = date,
                    description = description,
                    mileage = resolvedMileage
                )
                
                repairRepository.addRepair(repair)
                
                // Verificar logros después de añadir reparación
                achievementsService.checkAndNotifyNewAchievements()
            } catch (e: Exception) {
                _uiState.value = RepairsUiState.Error(e.message ?: "Error al agregar la reparación")
            }
        }
    }

    fun deleteRepair(repairId: String) {
        viewModelScope.launch {
            try {
                repairRepository.deleteRepair(repairId)
                
                // Verificar logros después de eliminar reparación
                achievementsService.checkAndNotifyNewAchievements()
            } catch (e: Exception) {
                _uiState.value = RepairsUiState.Error(e.message ?: "Error al eliminar la reparación")
            }
        }
    }

    fun refreshRepairs() {
        currentScooterId?.let { loadRepairs(it) }
    }

    fun updateRepair(updated: Repair) {
        viewModelScope.launch {
            try {
                repairRepository.updateRepair(updated)
                
                // Verificar logros después de actualizar reparación
                achievementsService.checkAndNotifyNewAchievements()
            } catch (e: Exception) {
                _uiState.value = RepairsUiState.Error(e.message ?: "Error al actualizar la reparación")
            }
        }
    }

    // Eliminado: ahora usamos RecordRepository.getPreviousMileageForDate
} 