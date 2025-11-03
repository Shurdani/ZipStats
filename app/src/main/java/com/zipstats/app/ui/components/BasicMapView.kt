package com.zipstats.app.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zipstats.app.model.Route
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay

@Composable
fun BasicMapView(
    route: Route,
    modifier: Modifier = Modifier
) {
    var isMapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var showTimeout by remember { mutableStateOf(false) }
    var shouldReloadMap by remember { mutableStateOf(false) } // Flag para forzar recarga
    var mapKey by remember { mutableStateOf(0) } // Clave para forzar recreación
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Convertir puntos de la ruta a LatLng
    val routePoints = remember(route.points) {
        Log.d("BasicMapView", "📍 Puntos de ruta: ${route.points.size}")
        route.points.map { point ->
            LatLng(point.latitude, point.longitude)
        }
    }
    
    // Estado de la cámara
    val cameraPositionState = rememberCameraPositionState {
        if (routePoints.isNotEmpty()) {
            position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                routePoints.first(),
                14f
            )
        } else {
            // Posición por defecto en Barcelona
            position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                LatLng(41.3851, 2.1734),
                14f
            )
        }
    }
    
    // Configuración del mapa con opciones mejoradas
    val mapProperties = remember {
        MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = false
        )
    }
    
    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = true,
            zoomGesturesEnabled = true,
            scrollGesturesEnabled = true,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false,
            compassEnabled = true,
            myLocationButtonEnabled = false
        )
    }
    
    // Observar el ciclo de vida para recargar el mapa cuando la app vuelve del background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Cuando la app vuelve del background, recargar el mapa
                    Log.d("BasicMapView", "🔄 App resumida, recargando mapa...")
                    isMapLoaded = false
                    mapError = null
                    showTimeout = false
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
            Log.d("BasicMapView", "🔄 Recargando mapa...")
            shouldReloadMap = false // Resetear el flag
        }
    }
    
    // Timeout de 5 segundos para mostrar advertencia si el mapa no carga
    LaunchedEffect(Unit) {
        delay(5000)
        if (!isMapLoaded && mapError == null) {
            showTimeout = true
            Log.w("BasicMapView", "⚠️ Timeout: El mapa está tardando más de lo esperado")
        }
    }
    
    // Ajustar cámara cuando se carguen los puntos
    LaunchedEffect(routePoints, isMapLoaded) {
        if (routePoints.isNotEmpty() && isMapLoaded) {
            try {
                // Esperar un momento para que el mapa esté listo
                delay(300)
                
                // Si hay múltiples puntos, ajustar a todos los puntos
                if (routePoints.size > 1) {
                    val boundsBuilder = LatLngBounds.Builder()
                    routePoints.forEach { boundsBuilder.include(it) }
                    val bounds = boundsBuilder.build()
                    
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(bounds, 100)
                    )
                    Log.d("BasicMapView", "📐 Cámara ajustada a bounds de ${routePoints.size} puntos")
                } else {
                    // Solo un punto, hacer zoom simple
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(routePoints.first(), 15f)
                    )
                    Log.d("BasicMapView", "📐 Cámara ajustada a punto único")
                }
            } catch (e: Exception) {
                Log.e("BasicMapView", "❌ Error ajustando cámara: ${e.message}", e)
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Mapa
        key(mapKey) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapLoaded = {
                isMapLoaded = true
                showTimeout = false
                Log.d("BasicMapView", "✅ Mapa cargado correctamente")
            }
        ) {
            // Dibujar la línea de la ruta con estilo mejorado
            if (routePoints.size > 1) {
                Log.d("BasicMapView", "🎨 Dibujando polyline con ${routePoints.size} puntos")
                Polyline(
                    points = routePoints,
                    color = androidx.compose.ui.graphics.Color(0xFF39FF14), // Verde lima brillante
                    width = 15f // Grosor aumentado
                )
                
                // Marcador de inicio (verde)
                Marker(
                    state = MarkerState(position = routePoints.first()),
                    title = "Inicio de la ruta",
                    snippet = "Punto de partida"
                )
                
                // Marcador de final (rojo)
                if (routePoints.size > 1) {
                    Marker(
                        state = MarkerState(position = routePoints.last()),
                        title = "Final de la ruta",
                        snippet = "Punto de llegada"
                    )
                }
                
                Log.d("BasicMapView", "📍 Marcadores añadidos: inicio y final")
            }
        }
        }
        
        // Indicador de carga
        if (!isMapLoaded && mapError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cargando mapa...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (showTimeout) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Esto está tardando más de lo normal.\nVerifica tu conexión a internet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        // Mensaje de error
        mapError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Error al cargar el mapa",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // Indicador de puntos cuando el mapa está cargado
        if (isMapLoaded && routePoints.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Sin puntos",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sin puntos GPS en esta ruta",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
