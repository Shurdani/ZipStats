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
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.RoundCap
import com.google.android.gms.maps.model.BitmapDescriptor
import android.content.res.Resources
import androidx.core.content.ContextCompat
import com.zipstats.app.R
import com.google.maps.android.SphericalUtil
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
                            
                            // NUEVO: Aplicar estilo personalizado al mapa
                            try {
                                val success = googleMap.setMapStyle(
                                    MapStyleOptions.loadRawResourceStyle(
                                        context,
                                        R.raw.map_style_light
                                    )
                                )
                                if (!success) {
                                    Log.e("CapturableMapView", "⚠️ El parseo del estilo falló")
                                } else {
                                    Log.d("CapturableMapView", "✅ Estilo de mapa aplicado correctamente")
                                }
                            } catch (e: Resources.NotFoundException) {
                                Log.e("CapturableMapView", "❌ No se encontró el archivo de estilo", e)
                            } catch (e: Exception) {
                                Log.e("CapturableMapView", "❌ Error al aplicar estilo del mapa", e)
                            }
                            
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
                                
                                // Crear polyline con degradado azul → violeta
                                createGradientPolyline(googleMap, routePoints, context)
                                
                                // Marcadores personalizados
                                val startMarker = createStartMarker(context)
                                val endMarker = createEndMarker(context)
                                
                                // Marcador de inicio (boton play personalizado) con rotación
                                val start = routePoints.first()
                                val next = routePoints[1]
                                val bearing = SphericalUtil.computeHeading(start, next).toFloat()
                                val adjustedBearing = (bearing - 90f) % 360 // compensamos que el icono apunta a la derecha
                                
                                googleMap.addMarker(
                                    MarkerOptions()
                                        .position(start)
                                        .title("Inicio de la ruta")
                                        .snippet("Punto de partida")
                                        .icon(startMarker)
                                        .anchor(0.5f, 0.5f) // Centrar el icono en la coordenada
                                        .rotation(adjustedBearing)
                                        .flat(true) // Hacer el marcador plano para que rote correctamente
                                )
                                
                                // Marcador de final (boton stop personalizado)
                                if (routePoints.size > 1) {
                                    googleMap.addMarker(
                                        MarkerOptions()
                                            .position(routePoints.last())
                                            .title("Final de la ruta")
                                            .snippet("Meta")
                                            .icon(endMarker)
                                            .anchor(0.5f, 0.5f) // Centrar el icono en la coordenada
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

/**
 * Crea un BitmapDescriptor personalizado para el marcador de inicio
 */
private fun createStartMarker(context: android.content.Context): BitmapDescriptor {
    // Cargar el drawable vectorial
    val drawable = ContextCompat.getDrawable(
        context,
        R.drawable.ic_marker_start
    ) ?: return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
    
    // Convertir a Bitmap
    val bitmap = android.graphics.Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * Crea una polyline con degradado azul → violeta y resplandor blanco
 */
private fun createGradientPolyline(
    googleMap: GoogleMap,
    routePoints: List<LatLng>,
    context: android.content.Context
) {
    if (routePoints.size < 2) return

    // Colores del degradado
    val startColor = 0xFF2979FF.toInt() // Azul (#2979FF)
    val endColor = 0xFF7E57C2.toInt()   // Violeta (#7E57C2)

    // Color del resplandor (blanco 25% opacidad)
    val glowColor = 0x40FFFFFF.toInt()

    // 1️⃣ Resplandor (debajo de todo)
    val glowPolyline = PolylineOptions()
        .addAll(routePoints)
        .color(glowColor)
        .width(25f)
        .jointType(JointType.ROUND)
        .startCap(RoundCap())
        .endCap(RoundCap())

    googleMap.addPolyline(glowPolyline)

    // 2️⃣ Polyline principal con degradado
    val totalSegments = routePoints.size - 1
    val colorSteps = 50 // número de tonos en el degradado

    for (i in 0 until totalSegments) {
        // Calculamos progreso proporcional al índice global (no solo 0..50)
        val progress = (i.toFloat() / totalSegments) * (colorSteps - 1) / (colorSteps - 1)

        val r = ((1 - progress) * ((startColor shr 16) and 0xFF) + progress * ((endColor shr 16) and 0xFF)).toInt()
        val g = ((1 - progress) * ((startColor shr 8) and 0xFF) + progress * ((endColor shr 8) and 0xFF)).toInt()
        val b = ((1 - progress) * (startColor and 0xFF) + progress * (endColor and 0xFF)).toInt()
        val segmentColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        val segmentPoints = listOf(routePoints[i], routePoints[i + 1])

        val mainPolyline = PolylineOptions()
            .addAll(segmentPoints)
            .color(segmentColor)
            .width(15f)
            .jointType(JointType.ROUND)
            .startCap(RoundCap())
            .endCap(RoundCap())

        googleMap.addPolyline(mainPolyline)
    }
}

/**
 * Crea un BitmapDescriptor personalizado para el marcador de fin
 */
private fun createEndMarker(context: android.content.Context): BitmapDescriptor {
    // Cargar el drawable vectorial
    val drawable = ContextCompat.getDrawable(
        context,
        R.drawable.ic_marker_end
    ) ?: return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
    
    // Convertir a Bitmap
    val bitmap = android.graphics.Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}