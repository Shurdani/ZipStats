package com.zipstats.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zipstats.app.MainActivity
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.util.LocationUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Servicio de foreground para seguimiento de ubicación GPS
 * Permite grabar rutas incluso con la pantalla apagada
 */
class LocationTrackingService : Service() {

    private val binder = LocalBinder()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Manager de estado global (singleton)
    private val trackingStateManager = TrackingStateManager
    
    // Estado del servicio
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    
    // Puntos de la ruta
    private val _routePoints = MutableStateFlow<List<RoutePoint>>(emptyList())
    val routePoints: StateFlow<List<RoutePoint>> = _routePoints.asStateFlow()
    
    // Estadísticas en tiempo real
    private val _currentDistance = MutableStateFlow(0.0)
    val currentDistance: StateFlow<Double> = _currentDistance.asStateFlow()
    
    private val _currentSpeed = MutableStateFlow(0.0)
    val currentSpeed: StateFlow<Double> = _currentSpeed.asStateFlow()
    
    private val _startTime = MutableStateFlow(0L)
    val startTime: StateFlow<Long> = _startTime.asStateFlow()
    
    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    override fun onCreate() {
        super.onCreate()
        
        // El manager de estado global ya está inicializado como singleton
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannel()
        setupLocationCallback()
        
        // Adquirir WakeLock para mantener el CPU activo con pantalla apagada
        acquireWakeLock()
        
        Log.d(TAG, "Servicio creado")
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startTracking()
            ACTION_PAUSE_TRACKING -> pauseTracking()
            ACTION_RESUME_TRACKING -> resumeTracking()
            ACTION_STOP_TRACKING -> stopTracking()
        }
        return START_STICKY
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    handleNewLocation(location)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (_isTracking.value) return
        
        Log.d(TAG, "Iniciando seguimiento GPS")
        _isTracking.value = true
        _isPaused.value = false
        _startTime.value = System.currentTimeMillis()
        _routePoints.value = emptyList()
        _currentDistance.value = 0.0
        
