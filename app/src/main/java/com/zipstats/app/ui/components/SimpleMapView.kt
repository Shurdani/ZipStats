package com.zipstats.app.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.zipstats.app.model.Route
import kotlinx.coroutines.delay

@Composable
fun SimpleMapView(
    route: Route,
    modifier: Modifier = Modifier
) {
    var isMapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var shouldReloadMap by remember { mutableStateOf(false) } // Flag para forzar recarga
    var mapKey by remember { mutableStateOf(0) } // Clave para forzar recreación
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Convertir puntos de la ruta a LatLng
    val routePoints = remember(route.points) {
        Log.d("SimpleMapView", "Puntos de ruta: ${route.points.size}")
        route.points.map { point ->
            LatLng(point.latitude, point.longitude)
        }
    }
    
    // Estado de la cámara
    val cameraPositionState = rememberCameraPositionState {
        if (routePoints.isNotEmpty()) {
            position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                routePoints.first(),
                15f
            )
        } else {
            // Posición por defecto en Barcelona
            position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                LatLng(41.3851, 2.1734),
                15f
            )
        }
    }
    
    // Configuración mínima del mapa
    val mapProperties = remember {
        MapProperties(
            mapType = MapType.NORMAL
        )
    }
    
    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = true,
            zoomGesturesEnabled = true,
            scrollGesturesEnabled = true
        )
    }
    
    // Ajustar cámara cuando se carguen los puntos
    LaunchedEffect(routePoints) {
        if (routePoints.isNotEmpty()) {
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(routePoints.first(), 15f)
                )
            } catch (e: Exception) {
                Log.e("SimpleMapView", "Error ajustando cámara: ${e.message}")
            }
        }
    }
    
    // Observar el ciclo de vida para recargar el mapa cuando la app vuelve del background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Cuando la app vuelve del background, recargar el mapa
                    Log.d("SimpleMapView", "🔄 App resumida, recargando mapa...")
                    isMapLoaded = false
                    mapError = null
                    mapKey++ // Incrementar clave para forzar recreación
                    shouldReloadMap = true // Activar recarga
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Efecto para manejar la recarga del mapa
    LaunchedEffect(shouldReloadMap) {
        if (shouldReloadMap) {
            Log.d("SimpleMapView", "🔄 Recargando mapa...")
            shouldReloadMap = false // Resetear el flag
        }
    }
    
    // Timeout para mostrar el mapa después de 3 segundos
    LaunchedEffect(Unit) {
        delay(3000) // 3 segundos
        if (!isMapLoaded) {
            Log.d("SimpleMapView", "Timeout alcanzado, mostrando mapa")
            isMapLoaded = true
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        key(mapKey) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapLoaded = {
                Log.d("SimpleMapView", "Mapa cargado correctamente")
                isMapLoaded = true
                mapError = null
            }
        ) {
            // Dibujar la línea de la ruta
            if (routePoints.size > 1) {
                Log.d("SimpleMapView", "Dibujando polyline con ${routePoints.size} puntos")
                Polyline(
                    points = routePoints,
                    color = androidx.compose.ui.graphics.Color.Blue,
                    width = 8f
                )
            }
        }
        }
        
        // Indicador de carga
        if (!isMapLoaded && mapError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Cargando mapa...",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        
        // Manejo de errores
        if (mapError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error al cargar el mapa",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Verifica tu conexión a internet y la API key",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "Error: $mapError",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
