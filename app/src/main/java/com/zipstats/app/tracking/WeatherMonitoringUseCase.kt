package com.zipstats.app.tracking

import android.content.Context
import android.location.Location
import android.util.Log
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.model.RouteWeatherSnapshot
import com.zipstats.app.model.SurfaceConditionType
import com.zipstats.app.repository.RouteRepository
import com.zipstats.app.repository.SettingsRepository
import com.zipstats.app.repository.WeatherData
import com.zipstats.app.repository.WeatherRepository
import com.zipstats.app.ui.tracking.TrackingNotificationHandler
import com.zipstats.app.ui.tracking.WeatherAdvisor
import com.zipstats.app.utils.LocationUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class WeatherMonitoringUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val weatherRepository: WeatherRepository,
    private val routeRepository: RouteRepository,
    private val settingsRepository: SettingsRepository,
) {

    private val weatherAdvisor = WeatherAdvisor()
    private val notificationHandler = TrackingNotificationHandler(context)

    private val settingsCollectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Snapshot inicial del clima (capturado en precarga, antes de iniciar ruta)
    private var _initialWeatherSnapshot: WeatherData? = null
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

    private val _shouldShowRainWarning = MutableStateFlow(false)
    val shouldShowRainWarning: StateFlow<Boolean> = _shouldShowRainWarning.asStateFlow()

    private val _isActiveRainWarning = MutableStateFlow(false)
    val isActiveRainWarning: StateFlow<Boolean> = _isActiveRainWarning.asStateFlow()

    private val _shouldShowExtremeWarning = MutableStateFlow(false)
    val shouldShowExtremeWarning: StateFlow<Boolean> = _shouldShowExtremeWarning.asStateFlow()

    private var lastWeatherBadgeState: WeatherBadgeState? = null

    private var weatherHadRain = false
    private var weatherRainStartMinute: Int? = null
    private var weatherMaxPrecipitation = 0.0
    private var weatherRainReason: String? = null
    private var pendingRainConfirmation: Boolean = false
    private var pendingRainMinute: Int? = null
    private var pendingRainReason: String? = null

    private var weatherHadWetRoad = false
    private var weatherHadExtremeConditions = false

    private var sessionClimateRegion: WeatherAdvisor.ClimateRegion? = null
    private var userClimateRegionOverride: WeatherAdvisor.ClimateRegion? = null
    private var weatherExtremeReason: String? = null

    private var recentPrecipitation3h: Double = 0.0

    private var maxWindSpeed = 0.0
    private var maxWindGusts = 0.0
    private var minTemperature = Double.MAX_VALUE
    private var maxTemperature = Double.MIN_VALUE
    private var maxUvIndex = 0.0

    private val _weatherStatus = MutableStateFlow<WeatherStatus>(WeatherStatus.Idle)
    val weatherStatus: StateFlow<WeatherStatus> = _weatherStatus.asStateFlow()

    private var weatherJob: kotlinx.coroutines.Job? = null
    private var continuousWeatherJob: kotlinx.coroutines.Job? = null

    val isInitialWeatherCaptured: Boolean
        get() = _initialWeatherCaptured

    init {
        settingsCollectorScope.launch {
            settingsRepository.climateRegionPreferenceFlow.collect { preference ->
                val newOverride = preference.toClimateRegionOrNull()
                if (newOverride != userClimateRegionOverride) {
                    userClimateRegionOverride = newOverride
                    sessionClimateRegion = null
                }
            }
        }
    }

    fun hasStartWeatherCaptured(): Boolean = _startWeatherTemperature != null

    fun hasActiveBadges(): Boolean =
        weatherHadRain || weatherHadWetRoad || weatherHadExtremeConditions

    fun dismissRainWarning() {
        _shouldShowRainWarning.value = false
    }

    fun dismissExtremeWarning() {
        _shouldShowExtremeWarning.value = false
    }

    fun dismissNotification() {
        notificationHandler.dismissWeatherNotification()
    }

    fun resetBadgesOnNewRoute() {
        _shouldShowRainWarning.value = false
        _isActiveRainWarning.value = false
        _shouldShowExtremeWarning.value = false
        lastWeatherBadgeState = null
        Log.d(TAG, "🔄 Badges reseteados al iniciar nueva ruta")
    }

    fun captureInitialWeather(scope: CoroutineScope, preLocation: Location) {
        if (_initialWeatherCaptured) {
            Log.d(TAG, "🌤️ Clima inicial ya capturado, omitiendo")
            return
        }

        Log.d(
            TAG,
            "🌤️ [Precarga] Capturando clima inicial en lat=${preLocation.latitude}, lon=${preLocation.longitude}"
        )

        weatherJob?.cancel()

        weatherJob = scope.launch {
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

                    _initialWeatherSnapshot = weather
                    _initialWeatherCaptured = true
                    _initialWeatherLatitude = preLocation.latitude
                    _initialWeatherLongitude = preLocation.longitude

                    _weatherStatus.value = toWeatherStatusSuccess(weather)

                    weatherMaxPrecipitation =
                        maxOf(weatherMaxPrecipitation, weather.precipitation)

                    recentPrecipitation3h = getRecentPrecipitation3h(
                        latitude = preLocation.latitude,
                        longitude = preLocation.longitude
                    )
                    weatherMaxPrecipitation =
                        maxOf(weatherMaxPrecipitation, recentPrecipitation3h)

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

                    val precip24h = if (isActiveRain) 0.0 else getRecentPrecipitation24h(
                        latitude = preLocation.latitude,
                        longitude = preLocation.longitude
                    )

                    val isWetRoad = if (isActiveRain) {
                        false
                    } else {
                        weatherAdvisor.checkWetRoadConditions(
                            condition = condition,
                            humidity = weather.humidity,
                            recentPrecipitation3h = recentPrecipitation3h,
                            precip24h = precip24h,
                            hasActiveRain = false,
                            isDay = weather.isDay,
                            temperature = weather.temperature,
                            dewPoint = weather.dewPoint,
                            weatherEmoji = weatherEmoji,
                            weatherDescription = weatherDescription,
                            windSpeed = weather.windSpeed,
                            climateRegion = resolveClimateRegion(
                                latitude = preLocation.latitude,
                                longitude = preLocation.longitude
                            ),
                            latitude = preLocation.latitude,
                            longitude = preLocation.longitude
                        )
                    }

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

                    if (isActiveRain) {
                        weatherHadRain = true
                        weatherRainStartMinute = 0
                        weatherRainReason = rainUserReason
                        weatherHadWetRoad = false
                        _shouldShowRainWarning.value = true
                        _isActiveRainWarning.value = true
                        Log.d(TAG, "🌧️ [Precarga] Lluvia activa al inicio de la ruta")
                    } else if (isWetRoad) {
                        weatherHadWetRoad = true
                        _shouldShowRainWarning.value = true
                        _isActiveRainWarning.value = false
                        Log.d(TAG, "🛣️ [Precarga] Calzada húmeda detectada")
                    }

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

    suspend fun captureStartWeather(
        scope: CoroutineScope,
        routePoints: StateFlow<List<RoutePoint>>,
        preLocation: Location?,
    ) {
        val snapshot = _initialWeatherSnapshot
        if (snapshot != null && _initialWeatherCaptured) {
            Log.d(TAG, "♻️ Reutilizando clima inicial capturado en precarga")

            val condition = snapshot.icon.uppercase()
            val weatherEmoji = snapshot.weatherEmoji
            val weatherDescription = snapshot.description

            val (isActiveRain, rainUserReason) = weatherAdvisor.checkActiveRain(
                condition = condition,
                description = weatherDescription,
                precipitation = snapshot.precipitation
            )

            val isWetRoad = if (isActiveRain) {
                false
            } else {
                val (lat, lon) = when {
                    _initialWeatherLatitude != null && _initialWeatherLongitude != null ->
                        _initialWeatherLatitude!! to _initialWeatherLongitude!!
                    preLocation != null ->
                        preLocation.latitude to preLocation.longitude
                    else -> null
                } ?: run {
                    Log.w(TAG, "⚠️ No hay coordenadas disponibles para calcular precip24h (snapshot inicial). Se omite regla de 24h.")
                    0.0 to 0.0
                }

                val precip24h = if (lat == 0.0 && lon == 0.0) 0.0 else getRecentPrecipitation24h(
                    latitude = lat,
                    longitude = lon
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
                    weatherEmoji = weatherEmoji,
                    weatherDescription = weatherDescription,
                    windSpeed = snapshot.windSpeed,
                    climateRegion = if (lat == 0.0 && lon == 0.0) {
                        resolveClimateRegionWithoutGps()
                    } else {
                        resolveClimateRegion(latitude = lat, longitude = lon)
                    },
                    latitude = if (lat == 0.0 && lon == 0.0) null else lat,
                    longitude = if (lat == 0.0 && lon == 0.0) null else lon
                )
            }

            if (isActiveRain) {
                weatherHadRain = true
                weatherHadWetRoad = false
                _shouldShowRainWarning.value = true
                _isActiveRainWarning.value = true
            } else if (isWetRoad) {
                weatherHadWetRoad = true
                _shouldShowRainWarning.value = true
                _isActiveRainWarning.value = false
                Log.d(TAG, "🛣️ [Precarga inicial] Estado actualizado: weatherHadWetRoad=true")
            } else {
                _shouldShowRainWarning.value = false
                _isActiveRainWarning.value = false
                Log.d(TAG, "🌤️ [Precarga inicial] Sin lluvia ni calzada húmeda: badge de lluvia desactivado")
            }

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
                weatherHadExtremeConditions = true
                _shouldShowExtremeWarning.value = true
                Log.d(TAG, "⚠️ [Precarga inicial] Estado actualizado: weatherHadExtremeConditions=true")
            }

            if (isActiveRain) {
                if (!weatherHadRain) {
                    weatherHadRain = true
                    weatherRainStartMinute = 0
                    weatherRainReason = rainUserReason
                }
                weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, snapshot.precipitation)
            }

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

            lastWeatherBadgeState = getCurrentWeatherBadgeState()
            Log.d(TAG, "🌤️ [Inicio de ruta] Estado inicial del clima establecido: $lastWeatherBadgeState")

            routeRepository.saveTempWeather(
                snapshot.copy(
                    shouldShowRainWarning = _shouldShowRainWarning.value,
                    isActiveRainWarning = _isActiveRainWarning.value,
                    shouldShowExtremeWarning = _shouldShowExtremeWarning.value
                )
            )

            _weatherStatus.value = toWeatherStatusSuccess(snapshot)

            Log.d(TAG, "✅ Clima inicial reutilizado: ${snapshot.temperature}°C $weatherEmoji")
            return
        }

        Log.d(TAG, "🌤️ No hay snapshot inicial, capturando clima normalmente")

        weatherJob?.cancel()

        weatherJob = scope.launch {
            try {
                _weatherStatus.value = WeatherStatus.Loading

                val startTime = System.currentTimeMillis()
                Log.d(TAG, "🌤️ [Paso 1/2] Esperando primer punto GPS (hasta 30s)...")
                var attempts = 0
                var points = routePoints.value

                while (points.isEmpty() && attempts < 60) {
                    delay(500)
                    points = routePoints.value
                    attempts++
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                    if (attempts % 10 == 0) {
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

                val maxRetries = 5
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
                        val condition = weather.icon.uppercase()
                        val weatherEmoji = weather.weatherEmoji
                        val weatherDescription = weather.description

                        if (weather.temperature.isNaN() ||
                            weather.temperature.isInfinite() ||
                            weather.temperature < -50 ||
                            weather.temperature > 60
                        ) {
                            Log.w(TAG, "⚠️ Clima recibido con temperatura inválida: ${weather.temperature}°C. Reintentando...")
                            if (retryCount < maxRetries) {
                                val delayMs = when (retryCount) {
                                    1 -> 5000L
                                    2 -> 8000L
                                    3 -> 10000L
                                    4 -> 12000L
                                    else -> 15000L
                                }
                                Log.d(TAG, "⏳ Reintentando en ${delayMs / 1000}s...")
                                delay(delayMs)
                            } else {
                                _weatherStatus.value = WeatherStatus.Error("Temperatura inválida recibida")
                                Log.e(TAG, "❌ Todos los intentos agotados. Temperatura inválida.")
                                return@launch
                            }
                            return@onSuccess
                        }

                        if (weatherEmoji.isBlank()) {
                            Log.w(TAG, "⚠️ Clima recibido con emoji vacío. Reintentando...")
                            if (retryCount < maxRetries) {
                                val delayMs = when (retryCount) {
                                    1 -> 5000L
                                    2 -> 8000L
                                    3 -> 10000L
                                    4 -> 12000L
                                    else -> 15000L
                                }
                                Log.d(TAG, "⏳ Reintentando en ${delayMs / 1000}s...")
                                delay(delayMs)
                            } else {
                                _weatherStatus.value = WeatherStatus.Error("Emoji de clima vacío")
                                Log.e(TAG, "❌ Todos los intentos agotados. Emoji vacío.")
                                return@launch
                            }
                            return@onSuccess
                        }

                        val (isActiveRain, rainUserReason) = weatherAdvisor.checkActiveRain(
                            condition = condition,
                            description = weatherDescription,
                            precipitation = weather.precipitation
                        )

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

                        val isWetRoad = if (isActiveRain) {
                            false
                        } else {
                            weatherAdvisor.checkWetRoadConditions(
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
                                windSpeed = weather.windSpeed,
                                climateRegion = resolveClimateRegion(
                                    latitude = firstPoint.latitude,
                                    longitude = firstPoint.longitude
                                ),
                                latitude = firstPoint.latitude,
                                longitude = firstPoint.longitude
                            )
                        }

                        if (isActiveRain) {
                            _shouldShowRainWarning.value = true
                            _isActiveRainWarning.value = true
                        } else if (isWetRoad) {
                            _shouldShowRainWarning.value = true
                            _isActiveRainWarning.value = false
                        } else {
                            _shouldShowRainWarning.value = false
                            _isActiveRainWarning.value = false
                        }

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
                                weatherRainStartMinute = 0
                                weatherRainReason = rainUserReason
                            }
                            weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, weather.precipitation)
                        }

                        _startWeatherTemperature = weather.temperature
                        _startWeatherEmoji = weatherEmoji
                        _startWeatherCode = weather.weatherCode
                        _startWeatherCondition = weather.icon
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

                        routeRepository.saveTempWeather(
                            weather.copy(
                                shouldShowRainWarning = _shouldShowRainWarning.value,
                                isActiveRainWarning = _isActiveRainWarning.value,
                                shouldShowExtremeWarning = _shouldShowExtremeWarning.value
                            )
                        )

                        _weatherStatus.value = toWeatherStatusSuccess(weather)

                        success = true

                        val elapsedMs = System.currentTimeMillis() - startApiTime
                        Log.d(TAG, "✅ Clima capturado y VALIDADO en ${elapsedMs}ms: ${weather.temperature}°C $weatherEmoji")
                        Log.d(TAG, "✅ Descripción: $weatherDescription")
                        Log.d(TAG, "✅ Precipitación: ${weather.precipitation}mm, Rain: ${weather.rain}mm, Showers: ${weather.showers}mm")
                        Log.d(TAG, "✅ Humedad: ${weather.humidity}%, Prob. lluvia: ${weather.rainProbability}%, Viento: ${weather.windSpeed} km/h")
                    }.onFailure { error ->
                        Log.e(TAG, "❌ Error en intento ${retryCount}/${maxRetries}: ${error.message}")

                        if (retryCount < maxRetries) {
                            val delayMs = when (retryCount) {
                                1 -> 5000L
                                2 -> 8000L
                                3 -> 10000L
                                4 -> 12000L
                                else -> 15000L
                            }
                            Log.d(TAG, "⏳ Reintentando en ${delayMs / 1000}s...")
                            delay(delayMs)
                        } else {
                            val totalElapsed = (System.currentTimeMillis() - startApiTime) / 1000
                            val totalTimeFromStart = (System.currentTimeMillis() - startTime) / 1000
                            _weatherStatus.value = WeatherStatus.Error(
                                error.message ?: "Error al obtener clima"
                            )
                            Log.e(TAG, "❌ Intento ${retryCount}/${maxRetries} falló después de ${totalElapsed}s de API (${totalTimeFromStart}s desde inicio). Clima no disponible. Botón clicable activado.")
                        }
                    }
                }

                if (!success && _weatherStatus.value is WeatherStatus.Loading) {
                    _weatherStatus.value = WeatherStatus.NotAvailable
                    Log.w(TAG, "⚠️ Clima no obtenido después de todos los intentos. Botón clicable activado.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción al capturar clima: ${e.message}", e)
                if (_weatherStatus.value is WeatherStatus.Loading) {
                    _weatherStatus.value = WeatherStatus.Error("Excepción: ${e.message}")
                }
            }
        }
    }

    fun startContinuousMonitoring(
        scope: CoroutineScope,
        routePoints: StateFlow<List<RoutePoint>>,
        startTime: StateFlow<Long>,
        isSessionActive: () -> Boolean,
    ) {
        continuousWeatherJob?.cancel()

        pendingRainConfirmation = false
        pendingRainMinute = null
        pendingRainReason = null

        val firstCheckDelayMs = 60_000L
        val regularCheckDelayMs = 5 * 60 * 1000L

        continuousWeatherJob = scope.launch {
            Log.d(
                TAG,
                "⏱️ [Monitoreo continuo] Esperando 1 min para primera verificación temprana..."
            )
            delay(firstCheckDelayMs)

            while (isSessionActive()) {
                val points = routePoints.value

                if (points.isEmpty()) {
                    delay(5_000)
                    continue
                }

                val currentPoint = points.last()
                val elapsedMinutes =
                    if (startTime.value > 0)
                        ((System.currentTimeMillis() - startTime.value) / (1000 * 60)).toInt()
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
                                toWeatherStatusSuccess(weather)
                            }

                        val condition = weather.icon.uppercase()
                        val weatherDescription = weather.description
                        val weatherEmoji = weather.weatherEmoji

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

                        val isWetRoad =
                            if (isActiveRain) {
                                false
                            } else {
                                weatherAdvisor.checkWetRoadConditions(
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
                                    windSpeed = weather.windSpeed,
                                    climateRegion = resolveClimateRegion(
                                        latitude = currentPoint.latitude,
                                        longitude = currentPoint.longitude
                                    ),
                                    latitude = currentPoint.latitude,
                                    longitude = currentPoint.longitude
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

                        val (isLowVisibility, visReason) =
                            weatherAdvisor.checkLowVisibility(weather.visibility)

                        if (isLowVisibility) {
                            Log.d(
                                TAG,
                                "🌫️ [Monitoreo continuo] Visibilidad crítica: ${weather.visibility}m - $visReason"
                            )
                        }

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

            pendingRainConfirmation = false
            pendingRainMinute = null
            pendingRainReason = null

            Log.d(TAG, "🌧️ [Monitoreo continuo] Detenido (tracking finalizado)")
        }
    }

    fun ensureContinuousMonitoringRunning(
        scope: CoroutineScope,
        routePoints: StateFlow<List<RoutePoint>>,
        startTime: StateFlow<Long>,
        isSessionActive: () -> Boolean,
    ) {
        if (!isSessionActive()) return
        if (continuousWeatherJob?.isActive == true) return

        Log.d(TAG, "🌧️ Reanudando monitoreo continuo del clima (job no activo)")
        startContinuousMonitoring(scope, routePoints, startTime, isSessionActive)
    }

    fun cancelContinuousMonitoring() {
        continuousWeatherJob?.cancel()
        continuousWeatherJob = null
    }

    fun cancelWeatherJob() {
        weatherJob?.cancel()
        weatherJob = null
    }

    suspend fun fetchWeatherManually(
        routePoints: List<RoutePoint>,
        startTimeMs: Long,
        onMessage: (String) -> Unit,
    ) {
        try {
            if (routePoints.isEmpty()) {
                Log.w(TAG, "⚠️ No hay puntos GPS para obtener clima manualmente")
                onMessage("Espera a tener puntos GPS")
                return
            }

            if (_startWeatherTemperature != null) {
                Log.d(TAG, "✅ Ya hay clima guardado, no es necesario obtenerlo de nuevo")
                return
            }

            Log.d(TAG, "🌤️ Usuario solicitó obtener clima manualmente")
            val firstPoint = routePoints.first()

            _weatherStatus.value = WeatherStatus.Loading

            val result = weatherRepository.getCurrentWeather(
                latitude = firstPoint.latitude,
                longitude = firstPoint.longitude
            )

            result.onSuccess { weather ->
                val condition = weather.icon.uppercase()
                val weatherEmoji = weather.weatherEmoji
                val weatherDescription = weather.description

                if (weather.temperature.isNaN() ||
                    weather.temperature.isInfinite() ||
                    weather.temperature < -50 ||
                    weather.temperature > 60
                ) {
                    Log.e(TAG, "⚠️ Clima recibido con temperatura inválida: ${weather.temperature}°C. NO se guardará.")
                    _weatherStatus.value = WeatherStatus.Error("Temperatura inválida recibida")
                    onMessage("Temperatura inválida recibida")
                    return
                }

                if (weatherEmoji.isBlank()) {
                    Log.e(TAG, "⚠️ Clima recibido con emoji vacío. NO se guardará.")
                    _weatherStatus.value = WeatherStatus.Error("Emoji de clima vacío")
                    onMessage("Emoji de clima vacío")
                    return
                }

                val (isActiveRain, rainUserReason) = weatherAdvisor.checkActiveRain(
                    condition = condition,
                    description = weatherDescription,
                    precipitation = weather.precipitation
                )

                if (isActiveRain) {
                    if (!weatherHadRain) {
                        weatherHadRain = true
                        val elapsedMinutes = if (startTimeMs > 0) {
                            ((System.currentTimeMillis() - startTimeMs) / (1000 * 60)).toInt()
                        } else {
                            0
                        }
                        weatherRainStartMinute = elapsedMinutes
                        weatherRainReason = rainUserReason
                    }
                    weatherMaxPrecipitation = maxOf(weatherMaxPrecipitation, weather.precipitation)
                }

                _startWeatherTemperature = weather.temperature
                _startWeatherEmoji = weatherEmoji
                _startWeatherCode = weather.weatherCode
                _startWeatherCondition = weather.icon
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

                _weatherStatus.value = toWeatherStatusSuccess(weather)
                Log.d(TAG, "✅ Clima obtenido manualmente: ${weather.temperature}°C $weatherEmoji")
                onMessage("Clima obtenido: ${formatTemperature(weather.temperature, 0)}°C $weatherEmoji")
            }.onFailure { error ->
                Log.e(TAG, "❌ Error al obtener clima manualmente: ${error.message}")
                _weatherStatus.value = WeatherStatus.Error(error.message ?: "Error al obtener clima")
                onMessage("Error al obtener clima: ${error.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción al obtener clima manualmente: ${e.message}", e)
            _weatherStatus.value = WeatherStatus.Error("Excepción: ${e.message}")
            onMessage("Error: ${e.message}")
        }
    }

    suspend fun awaitWeatherLoadedIfNeeded(maxWaitMs: Int = 5000) {
        if (_weatherStatus.value is WeatherStatus.Loading) {
            Log.d(TAG, "⏳ Clima aún cargando, esperando hasta ${maxWaitMs / 1000}s más...")
            var waited = 0
            while (_weatherStatus.value is WeatherStatus.Loading && waited < maxWaitMs) {
                delay(500)
                waited += 500
            }
        }
    }

    suspend fun fetchFinalWeatherSnapshot(lastPoint: RoutePoint) {
        Log.d(TAG, "📸 Capturando snapshot FINAL del clima...")
        try {
            val result = weatherRepository.getCurrentWeather(lastPoint.latitude, lastPoint.longitude)
            result.onSuccess { weather ->
                _finalWeatherEmoji = weather.weatherEmoji
                _finalWeatherDescription = weather.description
                _finalWeatherCondition = weather.icon
                _finalWeatherCode = weather.weatherCode
                _finalWeatherIsDay = weather.isDay
                _finalWeatherTemperature = weather.temperature
                _finalWeatherFeelsLike = weather.feelsLike
                _finalWeatherWindChill = weather.windChill
                _finalWeatherHeatIndex = weather.heatIndex
                _finalWeatherDewPoint = weather.dewPoint
                _finalWeatherWindSpeed = weather.windSpeed
                _finalWeatherWindGusts = weather.windGusts
                _finalWeatherWindDirection = weather.windDirection
                _finalWeatherHumidity = weather.humidity
                _finalWeatherRainProbability = weather.rainProbability
                _finalWeatherUvIndex = weather.uvIndex
                _finalWeatherVisibility = weather.visibility

                Log.d(TAG, "✅ Snapshot final capturado. Inicio preservado intacto.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error en snapshot final: ${e.message}. Se usarán datos de inicio.")
        }
    }

    fun captureRouteWeatherSnapshot(): RouteWeatherSnapshot {
        return RouteWeatherSnapshot(
            initialTemp = _startWeatherTemperature,
            initialEmoji = _startWeatherEmoji,
            initialCode = _startWeatherCode,
            initialCondition = _startWeatherCondition,
            initialDescription = _startWeatherDescription,
            initialIsDay = _startWeatherIsDay ?: true,
            initialFeelsLike = _startWeatherFeelsLike,
            initialWindChill = _startWeatherWindChill,
            initialHeatIndex = _startWeatherHeatIndex,
            initialHumidity = _startWeatherHumidity?.toDouble(),
            initialWindSpeed = _startWeatherWindSpeed,
            initialWindGusts = _startWeatherWindGusts,
            initialUvIndex = _startWeatherUvIndex,
            initialVisibility = _startWeatherVisibility,
            initialDewPoint = _startWeatherDewPoint,
            initialRainProbability = _startWeatherRainProbability?.toDouble(),
            initialWindDirection = _startWeatherWindDirection,

            finalTemp = _finalWeatherTemperature,
            finalEmoji = _finalWeatherEmoji,
            finalCode = _finalWeatherCode,
            finalCondition = _finalWeatherCondition,
            finalDescription = _finalWeatherDescription,
            finalIsDay = _finalWeatherIsDay,
            finalFeelsLike = _finalWeatherFeelsLike,
            finalWindChill = _finalWeatherWindChill,
            finalHeatIndex = _finalWeatherHeatIndex,
            finalDewPoint = _finalWeatherDewPoint,
            finalWindSpeed = _finalWeatherWindSpeed,
            finalWindGusts = _finalWeatherWindGusts,
            finalWindDirection = _finalWeatherWindDirection,
            finalHumidity = _finalWeatherHumidity?.toDouble(),
            finalRainProbability = _finalWeatherRainProbability?.toDouble(),
            finalUvIndex = _finalWeatherUvIndex,
            finalVisibility = _finalWeatherVisibility,

            maxWindSpeed = this.maxWindSpeed,
            maxWindGusts = this.maxWindGusts,
            minTemp = this.minTemperature,
            maxTemp = this.maxTemperature,
            maxUvIndex = this.maxUvIndex,
            maxPrecipitation = this.weatherMaxPrecipitation,

            hadRain = this.weatherHadRain || (_shouldShowRainWarning.value && _isActiveRainWarning.value),
            hadWetRoad = this.weatherHadWetRoad || (_shouldShowRainWarning.value && !_isActiveRainWarning.value),
            hadExtreme = this.weatherHadExtremeConditions || _shouldShowExtremeWarning.value,
            extremeReason = this.weatherExtremeReason,
            rainReason = this.weatherRainReason,
            rainStartMinute = this.weatherRainStartMinute
        )
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

    fun shouldAskSurfaceConditionQuestionsOnFinish(): Boolean {
        val detectedSurfaceCondition = getSurfaceConditionTypeForConfirmation()
        if (detectedSurfaceCondition != SurfaceConditionType.NONE) {
            return true
        }

        val clearConditions = setOf("CLEAR", "SUNNY", "MOSTLY_CLEAR", "PARTLY_CLOUDY")
        val cloudyConditions = setOf("CLOUDY", "MOSTLY_CLOUDY")
        val foggyConditions = setOf("FOG", "HAZE", "MIST")

        val startCondition = _startWeatherCondition?.uppercase()
        val finalCondition = _finalWeatherCondition?.uppercase()
        val primaryCondition = finalCondition ?: startCondition

        if (primaryCondition in clearConditions) {
            return false
        }

        val isCloudyByCondition = startCondition in cloudyConditions || finalCondition in cloudyConditions
        val isCloudyByCode = _startWeatherCode == 3 || _finalWeatherCode == 3
        val isFoggy = startCondition in foggyConditions || finalCondition in foggyConditions

        val humidity = _finalWeatherHumidity ?: _startWeatherHumidity
        val temperature = _finalWeatherTemperature ?: _startWeatherTemperature
        val dewPoint = _finalWeatherDewPoint ?: _startWeatherDewPoint

        val hasHumidityCondensationSignal = humidity != null &&
            humidity >= 88 &&
            temperature != null &&
            kotlin.math.abs(temperature - dewPoint!!) <= 2.0

        val hasRecentPrecipitationSignal =
            recentPrecipitation3h >= 0.15 || weatherMaxPrecipitation >= 0.15

        val weatherDescription = (_finalWeatherDescription ?: _startWeatherDescription).orEmpty()
        val hasMoistureDescription = listOf(
            "niebla",
            "bruma",
            "fog",
            "mist",
            "haze",
            "rocío",
            "rocio",
            "húmed",
            "humed"
        ).any { weatherDescription.contains(it, ignoreCase = true) }

        return isCloudyByCondition ||
            isCloudyByCode ||
            isFoggy ||
            hasRecentPrecipitationSignal ||
            hasHumidityCondensationSignal ||
            hasMoistureDescription
    }

    fun resetAll() {
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

        weatherHadRain = false
        weatherHadWetRoad = false
        weatherHadExtremeConditions = false
        weatherExtremeReason = null
        weatherMaxPrecipitation = 0.0
        weatherRainStartMinute = null
        weatherRainReason = null
        sessionClimateRegion = null

        maxWindSpeed = 0.0
        maxWindGusts = 0.0
        minTemperature = Double.MAX_VALUE
        maxTemperature = Double.MIN_VALUE
        maxUvIndex = 0.0

        pendingRainConfirmation = false
        pendingRainMinute = null
        pendingRainReason = null
        _initialWeatherSnapshot = null
        _initialWeatherCaptured = false
        _initialWeatherLatitude = null
        _initialWeatherLongitude = null

        _shouldShowRainWarning.value = false
        _isActiveRainWarning.value = false
        _shouldShowExtremeWarning.value = false
        lastWeatherBadgeState = null

        _weatherStatus.value = WeatherStatus.Idle
        routeRepository.clearTempWeather()

        Log.d(TAG, "🔄 Todos los estados de clima y badges han sido reseteados a cero")
    }

    fun resetOnCancel() {
        weatherJob?.cancel()
        continuousWeatherJob?.cancel()

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

        weatherHadRain = false
        weatherRainStartMinute = null
        weatherMaxPrecipitation = 0.0
        weatherRainReason = null
        weatherHadWetRoad = false
        weatherHadExtremeConditions = false
        sessionClimateRegion = null
        maxWindSpeed = 0.0
        maxWindGusts = 0.0
        minTemperature = Double.MAX_VALUE
        maxTemperature = Double.MIN_VALUE
        maxUvIndex = 0.0
        pendingRainConfirmation = false
        pendingRainMinute = null
        pendingRainReason = null

        _shouldShowRainWarning.value = false
        _isActiveRainWarning.value = false
        _shouldShowExtremeWarning.value = false
        lastWeatherBadgeState = null
        _weatherStatus.value = WeatherStatus.Idle

        _initialWeatherSnapshot = null
        _initialWeatherCaptured = false
        _initialWeatherLatitude = null
        _initialWeatherLongitude = null

        Log.d(TAG, "🔄 Estado del clima y badges reseteados al cancelar tracking")
    }

    fun restoreFromSavedWeather(isTrackingActive: Boolean) {
        val savedWeather = routeRepository.getTempWeather()
        if (savedWeather != null) {
            Log.d(TAG, "♻️ Recuperando clima guardado tras cambio de pantalla")

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

            if (isTrackingActive) {
                _shouldShowRainWarning.value = savedWeather.shouldShowRainWarning
                _isActiveRainWarning.value = savedWeather.isActiveRainWarning
                _shouldShowExtremeWarning.value = savedWeather.shouldShowExtremeWarning
                Log.d(TAG, "♻️ Badges restaurados (tracking activo)")
            } else {
                _shouldShowRainWarning.value = false
                _isActiveRainWarning.value = false
                _shouldShowExtremeWarning.value = false
                lastWeatherBadgeState = null
                Log.d(TAG, "🔄 Badges NO restaurados (estado Idle - pretracking)")
            }

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
            _shouldShowRainWarning.value = false
            _isActiveRainWarning.value = false
            _shouldShowExtremeWarning.value = false
            lastWeatherBadgeState = null
            _weatherStatus.value = WeatherStatus.Idle
            Log.d(TAG, "🔄 No hay clima guardado, badges y estado del clima limpiados")
        }
    }

    fun onSessionEnded() {
        continuousWeatherJob?.cancel()
        continuousWeatherJob = null
    }

    private suspend fun getRecentPrecipitation3h(latitude: Double, longitude: Double): Double {
        return weatherRepository
            .getRecentPrecipitationHours(latitude = latitude, longitude = longitude, hours = 3)
            .getOrElse { 0.0 }
            .coerceAtLeast(0.0)
    }

    private fun resolveClimateRegion(
        latitude: Double,
        longitude: Double,
    ): WeatherAdvisor.ClimateRegion {
        sessionClimateRegion?.let { return it }

        val region = userClimateRegionOverride
            ?: weatherAdvisor.inferClimateRegion(latitude, longitude)
        sessionClimateRegion = region
        val source = if (userClimateRegionOverride != null) {
            "manual (ajustes)"
        } else {
            "GPS (lat=$latitude, lon=$longitude)"
        }
        Log.d(TAG, "🗺️ Región climática fijada para esta ruta: $region ($source)")
        return region
    }

    private fun resolveClimateRegionWithoutGps(): WeatherAdvisor.ClimateRegion =
        sessionClimateRegion
            ?: userClimateRegionOverride
            ?: WeatherAdvisor.ClimateRegion.MEDITERRANEAN_COAST

    private suspend fun getRecentPrecipitation24h(latitude: Double, longitude: Double): Double {
        return weatherRepository
            .getRecentPrecipitationHours(latitude = latitude, longitude = longitude, hours = 24)
            .getOrElse { 0.0 }
            .coerceAtLeast(0.0)
    }

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

    private fun getBadgeText(state: WeatherBadgeState, extremeReason: String? = null): String {
        return when (state) {
            WeatherBadgeState.LLUVIA -> "🔵 Lluvia"
            WeatherBadgeState.CALZADA_HUMEDA -> "🟡 Calzada húmeda"
            WeatherBadgeState.EXTREMO -> {
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

    private fun getBadgeIconResId(state: WeatherBadgeState, weatherStatus: WeatherStatus): Int {
        if (weatherStatus is WeatherStatus.Success) {
            return WeatherRepository.getIconResIdForCondition(
                weatherStatus.icon,
                weatherStatus.isDay
            )
        }
        return android.R.drawable.ic_dialog_alert
    }

    private fun checkAndNotifyWeatherChange() {
        val currentState = getCurrentWeatherBadgeState()
        val lastState = lastWeatherBadgeState

        if (lastState == null) {
            lastWeatherBadgeState = currentState
            Log.d(TAG, "📍 Punto de control inicial: $currentState (Sin notificación)")
            return
        }

        if (currentState != lastState) {
            Log.d(TAG, "🔔 Cambio de Badge detectado: $lastState -> $currentState")

            if (currentState != WeatherBadgeState.SECO) {
                val text = getBadgeText(currentState, weatherExtremeReason)
                val icon = getBadgeIconResId(currentState, _weatherStatus.value)
                notificationHandler.showWeatherChangeNotification(text, icon)
            }

            lastWeatherBadgeState = currentState
        }
    }

    private fun toWeatherStatusSuccess(weather: WeatherData): WeatherStatus.Success =
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

    private fun formatTemperature(temperature: Double, decimals: Int = 1): String {
        val absTemp = kotlin.math.abs(temperature)
        val formatted = LocationUtils.formatNumberSpanish(absTemp, decimals)

        val isEffectiveZero = try {
            formatted.replace(",", ".").toDouble() == 0.0
        } catch (e: Exception) {
            false
        }

        return if (temperature < 0 && !isEffectiveZero) {
            "-$formatted"
        } else {
            formatted
        }
    }

    companion object {
        private const val TAG = "WeatherMonitoring"
    }
}
