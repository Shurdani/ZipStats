package com.zipstats.app.ui.routes

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.zipstats.app.R
import com.zipstats.app.model.Route
import com.zipstats.app.model.VehicleType
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.repository.WeatherRepository
import com.zipstats.app.ui.components.CapturableMapView
import com.zipstats.app.ui.components.MapSnapshotTrigger
import com.zipstats.app.ui.components.RouteSummaryCardFromRoute
import com.zipstats.app.ui.components.ZipStatsText
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.utils.CityUtils
import com.zipstats.app.utils.DateUtils
import com.zipstats.app.utils.LocationUtils
import com.zipstats.app.utils.ExportUiStrings
import com.zipstats.app.utils.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.Date
import java.util.Locale

@Composable
fun RouteDetailDialog(
    route: Route,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onAddToRecords: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Repositorios y Estados (Mantenemos tu lógica intacta)
    val vehicleRepository = remember {
        VehicleRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance())
    }
    
    var mapboxMapRef by remember { mutableStateOf<com.mapbox.maps.MapView?>(null) }
    var fullscreenMapRef by remember { mutableStateOf<com.mapbox.maps.MapView?>(null) }
    var mapSnapshotTrigger by remember { mutableStateOf<MapSnapshotTrigger?>(null) }
    var showAdvancedDetails by remember { mutableStateOf(false) }
    var showFullscreenMap by remember { mutableStateOf(false) }
    var isCapturingForShare by remember { mutableStateOf(false) }
    var showAnimationDialog by remember { mutableStateOf(false) }
    var vehicleIconRes by remember { mutableIntStateOf(R.drawable.ic_electric_scooter_adaptive) }
    var vehicleModel by remember { mutableStateOf(route.scooterName) }
    var vehicleType by remember { mutableStateOf<com.zipstats.app.model.VehicleType?>(null) }
    var showWeatherDialog by remember(route.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Capturar valores de tema en contexto composable (NO dentro de LaunchedEffect)
    val sharePrimaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()

    // Lógica de Compartir (Tu código original)
    LaunchedEffect(isCapturingForShare, mapSnapshotTrigger) {
        if (isCapturingForShare && mapSnapshotTrigger != null) {
            android.util.Log.d("RouteDetailDialog", "=== INICIANDO PROCESO DE COMPARTIR ===")
            android.util.Log.d("RouteDetailDialog", "Esperando a que el mapa esté completamente renderizado...")
            kotlinx.coroutines.delay(2000)
            android.util.Log.d("RouteDetailDialog", "Delay completado, llamando a ShareUtils...")
            try {
                ShareUtils.shareRouteImage(
                    context = context,
                    route = route,
                    snapshotTrigger = mapSnapshotTrigger!!,
                    vehicleRepository = vehicleRepository,
                    primaryColorArgb = sharePrimaryColorArgb,
                    onComplete = {
                        android.util.Log.d("RouteDetailDialog", "Proceso de compartir completado")
                        showFullscreenMap = false
                        isCapturingForShare = false
                        mapSnapshotTrigger = null
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("RouteDetailDialog", "Error al compartir ruta: ${e.message}", e)
                e.printStackTrace()
                showFullscreenMap = false
                isCapturingForShare = false
                mapSnapshotTrigger = null
            }
        }
    }

    // Obtener info del vehículo
    LaunchedEffect(route.scooterId) {
        try {
            val vehicle = vehicleRepository.getUserVehicles().find { it.id == route.scooterId }
            vehicleIconRes = getVehicleIconResource(vehicle?.vehicleType)
            vehicleType = vehicle?.vehicleType

            vehicleModel = if (vehicle != null && vehicle.modelo.isNotBlank()) {
                vehicle.modelo
            } else {
                route.scooterName
            }
        } catch (_: Exception) {
            vehicleIconRes = R.drawable.ic_electric_scooter_adaptive
            vehicleModel = route.scooterName
            vehicleType = null
        }
    }

    if (showWeatherDialog) {
        WeatherInfoDialog(route = route, onDismiss = { showWeatherDialog = false })
    }

    // --- UI PRINCIPAL (EL CAMBIO VISUAL) ---
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false // Para poder controlar el ancho
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f) // Margen lateral del 5%
                .heightIn(max = 780.dp) // Altura máxima controlada
                .clip(DialogShape),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(4.dp),
            shape = DialogShape
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // 1. ZONA HERO: EL MAPA (Ocupa la parte superior)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp) // Mapa grande
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                ) {
                    // El Mapa
                    CapturableMapView(
                        route = route,
                        onMapReady = { mapboxMap -> mapboxMapRef = mapboxMap },
                        modifier = Modifier.fillMaxSize(),
                        isCompact = true,
                        onStyleLoaded = { style ->
                            style.getLayer("poi-label")?.visibility(Visibility.NONE)
                            style.getLayer("transit-label")?.visibility(Visibility.NONE)
                        }
                    )

                    // Sombra gradiente inferior para legibilidad
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha=0.4f))
                                )
                            )
                    )

                    // Botones Flotantes sobre el mapa
                    
                    // Cerrar (X)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f), CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, "Cerrar", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                    }

                    // Añadir a Registros (Lista)
                    onAddToRecords?.let { addToRecords ->
                        IconButton(
                            onClick = addToRecords,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f), CircleShape)
                                .size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, "Añadir", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Expandir (Fullscreen)
                    IconButton(
                        onClick = { showFullscreenMap = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Fullscreen, "Expandir", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    IconButton(
                        onClick = {
                            if (route.points.isEmpty()) {
                                Toast.makeText(context, "No hay puntos para exportar", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            scope.launch {
                                val result = exportRouteGpxToDownloads(context, route)
                                result.onSuccess { (savedName, savedUri, notificationId) ->
                                    showGpxSavedNotification(context, savedUri, savedName, notificationId)
                                    Toast.makeText(context, ExportUiStrings.savedToDownloadsRelative(savedName), Toast.LENGTH_SHORT).show()
                                }.onFailure { error ->
                                    Toast.makeText(context, "Error al exportar GPX: ${error.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "Exportar GPX",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 2. CONTENIDO DE DATOS (Scrollable)
                Column(
                    modifier = Modifier
                        .weight(1f) // Ocupa el espacio restante
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    // Cabecera: Título y Vehículo
                    val title = CityUtils.getRouteTitleText(route, vehicleType)
                    ZipStatsText(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = vehicleIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ZipStatsText(
                            text = "$vehicleModel • ${DateUtils.formatForDisplay(java.time.LocalDate.ofEpochDay(route.startTime / (1000 * 60 * 60 * 24)))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // MÉTRICAS PRINCIPALES (Row limpio sin tarjetas)
                    CleanMetricsRow(
                        route = route,
                        onWeatherClick = { showWeatherDialog = true }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // DETALLES AVANZADOS (Acordeón)
                    if (route.movingTime > 0 || route.pauseCount > 0) {
                        AdvancedDetailsSection(
                            route = route,
                            expanded = showAdvancedDetails,
                            onToggle = { showAdvancedDetails = !showAdvancedDetails }
                        )
                    }
                }

                // 3. BARRA DE ACCIONES (Footer Fijo)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = 16.dp, // Sombra para separar del scroll
                    modifier = Modifier.fillMaxWidth().zIndex(1f)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        thickness = 0.8.dp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Borrar (Pequeño)
                        FilledTonalIconButton(
                            onClick = onDelete,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Delete, "Eliminar")
                        }

                        // ANIMAR (Grande - Principal)
                        Button(
                            onClick = { showAnimationDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            ZipStatsText("Ver Animación", color = MaterialTheme.colorScheme.onPrimary)
                        }

                        // Compartir (Pequeño)
                        FilledTonalIconButton(
                            onClick = {
                                isCapturingForShare = true
                                showFullscreenMap = true
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Share, "Compartir")
                        }
                    }
                }
            }
        }
    }

    // Modales auxiliares (Fullscreen y Animación)
    if (showFullscreenMap) {
        FullscreenMapDialog(
            route = route,
            vehicleType = vehicleType,
            onExportGpx = {
                if (route.points.isEmpty()) {
                    Toast.makeText(context, "No hay puntos para exportar", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        val result = exportRouteGpxToDownloads(context, route)
                        result.onSuccess { (savedName, savedUri, notificationId) ->
                            showGpxSavedNotification(context, savedUri, savedName, notificationId)
                            Toast.makeText(context, ExportUiStrings.savedToDownloadsRelative(savedName), Toast.LENGTH_SHORT).show()
                        }.onFailure { error ->
                            Toast.makeText(context, "Error al exportar GPX: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onDismiss = {
                showFullscreenMap = false
                isCapturingForShare = false
                mapSnapshotTrigger = null
            },
            onSnapshotHandlerReady = { trigger -> mapSnapshotTrigger = trigger },
            onMapReady = { mapView -> fullscreenMapRef = mapView }
        )
    }
    
    if (showAnimationDialog) {
        RouteAnimationDialog(route = route, onDismiss = { showAnimationDialog = false })
    }
}

// -------------------------------------------------------------------------
// DIÁLOGOS MODALES (MAPA FULLSCREEN Y CLIMA)
// -------------------------------------------------------------------------

@Composable
private fun FullscreenMapDialog(
    route: Route,
    vehicleType: com.zipstats.app.model.VehicleType? = null,
    onExportGpx: () -> Unit,
    onDismiss: () -> Unit,
    onMapReady: ((com.mapbox.maps.MapView?) -> Unit)? = null,
    onSnapshotHandlerReady: ((MapSnapshotTrigger) -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            /* =========================
             * MAPA – FULLSCREEN REAL
             * ========================= */
            CapturableMapView(
                route = route,
                modifier = Modifier.fillMaxSize(),
                onSnapshotHandlerReady = onSnapshotHandlerReady,
                onMapReady = { mapView ->
                    onMapReady?.invoke(mapView)
                },
                mapStyle = com.mapbox.maps.Style.MAPBOX_STREETS,
                onStyleLoaded = { style ->
                    // 1. Ocultar POIs (Tiendas, restaurantes, bancos...)
                    style.getLayer("poi-label")?.visibility(Visibility.NONE)
                    
                    // 2. Ocultar transporte público (Paradas de bus, metro...) 
                    // Queda mucho más limpio si lo quitas también.
                    style.getLayer("transit-label")?.visibility(Visibility.NONE)
                }
            )

            /* =========================
             * BOTÓN CERRAR
             * ========================= */
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .zIndex(20f),
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)
            ) {
                IconButton(onClick = onExportGpx, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Exportar GPX",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .zIndex(20f),
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            /* =========================
             * CARD FLOTANTE (OVERLAY)
             * ========================= */
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                TripDetailsOverlay(
                    route = route,
                    vehicleType = vehicleType
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// NUEVA TARJETA DE DETALLES DEL VIAJE (MATERIAL 3 / MODERN UI)
// -------------------------------------------------------------------------

@Composable
fun TripDetailsOverlay(
    route: Route,
    vehicleType: com.zipstats.app.model.VehicleType? = null,
    modifier: Modifier = Modifier
) {
    val tituloRuta: String = remember(route, vehicleType) { CityUtils.getRouteTitleText(route, vehicleType) }

    val fechaFormateada = remember(route.startTime) {
        val date = java.time.LocalDate.ofEpochDay(route.startTime / (1000 * 60 * 60 * 24))
        val formatter = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", java.util.Locale("es", "ES"))
        date.format(formatter)
    }

    RouteSummaryCardFromRoute(
        route = route,
        title = tituloRuta,
        subtitle = "${route.scooterName} • $fechaFormateada",
        duration = formatDurationWithUnits(route.totalDuration),
        modifier = modifier
    )
}

// -------------------------------------------------------------------------
// COMPONENTES VISUALES NUEVOS (Estilo Limpio)
// -------------------------------------------------------------------------

@Composable
fun CleanMetricsRow(route: Route, onWeatherClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Distancia
        MetricColumn(
            value = LocationUtils.formatNumberSpanish(route.totalDistance),
            unit = "km",
            label = "Distancia",
            color = MaterialTheme.colorScheme.primary
        )

        // Divisor Vertical
        Box(
            modifier = Modifier
                .height(40.dp)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )

        // Duración
        val durationText = formatDurationShort(route.totalDuration)
        val durationParts = durationText.split(" ")
        val (durationValue, durationUnit) = if (durationParts.size > 1) {
            // Formato: "45 min"
            durationParts[0] to durationParts[1]
        } else {
            // Formato: "1:30" (sin espacio) - mostrar solo el valor
            durationText to ""
        }
        MetricColumn(
            value = durationValue,
            unit = durationUnit,
            label = "Tiempo",
            color = MaterialTheme.colorScheme.primary
        )

        // Divisor Vertical
        Box(
            modifier = Modifier
                .height(40.dp)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )

        // Clima (Interactivo)
        val weatherTemp = route.weatherTemperature?.let { "${formatTemperature(it, 0)}°" } ?: "--"
        val weatherIconRes = remember(route.weatherCondition, route.weatherCode, route.weatherEmoji, route.weatherIsDay) {
            when {
                !route.weatherCondition.isNullOrBlank() ->
                    WeatherRepository.getIconResIdForCondition(route.weatherCondition, route.weatherIsDay ?: true)
                route.weatherCode != null ->
                    WeatherRepository.getIconResIdForWeather(route.weatherCode, if (route.weatherIsDay ?: true) 1 else 0)
                !route.weatherEmoji.isNullOrBlank() -> {
                    val inferredCode = inferWeatherCodeFromEmoji(route.weatherEmoji)
                    if (inferredCode != null) {
                        WeatherRepository.getIconResIdForWeather(inferredCode, if (route.weatherIsDay ?: true) 1 else 0)
                    } else {
                        R.drawable.help_outline
                    }
                }
                else -> R.drawable.help_outline
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // Sin ripple para evitar conflictos
                    onClick = onWeatherClick
                )
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = weatherIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
                Spacer(modifier = Modifier.width(6.dp))
                ZipStatsText(
                    text = weatherTemp,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            ZipStatsText(
                text = "Clima",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MetricColumn(value: String, unit: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            ZipStatsText(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.width(2.dp))
            ZipStatsText(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        ZipStatsText(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AdvancedDetailsSection(route: Route, expanded: Boolean, onToggle: () -> Unit) {
    val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "rotation")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // Sin ripple para evitar conflictos
                onClick = onToggle
            )
    ) {
        // Cabecera
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Analytics, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ZipStatsText(
                    text = "Métricas avanzadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotationState)
            )
        }

        // Contenido
        if (expanded) {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                AdvancedStatRow("Velocidad Real", "${LocationUtils.formatNumberSpanish(route.averageMovingSpeed)} km/h", true)
                Spacer(modifier = Modifier.height(12.dp))
                AdvancedStatRow("Velocidad Máxima", "${LocationUtils.formatNumberSpanish(route.maxSpeed)} km/h", false)
                Spacer(modifier = Modifier.height(12.dp))
                AdvancedStatRow("Velocidad Media", "${LocationUtils.formatNumberSpanish(route.averageSpeed)} km/h", false)
                Spacer(modifier = Modifier.height(12.dp))
                AdvancedStatRow("En Movimiento (${LocationUtils.formatNumberSpanish(route.movingPercentage.toDouble(), 0)}%)", formatDuration(route.movingTime), false)
                Spacer(modifier = Modifier.height(12.dp))
                AdvancedStatRow("Hora de Inicio", formatTime(route.startTime), false)
                Spacer(modifier = Modifier.height(12.dp))
                AdvancedStatRow("Hora de Fin", if (route.endTime != null) formatTime(route.endTime!!) else "--:--", false)
            }
        }
    }
}

@Composable
fun SafetyBadgesSection(route: Route) {
    // Leer datos guardados de la ruta (ya calculados al finalizar)
    val hadRain = route.weatherHadRain == true
    val hasWetRoad = route.weatherHadWetRoad == true && !hadRain // Calzada mojada solo si NO hay lluvia
    val hasExtremeConditions = route.weatherHadExtremeConditions == true

    // Texto del badge de clima extremo (usar razón guardada si está disponible)
    val extremeBadgeText = remember(route.weatherExtremeReason) {
        when {
            !hasExtremeConditions -> null
            !route.weatherExtremeReason.isNullOrBlank() -> {
                // Convertir razón guardada a texto amigable
                when (route.weatherExtremeReason) {
                    "STORM" -> "⚠️ Tormenta"
                    "SNOW" -> "⚠️ Nieve"
                    "GUSTS" -> "⚠️ Ráfagas"
                    "WIND" -> "⚠️ Viento intenso"
                    "COLD" -> "⚠️ Helada"
                    "HEAT" -> "⚠️ Calor intenso"
                    "UV" -> "⚠️ Radiación UV alta"
                    "VISIBILITY" -> "⚠️ Visibilidad reducida"
                    else -> "⚠️ Clima extremo"
                }
            }
            else -> "⚠️ Clima extremo"
        }
    }

    val badgeCount = listOf(hadRain, hasWetRoad, hasExtremeConditions).count { it }
    
    if (badgeCount > 0) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Si hay 2 o más badges, agruparlos en una sola tarjeta
        if (badgeCount >= 2) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    val badges = remember(hadRain, hasWetRoad, extremeBadgeText) {
                        mutableListOf<String>().apply {
                            if (hadRain) add("🔵 Llovió durante la ruta")
                            if (hasWetRoad) add("🟡 Calzada húmeda")
                            if (extremeBadgeText != null) add(extremeBadgeText)
                        }
                    }
                    
                    badges.forEachIndexed { index, badgeText ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                thickness = 0.5.dp
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            SafetyBadgeText(badgeText)
                        }
                    }
                }
            }
        } else {
            // Si hay solo 1 badge, mostrar tarjeta individual
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hadRain) SafetyBadge("🔵 Llovió durante la ruta")
                if (hasWetRoad) SafetyBadge("🟡 Calzada húmeda")
                if (extremeBadgeText != null) SafetyBadge(extremeBadgeText)
            }
        }
    }
}

@Composable
fun SafetyBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        ZipStatsText(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                lineHeight = 18.sp
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun SafetyBadgeText(text: String) {
    ZipStatsText(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(
            lineHeight = 18.sp
        ),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        maxLines = 1
    )
}

// -------------------------------------------------------------------------
// COMPONENTES UI AUXILIARES RESTANTES (CompactHeader, RouteTitle, StatsChips, etc.)
// (Se mantienen iguales que antes, omites para brevedad si ya están)
// -------------------------------------------------------------------------
@Composable
private fun CompactHeader(route: Route, vehicleIconRes: Int, vehicleName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = vehicleIconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp).padding(end = 6.dp)
        )
        ZipStatsText(
            text = vehicleName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        ZipStatsText(
            text = " · ",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        ZipStatsText(
            text = DateUtils.formatForDisplay(
                java.time.LocalDate.ofEpochDay(route.startTime / (1000 * 60 * 60 * 24))
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RouteTitle(route: Route) {
    val title: String = CityUtils.getRouteTitleText(route, null)

    ZipStatsText(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
        maxLines = 2
    )
}

@Composable
private fun StatsChips(route: Route, onWeatherClick: () -> Unit) {
    val context = LocalContext.current
    var weatherIconRes by remember(route.id) {
        mutableIntStateOf(
            when {
                !route.weatherCondition.isNullOrBlank() ->
                    WeatherRepository.getIconResIdForCondition(route.weatherCondition, route.weatherIsDay ?: true)
                route.weatherCode != null ->
                    WeatherRepository.getIconResIdForWeather(route.weatherCode, if (route.weatherIsDay ?: true) 1 else 0)
                !route.weatherEmoji.isNullOrBlank() -> {
                    val inferredCode = inferWeatherCodeFromEmoji(route.weatherEmoji)
                    if (inferredCode != null) {
                        WeatherRepository.getIconResIdForWeather(inferredCode, if (route.weatherIsDay ?: true) 1 else 0)
                    } else {
                        R.drawable.help_outline
                    }
                }
                else -> R.drawable.help_outline
            }
        )
    }
    var weatherTemp by remember(route.id) { mutableStateOf(if (route.weatherTemperature != null) "${formatTemperature(route.weatherTemperature, decimals = 0)}°C" else "--°C") }
    var isLoadingWeather by remember(route.id) { mutableStateOf(false) }

    LaunchedEffect(route.id) {
        if (route.weatherTemperature != null) {
            weatherIconRes = when {
                !route.weatherCondition.isNullOrBlank() ->
                    WeatherRepository.getIconResIdForCondition(route.weatherCondition, route.weatherIsDay ?: true)
                route.weatherCode != null ->
                    WeatherRepository.getIconResIdForWeather(route.weatherCode, if (route.weatherIsDay ?: true) 1 else 0)
                !route.weatherEmoji.isNullOrBlank() -> {
                    val inferredCode = inferWeatherCodeFromEmoji(route.weatherEmoji)
                    if (inferredCode != null) {
                        WeatherRepository.getIconResIdForWeather(inferredCode, if (route.weatherIsDay ?: true) 1 else 0)
                    } else {
                        R.drawable.help_outline
                    }
                }
                else -> R.drawable.help_outline
            }
            weatherTemp = "${formatTemperature(route.weatherTemperature, decimals = 0)}°C"
            return@LaunchedEffect
        }
        if (route.points.isNotEmpty()) {
            isLoadingWeather = true
            try {
                val appContext = context.applicationContext
                // Crear instancia manualmente con el contexto de la aplicación
                // Usa la función factory que no requiere Hilt
                val weatherRepository = com.zipstats.app.repository.WeatherRepository.create(appContext)
                val firstPoint = route.points.first()
                val result = weatherRepository.getCurrentWeather(firstPoint.latitude, firstPoint.longitude)
                result.onSuccess { weather ->
                    // Usar condition si está disponible, sino emoji como fallback
                    weatherIconRes = when {
                        !weather.icon.isNullOrBlank() ->
                            WeatherRepository.getIconResIdForCondition(weather.icon, weather.isDay)
                        true ->
                            WeatherRepository.getIconResIdForWeather(weather.weatherCode, if (weather.isDay) 1 else 0)
                        !weather.weatherEmoji.isNullOrBlank() -> {
                            val inferredCode = inferWeatherCodeFromEmoji(weather.weatherEmoji)
                            if (inferredCode != null) {
                                WeatherRepository.getIconResIdForWeather(inferredCode, if (weather.isDay) 1 else 0)
                            } else {
                                R.drawable.help_outline
                            }
                        }
                        else -> R.drawable.help_outline
                    }
                    weatherTemp = "${formatTemperature(weather.temperature, decimals = 0)}°C"
                }
            } catch (e: Exception) {
                // Ignorar
            } finally {
                isLoadingWeather = false
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatChip(value = "${LocationUtils.formatNumberSpanish(route.totalDistance)} km", label = "Distancia", modifier = Modifier.weight(1f))
        StatChip(value = formatDurationWithUnits(route.totalDuration), label = "Duración", modifier = Modifier.weight(1f))
        StatChip(
            value = if (isLoadingWeather) "..." else weatherTemp,
            label = "Clima",
            iconRes = weatherIconRes,
            modifier = Modifier.weight(1f).clickable(
                enabled = route.weatherTemperature != null,
                onClick = onWeatherClick
            )
        )
    }
}

@Composable
private fun StatChip(value: String, label: String, iconRes: Int? = null, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (iconRes != null) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).padding(end = 4.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
                    )
                }
                ZipStatsText(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
            }
            ZipStatsText(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CollapsibleAdvancedDetails(route: Route, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(
                    onClick = { onToggle() }
                ).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    ZipStatsText(text = "Ver más detalles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }

            if (expanded) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    AdvancedStatRow("Velocidad Real", "${LocationUtils.formatNumberSpanish(route.averageMovingSpeed)} km/h", true)
                    Spacer(modifier = Modifier.height(8.dp))
                    AdvancedStatRow("Velocidad Máxima", "${LocationUtils.formatNumberSpanish(route.maxSpeed)} km/h", false)
                    Spacer(modifier = Modifier.height(8.dp))
                    AdvancedStatRow("Velocidad Media", "${LocationUtils.formatNumberSpanish(route.averageSpeed)} km/h", false)
                    Spacer(modifier = Modifier.height(8.dp))
                    AdvancedStatRow("En Movimiento (${LocationUtils.formatNumberSpanish(route.movingPercentage.toDouble(), 0)}%)", formatDuration(route.movingTime), false)
                    Spacer(modifier = Modifier.height(8.dp))
                    AdvancedStatRow("Hora de Inicio", formatTime(route.startTime), false)
                    Spacer(modifier = Modifier.height(8.dp))
                    AdvancedStatRow("Hora de Fin", if (route.endTime != null) formatTime(route.endTime) else "--:--", false)
                }
            }
        }
    }
}

@Composable
private fun AdvancedStatRow(label: String, value: String, highlight: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZipStatsText(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ZipStatsText(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun WeatherInfoDialog(route: Route, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f) // Un poco más estrecho para que se vea el fondo
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box {
                // Botón Cerrar (X) discreto en la esquina
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(Icons.Default.Close, "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Leer datos guardados de la ruta (ya calculados al finalizar)
                    val hadRain = route.weatherHadRain == true
                    val hasWetRoad = route.weatherHadWetRoad == true && !hadRain // Calzada mojada solo si NO hay lluvia
                    val hasExtremeConditions = route.weatherHadExtremeConditions == true
                    
                    // Texto del badge de clima extremo (usar razón guardada si está disponible)
                    val extremeBadgeText = remember(route.weatherExtremeReason) {
                        when {
                            !hasExtremeConditions -> null
                            !route.weatherExtremeReason.isNullOrBlank() -> {
                                // Convertir razón guardada a texto amigable
                                when (route.weatherExtremeReason) {
                                    "STORM" -> "⚠️ Tormenta"
                                    "SNOW" -> "⚠️ Nieve"
                                    "GUSTS" -> "⚠️ Ráfagas"
                                    "WIND" -> "⚠️ Viento intenso"
                                    "COLD" -> "⚠️ Helada"
                                    "HEAT" -> "⚠️ Calor intenso"
                                    "UV" -> "⚠️ Radiación UV alta"
                                    "VISIBILITY" -> "⚠️ Visibilidad reducida"
                                    else -> "⚠️ Clima extremo"
                                }
                            }
                            else -> "⚠️ Clima extremo"
                        }
                    }
                    
                    // 1. HEADER (Icono + Temp) - Usar datos guardados directamente
                    val effectiveDescription = route.weatherDescription?.substringBefore("(")?.trim() ?: "Clima"
                    val weatherIconRes = remember(route.weatherCondition, route.weatherCode, route.weatherEmoji, route.weatherIsDay) {
                        when {
                            !route.weatherCondition.isNullOrBlank() ->
                                WeatherRepository.getIconResIdForCondition(route.weatherCondition, route.weatherIsDay ?: true)
                            route.weatherCode != null ->
                                WeatherRepository.getIconResIdForWeather(route.weatherCode, if (route.weatherIsDay ?: true) 1 else 0)
                            !route.weatherEmoji.isNullOrBlank() -> {
                                val inferredCode = inferWeatherCodeFromEmoji(route.weatherEmoji)
                                if (inferredCode != null) {
                                    WeatherRepository.getIconResIdForWeather(inferredCode, if (route.weatherIsDay ?: true) 1 else 0)
                                } else {
                                    R.drawable.help_outline
                                }
                            }
                            else -> R.drawable.help_outline
                        }
                    }
                    
                    Image(
                        painter = painterResource(id = weatherIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp), // Un pelín más grande
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ZipStatsText(
                        text = effectiveDescription,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = Int.MAX_VALUE
                    )
                    
                    ZipStatsText(
                        text = route.weatherTemperature?.let { "${formatTemperature(it)}°C" } ?: "--°C",
                        style = MaterialTheme.typography.displayMedium, // Número grande e impactante
                        fontWeight = FontWeight.Black, // Extra Bold
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 2. GRID DE DETALLES (2 Columnas - Reorganizado según estructura optimizada)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        // Columna Izquierda (Confort/Estado)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // 🔥 LÓGICA INTELIGENTE: Mostrar Wind Chill, Heat Index o feelsLike según disponibilidad
                            // Todos vienen directamente de Google API:
                            // - windChill: solo disponible cuando T < 15°C
                            // - heatIndex: solo disponible cuando T > 26°C
                            // - feelsLikeTemperature: siempre disponible como sensación térmica general
                            val temp = route.weatherTemperature
                            val windChill = route.weatherWindChill // Wind Chill de Google API (solo cuando T < 15°C)
                            val heatIndex = route.weatherHeatIndex // Heat Index de Google API (solo cuando T > 26°C)
                            val feelsLike = route.weatherFeelsLike // feelsLikeTemperature de Google API (siempre disponible como fallback)
                            
                            when {
                                // Frío (<15°C): Muestra Wind Chill si está disponible (viene directamente de Google API)
                                // Si no hay windChill disponible, muestra feelsLike como fallback
                                temp != null && temp < 15 && windChill != null -> {
                                    WeatherGridItem(
                                        icon = Icons.Default.Thermostat,
                                        label = "Sensación",
                                        value = "${formatTemperature(windChill)}°C"
                                    )
                                }
                                // Calor (>26°C): Muestra Heat Index si está disponible (viene directamente de Google API)
                                // Si no hay heatIndex disponible, muestra feelsLike como fallback
                                temp != null && temp > 26 && heatIndex != null -> {
                                    WeatherGridItem(
                                        icon = Icons.Default.Thermostat,
                                        label = "I de Calor",
                                        value = "${formatTemperature(heatIndex)}°C"
                                    )
                                }
                                // Fallback: Muestra feelsLikeTemperature (viene directamente de Google API)
                                // Se usa cuando: temperatura media (15-26°C), o cuando no hay windChill/heatIndex disponible
                                feelsLike != null -> {
                                    WeatherGridItem(
                                        icon = Icons.Default.Thermostat,
                                        label = "Sensación",
                                        value = "${formatTemperature(feelsLike)}°C"
                                    )
                                }
                            }
                            route.weatherHumidity?.let {
                                WeatherGridItem(
                                    icon = Icons.Default.Opacity,
                                    label = "Humedad",
                                    value = "$it%"
                                )
                            }
                            // Punto de rocío (crítico para Barcelona - explica por qué el suelo está mojado)
                            route.weatherDewPoint?.let { dewPoint ->
                                val dewPointText = "${formatTemperature(dewPoint)}°C"
                                WeatherGridItem(
                                    icon = Icons.Default.WaterDrop,
                                    label = "Punto de rocío",
                                    value = dewPointText
                                )
                            }
                            route.weatherWindSpeed?.let {
                                WeatherGridItem(
                                    icon = Icons.Default.Air,
                                    label = "Viento",
                                    value = "${LocationUtils.formatNumberSpanish(it)} km/h"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Columna Derecha (Peligros/Visibilidad)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Lógica de lluvia:
                            // - Si hubo lluvia (hadRain == true): mostrar mm si están disponibles, o "Detectada" si no.
                            //   NO mostrar probabilidad si hubo lluvia real.
                            // - Si NO hubo lluvia: mostrar solo probabilidad (si existe).
                            if (hadRain) {
                                // Hubo lluvia durante la ruta
                                val precip = route.weatherMaxPrecipitation
                                if (precip != null && precip > 0.0) {
                                    WeatherGridItem(Icons.Default.Grain, "Lluvia", "${LocationUtils.formatNumberSpanish(precip)} mm")
                                } else {
                                    // Lluvia detectada pero sin mm registrados
                                    WeatherGridItem(Icons.Default.Grain, "Lluvia", "Detectada")
                                }
                            } else {
                                // NO hubo lluvia: mostrar probabilidad si existe
                                route.weatherRainProbability?.let {
                                    WeatherGridItem(Icons.Default.Umbrella, "Prob. Lluvia", "$it%")
                                }
                            }
                            
                            // Visibilidad (crítico para Barcelona - niebla/talaia)
                            // Mostrar en kilómetros (convertir de metros a km)
                            route.weatherVisibility?.let { visibilityMeters ->
                                val visibilityKm = visibilityMeters / 1000.0
                                WeatherGridItem(Icons.Default.Visibility, "Visibilidad", "${LocationUtils.formatNumberSpanish(visibilityKm, 1)} km")
                            }
                            
                            // Índice UV (Solo si es de día Y tiene valor)
                            // 🔒 REGLA: UV solo de día, no se muestra de noche
                            if (route.weatherIsDay && (route.weatherUvIndex ?: 0.0) > 0) {
                                WeatherGridItem(Icons.Default.WbSunny, "Índice UV", LocationUtils.formatNumberSpanish(route.weatherUvIndex!!, 0))
                            }
                            
                            // Ráfagas (Picos de viento)
                            route.weatherWindGusts?.let {
                                WeatherGridItem(Icons.Default.Cyclone, "Ráfagas", "${LocationUtils.formatNumberSpanish(it)} km/h")
                            }
                        }
                    }

                    // 3. BADGE DE SEGURIDAD (Si aplica)
                    
                    val badgeCount = listOf(hadRain, hasWetRoad, hasExtremeConditions).count { it }
                    
                    if (badgeCount > 0) {
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Si hay 2 o más badges, agruparlos en una sola tarjeta
                        if (badgeCount >= 2) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    val badges = remember(hadRain, hasWetRoad, extremeBadgeText) {
                                        mutableListOf<String>().apply {
                                            if (hadRain) add("🔵 Llovió durante la ruta")
                                            if (hasWetRoad) add("🟡 Calzada húmeda")
                                            if (extremeBadgeText != null) add(extremeBadgeText)
                                        }
                                    }
                                    
                                    badges.forEachIndexed { index, badgeText ->
                                        if (index > 0) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                thickness = 0.5.dp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            SafetyBadgeText(text = badgeText)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Si hay solo 1 badge, mostrar tarjeta individual
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (hadRain) {
                                    SafetyBadge(text = "🔵 Llovió durante la ruta")
                                }
                                if (hasWetRoad) {
                                    SafetyBadge(text = "🟡 Calzada húmeda")
                                }
                                if (extremeBadgeText != null) {
                                    SafetyBadge(text = extremeBadgeText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper para los items del grid (Más compacto)
@Composable
private fun WeatherGridItem(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Icono con fondo circular suave
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            ZipStatsText(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ZipStatsText(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WeatherDetailRow(
    icon: ImageVector, 
    label: String, 
    value: String,
    isExtreme: Boolean = false // 🔥 Si es true, muestra el valor con cápsula sutil
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.primary, 
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        ZipStatsText(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        
        // Cápsula sutil solo en el valor cuando es extremo
        if (isExtreme) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
            ) {
                ZipStatsText(
                    text = value, 
                    style = MaterialTheme.typography.bodyLarge, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        } else {
            ZipStatsText(
                text = value, 
                style = MaterialTheme.typography.bodyLarge, 
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


// La lógica de compartir se ha movido a ShareUtils.kt

/**
 * Infiere un código de clima desde un emoji (último recurso cuando no hay weatherCondition ni weatherCode)
 */
private fun inferWeatherCodeFromEmoji(emoji: String?): Int? {
    if (emoji.isNullOrBlank()) return null
    return when (emoji) {
        "☀️", "🌙" -> 0
        "🌤️", "🌥️", "☁️🌙" -> 1
        "☁️" -> 3
        "🌫️" -> 45
        "🌦️" -> 61
        "🌧️" -> 65
        "🥶" -> 66
        "❄️" -> 71
        "⛈️", "⚡" -> 95
        else -> null
    }
}

private fun getVehicleIconResource(vehicleType: VehicleType?): Int {
    return when (vehicleType) {
        VehicleType.PATINETE -> R.drawable.ic_electric_scooter_adaptive
        VehicleType.BICICLETA -> R.drawable.ic_ciclismo_adaptive
        VehicleType.E_BIKE -> R.drawable.ic_bicicleta_electrica_adaptive
        VehicleType.MONOCICLO -> R.drawable.ic_unicycle_adaptive
        null -> R.drawable.ic_electric_scooter_adaptive
    }
}

private fun buildGpxFileName(route: Route): String {
    val date = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date(route.startTime))
    return "zipstats_ruta_${date}.gpx"
}

private suspend fun exportRouteGpxToDownloads(
    context: android.content.Context,
    route: Route
): Result<Triple<String, Uri, Int>> = withContext(Dispatchers.IO) {
    runCatching {
        val fileName = buildGpxFileName(route)
        val notificationId = fileName.hashCode()
        showGpxProgressNotification(context, notificationId)
        val gpxContent = buildRouteGpx(route)
        val gpxBytes = gpxContent.toByteArray(Charsets.UTF_8)

        val savedUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("No se pudo crear archivo GPX en Descargas")
            resolver.openOutputStream(uri)?.use { output -> output.write(gpxBytes) }
                ?: throw IllegalStateException("No se pudo escribir el GPX")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                if (context is android.app.Activity) {
                    ActivityCompat.requestPermissions(
                        context,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        2003
                    )
                }
                throw SecurityException("Falta permiso de almacenamiento")
            }
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destination = File(downloadsDir, fileName)
            destination.outputStream().use { it.write(gpxBytes) }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                destination
            )
        }
        Triple(fileName, savedUri, notificationId)
    }.onFailure {
        cancelGpxNotification(context, buildGpxFileName(route).hashCode())
    }
}

private fun buildRouteGpx(route: Route): String {
    val sortedPoints = route.points.sortedBy { it.timestamp }
    val routeName = route.notes.takeIf { it.isNotBlank() }
        ?: "Ruta ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(route.startTime))}"
    val gpxTime = formatGpxTimestamp(route.startTime)

    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    sb.append("<gpx version=\"1.1\" creator=\"ZipStats\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
    sb.append("  <metadata>\n")
    sb.append("    <name>").append(escapeXml(routeName)).append("</name>\n")
    if (gpxTime != null) {
        sb.append("    <time>").append(gpxTime).append("</time>\n")
    }
    sb.append("  </metadata>\n")
    sb.append("  <trk>\n")
    sb.append("    <name>").append(escapeXml(routeName)).append("</name>\n")
    sb.append("    <trkseg>\n")

    for (point in sortedPoints) {
        val lat = String.format(Locale.US, "%.7f", point.latitude)
        val lon = String.format(Locale.US, "%.7f", point.longitude)
        sb.append("      <trkpt lat=\"").append(lat).append("\" lon=\"").append(lon).append("\">\n")

        point.altitude?.let { altitude ->
            sb.append("        <ele>").append(String.format(Locale.US, "%.1f", altitude)).append("</ele>\n")
        }

        formatGpxTimestamp(point.timestamp)?.let { pointTime ->
            sb.append("        <time>").append(pointTime).append("</time>\n")
        }

        point.speed?.let { speedMs ->
            sb.append("        <extensions><speed>")
                .append(String.format(Locale.US, "%.2f", speedMs))
                .append("</speed></extensions>\n")
        }

        sb.append("      </trkpt>\n")
    }

    sb.append("    </trkseg>\n")
    sb.append("  </trk>\n")
    sb.append("</gpx>")
    return sb.toString()
}

private fun formatGpxTimestamp(timestamp: Long?): String? {
    if (timestamp == null || timestamp <= 0L) return null
    return runCatching {
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(timestamp))
    }.getOrNull()
}

private fun escapeXml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

private fun showGpxProgressNotification(context: android.content.Context, notificationId: Int) {
    if (!hasNotificationPermission(context)) return
    val notification = NotificationCompat.Builder(context, ExportUiStrings.NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_download)
        .setContentTitle(ExportUiStrings.PROGRESS_TITLE_GPX)
        .setContentText(ExportUiStrings.PROGRESS_SUBTITLE)
        .setProgress(0, 0, true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

private fun showGpxSavedNotification(
    context: android.content.Context,
    uri: Uri,
    fileName: String,
    notificationId: Int
) {
    if (!hasNotificationPermission(context)) return
    val openIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/gpx+xml")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        fileName.hashCode(),
        openIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notification = NotificationCompat.Builder(context, ExportUiStrings.NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_download_done)
        .setContentTitle(ExportUiStrings.COMPLETION_TITLE)
        .setContentText(fileName)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

private fun cancelGpxNotification(context: android.content.Context, notificationId: Int) {
    if (!hasNotificationPermission(context)) return
    NotificationManagerCompat.from(context).cancel(notificationId)
}

private fun hasNotificationPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) String.format("%dh %dm", hours, minutes % 60) else String.format("%dm %ds", minutes, seconds % 60)
}

private fun formatDurationShort(durationMs: Long): String {
    val minutes = durationMs / 1000 / 60
    val hours = minutes / 60
    return if (hours > 0) String.format("%d:%02d", hours, minutes % 60) else String.format("%d min", minutes)
}

private fun formatDurationWithUnits(durationMs: Long): String {
    val minutes = durationMs / 1000 / 60
    val hours = minutes / 60
    return if (hours > 0) String.format("%d h %d min", hours, minutes % 60) else String.format("%d min", minutes)
}

/**
 * Formatea la temperatura y evita el "-0" o "-0.0"
 */
private fun formatTemperature(temperature: Double, decimals: Int = 1): String {
    // 1. Obtenemos el valor absoluto para formatear el número "limpio"
    val absTemp = kotlin.math.abs(temperature)
    
    // 2. Usamos tu utilidad para formatear (ej: "0,0" o "17,5")
    val formatted = LocationUtils.formatNumberSpanish(absTemp, decimals)

    // 3. TRUCO DE MAGIA 🪄
    // Comprobamos si el número que vamos a mostrar es realmente un cero.
    // Reemplazamos la coma por punto para asegurar que toDouble() funcione.
    val isEffectiveZero = try {
        formatted.replace(",", ".").toDouble() == 0.0
    } catch (e: Exception) {
        false
    }

    // 4. Lógica de signo:
    // Solo ponemos el "-" si la temperatura original es negativa Y NO es un cero efectivo.
    return if (temperature < 0 && !isEffectiveZero) {
        "-$formatted"
    } else {
        formatted
    }
}

