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
import com.google.firebase.firestore.FirebaseFirestoreException
import com.zipstats.app.model.Record
import com.zipstats.app.model.Scooter
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.utils.ExcelExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

// Definición de estados de la UI
sealed class RecordsUiState {
    object Loading : RecordsUiState()
    object Success : RecordsUiState()
    data class Error(val message: String) : RecordsUiState()
}

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val recordRepository: RecordRepository,
    private val scooterRepository: VehicleRepository,
    private val achievementsService: com.zipstats.app.service.AchievementsService,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // 1. INPUTS (Filtros modificables por el usuario)
    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    private val _startDate = MutableStateFlow<LocalDate?>(null)
    val startDate: StateFlow<LocalDate?> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<LocalDate?>(null)
    val endDate: StateFlow<LocalDate?> = _endDate.asStateFlow()

    private val _selectedScooterForExport = MutableStateFlow<String?>(null)
    val selectedScooterForExport: StateFlow<String?> = _selectedScooterForExport.asStateFlow()

    // Estado UI simple
    private val _onboardingDismissedInSession = MutableStateFlow(false)
    val onboardingDismissedInSession: StateFlow<Boolean> = _onboardingDismissedInSession.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 2. FUENTES DE DATOS (Sources of Truth)

    // A. Flujo de Patinetes (Cacheado)
    val userScooters: StateFlow<List<Scooter>> = scooterRepository.getScooters()
        .catch { e ->
            // Mismo escudo que usamos en los registros
            val isPermissionError = e.message?.contains("PERMISSION_DENIED") == true ||
                    (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                            e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)

            if (isPermissionError) {
                Log.w("RecordsVM", "Listener de SCOOTERS detenido por logout. Ignorando crash.")
                emit(emptyList()) // Devolvemos lista vacía y no explotamos
            } else {
                Log.e("RecordsVM", "Error cargando scooters", e)
                emit(emptyList())
            }
        }
        // --- FIN DEL CAMBIO ---
        .map { it.sortedBy { scooter -> scooter.nombre } }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // B. Flujo Maestro de Registros (TODOS los registros, sin filtrar)
    private val allRecordsFlow = recordRepository.getRecords()
        .catch { e ->
            // 🛡️ ESCUDO ANTI-CRASH 🛡️
            // Verificamos si es un error de permisos (típico al cerrar sesión)
            val isPermissionError = e.message?.contains("PERMISSION_DENIED") == true ||
                    (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)

            if (isPermissionError) {
                // Si es por logout, silencio total. No actualizamos _errorMessage para no despertar a la UI.
                Log.w("RecordsVM", "Listener de registros detenido por logout (Permiso denegado). Ignorando crash.")
                emit(emptyList())
            } else {
                // Si es otro error real, lo mostramos
                Log.e("RecordsVM", "Error cargando registros", e)
                _errorMessage.value = e.message
                emit(emptyList())
            }
        }
        .flowOn(Dispatchers.IO)

    // StateFlow interno para cálculos síncronos (como calculateDifference)
    private val allRecordsState = allRecordsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // 3. LÓGICA REACTIVA (Combinación y Filtrado)

    // Flujo combinado que aplica todos los filtros automáticamente.
    private val filteredRecordsFlow = combine(
        allRecordsFlow,
        userScooters,
        _selectedModel,
        _startDate,
        _endDate
    ) { records: List<Record>, scooters: List<Scooter>, selectedModel: String?, start: LocalDate?, end: LocalDate? ->

        // Lógica de filtrado (Se ejecuta en background gracias a flowOn más abajo)
        records.filter { record ->
            // 1. Filtro por Modelo
            val matchesModel = if (selectedModel != null) {
                val scooter = scooters.find { it.nombre == record.patinete }
                scooter?.modelo == selectedModel
            } else true

            // 2. Filtro por Fechas
            val matchesDate = if (start != null || end != null) {
                try {
                    val recordDate = LocalDate.parse(record.fecha)
                    val afterStart = start == null || !recordDate.isBefore(start)
                    val beforeEnd = end == null || !recordDate.isAfter(end)
                    afterStart && beforeEnd
                } catch (e: Exception) {
                    true
                }
            } else true

            matchesModel && matchesDate
        }.sortedByDescending { it.fecha } // Ordenación siempre por fecha

    }.flowOn(Dispatchers.Default) // Cálculo pesado en hilo secundario

    // 4. OUTPUTS PÚBLICOS (Lo que ve la UI)

    // Lista principal de registros (Ya filtrada)
    val records: StateFlow<List<Record>> = filteredRecordsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI State global
    val uiState: StateFlow<RecordsUiState> = combine(
        allRecordsFlow,
        filteredRecordsFlow
    ) { all: List<Record>, _: List<Record> ->
        if (all.isEmpty()) {
            RecordsUiState.Success
        } else {
            RecordsUiState.Success
        } as RecordsUiState // Cast necesario para la herencia sellada
    }.catch { e ->
        emit(RecordsUiState.Error(e.message ?: "Error desconocido"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecordsUiState.Loading
    )

    // --- FUNCIONES Y ACCIONES ---

    fun markOnboardingDismissed() {
        _onboardingDismissedInSession.value = true
    }

    fun setSelectedModel(model: String?) {
        _selectedModel.value = model
    }

    fun setStartDate(date: LocalDate?) {
        _startDate.value = date
    }

    fun setEndDate(date: LocalDate?) {
        _endDate.value = date
    }

    fun setSelectedScooterForExport(scooter: String?) {
        _selectedScooterForExport.value = scooter
    }

    // --- OPERACIONES CRUD ---

    fun saveRecord(patinete: String, kilometraje: String, fecha: String) {
        addRecord(patinete, kilometraje, fecha)
    }

    fun addRecord(patinete: String, kilometraje: String, fecha: String) {
        viewModelScope.launch {
            try {
                val kmString = kilometraje.replace(",", ".").trim()
                val kmDouble = kmString.toDoubleOrNull() ?: throw Exception("El kilometraje debe ser un número válido")

                recordRepository.addRecord(
                    vehiculo = patinete,
                    kilometraje = kmDouble,
                    fecha = fecha
                ).onSuccess {
                    achievementsService.checkAndNotifyNewAchievements()
                    Log.d("RecordsVM", "Registro añadido")
                }.onFailure { e ->
                    _errorMessage.value = e.message ?: "Error al guardar"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            try {
                recordRepository.deleteRecord(recordId)
                achievementsService.checkAndNotifyNewAchievements()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun updateRecord(record: Record) {
        viewModelScope.launch {
            try {
                recordRepository.updateRecord(record)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun updateRecord(recordId: String, patinete: String, kilometraje: String, fecha: String) {
        viewModelScope.launch {
            try {
                val kmString = kilometraje.replace(",", ".").trim()
                val kmDouble = kmString.toDoubleOrNull() ?: throw Exception("Kilometraje inválido")

                val diferencia = calculateDifference(patinete, kmDouble, fecha)

                // Usamos allRecordsState.value para tener el histórico completo
                val originalRecord = allRecordsState.value.find { it.id == recordId }
                val isInitial = originalRecord?.isInitialRecord ?: false

                val updatedRecord = Record(
                    id = recordId,
                    vehiculo = patinete,
                    patinete = patinete,
                    kilometraje = kmDouble,
                    fecha = fecha,
                    diferencia = diferencia,
                    isInitialRecord = isInitial
                )

                recordRepository.updateRecord(updatedRecord)
                achievementsService.checkAndNotifyNewAchievements()

            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // Lógica de negocio compleja: Diferencia de Km
    private fun calculateDifference(patinete: String, newKilometraje: Double, fecha: String): Double {
        // IMPORTANTE: Usamos allRecordsState.value (TODOS los registros)
        val registrosAnteriores = allRecordsState.value
            .filter { it.patinete == patinete && it.fecha < fecha }
            .maxByOrNull { it.fecha }

        return if (registrosAnteriores != null) {
            newKilometraje - registrosAnteriores.kilometraje
        } else {
            0.0
        }
    }

    // --- EXPORTACIÓN ---

    fun exportToExcel(context: Context, onExportReady: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Reutilizamos la lógica de filtrado sobre la lista maestra para la exportación
                val recordsToExport = allRecordsState.value.filter { record ->
                    val matchesScooter = _selectedScooterForExport.value?.let { scooter ->
                        record.patinete == scooter
                    } ?: true

                    val matchesDate = if (_startDate.value != null || _endDate.value != null) {
                        try {
                            val rDate = LocalDate.parse(record.fecha)
                            val startOk = _startDate.value == null || !rDate.isBefore(_startDate.value)
                            val endOk = _endDate.value == null || !rDate.isAfter(_endDate.value)
                            startOk && endOk
                        } catch(e: Exception) { true }
                    } else true

                    matchesScooter && matchesDate
                }.sortedByDescending { it.fecha }

                val fileName = "registros_vehiculos_${System.currentTimeMillis()}.xlsx"
                val result = ExcelExporter.exportRecords(context, recordsToExport, fileName)

                result.fold(
                    onSuccess = { file -> withContext(Dispatchers.Main) { onExportReady(file) } },
                    onFailure = { error ->
                        withContext(Dispatchers.Main) { _errorMessage.value = "Error export: ${error.message}" }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _errorMessage.value = "Error export: ${e.message}" }
            }
        }
    }

    // --- PERMISOS (Sin cambios) ---

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestStoragePermission(activity: android.app.Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), STORAGE_PERMISSION_REQUEST_CODE)
        } else {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_REQUEST_CODE)
        }
    }

    companion object {
        const val STORAGE_PERMISSION_REQUEST_CODE = 1001
        const val EXPORT_EXCEL_REQUEST_CODE = 1003
    }

    // Al destruir el ViewModel, cancelamos todo para evitar fugas de memoria o crashes
    override fun onCleared() {
        super.onCleared()
        // Esto fuerza la desconexión de los listeners de Firestore
        // aunque Android suele hacerlo solo, esto es un seguro de vida.
        try {
            // Opcional: Si tienes alguna lógica de limpieza manual
        } catch (e: Exception) {
            // Ignorar
        }
    }
}