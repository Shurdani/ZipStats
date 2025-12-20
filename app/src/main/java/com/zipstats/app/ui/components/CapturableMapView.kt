// Kotlin code for CapturableMapView with Mapbox 11.8.0
// Complete refactor implementing bounding box, padding, day/night, clean render, markers,
// polyline gradient, cameraForCoordinates fit, etc.

// NOTE: Replace package name as needed
package com.zipstats.app.ui.components

// Si usas la solución de abajo, añade también este:
import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.turf.TurfMeasurement
import com.zipstats.app.R
import com.zipstats.app.model.Route

// Typealias para simplificar el tipo de función compleja del snapshot
typealias MapSnapshotTrigger = ((Bitmap?) -> Unit) -> Unit

@Composable
fun CapturableMapView(
    route: Route,
    onMapReady: ((MapView) -> Unit)? = null,
    onSnapshotHandlerReady: ((MapSnapshotTrigger) -> Unit)? = null, // Handler para capturar snapshot
    modifier: Modifier = Modifier,
    isCompact: Boolean = false, // Si es true, usa marcadores más grandes para mapas pequeños
    mapStyle: String = Style.MAPBOX_STREETS, // Estilo del mapa, por defecto MAPBOX_STREETS
    onStyleLoaded: ((Style) -> Unit)? = null // Callback para modificar el estilo después de cargarlo
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isMapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var showTimeout by remember { mutableStateOf(false) }
    var mapKey by remember { mutableStateOf(0) }
    var shouldReloadMap by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Convert route points to Mapbox Points
    val routePoints = remember(route.points) {
        route.points.map { p -> Point.fromLngLat(p.longitude, p.latitude) }
    }
    
    // Handle lifecycle to reload map
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                    isMapLoaded = false
                    mapError = null
                    showTimeout = false
                mapKey++
                shouldReloadMap = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Timeout warning
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5000)
        if (!isMapLoaded && mapError == null) showTimeout = true
    }

    val isDarkTheme = isSystemInDarkTheme()

    // 1. Variable para recordar si ya hemos centrado esta ruta específica (para no bloquear al usuario si mueve el mapa)
    var hasCenteredInitialState by remember(route.points) { mutableStateOf(false) }

    // 2. EFECTO REACTIVO: Este es el "seguro de vida". 
    // Se ejecuta cuando el mapa está listo O cuando cambian los puntos.
    LaunchedEffect(mapViewRef, routePoints) {
        val map = mapViewRef ?: return@LaunchedEffect
        
        if (routePoints.isNotEmpty() && !hasCenteredInitialState) {
            fitCameraToRoute(map, routePoints, isCompact)
            hasCenteredInitialState = true // Marcamos como centrado para esta ruta
        }
    }


    


    // Render area
    Box(modifier = modifier.fillMaxSize()) {
        key(mapKey) {
            AndroidView(
                factory = { ctx ->
                    val mapView = LayoutInflater.from(ctx)
                        .inflate(R.layout.mapview_no_attribution, null) as MapView

                    mapViewRef = mapView

                    val styleUri = mapStyle // Usar el estilo pasado como parámetro

                    mapView.mapboxMap.loadStyle(styleUri) { style ->
                        try {
                            isMapLoaded = true
                            configureMapUI(mapView)
                            
                            // Permitir modificar el estilo antes de agregar nuestras capas
                            onStyleLoaded?.invoke(style)
                            
                            addGradientPolyline(style, routePoints)
                            addMarkers(ctx, style, routePoints, isCompact)
                            fitCameraToRoute(mapView, routePoints, isCompact)
                            
                            // Preparar la función de snapshot y enviarla hacia arriba
                            val snapshotTrigger: ((Bitmap?) -> Unit) -> Unit = { callback ->
                                android.util.Log.d("CapturableMapView", "=== INICIO CAPTURA SNAPSHOT ===")
                                android.util.Log.d("CapturableMapView", "Dimensiones iniciales: ${mapView.width}x${mapView.height}")
                                
                                try {
                                    // Validar que el mapa tenga dimensiones válidas
                                    if (mapView.width <= 0 || mapView.height <= 0) {
                                        android.util.Log.e("CapturableMapView", "MapView tiene dimensiones inválidas: ${mapView.width}x${mapView.height}")
                                        callback(null)
                                    } else {
                                        // Esperar más tiempo para asegurar que el mapa esté completamente renderizado y visible
                                        // Primero esperamos a que el view esté completamente medido y visible
                                        mapView.post {
                                            // Luego esperamos un poco más para que los tiles se rendericen
                                            mapView.postDelayed({
                                                try {
                                                    android.util.Log.d("CapturableMapView", "Intentando capturar después de delays")
                                                    android.util.Log.d("CapturableMapView", "Dimensiones después de delays: ${mapView.width}x${mapView.height}")
                                                    
                                                    // Validar nuevamente después de la espera
                                                    if (mapView.width <= 0 || mapView.height <= 0) {
                                                        android.util.Log.e("CapturableMapView", "MapView aún tiene dimensiones inválidas después de esperar")
                                                        callback(null)
                                                    } else if (!mapView.isShown || mapView.visibility != android.view.View.VISIBLE) {
                                                        android.util.Log.e("CapturableMapView", "MapView no está visible: isShown=${mapView.isShown}, visibility=${mapView.visibility}")
                                                        callback(null)
                                                    } else {
                                                        android.util.Log.d("CapturableMapView", "Usando API nativa de Mapbox snapshot()")
                                                        android.util.Log.d("CapturableMapView", "Dimensiones del MapView: ${mapView.width}x${mapView.height}")
                                                        
                                                        // Usar la API nativa de Mapbox para capturar el mapa
                                                        // Esto captura solo el contenido del mapa, no toda la ventana
                                                        try {
                                                            mapView.snapshot { snapshotBitmap ->
                                                                if (snapshotBitmap == null) {
                                                                    android.util.Log.e("CapturableMapView", "Mapbox snapshot retornó null")
                                                                    // Fallback: usar draw() directamente
                                                                    try {
                                                                        val fallbackBitmap = Bitmap.createBitmap(
                                                                            mapView.width,
                                                                            mapView.height,
                                                                            Bitmap.Config.ARGB_8888
                                                                        )
                                                                        val canvas = android.graphics.Canvas(fallbackBitmap)
                                                                        mapView.draw(canvas)
                                                                        android.util.Log.d("CapturableMapView", "Fallback draw() exitoso: ${fallbackBitmap.width}x${fallbackBitmap.height}")
                                                                        callback(fallbackBitmap)
                                                                    } catch (e: Exception) {
                                                                        android.util.Log.e("CapturableMapView", "Error en fallback draw(): ${e.message}", e)
                                                                        callback(null)
                                                                    }
                                                                } else {
                                                                    android.util.Log.d("CapturableMapView", "Mapbox snapshot exitoso: ${snapshotBitmap.width}x${snapshotBitmap.height}")
                                                                    callback(snapshotBitmap)
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            android.util.Log.e("CapturableMapView", "Error al llamar snapshot(): ${e.message}", e)
                                                            // Fallback: usar draw() directamente
                                                            try {
                                                                val fallbackBitmap = Bitmap.createBitmap(
                                                                    mapView.width,
                                                                    mapView.height,
                                                                    Bitmap.Config.ARGB_8888
                                                                )
                                                                val canvas = android.graphics.Canvas(fallbackBitmap)
                                                                mapView.draw(canvas)
                                                                android.util.Log.d("CapturableMapView", "Fallback draw() después de error: ${fallbackBitmap.width}x${fallbackBitmap.height}")
                                                                callback(fallbackBitmap)
                                                            } catch (e2: Exception) {
                                                                android.util.Log.e("CapturableMapView", "Error en fallback draw(): ${e2.message}", e2)
                                                                callback(null)
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("CapturableMapView", "Error al capturar snapshot: ${e.message}", e)
                                                    e.printStackTrace()
                                                    callback(null)
                                                }
                                            }, 1000) // Esperar 1000ms para asegurar que el mapa esté completamente renderizado
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("CapturableMapView", "Error al preparar snapshot: ${e.message}", e)
                                    callback(null)
                                }
                            }
                            onSnapshotHandlerReady?.invoke(snapshotTrigger)
                            
                            onMapReady?.invoke(mapView)
                        } catch (e: Exception) {
                            mapError = e.message
                        }
                    }

                    mapView
                },
                update = { mapView ->
                    // ESTO SE EJECUTA EN CADA RECOMPOSICIÓN
                    // Si por lo que sea el LaunchedEffect falló, esto lo atrapa.
                    if (routePoints.isNotEmpty() && !hasCenteredInitialState) {
                        fitCameraToRoute(mapView, routePoints, isCompact)
                        hasCenteredInitialState = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // --- Loading overlay ---
        if (!isMapLoaded && mapError == null) LoadingOverlay(showTimeout)

        // --- Error overlay ---
        mapError?.let { ErrorOverlay(it) }

        // --- No points overlay ---
        if (isMapLoaded && routePoints.isEmpty()) NoPointsOverlay()
    }
}

// --- UI Overlays ---

@Composable
private fun LoadingOverlay(showTimeout: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            ZipStatsText("Cargando mapa…")
                    if (showTimeout) {
                Spacer(Modifier.height(8.dp))
                        ZipStatsText(
                    "Esto está tardando más de lo normal.\nVerifica tu conexión.",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
@Composable
private fun ErrorOverlay(error: String) {
            Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            ZipStatsText("Error al cargar el mapa")
            Spacer(Modifier.height(4.dp))
            ZipStatsText(error, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun NoPointsOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            ZipStatsText("Sin puntos GPS en esta ruta")
        }
    }
}

// --- Map UI configuration ---

private fun configureMapUI(mapView: MapView) {
    // Disable user interactions
    mapView.gestures.rotateEnabled = false
    mapView.gestures.pitchEnabled = false
    mapView.gestures.scrollEnabled = false
    // zoomEnabled no existe en esta versión de Mapbox, se controla con scrollEnabled

    // Disable built-in UI
    mapView.compass.enabled = false
    mapView.scalebar.enabled = false

    // Deshabilitar logo y botón de información para evitar crashes
    mapView.logo.enabled = false
    mapView.attribution.enabled = false
}

// --- Add gradient polyline ---

private fun addGradientPolyline(style: Style, points: List<Point>) {
    if (points.size < 2) return

    val glow = geoJsonSource("route-glow-source") {
        geometry(LineString.fromLngLats(points))
        lineMetrics(true)
    }
    style.addSource(glow)

    style.addLayer(
        lineLayer("route-glow-layer", "route-glow-source") {
            lineColor(0x40FFFFFF.toInt())
            lineWidth(24.0)
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
        }
    )

    val main = geoJsonSource("route-main-source") {
        geometry(LineString.fromLngLats(points))
        lineMetrics(true) // Necesario para que funcione el gradiente
    }
    style.addSource(main)

    style.addLayer(
        lineLayer("route-main-layer", "route-main-source") {
            lineWidth(8.0)
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
            // Degradado de azul (#2979FF) a violeta (#7E57C2) - colores de los marcadores
            lineGradient(
                Expression.interpolate {
                    linear()
                    lineProgress()
                    stop {
                        literal(0.0)
                        color(0xFF2979FF.toInt())
                    }
                    stop {
                        literal(1.0)
                        color(0xFF7E57C2.toInt())
                    }
                }
            )
        }
    )
}

// --- Add start/end markers ---

private fun addMarkers(context: Context, style: Style, points: List<Point>, isCompact: Boolean = false) {
    if (points.size < 2) return

    // Tamaño uniforme de iconos para ambos contextos
    val iconSize = 96
    
    val startDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_marker_start, context.theme)
    val endDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_marker_end, context.theme)

    val startIcon = startDrawable?.toBitmap(iconSize, iconSize)
    val endIcon = endDrawable?.toBitmap(iconSize, iconSize)
    
    // Escala 1.0 para ambos contextos (mapa pequeño y grande)
    val iconScale = 1.0

    if (startIcon != null) style.addImage("start_marker", startIcon)
    if (endIcon != null) style.addImage("end_marker", endIcon)

    // CÁLCULO: Orientación del primer segmento para rotar el icono
    val initialBearing = TurfMeasurement.bearing(points[0], points[1])

    // Start marker
    style.addSource(
        geoJsonSource("start-marker-source") {
            feature(Feature.fromGeometry(points.first()))
        }
    )

    style.addLayer(
        symbolLayer("start-marker-layer", "start-marker-source") {
            iconImage("start_marker")
            iconSize(iconScale)
            iconAnchor(IconAnchor.CENTER)
            iconAllowOverlap(true)
            iconIgnorePlacement(true)
            // AQUÍ LA MAGIA: Rotamos el icono
            iconRotate(initialBearing) 
            // Si tu icono apunta hacia arriba por defecto, usa: initialBearing
            // Si apunta hacia la derecha, resta 90: (initialBearing - 90.0)
        }
    )

    // End marker (Sin rotación o rotación final si quieres)
    style.addSource(
        geoJsonSource("end-marker-source") {
            feature(Feature.fromGeometry(points.last()))
        }
    )

    style.addLayer(
        symbolLayer("end-marker-layer", "end-marker-source") {
            iconImage("end_marker")
            iconSize(iconScale)
            iconAnchor(IconAnchor.CENTER)
            iconAllowOverlap(true)
        }
    )
}

// --- Fit camera to entire route with padding ---

private fun fitCameraToRoute(mapView: MapView, points: List<Point>, isCompact: Boolean = false) {
    if (points.size < 2) return

    val mapboxMap = mapView.mapboxMap

    // 1. ARREGLO DE TAMAÑO: Padding ajustado según el contexto
    val pixelDensity = mapView.context.resources.displayMetrics.density
    
    // En mapa compacto (pequeño) usar padding uniforme y pequeño para ver toda la ruta
    // En pantalla completa, usar más padding inferior por la tarjeta superpuesta
    val topPadding = if (isCompact) 16.0 * pixelDensity else 32.0 * pixelDensity
    val sidePadding = if (isCompact) 16.0 * pixelDensity else 32.0 * pixelDensity
    val bottomPadding = if (isCompact) 16.0 * pixelDensity else 200.0 * pixelDensity
    
    // 2. CÁLCULO DE ROTACIÓN INTELIGENTE (solo para rutas verticales)
    val start = points.first()
    val end = points.last()
    val routeBearing = TurfMeasurement.bearing(start, end)
    
    // Normalizar bearing a 0-360
    val normalizedBearing = ((routeBearing % 360) + 360) % 360
    
    // Detectar si la ruta es vertical (orientación norte-sur)
    // Consideramos vertical si el bearing está cerca de 0° (norte) o 180° (sur)
    // o cerca de 90° (este) o 270° (oeste) - pero en realidad, vertical es 0° o 180°
    val isVertical = (normalizedBearing < 45.0 || normalizedBearing > 315.0) || // Norte (0°)
                     (normalizedBearing > 135.0 && normalizedBearing < 225.0)   // Sur (180°)
    
    // Solo rotar si la ruta es vertical para hacerla horizontal
    val targetCameraBearing = if (isVertical) {
        routeBearing - 90.0
    } else {
        0.0 // Sin rotación para rutas horizontales
    }

    val camera = mapboxMap.cameraForCoordinates(
        points,
        EdgeInsets(topPadding, sidePadding, bottomPadding, sidePadding), // Padding inferior aumentado
        bearing = targetCameraBearing, 
        pitch = 0.0
    )

    // ¡IMPORTANTE! Usamos setCamera para que sea INSTANTÁNEO
    mapboxMap.setCamera(camera) 
}