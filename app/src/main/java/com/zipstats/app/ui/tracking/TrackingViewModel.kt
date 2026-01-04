package com.zipstats.app.ui.tracking

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.model.Scooter
import com.zipstats.app.repository.AppOverlayRepository
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.RouteRepository
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.service.LocationTrackingService
import com.zipstats.app.service.TrackingStateManager
import com.zipstats.app.tracking.LocationTracker
import com.zipstats.app.utils.LocationUtils
import com.zipstats.app.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

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
    data class Success(
        val temperature: Double,      // Temperatura en °C
        val feelsLike: Double,         // Sensación térmica
        val description: String,       // Descripción del clima
        val icon: String,              // Código (ahora será el numérico, ej: "3")
        val humidity: Int,             // Humedad %
        val windSpeed: Double,         // Velocidad del viento m/s
        val weatherEmoji: String,      // Emoji representativo (¡ARREGLADO!)
        val weatherCode: Int,
        val isDay: Boolean,
        val uvIndex: Double?,
        val windDirection: Int?,
        val windGusts: Double?,
        val rainProbability: Int?,
        val precipitation: Double,     // mm reales
        val rain: Double?,             // mm
        val showers: Double?          // mm
    ) : WeatherStatus()
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
    private val preferencesManager: PreferencesManager,
    private val appOverlayRepository: AppOverlayRepository
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
    private var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationTracker: LocationTracker
    private var preLocationCallback: LocationCallback? = null
    private var isPreLocationActive = false
    
    // Snapshot inicial del clima (capturado en precarga, antes de iniciar ruta)
    private var _initialWeatherSnapshot: com.zipstats.app.repository.WeatherData? = null
    private var _initialWeatherCaptured = false
    
    // Clima capturado al inicio de la ruta (se copia del snapshot al iniciar tracking)
    private var _startWeatherTemperature: Double? = null
    private var _startWeatherEmoji: String? = null
    private var _startWeatherDescription: String? = null
    private var _startWeatherIsDay: Boolean? = null

    private var _startWeatherFeelsLike: Double? = null

    private var _startWeatherHumidity: Int? = null

    private var _startWeatherWindSpeed: Double? = null

    // ... (tus otras variables _startWeather...)

    private var _startWeatherUvIndex: Double? = null
    private var _startWeatherWindDirection: Int? = null
    private var _startWeatherWindGusts: Double? = null
    private var _startWeatherRainProbability: Int? = null
    
    // Estado para aviso preventivo de lluvia
    private val _shouldShowRainWarning = MutableStateFlow(false)
    val shouldShowRainWarning: StateFlow<Boolean> = _shouldShowRainWarning.asStateFlow()
    
    // Tipo de aviso: true = lluvia activa, false = calzada mojada (sin lluvia activa)
    private val _isActiveRainWarning = MutableStateFlow(false)
    val isActiveRainWarning: StateFlow<Boolean> = _isActiveRainWarning.asStateFlow()
    
    // Estado para aviso preventivo de condiciones extremas
    private val _shouldShowExtremeWarning = MutableStateFlow(false)
    val shouldShowExtremeWarning: StateFlow<Boolean> = _shouldShowExtremeWarning.asStateFlow()
    
    /**
     * Descarta el aviso preventivo de lluvia
     */
    fun dismissRainWarning() {
        _shouldShowRainWarning.value = false
    }
    
    /**
     * Descarta el aviso preventivo de condiciones extremas
     */
    fun dismissExtremeWarning() {
        _shouldShowExtremeWarning.value = false
    }
    
    // Variables para detectar lluvia durante la ruta
    private var weatherHadRain = false
    private var weatherRainStartMinute: Int? = null
    private var weatherMaxPrecipitation = 0.0
    private var weatherRainReason: String? = null
    // Para confirmación de lluvia nueva (2 chequeos seguidos)
    private var pendingRainConfirmation: Boolean = false
    private var pendingRainMinute: Int? = null
    private var pendingRainReason: String? = null
    
    // Variables para rastrear el estado más adverso durante la ruta (para badges)
    // Prioridad: Condiciones extremas > Lluvia > Calzada mojada
    private var weatherHadWetRoad = false // Calzada mojada detectada (sin lluvia activa)
    private var weatherHadExtremeConditions = false // Condiciones extremas detectadas
    private var weatherExtremeReason: String? = null // Razón de condiciones extremas (WIND, GUSTS, STORM, SNOW, COLD, HEAT)
    
    // Valores máximos/mínimos durante la ruta (para reflejar el estado más adverso en los badges)
    private var maxWindSpeed = 0.0 // km/h
    private var maxWindGusts = 0.0 // km/h
    private var minTemperature = Double.MAX_VALUE // °C
    private var maxTemperature = Double.MIN_VALUE // °C
    private var maxUvIndex = 0.0
    
    // Estado del clima
    private val _weatherStatus = MutableStateFlow<WeatherStatus>(WeatherStatus.Idle)
    val weatherStatus: StateFlow<WeatherStatus> = _weatherStatus.asStateFlow()
    
    // Job para manejo del clima en segundo plano
    private var weatherJob: kotlinx.coroutines.Job? = null
    
    // Job para detección continua de lluvia durante la ruta
    private var continuousWeatherJob: kotlinx.coroutines.Job? = null
    
    // Job para el observador del estado global - permite cancelarlo explícitamente
    private var globalStateJob: kotlinx.coroutines.Job? = null
    
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

        // --- RECUPERAR CLIMA GUARDADO ---
        // Comprobamos si hay un clima guardado en la "caja fuerte" del repositorio
        val savedWeather = routeRepository.getTempWeather()
        if (savedWeather != null) {
            Log.d("TrackingViewModel", "♻️ Recuperando clima guardado tras cambio de pantalla")

            // 1. Restauramos las variables privadas para el guardado final
            _startWeatherTemperature = savedWeather.temperature
            _startWeatherEmoji = savedWeather.weatherEmoji
            _startWeatherDescription = savedWeather.description
            _startWeatherIsDay = savedWeather.isDay
            _startWeatherFeelsLike = savedWeather.feelsLike
            _startWeatherHumidity = savedWeather.humidity
            _startWeatherWindSpeed = savedWeather.windSpeed
            _startWeatherUvIndex = savedWeather.uvIndex
            _startWeatherWindDirection = savedWeather.windDirection
            _startWeatherWindGusts = savedWeather.windGusts
            _startWeatherRainProbability = savedWeather.rainProbability

            // 2. Restauramos el estado de la UI para que aparezca la tarjeta
            _weatherStatus.value = WeatherStatus.Success(
                temperature = savedWeather.temperature,
                feelsLike = savedWeather.feelsLike,
                description = savedWeather.description,
                icon = savedWeather.icon,
                humidity = savedWeather.humidity,
                windSpeed = savedWeather.windSpeed,
                weatherEmoji = savedWeather.weatherEmoji,
                weatherCode = savedWeather.weatherCode,
                isDay = savedWeather.isDay,
                uvIndex = savedWeather.uvIndex,
                windDirection = savedWeather.windDirection,
                windGusts = savedWeather.windGusts,
                rainProbability = savedWeather.rainProbability,
                precipitation = savedWeather.precipitation,
                rain = savedWeather.rain,
                showers = savedWeather.showers
            )
        }
    }

    /**
     * Sincroniza el estado del ViewModel con el estado global del servicio
     */
    private fun syncWithGlobalState() {
        // Cancelamos cualquier job anterior por si acaso
        globalStateJob?.cancel()
        
        // Guardamos la referencia en la variable
        globalStateJob = viewModelScope.launch {
            trackingStateManager.isTracking.collect { isTracking ->
                // Ya no necesitamos el "if (isSaving)" aquí, 
                // porque cancelaremos este Job antes de que eso ocurra.
                
                if (isTracking) {
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
     * Si hay GPS válido, captura el clima inicial
     */
    fun selectScooter(scooter: Scooter) {
        _selectedScooter.value = scooter
        viewModelScope.launch {
            saveLastUsedScooter(scooter.id)
            
            // Si hay GPS válido, capturar clima inicial
            if (hasValidGpsSignal() && _preLocation.value != null && !_initialWeatherCaptured) {
                captureInitialWeather()
            }
        }
    }
    
    /**
     * Captura el clima inicial en la pantalla de precarga (antes de iniciar ruta)
     * Se ejecuta cuando hay GPS válido y vehículo seleccionado
     */
    private suspend fun captureInitialWeather() {
        if (_initialWeatherCaptured) {
            Log.d(TAG, "🌤️ Clima inicial ya capturado, omitiendo")
            return
        }
        
        val preLocation = _preLocation.value
        if (preLocation == null) {
            Log.w(TAG, "⚠️ No hay ubicación GPS previa para capturar clima inicial")
            return
        }
        
        Log.d(TAG, "🌤️ [Precarga] Capturando clima inicial en lat=${preLocation.latitude}, lon=${preLocation.longitude}")
        
        // Cancelar cualquier job anterior de clima
        weatherJob?.cancel()
        
        weatherJob = viewModelScope.launch {
            try {
                _weatherStatus.value = WeatherStatus.Loading
                
                val weatherRepository = com.zipstats.app.repository.WeatherRepository()
                val result = weatherRepository.getCurrentWeather(
                    latitude = preLocation.latitude,
                    longitude = preLocation.longitude
                )
                
                result.onSuccess { weather ->
                    // Resolver código de clima efectivo basado en detección de lluvia efectiva
                    val (effectiveWeatherCode, _, _, isDerived) = resolveEffectiveWeatherCode(
                        weather.weatherCode,
                        weather.precipitation,
                        weather.rain,
                        weather.showers,
                        weather.humidity,
                        weather.rainProbability,
                        weather.windSpeed
                    )
                    
                    // Obtener emoji, descripción e icono usando el código efectivo
                    val effectiveEmoji = com.zipstats.app.repository.WeatherRepository.getEmojiForWeather(
                        effectiveWeatherCode,
                        if (weather.isDay) 1 else 0
                    )
                    val effectiveDescription = if (isDerived) {
                        "Lluvia"
                    } else {
                        com.zipstats.app.repository.WeatherRepository.getDescriptionForWeather(
                            effectiveWeatherCode,
                            if (weather.isDay) 1 else 0
                        )
                    }
                    
                    // Guardar snapshot inicial
                    _initialWeatherSnapshot = weather
                    _initialWeatherCaptured = true
                    
                    // Actualizar estado de UI
                    _weatherStatus.value = WeatherStatus.Success(
                        temperature = weather.temperature,
                        feelsLike = weather.feelsLike,
                        description = effectiveDescription,
                        icon = effectiveWeatherCode.toString(),
                        humidity = weather.humidity,
                        windSpeed = weather.windSpeed,
                        weatherEmoji = effectiveEmoji,
                        weatherCode = effectiveWeatherCode,
                        isDay = weather.isDay,
                        uvIndex = weather.uvIndex,
                        windDirection = weather.windDirection,
                        windGusts = weather.windGusts,
                        rainProbability = weather.rainProbability,
                        precipitation = weather.precipitation,
                        rain = weather.rain,
                        showers = weather.showers
                    )
                    
                    // Detectar si hay lluvia para mostrar aviso preventivo
                    val (isRaining, _, rainUserReason) = isRainingForScooter(
                        weather.weatherCode,
                        weather.precipitation,
                        weather.rain,
                        weather.showers,
                        weather.humidity,
                        weather.rainProbability,
                        weather.windSpeed
                    )
                    
                    // Determinar si es lluvia activa: Usar función compartida para garantizar umbrales idénticos
                    val isActiveRain = checkActiveRain(
                        weatherCode = weather.weatherCode,
                        isRaining = isRaining,
                        precipitation = weather.precipitation
                    )
                    
                    // Calzada mojada: Usar función compartida para garantizar umbrales idénticos
                    // 🔒 EXCLUSIÓN: Si hay lluvia activa, NO mostrar calzada mojada (son excluyentes)
                    val isWetRoad = if (isActiveRain) {
                        false // Excluir calzada mojada si hay lluvia activa
                    } else {
                        checkWetRoadConditions(
                            weatherCode = weather.weatherCode,
                            humidity = weather.humidity,
                            rainProbability = weather.rainProbability,
                            precipitation = weather.precipitation,
                            isDay = weather.isDay,
                            weatherEmoji = effectiveEmoji,
                            hasActiveRain = isActiveRain
                        )
                    }
                    
                    // Mostrar aviso si hay lluvia activa O calzada mojada (pero nunca ambos)
                    _shouldShowRainWarning.value = isActiveRain || isWetRoad
                    _isActiveRainWarning.value = isActiveRain
                    
                    // Detectar condiciones extremas (replicar lógica de checkExtremeConditions)
                    val hasExtremeConditions = checkExtremeConditions(
                        windSpeed = weather.windSpeed,
                        windGusts = weather.windGusts,
                        temperature = weather.temperature,
                        uvIndex = weather.uvIndex,
                        isDay = weather.isDay,
                        weatherEmoji = effectiveEmoji,
                        weatherDescription = effectiveDescription,
                        weatherCode = effectiveWeatherCode
                    )
                    _shouldShowExtremeWarning.value = hasExtremeConditions
                    
                    // 🔥 JERARQUÍA DE BADGES (misma lógica que RouteDetailDialog):
                    // 1. Lluvia: Máxima prioridad (siempre se muestra si existe)
                    // 2. Calzada mojada: Solo si NO hay lluvia (excluyente con lluvia)
                    // 3. Condiciones extremas: COMPLEMENTARIO (puede coexistir con lluvia o calzada mojada)
                    
                    // Detectar y guardar condiciones extremas (complementario, no excluye otros badges)
                    if (hasExtremeConditions) {
                        weatherHadExtremeConditions = true
                        // Detectar y guardar la causa específica en precarga
                        val cause = detectExtremeCause(
                            windSpeed = weather.windSpeed,
                            windGusts = weather.windGusts,
                            temperature = weather.temperature,
                            uvIndex = weather.uvIndex,
                            isDay = weather.isDay,
                            weatherEmoji = effectiveEmoji,
                            weatherDescription = effectiveDescription,
                            weatherCode = effectiveWeatherCode
                        )
                        if (cause != null) {
                            weatherExtremeReason = cause
                        }
                    }
                    
                    // Lluvia: Máxima prioridad (siempre se muestra si existe)
                    if (isActiveRain) {
                        weatherHadRain = true
                        weatherRainStartMinute = 0 // Al inicio de la ruta
                        weatherRainReason = rainUserReason // Guardar razón amigable para el usuario
                        weatherHadWetRoad = false // Lluvia excluye calzada mojada
                    } else if (isWetRoad) {
                        // Calzada mojada: Solo si NO hay lluvia activa
                        weatherHadWetRoad = true
                    }
                    
                    Log.d(TAG, "✅ [Precarga] Clima inicial capturado: ${weather.temperature}°C $effectiveEmoji")
                    if (isRaining) {
                        Log.d(TAG, "🌧️ [Precarga] Lluvia detectada - mostrar aviso preventivo")
                    }
                    if (hasExtremeConditions) {
                        Log.d(TAG, "⚠️ [Precarga] Condiciones extremas detectadas - mostrar aviso preventivo")
                    }
                }.onFailure { error ->
                    Log.e(TAG, "❌ [Precarga] Error al capturar clima inicial: ${error.message}")
                    _weatherStatus.value = WeatherStatus.Error(error.message ?: "Error al obtener clima")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Precarga] Excepción al capturar clima inicial: ${e.message}", e)
                _weatherStatus.value = WeatherStatus.Error("Excepción: ${e.message}")
            }
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
                    
                    // Si hay vehículo seleccionado, capturar clima inicial
                    if (_selectedScooter.value != null && !_initialWeatherCaptured) {
                        viewModelScope.launch {
                            captureInitialWeather()
                        }
                    }
                }
                accuracy <= 10f -> {
                    _gpsPreLocationState.value = GpsPreLocationState.Found(accuracy)
                    Log.d(TAG, "GPS previo: Señal encontrada (precisión: ${accuracy}m)")
                    
                    // Si hay vehículo seleccionado, capturar clima inicial
                    if (_selectedScooter.value != null && !_initialWeatherCaptured) {
                        viewModelScope.launch {
                            captureInitialWeather()
                        }
                    }
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
        return when (val state = _gpsPreLocationState.value) {
            is GpsPreLocationState.Ready -> true
            is GpsPreLocationState.Found -> state.accuracy <= 10f
            is GpsPreLocationState.Searching -> false
        }
    }

    /**
     * Inicia el seguimiento de la ruta
     */
    fun startTracking() {
        // Ocultar preavisos cuando se inicia el tracking (solo se muestran en precarga GPS)
        _shouldShowRainWarning.value = false
        _shouldShowExtremeWarning.value = false
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
                
                // Iniciar detección continua de lluvia cada 10 minutos
                startContinuousWeatherMonitoring()
                
                // Mostrar toast de confirmación
                _message.value = "¡Tracking iniciado al 100%!"
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar seguimiento", e)
                _trackingState.value = TrackingState.Error(e.message ?: "Error desconocido")
            }
        }
    }
    
    /**
     * Detecta si está lloviendo efectivamente para patinete
     * Usa múltiples reglas con JERARQUÍA DE CONFIANZA: prioriza evidencia física sobre estimaciones atmosféricas
     * Devuelve: (isRaining, reasonCode, userFriendlyReason)
     */
    private fun isRainingForScooter(
        weatherCode: Int,
        precipitation: Double?,
        rain: Double?,
        showers: Double?,
        humidity: Int?,
        rainProbability: Int?,
        windSpeed: Double?
    ): Triple<Boolean, String, String> {
        // 1️⃣ PRIORIDAD ABSOLUTA: Código oficial indica lluvia
        if (weatherCode in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82)) {
            return Triple(true, "WEATHER_CODE", "Lluvia detectada por código meteorológico")
        }

        // 2️⃣ PRIORIDAD ALTA: Precipitación medida > 0.4 mm (evidencia física directa)
        // Razón: Open-Meteo suele dar "ruido" de 0.1-0.2 mm por humedad ambiental
        // Para un motorista, menos de 0.4 mm es llovizna que no siempre requiere badge de lluvia activa
        val effectiveRain = maxOf(
            precipitation ?: 0.0,
            rain ?: 0.0,
            showers ?: 0.0
        )
        if (effectiveRain > 0.4) {
            return Triple(true, "PRECIPITATION", "Lluvia detectada por precipitación medida (${LocationUtils.formatNumberSpanish(effectiveRain)} mm)")
        }

        // 🔒 Regla de protección: condiciones probabilísticas solo si el cielo está cubierto (weatherCode >= 3)
        // Si el código es 0, 1 o 2 (Despejado/Nubes medias), ignoramos la humedad aunque sea del 90%
        // ya que es "bochorno" o bruma, no lluvia
        val isCloudyOrOvercast = weatherCode >= 3
        
        // 3️⃣ PRIORIDAD BAJA: Atmósfera lluviosa (solo si cielo cubierto y condiciones muy extremas)
        // Endurecido: humedad > 92% y probabilidad > 70% (en Barcelona es común 40% sin que caiga una gota)
        if (isCloudyOrOvercast && (humidity ?: 0) >= 92 && (rainProbability ?: 0) >= 70) {
            return Triple(true, "HUMIDITY_PROBABILITY", "Lluvia detectada por humedad muy alta y alta probabilidad de precipitación")
        }

        // 4️⃣ REGLA ELIMINADA: Diluvio urbano mediterráneo
        // La combinación de humedad alta y poco viento define la niebla matinal del Puerto de Barcelona,
        // no un diluvio. Esta condición ahora activa "Calzada Mojada" pero no "Lluvia".

        return Triple(false, "NONE", "No se detectó lluvia")
    }

    /**
     * Determina si hay lluvia activa (replica EXACTAMENTE la lógica usada en preavisos y monitoreo)
     * 🔒 IMPORTANTE: Esta función garantiza que los umbrales sean idénticos entre preavisos y badges
     */
    private fun checkActiveRain(
        weatherCode: Int,
        isRaining: Boolean,
        precipitation: Double?
    ): Boolean {
        val rainCodes = listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
        val isClearSky = weatherCode == 0 || weatherCode == 1
        
        // 🔒 UX: Si el cielo está despejado, NUNCA mostrar como lluvia activa (aunque haya precipitación)
        // La precipitación con cielo despejado = llovizna pasada = calzada mojada, no lluvia activa
        return if (isClearSky) {
            false // Cielo despejado = nunca lluvia activa (solo calzada mojada si hay precipitación)
        } else {
            // Usar umbral de 0.4 mm para evitar falsos positivos por ruido de humedad ambiental
            weatherCode in rainCodes || (isRaining && (precipitation ?: 0.0) > 0.4)
        }
    }
    
    /**
     * Verifica si hay calzada mojada (replica EXACTAMENTE la lógica de RouteDetailDialog.checkWetRoadConditions)
     * 🔒 IMPORTANTE: Esta función garantiza que los umbrales sean idénticos entre preavisos y badges
     * 
     * Acepta humedad alta aquí: Si hay >85% de humedad pero no hay lluvia confirmada por código o mm,
     * el badge Naranja es el correcto. El asfalto está húmedo y resbala, pero el usuario no se moja.
     */
    private fun checkWetRoadConditions(
        weatherCode: Int?,
        humidity: Int?,
        rainProbability: Int?,
        precipitation: Double?,
        isDay: Boolean,
        weatherEmoji: String?,
        hasActiveRain: Boolean
    ): Boolean {
        // 1. EXCLUSIÓN: Si hay lluvia activa, NO mostramos "Calzada Mojada"
        if (hasActiveRain) {
            return false
        }
        
        // 2. Si hay precipitación registrada pero no se detectó como "Lluvia activa"
        // (Ej: Llovió justo antes o llovizna muy fina que no activó el sensor pero mojó el suelo)
        // Usar umbral de 0.4 mm para evitar ruido de humedad ambiental
        // NOTA: Esta condición es independiente del estado del cielo (precipitación real medida)
        if (precipitation != null && precipitation > 0.0 && precipitation <= 0.4) {
            return true
        }
        
        // 3. Calzada mojada considerando día/noche (ACEPTAR HUMEDAD ALTA SIN RESTRICCIÓN DE CIELO)
        // Día: humedad muy alta (>90%) o probabilidad alta (>40%) - suelo puede estar mojado pero seca más rápido
        // Noche: humedad alta (>85%) es suficiente - el suelo tarda mucho más en secarse sin sol
        // En Barcelona el asfalto por la noche no seca debido a la humedad marina, aunque no haya llovido
        if (humidity != null) {
            if (isDay) {
                // Día: necesita condiciones más extremas
                if (humidity >= 90) {
                    return true
                }
                if (rainProbability != null && rainProbability > 40) {
                    return true
                }
            } else {
                // Noche: con humedad alta el suelo tarda mucho en secarse
                if (humidity >= 85) {
                    return true
                }
                if (rainProbability != null && rainProbability > 35) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Verifica si hay condiciones extremas (replica lógica de RouteDetailDialog.checkExtremeConditions)
     */
    private fun checkExtremeConditions(
        windSpeed: Double?,
        windGusts: Double?,
        temperature: Double?,
        uvIndex: Double?,
        isDay: Boolean,
        weatherEmoji: String?,
        weatherDescription: String?,
        weatherCode: Int? = null
    ): Boolean {
        // Viento fuerte (>40 km/h) - convertir de m/s a km/h
        val windSpeedKmh = (windSpeed ?: 0.0) * 3.6
        if (windSpeedKmh > 40) {
            return true
        }
        
        // Ráfagas de viento muy fuertes (>60 km/h) - convertir de m/s a km/h
        val windGustsKmh = (windGusts ?: 0.0) * 3.6
        if (windGustsKmh > 60) {
            return true
        }
        
        // Temperatura extrema (<0°C o >35°C)
        if (temperature != null) {
            if (temperature < 0 || temperature > 35) {
                return true
            }
        }
        
        // Índice UV muy alto (>8) - solo de día
        if (isDay && uvIndex != null && uvIndex > 8) {
            return true
        }
        
        // Tormenta (detectada por emoji o descripción)
        val isStorm = weatherEmoji?.let { emoji ->
            emoji.contains("⛈") || emoji.contains("⚡")
        } ?: false
        
        val isStormByDescription = weatherDescription?.let { desc ->
            desc.contains("Tormenta", ignoreCase = true) ||
            desc.contains("granizo", ignoreCase = true) ||
            desc.contains("rayo", ignoreCase = true)
        } ?: false

        if (isStorm || isStormByDescription) {
            return true
        }
        
        // Nieve (weatherCode 71, 73, 75, 77, 85, 86 o emoji ❄️)
        // La nieve es muy peligrosa en patinete por el riesgo de resbalar
        val isSnowByCode = weatherCode?.let { code ->
            code in listOf(71, 73, 75, 77, 85, 86)
        } ?: false
        
        val isSnowByEmoji = weatherEmoji?.let { emoji ->
            emoji.contains("❄️")
        } ?: false
        
        val isSnowByDescription = weatherDescription?.let { desc ->
            desc.contains("Nieve", ignoreCase = true) ||
            desc.contains("nevada", ignoreCase = true) ||
            desc.contains("snow", ignoreCase = true)
        } ?: false

        return isSnowByCode || isSnowByEmoji || isSnowByDescription
    }
    
    /**
     * Detecta la causa específica de condiciones extremas (misma lógica que StatisticsViewModel)
     * Retorna: "STORM", "SNOW", "GUSTS", "WIND", "COLD", "HEAT" o null
     */
    private fun detectExtremeCause(
        windSpeed: Double?,
        windGusts: Double?,
        temperature: Double?,
        uvIndex: Double?,
        isDay: Boolean,
        weatherEmoji: String?,
        weatherDescription: String?,
        weatherCode: Int? = null
    ): String? {
        // Prioridad: Tormenta > Nieve > Rachas > Viento > Temperatura
        
        // 1. Tormenta (prioridad máxima)
        val isStorm = weatherEmoji?.let { emoji ->
            emoji.contains("⛈") || emoji.contains("⚡")
        } ?: false
        
        val isStormByDescription = weatherDescription?.let { desc ->
            desc.contains("Tormenta", ignoreCase = true) ||
            desc.contains("granizo", ignoreCase = true) ||
            desc.contains("rayo", ignoreCase = true)
        } ?: false
        
        if (isStorm || isStormByDescription) {
            return "STORM"
        }
        
        // 2. Nieve (weatherCode 71, 73, 75, 77, 85, 86 o emoji ❄️)
        // La nieve es muy peligrosa en patinete por el riesgo de resbalar
        val isSnowByCode = weatherCode?.let { code ->
            code in listOf(71, 73, 75, 77, 85, 86)
        } ?: false
        
        val isSnowByEmoji = weatherEmoji?.let { emoji ->
            emoji.contains("❄️")
        } ?: false
        
        val isSnowByDescription = weatherDescription?.let { desc ->
            desc.contains("Nieve", ignoreCase = true) ||
            desc.contains("nevada", ignoreCase = true) ||
            desc.contains("snow", ignoreCase = true)
        } ?: false
        
        if (isSnowByCode || isSnowByEmoji || isSnowByDescription) {
            return "SNOW"
        }
        
        // 3. Rachas de viento muy fuertes (>60 km/h) - prioridad sobre viento normal
        val windGustsKmh = (windGusts ?: 0.0) * 3.6
        if (windGustsKmh > 60) {
            return "GUSTS"
        }
        
        // 4. Viento fuerte (>40 km/h)
        val windSpeedKmh = (windSpeed ?: 0.0) * 3.6
        if (windSpeedKmh > 40) {
            return "WIND"
        }
        
        // 5. Temperatura extrema
        if (temperature != null) {
            if (temperature < 0) {
                return "COLD"
            }
            if (temperature > 35) {
                return "HEAT"
            }
        }
        
        // 6. Índice UV muy alto (>8) - solo de día (se considera como calor)
        if (isDay && uvIndex != null && uvIndex > 8) {
            return "HEAT"
        }
        
        return null
    }

    /**
     * Resuelve el código de clima efectivo basado en detección de lluvia efectiva
     * Si hay lluvia efectiva, fuerza código 61 (lluvia ligera)
     * Devuelve: (effectiveCode, reasonCode, userFriendlyReason, isDerived)
     */
    private fun resolveEffectiveWeatherCode(
        originalCode: Int,
        precipitation: Double?,
        rain: Double?,
        showers: Double?,
        humidity: Int?,
        rainProbability: Int?,
        windSpeed: Double?
    ): Quadruple<Int, String, String, Boolean> {
        val (isRaining, reasonCode, userFriendlyReason) = isRainingForScooter(
            originalCode,
            precipitation,
            rain,
            showers,
            humidity,
            rainProbability,
            windSpeed
        )
        
        // Verificar si el código original ya indicaba lluvia
        val rainFromModel = originalCode in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82)
        
        return if (isRaining && !rainFromModel) {
            // Lluvia derivada (no del modelo)
            Quadruple(61, reasonCode, userFriendlyReason, true)
        } else if (isRaining && rainFromModel) {
            // Lluvia del modelo
            Quadruple(originalCode, reasonCode, userFriendlyReason, false)
        } else {
            Quadruple(originalCode, reasonCode, userFriendlyReason, false)
        }
    }
    
    /**
     * Data class para devolver múltiples valores
     */
    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    /**
     * Inicia el monitoreo continuo de lluvia durante la ruta
     * - Primer chequeo: 5 minutos
     * - Luego: cada 10 minutos
     * - Solo actualiza el icono si detecta lluvia nueva (no si ya había lluvia y mejora)
     * - Requiere 2 chequeos seguidos para confirmar lluvia nueva (evitar falsos positivos)
     */
    private fun startContinuousWeatherMonitoring() {
        // Cancelar cualquier monitoreo anterior
        continuousWeatherJob?.cancel()
        
        // Limpiar estado pendiente antes de iniciar nuevo monitoreo
        pendingRainConfirmation = false
        pendingRainMinute = null
        pendingRainReason = null
        
        continuousWeatherJob = viewModelScope.launch {
            // Primer chequeo a los 5 minutos (más rápido para UX y seguridad)
            kotlinx.coroutines.delay(5 * 60 * 1000L)
            
            var isFirstCheck = true
            
            // Mientras esté en tracking activo, chequear cada 10 minutos (después del primero)
            while (_trackingState.value is TrackingState.Tracking || 
                   _trackingState.value is TrackingState.Paused) {
                
                val points = _routePoints.value
                if (points.isNotEmpty()) {
                    val currentPoint = points.last()
                    val elapsedMinutes = if (_startTime.value > 0) {
                        ((System.currentTimeMillis() - _startTime.value) / (1000 * 60)).toInt()
                    } else {
                        0
                    }
                    
                    Log.d(TAG, "🌧️ [Monitoreo continuo] Chequeando clima en minuto $elapsedMinutes...")
                    
                    try {
                        val weatherRepository = com.zipstats.app.repository.WeatherRepository()
                        val result = weatherRepository.getCurrentWeather(
                            latitude = currentPoint.latitude,
                            longitude = currentPoint.longitude
                        )
                        
                        result.onSuccess { weather ->
                            // Detectar lluvia usando la función (para badge en resumen)
                            val (isRaining, _, rainUserReason) = isRainingForScooter(
                                weather.weatherCode,
                                weather.precipitation,
                                weather.rain,
                                weather.showers,
                                weather.humidity,
                                weather.rainProbability,
                                weather.windSpeed
                            )
                            
                            // Determinar si es lluvia activa: Usar función compartida para garantizar umbrales idénticos
                            val isActiveRain = checkActiveRain(
                                weatherCode = weather.weatherCode,
                                isRaining = isRaining,
                                precipitation = weather.precipitation
                            )
                            
                            // Obtener emoji para detección de condiciones extremas y calzada mojada
                            val effectiveEmoji = com.zipstats.app.repository.WeatherRepository.getEmojiForWeather(
                                weather.weatherCode,
                                if (weather.isDay) 1 else 0
                            )
                            
                            // Calzada mojada: Usar función compartida para garantizar umbrales idénticos
                            // 🔒 EXCLUSIÓN: Si hay lluvia activa, NO mostrar calzada mojada (son excluyentes)
                            val isWetRoad = if (isActiveRain) {
                                false // Excluir calzada mojada si hay lluvia activa
                            } else {
                                checkWetRoadConditions(
                                    weatherCode = weather.weatherCode,
                                    humidity = weather.humidity,
                                    rainProbability = weather.rainProbability,
                                    precipitation = weather.precipitation,
                                    isDay = weather.isDay,
                                    weatherEmoji = effectiveEmoji,
                                    hasActiveRain = isActiveRain
                                )
                            }
                            
                            // Detectar condiciones extremas
                            val hasExtremeConditions = checkExtremeConditions(
                                windSpeed = weather.windSpeed,
                                windGusts = weather.windGusts,
                                temperature = weather.temperature,
                                uvIndex = weather.uvIndex,
                                isDay = weather.isDay,
                                weatherEmoji = effectiveEmoji,
                                weatherDescription = weather.description,
                                weatherCode = weather.weatherCode
                            )
                            
                            // 🔥 JERARQUÍA DE BADGES (misma lógica que RouteDetailDialog):
                            // 1. Lluvia: Máxima prioridad (siempre se muestra si existe)
                            // 2. Calzada mojada: Solo si NO hay lluvia (excluyente con lluvia)
                            // 3. Condiciones extremas: COMPLEMENTARIO (puede coexistir con lluvia o calzada mojada)
                            
                            // Detectar y guardar condiciones extremas (complementario, no excluye otros badges)
                            if (hasExtremeConditions) {
                                weatherHadExtremeConditions = true
                                
                                // Detectar y guardar la causa específica
                                val cause = detectExtremeCause(
                                    windSpeed = weather.windSpeed,
                                    windGusts = weather.windGusts,
                                    temperature = weather.temperature,
                                    uvIndex = weather.uvIndex,
                                    isDay = weather.isDay,
                                    weatherEmoji = effectiveEmoji,
                                    weatherDescription = weather.description,
                                    weatherCode = weather.weatherCode
                                )
                                if (cause != null && weatherExtremeReason == null) {
                                    // Guardar la primera causa detectada (la más grave por prioridad)
                                    weatherExtremeReason = cause
                                }
                                
                                // Rastrear valores extremos para reflejarlos en los badges
                                val windSpeedKmh = (weather.windSpeed ?: 0.0) * 3.6
                                val windGustsKmh = (weather.windGusts ?: 0.0) * 3.6
                                maxWindSpeed = maxOf(maxWindSpeed, windSpeedKmh)
                                maxWindGusts = maxOf(maxWindGusts, windGustsKmh)
                                // weather.temperature es Double (no nullable), siempre tiene valor
                                minTemperature = minOf(minTemperature, weather.temperature)
                                maxTemperature = maxOf(maxTemperature, weather.temperature)
                                if (weather.uvIndex != null && weather.isDay) {
                                    maxUvIndex = maxOf(maxUvIndex, weather.uvIndex)
                                }
                            }
                            
                            // Lluvia: Máxima prioridad (siempre se muestra si existe)
                            if (isActiveRain) {
                                weatherHadRain = true
                                weatherHadWetRoad = false // Lluvia excluye calzada mojada
                            } else if (isWetRoad) {
                                // Calzada mojada: Solo si NO hay lluvia activa
                                weatherHadWetRoad = true
                                // Asegurar que haya precipitación para que el badge se muestre
                                // Usar umbral de 0.4 mm (lluvia activa) como referencia
                                // Forzar a 0.2 mm (dentro del rango de calzada mojada, por debajo de lluvia activa)
                                if (weatherMaxPrecipitation == null || weatherMaxPrecipitation < 0.4) {
                                    weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation ?: 0.0, 0.2)
                                }
                            }
                            
                            // Lógica: solo actualizar si detecta lluvia nueva (para icono)
                            // Si ya había lluvia y ahora no, mantener el icono de lluvia
                            if (isRaining) {
                                if (!weatherHadRain) {
                                    // Nueva lluvia detectada - requiere confirmación (2 chequeos seguidos)
                                    if (pendingRainConfirmation && pendingRainMinute != null) {
                                        // Confirmación: segundo chequeo también detecta lluvia
                                        Log.d(TAG, "🌧️ [Monitoreo continuo] Lluvia CONFIRMADA en minuto $elapsedMinutes (detectada primero en minuto $pendingRainMinute): $rainUserReason")
                                        
                                        weatherHadRain = true
                                        weatherHadWetRoad = false // Lluvia es más grave que calzada mojada
                                        weatherRainStartMinute = pendingRainMinute // Usar el minuto del primer chequeo
                                        weatherRainReason = pendingRainReason ?: rainUserReason
                                        
                                        // Actualizar solo los campos visibles en la tarjeta de clima durante tracking
                                        // (icono, temperatura, viento y dirección) ya que al finalizar se guardan los datos del inicio
                                        val (effectiveWeatherCode, _, _, _) = resolveEffectiveWeatherCode(
                                            weather.weatherCode,
                                            weather.precipitation,
                                            weather.rain,
                                            weather.showers,
                                            weather.humidity,
                                            weather.rainProbability,
                                            weather.windSpeed
                                        )
                                        
                                        val effectiveEmoji = com.zipstats.app.repository.WeatherRepository.getEmojiForWeather(
                                            effectiveWeatherCode,
                                            if (weather.isDay) 1 else 0
                                        )
                                        
                                        // Actualizar solo campos visibles en TrackingWeatherCard: icono, temperatura, viento y dirección
                                        val currentStatus = _weatherStatus.value
                                        if (currentStatus is WeatherStatus.Success) {
                                            _weatherStatus.value = currentStatus.copy(
                                                temperature = weather.temperature,
                                                weatherEmoji = effectiveEmoji,
                                                weatherCode = effectiveWeatherCode,
                                                icon = effectiveWeatherCode.toString(),
                                                windSpeed = weather.windSpeed,
                                                windDirection = weather.windDirection,
                                                isDay = weather.isDay
                                                // No actualizar: feelsLike, humidity, windGusts, uvIndex, description
                                                // porque no se muestran en la tarjeta y al finalizar se guardan los datos del inicio
                                            )
                                        }
                                        
                                        // Limpiar confirmación pendiente
                                        pendingRainConfirmation = false
                                        pendingRainMinute = null
                                        pendingRainReason = null
                                    } else {
                                        // Primer chequeo detecta lluvia - marcar como pendiente de confirmación
                                        Log.d(TAG, "🌧️ [Monitoreo continuo] Lluvia detectada en minuto $elapsedMinutes (pendiente de confirmación): $rainUserReason")
                                        pendingRainConfirmation = true
                                        pendingRainMinute = elapsedMinutes
                                        pendingRainReason = rainUserReason
                                    }
                                }
                                
                                // Actualizar precipitación máxima
                                weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, weather.precipitation)
                            } else {
                                // No hay lluvia ahora
                                if (pendingRainConfirmation) {
                                    // Falso positivo - cancelar confirmación pendiente
                                    Log.d(TAG, "🌧️ [Monitoreo continuo] Falso positivo cancelado - no llueve en minuto $elapsedMinutes")
                                    pendingRainConfirmation = false
                                    pendingRainMinute = null
                                    pendingRainReason = null
                                }
                                
                                // Si ya había lluvia y ahora no, mantener el icono de lluvia
                                if (weatherHadRain) {
                                    Log.d(TAG, "🌧️ [Monitoreo continuo] Ya no llueve, pero manteniendo icono de lluvia (ya llovió antes)")
                                }
                            }
                        }.onFailure { error ->
                            Log.w(TAG, "⚠️ [Monitoreo continuo] Error al obtener clima: ${error.message}")
                            // En caso de error, no limpiar pending - esperar al siguiente chequeo
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [Monitoreo continuo] Excepción: ${e.message}", e)
                        // En caso de excepción, no limpiar pending - esperar al siguiente chequeo
                    }
                }
                
                // Después del primer chequeo, esperar 10 minutos antes del siguiente
                if (isFirstCheck) {
                    isFirstCheck = false
                }
                kotlinx.coroutines.delay(10 * 60 * 1000L)
            }
            
            // Limpiar estado pendiente explícitamente al detener el monitoreo
            pendingRainConfirmation = false
            pendingRainMinute = null
            pendingRainReason = null
            
            Log.d(TAG, "🌧️ [Monitoreo continuo] Detenido (tracking finalizado)")
        }
    }

    /**
     * Captura el clima al inicio de la ruta con reintentos automáticos
     * Si ya hay snapshot inicial, lo reutiliza
     * Tiene hasta 60 segundos para obtener el clima antes de marcar error
     * Se ejecuta en segundo plano y no bloquea el inicio del tracking
     */
    private suspend fun captureStartWeather() {
        // Si ya hay snapshot inicial, reutilizarlo
        val snapshot = _initialWeatherSnapshot
        if (snapshot != null && _initialWeatherCaptured) {
            Log.d(TAG, "♻️ Reutilizando clima inicial capturado en precarga")
            
            // Resolver código de clima efectivo
            val (effectiveWeatherCode, _, _, isDerived) = resolveEffectiveWeatherCode(
                snapshot.weatherCode,
                snapshot.precipitation,
                snapshot.rain,
                snapshot.showers,
                snapshot.humidity,
                snapshot.rainProbability,
                snapshot.windSpeed
            )
            
            // Obtener emoji, descripción e icono usando el código efectivo
            val effectiveEmoji = com.zipstats.app.repository.WeatherRepository.getEmojiForWeather(
                effectiveWeatherCode,
                if (snapshot.isDay) 1 else 0
            )
            val effectiveDescription = if (isDerived) {
                "Lluvia"
            } else {
                com.zipstats.app.repository.WeatherRepository.getDescriptionForWeather(
                    effectiveWeatherCode,
                    if (snapshot.isDay) 1 else 0
                )
            }
            
            // Detectar lluvia durante la ruta (inicialización)
            val (isRaining, _, rainUserReason) = isRainingForScooter(
                snapshot.weatherCode,
                snapshot.precipitation,
                snapshot.rain,
                snapshot.showers,
                snapshot.humidity,
                snapshot.rainProbability,
                snapshot.windSpeed
            )
            
            if (isRaining) {
                if (!weatherHadRain) {
                    weatherHadRain = true
                    weatherRainStartMinute = 0 // Al inicio de la ruta
                    weatherRainReason = rainUserReason
                }
                weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, snapshot.precipitation)
            }
            
            // Guardar en variables de inicio de ruta
            _startWeatherTemperature = snapshot.temperature
            _startWeatherEmoji = effectiveEmoji
            _startWeatherDescription = effectiveDescription
            _startWeatherIsDay = snapshot.isDay
            _startWeatherFeelsLike = snapshot.feelsLike
            _startWeatherHumidity = snapshot.humidity
            _startWeatherWindSpeed = snapshot.windSpeed
            _startWeatherUvIndex = snapshot.uvIndex
            _startWeatherWindDirection = snapshot.windDirection
            _startWeatherWindGusts = snapshot.windGusts
            _startWeatherRainProbability = snapshot.rainProbability

            routeRepository.saveTempWeather(snapshot)

            _weatherStatus.value = WeatherStatus.Success(
                temperature = snapshot.temperature,
                feelsLike = snapshot.feelsLike,
                description = effectiveDescription,
                icon = effectiveWeatherCode.toString(),
                humidity = snapshot.humidity,
                windSpeed = snapshot.windSpeed,
                weatherEmoji = effectiveEmoji,
                weatherCode = effectiveWeatherCode,
                isDay = snapshot.isDay,
                uvIndex = snapshot.uvIndex,
                windDirection = snapshot.windDirection,
                windGusts = snapshot.windGusts,
                rainProbability = snapshot.rainProbability,
                precipitation = snapshot.precipitation,
                rain = snapshot.rain,
                showers = snapshot.showers
            )
            
            Log.d(TAG, "✅ Clima inicial reutilizado: ${snapshot.temperature}°C $effectiveEmoji")
            return
        }
        
        // Si no hay snapshot, proceder con la captura normal (fallback)
        Log.d(TAG, "🌤️ No hay snapshot inicial, capturando clima normalmente")
        
        // Cancelar cualquier job anterior de clima
        weatherJob?.cancel()
        
        // Iniciar nuevo job en segundo plano
        weatherJob = viewModelScope.launch {
            try {
                _weatherStatus.value = WeatherStatus.Loading
                
                // Paso 1: Esperar hasta 30 segundos a que llegue el primer punto GPS
                val startTime = System.currentTimeMillis()
                Log.d(TAG, "🌤️ [Paso 1/2] Esperando primer punto GPS (hasta 30s)...")
                var attempts = 0
                var points = _routePoints.value
                
                // 60 intentos × 500ms = 30 segundos para GPS
                while (points.isEmpty() && attempts < 60) {
                    kotlinx.coroutines.delay(500)
                    points = _routePoints.value
                    attempts++
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                    if (attempts % 10 == 0) { // Log cada 5 segundos
                        Log.d(TAG, "🌤️ Esperando GPS... ${elapsedSeconds}s transcurridos")
                    }
                }
                
                val gpsWaitTime = (System.currentTimeMillis() - startTime) / 1000
                if (points.isEmpty()) {
                    Log.w(TAG, "⚠️ No hay puntos GPS después de ${gpsWaitTime}s. Mostrando error y botón clicable.")
                    _weatherStatus.value = WeatherStatus.NotAvailable
                    return@launch
                }
                
                val firstPoint = points.first()
                Log.d(TAG, "🌤️ [Paso 2/2] GPS obtenido en ${gpsWaitTime}s. Consultando clima...")
                Log.d(TAG, "🌤️ Ubicación: lat=${firstPoint.latitude}, lon=${firstPoint.longitude}")
                
                // Paso 2: Intentar obtener el clima con reintentos automáticos
                // Objetivo: intentar durante ~30 segundos más, totalizando ~60s desde inicio
                val maxRetries = 5  // Aumentado de 3 a 5 intentos
                var retryCount = 0
                var success = false
                val weatherRepository = com.zipstats.app.repository.WeatherRepository()
                val startApiTime = System.currentTimeMillis()
                
                while (!success && retryCount < maxRetries) {
                    retryCount++
                    Log.d(TAG, "🌤️ Intento ${retryCount}/${maxRetries} de obtener clima...")
                    
                    val result = weatherRepository.getCurrentWeather(
                        latitude = firstPoint.latitude,
                        longitude = firstPoint.longitude
                    )
                    
                    result.onSuccess { weather ->
                        // Resolver código de clima efectivo basado en detección de lluvia efectiva
                        val (effectiveWeatherCode, _, userFriendlyReason, isDerived) = resolveEffectiveWeatherCode(
                            weather.weatherCode,
                            weather.precipitation,
                            weather.rain,
                            weather.showers,
                            weather.humidity,
                            weather.rainProbability,
                            weather.windSpeed
                        )
                        
                        // Obtener emoji, descripción e icono usando el código efectivo
                        val effectiveEmoji = com.zipstats.app.repository.WeatherRepository.getEmojiForWeather(
                            effectiveWeatherCode,
                            if (weather.isDay) 1 else 0
                        )
                        // Si es derivado, usar "Lluvia" en lugar de la descripción del modelo
                        val effectiveDescription = if (isDerived) {
                            "Lluvia"
                        } else {
                            com.zipstats.app.repository.WeatherRepository.getDescriptionForWeather(
                                effectiveWeatherCode,
                                if (weather.isDay) 1 else 0
                            )
                        }
                        val effectiveIcon = effectiveWeatherCode.toString()
                        
                        // Validar que el clima recibido sea válido antes de guardarlo
                        if (weather.temperature.isNaN() || 
                            weather.temperature.isInfinite() || 
                            weather.temperature < -50 || 
                            weather.temperature > 60) {
                            Log.w(TAG, "⚠️ Clima recibido con temperatura inválida: ${weather.temperature}°C. Reintentando...")
                            // Continuar intentando en lugar de marcar error inmediatamente
                            if (retryCount < maxRetries) {
                                val delayMs = when (retryCount) {
                                    1 -> 5000L
                                    2 -> 8000L
                                    3 -> 10000L
                                    4 -> 12000L
                                    else -> 15000L
                                }
                                Log.d(TAG, "⏳ Reintentando en ${delayMs / 1000}s...")
                                kotlinx.coroutines.delay(delayMs)
                            } else {
                                // Último intento y sigue inválido, marcar error
                                _weatherStatus.value = WeatherStatus.Error("Temperatura inválida recibida")
                                Log.e(TAG, "❌ Todos los intentos agotados. Temperatura inválida.")
                                return@launch
                            }
                            // Continuar el bucle while
                            return@onSuccess
                        }
                        
                        if (effectiveEmoji.isBlank()) {
                            Log.w(TAG, "⚠️ Clima recibido con emoji vacío. Reintentando...")
                            // Continuar intentando en lugar de marcar error inmediatamente
                            if (retryCount < maxRetries) {
                                val delayMs = when (retryCount) {
                                    1 -> 5000L
                                    2 -> 8000L
                                    3 -> 10000L
                                    4 -> 12000L
                                    else -> 15000L
                                }
                                Log.d(TAG, "⏳ Reintentando en ${delayMs / 1000}s...")
                                kotlinx.coroutines.delay(delayMs)
                            } else {
                                // Último intento y sigue vacío, marcar error
                                _weatherStatus.value = WeatherStatus.Error("Emoji de clima vacío")
                                Log.e(TAG, "❌ Todos los intentos agotados. Emoji vacío.")
                                return@launch
                            }
                            // Continuar el bucle while
                            return@onSuccess
                        }
                        
                        // Detectar lluvia durante la ruta (inicialización)
                        val (isRaining, _, rainUserReason) = isRainingForScooter(
                            weather.weatherCode,
                            weather.precipitation,
                            weather.rain,
                            weather.showers,
                            weather.humidity,
                            weather.rainProbability,
                            weather.windSpeed
                        )
                        
                        if (isRaining) {
                            if (!weatherHadRain) {
                                weatherHadRain = true
                                weatherRainStartMinute = 0 // Al inicio de la ruta
                                weatherRainReason = rainUserReason // Guardar razón amigable para el usuario
                            }
                            weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, weather.precipitation)
                        }
                        
                        // Clima válido - guardar y salir (usando valores efectivos)
                        _startWeatherTemperature = weather.temperature
                        _startWeatherEmoji = effectiveEmoji
                        _startWeatherDescription = effectiveDescription
                        _startWeatherIsDay = weather.isDay
                        _startWeatherFeelsLike = weather.feelsLike
                        _startWeatherHumidity = weather.humidity
                        _startWeatherWindSpeed = weather.windSpeed
                        _startWeatherUvIndex = weather.uvIndex
                        _startWeatherWindDirection = weather.windDirection
                        _startWeatherWindGusts = weather.windGusts
                        _startWeatherRainProbability = weather.rainProbability

                        routeRepository.saveTempWeather(weather)

                        _weatherStatus.value = WeatherStatus.Success(
                            temperature = weather.temperature,
                            feelsLike = weather.feelsLike,
                            description = effectiveDescription,
                            icon = effectiveIcon,
                            humidity = weather.humidity,
                            windSpeed = weather.windSpeed,
                            weatherEmoji = effectiveEmoji,
                            weatherCode = effectiveWeatherCode,
                            isDay = weather.isDay,
                            uvIndex = weather.uvIndex,
                            windDirection = weather.windDirection,
                            windGusts = weather.windGusts,
                            rainProbability = weather.rainProbability,
                            precipitation = weather.precipitation,
                            rain = weather.rain,
                            showers = weather.showers
                        )

                        success = true
                        
                        val elapsedMs = System.currentTimeMillis() - startApiTime
                        Log.d(TAG, "✅ Clima capturado y VALIDADO en ${elapsedMs}ms: ${weather.temperature}°C $effectiveEmoji")
                        Log.d(TAG, "✅ Descripción: $effectiveDescription")
                        Log.d(TAG, "✅ Precipitación: ${weather.precipitation}mm, Rain: ${weather.rain}mm, Showers: ${weather.showers}mm")
                        Log.d(TAG, "✅ Humedad: ${weather.humidity}%, Prob. lluvia: ${weather.rainProbability}%, Viento: ${weather.windSpeed} km/h")
                        if (effectiveWeatherCode != weather.weatherCode || isDerived) {
                            Log.d(TAG, "🌧️ Código ajustado: ${weather.weatherCode} -> $effectiveWeatherCode (${if (isDerived) "derivado" else "modelo"}: $userFriendlyReason)")
                        }
                    }.onFailure { error ->
                        Log.e(TAG, "❌ Error en intento ${retryCount}/${maxRetries}: ${error.message}")
                        
                        if (retryCount < maxRetries) {
                            // Delay progresivo más largo: 5s, 8s, 10s, 12s, 15s
                            val delayMs = when (retryCount) {
                                1 -> 5000L
                                2 -> 8000L
                                3 -> 10000L
                                4 -> 12000L
                                else -> 15000L
                            }
                            Log.d(TAG, "⏳ Reintentando en ${delayMs / 1000}s...")
                            kotlinx.coroutines.delay(delayMs)
                        } else {
                            // Último intento falló - ahora sí marcar error y hacer botón clicable
                            val totalElapsed = (System.currentTimeMillis() - startApiTime) / 1000
                            val totalTimeFromStart = (System.currentTimeMillis() - startTime) / 1000
                            _weatherStatus.value = WeatherStatus.Error(
                                error.message ?: "Error al obtener clima"
                            )
                            Log.e(TAG, "❌ Intento ${retryCount}/${maxRetries} falló después de ${totalElapsed}s de API (${totalTimeFromStart}s desde inicio). Clima no disponible. Botón clicable activado.")
                        }
                    }
                }
                
                // Si llegamos aquí sin éxito y sin error marcado, marcar como no disponible
                if (!success && _weatherStatus.value is WeatherStatus.Loading) {
                    _weatherStatus.value = WeatherStatus.NotAvailable
                    Log.w(TAG, "⚠️ Clima no obtenido después de todos los intentos. Botón clicable activado.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción al capturar clima: ${e.message}", e)
                // Solo marcar error si es excepción, no si es por tiempo agotado
                if (_weatherStatus.value is WeatherStatus.Loading) {
                    _weatherStatus.value = WeatherStatus.Error("Excepción: ${e.message}")
                }
            }
        }
    }

    /**
     * Intenta obtener el clima manualmente cuando el usuario hace clic en el cartel
     * Solo funciona si hay puntos GPS y no hay clima guardado aún
     */
    fun fetchWeatherManually() {
        viewModelScope.launch {
            try {
                val points = _routePoints.value
                if (points.isEmpty()) {
                    Log.w(TAG, "⚠️ No hay puntos GPS para obtener clima manualmente")
                    _message.value = "Espera a tener puntos GPS"
                    return@launch
                }
                
                // Si ya hay clima válido, no hacer nada
                if (_startWeatherTemperature != null) {
                    Log.d(TAG, "✅ Ya hay clima guardado, no es necesario obtenerlo de nuevo")
                    return@launch
                }
                
                Log.d(TAG, "🌤️ Usuario solicitó obtener clima manualmente")
                val firstPoint = points.first()
                
                _weatherStatus.value = WeatherStatus.Loading
                
                val weatherRepository = com.zipstats.app.repository.WeatherRepository()
                val result = weatherRepository.getCurrentWeather(
                    latitude = firstPoint.latitude,
                    longitude = firstPoint.longitude
                )
                
                result.onSuccess { weather ->
                    // Resolver código de clima efectivo basado en detección de lluvia efectiva
                    val (effectiveWeatherCode, _, _, isDerived) = resolveEffectiveWeatherCode(
                        weather.weatherCode,
                        weather.precipitation,
                        weather.rain,
                        weather.showers,
                        weather.humidity,
                        weather.rainProbability,
                        weather.windSpeed
                    )
                    
                    // Obtener emoji, descripción e icono usando el código efectivo
                    val effectiveEmoji = com.zipstats.app.repository.WeatherRepository.getEmojiForWeather(
                        effectiveWeatherCode,
                        if (weather.isDay) 1 else 0
                    )
                    // Si es derivado, usar "Lluvia" en lugar de la descripción del modelo
                    val effectiveDescription = if (isDerived) {
                        "Lluvia"
                    } else {
                        com.zipstats.app.repository.WeatherRepository.getDescriptionForWeather(
                            effectiveWeatherCode,
                            if (weather.isDay) 1 else 0
                        )
                    }
                    val effectiveIcon = effectiveWeatherCode.toString()
                    
                    // Validar que el clima recibido sea válido antes de guardarlo
                    if (weather.temperature.isNaN() || 
                        weather.temperature.isInfinite() || 
                        weather.temperature < -50 || 
                        weather.temperature > 60) {
                        Log.e(TAG, "⚠️ Clima recibido con temperatura inválida: ${weather.temperature}°C. NO se guardará.")
                        _weatherStatus.value = WeatherStatus.Error("Temperatura inválida recibida")
                        _message.value = "Temperatura inválida recibida"
                        return@launch
                    }
                    
                    if (effectiveEmoji.isBlank()) {
                        Log.e(TAG, "⚠️ Clima recibido con emoji vacío. NO se guardará.")
                        _weatherStatus.value = WeatherStatus.Error("Emoji de clima vacío")
                        _message.value = "Emoji de clima vacío"
                        return@launch
                    }
                    
                    // Detectar lluvia durante la ruta usando la nueva función
                    val (isRaining, _, rainUserReason) = isRainingForScooter(
                        weather.weatherCode,
                        weather.precipitation,
                        weather.rain,
                        weather.showers,
                        weather.humidity,
                        weather.rainProbability,
                        weather.windSpeed
                    )
                    
                    if (isRaining) {
                        if (!weatherHadRain) {
                            weatherHadRain = true
                            // Calcular minutos transcurridos desde el inicio
                            val elapsedMinutes = if (_startTime.value > 0) {
                                ((System.currentTimeMillis() - _startTime.value) / (1000 * 60)).toInt()
                            } else {
                                0
                            }
                            weatherRainStartMinute = elapsedMinutes
                            weatherRainReason = rainUserReason // Guardar razón amigable para el usuario
                        }
                        weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, weather.precipitation)
                    }
                    
                    _startWeatherTemperature = weather.temperature
                    _startWeatherEmoji = effectiveEmoji
                    _startWeatherDescription = effectiveDescription
                    _startWeatherIsDay = weather.isDay
                    _startWeatherFeelsLike = weather.feelsLike
                    _startWeatherHumidity = weather.humidity
                    _startWeatherWindSpeed = weather.windSpeed
                    _startWeatherUvIndex = weather.uvIndex
                    _startWeatherWindDirection = weather.windDirection
                    _startWeatherWindGusts = weather.windGusts
                    _startWeatherRainProbability = weather.rainProbability

                    routeRepository.saveTempWeather(weather)


                    _weatherStatus.value = WeatherStatus.Success(
                        temperature = weather.temperature,
                        feelsLike = weather.feelsLike,
                        description = effectiveDescription,
                        icon = effectiveIcon,
                        humidity = weather.humidity,
                        windSpeed = weather.windSpeed,
                        weatherEmoji = effectiveEmoji,
                        weatherCode = effectiveWeatherCode,
                        isDay = weather.isDay,
                        uvIndex = weather.uvIndex,
                        windDirection = weather.windDirection,
                        windGusts = weather.windGusts,
                        rainProbability = weather.rainProbability,
                        precipitation = weather.precipitation,
                        rain = weather.rain,
                        showers = weather.showers
                    )
                    Log.d(TAG, "✅ Clima obtenido manualmente: ${weather.temperature}°C $effectiveEmoji")
                    _message.value = "Clima obtenido: ${formatTemperature(weather.temperature, 0)}°C $effectiveEmoji"
                }.onFailure { error ->
                    Log.e(TAG, "❌ Error al obtener clima manualmente: ${error.message}")
                    _weatherStatus.value = WeatherStatus.Error(error.message ?: "Error al obtener clima")
                    _message.value = "Error al obtener clima: ${error.message}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción al obtener clima manualmente: ${e.message}", e)
                _weatherStatus.value = WeatherStatus.Error("Excepción: ${e.message}")
                _message.value = "Error: ${e.message}"
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
                // 🛑 CORTE DE SEGURIDAD TOTAL 🛑
                // Cancelamos la escucha del estado global. 
                // A partir de esta línea, NADA externo puede cambiar el estado de esta pantalla.
                globalStateJob?.cancel()
                globalStateJob = null

                // 1. Establecemos estado local GUARDANDO
                _trackingState.value = TrackingState.Saving
                
                // 2. Mostrar overlay (UI)
                appOverlayRepository.showSplashOverlay("Guardando ruta…")

                // 3. Detener el servicio (Ahora es seguro, nadie escuchará el evento de parada)
                stopTrackingService()
                trackingStateManager.resetState() // Esto emitirá 'false' globalmente, pero ya no escuchamos.
                
                // Guardar datos antes de resetear
                val scooter = _selectedScooter.value ?: throw Exception("No hay vehículo seleccionado")
                val points = _routePoints.value
                val startTime = _startTime.value
                val endTime = System.currentTimeMillis()
                
                // Resetear variables locales (el estado ya está en Saving y el trackingStateManager ya se reseteó arriba)
                _routePoints.value = emptyList()
                _currentDistance.value = 0.0
                _currentSpeed.value = 0.0
                _duration.value = 0L
                
                if (points.isEmpty()) {
                    throw Exception("No se registraron puntos GPS")
                }
                
                // Verificar estado del clima
                var weatherState = _weatherStatus.value
                Log.d(TAG, "Estado del clima al finalizar: $weatherState")
                
                // Si el clima aún está cargando, dar unos segundos más de gracia
                if (weatherState is WeatherStatus.Loading) {
                    Log.d(TAG, "⏳ Clima aún cargando, esperando hasta 5s más...")
                    var waited = 0
                    while (_weatherStatus.value is WeatherStatus.Loading && waited < 5000) {
                        kotlinx.coroutines.delay(500)
                        waited += 500
                    }
                    weatherState = _weatherStatus.value
                    Log.d(TAG, "Estado después de espera: $weatherState")
                }
                
                // Guardar referencia al clima antes de resetear
                // IMPORTANTE: Solo usar clima si realmente se capturó correctamente (no valores genéricos)
                var savedWeatherTemp = _startWeatherTemperature
                var savedWeatherEmoji = _startWeatherEmoji
                var savedWeatherDesc = _startWeatherDescription
                var savedIsDay = _startWeatherIsDay ?: true
                var savedFeelsLike = _startWeatherFeelsLike
                var savedHumidity = _startWeatherHumidity
                var savedWindSpeed = _startWeatherWindSpeed
                var savedUvIndex = _startWeatherUvIndex
                var savedWindDirection = _startWeatherWindDirection
                var savedWindGusts = _startWeatherWindGusts
                var savedRainProbability = _startWeatherRainProbability
                
                // Validar que el clima sea real y no valores por defecto
                // Aceptar cualquier emoji válido (incluido ☁️) pero temperatura debe ser válida
                var hasValidWeather = savedWeatherTemp != null && 
                                      savedWeatherTemp > -50 && savedWeatherTemp < 60 && // Rango válido de temperatura
                                      savedWeatherEmoji != null && 
                                      savedWeatherEmoji.isNotBlank()
                
                Log.d(TAG, "🔍 Validación clima inicial: temp=$savedWeatherTemp, emoji=$savedWeatherEmoji, válido=$hasValidWeather")
                
                // Si no hay clima válido al finalizar, intentar obtenerlo una última vez
                if (!hasValidWeather && points.isNotEmpty()) {
                    Log.d(TAG, "🌤️ No hay clima válido al finalizar, intentando obtenerlo...")
                    val firstPoint = points.first()
                    
                    try {
                        val weatherRepository = com.zipstats.app.repository.WeatherRepository()
                        _weatherStatus.value = WeatherStatus.Loading
                        
                        val weatherResult = weatherRepository.getCurrentWeather(
                            latitude = firstPoint.latitude,
                            longitude = firstPoint.longitude
                        )
                        
                        // Verificar el resultado (getCurrentWeather devuelve Result, así que verificamos directamente)
                        weatherResult.fold(
                            onSuccess = { weather ->
                                // Resolver código de clima efectivo basado en detección de lluvia efectiva
                                val (effectiveWeatherCode, _, _, isDerived) = resolveEffectiveWeatherCode(
                                    weather.weatherCode,
                                    weather.precipitation,
                                    weather.rain,
                                    weather.showers,
                                    weather.humidity,
                                    weather.rainProbability,
                                    weather.windSpeed
                                )
                                
                                // Obtener emoji, descripción usando el código efectivo
                                val effectiveEmoji = com.zipstats.app.repository.WeatherRepository.getEmojiForWeather(
                                    effectiveWeatherCode,
                                    if (weather.isDay) 1 else 0
                                )
                                // Si es derivado, usar "Lluvia" en lugar de la descripción del modelo
                                val effectiveDescription = if (isDerived) {
                                    "Lluvia"
                                } else {
                                    com.zipstats.app.repository.WeatherRepository.getDescriptionForWeather(
                                        effectiveWeatherCode,
                                        if (weather.isDay) 1 else 0
                                    )
                                }
                                
                                // Validar que el clima recibido sea válido
                                if (!weather.temperature.isNaN() && 
                                    !weather.temperature.isInfinite() && 
                                    weather.temperature > -50 && 
                                    weather.temperature < 60 &&
                                    weather.temperature != 0.0 &&
                                    effectiveEmoji.isNotBlank()) {
                                    
                                    savedWeatherTemp = weather.temperature
                                    savedWeatherEmoji = effectiveEmoji
                                    savedWeatherDesc = effectiveDescription
                                    savedIsDay = weather.isDay
                                    savedFeelsLike = weather.feelsLike
                                    savedHumidity = weather.humidity
                                    savedWindSpeed = weather.windSpeed
                                    savedUvIndex = _startWeatherUvIndex
                                    savedWindDirection = _startWeatherWindDirection
                                    savedWindGusts = _startWeatherWindGusts
                                    savedRainProbability = _startWeatherRainProbability
                                    hasValidWeather = true
                                    
                                    // Detectar lluvia durante la ruta usando la nueva función
                                    val (isRaining, _, rainUserReason) = isRainingForScooter(
                                        weather.weatherCode,
                                        weather.precipitation,
                                        weather.rain,
                                        weather.showers,
                                        weather.humidity,
                                        weather.rainProbability,
                                        weather.windSpeed
                                    )
                                    
                                    if (isRaining) {
                                        if (!weatherHadRain) {
                                            weatherHadRain = true
                                            // Calcular minutos transcurridos desde el inicio
                                            val elapsedMinutesCalc = if (_startTime.value > 0) {
                                                ((endTime - _startTime.value) / (1000 * 60)).toInt()
                                            } else {
                                                0
                                            }
                                            weatherRainStartMinute = elapsedMinutesCalc
                                            weatherRainReason = rainUserReason // Guardar razón amigable para el usuario
                                        }
                                        weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, weather.precipitation)
                                    }
                                    
                                    Log.d(TAG, "✅ Clima obtenido al finalizar: ${savedWeatherTemp}°C $effectiveEmoji")
                                } else {
                                    Log.w(TAG, "⚠️ Clima obtenido pero inválido, no se usará")
                                }
                            },
                            onFailure = { error ->
                                Log.e(TAG, "❌ Error al obtener clima al finalizar: ${error.message}")
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Excepción al obtener clima al finalizar: ${e.message}", e)
                    }
                }
                
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

                // Calcular minutos transcurridos para weatherRainStartMinute
                if (_startTime.value > 0) {
                    ((endTime - _startTime.value) / (1000 * 60)).toInt()
                } else {
                    0
                }
                
                // Usar el clima capturado al INICIO de la ruta SOLO si es válido
                // 🔥 Si detectamos condiciones extremas durante la ruta, usar los valores máximos/mínimos
                // para que los badges reflejen el estado más adverso
                // NOTA: savedWindSpeed y savedWindGusts están en m/s, pero Route espera km/h
                // Convertir a km/h antes de comparar y guardar
                val savedWindSpeedKmh = (savedWindSpeed ?: 0.0) * 3.6
                val savedWindGustsKmh = (savedWindGusts ?: 0.0) * 3.6
                
                // 🔥 LÓGICA DE VALORES DE VIENTO PARA BADGES:
                // Si se detectaron condiciones extremas durante la ruta (valores máximos > 0), usar esos
                // Si solo se detectaron en precarga (weatherHadExtremeConditions true pero maxWindSpeed = 0),
                // usar los valores iniciales convertidos a km/h para reflejar el estado más adverso
                val finalWindSpeed = if (weatherHadExtremeConditions) {
                    if (maxWindSpeed > 0.0) {
                        // Se detectaron durante la ruta: usar el máximo entre inicial y durante la ruta
                        maxOf(savedWindSpeedKmh, maxWindSpeed)
                    } else {
                        // Solo se detectaron en precarga: usar el valor inicial convertido a km/h
                        // Esto refleja el estado más adverso que ocurrió (al inicio de la ruta)
                        if (savedWindSpeed != null) savedWindSpeedKmh else null
                    }
                } else {
                    // No hubo condiciones extremas: usar el valor inicial convertido a km/h
                    if (savedWindSpeed != null) savedWindSpeedKmh else null
                }
                
                val finalWindGusts = if (weatherHadExtremeConditions) {
                    if (maxWindGusts > 0.0) {
                        // Se detectaron durante la ruta: usar el máximo entre inicial y durante la ruta
                        maxOf(savedWindGustsKmh, maxWindGusts)
                    } else {
                        // Solo se detectaron en precarga: usar el valor inicial convertido a km/h
                        // Esto refleja el estado más adverso que ocurrió (al inicio de la ruta)
                        if (savedWindGusts != null) savedWindGustsKmh else null
                    }
                } else {
                    // No hubo condiciones extremas: usar el valor inicial convertido a km/h
                    if (savedWindGusts != null) savedWindGustsKmh else null
                }
                
                val finalTemperature = if (weatherHadExtremeConditions && 
                    (minTemperature < Double.MAX_VALUE || maxTemperature > Double.MIN_VALUE)) {
                    // Usar el valor más extremo (más bajo o más alto)
                    when {
                        minTemperature < 0 -> minTemperature // Temperatura bajo cero
                        maxTemperature > 35 -> maxTemperature // Temperatura muy alta
                        else -> savedWeatherTemp // Mantener temperatura inicial si no es extrema
                    }
                } else {
                    savedWeatherTemp
                }
                
                val finalUvIndex = if (weatherHadExtremeConditions && maxUvIndex > 0.0) {
                    maxOf(savedUvIndex ?: 0.0, maxUvIndex)
                } else {
                    savedUvIndex
                }
                
                // 🔥 LÓGICA DE BADGES: Reflejar el estado MÁS ADVERSO que ocurrió durante la ruta
                // Si weatherHadExtremeConditions es true (ya sea en precarga o durante la ruta), 
                // marcar como extremas. Esto asegura que:
                // - Si empieza en condiciones extremas y luego acaba en normales → Badge de extremas
                // - Si empieza en lluvia y acaba en seco → Badge de lluvia (ya manejado por weatherHadRain)
                // - Si arranca en calzada mojada y acaba en lluvia → Badge de lluvia (ya manejado por weatherHadRain)
                // Para los valores de viento, usar máximos si se detectaron durante la ruta, sino usar valores iniciales
                val hadExtremeConditionsDuringRoute = weatherHadExtremeConditions
                val useMaxValuesForExtremes = maxWindSpeed > 0.0 || maxWindGusts > 0.0 || 
                    (minTemperature < Double.MAX_VALUE && (minTemperature < 0 || minTemperature > 35)) ||
                    (maxTemperature > Double.MIN_VALUE && maxTemperature > 35) ||
                    maxUvIndex > 8.0
                
                val route = if (hasValidWeather) {
                    Log.d(TAG, "✅ Usando clima válido del INICIO de la ruta: ${savedWeatherTemp}°C ${savedWeatherEmoji}")
                    if (hadExtremeConditionsDuringRoute) {
                        if (useMaxValuesForExtremes) {
                            Log.d(TAG, "⚠️ Ajustando datos de clima con valores extremos detectados durante la ruta")
                        } else {
                            Log.d(TAG, "⚠️ Condiciones extremas detectadas en precarga - badge reflejará estado más adverso")
                        }
                    }
                    baseRoute.copy(
                        weatherTemperature = finalTemperature,
                        weatherEmoji = savedWeatherEmoji,
                        weatherDescription = savedWeatherDesc,
                        weatherIsDay = savedIsDay,
                        weatherFeelsLike = savedFeelsLike,
                        weatherHumidity = savedHumidity,
                        weatherWindSpeed = finalWindSpeed,
                        weatherUvIndex = finalUvIndex,
                        weatherWindDirection = savedWindDirection,
                        weatherWindGusts = finalWindGusts,
                        weatherRainProbability = savedRainProbability,
                        weatherHadRain = if (weatherHadRain) true else null,
                        weatherRainStartMinute = weatherRainStartMinute,
                        // 🔥 Si detectamos calzada mojada durante la ruta, asegurar que haya precipitación para el badge
                        // Usar 0.2 mm (dentro del rango de calzada mojada, por debajo del umbral de lluvia activa de 0.4 mm)
                        weatherMaxPrecipitation = when {
                            weatherMaxPrecipitation != null && weatherMaxPrecipitation > 0.0 -> weatherMaxPrecipitation
                            weatherHadWetRoad && !weatherHadRain -> 0.2 // Forzar precipitación si hubo calzada mojada
                            else -> null
                        },
                        weatherRainReason = weatherRainReason,
                        weatherHadExtremeConditions = if (hadExtremeConditionsDuringRoute) true else null,
                        weatherExtremeReason = if (hadExtremeConditionsDuringRoute) weatherExtremeReason else null
                    )
                } else {
                    Log.w(TAG, "⚠️ No se capturó clima válido al inicio, guardando ruta SIN clima (temp=$savedWeatherTemp, emoji=$savedWeatherEmoji)")
                    // Asegurar explícitamente que los campos de clima sean null
                    baseRoute.copy(
                        weatherTemperature = null,
                        weatherEmoji = null,
                        weatherDescription = null,
                        weatherIsDay = true,
                        weatherFeelsLike = null,
                        weatherHumidity = null,
                        weatherWindSpeed = null,
                        weatherUvIndex = null,
                        weatherWindDirection = null,
                        weatherWindGusts = null,
                        weatherRainProbability = null,
                        weatherHadRain = null,
                        weatherRainStartMinute = null,
                        weatherMaxPrecipitation = null,
                        weatherRainReason = null,
                        weatherHadExtremeConditions = null
                    )
                }
                
                // Guardar en Firebase
                val result = routeRepository.saveRoute(route)
                
                if (result.isSuccess) {
                    result.getOrNull() ?: ""
                    
                    // NO intentar obtener clima en segundo plano - puede guardar el clima ACTUAL en lugar del del momento
                    // Si no se capturó al inicio, la ruta se guarda sin clima (correcto)
                    if (!hasValidWeather) {
                        Log.d(TAG, "📝 Ruta guardada sin clima. No se intentará obtener clima actual (evitar guardar clima incorrecto o genérico)")
                    }
                    
                // Limpiar datos de clima después de usar
                _startWeatherTemperature = null
                // Limpiar variables de estado más adverso
                weatherHadRain = false
                weatherHadWetRoad = false
                weatherHadExtremeConditions = false
                weatherExtremeReason = null
                weatherMaxPrecipitation = 0.0
                maxWindSpeed = 0.0
                maxWindGusts = 0.0
                minTemperature = Double.MAX_VALUE
                maxTemperature = Double.MIN_VALUE
                maxUvIndex = 0.0
                _startWeatherEmoji = null
                _startWeatherDescription = null
                _startWeatherIsDay = null
                _startWeatherFeelsLike = null
                _startWeatherHumidity = null
                _startWeatherWindSpeed = null
                _startWeatherUvIndex = null
                _startWeatherWindDirection = null
                _startWeatherWindGusts = null
                _startWeatherRainProbability = null
                _weatherStatus.value = WeatherStatus.Idle
                
                // Limpiar variables de detección de lluvia y estado más adverso
                weatherHadRain = false
                weatherRainStartMinute = null
                weatherMaxPrecipitation = 0.0
                weatherRainReason = null
                weatherHadWetRoad = false
                weatherHadExtremeConditions = false
                weatherExtremeReason = null
                maxWindSpeed = 0.0
                maxWindGusts = 0.0
                minTemperature = Double.MAX_VALUE
                maxTemperature = Double.MIN_VALUE
                maxUvIndex = 0.0
                
                // Limpiar estado pendiente explícitamente
                pendingRainConfirmation = false
                pendingRainMinute = null
                pendingRainReason = null
                
                // Limpiar snapshot inicial
                _initialWeatherSnapshot = null
                _initialWeatherCaptured = false
                _shouldShowRainWarning.value = false

                routeRepository.clearTempWeather()
                    
                    var message = "Ruta guardada exitosamente: ${LocationUtils.formatNumberSpanish(route.totalDistance.roundToOneDecimal())} km"
                    
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
                                message += "\nDistancia añadida a registros: ${LocationUtils.formatNumberSpanish(route.totalDistance.roundToOneDecimal())} km"
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
                    // NO cambiar a Idle aquí - mantener en Saving hasta que se oculte el overlay
                    // El flag isClosing en TrackingScreen maneja el renderizado
                    // _trackingState.value = TrackingState.Idle  // ❌ ELIMINADO - causa el flash
                    
                    // Mínimo tiempo de UX antes de ocultar overlay
                    kotlinx.coroutines.delay(600)
                    
                    // Ocultar overlay después de guardar
                    appOverlayRepository.hideOverlay()
                    
                    // Solo cambiar a Idle después de ocultar el overlay (aunque ya no importa porque isClosing bloquea el renderizado)
                    // Pero lo hacemos por limpieza del estado
                    _trackingState.value = TrackingState.Idle
                } else {
                    throw result.exceptionOrNull() ?: Exception("Error al guardar la ruta")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al finalizar ruta", e)
                _trackingState.value = TrackingState.Error(e.message ?: "Error al guardar la ruta")
                _message.value = "Error: ${e.message}"
                
                // Ocultar overlay en caso de error también
                appOverlayRepository.hideOverlay()
            }
        }
    }

    /**
     * Cancela el seguimiento sin guardar
     */
    fun cancelTracking() {
        // Cancelar job de clima si está activo
        weatherJob?.cancel()
        continuousWeatherJob?.cancel()
        
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
        _startWeatherIsDay = null
        _startWeatherFeelsLike = null
        _startWeatherHumidity = null
        _startWeatherWindSpeed = null
        _startWeatherUvIndex = null
        _startWeatherWindDirection = null
        _startWeatherWindGusts = null
        _startWeatherRainProbability = null
        
        // Limpiar variables de detección de lluvia
        weatherHadRain = false
        weatherRainStartMinute = null
        weatherMaxPrecipitation = 0.0
        weatherRainReason = null
        weatherHadWetRoad = false
        weatherHadExtremeConditions = false
        maxWindSpeed = 0.0
        maxWindGusts = 0.0
        minTemperature = Double.MAX_VALUE
        maxTemperature = Double.MIN_VALUE
        maxUvIndex = 0.0
        pendingRainConfirmation = false
        pendingRainMinute = null
        pendingRainReason = null

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
        // Cancelar jobs de clima
        weatherJob?.cancel()
        continuousWeatherJob?.cancel()
        
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
    
    /**
     * Formatea la temperatura asegurándose de que 0 se muestre sin signo menos
     */
    /**
     * Formatea la temperatura y evita el "-0" o "-0.0"
     */
    private fun formatTemperature(temperature: Double, decimals: Int = 1): String {
        // 1. Obtenemos el valor absoluto para formatear el número "limpio"
        val absTemp = kotlin.math.abs(temperature)
        
        // 2. Usamos tu utilidad para formatear (ej: "0,0" o "17,5")
        val formatted = LocationUtils.formatNumberSpanish(absTemp, decimals)

        // 3. TRUCO DE MAGIA 🪄
        // Comprobamos si el número que vamos a mostrar es realmente un cero.
        // Reemplazamos la coma por punto para asegurar que toDouble() funcione.
        val isEffectiveZero = try {
            formatted.replace(",", ".").toDouble() == 0.0
        } catch (e: Exception) {
            false
        }

        // 4. Lógica de signo:
        // Solo ponemos el "-" si la temperatura original es negativa Y NO es un cero efectivo.
        return if (temperature < 0 && !isEffectiveZero) {
            "-$formatted"
        } else {
            formatted
        }
    }
}

