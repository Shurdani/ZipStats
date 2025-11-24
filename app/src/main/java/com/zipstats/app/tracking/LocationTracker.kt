package com.zipstats.app.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

/**
 * Configuración óptima de GPS para seguimiento de vehículos de movilidad personal
 * Clase independiente para manejo avanzado de ubicación
 */
class LocationTracker(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    /**
     * Crea configuración óptima de GPS para seguimiento en tiempo real
     * Optimizada para vehículos de movilidad personal (patinetes, bicicletas, etc.)
     */
    fun createOptimalLocationRequest(): LocationRequest {
        return LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            500L // Actualización cada 500ms (2Hz) - CRÍTICO para precisión
        ).apply {
            // Permite actualizaciones más frecuentes si el GPS puede proveerlas
            setMinUpdateIntervalMillis(250L) // Hasta 4Hz cuando sea necesario
            
            // Tiempo máximo entre actualizaciones
            setMaxUpdateDelayMillis(1000L) // 1 segundo máximo
            
            // Distancia mínima entre actualizaciones (0 = basado solo en tiempo)
            setMinUpdateDistanceMeters(0f)
            
            // No esperar a tener alta precisión inicial - empezar inmediatamente
            setWaitForAccurateLocation(false)
            
            // Duración máxima de la solicitud (infinito para seguimiento continuo)
            setDurationMillis(Long.MAX_VALUE)
            
            // Granularidad: FINE para máxima precisión
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            
        }.build()
    }
    
    /**
     * Crea configuración GPS para seguimiento en background
     * Optimizada para ahorro de batería manteniendo precisión
     */
    fun createBackgroundLocationRequest(): LocationRequest {
        return LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            2000L // Actualización cada 2 segundos en background
        ).apply {
            setMinUpdateIntervalMillis(1000L) // Mínimo 1 segundo
            setMaxUpdateDelayMillis(5000L) // Máximo 5 segundos
            setMinUpdateDistanceMeters(5f) // Mínimo 5 metros de movimiento
            setWaitForAccurateLocation(false)
            setDurationMillis(Long.MAX_VALUE)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
        }.build()
    }
    
    /**
     * Crea configuración GPS para seguimiento de alta precisión
     * Para cuando se necesita máxima precisión (ej: competiciones)
     */
    fun createHighAccuracyLocationRequest(): LocationRequest {
        return LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            250L // Actualización cada 250ms (4Hz) - máxima frecuencia
        ).apply {
            setMinUpdateIntervalMillis(100L) // Hasta 10Hz si es posible
            setMaxUpdateDelayMillis(500L) // Máximo 500ms de retraso
            setMinUpdateDistanceMeters(0f) // Sin filtro de distancia
            setWaitForAccurateLocation(true) // Esperar alta precisión
            setDurationMillis(Long.MAX_VALUE)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
        }.build()
    }
    
    /**
     * Inicia seguimiento GPS con callback
     * @param locationRequest Configuración de ubicación
     * @param callback Callback para recibir actualizaciones
     * @return true si se inició correctamente, false si no hay permisos
     */
    fun startTracking(
        locationRequest: LocationRequest,
        callback: LocationCallback
    ): Boolean {
        if (!hasLocationPermission()) {
            return false
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )
        
        return true
    }
    
    /**
     * Inicia seguimiento GPS en background con callback
     * @param locationRequest Configuración de ubicación
     * @param callback Callback para recibir actualizaciones
     * @return true si se inició correctamente, false si no hay permisos
     */
    fun startBackgroundTracking(
        locationRequest: LocationRequest,
        callback: LocationCallback
    ): Boolean {
        if (!hasLocationPermission() || !hasBackgroundLocationPermission()) {
            return false
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )
        
        return true
    }
    
    /**
     * Detiene seguimiento GPS
     * @param callback Callback que se estaba usando
     */
    fun stopTracking(callback: LocationCallback) {
        fusedLocationClient.removeLocationUpdates(callback)
    }
    
    /**
     * Obtiene la última ubicación conocida
     * @param callback Callback para recibir la ubicación
     */
    fun getLastLocation(callback: (android.location.Location?) -> Unit) {
        if (!hasLocationPermission()) {
            callback(null)
            return
        }
        
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                callback(location)
            }
            .addOnFailureListener {
                callback(null)
            }
    }
    
    /**
     * Verifica si tiene permisos de ubicación
     */
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Verifica si tiene permisos de ubicación en background (Android 10+)
     */
    private fun hasBackgroundLocationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No se requiere en versiones anteriores
        }
    }
    
    /**
     * Verifica si el GPS está habilitado
     */
    fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
    }
    
    /**
     * Obtiene la precisión GPS actual (0-100)
     * @param callback Callback con la precisión estimada
     */
    fun getGpsAccuracy(callback: (Int) -> Unit) {
        getLastLocation { location ->
            if (location != null && location.hasAccuracy()) {
                val accuracy = location.accuracy
                val accuracyPercentage = when {
                    accuracy <= 5f -> 100 // Excelente
                    accuracy <= 10f -> 80  // Muy buena
                    accuracy <= 20f -> 60  // Buena
                    accuracy <= 50f -> 40  // Regular
                    else -> 20 // Mala
                }
                callback(accuracyPercentage)
            } else {
                callback(0)
            }
        }
    }
}
