package com.zipstats.app.ui.tracking

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zipstats.app.model.Route
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.model.Scooter
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.RouteRepository
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.service.LocationTrackingService
import com.zipstats.app.service.TrackingStateManager
import com.zipstats.app.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject

/**
 * Estado del seguimiento de rutas
 */
sealed class TrackingState {
    object Idle : TrackingState()
    object Tracking : TrackingState()
    object Paused : TrackingState()
    object Saving : TrackingState()
    data class Error(val message: String) : TrackingState()
}

/**
 * ViewModel para gestionar el seguimiento de rutas GPS
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    application: Application,
    private val routeRepository: RouteRepository,
    private val scooterRepository: VehicleRepository,
    private val recordRepository: RecordRepository,
    private val preferencesManager: PreferencesManager
) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    
    // Manager de estado global
    private val trackingStateManager = TrackingStateManager
    
    // Servicio de seguimiento
    private var trackingService: LocationTrackingService? = null
    private var serviceBound = false
    
    // Estado del tracking
    private val _trackingState = MutableStateFlow<TrackingState>(TrackingState.Idle)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()
    
    // Patinete seleccionado para la ruta
    private val _selectedScooter = MutableStateFlow<Scooter?>(null)
    val selectedScooter: StateFlow<Scooter?> = _selectedScooter.asStateFlow()
    
    // Datos del servicio
    private val _routePoints = MutableStateFlow<List<RoutePoint>>(emptyList())
    val routePoints: StateFlow<List<RoutePoint>> = _routePoints.asStateFlow()
    
    private val _currentDistance = MutableStateFlow(0.0)
    val currentDistance: StateFlow<Double> = _currentDistance.asStateFlow()
    
    private val _currentSpeed = MutableStateFlow(0.0)
    val currentSpeed: StateFlow<Double> = _currentSpeed.asStateFlow()
    
    private val _averageMovingSpeed = MutableStateFlow(0.0)
    val averageMovingSpeed: StateFlow<Double> = _averageMovingSpeed.asStateFlow()
    
    private val _timeInMotion = MutableStateFlow(0L)
    val timeInMotion: StateFlow<Long> = _timeInMotion.asStateFlow()
    
    private val _startTime = MutableStateFlow(0L)
    val startTime: StateFlow<Long> = _startTime.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    // Variables para manejar la pausa
    private var _pausedDuration = 0L // Tiempo acumulado durante pausas
    private var _pauseStartTime = 0L // Tiempo cuando se pausó
    
    // Estado de la señal GPS
    private val _gpsSignalStrength = MutableStateFlow(0f) // 0-100
    val gpsSignalStrength: StateFlow<Float> = _gpsSignalStrength.asStateFlow()
    
    // Lista de patinetes disponibles
    private val _scooters = MutableStateFlow<List<Scooter>>(emptyList())
    val scooters: StateFlow<List<Scooter>> = _scooters.asStateFlow()
    
    // Mensaje de resultado
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationTrackingService.LocalBinder
            trackingService = binder.getService()
            serviceBound = true
            
            // Observar cambios del servicio
            observeService()
            
            Log.d(TAG, "Servicio conectado")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
            serviceBound = false
            Log.d(TAG, "Servicio desconectado")
        }
    }

    init {
        loadScooters()
        // Sincronizar con el estado global del servicio
        syncWithGlobalState()
    }

    /**
     * Sincroniza el estado del ViewModel con el estado global del servicio
     */
    private fun syncWithGlobalState() {
        viewModelScope.launch {
            // Observar el estado global de tracking
            trackingStateManager.isTracking.collect { isTracking ->
                if (isTracking) {
                    // Si hay tracking activo, conectar con el servicio
                    if (!serviceBound) {
                        connectToService()
                    }
                    _trackingState.value = if (trackingStateManager.isPaused.value) {
                        TrackingState.Paused
                    } else {
                        TrackingState.Tracking
                    }
                } else {
                    // Si no hay tracking activo, resetear estado local
                    _trackingState.value = TrackingState.Idle
                    _routePoints.value = emptyList()
                    _currentDistance.value = 0.0
                    _currentSpeed.value = 0.0
                    _averageMovingSpeed.value = 0.0
                    _timeInMotion.value = 0L
                    _duration.value = 0L
                    _gpsSignalStrength.value = 0f
                }
            }
        }
    }
    
    /**
     * Conecta con el servicio de tracking si está activo
     */
    private fun connectToService() {
        try {
            val intent = Intent(context, LocationTrackingService::class.java)
            val bound = context.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            if (bound) {
                serviceBound = true
                Log.d(TAG, "Conectado al servicio existente")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al conectar con servicio existente", e)
        }
    }

    /**
     * Carga la lista de vehículos del usuario y selecciona el último usado
     */
    private fun loadScooters() {
        viewModelScope.launch {
            scooterRepository.getScooters().collect { scootersList ->
                _scooters.value = scootersList
                
                // Auto-seleccionar vehículo
                if (_selectedScooter.value == null && scootersList.isNotEmpty()) {
                    // Intentar cargar el último vehículo usado
                    val lastUsedScooterId = loadLastUsedScooter()
                    val scooterToSelect = if (lastUsedScooterId != null) {
                        scootersList.find { it.id == lastUsedScooterId }
                    } else {
                        null
                    }
                    
                    // Si no hay último usado o no existe, seleccionar el primero
                    _selectedScooter.value = scooterToSelect ?: scootersList.firstOrNull()
                }
            }
        }
    }
    
    /**
     * Carga el ID del último vehículo usado desde preferencias
     */
    private suspend fun loadLastUsedScooter(): String? {
        return try {
            preferencesManager.getLastUsedScooterId()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar último vehículo usado", e)
            null
        }
    }
    
    /**
     * Guarda el ID del último vehículo usado en preferencias
     */
    private suspend fun saveLastUsedScooter(scooterId: String) {
        try {
            preferencesManager.saveLastUsedScooterId(scooterId)
            Log.d(TAG, "Último vehículo guardado: $scooterId")
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar último vehículo", e)
        }
    }

    /**
     * Selecciona un vehículo para la ruta y lo guarda como último usado
     */
    fun selectScooter(scooter: Scooter) {
        _selectedScooter.value = scooter
        viewModelScope.launch {
            saveLastUsedScooter(scooter.id)
        }
    }

    /**
     * Inicia el seguimiento de la ruta
     */
    fun startTracking() {
        val scooter = _selectedScooter.value
        if (scooter == null) {
            _message.value = "Por favor, selecciona un vehículo primero"
            return
        }

        viewModelScope.launch {
            try {
                // Iniciar y vincular el servicio
                val intent = Intent(context, LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_START_TRACKING
                }
                context.startForegroundService(intent)
                context.bindService(
                    Intent(context, LocationTrackingService::class.java),
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
                )
                
                _trackingState.value = TrackingState.Tracking
                trackingStateManager.updateTrackingState(true)
                trackingStateManager.updatePausedState(false)
                Log.d(TAG, "Seguimiento iniciado")
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar seguimiento", e)
                _trackingState.value = TrackingState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Pausa el seguimiento
     */
    fun pauseTracking() {
        trackingService?.pauseTracking()
        _trackingState.value = TrackingState.Paused
        trackingStateManager.updatePausedState(true)
        
        // Guardar el tiempo cuando se pausa
        _pauseStartTime = System.currentTimeMillis()
    }

    /**
     * Reanuda el seguimiento
     */
    fun resumeTracking() {
        trackingService?.resumeTracking()
        _trackingState.value = TrackingState.Tracking
        trackingStateManager.updatePausedState(false)
        
        // Calcular tiempo de pausa y acumularlo
        if (_pauseStartTime > 0) {
            val pauseDuration = System.currentTimeMillis() - _pauseStartTime
            _pausedDuration += pauseDuration
            _pauseStartTime = 0L
        }
    }

    /**
     * Finaliza y guarda la ruta
     */
    fun finishTracking(notes: String = "", addToRecords: Boolean = false) {
        viewModelScope.launch {
            try {
                // Detener el servicio inmediatamente
                stopTrackingService()
                
                // Guardar datos antes de resetear
                val scooter = _selectedScooter.value ?: throw Exception("No hay vehículo seleccionado")
                val points = _routePoints.value
                val startTime = _startTime.value
                val endTime = System.currentTimeMillis()
                
                // Resetear estado inmediatamente
                _trackingState.value = TrackingState.Saving
                trackingStateManager.resetState()
                _routePoints.value = emptyList()
                _currentDistance.value = 0.0
                _currentSpeed.value = 0.0
                _duration.value = 0L
                
                if (points.isEmpty()) {
                    throw Exception("No se registraron puntos GPS")
                }
                
                // Crear la ruta
                val route = routeRepository.createRouteFromPoints(
                    points = points,
                    scooterId = scooter.id,
                    scooterName = scooter.nombre,
                    startTime = startTime,
                    endTime = endTime,
                    notes = notes,
                    timeInMotion = _timeInMotion.value
                )
                
                // Guardar en Firebase
                val result = routeRepository.saveRoute(route)
                
                if (result.isSuccess) {
                    var message = "Ruta guardada exitosamente: ${String.format("%.1f", route.totalDistance.roundToOneDecimal())} km"
                    
                    // Si se solicita añadir a registros
                    if (addToRecords) {
                        try {
                            Log.d(TAG, "Intentando añadir ruta a registros para patinete: ${scooter.nombre}")
                            
                            // Obtener la fecha actual
                            val currentDate = java.time.LocalDate.now()
                            val formattedDate = com.zipstats.app.utils.DateUtils.formatForApi(currentDate)
                            
                            // Añadir registro con la distancia total acumulada
                            val allRecords = recordRepository.getRecords().first()
                            Log.d(TAG, "Registros obtenidos: ${allRecords.size}")
                            
                            val lastRecord = allRecords
                                .filter { it.patinete == scooter.nombre }
                                .maxByOrNull { it.fecha }
                            
                            Log.d(TAG, "Último registro encontrado: $lastRecord")
                            
                            val newKilometraje = if (lastRecord != null) {
                                lastRecord.kilometraje + route.totalDistance
                            } else {
                                route.totalDistance
                            }
                            
                            Log.d(TAG, "Nuevo kilometraje: $newKilometraje")
                            
                            val addResult = recordRepository.addRecord(
                                vehiculo = scooter.nombre,
                                kilometraje = newKilometraje,
                                fecha = formattedDate
                            )
                            
                            if (addResult.isSuccess) {
                                Log.d(TAG, "Registro añadido exitosamente")
                                message += "\nDistancia añadida a registros: ${String.format("%.1f", route.totalDistance.roundToOneDecimal())} km"
                            } else {
                                Log.w(TAG, "Error al añadir a registros: ${addResult.exceptionOrNull()?.message}")
                                message += "\nRuta guardada, pero error al añadir a registros"
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error al añadir a registros: ${e.message}")
                            message += "\nRuta guardada, pero error al añadir a registros"
                        }
                    }
                    
                    _message.value = message
                    _trackingState.value = TrackingState.Idle
                } else {
                    throw result.exceptionOrNull() ?: Exception("Error al guardar la ruta")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al finalizar ruta", e)
                _trackingState.value = TrackingState.Error(e.message ?: "Error al guardar la ruta")
                _message.value = "Error: ${e.message}"
            }
        }
    }

    /**
     * Cancela el seguimiento sin guardar
     */
    fun cancelTracking() {
        stopTrackingService()
        _trackingState.value = TrackingState.Idle
        trackingStateManager.resetState()
        _routePoints.value = emptyList()
        _currentDistance.value = 0.0
        _currentSpeed.value = 0.0
        _duration.value = 0L
        _message.value = "Ruta cancelada"
    }

    /**
     * Detiene el servicio de seguimiento
     */
    private fun stopTrackingService() {
        try {
            if (serviceBound) {
                context.unbindService(serviceConnection)
                serviceBound = false
            }
            
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_STOP_TRACKING
            }
            context.startService(intent)
            
            trackingService = null
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener servicio", e)
        }
    }

    /**
     * Observa los cambios en el servicio de seguimiento
     */
    private fun observeService() {
        viewModelScope.launch {
            trackingService?.routePoints?.collect { points ->
                _routePoints.value = points
            }
        }
        
        viewModelScope.launch {
            trackingService?.currentDistance?.collect { distance ->
                _currentDistance.value = distance
            }
        }
        
        viewModelScope.launch {
            trackingService?.currentSpeed?.collect { speed ->
                _currentSpeed.value = speed
            }
        }
        
        viewModelScope.launch {
            trackingService?.averageMovingSpeed?.collect { speed ->
                _averageMovingSpeed.value = speed
            }
        }
        
        viewModelScope.launch {
            trackingService?.timeInMotion?.collect { time ->
                _timeInMotion.value = time
            }
        }
        
        viewModelScope.launch {
            trackingService?.startTime?.collect { time ->
                _startTime.value = time
                if (time > 0) {
                    startDurationTimer()
                }
            }
        }
        
        viewModelScope.launch {
            trackingService?.isPaused?.collect { isPaused ->
                _trackingState.value = if (isPaused) {
                    TrackingState.Paused
                } else {
                    TrackingState.Tracking
                }
            }
        }
        
        // Observar precisión GPS para calcular fuerza de señal
        viewModelScope.launch {
            trackingService?.routePoints?.collect { points ->
                if (points.isNotEmpty()) {
                    val lastPoint = points.last()
                    // Convertir precisión a fuerza de señal (0-100)
                    // Precisión menor = señal más fuerte
                    val accuracy = lastPoint.accuracy ?: 100f // Usar 100m si no hay precisión
                    val signalStrength = when {
                        accuracy <= 5f -> 100f // Excelente (≤5m)
                        accuracy <= 10f -> 80f  // Buena (≤10m)
                        accuracy <= 20f -> 60f  // Regular (≤20m)
                        accuracy <= 50f -> 40f  // Débil (≤50m)
                        else -> 20f // Muy débil (>50m)
                    }
                    _gpsSignalStrength.value = signalStrength
                    Log.d(TAG, "GPS Signal - Accuracy: ${accuracy}m, Strength: ${signalStrength}%")
                }
            }
        }
    }

    /**
     * Inicia un temporizador para actualizar la duración
     */
    private fun startDurationTimer() {
        viewModelScope.launch {
            while (_trackingState.value is TrackingState.Tracking || 
                   _trackingState.value is TrackingState.Paused) {
                if (_startTime.value > 0) {
                    // Calcular duración total menos el tiempo de pausas
                    val currentPauseTime = if (_trackingState.value is TrackingState.Paused && _pauseStartTime > 0) {
                        System.currentTimeMillis() - _pauseStartTime
                    } else {
                        0L
                    }
                    _duration.value = System.currentTimeMillis() - _startTime.value - _pausedDuration - currentPauseTime
                }
                kotlinx.coroutines.delay(1000) // Actualizar cada segundo
            }
        }
    }

    /**
     * Limpia el mensaje
     */
    fun clearMessage() {
        _message.value = null
    }

    override fun onCleared() {
        super.onCleared()
        if (serviceBound) {
            context.unbindService(serviceConnection)
        }
    }

    companion object {
        private const val TAG = "TrackingViewModel"
    }
    
    private fun Double.roundToOneDecimal(): Double {
        return (this * 10.0).roundToInt() / 10.0
    }
}

