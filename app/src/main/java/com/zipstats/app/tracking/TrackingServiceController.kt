package com.zipstats.app.tracking

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.model.Scooter
import com.zipstats.app.service.LocationTrackingService
import com.zipstats.app.service.TrackingStateManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controla el servicio GPS en foreground y el posicionamiento previo a iniciar ruta.
 */
@Singleton
class TrackingServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val trackingStateManager = TrackingStateManager
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var trackingService: LocationTrackingService? = null
    private var serviceBound = false
    private var observationScope: CoroutineScope? = null
    private var observeJob: Job? = null
    private var durationTimerJob: Job? = null

    private var preLocationCallback: LocationCallback? = null
    private var isPreLocationActive = false
    private var onPreLocationUpdate: ((Location, GpsPreLocationState) -> Unit)? = null
    private var onServicePauseChanged: ((Boolean) -> Unit)? = null

    private var pausedDuration = 0L
    private var pauseStartTime = 0L
    private var isSessionPaused = false

    private val _gpsPreLocationState = MutableStateFlow<GpsPreLocationState>(GpsPreLocationState.Searching)
    val gpsPreLocationState: StateFlow<GpsPreLocationState> = _gpsPreLocationState.asStateFlow()

    private val _preLocation = MutableStateFlow<Location?>(null)
    val preLocation: StateFlow<Location?> = _preLocation.asStateFlow()

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

    private val _gpsSignalStrength = MutableStateFlow(0f)
    val gpsSignalStrength: StateFlow<Float> = _gpsSignalStrength.asStateFlow()

    val isServiceBound: Boolean get() = serviceBound

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationTrackingService.LocalBinder
            trackingService = binder.getService()
            serviceBound = true
            startObservingService()
            Log.d(TAG, "Servicio conectado")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
            serviceBound = false
            Log.d(TAG, "Servicio desconectado")
        }
    }

    fun attach(
        scope: CoroutineScope,
        onServicePauseChanged: ((Boolean) -> Unit)? = null,
    ) {
        observationScope = scope
        this.onServicePauseChanged = onServicePauseChanged
        if (serviceBound && trackingService != null) {
            startObservingService()
        }
    }

    fun connectToExistingService() {
        if (serviceBound) return

        try {
            val intent = Intent(context, LocationTrackingService::class.java)
            val bound = context.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
            if (bound) {
                serviceBound = true
                Log.d(TAG, "Conectado al servicio existente")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al conectar con servicio existente", e)
        }
    }

    fun startTracking(scooter: Scooter) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_TRACKING
        }
        context.startForegroundService(intent)
        context.bindService(
            Intent(context, LocationTrackingService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )

        trackingService?.updateVehicleType(scooter.vehicleType)
        trackingStateManager.updateTrackingState(true)
        trackingStateManager.updatePausedState(false)
        isSessionPaused = false
        pausedDuration = 0L
        pauseStartTime = 0L
        Log.d(TAG, "Seguimiento iniciado")
    }

    fun pauseTracking() {
        trackingService?.pauseTracking()
        trackingStateManager.updatePausedState(true)
        isSessionPaused = true
        pauseStartTime = System.currentTimeMillis()
    }

    fun resumeTracking() {
        trackingService?.resumeTracking()
        trackingStateManager.updatePausedState(false)
        isSessionPaused = false

        if (pauseStartTime > 0) {
            pausedDuration += System.currentTimeMillis() - pauseStartTime
            pauseStartTime = 0L
        }
    }

    fun stopTracking() {
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
            observeJob?.cancel()
            observeJob = null
            durationTimerJob?.cancel()
            durationTimerJob = null
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener servicio", e)
        }
    }

    fun unbindIfBound() {
        if (serviceBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error al desvincular servicio", e)
            }
            serviceBound = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startPreLocationTracking(onLocationUpdate: (Location, GpsPreLocationState) -> Unit) {
        if (isPreLocationActive) return

        onPreLocationUpdate = onLocationUpdate
        Log.d(TAG, "Iniciando posicionamiento GPS previo")
        isPreLocationActive = true
        _gpsPreLocationState.value = GpsPreLocationState.Searching

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L,
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
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos en posicionamiento previo", e)
            _gpsPreLocationState.value = GpsPreLocationState.Searching
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar posicionamiento previo", e)
            _gpsPreLocationState.value = GpsPreLocationState.Searching
        }
    }

    fun stopPreLocationTracking() {
        if (!isPreLocationActive) return

        Log.d(TAG, "Deteniendo posicionamiento GPS previo")
        isPreLocationActive = false
        onPreLocationUpdate = null

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

    fun restartPreLocationTracking(onLocationUpdate: (Location, GpsPreLocationState) -> Unit) {
        stopPreLocationTracking()
        startPreLocationTracking(onLocationUpdate)
    }

    fun hasValidGpsSignal(): Boolean {
        return when (val state = _gpsPreLocationState.value) {
            is GpsPreLocationState.Ready -> true
            is GpsPreLocationState.Found -> state.accuracy <= 10f
            is GpsPreLocationState.Searching -> false
        }
    }

    fun resetSessionMetrics() {
        _routePoints.value = emptyList()
        _currentDistance.value = 0.0
        _currentSpeed.value = 0.0
        _averageMovingSpeed.value = 0.0
        _timeInMotion.value = 0L
        _duration.value = 0L
        _gpsSignalStrength.value = 0f
        pausedDuration = 0L
        pauseStartTime = 0L
        isSessionPaused = false
    }

    fun resetTrackingUI() {
        resetSessionMetrics()
        Log.d(TAG, "🧹 UI de tracking reseteada")
    }

    private fun handlePreLocation(location: Location) {
        val accuracy = location.accuracy
        val currentPreLocation = _preLocation.value
        if (currentPreLocation == null || accuracy < currentPreLocation.accuracy) {
            _preLocation.value = location

            val newState = when {
                accuracy <= 6f -> {
                    Log.d(TAG, "GPS previo: Listo (precisión: ${accuracy}m)")
                    GpsPreLocationState.Ready
                }
                accuracy <= 10f -> {
                    Log.d(TAG, "GPS previo: Señal encontrada (precisión: ${accuracy}m)")
                    GpsPreLocationState.Found(accuracy)
                }
                else -> {
                    Log.d(TAG, "GPS previo: Buscando señal mejor (precisión: ${accuracy}m)")
                    GpsPreLocationState.Found(accuracy)
                }
            }

            _gpsPreLocationState.value = newState
            onPreLocationUpdate?.invoke(location, newState)
        }
    }

    private fun startObservingService() {
        val scope = observationScope ?: return
        val service = trackingService ?: return

        observeJob?.cancel()
        observeJob = scope.launch {
            launch {
                service.routePoints.collect { points ->
                    _routePoints.value = points
                }
            }
            launch {
                service.currentDistance.collect { distance ->
                    _currentDistance.value = distance
                }
            }
            launch {
                service.currentSpeed.collect { speed ->
                    _currentSpeed.value = speed
                }
            }
            launch {
                service.averageMovingSpeed.collect { speed ->
                    _averageMovingSpeed.value = speed
                }
            }
            launch {
                service.timeInMotion.collect { time ->
                    _timeInMotion.value = time
                }
            }
            launch {
                service.startTime.collect { time ->
                    _startTime.value = time
                    if (time > 0) {
                        startDurationTimer()
                    }
                }
            }
            launch {
                service.isPaused.collect { isPaused ->
                    isSessionPaused = isPaused
                    onServicePauseChanged?.invoke(isPaused)
                }
            }
            launch {
                service.routePoints.collect { points ->
                    if (points.isNotEmpty()) {
                        val lastPoint = points.last()
                        val accuracy = lastPoint.accuracy ?: 100f
                        _gpsSignalStrength.value = accuracyToSignalStrength(accuracy)
                        Log.d(TAG, "GPS Signal - Accuracy: ${accuracy}m, Strength: ${_gpsSignalStrength.value}%")
                    }
                }
            }
        }
    }

    private fun startDurationTimer() {
        val scope = observationScope ?: return

        durationTimerJob?.cancel()
        durationTimerJob = scope.launch {
            while (trackingStateManager.isTracking.value) {
                if (_startTime.value > 0) {
                    val currentPauseTime = if (isSessionPaused && pauseStartTime > 0) {
                        System.currentTimeMillis() - pauseStartTime
                    } else {
                        0L
                    }
                    _duration.value = System.currentTimeMillis() - _startTime.value - pausedDuration - currentPauseTime
                }
                delay(1000)
            }
        }
    }

    private fun accuracyToSignalStrength(accuracy: Float): Float = when {
        accuracy <= 5f -> 100f
        accuracy <= 10f -> 80f
        accuracy <= 20f -> 60f
        accuracy <= 50f -> 40f
        else -> 20f
    }

    companion object {
        private const val TAG = "TrackingServiceController"
    }
}
