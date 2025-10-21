package com.zipstats.app.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zipstats.app.model.Route
import com.zipstats.app.model.RoutePoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun RouteMapView(
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
        Log.d("RouteMapView", "Puntos de ruta: ${route.points.size}")
        route.points.forEachIndexed { index, point ->
            Log.d("RouteMapView", "Punto $index: lat=${point.latitude}, lng=${point.longitude}")
        }
        route.points.map { point ->
            LatLng(point.latitude, point.longitude)
        }
    }
    
    // Calcular bounds para ajustar la cámara
    val bounds = remember(routePoints) {
        if (routePoints.isNotEmpty()) {
            val builder = LatLngBounds.builder()
            routePoints.forEach { point ->
                builder.include(point)
            }
            builder.build()
        } else {
            null
        }
    }
    
    // Estado de la cámara con posición por defecto
    val cameraPositionState = rememberCameraPositionState {
        if (routePoints.isNotEmpty()) {
            position = CameraPosition.fromLatLngZoom(
                routePoints.first(),
                15f
            )
        } else {
            // Posición por defecto en Madrid
            position = CameraPosition.fromLatLngZoom(
                LatLng(40.4168, -3.7038),
                15f
            )
        }
    }
    
    // Configuración del mapa
    val mapProperties = remember {
        MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = false,
            isTrafficEnabled = false,
            isBuildingEnabled = true,
            isIndoorEnabled = true
        )
    }
    
    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = true,
            compassEnabled = true,
            myLocationButtonEnabled = false,
            zoomGesturesEnabled = true,
            scrollGesturesEnabled = true,
            tiltGesturesEnabled = true
        )
    }
    
    // Observar el ciclo de vida para recargar el mapa cuando la app vuelve del background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Cuando la app vuelve del background, recargar el mapa
                    Log.d("RouteMapView", "🔄 App resumida, recargando mapa...")
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
            Log.d("RouteMapView", "🔄 Recargando mapa...")
            shouldReloadMap = false // Resetear el flag
        }
    }
    
    // Ajustar la cámara cuando se carguen los bounds
    LaunchedEffect(bounds, routePoints) {
        if (bounds != null && routePoints.size > 1) {
            Log.d("RouteMapView", "Ajustando cámara a bounds: $bounds")
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, 100)
                )
            } catch (e: Exception) {
                Log.e("RouteMapView", "Error ajustando cámara: ${e.message}")
                // Fallback: centrar en el primer punto
                if (routePoints.isNotEmpty()) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(routePoints.first(), 15f)
                    )
                }
            }
        } else if (routePoints.isNotEmpty()) {
            // Si solo hay un punto, centrar en él
            Log.d("RouteMapView", "Centrando en único punto: ${routePoints.first()}")
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(routePoints.first(), 15f)
            )
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
                Log.d("RouteMapView", "Mapa cargado correctamente")
                isMapLoaded = true
                mapError = null
            }
        ) {
        // Dibujar la línea de la ruta
        if (routePoints.size > 1) {
            Log.d("RouteMapView", "Dibujando polyline con ${routePoints.size} puntos")
            Polyline(
                points = routePoints,
                color = androidx.compose.ui.graphics.Color.Blue,
                width = 8f
            )
            
            // Marcador de inicio (verde)
            Marker(
                state = com.google.maps.android.compose.rememberMarkerState(position = routePoints.first()),
                title = "Inicio de la ruta"
            )
            
            // Marcador de fin (rojo)
            if (routePoints.size > 1) {
                Marker(
                    state = com.google.maps.android.compose.rememberMarkerState(position = routePoints.last()),
                    title = "Fin de la ruta"
                )
            }
        } else if (routePoints.size == 1) {
            // Solo un punto, mostrar marcador
            Log.d("RouteMapView", "Mostrando único punto como marcador")
            Marker(
                state = com.google.maps.android.compose.rememberMarkerState(position = routePoints.first()),
                title = "Ubicación registrada"
            )
        } else {
            Log.d("RouteMapView", "No hay puntos para mostrar: ${routePoints.size}")
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
