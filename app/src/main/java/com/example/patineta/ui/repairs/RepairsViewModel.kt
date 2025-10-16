package com.example.patineta.ui.repairs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.patineta.model.Repair
import com.example.patineta.repository.RepairRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import com.example.patineta.repository.RecordRepository
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
    private val achievementsService: com.example.patineta.service.AchievementsService
) : ViewModel() {

    private val _uiState = MutableStateFlow<RepairsUiState>(RepairsUiState.Loading)
    val uiState: StateFlow<RepairsUiState> = _uiState.asStateFlow()

    private var currentScooterId: String? = null

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

    fun addRepair(date: LocalDate, description: String, mileage: Double?) {
        currentScooterId?.let { scooterId ->
            viewModelScope.launch {
                try {
                    val resolvedMileage = mileage ?: recordRepository.getPreviousMileageForDate(
                        patinete = scooterId,
                        fechaIso = date.toString()
                    )
                    val repair = Repair(
                        vehicleId = scooterId,
                        scooterId = scooterId,
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