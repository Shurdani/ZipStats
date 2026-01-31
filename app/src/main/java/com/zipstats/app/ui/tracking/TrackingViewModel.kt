package com.zipstats.app.ui.tracking

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
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
import kotlin.Double
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
    
    // ID del canal de notificaciones de cambio de clima
    private val WEATHER_CHANGE_CHANNEL_ID = "weather_change_channel"
    private val WEATHER_CHANGE_NOTIFICATION_ID = 2000
    
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
    private var weatherExtremeReason: String? = null // Razón de condiciones extremas (WIND, GUSTS, STORM, SNOW, COLD, HEAT)

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
                
                val result = weatherRepository.getCurrentWeather(
                    latitude = preLocation.latitude,
                    longitude = preLocation.longitude
                )
                
                result.onSuccess { weather ->
                    // Google Weather API ya devuelve condiciones efectivas, no necesitamos "adivinar"
                    // weather.icon contiene el condition string (ej: "RAIN", "CLEAR", "LIGHT_RAIN")
                    val condition = weather.icon.uppercase()
                    
                    // Obtener emoji y descripción directamente desde Google (ya vienen correctos)
                    val weatherEmoji = weather.weatherEmoji
                    val weatherDescription = weather.description // Ya viene en español de Google
                    
                    // Guardar snapshot inicial
                    _initialWeatherSnapshot = weather
                    _initialWeatherCaptured = true
                    _initialWeatherLatitude = preLocation.latitude
                    _initialWeatherLongitude = preLocation.longitude
                    
                    // Actualizar estado de UI
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

                    // Guardar precipitación máxima desde el inicio.
                    // Nota: en WeatherRepository, `weather.precipitation` viene de Google:
                    // `precipitation.qpf.quantity` (acumulación de precipitación en la ÚLTIMA HORA).
                    weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, weather.precipitation)

                    // Precipitación acumulada reciente (últimas 3h) desde histórico de Google
                    recentPrecipitation3h = getRecentPrecipitation3h(
                        latitude = preLocation.latitude,
                        longitude = preLocation.longitude
                    )
                    weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, recentPrecipitation3h)
                    
                    // Determinar si es lluvia activa usando condición y descripción de Google
                    val (isActiveRain, rainUserReason) = checkActiveRain(
                        condition = condition,
                        description = weatherDescription,
                        precipitation = weather.precipitation
                    )
                    Log.d(TAG, "🔍 [Precarga] checkActiveRain: condition=$condition, description=$weatherDescription, precip=${weather.precipitation}, isActiveRain=$isActiveRain")
                    
                    // Calzada húmeda: Solo si NO hay lluvia activa
                    val precip24h = if (isActiveRain) 0.0 else getRecentPrecipitation24h(
                        latitude = preLocation.latitude,
                        longitude = preLocation.longitude
                    )
                    val isWetRoad = if (isActiveRain) {
                        // Si hay lluvia activa, NO debe haber calzada húmeda
                        false // Excluir calzada húmeda si hay lluvia activa
                    } else {
                        checkWetRoadConditions(
                            condition = condition,
                            humidity = weather.humidity,
                            recentPrecipitation3h = recentPrecipitation3h,
                            precip24h = precip24h,
                            hasActiveRain = isActiveRain,
                            isDay = weather.isDay,
                            temperature = weather.temperature,
                            dewPoint = weather.dewPoint,
                            weatherEmoji = weatherEmoji,
                            weatherDescription = weatherDescription,
                            windSpeed= weather.windSpeed

                            )
                    }
                    
                    // 🔥 IMPORTANTE: NO establecer StateFlows aquí - se establecerán más abajo
                    // después de verificar lluvia activa vs calzada húmeda
                    
                    // Detectar visibilidad reducida (crítico para Barcelona - niebla/talaia)
                    val (isLowVisibility, visReason) = checkLowVisibility(weather.visibility)
                    
                    // Detectar condiciones extremas (incluye visibilidad reducida)
                    val hasExtremeConditions = checkExtremeConditions(
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
                    _shouldShowExtremeWarning.value = hasExtremeConditions
                    
                    // 🔥 JERARQUÍA DE BADGES (misma lógica que RouteDetailDialog):
                    // 1. Lluvia: Máxima prioridad (siempre se muestra si existe)
                    // 2. Calzada húmeda: Solo si NO hay lluvia (excluyente con lluvia)
                    // 3. Condiciones extremas: COMPLEMENTARIO (puede coexistir con lluvia o calzada húmeda)
                    
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
                            weatherEmoji = weatherEmoji,
                            weatherDescription = weatherDescription,
                            weatherCode = weather.weatherCode,
                            visibility = weather.visibility
                        )
                        if (cause != null) {
                            weatherExtremeReason = cause
                        }
                        
                        // Log específico para visibilidad en Barcelona
                        if (isLowVisibility) {
                            Log.d(TAG, "🌫️ [Precarga] Visibilidad crítica detectada: ${weather.visibility}m - $visReason")
                        }
                    }
                    
                    // Lluvia: Máxima prioridad (siempre se muestra si existe)
                    if (isActiveRain) {
                        weatherHadRain = true
                        weatherRainStartMinute = 0 // Al inicio de la ruta
                        weatherRainReason = rainUserReason // Guardar razón amigable para el usuario
                        weatherHadWetRoad = false // Lluvia excluye calzada húmeda
                        // 🔥 IMPORTANTE: Actualizar StateFlows para que los badges se muestren en la UI
                        _shouldShowRainWarning.value = true
                        _isActiveRainWarning.value = true
                        Log.d(TAG, "🌧️ [Precarga] Lluvia activa detectada - badge 🔵 activado")
                    } else if (isWetRoad) {
                        // Calzada húmeda: Solo si NO hay lluvia activa
                        weatherHadWetRoad = true
                        // 🔥 IMPORTANTE: Actualizar StateFlows para que los badges se muestren en la UI
                        _shouldShowRainWarning.value = true
                        _isActiveRainWarning.value = false
                        Log.d(TAG, "🛣️ [Precarga] Calzada húmeda detectada - badge 🟡 activado")
                    }
                    
                    // Condiciones extremas: COMPLEMENTARIO (puede coexistir con lluvia o calzada húmeda)
                    if (hasExtremeConditions) {
                        weatherHadExtremeConditions = true
                        _shouldShowExtremeWarning.value = true
                        Log.d(TAG, "⚠️ [Precarga] Condiciones extremas detectadas - badge ⚠️ activado")
                    }
                    
                    Log.d(TAG, "✅ [Precarga] Clima inicial capturado: ${weather.temperature}°C $weatherEmoji")
                    // El log de lluvia se maneja arriba según si es lluvia activa o calzada húmeda
                    // (líneas 629 y 636)
                    
                    // 🔔 Inicializar estado anterior del clima después de actualizar badges
                    lastWeatherBadgeState = getCurrentWeatherBadgeState()
                    Log.d(TAG, "🌤️ [Precarga] Estado inicial del clima establecido: $lastWeatherBadgeState")
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
    private fun checkActiveRain(
        condition: String, // Condition string de Google (ej: "RAIN", "CLOUDY")
        description: String, // Descripción de Google (ej: "Lluvia", "Nublado")
        precipitation: Double
    ): Pair<Boolean, String> {
        val cond = condition.uppercase()
        val desc = description.uppercase()
        
        // Términos que indican lluvia en la descripción de Google
        val rainTerms = listOf("LLUVIA", "RAIN", "CHUBASCO", "TORMENTA", "DRIZZLE", "LLOVIZNA", "THUNDERSTORM", "SHOWER")
        
        // Condiciones de Google que indican lluvia
        val rainConditions = listOf("RAIN", "LIGHT_RAIN", "HEAVY_RAIN", "THUNDERSTORM", "DRIZZLE", 
                                    "LIGHT_RAIN_SHOWERS", "RAIN_SHOWERS", "HEAVY_RAIN_SHOWERS",
                                    "CHANCE_OF_SHOWERS", "SCATTERED_SHOWERS")
        
        // Si la condición contiene lluvia, es lluvia activa
        if (rainConditions.any { cond.contains(it) }) {
            return true to "Lluvia detectada por Google"
        }
        
        // Si la descripción menciona lluvia, es lluvia activa (incluso si es débil)
        if (rainTerms.any { desc.contains(it) }) {
            return true to "Lluvia detectada por Google"
        }
        
        // Si no hay indicación de lluvia en Google, no es lluvia activa
        // (podría ser calzada húmeda si ha parado de llover o hay mucha humedad)
        return false to "No se detectó lluvia"
    }

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
    
    /**
     * Verifica si hay calzada húmeda cuando NO hay lluvia activa
     * 
     * Se activa por señales físicas basadas en datos de Google:
     * - Lluvia reciente (histórico últimas 3h con precipitación acumulada > 0)
     * - Alta humedad + cielo nublado/niebla (condensación)
     * - Persistencia por temporal: humedad >= 80% + precipitación 24h > 0 + (temperature - dewPoint) <= 3°C
     * - Humedad extrema
     * - Nieve / aguanieve
     * 
     * IMPORTANTE: Si hay lluvia activa (detectada por checkActiveRain), esta función siempre retorna false
     */
    private fun checkWetRoadConditions(
        condition: String,
        humidity: Int,
        recentPrecipitation3h: Double,
        precip24h: Double,
        hasActiveRain: Boolean,
        isDay: Boolean,
        temperature: Double?,
        dewPoint: Double?,
        weatherEmoji: String? = null,
        weatherDescription: String? = null,
        windSpeed: Double? = null
    ): Boolean {

        // ─────────────────────────────
        // CORTES RÁPIDOS
        // ─────────────────────────────
        if (hasActiveRain) return false

        val cond = condition.uppercase()
        val desc = weatherDescription?.uppercase().orEmpty()
        val windSpeedKmh = (windSpeed ?: 0.0) * 3.6
        val dewSpread = if (temperature != null && dewPoint != null) temperature - dewPoint else null

        // ─────────────────────────────
        // FILTRO DE AUTOSECADO
        // ─────────────────────────────
        val isAutoDrying = humidity < 65 && windSpeedKmh > 15.0
        if (isAutoDrying) return false

        // ─────────────────────────────
        // CONDENSACIÓN (SIN LLUVIA)
        // ─────────────────────────────
        val isVeryHumid = humidity >= 92

        val isCondensingBySky =
            isVeryHumid && (cond == "FOG" || cond == "HAZE")

        val isCondensingByDewPoint =
            !isDay &&
                    isVeryHumid &&
                    dewSpread != null &&
                    dewSpread <= 1.0

        val isCondensing = isCondensingBySky || isCondensingByDewPoint

        // ─────────────────────────────
        // PERSISTENCIA POR TEMPORAL
        // ─────────────────────────────
        val isStormPersistence =
            humidity >= 90 &&
                    precip24h > 0.0 &&
                    dewSpread != null &&
                    dewSpread <= 1.5

        // ─────────────────────────────
        // HUMEDAD EXTREMA
        // ─────────────────────────────
        val isExtremelyHumid = humidity >= 95

        // ─────────────────────────────
        // NIEVE / AGUANIEVE
        // ─────────────────────────────
        val hasSnowOrSleet =
            cond == "SNOW" ||
                    cond == "SLEET" ||
                    cond.contains("SNOW") ||
                    cond.contains("SLEET") ||
                    desc.contains("NIEVE") ||
                    desc.contains("AGUANIEVE") ||
                    desc.contains("SNOW") ||
                    desc.contains("SLEET") ||
                    weatherEmoji?.contains("❄️") == true ||
                    weatherEmoji?.contains("🥶") == true

        // ─────────────────────────────
        // LLUVIA RECIENTE (HISTÓRICO)
        // ─────────────────────────────
        val hasRecentPrecipitation =
            recentPrecipitation3h > 0.0 && humidity > 70

        // ─────────────────────────────
        // RESULTADO FINAL
        // ─────────────────────────────
        return isCondensing ||
                isStormPersistence ||
                isExtremelyHumid ||
                hasSnowOrSleet ||
                hasRecentPrecipitation
    }

    private fun checkLowVisibility(visibility: Double?): Pair<Boolean, String?> {
        if (visibility == null) return false to null
        
        return when {
            visibility < 1000 -> true to "Niebla cerrada: Visibilidad < 1km"
            visibility < 3000 -> true to "Visibilidad reducida por neblina"
            else -> false to null
        }
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
        weatherCode: Int? = null,
        visibility: Double? = null
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

        if (isSnowByCode || isSnowByEmoji || isSnowByDescription) {
            return true
        }
        
        // Visibilidad reducida (crítico para Barcelona - niebla/talaia)
        if (visibility != null) {
            val (isLowVisibility, _) = checkLowVisibility(visibility)
            if (isLowVisibility) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Detecta la causa específica de condiciones extremas (misma lógica que StatisticsViewModel)
     * Retorna: "STORM", "SNOW", "GUSTS", "WIND", "COLD", "HEAT", "VISIBILITY" o null
     */
    private fun detectExtremeCause(
        windSpeed: Double?,
        windGusts: Double?,
        temperature: Double?,
        uvIndex: Double?,
        isDay: Boolean,
        weatherEmoji: String?,
        weatherDescription: String?,
        weatherCode: Int? = null,
        visibility: Double? = null
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
        
        // 7. Visibilidad reducida (crítico para Barcelona - niebla/talaia)
        if (visibility != null) {
            val (isLowVisibility, _) = checkLowVisibility(visibility)
            if (isLowVisibility) {
                return "VISIBILITY"
            }
        }
        
        return null
    }

    /**
     * ELIMINADO: resolveEffectiveWeatherCode()
     * Ya no es necesario porque Google Weather API ya devuelve condiciones efectivas.
     * Google usa IA para filtrar radares y determinar si realmente está lloviendo,
     * así que no necesitamos "adivinar" o "derivar" condiciones.
     */

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
            // Mientras esté en tracking activo, chequear el clima cada 5 minutos
            // Esto permite detectar cambios de condiciones más rápido (lluvia, condiciones extremas, etc.)
            // Para una ruta típica de 30-60 min, serán ~6-12 llamadas, que es razonable para Google Weather API

            Log.d(TAG, "⏱️ [Monitoreo continuo] Esperando 5 min para la primera actualización (usando precarga)...")
            kotlinx.coroutines.delay(5 * 60 * 1000)

            while (_trackingState.value is TrackingState.Tracking || 
                   _trackingState.value is TrackingState.Paused) {
                
                val points = _routePoints.value
                // Si acabamos de volver a la pantalla, puede tardar unos segundos en rehidratarse la lista.
                // No esperemos 5 minutos: reintentar rápido hasta tener puntos.
                if (points.isEmpty()) {
                    kotlinx.coroutines.delay(5_000)
                    continue
                }
                if (points.isNotEmpty()) {
                    val currentPoint = points.last()
                    val elapsedMinutes = if (_startTime.value > 0) {
                        ((System.currentTimeMillis() - _startTime.value) / (1000 * 60)).toInt()
                    } else {
                        0
                    }
                    
                    Log.d(TAG, "🌧️ [Monitoreo continuo] Chequeando clima en minuto $elapsedMinutes...")
                    
                    try {
                        val result = weatherRepository.getCurrentWeather(
                            latitude = currentPoint.latitude,
                            longitude = currentPoint.longitude
                        )
                        
                        result.onSuccess { weather ->
                            Log.d(TAG, "✅ [Monitoreo continuo] Clima obtenido: ${weather.temperature}°C, condición=${weather.icon}, precip=${weather.precipitation}mm, humedad=${weather.humidity}%")
                            
                            // 🔥 IMPORTANTE: Actualizar weatherStatus SIEMPRE para que la UI (tarjeta + badge) reaccione.
                            // Antes solo lo actualizábamos si ya era Success; si estaba Idle/Loading/Error, la tarjeta no se refrescaba.
                            val currentStatus = _weatherStatus.value
                            _weatherStatus.value = if (currentStatus is WeatherStatus.Success) {
                                currentStatus.copy(
                                    temperature = weather.temperature,
                                    weatherEmoji = weather.weatherEmoji,
                                    weatherCode = weather.weatherCode,
                                    icon = weather.icon, // Condition string de Google
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
                            
                            // Google Weather API ya devuelve condiciones efectivas
                            val condition = weather.icon.uppercase()
                            val weatherDescription = weather.description // Descripción de Google
                            
                            // Determinar si es lluvia activa usando condición y descripción de Google
                            val (isActiveRain, rainUserReason) = checkActiveRain(
                                condition = condition,
                                description = weatherDescription,
                                precipitation = weather.precipitation
                            )
                            
                            Log.d(TAG, "🔍 [Monitoreo continuo] Detección: isActiveRain=$isActiveRain, razón=$rainUserReason")
                            
                            // Obtener emoji directamente de Google (ya viene correcto)
                            val weatherEmoji = weather.weatherEmoji

                            // Precipitación acumulada reciente (últimas 3h) desde histórico de Google
                            // (solo necesaria si NO hay lluvia activa)
                            val localRecentPrecip3h = if (isActiveRain) 0.0 else getRecentPrecipitation3h(
                                latitude = currentPoint.latitude,
                                longitude = currentPoint.longitude
                            )
                            val localPrecip24h = if (isActiveRain) 0.0 else getRecentPrecipitation24h(
                                latitude = currentPoint.latitude,
                                longitude = currentPoint.longitude
                            )
                            recentPrecipitation3h = maxOf(recentPrecipitation3h, localRecentPrecip3h)
                            weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, weather.precipitation, localRecentPrecip3h)
                            
                            // Calzada húmeda: Solo si NO hay lluvia activa
                            val isWetRoad = if (isActiveRain) {
                                false // Excluir calzada húmeda si hay lluvia activa
                            } else {
                                checkWetRoadConditions(
                                    condition = condition,
                                    humidity = weather.humidity,
                                    recentPrecipitation3h = localRecentPrecip3h,
                                    precip24h = localPrecip24h,
                                    hasActiveRain = isActiveRain,
                                    isDay = weather.isDay,
                                    temperature = weather.temperature,
                                    dewPoint = weather.dewPoint,
                                    weatherEmoji = weatherEmoji,
                                    weatherDescription = weather.description,
                                    windSpeed= weather.windSpeed

                                )
                            }
                            
                            Log.d(TAG, "🛣️ [Monitoreo continuo] Calzada húmeda: isWetRoad=$isWetRoad")
                            
                            // Detectar condiciones extremas
                            // Detectar visibilidad reducida durante monitoreo continuo
                            val (isLowVisibility, visReason) = checkLowVisibility(weather.visibility)
                            
                            val hasExtremeConditions = checkExtremeConditions(
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
                            
                            // Log específico para visibilidad en Barcelona
                            if (isLowVisibility) {
                                Log.d(TAG, "🌫️ [Monitoreo continuo] Visibilidad crítica detectada: ${weather.visibility}m - $visReason")
                            }
                            
                            Log.d(TAG, "⚠️ [Monitoreo continuo] Condiciones extremas: hasExtremeConditions=$hasExtremeConditions")
                            
                            // 🔥 JERARQUÍA DE BADGES (misma lógica que RouteDetailDialog):
                            // 1. Lluvia: Máxima prioridad (siempre se muestra si existe)
                            // 2. Calzada húmeda: Solo si NO hay lluvia (excluyente con lluvia)
                            // 3. Condiciones extremas: COMPLEMENTARIO (puede coexistir con lluvia o calzada húmeda)
                            
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
                                    weatherEmoji = weatherEmoji,
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
                                weatherHadWetRoad = false // Lluvia excluye calzada húmeda
                                // Actualizar flags para mostrar icono en tarjeta del clima durante tracking
                                _shouldShowRainWarning.value = true
                                _isActiveRainWarning.value = true
                                Log.d(TAG, "🌧️ [Monitoreo continuo] Estado actualizado: weatherHadRain=true, weatherHadWetRoad=false")
                            } else if (isWetRoad) {
                                // Calzada húmeda: Solo si NO hay lluvia activa
                                weatherHadWetRoad = true
                                // Actualizar flags para mostrar icono en tarjeta del clima durante tracking
                                _shouldShowRainWarning.value = true
                                _isActiveRainWarning.value = false
                                // 🌧️ Honestidad de datos: No forzar precipitación - usar solo lo que Google devuelve
                                // El badge de calzada húmeda se activará por humedad/punto de rocío, no por valores inventados
                                weatherMaxPrecipitation = maxOf(
                                    weatherMaxPrecipitation ?: 0.0,
                                    weather.precipitation // Usar solo lo que Google realmente reporta
                                )
                                Log.d(TAG, "🛣️ [Monitoreo continuo] Estado actualizado: weatherHadWetRoad=true, precipMax=${weatherMaxPrecipitation}mm (sin forzar valores)")
                            } else {
                                // Si no hay lluvia ni calzada húmeda, mantener los flags si ya estaban activos
                                // (no los reseteamos para mantener el icono visible durante toda la ruta)
                                Log.d(TAG, "☀️ [Monitoreo continuo] Sin lluvia ni calzada húmeda")
                            }
                            
                            // Actualizar flag de condiciones extremas
                            if (hasExtremeConditions) {
                                weatherHadExtremeConditions = true // 🔥 IMPORTANTE: Establecer aquí también cuando aparece durante la ruta
                                _shouldShowExtremeWarning.value = true
                                
                                // Detectar y guardar la causa específica si aún no está establecida
                                val cause = detectExtremeCause(
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
                                if (cause != null && weatherExtremeReason == null) {
                                    // Guardar la primera causa detectada (la más grave por prioridad)
                                    weatherExtremeReason = cause
                                }
                                
                                Log.d(TAG, "⚠️ [Monitoreo continuo] Condiciones extremas detectadas: weatherHadExtremeConditions=true")
                            }
                            
                            // Lógica: solo actualizar si detecta lluvia nueva (para icono)
                            // Si ya había lluvia y ahora no, mantener el icono de lluvia
                            if (isActiveRain) {
                                if (!weatherHadRain) {
                                    // Nueva lluvia detectada - requiere confirmación (2 chequeos seguidos)
                                    if (pendingRainConfirmation && pendingRainMinute != null) {
                                        // Confirmación: segundo chequeo también detecta lluvia
                                        Log.d(TAG, "🌧️ [Monitoreo continuo] Lluvia CONFIRMADA en minuto $elapsedMinutes (detectada primero en minuto $pendingRainMinute): $rainUserReason")
                                        
                                        weatherHadRain = true
                                        weatherHadWetRoad = false // Lluvia es más grave que calzada húmeda
                                        weatherRainStartMinute = pendingRainMinute // Usar el minuto del primer chequeo
                                        weatherRainReason = pendingRainReason ?: rainUserReason
                                        
                                        // Actualizar flags para mostrar icono en tarjeta del clima durante tracking
                                        _shouldShowRainWarning.value = true
                                        _isActiveRainWarning.value = true
                                        
                                        // Nota: weatherStatus ya se actualizó arriba al obtener el clima nuevo
                                        // No es necesario actualizarlo aquí de nuevo
                                        
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
                            
                            // Resumen final del chequeo
                            Log.d(TAG, "📊 [Monitoreo continuo] Resumen: hadRain=$weatherHadRain, hadWetRoad=$weatherHadWetRoad, hadExtreme=$weatherHadExtremeConditions, precipMax=${weatherMaxPrecipitation}mm")
                            
                            // 💾 Guardar SIEMPRE el último clima obtenido (con badges actuales)
                            // para que, si el usuario cambia de pantalla y vuelve, se recupere el
                            // estado MÁS RECIENTE y no solo el clima inicial.
                            routeRepository.saveTempWeather(
                                weather.copy(
                                    shouldShowRainWarning = _shouldShowRainWarning.value,
                                    isActiveRainWarning = _isActiveRainWarning.value,
                                    shouldShowExtremeWarning = _shouldShowExtremeWarning.value
                                )
                            )
                            
                            // 🔔 Detectar cambios en el estado del clima y mostrar notificaciones
                            checkAndNotifyWeatherChange()
                        }.onFailure { error ->
                            Log.w(TAG, "⚠️ [Monitoreo continuo] Error al obtener clima: ${error.message}")
                            // En caso de error, no limpiar pending - esperar al siguiente chequeo
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [Monitoreo continuo] Excepción: ${e.message}", e)
                        // En caso de excepción, no limpiar pending - esperar al siguiente chequeo
                    }
                }
                
                // Esperar 5 minutos antes del siguiente chequeo
                kotlinx.coroutines.delay(5 * 60 * 1000L)
            }
            
            // Limpiar estado pendiente explícitamente al detener el monitoreo
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
     * Crea el canal de notificaciones para cambios de clima
     */
    private fun createWeatherChangeNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WEATHER_CHANGE_CHANNEL_ID,
                "Avisos de Seguridad",
                NotificationManager.IMPORTANCE_HIGH // Prioridad alta para que vibre y llegue al smartwatch
            ).apply {
                description = "Notificaciones cuando cambia el clima durante una ruta activa"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                setSound(null, null) // Sin sonido, solo vibración
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "📢 Canal de notificaciones de cambio de clima creado")
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
     * Muestra una notificación de cambio de clima con vibración
     */
    private fun showWeatherChangeNotification(newState: WeatherBadgeState) {
        try {
            // Crear canal si no existe
            createWeatherChangeNotificationChannel()
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val weatherStatus = _weatherStatus.value
            
            // Obtener texto e icono del badge
            val badgeText = getBadgeText(newState, weatherExtremeReason)
            val iconResId = getBadgeIconResId(newState, weatherStatus)
            
            // Crear patrón de vibración de doble pulso
            val vibrationPattern = longArrayOf(0, 200, 100, 200)
            
            // Intent para abrir la app en la pantalla de tracking
            val intent = Intent(context, com.zipstats.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            // Crear notificación
            val notification = NotificationCompat.Builder(context, WEATHER_CHANGE_CHANNEL_ID)
                .setSmallIcon(iconResId)
                .setContentTitle("Aviso de Seguridad")
                .setContentText(badgeText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION) // Para que el sistema entienda que es información en tiempo real
                .setVibrate(vibrationPattern)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(badgeText))
                .build()
            
            // Vibrar dispositivo (solo si tiene vibrator)
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                
                // Verificar que el vibrator esté disponible
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (vibrator.hasVibrator()) {
                        val vibrationEffect = VibrationEffect.createWaveform(vibrationPattern, -1)
                        vibrator.vibrate(vibrationEffect)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (vibrator.hasVibrator()) {
                        vibrator.vibrate(vibrationPattern, -1)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ No se pudo vibrar el dispositivo: ${e.message}")
            }
            
            // Mostrar notificación
            notificationManager.notify(WEATHER_CHANGE_NOTIFICATION_ID, notification)
            
            Log.d(TAG, "📢 Notificación de cambio de clima mostrada: $badgeText")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al mostrar notificación de cambio de clima: ${e.message}", e)
        }
    }

    /**
     * Detecta cambios en el estado del clima y muestra notificaciones si es necesario
     */
    private fun checkAndNotifyWeatherChange() {
        val currentState = getCurrentWeatherBadgeState()
        val lastState = lastWeatherBadgeState
        
        // Solo notificar si hay un cambio de estado
        if (currentState != lastState && lastState != null) {
            Log.d(TAG, "🔄 Cambio de estado de clima detectado: $lastState -> $currentState")
            showWeatherChangeNotification(currentState)
        }
        
        // Actualizar estado anterior
        lastWeatherBadgeState = currentState
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
            
            // Google Weather API ya devuelve condiciones efectivas, no necesitamos procesarlas
            val condition = snapshot.icon.uppercase()
            val weatherEmoji = snapshot.weatherEmoji
            val weatherDescription = snapshot.description // Ya viene en español de Google
            
            // Determinar si es lluvia activa usando condición y descripción de Google
            val (isActiveRain, rainUserReason) = checkActiveRain(
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
                checkWetRoadConditions(
                    condition = condition,
                    humidity = snapshot.humidity,
                    recentPrecipitation3h = recentPrecipitation3h,
                    precip24h = precip24h,
                    hasActiveRain = isActiveRain,
                    isDay = snapshot.isDay,
                    temperature = snapshot.temperature,
                    dewPoint = snapshot.dewPoint,
                    weatherEmoji = weatherEmoji,
                    weatherDescription = weatherDescription,
                    windSpeed= snapshot.windSpeed

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
            val hasExtremeConditions = checkExtremeConditions(
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
                        val (isActiveRain, rainUserReason) = checkActiveRain(
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
                        recentPrecipitation3h = maxOf(recentPrecipitation3h, localRecentPrecip3h)
                        weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, weather.precipitation, localRecentPrecip3h)

                        // Calzada húmeda: Solo si NO hay lluvia activa
                        val isWetRoad = if (isActiveRain) {
                            false // Excluir calzada húmeda si hay lluvia activa
                        } else {
                            checkWetRoadConditions(
                                condition = condition,
                                humidity = weather.humidity,
                                recentPrecipitation3h = localRecentPrecip3h,
                                precip24h = localPrecip24h,
                                hasActiveRain = isActiveRain,
                                isDay = weather.isDay,
                                temperature = weather.temperature,
                                dewPoint = weather.dewPoint,
                                weatherEmoji = weatherEmoji,
                                weatherDescription = weatherDescription,
                                weather.windSpeed
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
                        val hasExtremeConditions = checkExtremeConditions(
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
                    val (isActiveRain, rainUserReason) = checkActiveRain(
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

    /**
     * Finaliza y guarda la ruta
     */
    fun finishTracking(notes: String = "", addToRecords: Boolean = false) {
        viewModelScope.launch {
            try {
                // 🛑 CORTE DE SEGURIDAD Y LIMPIEZA DE UI 🛑

                // 1. Cancelar la notificación de clima inmediatamente
                try {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(WEATHER_CHANGE_NOTIFICATION_ID)
                    Log.d(TAG, "🧹 Notificación de clima eliminada al finalizar ruta")
                } catch (e: Exception) {
                    Log.e(TAG, "Error al cancelar notificación: ${e.message}")
                }

                // 2. Cancelar jobs de monitoreo (viento, lluvia, etc.)
                continuousWeatherJob?.cancel()
                continuousWeatherJob = null

                // Cancelamos la escucha del estado global.
                globalStateJob?.cancel()
                globalStateJob = null

                // 3. Establecemos estado local GUARDANDO
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
                var savedWeatherCode = _startWeatherCode
                var savedWeatherCondition = _startWeatherCondition
                var savedWeatherDesc = _startWeatherDescription
                var savedIsDay = _startWeatherIsDay ?: true
                var savedFeelsLike = _startWeatherFeelsLike
                var savedWindChill = _startWeatherWindChill
                var savedHeatIndex = _startWeatherHeatIndex
                var savedHumidity = _startWeatherHumidity
                var savedWindSpeed = _startWeatherWindSpeed
                var savedUvIndex = _startWeatherUvIndex
                var savedWindDirection = _startWeatherWindDirection
                var savedWindGusts = _startWeatherWindGusts
                var savedRainProbability = _startWeatherRainProbability
                var savedVisibility = _startWeatherVisibility
                var savedDewPoint = _startWeatherDewPoint
                
                // Validar que el clima sea real y no valores por defecto
                // Aceptar cualquier emoji válido (incluido ☁️) pero temperatura debe ser válida
                var hasValidWeather = savedWeatherTemp != null && 
                                      savedWeatherTemp > -50 && savedWeatherTemp < 60 && // Rango válido de temperatura
                                      savedWeatherEmoji != null && 
                                      savedWeatherEmoji.isNotBlank()
                
                Log.d(TAG, "🔍 Validación clima inicial: temp=$savedWeatherTemp, emoji=$savedWeatherEmoji, válido=$hasValidWeather")
                
                // 🔥 Si hay badges activos (lluvia, calzada húmeda o condiciones extremas),
                // capturar snapshot FINAL del clima actual para guardar el estado completo cuando cambió
                val hasActiveBadges = weatherHadRain || weatherHadWetRoad || weatherHadExtremeConditions
                if (hasActiveBadges && hasValidWeather && points.isNotEmpty()) {
                    Log.d(TAG, "📸 Hay badges activos, capturando snapshot FINAL del clima actual...")
                    val lastPoint = points.last() // Usar el último punto para obtener clima del final de la ruta
                    
                    try {
                        val finalWeatherResult = weatherRepository.getCurrentWeather(
                            latitude = lastPoint.latitude,
                            longitude = lastPoint.longitude
                        )
                        
                        finalWeatherResult.fold(
                            onSuccess = { weather ->
                                // Validar que el clima recibido sea válido
                                if (!weather.temperature.isNaN() && 
                                    !weather.temperature.isInfinite() && 
                                    weather.temperature > -50 && 
                                    weather.temperature < 60 &&
                                    weather.temperature != 0.0 &&
                                    weather.weatherEmoji.isNotBlank()) {
                                    
                                    // Usar el snapshot FINAL del clima cuando hay badges activos
                                    savedWeatherTemp = weather.temperature
                                    savedWeatherEmoji = weather.weatherEmoji
                                    savedWeatherCode = weather.weatherCode
                                    savedWeatherCondition = weather.icon
                                    savedWeatherDesc = weather.description
                                    savedIsDay = weather.isDay
                                    savedFeelsLike = weather.feelsLike
                                    savedWindChill = weather.windChill
                                    savedHeatIndex = weather.heatIndex
                                    savedHumidity = weather.humidity
                                    savedWindSpeed = weather.windSpeed
                                    savedUvIndex = weather.uvIndex
                                    savedWindDirection = weather.windDirection
                                    savedWindGusts = weather.windGusts
                                    savedRainProbability = weather.rainProbability
                                    savedVisibility = weather.visibility
                                    savedDewPoint = weather.dewPoint
                                    
                                    // 🔥 IMPORTANTE: Los badges se basan en lo detectado durante la ruta, no en el clima final
                                    // Si las condiciones cambian al final (ej: deja de llover), los badges siguen activos
                                    // porque reflejan lo que pasó durante la ruta (weatherHadRain, weatherHadWetRoad, etc.)
                                    // El snapshot final solo actualiza los datos del clima, pero los badges ya están establecidos
                                    Log.d(TAG, "✅ Snapshot FINAL capturado: ${savedWeatherTemp}°C ${savedWeatherEmoji} (badges detectados durante ruta: lluvia=$weatherHadRain, calzada=$weatherHadWetRoad, extremas=$weatherHadExtremeConditions)")
                                } else {
                                    Log.w(TAG, "⚠️ Snapshot final obtenido pero inválido, usando clima inicial")
                                }
                            },
                            onFailure = { error ->
                                Log.w(TAG, "⚠️ Error al obtener snapshot final del clima: ${error.message}, usando clima inicial")
                            }
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Excepción al obtener snapshot final del clima: ${e.message}, usando clima inicial", e)
                    }
                }
                
                // Si no hay clima válido al finalizar, intentar obtenerlo una última vez
                if (!hasValidWeather && points.isNotEmpty()) {
                    Log.d(TAG, "🌤️ No hay clima válido al finalizar, intentando obtenerlo...")
                    val firstPoint = points.first()
                    
                    try {
                        _weatherStatus.value = WeatherStatus.Loading
                        
                        val weatherResult = weatherRepository.getCurrentWeather(
                            latitude = firstPoint.latitude,
                            longitude = firstPoint.longitude
                        )
                        
                        // Verificar el resultado (getCurrentWeather devuelve Result, así que verificamos directamente)
                        weatherResult.fold(
                            onSuccess = { weather ->
                                // Google Weather API ya devuelve condiciones efectivas, no necesitamos procesarlas
                                val condition = weather.icon.uppercase()
                                val weatherEmoji = weather.weatherEmoji
                                val weatherDescription = weather.description // Ya viene en español de Google
                                
                                // Validar que el clima recibido sea válido
                                if (!weather.temperature.isNaN() && 
                                    !weather.temperature.isInfinite() && 
                                    weather.temperature > -50 && 
                                    weather.temperature < 60 &&
                                    weather.temperature != 0.0 &&
                                    weatherEmoji.isNotBlank()) {
                                    
                                    savedWeatherTemp = weather.temperature
                                    savedWeatherEmoji = weatherEmoji
                                    savedWeatherCode = weather.weatherCode
                                    savedWeatherCondition = weather.icon
                                    savedWeatherDesc = weatherDescription
                                    savedIsDay = weather.isDay
                                    savedFeelsLike = weather.feelsLike
                                    savedWindChill = weather.windChill
                                    savedHeatIndex = weather.heatIndex
                                    savedHumidity = weather.humidity
                                    savedWindSpeed = weather.windSpeed
                                    savedUvIndex = _startWeatherUvIndex
                                    savedWindDirection = _startWeatherWindDirection
                                    savedWindGusts = _startWeatherWindGusts
                                    savedRainProbability = _startWeatherRainProbability
                                    savedVisibility = weather.visibility
                                    savedDewPoint = weather.dewPoint
                                    hasValidWeather = true
                                    
                                    // Determinar si es lluvia activa usando condición y descripción de Google
                                    val (isActiveRain, rainUserReason) = checkActiveRain(
                                        condition = condition,
                                        description = weatherDescription,
                                        precipitation = weather.precipitation
                                    )
                                    
                                    if (isActiveRain) {
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
                                    
                                    Log.d(TAG, "✅ Clima obtenido al finalizar: ${savedWeatherTemp}°C $weatherEmoji")
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
                
                // 🔥 Si hay badges activos, ya se capturó un snapshot FINAL del clima (ver arriba)
                // Por lo tanto, savedWeather* ya contiene el clima final cuando hay badges activos
                // Solo necesitamos aplicar lógica de valores extremos para viento/UV si aplica
                val hadExtremeConditionsDuringRoute = weatherHadExtremeConditions
                val useMaxValuesForExtremes = maxWindSpeed > 0.0 || maxWindGusts > 0.0 || 
                    (minTemperature < Double.MAX_VALUE && (minTemperature < 0 || minTemperature > 35)) ||
                    (maxTemperature > Double.MIN_VALUE && maxTemperature > 35) ||
                    maxUvIndex > 8.0
                
                // hasActiveBadges ya se declaró arriba cuando se capturó el snapshot final
                
                // Calcular temperatura final: aplicar lógica de valores extremos solo si no hay snapshot final
                val finalTemperature = if (weatherHadExtremeConditions && !hasActiveBadges &&
                    (minTemperature < Double.MAX_VALUE || maxTemperature > Double.MIN_VALUE)) {
                    when {
                        minTemperature < 0 -> minTemperature // Temperatura bajo cero
                        maxTemperature > 35 -> maxTemperature // Temperatura muy alta
                        else -> savedWeatherTemp // Mantener temperatura inicial si no es extrema
                    }
                } else {
                    savedWeatherTemp // Usar snapshot final si hay badges, o inicial si no
                }
                
                // Para viento: aplicar lógica de valores extremos solo si no hay snapshot final
                val finalWindSpeedCorrected = if (weatherHadExtremeConditions && !hasActiveBadges && maxWindSpeed > 0.0) {
                    // Se detectaron durante la ruta sin snapshot final: usar el máximo
                    maxOf(savedWindSpeedKmh, maxWindSpeed)
                } else {
                    // Si hay snapshot final (hasActiveBadges), usar el valor del snapshot (ya convertido arriba)
                    // Si no hay extremas, usar el valor inicial convertido
                    if (savedWindSpeed != null) savedWindSpeedKmh else null
                }
                
                val finalWindGustsCorrected = if (weatherHadExtremeConditions && !hasActiveBadges && maxWindGusts > 0.0) {
                    // Se detectaron durante la ruta sin snapshot final: usar el máximo
                    maxOf(savedWindGustsKmh, maxWindGusts)
                } else {
                    // Si hay snapshot final (hasActiveBadges), usar el valor del snapshot (ya convertido arriba)
                    // Si no hay extremas, usar el valor inicial convertido
                    if (savedWindGusts != null) savedWindGustsKmh else null
                }
                
                // Para UV: aplicar lógica de valores extremos solo si no hay snapshot final
                val finalUvIndexCorrected = if (weatherHadExtremeConditions && !hasActiveBadges && maxUvIndex > 0.0) {
                    maxOf(savedUvIndex ?: 0.0, maxUvIndex)
                } else {
                    savedUvIndex // Usar snapshot final si hay badges, o inicial si no
                }
                
                // El resto de variables vienen directamente del snapshot (final si hay badges, inicial si no)
                val finalEmoji = savedWeatherEmoji
                val finalWeatherCode = savedWeatherCode
                val finalWeatherCondition = savedWeatherCondition
                val finalDescription = savedWeatherDesc
                val finalIsDay = savedIsDay
                val finalFeelsLike = savedFeelsLike
                val finalWindChill = savedWindChill
                val finalHeatIndex = savedHeatIndex
                val finalHumidity = savedHumidity
                val finalRainProbability = savedRainProbability
                val finalVisibility = savedVisibility
                val finalDewPoint = savedDewPoint
                val finalWindDirection = savedWindDirection
                
                // 🔥 CORRECCIÓN: Calcular valores finales de badges ANTES del copy()
                // Sincronizar weatherHadRain con el estado de los badges
                // Si el badge de lluvia está activo (_shouldShowRainWarning=true y _isActiveRainWarning=true),
                // entonces weatherHadRain debe ser true, incluso si el monitoreo continuo no lo detectó
                // IMPORTANTE: Los badges se basan en lo detectado durante la ruta (weatherHadRain),
                // no en el clima final del snapshot. Si las condiciones cambian al final, los badges
                // siguen reflejando lo que pasó durante la ruta.
                val finalHadRain = weatherHadRain || 
                    (_shouldShowRainWarning.value && _isActiveRainWarning.value)
                Log.d(TAG, "💾 Guardando ruta: weatherHadRain=$finalHadRain (detectado=$weatherHadRain, badge activo=${_shouldShowRainWarning.value && _isActiveRainWarning.value})")
                
                // Sincronizar weatherHadWetRoad con el estado de los badges
                // Si el badge de calzada húmeda está activo (_shouldShowRainWarning=true y _isActiveRainWarning=false),
                // entonces weatherHadWetRoad debe ser true, incluso si el monitoreo continuo no lo detectó
                // IMPORTANTE: Una vez que weatherHadWetRoad es true, solo se puede resetear si hay lluvia activa
                // Los badges se basan en lo detectado durante la ruta, no en el clima final del snapshot
                val finalWetRoad = if (finalHadRain) {
                    // Si hubo lluvia activa, no puede haber calzada húmeda (son excluyentes)
                    Log.d(TAG, "💾 Guardando ruta: weatherHadRain=true, weatherHadWetRoad=false (lluvia excluye calzada húmeda)")
                    false
                } else {
                    // Si no hubo lluvia, mantener el estado detectado O sincronizar con badges activos
                    val wetRoadValue = weatherHadWetRoad || 
                        (_shouldShowRainWarning.value && !_isActiveRainWarning.value)
                    Log.d(TAG, "💾 Guardando ruta: weatherHadWetRoad=$wetRoadValue (detectado=$weatherHadWetRoad, badge activo=${_shouldShowRainWarning.value && !_isActiveRainWarning.value})")
                    wetRoadValue
                }
                
                // Sincronizar weatherHadExtremeConditions con el estado de los badges
                // Si el badge de condiciones extremas está activo (_shouldShowExtremeWarning=true),
                // entonces weatherHadExtremeConditions debe ser true, incluso si el monitoreo continuo no lo detectó
                // IMPORTANTE: Los badges se basan en lo detectado durante la ruta, no en el clima final del snapshot
                val finalHadExtreme = hadExtremeConditionsDuringRoute || 
                    _shouldShowExtremeWarning.value
                Log.d(TAG, "💾 Guardando ruta: weatherHadExtremeConditions=$finalHadExtreme (detectado=$hadExtremeConditionsDuringRoute, badge activo=${_shouldShowExtremeWarning.value})")
                
                val route = if (hasValidWeather) {
                    if (hasActiveBadges) {
                        Log.d(TAG, "✅ Usando snapshot FINAL del clima (badges activos): ${finalEmoji} ${finalTemperature}°C")
                    } else {
                        Log.d(TAG, "✅ Usando snapshot INICIAL del clima: ${finalTemperature}°C ${finalEmoji}")
                    }
                    if (hadExtremeConditionsDuringRoute) {
                        if (useMaxValuesForExtremes) {
                            Log.d(TAG, "⚠️ Ajustando datos de clima con valores extremos detectados durante la ruta")
                        } else {
                            Log.d(TAG, "⚠️ Condiciones extremas detectadas en precarga - badge reflejará estado más adverso")
                        }
                    }
                    baseRoute.copy(
                        weatherTemperature = finalTemperature,
                        weatherEmoji = finalEmoji,
                        weatherCode = finalWeatherCode,
                        weatherCondition = finalWeatherCondition,
                        weatherDescription = finalDescription,
                        weatherIsDay = finalIsDay,
                        weatherFeelsLike = finalFeelsLike,
                        weatherWindChill = finalWindChill,
                        weatherHeatIndex = finalHeatIndex,
                        weatherHumidity = finalHumidity,
                        weatherWindSpeed = finalWindSpeedCorrected,
                        weatherUvIndex = finalUvIndexCorrected,
                        weatherWindDirection = finalWindDirection,
                        weatherWindGusts = finalWindGustsCorrected,
                        weatherRainProbability = finalRainProbability,
                        weatherVisibility = finalVisibility,
                        weatherDewPoint = finalDewPoint,
                        weatherHadRain = finalHadRain,
                        weatherRainStartMinute = weatherRainStartMinute,
                        // 🌧️ Honestidad de datos: Usar exactamente lo que Google devuelve
                        // No forzar precipitación si no la hubo - el badge de "Calzada húmeda"
                        // se activará por humedad y punto de rocío, no por valores inventados
                        // Esto mantiene las estadísticas precisas (0.0 mm = no llovió realmente)
                        weatherMaxPrecipitation = if (finalHadRain && weatherMaxPrecipitation > 0.0) {
                            weatherMaxPrecipitation
                        } else {
                            null // Si no hubo lluvia durante la ruta, no guardar mm residuales
                        },
                        weatherRainReason = weatherRainReason,
                        weatherHadWetRoad = finalWetRoad,
                        weatherHadExtremeConditions = finalHadExtreme,
                        weatherExtremeReason = if (finalHadExtreme) weatherExtremeReason else null
                    )
                } else {
                    Log.w(TAG, "⚠️ No se capturó clima válido al inicio, guardando ruta SIN clima (temp=$savedWeatherTemp, emoji=$savedWeatherEmoji)")
                    // Asegurar explícitamente que los campos de clima sean null
                    baseRoute.copy(
                        weatherTemperature = null,
                        weatherEmoji = null,
                        weatherCode = null,
                        weatherCondition = null,
                        weatherDescription = null,
                        weatherIsDay = true,
                        weatherFeelsLike = null,
                        weatherWindChill = null,
                        weatherHeatIndex = null,
                        weatherHumidity = null,
                        weatherWindSpeed = null,
                        weatherUvIndex = null,
                        weatherWindDirection = null,
                        weatherWindGusts = null,
                        weatherRainProbability = null,
                        weatherVisibility = null,
                        weatherDewPoint = null,
                        weatherHadRain = null,
                        weatherRainStartMinute = null,
                        weatherMaxPrecipitation = null,
                        weatherRainReason = null,
                        weatherHadWetRoad = null,
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
                _initialWeatherLatitude = null
                _initialWeatherLongitude = null
                
                // 🔥 Limpiar TODOS los badges al finalizar
                _shouldShowRainWarning.value = false
                _isActiveRainWarning.value = false
                _shouldShowExtremeWarning.value = false
                
                // 🔔 Resetear estado anterior del clima al finalizar tracking
                lastWeatherBadgeState = null
                Log.d(TAG, "🔄 Estado del clima y badges reseteados al finalizar tracking")

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
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(WEATHER_CHANGE_NOTIFICATION_ID)
            Log.d(TAG, "🧹 Notificación de clima limpiada en onCleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error al limpiar notificación: ${e.message}")
        }

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

