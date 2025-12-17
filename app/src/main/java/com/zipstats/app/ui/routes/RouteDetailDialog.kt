package com.zipstats.app.ui.routes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.zipstats.app.ui.components.RouteSummaryCard
import com.zipstats.app.utils.CityUtils
import com.zipstats.app.utils.DateUtils
import com.zipstats.app.utils.ShareUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RouteDetailDialog(
    route: Route,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onAddToRecords: (() -> Unit)? = null,
    onShare: () -> Unit,
    allRoutes: List<Route> = emptyList(),
    onRouteChange: ((Route) -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Instancia única del repositorio usando remember para evitar crear múltiples instancias
    val vehicleRepository = remember {
        VehicleRepository(
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance()
        )
    }
    
    var mapboxMapRef by remember { mutableStateOf<com.mapbox.maps.MapView?>(null) }
    var fullscreenMapRef by remember { mutableStateOf<com.mapbox.maps.MapView?>(null) }
    var mapSnapshotTrigger by remember { mutableStateOf<MapSnapshotTrigger?>(null) }
    var showAdvancedDetails by remember { mutableStateOf(false) }
    var showFullscreenMap by remember { mutableStateOf(false) }
    var isCapturingForShare by remember { mutableStateOf(false) }
    var showAnimationDialog by remember { mutableStateOf(false) }
    var vehicleIconRes by remember { mutableStateOf(R.drawable.ic_electric_scooter_adaptive) }
    var vehicleModel by remember { mutableStateOf(route.scooterName) }

    // Estado para el diálogo del clima
    var showWeatherDialog by remember(route.id) { mutableStateOf(false) }

    if (showWeatherDialog) {
        WeatherInfoDialog(
            route = route,
            onDismiss = { showWeatherDialog = false }
        )
    }
    
    // LaunchedEffect para manejar la lógica de compartir cuando el trigger esté listo
    LaunchedEffect(isCapturingForShare, mapSnapshotTrigger) {
        // Solo entramos si el usuario quiere compartir Y el mapa ya nos dio el permiso para hacer fotos
        if (isCapturingForShare && mapSnapshotTrigger != null) {
            android.util.Log.d("RouteDetailDialog", "=== INICIANDO PROCESO DE COMPARTIR ===")
            android.util.Log.d("RouteDetailDialog", "Esperando a que el mapa esté completamente renderizado...")
            
            // Espera más larga para asegurar que el mapa esté completamente renderizado
            // y que los tiles hayan cargado visualmente
            kotlinx.coroutines.delay(2000) // Aumentado a 2 segundos
            
            android.util.Log.d("RouteDetailDialog", "Delay completado, llamando a ShareUtils...")
            
            try {
                ShareUtils.shareRouteImage(
                    context = context,
                    route = route,
                    snapshotTrigger = mapSnapshotTrigger!!,
                    vehicleRepository = vehicleRepository,
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

            if (vehicle != null && vehicle.modelo.isNotBlank()) {
                vehicleModel = vehicle.modelo
            } else {
                vehicleModel = route.scooterName
            }
        } catch (e: Exception) {
            vehicleIconRes = R.drawable.ic_electric_scooter_adaptive
            vehicleModel = route.scooterName
        }
    }

    Dialog(
        onDismissRequest = {
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = com.zipstats.app.ui.theme.DialogShape
            ) {
                Box(modifier = Modifier.fillMaxSize()) {

                    Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                    ) {
                        // Header minimalista
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            onAddToRecords?.let { addToRecords ->
                                IconButton(onClick = addToRecords) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.List,
                                        contentDescription = "Añadir a registros",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar"
                                )
                            }
                        }

                        // Contenido scrolleable
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            CompactHeader(route = route, vehicleIconRes = vehicleIconRes, vehicleName = vehicleModel)

                            Spacer(modifier = Modifier.height(8.dp))

                            RouteTitle(route = route)

                            Spacer(modifier = Modifier.height(12.dp))

                            // Mapa compacto
                            val mapClickInteractionSource = remember { MutableInteractionSource() }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = mapClickInteractionSource,
                                            indication = null,
                                            onClick = { showFullscreenMap = true }
                                        ),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    shape = com.zipstats.app.ui.theme.SmallCardShape
                                ) {
                                    CapturableMapView(
                                        route = route,
                                        onMapReady = { mapboxMap -> mapboxMapRef = mapboxMap },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(280.dp),
                                        isCompact = true, // Mapa pequeño, usar marcadores más grandes
                                        onStyleLoaded = { style ->
                                            // 1. Ocultar POIs (Tiendas, restaurantes, bancos...)
                                            style.getLayer("poi-label")?.visibility(Visibility.NONE)
                                            
                                            // 2. Ocultar transporte público (Paradas de bus, metro...) 
                                            // Queda mucho más limpio si lo quitas también.
                                            style.getLayer("transit-label")?.visibility(Visibility.NONE)
                                        }
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    shadowElevation = 4.dp
                                ) {
                                    IconButton(
                                        onClick = { showFullscreenMap = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fullscreen,
                                            contentDescription = "Ver en pantalla completa",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            StatsChips(
                                route = route,
                                onWeatherClick = { showWeatherDialog = true }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (route.movingTime > 0 || route.pauseCount > 0) {
                                CollapsibleAdvancedDetails(
                                    route = route,
                                    expanded = showAdvancedDetails,
                                    onToggle = { showAdvancedDetails = !showAdvancedDetails }
                                )
                            }

                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }

                    // Botones flotantes
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = onDelete,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar")
                        }

                        SmallFloatingActionButton(
                            onClick = { showAnimationDialog = true },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Animar ruta")
                        }

                        FloatingActionButton(
                            onClick = {
                                isCapturingForShare = true
                                showFullscreenMap = true
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Compartir")
                        }
                    }
                }
            }
        }
    }

    // Modal Fullscreen para Captura
    if (showFullscreenMap) {
        FullscreenMapDialog(
            route = route,
            onDismiss = {
                showFullscreenMap = false
                isCapturingForShare = false
                mapSnapshotTrigger = null
            },
            onSnapshotHandlerReady = { trigger ->
                // Guardamos el trigger en el estado
                mapSnapshotTrigger = trigger
            },
            onMapReady = { mapView ->
                fullscreenMapRef = mapView
            }
        )
    }
    
    // Modal de Animación
    if (showAnimationDialog) {
        RouteAnimationDialog(
            route = route,
            onDismiss = { showAnimationDialog = false }
        )
    }
}

// -------------------------------------------------------------------------
// DIÁLOGOS MODALES (MAPA FULLSCREEN Y CLIMA)
// -------------------------------------------------------------------------

@Composable
private fun FullscreenMapDialog(
    route: Route,
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
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .zIndex(10f),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 8.dp
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            TripDetailsOverlay(
                route = route,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            )
        }
    }
}

