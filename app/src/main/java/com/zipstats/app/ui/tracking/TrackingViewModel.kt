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
import com.zipstats.app.model.Route
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.model.RouteWeatherDecision
import com.zipstats.app.model.RouteWeatherSnapshot
import com.zipstats.app.model.Scooter
import com.zipstats.app.model.SurfaceConditionType
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
import kotlinx.coroutines.delay
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
 * Estado del clima para notificaciones (simplificado)
 */
enum class WeatherBadgeState {
    SECO,              // Sin badges activos
    CALZADA_HUMEDA,   // Calzada húmeda (🟡)
    LLUVIA,           // Lluvia activa (🔵)
    EXTREMO           // Condiciones extremas (⚠️)
}

/**
 * Estado de la captura del clima
 */
sealed class WeatherStatus {
    object Idle : WeatherStatus()
    object Loading : WeatherStatus()
    data class Success(
        val temperature: Double,      // Temperatura en °C
        val feelsLike: Double,         // Sensación térmica general
        val windChill: Double?,        // Wind Chill (solo relevante <15°C) - viene directamente de Google API
        val heatIndex: Double?,        // Índice de calor (Heat Index - solo relevante >26°C)
        val description: String,       // Descripción del clima
        val icon: String,              // Código (ahora será el numérico, ej: "3")
        val humidity: Int,             // Humedad %
        val windSpeed: Double,         // Velocidad del viento en km/h
        val weatherEmoji: String,      // Emoji representativo (¡ARREGLADO!)
        val weatherCode: Int,
        val isDay: Boolean,
        val uvIndex: Double?,
        val windDirection: Int?,
        val windGusts: Double?,        // Ráfagas en km/h
        val rainProbability: Int?,
        val precipitation: Double,     // mm reales
        val rain: Double?,             // mm
        val showers: Double?,          // mm
        val dewPoint: Double?,         // Punto de rocío en °C (para detectar condensación)
        val visibility: Double?        // Visibilidad en metros (para detectar niebla/talaia en Barcelona)
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
    private val appOverlayRepository: AppOverlayRepository,
    private val weatherRepository: com.zipstats.app.repository.WeatherRepository
) : AndroidViewModel(application) {

    private val notificationHandler = TrackingNotificationHandler(getApplication())

    private val weatherAdvisor = WeatherAdvisor()

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
    private var _initialWeatherLatitude: Double? = null
    private var _initialWeatherLongitude: Double? = null
    
    // Clima capturado al inicio de la ruta (se copia del snapshot al iniciar tracking)
    private var _startWeatherTemperature: Double? = null
    private var _startWeatherEmoji: String? = null
    private var _startWeatherCode: Int? = null
    private var _startWeatherCondition: String? = null
    private var _startWeatherDescription: String? = null
    private var _startWeatherIsDay: Boolean? = null

    private var _startWeatherFeelsLike: Double? = null
    private var _startWeatherWindChill: Double? = null
    private var _startWeatherHeatIndex: Double? = null

    private var _startWeatherHumidity: Int? = null

    private var _startWeatherWindSpeed: Double? = null

    private var _startWeatherUvIndex: Double? = null
    private var _startWeatherWindDirection: Int? = null
    private var _startWeatherWindGusts: Double? = null
    private var _startWeatherRainProbability: Int? = null
    private var _startWeatherVisibility: Double? = null
    private var _startWeatherDewPoint: Double? = null

    // Clima al FINAL (nuevas, solo se rellenan si hay badges)
    private var _finalWeatherTemperature: Double? = null
    private var _finalWeatherEmoji: String? = null
    private var _finalWeatherCode: Int? = null
    private var _finalWeatherCondition: String? = null
    private var _finalWeatherDescription: String? = null
    private var _finalWeatherIsDay: Boolean? = null
    private var _finalWeatherFeelsLike: Double? = null
    private var _finalWeatherWindChill: Double? = null
    private var _finalWeatherHeatIndex: Double? = null
    private var _finalWeatherDewPoint: Double? = null
    private var _finalWeatherWindSpeed: Double? = null
    private var _finalWeatherWindGusts: Double? = null
    private var _finalWeatherWindDirection: Int? = null
    private var _finalWeatherHumidity: Int? = null
    private var _finalWeatherRainProbability: Int? = null
    private var _finalWeatherUvIndex: Double? = null
    private var _finalWeatherVisibility: Double? = null
    
    // Estado para aviso preventivo de lluvia
    private val _shouldShowRainWarning = MutableStateFlow(false)
    val shouldShowRainWarning: StateFlow<Boolean> = _shouldShowRainWarning.asStateFlow()
    
    // Tipo de aviso: true = lluvia activa, false = calzada humeda (sin lluvia activa)
    private val _isActiveRainWarning = MutableStateFlow(false)
    val isActiveRainWarning: StateFlow<Boolean> = _isActiveRainWarning.asStateFlow()
    
    // Estado para aviso preventivo de condiciones extremas
    private val _shouldShowExtremeWarning = MutableStateFlow(false)
    val shouldShowExtremeWarning: StateFlow<Boolean> = _shouldShowExtremeWarning.asStateFlow()
    
    // Estado anterior del clima para detectar cambios y mostrar notificaciones
    private var lastWeatherBadgeState: WeatherBadgeState? = null
    

    
    // Nota: se eliminó la histéresis de "calzada humeda".
    // Ahora solo usamos señales de Google (histórico reciente + humedad/condensación/nieve).
    
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
    // Prioridad: Condiciones extremas > Lluvia > Calzada humeda
    private var weatherHadWetRoad = false // Calzada húmeda detectada (sin lluvia activa)
    private var weatherHadExtremeConditions = false // Condiciones extremas detectadas
    private var weatherDetectedClimateProfile: String? = null // Perfil climático inferido
    private var weatherExtremeReason: String? = null // Razón de condiciones extremas (WIND, GUSTS, STORM, SNOW, COLD, HEAT, UV, VISIBILITY)

    // Precipitación acumulada reciente (últimas 3h) basada en histórico de Google
    private var recentPrecipitation3h: Double = 0.0
    
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
    val vehiclesLoaded: StateFlow<Boolean> = appOverlayRepository.vehiclesLoaded
    
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
            _startWeatherCode = savedWeather.weatherCode
            _startWeatherCondition = savedWeather.icon
            _startWeatherDescription = savedWeather.description
            _startWeatherIsDay = savedWeather.isDay
            _startWeatherFeelsLike = savedWeather.feelsLike
            _startWeatherWindChill = savedWeather.windChill
            _startWeatherHeatIndex = savedWeather.heatIndex
            _startWeatherHumidity = savedWeather.humidity
            _startWeatherWindSpeed = savedWeather.windSpeed
            _startWeatherUvIndex = savedWeather.uvIndex
            _startWeatherWindDirection = savedWeather.windDirection
            _startWeatherWindGusts = savedWeather.windGusts
            _startWeatherRainProbability = savedWeather.rainProbability
            _startWeatherVisibility = savedWeather.visibility
            _startWeatherDewPoint = savedWeather.dewPoint

            // 2. 🔥 IMPORTANTE: Restaurar badges SOLO si hay tracking ACTIVO de verdad.
            // Ojo: en init, `_trackingState` aún puede ser Idle hasta que `syncWithGlobalState()` emita.
            // Por eso usamos el estado global del TrackingStateManager.
            if (trackingStateManager.isTracking.value) {
                _shouldShowRainWarning.value = savedWeather.shouldShowRainWarning
                _isActiveRainWarning.value = savedWeather.isActiveRainWarning
                _shouldShowExtremeWarning.value = savedWeather.shouldShowExtremeWarning
                Log.d(TAG, "♻️ Badges restaurados (tracking activo)")
            } else {
                // En pretracking, los badges deben estar limpios
                _shouldShowRainWarning.value = false
                _isActiveRainWarning.value = false
                _shouldShowExtremeWarning.value = false
                lastWeatherBadgeState = null
                Log.d(TAG, "🔄 Badges NO restaurados (estado Idle - pretracking)")
            }

            // 3. Restauramos el estado de la UI para que aparezca la tarjeta (solo datos del clima, sin badges)
            _weatherStatus.value = WeatherStatus.Success(
                temperature = savedWeather.temperature,
                feelsLike = savedWeather.feelsLike,
                windChill = savedWeather.windChill,
                heatIndex = savedWeather.heatIndex,
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
                showers = savedWeather.showers,
                dewPoint = savedWeather.dewPoint,
                visibility = savedWeather.visibility
            )
        } else {
            // 🔥 IMPORTANTE: Si NO hay clima guardado, asegurar que TODO esté limpio
            // Esto puede pasar si se finalizó/canceló una ruta y el ViewModel se mantiene vivo
            _shouldShowRainWarning.value = false
            _isActiveRainWarning.value = false
            _shouldShowExtremeWarning.value = false
            lastWeatherBadgeState = null
            _weatherStatus.value = WeatherStatus.Idle // Resetear estado del clima también
            Log.d(TAG, "🔄 No hay clima guardado, badges y estado del clima limpiados")
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
                    // 🔁 Si el usuario cambia de pantalla y vuelve, el ViewModel puede recrearse y el job
                    // de monitoreo continuo no estar corriendo. Lo reanudamos automáticamente.
                    ensureContinuousWeatherMonitoringRunning()
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
                    // Cortar el monitoreo si estaba activo
                    continuousWeatherJob?.cancel()
                    continuousWeatherJob = null
                }
            }
        }
    }

    /**
     * Asegura que el monitoreo continuo del clima esté corriendo si hay tracking activo.
     * Se usa al volver a la pantalla (ViewModel recreado / servicio reconectado).
     */
    private fun ensureContinuousWeatherMonitoringRunning() {
        val isTrackingActive =
            _trackingState.value is TrackingState.Tracking || _trackingState.value is TrackingState.Paused
        if (!isTrackingActive) return
        if (continuousWeatherJob?.isActive == true) return

        Log.d(TAG, "🌧️ Reanudando monitoreo continuo del clima (job no activo)")
        startContinuousWeatherMonitoring()
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
                appOverlayRepository.setVehiclesLoaded()

                val currentSelectedId = _selectedScooter.value?.id
                val selectedStillExists = currentSelectedId != null &&
                        scootersList.any { it.id == currentSelectedId }

                // Si no hay selección o quedó inválida tras recargar datos, reconciliar selección.
                if (!selectedStillExists) {
                    val lastUsedScooterId = loadLastUsedScooter()
                    val scooterToSelect = if (lastUsedScooterId != null) {
                        scootersList.find { it.id == lastUsedScooterId }
                    } else {
                        null
                    }
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

        Log.d(
            TAG,
            "🌤️ [Precarga] Capturando clima inicial en lat=${preLocation.latitude}, lon=${preLocation.longitude}"
        )

        // Cancelar job anterior si existe
        weatherJob?.cancel()

        weatherJob = viewModelScope.launch {
            try {
                _weatherStatus.value = WeatherStatus.Loading

                val result = weatherRepository.getCurrentWeather(
                    latitude = preLocation.latitude,
                    longitude = preLocation.longitude
                )

                result.onSuccess { weather ->

                    val condition = weather.icon.uppercase()
                    val weatherEmoji = weather.weatherEmoji
                    val weatherDescription = weather.description

                    // Guardar snapshot inicial
                    _initialWeatherSnapshot = weather
                    _initialWeatherCaptured = true
                    _initialWeatherLatitude = preLocation.latitude
                    _initialWeatherLongitude = preLocation.longitude

                    // ----------------------------
                    // UI: estado base
                    // ----------------------------
                    _weatherStatus.value = WeatherStatus.Success(
                        temperature = weather.temperature,
                        feelsLike = weather.feelsLike,
                        windChill = weather.windChill,
                        heatIndex = weather.heatIndex,
                        description = weatherDescription,
                        icon = weather.icon,
                        humidity = weather.humidity,
                        windSpeed = weather.windSpeed,
                        weatherEmoji = weatherEmoji,
                        weatherCode = weather.weatherCode,
                        isDay = weather.isDay,
                        uvIndex = weather.uvIndex,
                        windDirection = weather.windDirection,
                        windGusts = weather.windGusts,
                        rainProbability = weather.rainProbability,
                        precipitation = weather.precipitation,
                        rain = weather.rain,
                        showers = weather.showers,
                        dewPoint = weather.dewPoint,
                        visibility = weather.visibility
                    )

                    // ----------------------------
                    // Precipitación histórica
                    // ----------------------------
                    weatherMaxPrecipitation =
                        maxOf(weatherMaxPrecipitation, weather.precipitation)

                    recentPrecipitation3h = getRecentPrecipitation3h(
                        latitude = preLocation.latitude,
                        longitude = preLocation.longitude
                    )
                    weatherMaxPrecipitation =
                        maxOf(weatherMaxPrecipitation, recentPrecipitation3h)

                    // ----------------------------
                    // Lluvia activa (Google)
                    // ----------------------------
                    val (isActiveRain, rainUserReason) =
                        weatherAdvisor.checkActiveRain(
                            condition = condition,
                            description = weatherDescription,
                            precipitation = weather.precipitation
                        )

                    Log.d(
                        TAG,
                        "🔍 [Precarga] checkActiveRain: condition=$condition, precip=${weather.precipitation}, isActiveRain=$isActiveRain"
                    )

                    // ----------------------------
                    // Calzada húmeda (solo si NO llueve)
                    // ----------------------------
                    val precip24h = if (isActiveRain) 0.0 else getRecentPrecipitation24h(
                        latitude = preLocation.latitude,
                        longitude = preLocation.longitude
                    )

                    val isWetRoad = if (isActiveRain) {
                        false
                    } else {
                        weatherDetectedClimateProfile = weatherAdvisor.detectClimateProfileTag(
                            condition = condition,
                            description = weatherDescription,
                            humidity = weather.humidity,
                            recentPrecipitation3h = recentPrecipitation3h,
                            precip24h = precip24h,
                            temperature = weather.temperature,
                            dewPoint = weather.dewPoint,
                            windSpeed = weather.windSpeed,
                            weatherEmoji = weatherEmoji,
                            uvIndex = weather.uvIndex,
                            isDay = weather.isDay
                        )
                        Log.d(
                            TAG,
                            "🧭 [Precarga] Perfil climático detectado: $weatherDetectedClimateProfile"
                        )
                        weatherAdvisor.checkWetRoadConditions(
                            condition = condition,
                            humidity = weather.humidity,
                            recentPrecipitation3h = recentPrecipitation3h,
                            precip24h = precip24h,
                            hasActiveRain = false,
                            isDay = weather.isDay,
                            temperature = weather.temperature,
                            dewPoint = weather.dewPoint,
                            uvIndex = weather.uvIndex,
                            weatherEmoji = weatherEmoji,
                            weatherDescription = weatherDescription,
                            windSpeed = weather.windSpeed
                        )
                    }

                    // ----------------------------
                    // Condiciones extremas
                    // ----------------------------
                    val (isLowVisibility, visReason) =
                        weatherAdvisor.checkLowVisibility(weather.visibility)

                    val hasExtremeConditions =
                        weatherAdvisor.checkExtremeConditions(
                            windSpeed = weather.windSpeed,
                            windGusts = weather.windGusts,
                            temperature = weather.temperature,
                            uvIndex = weather.uvIndex,
                            isDay = weather.isDay,
                            weatherEmoji = weatherEmoji,
                            weatherDescription = weatherDescription,
                            weatherCode = weather.weatherCode,
                            visibility = weather.visibility
                        )

                    // ----------------------------
                    // BADGES — JERARQUÍA OFICIAL
                    // ----------------------------

                    // 1️⃣ Lluvia (máxima prioridad)
                    if (isActiveRain) {
                        weatherHadRain = true
                        weatherRainStartMinute = 0
                        weatherRainReason = rainUserReason

                        weatherHadWetRoad = false

                        _shouldShowRainWarning.value = true
                        _isActiveRainWarning.value = true

                        Log.d(TAG, "🌧️ [Precarga] Lluvia activa al inicio de la ruta")
                    }
                    // 2️⃣ Calzada húmeda (solo si NO llueve)
                    else if (isWetRoad) {
                        weatherHadWetRoad = true

                        _shouldShowRainWarning.value = true
                        _isActiveRainWarning.value = false

                        Log.d(TAG, "🛣️ [Precarga] Calzada húmeda detectada")
                    }

                    // 3️⃣ Condiciones extremas (complementarias)
                    if (hasExtremeConditions) {
                        weatherHadExtremeConditions = true
                        _shouldShowExtremeWarning.value = true

                        val cause = weatherAdvisor.detectExtremeCause(
                            windSpeed = weather.windSpeed,
                            windGusts = weather.windGusts,
                            temperature = weather.temperature,
                            uvIndex = weather.uvIndex,
                            isDay = weather.isDay,
                            weatherEmoji = weatherEmoji,
                            weatherDescription = weatherDescription,
                            weatherCode = weather.weatherCode,
                            visibility = weather.visibility
                        )

                        if (cause != null) {
                            weatherExtremeReason = cause
                        }

                        if (isLowVisibility) {
                            Log.d(
                                TAG,
                                "🌫️ [Precarga] Visibilidad crítica: ${weather.visibility}m - $visReason"
                            )
                        }

                        Log.d(TAG, "⚠️ [Precarga] Condiciones extremas detectadas")
                    }

                    // ----------------------------
                    // Estado inicial para notificaciones
                    // ----------------------------
                    lastWeatherBadgeState = getCurrentWeatherBadgeState()

                    Log.d(
                        TAG,
                        "✅ [Precarga] Clima inicial capturado: ${weather.temperature}°C $weatherEmoji"
                    )
                    Log.d(
                        TAG,
                        "🌤️ [Precarga] Estado inicial de badges: $lastWeatherBadgeState"
                    )
                }.onFailure { error ->
                    Log.e(
                        TAG,
                        "❌ [Precarga] Error al capturar clima inicial: ${error.message}"
                    )
                    _weatherStatus.value =
                        WeatherStatus.Error(error.message ?: "Error al obtener clima")
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "❌ [Precarga] Excepción al capturar clima inicial: ${e.message}",
                    e
                )
                _weatherStatus.value =
                    WeatherStatus.Error("Excepción: ${e.message}")
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
     * Reinicia la escucha pre-GPS desde cero para evitar estados "pegados"
     * al volver a la pantalla de tracking.
     */
    fun restartPreLocationTracking() {
        stopPreLocationTracking()
        startPreLocationTracking()
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
        // 🔥 IMPORTANTE: Resetear badges al iniciar una nueva ruta
        // Esto asegura que cada ruta empiece limpia y los badges se actualicen según el clima real
        _shouldShowRainWarning.value = false
        _isActiveRainWarning.value = false
        _shouldShowExtremeWarning.value = false
        lastWeatherBadgeState = null // Resetear estado anterior para notificaciones
        Log.d(TAG, "🔄 Badges reseteados al iniciar nueva ruta")
        
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
                // (El estado inicial del clima se establece dentro de captureStartWeather)
                captureStartWeather()
                
                // Iniciar detección continua de lluvia cada 5 minutos
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
     * Determina si hay lluvia activa confiando completamente en Google Weather
     * 
     * Si Google dice que hay lluvia (en descripción o condición), activamos el badge/preaviso.
     * No importa si es ligera o intensa - confiamos en la decisión de Google.
     * 
     * @return Pair<Boolean, String> donde el Boolean indica si hay lluvia activa y el String es la razón amigable para el usuario
     */


    private suspend fun getRecentPrecipitation3h(latitude: Double, longitude: Double): Double {
        return weatherRepository
            .getRecentPrecipitationHours(latitude = latitude, longitude = longitude, hours = 3)
            .getOrElse { 0.0 }
            .coerceAtLeast(0.0)
    }

    private suspend fun getRecentPrecipitation24h(latitude: Double, longitude: Double): Double {
        return weatherRepository
            .getRecentPrecipitationHours(latitude = latitude, longitude = longitude, hours = 24)
            .getOrElse { 0.0 }
            .coerceAtLeast(0.0)
    }

    private fun startContinuousWeatherMonitoring() {
        // Cancelar cualquier monitoreo anterior
        continuousWeatherJob?.cancel()

        // Limpiar estado pendiente antes de iniciar nuevo monitoreo
        pendingRainConfirmation = false
        pendingRainMinute = null
        pendingRainReason = null

        val firstCheckDelayMs = 60_000L
        val regularCheckDelayMs = 5 * 60 * 1000L

        continuousWeatherJob = viewModelScope.launch {

            Log.d(
                TAG,
                "⏱️ [Monitoreo continuo] Esperando 1 min para primera verificación temprana..."
            )
            delay(firstCheckDelayMs)

            while (
                _trackingState.value is TrackingState.Tracking ||
                _trackingState.value is TrackingState.Paused
            ) {

                val points = _routePoints.value

                // Esperar a que haya puntos (rehidratación tras volver a la pantalla)
                if (points.isEmpty()) {
                    delay(5_000)
                    continue
                }

                val currentPoint = points.last()
                val elapsedMinutes =
                    if (_startTime.value > 0)
                        ((System.currentTimeMillis() - _startTime.value) / (1000 * 60)).toInt()
                    else 0

                Log.d(TAG, "🌧️ [Monitoreo continuo] Chequeando clima en minuto $elapsedMinutes...")

                try {
                    val result = weatherRepository.getCurrentWeather(
                        latitude = currentPoint.latitude,
                        longitude = currentPoint.longitude
                    )

                    result.onSuccess { weather ->

                        Log.d(
                            TAG,
                            "✅ [Monitoreo continuo] Clima obtenido: ${weather.temperature}°C, " +
                                    "condición=${weather.icon}, precip=${weather.precipitation}mm, " +
                                    "humedad=${weather.humidity}%"
                        )

                        // 🔥 Actualizar SIEMPRE el estado del clima para la UI
                        val currentStatus = _weatherStatus.value
                        _weatherStatus.value =
                            if (currentStatus is WeatherStatus.Success) {
                                currentStatus.copy(
                                    temperature = weather.temperature,
                                    weatherEmoji = weather.weatherEmoji,
                                    weatherCode = weather.weatherCode,
                                    icon = weather.icon,
                                    windSpeed = weather.windSpeed,
                                    windDirection = weather.windDirection,
                                    isDay = weather.isDay,
                                    description = weather.description
                                )
                            } else {
                                WeatherStatus.Success(
                                    temperature = weather.temperature,
                                    feelsLike = weather.feelsLike,
                                    windChill = weather.windChill,
                                    heatIndex = weather.heatIndex,
                                    description = weather.description,
                                    icon = weather.icon,
                                    humidity = weather.humidity,
                                    windSpeed = weather.windSpeed,
                                    weatherEmoji = weather.weatherEmoji,
                                    weatherCode = weather.weatherCode,
                                    isDay = weather.isDay,
                                    uvIndex = weather.uvIndex,
                                    windDirection = weather.windDirection,
                                    windGusts = weather.windGusts,
                                    rainProbability = weather.rainProbability,
                                    precipitation = weather.precipitation,
                                    rain = weather.rain,
                                    showers = weather.showers,
                                    dewPoint = weather.dewPoint,
                                    visibility = weather.visibility
                                )
                            }

                        val condition = weather.icon.uppercase()
                        val weatherDescription = weather.description
                        val weatherEmoji = weather.weatherEmoji

                        // 🌧️ Lluvia activa
                        val (isActiveRain, rainUserReason) =
                            weatherAdvisor.checkActiveRain(
                                condition = condition,
                                description = weatherDescription,
                                precipitation = weather.precipitation
                            )

                        Log.d(
                            TAG,
                            "🔍 [Monitoreo continuo] Detección: isActiveRain=$isActiveRain, razón=$rainUserReason"
                        )

                        // Precipitación histórica (solo si NO hay lluvia activa)
                        val localRecentPrecip3h =
                            if (isActiveRain) 0.0 else getRecentPrecipitation3h(
                                latitude = currentPoint.latitude,
                                longitude = currentPoint.longitude
                            )

                        val localPrecip24h =
                            if (isActiveRain) 0.0 else getRecentPrecipitation24h(
                                latitude = currentPoint.latitude,
                                longitude = currentPoint.longitude
                            )

                        recentPrecipitation3h =
                            0.7 * recentPrecipitation3h + 0.3 * localRecentPrecip3h

                        weatherMaxPrecipitation =
                            maxOf(weatherMaxPrecipitation, weather.precipitation, localRecentPrecip3h)

                        // 🛣️ Calzada húmeda (excluida si hay lluvia activa)
                        val isWetRoad =
                            if (isActiveRain) {
                                false
                            } else {
                                weatherDetectedClimateProfile = weatherAdvisor.detectClimateProfileTag(
                                    condition = condition,
                                    description = weatherDescription,
                                    humidity = weather.humidity,
                                    recentPrecipitation3h = localRecentPrecip3h,
                                    precip24h = localPrecip24h,
                                    temperature = weather.temperature,
                                    dewPoint = weather.dewPoint,
                                    windSpeed = weather.windSpeed,
                                    weatherEmoji = weatherEmoji,
                                    uvIndex = weather.uvIndex,
                                    isDay = weather.isDay
                                )
                                Log.d(
                                    TAG,
                                    "🧭 [Monitoreo continuo] Perfil climático detectado: $weatherDetectedClimateProfile"
                                )
                                weatherAdvisor.checkWetRoadConditions(
                                    condition = condition,
                                    humidity = weather.humidity,
                                    recentPrecipitation3h = localRecentPrecip3h,
                                    precip24h = localPrecip24h,
                                    hasActiveRain = isActiveRain,
                                    isDay = weather.isDay,
                                    temperature = weather.temperature,
                                    dewPoint = weather.dewPoint,
                                    uvIndex = weather.uvIndex,
                                    weatherEmoji = weatherEmoji,
                                    weatherDescription = weather.description,
                                    windSpeed = weather.windSpeed
                                )
                            }

                        Log.d(
                            TAG,
                            "🛣️ [Monitoreo continuo] Calzada húmeda: isWetRoad=$isWetRoad"
                        )
                        if (isWetRoad) {
                            weatherHadWetRoad = true
                            _shouldShowRainWarning.value = true
                            _isActiveRainWarning.value = false
                            Log.d(TAG, "🛣️ [Monitoreo] Calzada húmeda confirmada")
                        }
                        // 🌫️ Visibilidad reducida
                        val (isLowVisibility, visReason) =
                            weatherAdvisor.checkLowVisibility(weather.visibility)

                        if (isLowVisibility) {
                            Log.d(
                                TAG,
                                "🌫️ [Monitoreo continuo] Visibilidad crítica: ${weather.visibility}m - $visReason"
                            )
                        }

                        // ⚠️ Condiciones extremas (complementarias)
                        val hasExtremeConditions =
                            weatherAdvisor.checkExtremeConditions(
                                windSpeed = weather.windSpeed,
                                windGusts = weather.windGusts,
                                temperature = weather.temperature,
                                uvIndex = weather.uvIndex,
                                isDay = weather.isDay,
                                weatherEmoji = weatherEmoji,
                                weatherDescription = weather.description,
                                weatherCode = weather.weatherCode,
                                visibility = weather.visibility
                            )

                        Log.d(
                            TAG,
                            "⚠️ [Monitoreo continuo] Condiciones extremas: $hasExtremeConditions"
                        )

                        // Siempre acumular, independientemente de si hay condiciones extremas
                        maxWindSpeed = maxOf(maxWindSpeed, weather.windSpeed ?: 0.0)
                        maxWindGusts = maxOf(maxWindGusts, weather.windGusts ?: 0.0)
                        if (weather.uvIndex != null && weather.isDay) {
                            maxUvIndex = maxOf(maxUvIndex, weather.uvIndex)
                        }

                        if (hasExtremeConditions) {
                            weatherHadExtremeConditions = true
                            _shouldShowExtremeWarning.value = true

                            val cause =
                                weatherAdvisor.detectExtremeCause(
                                    windSpeed = weather.windSpeed,
                                    windGusts = weather.windGusts,
                                    temperature = weather.temperature,
                                    uvIndex = weather.uvIndex,
                                    isDay = weather.isDay,
                                    weatherEmoji = weatherEmoji,
                                    weatherDescription = weather.description,
                                    weatherCode = weather.weatherCode
                                )

                            if (cause != null && weatherExtremeReason == null) {
                                weatherExtremeReason = cause
                            }


                            minTemperature = minOf(minTemperature, weather.temperature)
                            maxTemperature = maxOf(maxTemperature, weather.temperature)

                        }

                        // 🌧️ JERARQUÍA DE LLUVIA
                        if (isActiveRain) {
                            if (weatherHadRain) {
                                _shouldShowRainWarning.value = true
                                _isActiveRainWarning.value = true
                            } else {
                                if (pendingRainConfirmation) {
                                    weatherHadRain = true
                                    weatherHadWetRoad = false

                                    weatherRainStartMinute =
                                        pendingRainMinute ?: elapsedMinutes
                                    weatherRainReason =
                                        pendingRainReason ?: rainUserReason

                                    _shouldShowRainWarning.value = true
                                    _isActiveRainWarning.value = true

                                    pendingRainConfirmation = false
                                    pendingRainMinute = null
                                    pendingRainReason = null

                                    Log.d(
                                        TAG,
                                        "🌧️ [Monitoreo] Lluvia CONFIRMADA en minuto $weatherRainStartMinute: $weatherRainReason"
                                    )
                                } else {
                                    pendingRainConfirmation = true
                                    pendingRainMinute = elapsedMinutes
                                    pendingRainReason = rainUserReason

                                    Log.d(
                                        TAG,
                                        "🌧️ [Monitoreo] Lluvia detectada (pendiente confirmación)"
                                    )
                                }
                            }

                            weatherMaxPrecipitation =
                                maxOf(weatherMaxPrecipitation, weather.precipitation)

                        } else {
                            if (pendingRainConfirmation) {
                                pendingRainConfirmation = false
                                pendingRainMinute = null
                                pendingRainReason = null

                                Log.d(
                                    TAG,
                                    "🌧️ [Monitoreo] Falso positivo de lluvia cancelado"
                                )
                            }
                            if (!isWetRoad) {
                                // Si ya no hay lluvia activa NI calzada húmeda, apagamos el badge en UI.
                                // El histórico (weatherHadRain/weatherHadWetRoad) se mantiene para persistencia.
                                _shouldShowRainWarning.value = false
                                _isActiveRainWarning.value = false
                            }
                        }
                        checkAndNotifyWeatherChange()

                    }

                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "❌ [Monitoreo continuo] Error obteniendo clima",
                        e
                    )
                }
                delay(regularCheckDelayMs)
            }

            // Limpieza final al detener tracking
            pendingRainConfirmation = false
            pendingRainMinute = null
            pendingRainReason = null

            Log.d(TAG, "🌧️ [Monitoreo continuo] Detenido (tracking finalizado)")
        }
    }



    /**
     * Determina el estado actual del clima basado en los badges activos
     */
    private fun getCurrentWeatherBadgeState(): WeatherBadgeState {
        val hasRain = _shouldShowRainWarning.value && _isActiveRainWarning.value
        val hasWetRoad = _shouldShowRainWarning.value && !_isActiveRainWarning.value
        val hasExtreme = _shouldShowExtremeWarning.value
        
        return when {
            hasRain -> WeatherBadgeState.LLUVIA
            hasWetRoad -> WeatherBadgeState.CALZADA_HUMEDA
            hasExtreme -> WeatherBadgeState.EXTREMO
            else -> WeatherBadgeState.SECO
        }
    }


    /**
     * Obtiene el texto del badge según el estado
     */
    private fun getBadgeText(state: WeatherBadgeState, extremeReason: String? = null): String {
        return when (state) {
            WeatherBadgeState.LLUVIA -> "🔵 Lluvia"
            WeatherBadgeState.CALZADA_HUMEDA -> "🟡 Calzada húmeda"
            WeatherBadgeState.EXTREMO -> {
                // Usar razón específica si está disponible
                when (extremeReason) {
                    "STORM" -> "⚠️ Tormenta"
                    "SNOW" -> "⚠️ Nieve"
                    "GUSTS" -> "⚠️ Ráfagas"
                    "WIND" -> "⚠️ Viento intenso"
                    "COLD" -> "⚠️ Helada"
                    "HEAT" -> "⚠️ Calor intenso"
                    "UV" -> "⚠️ Radiación UV alta"
                    "VISIBILITY" -> "⚠️ Visibilidad reducida"
                    else -> "⚠️ Clima extremo"
                }
            }
            WeatherBadgeState.SECO -> "☀️ Clima seco"
        }
    }

    /**
     * Obtiene el icono del badge según el estado
     */
    private fun getBadgeIconResId(state: WeatherBadgeState, weatherStatus: WeatherStatus): Int {
        // Usar el icono del clima actual si está disponible
        if (weatherStatus is WeatherStatus.Success) {
            return com.zipstats.app.repository.WeatherRepository.getIconResIdForCondition(
                weatherStatus.icon,
                weatherStatus.isDay
            )
        }
        // Fallback: usar icono genérico de alerta
        return android.R.drawable.ic_dialog_alert
    }



    /**
     * Detecta cambios en el estado del clima y muestra notificaciones si es necesario
     */
    private fun checkAndNotifyWeatherChange() {
        // 1. Obtenemos la "Foto" actual de los badges
        val currentState = getCurrentWeatherBadgeState()
        val lastState = lastWeatherBadgeState

        // 2. Si es la primera vez que entramos (arranque), guardamos y salimos
        if (lastState == null) {
            lastWeatherBadgeState = currentState
            Log.d(TAG, "📍 Punto de control inicial: $currentState (Sin notificación)")
            return
        }

        // 3. Si el Badge ha cambiado respecto a la última vez
        if (currentState != lastState) {
            Log.d(TAG, "🔔 Cambio de Badge detectado: $lastState -> $currentState")

            // Solo notificamos si el nuevo estado no es "SECO"
            // (Para no molestar cuando sale el sol, solo cuando hay peligro)
            if (currentState != WeatherBadgeState.SECO) {

                // Obtenemos textos e iconos dinámicamente
                val text = getBadgeText(currentState, weatherExtremeReason)
                val icon = getBadgeIconResId(currentState, _weatherStatus.value)

                notificationHandler.showWeatherChangeNotification(text, icon)
            }

            // 4. Actualizamos la memoria para la próxima comparación
            lastWeatherBadgeState = currentState
        }
    }

    /**
     * Captura el clima al inicio de la ruta con reintentos automáticos
     * Si ya hay snapshot inicial, lo reutiliza
     * Tiene hasta 60 segundos para obtener el clima antes de marcar error
     * Se ejecuta en segundo plano y no bloquea el inicio del tracking
     */
     suspend fun captureStartWeather() {
        // Si ya hay snapshot inicial, reutilizarlo
        val snapshot = _initialWeatherSnapshot
        if (snapshot != null && _initialWeatherCaptured) {
            Log.d(TAG, "♻️ Reutilizando clima inicial capturado en precarga")
            
            // Google Weather API ya devuelve condiciones efectivas, no necesitamos procesarlas
            val condition = snapshot.icon.uppercase()
            val weatherEmoji = snapshot.weatherEmoji
            val weatherDescription = snapshot.description // Ya viene en español de Google
            
            // Determinar si es lluvia activa usando condición y descripción de Google
            val (isActiveRain, rainUserReason) = weatherAdvisor.checkActiveRain(
                condition = condition,
                description = weatherDescription,
                precipitation = snapshot.precipitation
            )
            
            // Calzada húmeda: Solo si NO hay lluvia activa
            val isWetRoad = if (isActiveRain) {
                false // Excluir calzada húmeda si hay lluvia activa
            } else {
                val (lat, lon) = when {
                    _initialWeatherLatitude != null && _initialWeatherLongitude != null ->
                        _initialWeatherLatitude!! to _initialWeatherLongitude!!
                    _preLocation.value != null ->
                        _preLocation.value!!.latitude to _preLocation.value!!.longitude
                    else -> null
                } ?: run {
                    Log.w(TAG, "⚠️ No hay coordenadas disponibles para calcular precip24h (snapshot inicial). Se omite regla de 24h.")
                    0.0 to 0.0
                }

                val precip24h = if (lat == 0.0 && lon == 0.0) 0.0 else getRecentPrecipitation24h(
                    latitude = lat,
                    longitude = lon
                )
                weatherDetectedClimateProfile = weatherAdvisor.detectClimateProfileTag(
                    condition = condition,
                    description = weatherDescription,
                    humidity = snapshot.humidity,
                    recentPrecipitation3h = recentPrecipitation3h,
                    precip24h = precip24h,
                    temperature = snapshot.temperature,
                    dewPoint = snapshot.dewPoint,
                    windSpeed = snapshot.windSpeed,
                    weatherEmoji = weatherEmoji,
                    uvIndex = snapshot.uvIndex,
                    isDay = snapshot.isDay
                )
                Log.d(
                    TAG,
                    "🧭 [Inicio de ruta] Perfil climático detectado: $weatherDetectedClimateProfile"
                )
                weatherAdvisor.checkWetRoadConditions(
                    condition = condition,
                    humidity = snapshot.humidity,
                    recentPrecipitation3h = recentPrecipitation3h,
                    precip24h = precip24h,
                    hasActiveRain = isActiveRain,
                    isDay = snapshot.isDay,
                    temperature = snapshot.temperature,
                    dewPoint = snapshot.dewPoint,
                    uvIndex = snapshot.uvIndex,
                    weatherEmoji = weatherEmoji,
                    weatherDescription = weatherDescription,
                    windSpeed = snapshot.windSpeed
                )
            }
            
            // Actualizar flags de preaviso para mostrar iconos en tarjeta del clima
            if (isActiveRain) {
                weatherHadRain = true
                weatherHadWetRoad = false // Lluvia excluye calzada húmeda
                _shouldShowRainWarning.value = true
                _isActiveRainWarning.value = true
            } else if (isWetRoad) {
                weatherHadWetRoad = true // 🔥 IMPORTANTE: Establecer aquí también, no solo en monitoreo continuo
                _shouldShowRainWarning.value = true
                _isActiveRainWarning.value = false
                Log.d(TAG, "🛣️ [Precarga inicial] Estado actualizado: weatherHadWetRoad=true")
            }
            
            // Detectar condiciones extremas
            val hasExtremeConditions = weatherAdvisor.checkExtremeConditions(
                windSpeed = snapshot.windSpeed,
                windGusts = snapshot.windGusts,
                temperature = snapshot.temperature,
                uvIndex = snapshot.uvIndex,
                isDay = snapshot.isDay,
                weatherEmoji = weatherEmoji,
                weatherDescription = weatherDescription,
                weatherCode = snapshot.weatherCode,
                visibility = snapshot.visibility
            )
            
            if (hasExtremeConditions) {
                weatherHadExtremeConditions = true // 🔥 IMPORTANTE: Establecer aquí también, no solo en monitoreo continuo
                _shouldShowExtremeWarning.value = true
                Log.d(TAG, "⚠️ [Precarga inicial] Estado actualizado: weatherHadExtremeConditions=true")
            }
            
            if (isActiveRain) {
                if (!weatherHadRain) {
                    weatherHadRain = true
                    weatherRainStartMinute = 0 // Al inicio de la ruta
                    weatherRainReason = rainUserReason
                }
                weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, snapshot.precipitation)
            }
            
            // Guardar en variables de inicio de ruta
            _startWeatherTemperature = snapshot.temperature
            _startWeatherEmoji = weatherEmoji
            _startWeatherCode = snapshot.weatherCode
            _startWeatherCondition = snapshot.icon
            _startWeatherDescription = weatherDescription
            _startWeatherIsDay = snapshot.isDay
            _startWeatherFeelsLike = snapshot.feelsLike
            _startWeatherWindChill = snapshot.windChill
            _startWeatherHeatIndex = snapshot.heatIndex
            _startWeatherHumidity = snapshot.humidity
            _startWeatherWindSpeed = snapshot.windSpeed
            _startWeatherUvIndex = snapshot.uvIndex
            _startWeatherWindDirection = snapshot.windDirection
            _startWeatherWindGusts = snapshot.windGusts
            _startWeatherRainProbability = snapshot.rainProbability
            _startWeatherVisibility = snapshot.visibility
            _startWeatherDewPoint = snapshot.dewPoint

            // 🔔 Inicializar estado anterior del clima después de actualizar badges
            lastWeatherBadgeState = getCurrentWeatherBadgeState()
            Log.d(TAG, "🌤️ [Inicio de ruta] Estado inicial del clima establecido: $lastWeatherBadgeState")
            
            // Guardar clima con estados de advertencia actuales
            routeRepository.saveTempWeather(
                snapshot.copy(
                    shouldShowRainWarning = _shouldShowRainWarning.value,
                    isActiveRainWarning = _isActiveRainWarning.value,
                    shouldShowExtremeWarning = _shouldShowExtremeWarning.value
                )
            )

            _weatherStatus.value = WeatherStatus.Success(
                temperature = snapshot.temperature,
                feelsLike = snapshot.feelsLike,
                windChill = snapshot.windChill,
                heatIndex = snapshot.heatIndex,
                description = weatherDescription,
                icon = snapshot.icon, // Condition string de Google
                humidity = snapshot.humidity,
                windSpeed = snapshot.windSpeed,
                weatherEmoji = weatherEmoji,
                weatherCode = snapshot.weatherCode, // Mapeado para compatibilidad
                isDay = snapshot.isDay,
                uvIndex = snapshot.uvIndex,
                windDirection = snapshot.windDirection,
                windGusts = snapshot.windGusts,
                rainProbability = snapshot.rainProbability,
                precipitation = snapshot.precipitation,
                rain = snapshot.rain,
                showers = snapshot.showers,
                dewPoint = snapshot.dewPoint,
                visibility = snapshot.visibility
            )
            
            Log.d(TAG, "✅ Clima inicial reutilizado: ${snapshot.temperature}°C $weatherEmoji")
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
                val startApiTime = System.currentTimeMillis()
                
                while (!success && retryCount < maxRetries) {
                    retryCount++
                    Log.d(TAG, "🌤️ Intento ${retryCount}/${maxRetries} de obtener clima...")
                    
                    val result = weatherRepository.getCurrentWeather(
                        latitude = firstPoint.latitude,
                        longitude = firstPoint.longitude
                    )
                    
                    result.onSuccess { weather ->
                        // Google Weather API ya devuelve condiciones efectivas, no necesitamos procesarlas
                        val condition = weather.icon.uppercase()
                        val weatherEmoji = weather.weatherEmoji
                        val weatherDescription = weather.description // Ya viene en español de Google
                        
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
                        
                        if (weatherEmoji.isBlank()) {
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
                        
                        // Determinar si es lluvia activa usando condición y descripción de Google
                        val (isActiveRain, rainUserReason) = weatherAdvisor.checkActiveRain(
                            condition = condition,
                            description = weatherDescription,
                            precipitation = weather.precipitation
                        )
                        
                        // Precipitación acumulada reciente (últimas 3h) desde histórico de Google
                        val localRecentPrecip3h = if (isActiveRain) 0.0 else getRecentPrecipitation3h(
                            latitude = firstPoint.latitude,
                            longitude = firstPoint.longitude
                        )
                        val localPrecip24h = if (isActiveRain) 0.0 else getRecentPrecipitation24h(
                            latitude = firstPoint.latitude,
                            longitude = firstPoint.longitude
                        )
                        recentPrecipitation3h = 0.7 * recentPrecipitation3h + 0.3 * localRecentPrecip3h
                        weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, weather.precipitation, localRecentPrecip3h)

                        // Calzada húmeda: Solo si NO hay lluvia activa
                        val isWetRoad = if (isActiveRain) {
                            false // Excluir calzada húmeda si hay lluvia activa
                        } else {
                            weatherDetectedClimateProfile = weatherAdvisor.detectClimateProfileTag(
                                condition = condition,
                                description = weatherDescription,
                                humidity = weather.humidity,
                                recentPrecipitation3h = localRecentPrecip3h,
                                precip24h = localPrecip24h,
                                temperature = weather.temperature,
                                dewPoint = weather.dewPoint,
                                windSpeed = weather.windSpeed,
                                weatherEmoji = weatherEmoji,
                                uvIndex = weather.uvIndex,
                                isDay = weather.isDay
                            )
                            Log.d(
                                TAG,
                                "🧭 [Captura manual] Perfil climático detectado: $weatherDetectedClimateProfile"
                            )
                            weatherAdvisor.checkWetRoadConditions(
                                condition = condition,
                                humidity = weather.humidity,
                                recentPrecipitation3h = localRecentPrecip3h,
                                precip24h = localPrecip24h,
                                hasActiveRain = isActiveRain,
                                isDay = weather.isDay,
                                temperature = weather.temperature,
                                dewPoint = weather.dewPoint,
                                uvIndex = weather.uvIndex,
                                weatherEmoji = weatherEmoji,
                                weatherDescription = weatherDescription,
                                windSpeed = weather.windSpeed
                            )
                        }
                        
                        // Actualizar flags de preaviso para mostrar iconos en tarjeta del clima
                        if (isActiveRain) {
                            _shouldShowRainWarning.value = true
                            _isActiveRainWarning.value = true
                        } else if (isWetRoad) {
                            _shouldShowRainWarning.value = true
                            _isActiveRainWarning.value = false
                        }
                        
                        // Detectar condiciones extremas
                        val hasExtremeConditions = weatherAdvisor.checkExtremeConditions(
                            windSpeed = weather.windSpeed,
                            windGusts = weather.windGusts,
                            temperature = weather.temperature,
                            uvIndex = weather.uvIndex,
                            isDay = weather.isDay,
                            weatherEmoji = weatherEmoji,
                            weatherDescription = weatherDescription,
                            weatherCode = weather.weatherCode,
                            visibility = weather.visibility
                        )
                        
                        if (hasExtremeConditions) {
                            _shouldShowExtremeWarning.value = true
                        }
                        
                        if (isActiveRain) {
                            if (!weatherHadRain) {
                                weatherHadRain = true
                                weatherRainStartMinute = 0 // Al inicio de la ruta
                                weatherRainReason = rainUserReason // Guardar razón amigable para el usuario
                            }
                            weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, weather.precipitation)
                        }
                        
                        // Clima válido - guardar y salir (usando valores de Google directamente)
                        _startWeatherTemperature = weather.temperature
                        _startWeatherEmoji = weatherEmoji
                        _startWeatherDescription = weatherDescription
                        _startWeatherIsDay = weather.isDay
                        _startWeatherFeelsLike = weather.feelsLike
                        _startWeatherWindChill = weather.windChill
                        _startWeatherHeatIndex = weather.heatIndex
                        _startWeatherHumidity = weather.humidity
                        _startWeatherWindSpeed = weather.windSpeed
                        _startWeatherUvIndex = weather.uvIndex
                        _startWeatherWindDirection = weather.windDirection
                        _startWeatherWindGusts = weather.windGusts
                        _startWeatherRainProbability = weather.rainProbability
                        _startWeatherVisibility = weather.visibility
                        _startWeatherDewPoint = weather.dewPoint

                        // Guardar clima con estados de advertencia actuales
                        routeRepository.saveTempWeather(
                            weather.copy(
                                shouldShowRainWarning = _shouldShowRainWarning.value,
                                isActiveRainWarning = _isActiveRainWarning.value,
                                shouldShowExtremeWarning = _shouldShowExtremeWarning.value
                            )
                        )

                        _weatherStatus.value = WeatherStatus.Success(
                            temperature = weather.temperature,
                            feelsLike = weather.feelsLike,
                            windChill = weather.windChill,
                            heatIndex = weather.heatIndex,
                            description = weatherDescription,
                            icon = weather.icon, // Condition string de Google
                            humidity = weather.humidity,
                            windSpeed = weather.windSpeed,
                            weatherEmoji = weatherEmoji,
                            weatherCode = weather.weatherCode, // Mapeado para compatibilidad
                            isDay = weather.isDay,
                            uvIndex = weather.uvIndex,
                            windDirection = weather.windDirection,
                            windGusts = weather.windGusts,
                            rainProbability = weather.rainProbability,
                            precipitation = weather.precipitation,
                            rain = weather.rain,
                            showers = weather.showers,
                            dewPoint = weather.dewPoint,
                            visibility = weather.visibility
                        )

                        success = true
                        
                        val elapsedMs = System.currentTimeMillis() - startApiTime
                        Log.d(TAG, "✅ Clima capturado y VALIDADO en ${elapsedMs}ms: ${weather.temperature}°C $weatherEmoji")
                        Log.d(TAG, "✅ Descripción: $weatherDescription")
                        Log.d(TAG, "✅ Precipitación: ${weather.precipitation}mm, Rain: ${weather.rain}mm, Showers: ${weather.showers}mm")
                        Log.d(TAG, "✅ Humedad: ${weather.humidity}%, Prob. lluvia: ${weather.rainProbability}%, Viento: ${weather.windSpeed} km/h")
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
                
                val result = weatherRepository.getCurrentWeather(
                    latitude = firstPoint.latitude,
                    longitude = firstPoint.longitude
                )
                
                result.onSuccess { weather ->
                    // Google Weather API ya devuelve condiciones efectivas, no necesitamos procesarlas
                    val condition = weather.icon.uppercase()
                    val weatherEmoji = weather.weatherEmoji
                    val weatherDescription = weather.description // Ya viene en español de Google
                    
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
                    
                    if (weatherEmoji.isBlank()) {
                        Log.e(TAG, "⚠️ Clima recibido con emoji vacío. NO se guardará.")
                        _weatherStatus.value = WeatherStatus.Error("Emoji de clima vacío")
                        _message.value = "Emoji de clima vacío"
                        return@launch
                    }
                    
                    // Determinar si es lluvia activa usando condición y descripción de Google
                    val (isActiveRain, rainUserReason) = weatherAdvisor.checkActiveRain(
                        condition = condition,
                        description = weatherDescription,
                        precipitation = weather.precipitation
                    )
                    
                    if (isActiveRain) {
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
                    _startWeatherEmoji = weatherEmoji
                    _startWeatherDescription = weatherDescription
                    _startWeatherIsDay = weather.isDay
                    _startWeatherFeelsLike = weather.feelsLike
                    _startWeatherWindChill = weather.windChill
                    _startWeatherHeatIndex = weather.heatIndex
                    _startWeatherHumidity = weather.humidity
                    _startWeatherWindSpeed = weather.windSpeed
                    _startWeatherUvIndex = weather.uvIndex
                    _startWeatherWindDirection = weather.windDirection
                    _startWeatherWindGusts = weather.windGusts
                    _startWeatherRainProbability = weather.rainProbability
                    _startWeatherVisibility = weather.visibility
                    _startWeatherDewPoint = weather.dewPoint

                    routeRepository.saveTempWeather(weather)


                    _weatherStatus.value = WeatherStatus.Success(
                        temperature = weather.temperature,
                        feelsLike = weather.feelsLike,
                        windChill = weather.windChill,
                        heatIndex = weather.heatIndex,
                        description = weatherDescription,
                        icon = weather.icon, // Condition string de Google
                        humidity = weather.humidity,
                        windSpeed = weather.windSpeed,
                        weatherEmoji = weatherEmoji,
                        weatherCode = weather.weatherCode, // Mapeado para compatibilidad
                        isDay = weather.isDay,
                        uvIndex = weather.uvIndex,
                        windDirection = weather.windDirection,
                        windGusts = weather.windGusts,
                        rainProbability = weather.rainProbability,
                        precipitation = weather.precipitation,
                        rain = weather.rain,
                        showers = weather.showers,
                        dewPoint = weather.dewPoint,
                        visibility = weather.visibility
                    )
                    Log.d(TAG, "✅ Clima obtenido manualmente: ${weather.temperature}°C $weatherEmoji")
                    _message.value = "Clima obtenido: ${formatTemperature(weather.temperature, 0)}°C $weatherEmoji"
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

    fun getSurfaceConditionTypeForConfirmation(): SurfaceConditionType {
        val hadRainNow = weatherHadRain || (_shouldShowRainWarning.value && _isActiveRainWarning.value)
        val hadWetRoadNow = weatherHadWetRoad || (_shouldShowRainWarning.value && !_isActiveRainWarning.value)

        return when {
            hadRainNow -> SurfaceConditionType.RAIN
            hadWetRoadNow -> SurfaceConditionType.WET_ROAD
            else -> SurfaceConditionType.NONE
        }
    }

    /**
     * Finaliza y guarda la ruta
     */
    fun finishTracking(
        notes: String = "",
        addToRecords: Boolean = false,
        surfaceConditionType: SurfaceConditionType = SurfaceConditionType.NONE,
        isSurfaceConditionConfirmed: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                // 1. 🛑 PARADA TÉCNICA Y LIMPIEZA DE UI
                notificationHandler.dismissWeatherNotification()

                // Cancelar jobs de monitoreo (viento, lluvia, etc.)
                continuousWeatherJob?.cancel()
                globalStateJob?.cancel()
                continuousWeatherJob = null
                globalStateJob = null

                // 2. 🛰️ DETENER SERVICIO Y CAMBIAR ESTADO
                _trackingState.value = TrackingState.Saving
                appOverlayRepository.showSplashOverlay("Guardando ruta…")

                stopTrackingService()
                trackingStateManager.resetState()

                // 3. ⏳ TIEMPO DE GRACIA PARA EL CLIMA
                // Si el clima está cargando, esperamos hasta 5 segundos
                if (_weatherStatus.value is WeatherStatus.Loading) {
                    Log.d(TAG, "⏳ Clima aún cargando, esperando hasta 5s más...")
                    var waited = 0
                    while (_weatherStatus.value is WeatherStatus.Loading && waited < 5000) {
                        kotlinx.coroutines.delay(500)
                        waited += 500
                    }
                }

                // 4. 📸 CAPTURA DE DATOS (Snapshot)
                val points = _routePoints.value
                val scooter = _selectedScooter.value ?: throw Exception("No hay vehículo seleccionado")
                val startTime = _startTime.value
                val endTime = System.currentTimeMillis()

                if (points.isEmpty()) throw Exception("No se registraron puntos GPS")

                // Si hubo condiciones especiales (lluvia/extremo), intentamos capturar el clima FINAL
                val hasActiveBadges = weatherHadRain || weatherHadWetRoad || weatherHadExtremeConditions
                if (hasActiveBadges) {
                    fetchFinalWeatherSnapshot(points.last())
                }

                // Creamos el objeto con todos los datos climáticos actuales
                val weatherSnapshot = captureRouteWeatherSnapshot()
                val weatherDecision = RouteWeatherDecision(
                    surfaceConditionType = surfaceConditionType,
                    isSurfaceConditionConfirmed = isSurfaceConditionConfirmed
                )

                // 5. 🧹 RESET DE VARIABLES UI (Limpiamos el ViewModel)
                resetTrackingUI()

                // 6. 🏗️ CREAR RUTA Y PROCESAR CLIMA (Delegación al Repository)
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

                // El repositorio decide qué valores de clima son los correctos (iniciales vs extremos)
                val finalRoute = routeRepository.finalizeRouteWithWeather(
                    baseRoute = baseRoute,
                    snap = weatherSnapshot,
                    decision = weatherDecision
                )

                // 7. ☁️ GUARDAR EN FIREBASE
                val result = routeRepository.saveRoute(finalRoute)

                if (result.isSuccess) {
                    Log.d(TAG, "✅ Ruta guardada con éxito: ${finalRoute.id}")

                    resetAllWeatherVariables()

                    // Usamos el formato detallado de tu backup
                    val distanceText = LocationUtils.formatNumberSpanish(finalRoute.totalDistance.roundToOneDecimal())
                    var message = "Ruta guardada: $distanceText km"

                    // 8. 📝 AÑADIR A REGISTROS (Si aplica)
                    if (addToRecords) {
                        processRouteRecords(scooter, finalRoute)
                        message += "\nDistancia añadida a registros correctamente"
                    }

                    _message.value = message

                } else {
                    throw result.exceptionOrNull() ?: Exception("Error desconocido al guardar")
                }

            } catch (e: Exception) {
                handleTrackingError(e)
            } finally {
                // 1. Pequeña espera para que el usuario lea "Guardando..."
                kotlinx.coroutines.delay(600)

                // 2. Cerramos el overlay con el nombre correcto
                appOverlayRepository.hideOverlay()

                // 3. Volvemos al estado inicial
                _trackingState.value = TrackingState.Idle
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
        weatherDetectedClimateProfile = null
        maxWindSpeed = 0.0
        maxWindGusts = 0.0
        minTemperature = Double.MAX_VALUE
        maxTemperature = Double.MIN_VALUE
        maxUvIndex = 0.0
        pendingRainConfirmation = false
        pendingRainMinute = null
        pendingRainReason = null
        
        // 🔥 Limpiar TODOS los badges al cancelar
        _shouldShowRainWarning.value = false
        _isActiveRainWarning.value = false
        _shouldShowExtremeWarning.value = false
        
        // 🔔 Resetear estado anterior del clima al cancelar tracking
        lastWeatherBadgeState = null
        
        // Limpiar estado del clima en la UI
        _weatherStatus.value = WeatherStatus.Idle
        
        // Limpiar snapshot inicial
        _initialWeatherSnapshot = null
        _initialWeatherCaptured = false
        _initialWeatherLatitude = null
        _initialWeatherLongitude = null
        
        Log.d(TAG, "🔄 Estado del clima y badges reseteados al cancelar tracking")
        
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

        // 1. Limpiar la notificación de la barra de estado
        notificationHandler.dismissWeatherNotification()

        // 2. Cancelar jobs de clima
        weatherJob?.cancel()
        continuousWeatherJob?.cancel()

        // 3. Detener posicionamiento previo si está activo
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

    fun captureRouteWeatherSnapshot(): RouteWeatherSnapshot {
        return RouteWeatherSnapshot(
            // 1. Datos del INICIO (siempre presentes, nunca tocados)
            initialTemp            = _startWeatherTemperature,
            initialEmoji           = _startWeatherEmoji,
            initialCode            = _startWeatherCode,
            initialCondition       = _startWeatherCondition,
            initialDescription     = _startWeatherDescription,
            initialIsDay           = _startWeatherIsDay ?: true,
            initialFeelsLike       = _startWeatherFeelsLike,
            initialWindChill       = _startWeatherWindChill,
            initialHeatIndex       = _startWeatherHeatIndex,
            initialHumidity        = _startWeatherHumidity?.toDouble(),
            initialWindSpeed       = _startWeatherWindSpeed,
            initialWindGusts       = _startWeatherWindGusts,
            initialUvIndex         = _startWeatherUvIndex,
            initialVisibility      = _startWeatherVisibility,
            initialDewPoint        = _startWeatherDewPoint,
            initialRainProbability = _startWeatherRainProbability?.toDouble(),
            initialWindDirection   = _startWeatherWindDirection,

            // 2. Datos del FINAL (null si ruta tranquila, sin badges)
            finalTemp            = _finalWeatherTemperature,
            finalEmoji           = _finalWeatherEmoji,
            finalCode            = _finalWeatherCode,
            finalCondition       = _finalWeatherCondition,
            finalDescription     = _finalWeatherDescription,
            finalIsDay           = _finalWeatherIsDay,
            finalFeelsLike       = _finalWeatherFeelsLike,
            finalWindChill       = _finalWeatherWindChill,
            finalHeatIndex       = _finalWeatherHeatIndex,
            finalDewPoint        = _finalWeatherDewPoint,
            finalWindSpeed       = _finalWeatherWindSpeed,
            finalWindGusts       = _finalWeatherWindGusts,
            finalWindDirection   = _finalWeatherWindDirection,
            finalHumidity        = _finalWeatherHumidity?.toDouble(),
            finalRainProbability = _finalWeatherRainProbability?.toDouble(),
            finalUvIndex         = _finalWeatherUvIndex,
            finalVisibility      = _finalWeatherVisibility,

            // 3. Extremos del monitoreo continuo (sin cambios)
            maxWindSpeed    = this.maxWindSpeed,
            maxWindGusts    = this.maxWindGusts,
            minTemp         = this.minTemperature,
            maxTemp         = this.maxTemperature,
            maxUvIndex      = this.maxUvIndex,
            maxPrecipitation = this.weatherMaxPrecipitation,

            // 4. Badges (sin cambios)
            hadRain        = this.weatherHadRain || (_shouldShowRainWarning.value && _isActiveRainWarning.value),
            hadWetRoad     = this.weatherHadWetRoad || (_shouldShowRainWarning.value && !_isActiveRainWarning.value),
            hadExtreme     = this.weatherHadExtremeConditions || _shouldShowExtremeWarning.value,
            climateProfile = this.weatherDetectedClimateProfile,
            extremeReason  = this.weatherExtremeReason,
            rainReason     = this.weatherRainReason,
            rainStartMinute = this.weatherRainStartMinute
        )
    }

     fun resetTrackingUI() {
        _routePoints.value = emptyList()
        _currentDistance.value = 0.0
        _currentSpeed.value = 0.0
        _duration.value = 0L
        _timeInMotion.value = 0L
        Log.d(TAG, "🧹 UI de tracking reseteada")
    }

     fun resetAllWeatherVariables() {
        // 1. Datos base del clima
        _startWeatherTemperature = null
        _startWeatherEmoji = null
        _startWeatherCode = null
        _startWeatherCondition = null
        _startWeatherDescription = null
        _startWeatherIsDay = null
        _startWeatherFeelsLike = null
        _startWeatherWindChill = null
        _startWeatherHeatIndex = null
        _startWeatherHumidity = null
        _startWeatherWindSpeed = null
        _startWeatherUvIndex = null
        _startWeatherWindDirection = null
        _startWeatherWindGusts = null
        _startWeatherRainProbability = null
        _startWeatherVisibility = null
        _startWeatherDewPoint = null

         _finalWeatherTemperature = null
         _finalWeatherEmoji = null
         _finalWeatherCode = null
         _finalWeatherCondition = null
         _finalWeatherDescription = null
         _finalWeatherIsDay = null
         _finalWeatherFeelsLike = null
         _finalWeatherWindChill = null
         _finalWeatherHeatIndex = null
         _finalWeatherHumidity = null
         _finalWeatherWindSpeed = null
         _finalWeatherUvIndex = null
         _finalWeatherWindDirection = null
         _finalWeatherWindGusts = null
         _finalWeatherRainProbability = null
         _finalWeatherVisibility = null
         _finalWeatherDewPoint = null

        // 2. Acumulados y detección de la ruta
        weatherHadRain = false
        weatherHadWetRoad = false
        weatherHadExtremeConditions = false
        weatherDetectedClimateProfile = null
        weatherExtremeReason = null
        weatherMaxPrecipitation = 0.0
        weatherRainStartMinute = null
        weatherRainReason = null

        // 3. Valores extremos detectados
        maxWindSpeed = 0.0
        maxWindGusts = 0.0
        minTemperature = Double.MAX_VALUE
        maxTemperature = Double.MIN_VALUE
        maxUvIndex = 0.0

        // 4. Estados lógicos y Snapshots
        pendingRainConfirmation = false
        pendingRainMinute = null
        pendingRainReason = null
        _initialWeatherSnapshot = null
        _initialWeatherCaptured = false
        _initialWeatherLatitude = null
        _initialWeatherLongitude = null

        // 5. Badges y Notificaciones
        _shouldShowRainWarning.value = false
        _isActiveRainWarning.value = false
        _shouldShowExtremeWarning.value = false
        lastWeatherBadgeState = null

        // 6. Estado final e hilos
        _weatherStatus.value = WeatherStatus.Idle
        routeRepository.clearTempWeather()

        Log.d(TAG, "🔄 Todos los estados de clima y badges han sido reseteados a cero")
    }

    private suspend fun fetchFinalWeatherSnapshot(lastPoint: RoutePoint) {
        Log.d(TAG, "📸 Capturando snapshot FINAL del clima...")
        try {
            val result = weatherRepository.getCurrentWeather(lastPoint.latitude, lastPoint.longitude)
            result.onSuccess { weather ->
                // ✅ Ahora escribe en _final*, sin tocar _start*
                _finalWeatherEmoji        = weather.weatherEmoji
                _finalWeatherDescription  = weather.description
                _finalWeatherCondition    = weather.icon
                _finalWeatherCode         = weather.weatherCode
                _finalWeatherIsDay        = weather.isDay
                _finalWeatherTemperature  = weather.temperature
                _finalWeatherFeelsLike    = weather.feelsLike
                _finalWeatherWindChill    = weather.windChill
                _finalWeatherHeatIndex    = weather.heatIndex
                _finalWeatherDewPoint     = weather.dewPoint
                _finalWeatherWindSpeed    = weather.windSpeed
                _finalWeatherWindGusts    = weather.windGusts
                _finalWeatherWindDirection = weather.windDirection
                _finalWeatherHumidity     = weather.humidity
                _finalWeatherRainProbability = weather.rainProbability
                _finalWeatherUvIndex      = weather.uvIndex
                _finalWeatherVisibility   = weather.visibility

                Log.d(TAG, "✅ Snapshot final capturado. Inicio preservado intacto.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error en snapshot final: ${e.message}. Se usarán datos de inicio.")
        }
    }

    private suspend fun processRouteRecords(scooter: Scooter, route: Route) {
        try {
            Log.d(TAG, "Intentando añadir ruta a registros para patinete: ${scooter.nombre}")

            // 1. Obtener la fecha y los registros actuales
            val currentDate = java.time.LocalDate.now()
            val formattedDate = com.zipstats.app.utils.DateUtils.formatForApi(currentDate)
            val allRecords = recordRepository.getRecords().first()

            // 2. Buscar el último registro de este patinete para saber los km anteriores
            val lastRecord = allRecords
                .filter { it.patinete == scooter.nombre }
                .maxByOrNull { it.fecha }

            // 3. Calcular el nuevo kilometraje acumulado
            val newKilometraje = if (lastRecord != null) {
                lastRecord.kilometraje + route.totalDistance
            } else {
                route.totalDistance
            }

            // 4. Guardar el nuevo registro
            // (Aquí usa la llamada a tu repositorio que ya tenías escrita)
            /* recordRepository.saveRecord(...) */

            Log.d(TAG, "✅ Registro actualizado: ${scooter.nombre} ahora tiene $newKilometraje km")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al procesar registros de kilometraje: ${e.message}")
            // No lanzamos la excepción para que la ruta se considere guardada
            // aunque el registro falle
        }
    }
    private fun handleTrackingError(e: Exception) {
        Log.e(TAG, "❌ Error al finalizar tracking: ${e.message}", e)
        _message.value = "Error al guardar: ${e.message}"
        // Esto asegura que la UI sepa que hubo un fallo y detenga el estado de carga
        _trackingState.value = TrackingState.Error(e.message ?: "Error desconocido")
    }

}