package com.zipstats.app.ui.repairs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zipstats.app.model.Repair
import com.zipstats.app.model.Scooter
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.RepairRepository
import com.zipstats.app.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
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

    // 1. INPUT: ID del scooter seleccionado
    private val _currentScooterId = MutableStateFlow<String?>(null)

    // 2. OUTPUT REACTIVO: Estado del scooter seleccionado
    @OptIn(ExperimentalCoroutinesApi::class)
    val scooterState: StateFlow<Scooter?> = _currentScooterId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else scooterRepository.getScooters().flatMapLatest { scooters ->
                flowOf(scooters.find { it.id == id })
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // 3. OUTPUT REACTIVO: Estado de la lista de reparaciones
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RepairsUiState> = _currentScooterId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                repairRepository.getRepairsForScooter(id)
            }
        }
        .combine(scooterState) { repairs: List<Repair>, scooter: Scooter? ->
            if (scooter == null && _currentScooterId.value != null) {
                RepairsUiState.Error("Vehículo no encontrado")
            } else {
                RepairsUiState.Success(repairs)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RepairsUiState.Loading
        )

    // --- ACCIONES ---

    fun loadScooterAndRepairs(scooterId: String) {
        _currentScooterId.value = scooterId
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
                // El error se manejaría idealmente con un SharedFlow de eventos (Snackbar),
                // pero por ahora mantenemos el patrón simple o logs.
                // _uiState es derivado, no podemos setearlo directamente a Error aquí fácilmente
                // sin romper el flujo reactivo. Podríamos usar un canal de efectos secundarios.
            }
        }
    }

    fun deleteRepair(repairId: String) {
        viewModelScope.launch {
            try {
                repairRepository.deleteRepair(repairId)
                achievementsService.checkAndNotifyNewAchievements()
            } catch (e: Exception) {
                // Manejo de error
            }
        }
    }

    fun refreshRepairs() {
        // En el patrón reactivo, "refresh" suele ser automático si la fuente de datos cambia.
        // Si el repositorio no es reactivo real (Firestore lo es), aquí recargaríamos.
        // Dado que usamos Flows de Firestore, esto podría no ser necesario, 
        // pero lo mantenemos por compatibilidad si se fuerza una recarga manual.
        val currentId = _currentScooterId.value
        if (currentId != null) {
            // Un truco sucio para forzar re-emisión es poner null y volver a poner el ID,
            // pero con Firestore listeners no debería hacer falta.
            // _currentScooterId.value = null
            // _currentScooterId.value = currentId
        }
    }

    fun updateRepair(updated: Repair) {
        viewModelScope.launch {
            try {
                repairRepository.updateRepair(updated)
                achievementsService.checkAndNotifyNewAchievements()
            } catch (e: Exception) {
                // Manejo de error
            }
        }
    }
}
