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
import com.zipstats.app.tracking.LocationTracker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import android.location.Location
import android.os.Looper
import android.annotation.SuppressLint
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
 * Estado de la captura del clima
 */
sealed class WeatherStatus {
    object Idle : WeatherStatus()
    object Loading : WeatherStatus()
    data class Success(val temperature: Double, val emoji: String) : WeatherStatus()
    data class Error(val message: String) : WeatherStatus()
    object NotAvailable : WeatherStatus()
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
    
    // Estado de GPS previo (para posicionamiento antes de iniciar)
    sealed class GpsPreLocationState {
        object Searching : GpsPreLocationState()
        data class Found(val accuracy: Float) : GpsPreLocationState()
        object Ready : GpsPreLocationState()
    }
    
    private val _gpsPreLocationState = MutableStateFlow<GpsPreLocationState>(GpsPreLocationState.Searching)
    val gpsPreLocationState: StateFlow<GpsPreLocationState> = _gpsPreLocationState.asStateFlow()
    
    private val _preLocation = MutableStateFlow<Location?>(null)
    val preLocation: StateFlow<Location?> = _preLocation.asStateFlow()
    
    // Cliente de ubicación para GPS previo
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationTracker: LocationTracker
    private var preLocationCallback: LocationCallback? = null
    private var isPreLocationActive = false
    
    // Clima capturado al inicio de la ruta
    private var _startWeatherTemperature: Double? = null
    private var _startWeatherEmoji: String? = null
    private var _startWeatherDescription: String? = null
    
    // Estado del clima
    private val _weatherStatus = MutableStateFlow<WeatherStatus>(WeatherStatus.Idle)
    val weatherStatus: StateFlow<WeatherStatus> = _weatherStatus.asStateFlow()
    
    // Job para manejo del clima en segundo plano
    private var weatherJob: kotlinx.coroutines.Job? = null
    
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
        
        // Inicializar cliente de ubicación para GPS previo
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        locationTracker = LocationTracker(context)
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
     * Inicia la escucha de GPS previa (sin grabar ruta aún)
     * Se llama cuando la pantalla entra en estado Idle
     */
    @SuppressLint("MissingPermission")
    fun startPreLocationTracking() {
        if (isPreLocationActive) return
        
        Log.d(TAG, "Iniciando posicionamiento GPS previo")
        isPreLocationActive = true
        _gpsPreLocationState.value = GpsPreLocationState.Searching
        
        val locationRequest = LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            1000L // Actualización cada segundo para ahorro de batería
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setMaxUpdateDelayMillis(2000L)
            setWaitForAccurateLocation(false)
        }.build()
        
        preLocationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    handlePreLocation(location)
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                preLocationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos en posicionamiento previo", e)
            _gpsPreLocationState.value = GpsPreLocationState.Searching
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar posicionamiento previo", e)
            _gpsPreLocationState.value = GpsPreLocationState.Searching
        }
    }
    
    /**
     * Maneja las ubicaciones previas (antes de iniciar tracking)
     */
    private fun handlePreLocation(location: Location) {
        val accuracy = location.accuracy
        
        // Mantener la mejor posición recibida
        val currentPreLocation = _preLocation.value
        if (currentPreLocation == null || accuracy < (currentPreLocation.accuracy)) {
            _preLocation.value = location
            
            // Actualizar estado según precisión
            when {
                accuracy <= 6f -> {
                    _gpsPreLocationState.value = GpsPreLocationState.Ready
                    Log.d(TAG, "GPS previo: Listo (precisión: ${accuracy}m)")
                }
                accuracy <= 10f -> {
                    _gpsPreLocationState.value = GpsPreLocationState.Found(accuracy)
                    Log.d(TAG, "GPS previo: Señal encontrada (precisión: ${accuracy}m)")
                }
                else -> {
                    _gpsPreLocationState.value = GpsPreLocationState.Found(accuracy)
                    Log.d(TAG, "GPS previo: Buscando señal mejor (precisión: ${accuracy}m)")
                }
            }
        }
    }
    
    /**
     * Detiene la escucha de GPS previo
     */
    fun stopPreLocationTracking() {
        if (!isPreLocationActive) return
        
        Log.d(TAG, "Deteniendo posicionamiento GPS previo")
        isPreLocationActive = false
        
        preLocationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error al detener posicionamiento previo", e)
            }
        }
        
        preLocationCallback = null
        _gpsPreLocationState.value = GpsPreLocationState.Searching
        _preLocation.value = null
    }
    
    /**
     * Verifica si hay señal GPS válida para iniciar
     */
    fun hasValidGpsSignal(): Boolean {
        val state = _gpsPreLocationState.value
        return when (state) {
            is GpsPreLocationState.Ready -> true
            is GpsPreLocationState.Found -> state.accuracy <= 10f
            is GpsPreLocationState.Searching -> false
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
        
        // Verificar que hay señal GPS válida
        if (!hasValidGpsSignal()) {
            _message.value = "Esperando señal GPS... Por favor espera unos segundos"
            return
        }
        
        // Detener el posicionamiento previo
        stopPreLocationTracking()

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
                
                // Actualizar tipo de vehículo en el servicio
                trackingService?.updateVehicleType(scooter.vehicleType)
                
                _trackingState.value = TrackingState.Tracking
                trackingStateManager.updateTrackingState(true)
                trackingStateManager.updatePausedState(false)
                Log.d(TAG, "Seguimiento iniciado")
                
                // Capturar el clima al INICIO de la ruta
                captureStartWeather()
                
                // Mostrar toast de confirmación
                _message.value = "¡Tracking iniciado al 100%!"
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar seguimiento", e)
                _trackingState.value = TrackingState.Error(e.message ?: "Error desconocido")
            }
        }
    }
    
    /**
     * Captura el clima al inicio de la ruta con reintentos automáticos
     * Se ejecuta en segundo plano y no bloquea el inicio del tracking
     */
    private suspend fun captureStartWeather() {
        // Cancelar cualquier job anterior de clima
        weatherJob?.cancel()
        
        // Iniciar nuevo job en segundo plano
        weatherJob = viewModelScope.launch {
            try {
                _weatherStatus.value = WeatherStatus.Loading
                
                // Paso 1: Esperar hasta 5 segundos a que llegue el primer punto GPS
                Log.d(TAG, "🌤️ [Paso 1/2] Esperando primer punto GPS...")
                var attempts = 0
                var points = _routePoints.value
                
                while (points.isEmpty() && attempts < 10) { // 10 intentos × 500ms = 5 segundos
                    kotlinx.coroutines.delay(500)
                    points = _routePoints.value
                    attempts++
                    if (attempts % 2 == 0) { // Log cada segundo
                        Log.d(TAG, "🌤️ Esperando GPS... ${attempts / 2}s")
                    }
                }
                
                if (points.isEmpty()) {
                    Log.w(TAG, "⚠️ No hay puntos GPS después de 5 segundos")
                    _weatherStatus.value = WeatherStatus.NotAvailable
                    return@launch
                }
                
                val firstPoint = points.first()
                Log.d(TAG, "🌤️ [Paso 2/2] GPS obtenido. Consultando clima...")
                Log.d(TAG, "🌤️ Ubicación: lat=${firstPoint.latitude}, lon=${firstPoint.longitude}")
                
                // Paso 2: Intentar obtener el clima con reintentos automáticos
                val maxRetries = 3
                var retryCount = 0
                var success = false
                val weatherRepository = com.zipstats.app.repository.WeatherRepository()
                
                while (!success && retryCount < maxRetries) {
                    retryCount++
                    Log.d(TAG, "🌤️ Intento ${retryCount}/${maxRetries} de obtener clima...")
                    
                    val result = weatherRepository.getCurrentWeather(
                        latitude = firstPoint.latitude,
                        longitude = firstPoint.longitude
                    )
                    
                    result.onSuccess { weather ->
                        _startWeatherTemperature = weather.temperature
                        _startWeatherEmoji = weather.weatherEmoji
                        _startWeatherDescription = weather.description
                        _weatherStatus.value = WeatherStatus.Success(weather.temperature, weather.weatherEmoji)
                        success = true
                        Log.d(TAG, "✅ Clima capturado exitosamente: ${weather.temperature}°C ${weather.weatherEmoji}")
                        Log.d(TAG, "✅ Descripción: ${weather.description}")
                    }.onFailure { error ->
                        Log.e(TAG, "❌ Error en intento ${retryCount}: ${error.message}")
                        
                        if (retryCount < maxRetries) {
                            // Delay progresivo: 3s, 6s, 10s
                            val delayMs = when (retryCount) {
                                1 -> 3000L
                                2 -> 6000L
                                else -> 10000L
                            }
                            Log.d(TAG, "⏳ Reintentando en ${delayMs / 1000}s...")
                            kotlinx.coroutines.delay(delayMs)
                        } else {
                            // Último intento falló
                            _weatherStatus.value = WeatherStatus.Error(
                                error.message ?: "Error al obtener clima"
                            )
                            Log.e(TAG, "❌ Todos los intentos fallaron. Clima no disponible.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción al capturar clima: ${e.message}", e)
                _weatherStatus.value = WeatherStatus.Error("Excepción: ${e.message}")
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
                
                // Verificar estado del clima
                val weatherState = _weatherStatus.value
                Log.d(TAG, "Estado del clima al finalizar: $weatherState")
                
                // Si el clima aún está cargando, dar unos segundos más de gracia
                if (weatherState is WeatherStatus.Loading) {
                    Log.d(TAG, "⏳ Clima aún cargando, esperando hasta 5s más...")
                    var waited = 0
                    while (_weatherStatus.value is WeatherStatus.Loading && waited < 5000) {
                        kotlinx.coroutines.delay(500)
                        waited += 500
                    }
                    Log.d(TAG, "Estado después de espera: ${_weatherStatus.value}")
                }
                
                // Guardar referencia al clima antes de resetear
                val savedWeatherTemp = _startWeatherTemperature
                val savedWeatherEmoji = _startWeatherEmoji
                val savedWeatherDesc = _startWeatherDescription
                
                // Crear la ruta con análisis post-ruta
                val baseRoute = routeRepository.createRouteFromPoints(
                    points = points,
                    scooterId = scooter.id,
                    scooterName = scooter.nombre,
                    startTime = startTime,
                    endTime = endTime,
                    notes = notes,
                    timeInMotion = _timeInMotion.value,
                    vehicleType = scooter.vehicleType
                )
                
                // Usar el clima capturado al INICIO de la ruta
                val route = if (savedWeatherTemp != null) {
                    Log.d(TAG, "✅ Usando clima del INICIO de la ruta: ${savedWeatherTemp}°C")
                    baseRoute.copy(
                        weatherTemperature = savedWeatherTemp,
                        weatherEmoji = savedWeatherEmoji,
                        weatherDescription = savedWeatherDesc
                    )
                } else {
                    Log.w(TAG, "⚠️ No se capturó clima al inicio, guardando ruta sin clima")
                    baseRoute
                }
                
                // Guardar en Firebase
                val result = routeRepository.saveRoute(route)
                
                if (result.isSuccess) {
                    val savedRouteId = result.getOrNull() ?: ""
                    
                    // Si no hay clima aún, intentar obtenerlo en segundo plano
                    if (savedWeatherTemp == null && points.isNotEmpty()) {
                        Log.d(TAG, "🔄 Iniciando actualización de clima en segundo plano...")
                        viewModelScope.launch {
                            try {
                                // Dar tiempo a que el weatherJob termine si aún está activo
                                var retries = 0
                                while (_weatherStatus.value is WeatherStatus.Loading && retries < 10) {
                                    kotlinx.coroutines.delay(1000)
                                    retries++
                                    Log.d(TAG, "⏳ Esperando clima (${retries}s)...")
                                }
                                
                                // Si se obtuvo el clima durante la espera, actualizar la ruta
                                if (_startWeatherTemperature != null) {
                                    Log.d(TAG, "✅ Clima obtenido, actualizando ruta $savedRouteId...")
                                    routeRepository.updateRouteWeather(
                                        routeId = savedRouteId,
                                        temperature = _startWeatherTemperature!!,
                                        emoji = _startWeatherEmoji ?: "☁️",
                                        description = _startWeatherDescription ?: ""
                                    )
                                } else {
                                    // Último intento: llamar directamente a la API
                                    Log.d(TAG, "🔄 Último intento de obtener clima...")
                                    val firstPoint = points.first()
                                    routeRepository.fetchAndUpdateWeather(
                                        routeId = savedRouteId,
                                        latitude = firstPoint.latitude,
                                        longitude = firstPoint.longitude
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error al actualizar clima en segundo plano: ${e.message}", e)
                            } finally {
                                // Limpiar datos de clima
                                _startWeatherTemperature = null
                                _startWeatherEmoji = null
                                _startWeatherDescription = null
                                _weatherStatus.value = WeatherStatus.Idle
                            }
                        }
                    } else {
                        // Limpiar datos de clima después de usar
                        _startWeatherTemperature = null
                        _startWeatherEmoji = null
                        _startWeatherDescription = null
                        _weatherStatus.value = WeatherStatus.Idle
                    }
                    
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
        // Cancelar job de clima si está activo
        weatherJob?.cancel()
        
        stopTrackingService()
        _trackingState.value = TrackingState.Idle
        trackingStateManager.resetState()
        _routePoints.value = emptyList()
        _currentDistance.value = 0.0
        _currentSpeed.value = 0.0
        _duration.value = 0L
        
        // Limpiar datos de clima
        _startWeatherTemperature = null
        _startWeatherEmoji = null
        _startWeatherDescription = null
        _weatherStatus.value = WeatherStatus.Idle
        
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
        // Cancelar job de clima
        weatherJob?.cancel()
        
        // Detener posicionamiento previo si está activo
        stopPreLocationTracking()
        
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