// -------------------------------------------------------------------------
// NUEVA TARJETA DE DETALLES DEL VIAJE (MATERIAL 3 / MODERN UI)
// -------------------------------------------------------------------------

@Composable
fun TripDetailsOverlay(
    route: Route,
    modifier: Modifier = Modifier
) {
    val tituloRuta: String = remember(route) { CityUtils.getRouteTitleText(route) }

    val fechaFormateada = remember(route.startTime) {
        val date = java.time.LocalDate.ofEpochDay(route.startTime / (1000 * 60 * 60 * 24))
        val formatter = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", java.util.Locale("es", "ES"))
        date.format(formatter)
    }

    val weatherIconRes = remember(route.weatherEmoji, route.weatherIsDay) {
        if (route.weatherTemperature != null) {
            getWeatherIconResId(route.weatherEmoji, route.weatherIsDay)
        } else {
            null
        }
    }

    val weatherText = remember(route.weatherDescription) {
        route.weatherDescription?.substringBefore("(")?.trim()
    }

    RouteSummaryCard(
        title = tituloRuta,
        subtitle = "${route.scooterName} • $fechaFormateada",
        distanceKm = route.totalDistance.toFloat(),
        duration = formatDurationWithUnits(route.totalDuration),
        avgSpeed = route.averageSpeed.toFloat(),
        temperature = route.weatherTemperature?.toInt(),
        weatherText = weatherText,
        weatherIconRes = weatherIconRes,
        modifier = modifier
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
        Text(
            text = vehicleName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
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
        Text(
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
    val title: String = CityUtils.getRouteTitleText(route)

    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun StatsChips(route: Route, onWeatherClick: () -> Unit) {
    val weatherClickInteractionSource = remember { MutableInteractionSource() }
    var weatherIconRes by remember(route.id) { mutableStateOf(getWeatherIconResId(route.weatherEmoji, route.weatherIsDay)) }
    var weatherTemp by remember(route.id) { mutableStateOf(if (route.weatherTemperature != null) String.format("%.0f°C", route.weatherTemperature) else "--°C") }
    var isLoadingWeather by remember(route.id) { mutableStateOf(false) }

    LaunchedEffect(route.id) {
        if (route.weatherTemperature != null) {
            weatherIconRes = getWeatherIconResId(route.weatherEmoji, route.weatherIsDay)
            weatherTemp = String.format("%.0f°C", route.weatherTemperature)
            return@LaunchedEffect
        }
        if (route.points.isNotEmpty()) {
            isLoadingWeather = true
            try {
                val weatherRepository = WeatherRepository()
                val firstPoint = route.points.first()
                val result = weatherRepository.getCurrentWeather(firstPoint.latitude, firstPoint.longitude)
                result.onSuccess { weather ->
                    weatherIconRes = getWeatherIconResId(weather.weatherEmoji, weather.isDay)
                    weatherTemp = String.format("%.0f°C", weather.temperature)
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
        StatChip(value = String.format("%.1f km", route.totalDistance), label = "Distancia", modifier = Modifier.weight(1f))
        StatChip(value = formatDurationWithUnits(route.totalDuration), label = "Duración", modifier = Modifier.weight(1f))
        StatChip(
            value = if (isLoadingWeather) "..." else weatherTemp,
            label = "Clima",
            iconRes = weatherIconRes,
            modifier = Modifier.weight(1f).clickable(
                interactionSource = weatherClickInteractionSource,
                indication = null,
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (iconRes != null) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).padding(end = 6.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
                    )
                }
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun CollapsibleAdvancedDetails(route: Route, expanded: Boolean, onToggle: () -> Unit) {
    val toggleInteractionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(
                    interactionSource = toggleInteractionSource,
                    indication = null,
                    onClick = { onToggle() }
                ).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Ver más detalles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }

            if (expanded) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    AdvancedStatRow("Velocidad Real", String.format("%.1f km/h", route.averageMovingSpeed), true)
                    Spacer(modifier = Modifier.height(8.dp))
                    AdvancedStatRow("Velocidad Máxima", String.format("%.1f km/h", route.maxSpeed), false)
                    Spacer(modifier = Modifier.height(8.dp))
                    AdvancedStatRow("Velocidad Media", String.format("%.1f km/h", route.averageSpeed), false)
                    Spacer(modifier = Modifier.height(8.dp))
                    AdvancedStatRow("Tiempo en Movimiento (${String.format("%.0f%%", route.movingPercentage)})", formatDuration(route.movingTime), false)
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = getWeatherIconResId(route.weatherEmoji, route.weatherIsDay)),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = route.weatherDescription?.substringBefore("(")?.trim() ?: "Detalles del Clima", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "${String.format("%.1f", route.weatherTemperature)}°C", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    route.weatherFeelsLike?.let { WeatherDetailRow(Icons.Default.Thermostat, "Sensación térmica", "${String.format("%.1f", it)}°C") }
                    route.weatherHumidity?.let { WeatherDetailRow(Icons.Default.WaterDrop, "Humedad", "${it}%") }
                    route.weatherWindSpeed?.let { WeatherDetailRow(Icons.Default.Air, "Viento", "${String.format("%.1f", it)} km/h (${convertWindDirectionToText(route.weatherWindDirection)})") }
                    route.weatherWindGusts?.let { WeatherDetailRow(Icons.Default.Cyclone, "Ráfagas", "${String.format("%.1f", it)} km/h") }
                    route.weatherRainProbability?.let { WeatherDetailRow(Icons.Default.Grain, "Prob. de lluvia", "$it%") }
                    if (route.weatherIsDay && route.weatherUvIndex != null && route.weatherUvIndex > 0) {
                        WeatherDetailRow(Icons.Default.WbSunny, "Índice UV", String.format("%.0f", route.weatherUvIndex))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
            }
        }
    }
}

@Composable
private fun WeatherDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

// La lógica de compartir se ha movido a ShareUtils.kt

@DrawableRes
private fun getWeatherIconResId(emoji: String?, isDay: Boolean): Int {
    if (emoji.isNullOrBlank()) return R.drawable.help_outline

    return when (emoji) {
        // ☀️ Cielo Despejado
        "☀️" -> R.drawable.wb_sunny
        "🌙" -> R.drawable.nightlight

        // ⛅ Nubes Parciales
        "🌤️", "🌥️","☁️🌙" -> if (isDay) R.drawable.partly_cloudy_day else R.drawable.partly_cloudy_night

        // ☁️ Nublado (A veces la API manda esto de noche también)
        "☁️" -> R.drawable.cloud

        // 🌫️ Niebla
        "🌫️" -> R.drawable.foggy

        // 🌦️ Lluvia Ligera / Chubascos leves (Sol con lluvia) -> Icono Normal
        "🌦️" -> R.drawable.rainy

        // 🌧️ Lluvia Fuerte / Densa (Solo nube) -> Icono HEAVY (Nuevo)
        "🌧️" -> R.drawable.rainy_heavy

        // 🥶 Aguanieve / Hielo (Cara de frío) -> Icono SNOWY RAINY (Nuevo)
        "🥶" -> R.drawable.rainy_snow

        // ❄️ Nieve
        "❄️" -> R.drawable.snowing

        // ⛈️ Tormenta / Granizo / Rayo
        "⛈️", "⚡" -> R.drawable.thunderstorm
        // Nota: Si tienes R.drawable.hail, puedes asignar "⛈️" a ese.

        // 🤷 Default
        else -> R.drawable.help_outline
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

private fun convertWindDirectionToText(degrees: Int?): String {
    if (degrees == null) return "-"
    val directions = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    val index = ((degrees.toFloat() + 22.5f) % 360 / 45.0f).toInt()
    return directions[index]
}