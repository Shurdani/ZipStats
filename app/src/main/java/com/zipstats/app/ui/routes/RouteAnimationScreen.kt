package com.zipstats.app.ui.routes

import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.zipstats.app.ui.components.ZipStatsText
import androidx.compose.ui.zIndex
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.hbisoft.hbrecorder.HBRecorder
import com.hbisoft.hbrecorder.HBRecorderListener
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zipstats.app.R
import com.zipstats.app.map.RouteAnimator
import com.zipstats.app.model.Route
import com.zipstats.app.model.VehicleType
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.ui.components.HideSystemBarsEffect
import com.zipstats.app.ui.components.RouteSummaryCard
import com.zipstats.app.utils.CityUtils
import com.zipstats.app.ui.components.RouteSummaryCardFromRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RouteAnimationDialog(
    route: Route,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Obtener Activity desde el contexto (necesario para servicios en primer plano en Android 12+)
    val activity = context as? ComponentActivity
    
    // Scope de corrutinas para delays
    val scope = rememberCoroutineScope()
    
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
    var isRecording by remember { mutableStateOf(false) } // Controla si ocultamos UI durante grabación
    var isSpeed2x by remember { mutableStateOf(false) } // Muestra "1x" inicialmente (velocidad real es 2x)
    var animator by remember { mutableStateOf<RouteAnimator?>(null) }
    var vehicleType by remember { mutableStateOf<VehicleType?>(null) }
    
    // Repositorio de vehículos para obtener el tipo
    val vehicleRepository = remember {
        VehicleRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance())
    }
    
    // Obtener tipo de vehículo
    LaunchedEffect(route.scooterId) {
        try {
            val vehicle = vehicleRepository.getUserVehicles().find { it.id == route.scooterId }
            vehicleType = vehicle?.vehicleType
        } catch (e: Exception) {
            vehicleType = null
        }
    }
    
    // Referencia al MediaPlayer
    val mediaPlayer = remember { 
        // IMPORTANTE: Asegúrate de tener 'cinematic_music.mp3' en res/raw/
        // Si falla la creación (ej. archivo no existe), devuelve null y no crashea
        try {
            MediaPlayer.create(context, R.raw.cinematic_music).apply {
                isLooping = true
                setVolume(1.0f, 1.0f) // Volumen al máximo para la grabación
            }
        } catch (e: Exception) { 
            android.util.Log.e("RouteAnimation", "Error al cargar música: ${e.message}", e)
            null 
        }
    }
    
    // --- CONFIGURACIÓN HBRECORDER ---
    // Usar activity si está disponible, sino usar context (para compatibilidad)
    val hbRecorder = remember { 
        val recorderContext = activity ?: context
        HBRecorder(recorderContext, object : HBRecorderListener {
            override fun HBRecorderOnStart() {
                // 4. ¡GRABANDO! Arrancamos el show
                android.util.Log.d("Recorder", "Grabación iniciada")
                // Pequeño delay para asegurar que el overlay de grabación del sistema se quite
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    animator?.startAnimation(0f) // Empezar desde 0
                    mediaPlayer?.seekTo(0)
                    mediaPlayer?.start()
                    isPlaying = true
                }, 500)
            }

            override fun HBRecorderOnComplete() {
                android.util.Log.d("Recorder", "Grabación completada")
                isRecording = false
                isPlaying = false
                // Aquí podrías mostrar un Toast con la ruta del archivo o abrir compartir
                Toast.makeText(context, "Vídeo guardado en Galería", Toast.LENGTH_LONG).show()
            }

            override fun HBRecorderOnError(errorCode: Int, reason: String?) {
                android.util.Log.e("Recorder", "Error: $errorCode - $reason")
                isRecording = false
                isPlaying = false
                // Mostrar mensaje de error más detallado
                val errorMessage = when (errorCode) {
                    38 -> "Error: El MediaRecorder no está configurado correctamente"
                    100 -> "Error: No se pudo crear el archivo de vídeo"
                    else -> "Error al grabar (código $errorCode): ${reason ?: "Error desconocido"}"
                }
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
            
            override fun HBRecorderOnPause() {
                android.util.Log.d("Recorder", "Grabación pausada")
                // Pausar música si está reproduciéndose
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                }
            }
            
            override fun HBRecorderOnResume() {
                android.util.Log.d("Recorder", "Grabación reanudada")
                // Reanudar música si estaba pausada
                if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
                    mediaPlayer?.start()
                }
            }
        })
    }
    
    // --- LANZADOR DE PERMISOS ---
    val startMediaProjection = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            // 3. Permiso concedido -> Configuramos y empezamos a grabar
            android.util.Log.d("Recorder", "Permiso concedido, configurando grabador...")
            
            try {
                // Configuración básica (siempre disponible)
                android.util.Log.d("Recorder", "Configurando audio...")
                // Deshabilitar audio para evitar problemas con permisos en Android 14+
                // La música se grabará desde el sistema si es necesario
                hbRecorder.isAudioEnabled(false) // No grabar audio del sistema
                
                // Configurar nombre de archivo
                android.util.Log.d("Recorder", "Configurando nombre de archivo...")
                val fileName = "Ruta_${route.id}_${System.currentTimeMillis()}.mp4"
                hbRecorder.setFileName(fileName)
                
                // Guardar en galería automáticamente (Android 10+)
                // Para Android 10+, usar MediaStore con ContentValues
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.util.Log.d("Recorder", "Configurando URI de salida para Android 10+...")
                    try {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ZipStats")
                        }
                        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            hbRecorder.setOutputUri(uri)
                            android.util.Log.d("Recorder", "URI configurada: $uri")
                        } else {
                            android.util.Log.e("Recorder", "No se pudo crear URI, usando ruta por defecto")
                            // Fallback: usar ruta por defecto
                            hbRecorder.setOutputPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Recorder", "Error al configurar URI: ${e.message}", e)
                        // Fallback: usar ruta por defecto
                        hbRecorder.setOutputPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath)
                    }
                } else {
                    // Para Android 9 y menores, usar ruta directa
                    android.util.Log.d("Recorder", "Configurando ruta de salida para Android 9-...")
                    hbRecorder.setOutputPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath)
                }
                
                // Habilitar configuraciones personalizadas para mayor control
                android.util.Log.d("Recorder", "Habilitando configuraciones personalizadas...")
                hbRecorder.enableCustomSettings()
                
                // Configuraciones personalizadas (requieren enableCustomSettings primero)
                // NO configuramos setAudioSource porque el audio está deshabilitado
                // Esto evita problemas con permisos FOREGROUND_SERVICE_MICROPHONE en Android 14+
                
                android.util.Log.d("Recorder", "Configurando codificador de video...")
                hbRecorder.setVideoEncoder("H264")
                
                // Dimensiones de pantalla (opcional, se ajustan automáticamente si no se especifica)
                // hbRecorder.setScreenDimensions(1920, 1080) // Alto, Ancho
                
                android.util.Log.d("Recorder", "Configurando bitrate y frame rate...")
                hbRecorder.setVideoBitrate(5000000) // 5Mbps (Calidad alta)
                hbRecorder.setVideoFrameRate(60)
                
                android.util.Log.d("Recorder", "Configuración completada, iniciando grabación...")
                
                // En Android 12+, el servicio en primer plano debe iniciarse desde una Activity visible
                // Añadimos un pequeño delay para asegurar que la Activity esté completamente visible
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        isRecording = true
                        hbRecorder.startScreenRecording(result.data, result.resultCode)
                        android.util.Log.d("Recorder", "Llamada a startScreenRecording completada")
                    } catch (e: Exception) {
                        android.util.Log.e("Recorder", "Error al iniciar grabación después del delay: ${e.message}", e)
                        isRecording = false
                        Toast.makeText(context, "Error al iniciar grabación: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }, 100) // Pequeño delay para asegurar que la Activity esté lista
            } catch (e: Exception) {
                android.util.Log.e("Recorder", "Error al iniciar grabación: ${e.message}", e)
                isRecording = false
                Toast.makeText(context, "Error al iniciar grabación: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            android.util.Log.d("Recorder", "Usuario canceló el permiso de grabación")
            isRecording = false // Usuario canceló
        }
    }
    
    // Referencia mutable al estilo
    val mapStyleRef = remember { mutableStateOf<Style?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false // Importante: permite que el contenido se expanda detrás de las barras
        )
    ) {
        // 🔥 Ocultar barras del sistema para grabación a pantalla completa
        // ✅ IMPORTANTE: Debe estar DENTRO del contenido del Dialog para detectar su ventana
        HideSystemBarsEffect()
        
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Medir altura de la card para padding dinámico de la cámara (no del mapa)
            var cardHeightPx by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            val bottomPadding = if (isRecording) 24.dp else 110.dp
            val cardHeightDp = with(density) { 
                if (cardHeightPx > 0) cardHeightPx.toDp() else 0.dp 
            }
            
            /* =========================
             * MAPA – FULLSCREEN REAL
             * ========================= */
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
                    
                    mapView.mapboxMap.loadStyle(Style.SATELLITE) { style ->
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
                                    // 5. FIN DEL SHOW -> CORTAR
                                    if (isRecording) {
                                        mediaPlayer?.pause()
                                        // Esperamos 1 seg extra para el "Fade out" visual
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            hbRecorder.stopScreenRecording()
                                        }, 1000)
                                    } else {
                                        isPlaying = false
                                        mediaPlayer?.pause()
                                        mediaPlayer?.seekTo(0)
                                    }
                                }
                            )
                            animator = routeAnimator
                            
                            // Auto-play solo si NO estamos en modo grabación pendiente
                            if (!isRecording) {
                                mapView.postDelayed({
                                    routeAnimator.startAnimation()
                                    isPlaying = true
                                    mediaPlayer?.start()
                                }, 1000)
                            }
                        }
                    }
                    mapView
                },
                modifier = Modifier.fillMaxSize()
            )
            
            /* =========================
             * BOTÓN CERRAR
             * ========================= */
            if (!isRecording) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        // ❌ Eliminado statusBarsPadding() - En modo inmersivo las barras están ocultas
                        .padding(16.dp)
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), CircleShape)
                        .zIndex(20f)
                ) {
                    Icon(Icons.Default.Close, "Cerrar", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            
            /* =========================
             * CARD FLOTANTE (OVERLAY)
             * ========================= */
            val tituloRuta = remember(route, vehicleType) { CityUtils.getRouteTitleText(route, vehicleType) }
            val fechaFormateada = remember(route.startTime) {
                val date = java.time.LocalDate.ofEpochDay(route.startTime / (1000 * 60 * 60 * 24))
                val formatter = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", java.util.Locale("es", "ES"))
                date.format(formatter)
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // ❌ Eliminado navigationBarsPadding() - En modo inmersivo las barras están ocultas
                    .padding(bottom = bottomPadding)
                    .zIndex(10f)
                    .onGloballyPositioned { coordinates ->
                        cardHeightPx = coordinates.size.height
                    },
                contentAlignment = Alignment.Center
            ) {
                RouteSummaryCardFromRoute(
                    route = route,
                    title = tituloRuta,
                    subtitle = "${route.scooterName} • $fechaFormateada",
                    duration = formatDurationWithUnits(route.totalDuration)
                )
            }
            
            // Actualizar padding dinámico de la cámara cuando cambie la altura de la card
            LaunchedEffect(cardHeightPx, bottomPadding) {
                animator?.let {
                    val cardHeightDpValue = with(density) { if (cardHeightPx > 0) cardHeightPx.toDp() else 0.dp }
                    val totalPaddingDp = cardHeightDpValue + bottomPadding
                    it.bottomPaddingDp = (totalPaddingDp.value * density.density).toDouble()
                }
            }
            
            /* =========================
             * BARRA DE CONTROL UNIFICADA
             * ========================= */
            // 3. UI SUPERPUESTA (Solo visible si NO grabamos)
            if (!isRecording) {
                AnimationControlBar(
                    isPlaying = isPlaying,
                    playbackSpeed = if (isSpeed2x) 2f else 1f,
                    onTogglePlay = {
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
                    onChangeSpeed = {
                        // Guardar el estado de reproducción ANTES de cambiar velocidad
                        val wasPlaying = isPlaying && (mediaPlayer?.isPlaying == true)
                        
                        isSpeed2x = !isSpeed2x
                        animator?.toggleSpeed()
                        
                        // Mantener el estado de reproducción - no cambiar isPlaying
                        // Solo asegurarse de que el MediaPlayer continúe si estaba reproduciéndose
                        if (wasPlaying) {
                            // Pequeño delay para asegurar que la animación se reinicie completamente
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                // Usar safe call porque mediaPlayer puede cambiar durante el delay
                                mediaPlayer?.let { player ->
                                    if (!player.isPlaying) {
                                        player.start()
                                    }
                                }
                            }, 100) // Delay un poco mayor para asegurar estabilidad
                        }
                    },
                    onSave = {
                        scope.launch {
                            // 1. Detenemos todo primero
                            animator?.stopAnimation(resetIndex = true)
                            mediaPlayer?.pause()
                            mediaPlayer?.seekTo(0)
                            isPlaying = false
                            
                            // 2. 🔥 OCULTAR UI ANTES DE GRABAR 🔥
                            // Establecemos isRecording = true para ocultar los botones inmediatamente
                            isRecording = true
                            
                            // 3. 🔥 RETARDO DE SEGURIDAD 🔥
                            // Esperamos 250ms para que Compose termine de actualizar la pantalla
                            // y los botones desaparezcan completamente del buffer antes de capturar el primer frame
                            delay(250)
                            
                            // 4. Pedimos permiso al sistema
                            // Esto lanzará el popup "¿Desea iniciar grabación?"
                            // Cuando el usuario acepte, el 'startMediaProjection' de arriba se ejecuta
                            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            animator?.stopAnimation()
            try {
                if (isRecording) {
                    hbRecorder.stopScreenRecording()
                }
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer.stop()
                }
                mediaPlayer?.release()
            } catch (e: Exception) {
                android.util.Log.e("RouteAnimation", "Error al liberar recursos: ${e.message}", e)
            }
        }
    }
}

// Componente: Barra de Control Unificada (Playback Pill)
@Composable
fun AnimationControlBar(
    isPlaying: Boolean,
    playbackSpeed: Float,
    onTogglePlay: () -> Unit,
    onChangeSpeed: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Contenedor tipo "Pastilla" (Stadium Shape)
    Surface(
        modifier = modifier
            .height(72.dp) // Altura cómoda
            .padding(horizontal = 16.dp) // Margen lateral
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f), CircleShape),
        shape = CircleShape, // Bordes totalmente redondos
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp) // Padding interno
                .widthIn(min = 280.dp), // Ancho mínimo para que no quede muy pequeña
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. VELOCIDAD (Izquierda)
            TextButton(
                onClick = onChangeSpeed,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                ZipStatsText(
                    text = "${playbackSpeed.toInt()}x",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    autoResize = true // 🔥 Métrica numérica, reduce si no cabe
                )
            }

            // 2. PLAY/PAUSE (Centro - Destacado)
            // Usamos un Box para darle un fondo circular blanco al botón principal
            FilledTonalIconButton(
                onClick = onTogglePlay,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .size(56.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    modifier = Modifier.size(32.dp)
                )
            }

            // 3. GUARDAR/DESCARGAR (Derecha)
            FilledTonalIconButton(
                onClick = onSave,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Guardar video"
                )
            }
        }
    }
}

// Funciones auxiliares para la tarjeta de resumen
private fun formatDurationWithUnits(durationMs: Long): String {
    val minutes = durationMs / 1000 / 60
    val hours = minutes / 60
    return if (hours > 0) String.format("%d h %d min", hours, minutes % 60) else String.format("%d min", minutes)
}
