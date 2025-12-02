package com.zipstats.app.ui.routes

import android.media.MediaPlayer
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.turf.TurfMeasurement
import com.zipstats.app.R
import com.zipstats.app.map.RouteAnimator
import com.zipstats.app.model.Route

@Composable
fun RouteAnimationDialog(
    route: Route,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // Convertir puntos con velocidad
    val routePoints = remember(route.points) {
        route.points.map { p -> Point.fromLngLat(p.longitude, p.latitude) }
    }
    
    // Puntos con velocidad para el animator (speed en m/s, se convertirá a km/h)
    val routePointsWithSpeed = remember(route.points) {
        route.points.map { it.speed } // Lista de velocidades en m/s
    }
    
    // Estados de UI
    var isPlaying by remember { mutableStateOf(false) } // Empieza pausado para cargar
    var isSpeed2x by remember { mutableStateOf(false) } // Muestra "1x" inicialmente (velocidad real es 2x)
    var animator by remember { mutableStateOf<RouteAnimator?>(null) }
    
    // Referencia al MediaPlayer
    val mediaPlayer = remember { 
        // IMPORTANTE: Asegúrate de tener 'cinematic_music.mp3' en res/raw/
        // Si falla la creación (ej. archivo no existe), devuelve null y no crashea
        try {
            MediaPlayer.create(context, R.raw.cinematic_music).apply {
                isLooping = true
                setVolume(0.5f, 0.5f) // Volumen al 50% para no aturdir
            }
        } catch (e: Exception) { 
            android.util.Log.e("RouteAnimation", "Error al cargar música: ${e.message}", e)
            null 
        }
    }
    
    // Referencia mutable al estilo
    val mapStyleRef = remember { mutableStateOf<Style?>(null) }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            
            // 1. MAPA
            AndroidView(
                factory = { ctx ->
                    val mapView = LayoutInflater.from(ctx)
                        .inflate(R.layout.mapview_no_attribution, null) as MapView
                    
                    // Ajustes UI Mapa
                    mapView.gestures.rotateEnabled = true
                    mapView.gestures.pitchEnabled = true
                    mapView.gestures.scrollEnabled = true // Permitimos mover, pero el animador recentrará
                    mapView.compass.enabled = false
                    mapView.scalebar.enabled = false
                    
                    // Deshabilitar logo y botón de información para evitar crashes
                    mapView.logo.enabled = false
                    mapView.attribution.enabled = false

                    if (routePoints.isNotEmpty()) {
                        val startPoint = routePoints.first()
                        val initialCamera = com.mapbox.maps.CameraOptions.Builder()
                            .center(startPoint)
                            .zoom(16.0)
                            .build()
                        
                        mapView.mapboxMap.setCamera(initialCamera)
                    }
                    
                    mapView.mapboxMap.loadStyleUri(Style.SATELLITE) { style ->
                        mapStyleRef.value = style

                        // Style.SATELLITE no incluye terreno por defecto, así que no es necesario desactivarlo
                        // El mapa satelital será plano y limpio sin ondulaciones

                        if (routePoints.size >= 2) {
                            // FUENTES Y CAPAS
                            val routeSource = geoJsonSource("route-source") {
                                geometry(com.mapbox.geojson.LineString.fromLngLats(routePoints))
                                lineMetrics(true)
                            }
                            style.addSource(routeSource)
                            
                            // Polyline estática (oculta al inicio, se mostrará al final si es necesario)
                            style.addLayer(
                                lineLayer("route-layer", "route-source") {
                                    lineWidth(6.0)
                                    lineCap(LineCap.ROUND)
                                    lineJoin(LineJoin.ROUND)
                                    lineColor("#FFC107") // Amarillo deportivo tipo "Tour de France"
                                    visibility(Visibility.NONE) // Ocultar al inicio, solo el trail se mostrará
                                }
                            )
                            
                            // TRAIL ANIMADO (línea que se dibuja progresivamente)
                            // Inicializar con el primer punto duplicado (no se verá como línea hasta que haya 2 puntos diferentes)
                            val initialTrailPoint = if (routePoints.isNotEmpty()) routePoints.first() else null
                            val trailSource = geoJsonSource("trail-source") {
                                if (initialTrailPoint != null) {
                                    geometry(com.mapbox.geojson.LineString.fromLngLats(listOf(initialTrailPoint, initialTrailPoint)))
                                } else {
                                    geometry(com.mapbox.geojson.LineString.fromLngLats(listOf()))
                                }
                            }
                            style.addSource(trailSource)
                            
                            style.addLayer(
                                lineLayer("trail-layer", "trail-source") {
                                    lineWidth(6.0)
                                    lineCap(LineCap.ROUND)
                                    lineJoin(LineJoin.ROUND)
                                    lineColor("#FFD600") // Amarillo brillante estilo Relive
                                }
                            )
                            
                            // Imágenes de marcadores
                            val iconSize = 96
                            val vehicleDrawable = ResourcesCompat.getDrawable(ctx.resources, R.drawable.ic_point, ctx.theme)
                            vehicleDrawable?.toBitmap(iconSize, iconSize)?.let { style.addImage("vehicle_marker", it) }
                            
                            // Vehículo
                            val initialPoint = routePoints.first()
                            style.addSource(geoJsonSource("vehicle-marker-source") {
                                feature(Feature.fromGeometry(initialPoint))
                            })
                            style.addLayer(symbolLayer("vehicle-marker-layer", "vehicle-marker-source") {
                                iconImage("vehicle_marker")
                                iconSize(0.5) // Marcador pequeño
                                iconAnchor(IconAnchor.CENTER)
                                iconAllowOverlap(true)
                                iconIgnorePlacement(true)
                            })

                            // INICIALIZAR ANIMADOR
                            val routeAnimator = RouteAnimator(
                                mapboxMap = mapView.mapboxMap,
                                route = routePoints,
                                speeds = routePointsWithSpeed, // Velocidades en m/s
                                onMarkerPositionChanged = { point, bearing, progress, traveledPoints, currentSpeedKmh ->
                                    mapStyleRef.value?.let { currentStyle ->
                                        // Actualizar Vehículo
                                        val vehicleSource = currentStyle.getSource("vehicle-marker-source") as? GeoJsonSource
                                        vehicleSource?.feature(Feature.fromGeometry(point))
                                        
                                        val vehicleLayer = currentStyle.getLayer("vehicle-marker-layer") as? SymbolLayer
                                        vehicleLayer?.iconRotate(bearing)
                                        
                                        // Actualizar TRAIL animado (línea que se dibuja progresivamente)
                                        // Solo actualizar si hay al menos 2 puntos (necesario para una línea válida)
                                        val trailSource = currentStyle.getSource("trail-source") as? GeoJsonSource
                                        if (traveledPoints.size >= 2) {
                                            trailSource?.geometry(com.mapbox.geojson.LineString.fromLngLats(traveledPoints))
                                        } else if (traveledPoints.size == 1) {
                                            // Si solo hay 1 punto, usar punto duplicado (no se verá como línea)
                                            val singlePoint = traveledPoints.first()
                                            trailSource?.geometry(com.mapbox.geojson.LineString.fromLngLats(listOf(singlePoint, singlePoint)))
                                        }
                                        // Si no hay puntos, no actualizar (mantener el estado anterior)
                                    }
                                },
                                onAnimationEnd = {
                                    // Cuando la animación termine, detener la música y actualizar el estado
                                    if (mediaPlayer?.isPlaying == true) {
                                        mediaPlayer?.pause()
                                    }
                                    isPlaying = false
                                }
                            )
                            animator = routeAnimator
                            
                            // Auto-Start después de un segundo
                            mapView.postDelayed({
                                routeAnimator.startAnimation()
                                isPlaying = true
                                mediaPlayer?.start()
                            }, 1000)
                        }
                    }
                    mapView
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // 2. BOTÓN CERRAR (Top Right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .zIndex(20f)
            ) {
                IconButton(
                    onClick = onDismiss, 
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
            
            // 3. BARRA DE CONTROL CINEMÁTICA (Bottom)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(180.dp) // Altura del degradado
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, // Controles separados
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    
                    // BOTÓN VELOCIDAD (Izquierda)
                    FloatingActionButton(
                        onClick = {
                            isSpeed2x = !isSpeed2x
                            animator?.toggleSpeed()
                        },
                        containerColor = if (isSpeed2x) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                        contentColor = if (isSpeed2x) Color.Black else Color.White,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (isSpeed2x) "2x" else "1x",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // BOTÓN PLAY/PAUSE (Centro - Grande)
                    FloatingActionButton(
                        onClick = {
                            if (isPlaying) {
                                animator?.pauseAnimation()
                                if (mediaPlayer?.isPlaying == true) mediaPlayer.pause()
                                isPlaying = false
                            } else {
                                // Si la animación ya terminó, reiniciar desde el principio
                                if (animator?.isAnimationComplete() == true) {
                                    animator?.stopAnimation(resetIndex = true)
                                    // Reiniciar marcador y trail a posición inicial
                                    if (routePoints.isNotEmpty()) {
                                        mapStyleRef.value?.let { currentStyle ->
                                            val vehicleSource = currentStyle.getSource("vehicle-marker-source") as? GeoJsonSource
                                            vehicleSource?.feature(Feature.fromGeometry(routePoints.first()))
                                            
                                            val vehicleLayer = currentStyle.getLayer("vehicle-marker-layer") as? SymbolLayer
                                            if (routePoints.size > 1) {
                                                val initialBearing = TurfMeasurement.bearing(routePoints[0], routePoints[1])
                                                vehicleLayer?.iconRotate(initialBearing)
                                            }
                                            
                                            // Reiniciar trail (con primer punto duplicado, no se verá como línea)
                                            val trailSource = currentStyle.getSource("trail-source") as? GeoJsonSource
                                            val firstPoint = routePoints.first()
                                            trailSource?.geometry(com.mapbox.geojson.LineString.fromLngLats(listOf(firstPoint, firstPoint)))
                                        }
                                    }
                                    animator?.startAnimation(0f)
                                } else {
                                    animator?.resumeAnimation()
                                }
                                mediaPlayer?.start()
                                isPlaying = true
                            }
                        },
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // BOTÓN REINICIAR (Derecha)
                    FloatingActionButton(
                        onClick = { 
                            // Detener animación y reiniciar posición
                            animator?.stopAnimation(resetIndex = true)
                            
                            // Pausar música si está reproduciéndose
                            if (mediaPlayer?.isPlaying == true) {
                                mediaPlayer?.pause()
                            }
                            
                            // Actualizar estado
                            isPlaying = false
                            
                            // Reiniciar marcador y trail a posición inicial
                            if (routePoints.isNotEmpty()) {
                                mapStyleRef.value?.let { currentStyle ->
                                    val vehicleSource = currentStyle.getSource("vehicle-marker-source") as? GeoJsonSource
                                    vehicleSource?.feature(Feature.fromGeometry(routePoints.first()))
                                    
                                    val vehicleLayer = currentStyle.getLayer("vehicle-marker-layer") as? SymbolLayer
                                    if (routePoints.size > 1) {
                                        val initialBearing = TurfMeasurement.bearing(routePoints[0], routePoints[1])
                                        vehicleLayer?.iconRotate(initialBearing)
                                    }
                                    
                                    // Reiniciar trail (vacío)
                                    val trailSource = currentStyle.getSource("trail-source") as? GeoJsonSource
                                    trailSource?.geometry(com.mapbox.geojson.LineString.fromLngLats(listOf()))
                                }
                            }
                        },
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Refresh, "Reiniciar")
                    }
                }
            }
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            animator?.stopAnimation()
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer.stop()
                }
                mediaPlayer?.release()
            } catch (e: Exception) {
                android.util.Log.e("RouteAnimation", "Error al liberar MediaPlayer: ${e.message}", e)
            }
        }
    }
}