        // Configurar solicitud de ubicación de alta precisión
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
            setMinUpdateDistanceMeters(LOCATION_MIN_DISTANCE)
            setWaitForAccurateLocation(false)
            setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL)
        }.build()

        // Iniciar actualizaciones de ubicación
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // Actualizar estado global
        trackingStateManager.updateTrackingState(true)
        trackingStateManager.updatePausedState(false)
        
        // Iniciar servicio en foreground
        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun pauseTracking() {
        if (!_isTracking.value || _isPaused.value) return
        
        Log.d(TAG, "Pausando seguimiento GPS")
        _isPaused.value = true
        trackingStateManager.updatePausedState(true)
        
        // Actualizar notificación
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    fun resumeTracking() {
        if (!_isTracking.value || !_isPaused.value) return
        
        Log.d(TAG, "Reanudando seguimiento GPS")
        _isPaused.value = false
        trackingStateManager.updatePausedState(false)
        
        // Actualizar notificación
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    fun stopTracking() {
        if (!_isTracking.value) return
        
        Log.d(TAG, "Deteniendo seguimiento GPS")
        _isTracking.value = false
        _isPaused.value = false
        
        // Actualizar estado global
        trackingStateManager.resetState()
        
        // Detener actualizaciones de ubicación
        fusedLocationClient.removeLocationUpdates(locationCallback)
        
        // Detener servicio foreground
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleNewLocation(location: Location) {
        // Si está pausado, no registrar puntos
        if (_isPaused.value) return
        
        // Filtrar ubicaciones con mala precisión
        if (location.accuracy > MAX_ACCURACY_METERS) {
            Log.d(TAG, "Ubicación descartada por baja precisión: ${location.accuracy}m")
            return
        }
        
        val routePoint = LocationUtils.locationToRoutePoint(location)
        val currentPoints = _routePoints.value.toMutableList()
        
        // Si hay puntos previos, calcular distancia
        if (currentPoints.isNotEmpty()) {
            val lastPoint = currentPoints.last()
            val distance = LocationUtils.calculateDistance(lastPoint, routePoint)
            
            // Evitar puntos muy cercanos (ruido GPS)
            if (distance < MIN_DISTANCE_BETWEEN_POINTS_KM) {
                return
            }
            
        // Actualizar distancia total
        _currentDistance.value += distance
        trackingStateManager.updateDistance(LocationUtils.formatDistance(_currentDistance.value))
        }
        
        // Agregar punto
        currentPoints.add(routePoint)
        _routePoints.value = currentPoints
        
        // Actualizar velocidad actual con filtro para velocidades muy bajas
        if (location.hasSpeed()) {
            val speedKmh = LocationUtils.metersPerSecondToKmPerHour(location.speed)
            _currentSpeed.value = LocationUtils.filterSpeed(speedKmh)
        } else {
            _currentSpeed.value = 0.0
        }
        
        // Actualizar duración
        val currentTime = System.currentTimeMillis()
        val duration = currentTime - _startTime.value
        val formattedDuration = formatDuration(duration)
        trackingStateManager.updateDuration(formattedDuration)
        
        // Actualizar notificación con estadísticas
        notificationManager.notify(NOTIFICATION_ID, createNotification())
        
        Log.d(TAG, "Punto GPS registrado: ${currentPoints.size} puntos, " +
                "${LocationUtils.formatDistance(_currentDistance.value)}")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Seguimiento GPS",
                NotificationManager.IMPORTANCE_DEFAULT // Cambiado de LOW a DEFAULT para mejor visibilidad
            ).apply {
                description = "Notificación persistente para seguimiento de rutas GPS"
                setShowBadge(true)
                enableLights(true)
                enableVibration(false) // Sin vibración en cada actualización
                setSound(null, null) // Sin sonido en cada actualización
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Canal de notificación creado")
        }
    }

    private fun createNotification(): Notification {
        // Intent para abrir la app en la pantalla de tracking
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_TRACKING
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (_isPaused.value) {
            "Seguimiento GPS - Pausado"
        } else {
            "Grabando ruta GPS"
        }

        val text = buildString {
            append(LocationUtils.formatDistance(_currentDistance.value))
            append(" • ")
            append("${_routePoints.value.size} puntos")
            if (_currentSpeed.value > 0) {
                append(" • ")
                append(LocationUtils.formatSpeed(_currentSpeed.value))
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Icono de ubicación del sistema
            .setContentIntent(pendingIntent)
            .setOngoing(true) // No se puede deslizar para cerrar
            .setOnlyAlertOnce(true) // No alertar en cada actualización
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Limpia los datos de la ruta actual
     */
    fun clearRoute() {
        _routePoints.value = emptyList()
        _currentDistance.value = 0.0
        _currentSpeed.value = 0.0
    }

    override fun onDestroy() {
        super.onDestroy()
        if (_isTracking.value) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        releaseWakeLock()
        Log.d(TAG, "Servicio destruido")
    }
    
    /**
     * Adquiere un WakeLock parcial para mantener el CPU activo
     * Esto permite que el GPS funcione con la pantalla apagada
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PatinetaTrack::LocationTrackingWakeLock"
            ).apply {
                acquire(10 * 60 * 60 * 1000L) // Máximo 10 horas
            }
            Log.d(TAG, "WakeLock adquirido")
        } catch (e: Exception) {
            Log.e(TAG, "Error al adquirir WakeLock", e)
        }
    }
    
    /**
     * Libera el WakeLock cuando el servicio termina
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock liberado")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar WakeLock", e)
        }
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si el usuario cierra la app desde recientes, mantener el servicio vivo
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Tarea removida - servicio sigue activo")
        
        // Asegurar que la notificación sigue visible
        if (_isTracking.value) {
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }
    
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    companion object {
        private const val TAG = "LocationTrackingService"
        
        const val ACTION_START_TRACKING = "com.zipstats.app.action.START_TRACKING"
        const val ACTION_PAUSE_TRACKING = "com.zipstats.app.action.PAUSE_TRACKING"
        const val ACTION_RESUME_TRACKING = "com.zipstats.app.action.RESUME_TRACKING"
        const val ACTION_STOP_TRACKING = "com.zipstats.app.action.STOP_TRACKING"
        const val ACTION_OPEN_TRACKING = "com.zipstats.app.action.OPEN_TRACKING"
        
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "location_tracking_channel"
        
        // Configuración de ubicación
        private const val LOCATION_UPDATE_INTERVAL = 3000L // 3 segundos
        private const val LOCATION_FASTEST_INTERVAL = 1500L // 1.5 segundos
        private const val LOCATION_MIN_DISTANCE = 3f // 3 metros
        
        // Filtros de calidad
        private const val MAX_ACCURACY_METERS = 30f // Descartar ubicaciones con precisión > 30m
        private const val MIN_DISTANCE_BETWEEN_POINTS_KM = 0.003 // 3 metros en km
    }
}

