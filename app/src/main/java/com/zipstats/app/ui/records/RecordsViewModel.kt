package com.zipstats.app.ui.records

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.zipstats.app.model.Record
import com.zipstats.app.model.Scooter
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.utils.ExcelExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val recordRepository: RecordRepository,
    private val scooterRepository: VehicleRepository,
    private val achievementsService: com.zipstats.app.service.AchievementsService,
    private val auth: FirebaseAuth,
    private val context: Context
) : ViewModel() {

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    private val _startDate = MutableStateFlow<LocalDate?>(null)
    val startDate: StateFlow<LocalDate?> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<LocalDate?>(null)
    val endDate: StateFlow<LocalDate?> = _endDate.asStateFlow()

    private val _selectedScooterForExport = MutableStateFlow<String?>(null)
    val selectedScooterForExport: StateFlow<String?> = _selectedScooterForExport.asStateFlow()

    private val _lastEightRecords = MutableStateFlow<List<Record>>(emptyList())
    val lastEightRecords: StateFlow<List<Record>> = _lastEightRecords.asStateFlow()

    private val _records = MutableStateFlow<List<Record>>(emptyList())
    val records: StateFlow<List<Record>> = _records.asStateFlow()

    private val _userScooters = MutableStateFlow<List<Scooter>>(emptyList())
    val userScooters: StateFlow<List<Scooter>> = _userScooters.asStateFlow()

    private val _uiState = MutableStateFlow<RecordsUiState>(RecordsUiState.Loading)
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()
    
    // Estado para controlar si el onboarding se ha descartado en esta sesión
    private val _onboardingDismissedInSession = MutableStateFlow(false)
    val onboardingDismissedInSession: StateFlow<Boolean> = _onboardingDismissedInSession.asStateFlow()
    
    fun markOnboardingDismissed() {
        _onboardingDismissedInSession.value = true
    }

    // Función auxiliar para validar fechas
    private fun isValidDate(dateStr: String): Boolean {
        return try {
            if (!dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                return false
            }
            val (year, month, day) = dateStr.split("-").map { it.toInt() }
            if (year < 1900 || year > 2100) return false
            if (month < 1 || month > 12) return false
            
            val daysInMonth = when (month) {
                2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
            
            day in 1..daysInMonth
        } catch (e: Exception) {
            false
        }
    }

    init {
        loadUserScooters()
        loadRecords()

        // Observar cambios en el modelo seleccionado y actualizar los registros
        viewModelScope.launch {
            combine(
                _records,
                _userScooters,
                _selectedModel
            ) { records, scooters, selectedModel ->
                Triple(records, scooters, selectedModel)
            }.collect { (records, scooters, selectedModel) ->
                updateLastEightRecords(records, scooters, selectedModel)
            }
        }
    }

    private fun updateLastEightRecords(
        records: List<Record>,
        scooters: List<Scooter>,
        selectedModel: String?
    ) {
        val filteredRecords = when (selectedModel) {
            null -> records
            else -> records.filter { record ->
                scooters.find { it.nombre == record.patinete }?.modelo == selectedModel
            }
        }
        _lastEightRecords.value = filteredRecords.sortedByDescending { it.fecha }.take(8)
    }

    fun setSelectedModel(model: String?) {
        _selectedModel.value = model
    }

    private fun loadUserScooters() {
        viewModelScope.launch {
            try {
                scooterRepository.getScooters().collect { scooters ->
                    _userScooters.value = scooters.sortedBy { it.nombre }
                }
            } catch (e: Exception) {
                _uiState.value = RecordsUiState.Error(e.message ?: "Error al cargar los patinetes")
            }
        }
    }

    private fun loadRecords() {
        viewModelScope.launch {
            try {
                recordRepository.getRecords().collect { allRecords ->
                    val sortedRecords = allRecords.sortedByDescending { it.fecha }
                    _records.value = sortedRecords
                    // Actualizar también los últimos 8 registros
                    updateLastEightRecords(sortedRecords, _userScooters.value, _selectedModel.value)
                    _uiState.value = RecordsUiState.Success
                }
            } catch (e: Exception) {
                _uiState.value = RecordsUiState.Error(e.message ?: "Error al cargar los registros")
            }
        }
    }

    fun saveRecord(patinete: String, kilometraje: String, fecha: String) {
        viewModelScope.launch {
            try {
                // Reemplazar la coma por punto y eliminar espacios
                val kmString = kilometraje.replace(",", ".").trim()
                val kmDouble = kmString.toDoubleOrNull() ?: return@launch
                
                // Guardar el nuevo registro
                recordRepository.addRecord(
                    vehiculo = patinete,
                    kilometraje = kmDouble,
                    fecha = fecha
                ).onSuccess {
                    _uiState.value = RecordsUiState.Success
                    
                    // Verificar logros después de añadir el registro
                    achievementsService.checkAndNotifyNewAchievements()
                    Log.d("RecordsVM", "Registro guardado, verificando logros")
                }.onFailure { e ->
                    _uiState.value = RecordsUiState.Error(e.message ?: "Error al guardar el registro")
                }
            } catch (e: Exception) {
                _uiState.value = RecordsUiState.Error(e.message ?: "Error al guardar el registro")
            }
        }
    }

    fun addRecord(patinete: String, kilometraje: String, fecha: String) {
        viewModelScope.launch {
            try {
                // Reemplazar la coma por punto y eliminar espacios
                val kmString = kilometraje.replace(",", ".").trim()
                val kmDouble = kmString.toDoubleOrNull() ?: throw Exception("El kilometraje debe ser un número válido")
                
                // Guardar el nuevo registro
                recordRepository.addRecord(
                    vehiculo = patinete,
                    kilometraje = kmDouble,
                    fecha = fecha
                ).onSuccess {
                    _uiState.value = RecordsUiState.Success
                    
                    // Verificar logros después de añadir el registro
                    achievementsService.checkAndNotifyNewAchievements()
                    Log.d("RecordsVM", "Registro añadido, verificando logros")
                }.onFailure { e ->
                    _uiState.value = RecordsUiState.Error(e.message ?: "Error al guardar el registro")
                }
            } catch (e: Exception) {
                _uiState.value = RecordsUiState.Error(e.message ?: "Error al guardar el registro")
            }
        }
    }

    private fun calculateDifference(patinete: String, newKilometraje: Double, fecha: String): Double {
        val registrosAnteriores = records.value
            .filter { it.vehicleName == patinete && it.fecha < fecha }
            .maxByOrNull { it.fecha }

        return if (registrosAnteriores != null) {
            newKilometraje - registrosAnteriores.kilometraje
        } else {
            0.0
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            try {
                recordRepository.deleteRecord(recordId)
                
                // Verificar logros después de eliminar el registro
                achievementsService.checkAndNotifyNewAchievements()
                Log.d("RecordsVM", "Registro eliminado, verificando logros")
            } catch (e: Exception) {
                _uiState.value = RecordsUiState.Error(e.message ?: "Error al eliminar el registro")
            }
        }
    }

    fun updateRecord(record: Record) {
        viewModelScope.launch {
            try {
                recordRepository.updateRecord(record)
            } catch (e: Exception) {
                _uiState.value = RecordsUiState.Error(e.message ?: "Error al actualizar el registro")
            }
        }
    }

    fun updateRecord(recordId: String, patinete: String, kilometraje: String, fecha: String) {
        viewModelScope.launch {
            try {
                // Reemplazar la coma por punto y eliminar espacios
                val kmString = kilometraje.replace(",", ".").trim()
                val kmDouble = kmString.toDoubleOrNull() ?: throw Exception("El kilometraje debe ser un número válido")
                
                // Calcular la diferencia para el registro actualizado
                val diferencia = calculateDifference(patinete, kmDouble, fecha)
                
                // Obtener el registro original para verificar si es un registro inicial
                val originalRecord = records.value.find { it.id == recordId }
                val isInitial = originalRecord?.isInitialRecord ?: false
                
                // Crear el registro actualizado con la diferencia calculada
                val updatedRecord = Record(
                    id = recordId,
                    vehiculo = patinete,
                    patinete = patinete,
                    kilometraje = kmDouble,
                    fecha = fecha,
                    diferencia = diferencia,
                    isInitialRecord = isInitial
                )
                
                // Actualizar en el repositorio
                recordRepository.updateRecord(updatedRecord)
                
                // Verificar logros después de actualizar el registro
                achievementsService.checkAndNotifyNewAchievements()
                Log.d("RecordsVM", "Registro actualizado, verificando logros")
                
            } catch (e: Exception) {
                _uiState.value = RecordsUiState.Error(e.message ?: "Error al actualizar el registro")
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestStoragePermission(activity: android.app.Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }

    fun setStartDate(date: LocalDate?) {
        _startDate.value = date
        updateFilteredRecords()
    }

    fun setEndDate(date: LocalDate?) {
        _endDate.value = date
        updateFilteredRecords()
    }

    fun setSelectedScooterForExport(scooter: String?) {
        _selectedScooterForExport.value = scooter
    }

    private fun updateFilteredRecords() {
        val filteredRecords = _records.value.filter { record ->
            val recordDate = LocalDate.parse(record.fecha)
            val matchesDate = when {
                _startDate.value != null && _endDate.value != null -> 
                    !recordDate.isBefore(_startDate.value) && 
                    !recordDate.isAfter(_endDate.value)
                _startDate.value != null -> 
                    !recordDate.isBefore(_startDate.value)
                _endDate.value != null -> 
                    !recordDate.isAfter(_endDate.value)
                else -> true
            }
            val matchesScooter = _selectedModel.value?.let { model ->
                _userScooters.value.find { it.nombre == record.patinete }?.modelo == model
            } ?: true
            matchesDate && matchesScooter
        }
        _lastEightRecords.value = filteredRecords.sortedByDescending { it.fecha }
    }

    fun exportToExcel(context: Context, onExportReady: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Filtrar registros por patinete y fechas
                val recordsToExport = _records.value.filter { record ->
                    val matchesScooter = _selectedScooterForExport.value?.let { scooter ->
                        record.patinete == scooter
                    } ?: true
                    
                    val recordDate = LocalDate.parse(record.fecha)
                    val matchesDate = when {
                        _startDate.value != null && _endDate.value != null -> 
                            !recordDate.isBefore(_startDate.value) && 
                            !recordDate.isAfter(_endDate.value)
                        _startDate.value != null -> 
                            !recordDate.isBefore(_startDate.value)
                        _endDate.value != null -> 
                            !recordDate.isAfter(_endDate.value)
                        else -> true
                    }
                    
                    matchesScooter && matchesDate
                }
                
                // Usar el nuevo ExcelExporter
                val fileName = "registros_vehiculos_${System.currentTimeMillis()}.xlsx"
                val result = ExcelExporter.exportRecords(context, recordsToExport, fileName)
                
                result.fold(
                    onSuccess = { file ->
                        withContext(Dispatchers.Main) {
                            onExportReady(file)
                        }
                    },
                    onFailure = { error ->
                        withContext(Dispatchers.Main) {
                            _uiState.value = RecordsUiState.Error("Error al exportar a Excel: ${error.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = RecordsUiState.Error("Error al exportar a Excel: ${e.message}")
                }
            }
        }
    }

    companion object {
        const val STORAGE_PERMISSION_REQUEST_CODE = 1001
        const val EXPORT_EXCEL_REQUEST_CODE = 1003
    }
}

sealed class RecordsUiState {
    object Loading : RecordsUiState()
    object Success : RecordsUiState()
    data class Error(val message: String) : RecordsUiState()
} 