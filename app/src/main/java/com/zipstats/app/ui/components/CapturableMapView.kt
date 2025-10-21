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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zipstats.app.model.Route
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlinx.coroutines.delay

@Composable
fun CapturableMapView(
    route: Route,
    onMapReady: ((com.google.android.gms.maps.GoogleMap) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isMapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var showTimeout by remember { mutableStateOf(false) }
    var shouldReloadMap by remember { mutableStateOf(false) } // Flag para forzar recarga
    var mapInstance by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    var mapKey by remember { mutableStateOf(0) } // Clave para forzar recreación
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Convertir puntos de la ruta a LatLng
    val routePoints = remember(route.points) {
        Log.d("CapturableMapView", "📍 Puntos de ruta: ${route.points.size}")
        route.points.map { point ->
            LatLng(point.latitude, point.longitude)
        }
    }
    
    // Observar el ciclo de vida para recargar el mapa cuando la app vuelve del background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Cuando la app vuelve del background, recargar el mapa
                    Log.d("CapturableMapView", "🔄 App resumida, recargando mapa...")
                    isMapLoaded = false
                    mapError = null
                    showTimeout = false
                    mapInstance = null // Limpiar instancia del mapa
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
            Log.d("CapturableMapView", "🔄 Recargando mapa...")
            // Forzar recreación del AndroidView
            shouldReloadMap = false // Resetear el flag
        }
    }
    
    // Timeout de 5 segundos para mostrar advertencia si el mapa no carga
    LaunchedEffect(Unit) {
        delay(5000)
        if (!isMapLoaded && mapError == null) {
            showTimeout = true
            Log.w("CapturableMapView", "⚠️ Timeout: El mapa está tardando más de lo esperado")
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Mapa usando AndroidView para acceso directo al GoogleMap
        key(mapKey) {
            AndroidView(
                factory = { context ->
                MapView(context).apply {
                    onCreate(null)
                    getMapAsync { googleMap ->
                        try {
                            isMapLoaded = true
                            showTimeout = false
                            mapInstance = googleMap
                            Log.d("CapturableMapView", "✅ Mapa cargado correctamente")
                            
                            // Configurar el mapa
                            googleMap.uiSettings.apply {
                                isZoomControlsEnabled = true
                                isZoomGesturesEnabled = true
                                isScrollGesturesEnabled = true
                                isTiltGesturesEnabled = false
                                isRotateGesturesEnabled = false
                                isCompassEnabled = true
                                isMyLocationButtonEnabled = false
                            }
                            
                            // Dibujar la ruta
                            if (routePoints.isNotEmpty()) {
                                Log.d("CapturableMapView", "🎨 Dibujando polyline con ${routePoints.size} puntos")
                                
                                // Dibujar polyline
                                val polylineOptions = PolylineOptions()
                                    .addAll(routePoints)
                                    .color(0xFF2196F3.toInt())
                                    .width(10f)
                                googleMap.addPolyline(polylineOptions)
                                
                                // Marcador de inicio (punto verde)
                                googleMap.addMarker(
                                    MarkerOptions()
                                        .position(routePoints.first())
                                        .title("Inicio de la ruta")
                                        .snippet("Punto de partida")
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                                )
                                
                                // Marcador de final (bandera a cuadros - rojo)
                                if (routePoints.size > 1) {
                                    googleMap.addMarker(
                                        MarkerOptions()
                                            .position(routePoints.last())
                                            .title("Final de la ruta")
                                            .snippet("Meta")
                                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                    )
                                }
                                
                                // Ajustar cámara a la ruta
                                if (routePoints.size > 1) {
                                    val boundsBuilder = LatLngBounds.Builder()
                                    routePoints.forEach { boundsBuilder.include(it) }
                                    val bounds = boundsBuilder.build()
                                    googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                                    Log.d("CapturableMapView", "📐 Cámara ajustada a bounds de ${routePoints.size} puntos")
                                } else {
                                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(routePoints.first(), 15f))
                                    Log.d("CapturableMapView", "📐 Cámara ajustada a punto único")
                                }
                                
                                Log.d("CapturableMapView", "📍 Marcadores añadidos: inicio y final")
                            } else {
                                // Posición por defecto en Barcelona
                                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    LatLng(41.3851, 2.1734), 14f
                                ))
                            }
                            
                            // Llamar al callback cuando el mapa esté listo
                            onMapReady?.invoke(googleMap)
                            
                        } catch (e: Exception) {
                            Log.e("CapturableMapView", "❌ Error configurando mapa: ${e.message}", e)
                            mapError = e.message
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
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
