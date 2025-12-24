package com.zipstats.app.ui.routes

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.zipstats.app.ui.components.ZipStatsText
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
    var vehicleIconRes by remember { mutableStateOf(R.drawable.ic_electric_scooter_adaptive) }
    var vehicleModel by remember { mutableStateOf(route.scooterName) }
    var showWeatherDialog by remember(route.id) { mutableStateOf(false) }

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
                .clip(RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface // O Color(0xFF1E1E1E) si quieres forzar oscuro
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // 1. ZONA HERO: EL MAPA (Ocupa la parte superior)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp) // Mapa grande
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
                            .background(Color.Black.copy(alpha=0.3f), CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, "Cerrar", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    // Añadir a Registros (Lista)
                    onAddToRecords?.let { addToRecords ->
                        IconButton(
                            onClick = addToRecords,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha=0.3f), CircleShape)
                                .size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, "Añadir", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Expandir (Fullscreen)
                    IconButton(
                        onClick = { showFullscreenMap = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Fullscreen, "Expandir", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // 2. CONTENIDO DE DATOS (Scrollable)
                Column(
                    modifier = Modifier
                        .weight(1f) // Ocupa el espacio restante
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    // Cabecera: Título y Vehículo
                    val title = CityUtils.getRouteTitleText(route)
                    ZipStatsText(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(16.dp))

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
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp, // Sombra para separar del scroll
                    modifier = Modifier.fillMaxWidth().zIndex(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
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
                            modifier = Modifier.size(50.dp)
                        ) {
                            Icon(Icons.Default.Delete, "Eliminar")
                        }

                        // ANIMAR (Grande - Principal)
                        Button(
                            onClick = { showAnimationDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ver Animación")
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
                            modifier = Modifier.size(50.dp)
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
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .zIndex(20f),
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                shadowElevation = 8.dp
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
                    route = route
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
            value = String.format("%.1f", route.totalDistance),
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
        val weatherIconRes = remember(route.weatherEmoji, route.weatherIsDay) {
            getWeatherIconResId(route.weatherEmoji, route.weatherIsDay ?: true)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                AdvancedStatRow("Velocidad Real", String.format("%.1f km/h", route.averageMovingSpeed), true)
                Spacer(modifier = Modifier.height(12.dp))
                AdvancedStatRow("Velocidad Máxima", String.format("%.1f km/h", route.maxSpeed), false)
                Spacer(modifier = Modifier.height(12.dp))
                AdvancedStatRow("Velocidad Media", String.format("%.1f km/h", route.averageSpeed), false)
                Spacer(modifier = Modifier.height(12.dp))
                AdvancedStatRow("En Movimiento (${String.format("%.0f%%", route.movingPercentage)})", formatDuration(route.movingTime), false)
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
    // Reutilizamos tu lógica de badges existente
    val hadRain = route.weatherHadRain == true
    val hasWetRoad = if (hadRain) false else checkWetRoadConditions(route)
    val hasExtremeConditions = checkExtremeConditions(route)

    if (hadRain || hasWetRoad || hasExtremeConditions) {
        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hadRain) SafetyBadge("🔵 Ruta realizada con lluvia", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            if (hasWetRoad) SafetyBadge("🟡 Precaución: calzada mojada", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            if (hasExtremeConditions) SafetyBadge("⚠️ Condiciones extremas", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
fun SafetyBadge(text: String, containerColor: Color, contentColor: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        ZipStatsText(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(12.dp),
            textAlign = TextAlign.Center
        )
    }
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
    val title: String = CityUtils.getRouteTitleText(route)

    ZipStatsText(
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
    var weatherIconRes by remember(route.id) { mutableStateOf(getWeatherIconResId(route.weatherEmoji, route.weatherIsDay)) }
    var weatherTemp by remember(route.id) { mutableStateOf(if (route.weatherTemperature != null) "${formatTemperature(route.weatherTemperature, decimals = 0)}°C" else "--°C") }
    var isLoadingWeather by remember(route.id) { mutableStateOf(false) }

    LaunchedEffect(route.id) {
        if (route.weatherTemperature != null) {
            weatherIconRes = getWeatherIconResId(route.weatherEmoji, route.weatherIsDay)
                    weatherTemp = "${formatTemperature(route.weatherTemperature, decimals = 0)}°C"
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
        StatChip(value = String.format("%.1f km", route.totalDistance), label = "Distancia", modifier = Modifier.weight(1f))
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
                    AdvancedStatRow("Velocidad Real", String.format("%.1f km/h", route.averageMovingSpeed), true)
                    Spacer(modifier = Modifier.height(8.dp))
                    AdvancedStatRow("Velocidad Máxima", String.format("%.1f km/h", route.maxSpeed), false)
                    Spacer(modifier = Modifier.height(8.dp))
                    AdvancedStatRow("Velocidad Media", String.format("%.1f km/h", route.averageSpeed), false)
                    Spacer(modifier = Modifier.height(8.dp))
                    AdvancedStatRow("En Movimiento (${String.format("%.0f%%", route.movingPercentage)})", formatDuration(route.movingTime), false)
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
                    // 1. HEADER (Icono + Temp)
                    Image(
                        painter = painterResource(id = getWeatherIconResId(route.weatherEmoji, route.weatherIsDay ?: true)),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp), // Un pelín más grande
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ZipStatsText(
                        text = route.weatherDescription?.substringBefore("(")?.trim() ?: "Clima",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    ZipStatsText(
                        text = route.weatherTemperature?.let { "${formatTemperature(it)}°C" } ?: "--°C",
                        style = MaterialTheme.typography.displayMedium, // Número grande e impactante
                        fontWeight = FontWeight.Black, // Extra Bold
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 2. GRID DE DETALLES (2 Columnas)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        // Columna Izquierda
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            route.weatherFeelsLike?.let {
                                WeatherGridItem(
                                    icon = Icons.Default.Thermostat,
                                    label = "Sensación",
                                    value = "${formatTemperature(it)}°C"
                                )
                            }
                            route.weatherHumidity?.let {
                                WeatherGridItem(
                                    icon = Icons.Default.WaterDrop,
                                    label = "Humedad",
                                    value = "${it}%"
                                )
                            }
                            route.weatherWindSpeed?.let {
                                WeatherGridItem(
                                    icon = Icons.Default.Air,
                                    label = "Viento",
                                    value = "${String.format("%.1f", it)} km/h"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Columna Derecha
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Lógica inteligente precipitación vs probabilidad
                            val precip = route.weatherMaxPrecipitation ?: 0.0
                            if (precip > 0.0) {
                                WeatherGridItem(Icons.Default.Grain, "Lluvia", "${String.format("%.1f", precip)} mm")
                            } else {
                                route.weatherRainProbability?.let {
                                    WeatherGridItem(Icons.Default.Cloud, "Prob. Lluvia", "$it%")
                                }
                            }
                            
                            // Índice UV (Solo si es de día) o Ráfagas
                            if (route.weatherIsDay && (route.weatherUvIndex ?: 0.0) > 0) {
                                WeatherGridItem(Icons.Default.WbSunny, "Índice UV", String.format("%.0f", route.weatherUvIndex!!))
                            } else {
                                route.weatherWindGusts?.let {
                                    WeatherGridItem(Icons.Default.Cyclone, "Ráfagas", "${String.format("%.1f", it)} km/h")
                                }
                            }
                        }
                    }

                    // 3. BADGE DE SEGURIDAD (Si aplica)
                    val hasWetRoad = if (route.weatherHadRain == true) false else checkWetRoadConditions(route)
                    val hadRain = route.weatherHadRain == true
                    val hasExtremeConditions = checkExtremeConditions(route)
                    
                    if (hasWetRoad || hadRain || hasExtremeConditions) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hadRain) {
                                SafetyBadge(
                                    text = "🔵 Ruta con lluvia",
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            if (hasWetRoad) {
                                SafetyBadge(
                                    text = "🟡 Precaución: calzada mojada",
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            if (hasExtremeConditions) {
                                SafetyBadge(
                                    text = "⚠️ Condiciones extremas",
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            ZipStatsText(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
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

/**
 * Verifica si hay condiciones de calzada mojada (SIN lluvia activa).
 * Se usa para mostrar el aviso amarillo. Si llueve, se muestra el aviso de lluvia (azul/rosa) y este se oculta.
 * Considera día/noche porque la evaporación cambia significativamente.
 * 🔒 IMPORTANTE: Solo evalúa condiciones probabilísticas si el cielo NO está despejado.
 */
private fun checkWetRoadConditions(route: Route): Boolean {
    // 1. EXCLUSIÓN: Si llovió durante la ruta, NO mostramos "Calzada Mojada".
    // ¿Por qué? Porque ya mostraremos el badge de "Ruta realizada con lluvia" que es más específico.
    if (route.weatherHadRain == true) {
        return false
    }
    
    val isDay = route.weatherIsDay ?: true // Por defecto asumimos día si no está definido
    
    // 🔒 Verificar si el cielo está despejado (usando emoji ya que Route no tiene weatherCode)
    val isClearSky = route.weatherEmoji?.let { emoji ->
        emoji == "☀️" || emoji == "🌙"
    } ?: false
    
    // 2. Calzada mojada considerando día/noche
    // Día: humedad muy alta (>90%) o probabilidad alta (>40%) - suelo puede estar mojado pero seca más rápido
    // Noche: humedad alta (>85%) es suficiente - el suelo tarda mucho más en secarse sin sol
    // 🔒 Solo evaluar condiciones probabilísticas si el cielo NO está despejado
    if (!isClearSky && route.weatherHumidity != null) {
        if (isDay) {
            // Día: necesita condiciones más extremas
            if (route.weatherHumidity >= 90) {
                return true
            }
            if (route.weatherRainProbability != null && route.weatherRainProbability > 40) {
                return true
            }
        } else {
            // Noche: con humedad alta el suelo tarda mucho en secarse
            if (route.weatherHumidity >= 85) {
                return true
            }
            if (route.weatherRainProbability != null && route.weatherRainProbability > 35) {
                return true
            }
        }
    }
    
    // 3. Si hay precipitación máxima registrada por la API pero no se detectó como "Lluvia activa"
    // (Ej: Llovió justo antes de salir o llovizna muy fina que no activó el sensor de lluvia pero mojó el suelo)
    // NOTA: Esta condición es independiente del estado del cielo (precipitación real medida)
    if (route.weatherMaxPrecipitation != null && route.weatherMaxPrecipitation > 0.1) {
        return true
    }
    
    return false
}

/**
 * Verifica si hay condiciones extremas durante la ruta
 */
private fun checkExtremeConditions(route: Route): Boolean {
    // 🔥 PRIORIDAD: Si se detectó durante la ruta, mostrar badge (independientemente de valores guardados)
    if (route.weatherHadExtremeConditions == true) {
        return true
    }
    
    // Si no hay flag, evaluar valores guardados (para compatibilidad con rutas antiguas)
    // Viento fuerte (>40 km/h)
    if (route.weatherWindSpeed != null && route.weatherWindSpeed > 40) {
        return true
    }
    
    // Ráfagas de viento muy fuertes (>60 km/h)
    if (route.weatherWindGusts != null && route.weatherWindGusts > 60) {
        return true
    }
    
    // Temperatura extrema (<0°C o >35°C)
    if (route.weatherTemperature != null) {
        if (route.weatherTemperature < 0 || route.weatherTemperature > 35) {
            return true
        }
    }
    
    // Índice UV muy alto (>8) - solo de día
    if (route.weatherIsDay && route.weatherUvIndex != null && route.weatherUvIndex > 8) {
        return true
    }
    
    // Tormenta (detectada por emoji o descripción)
    val isStorm = route.weatherEmoji?.let { emoji ->
        emoji.contains("⛈") || emoji.contains("⚡")
    } ?: false
    
    val isStormByDescription = route.weatherDescription?.let { desc ->
        desc.contains("Tormenta", ignoreCase = true) ||
        desc.contains("granizo", ignoreCase = true) ||
        desc.contains("rayo", ignoreCase = true)
    } ?: false
    
    if (isStorm || isStormByDescription) {
        return true
    }
    
    return false
}

// La lógica de compartir se ha movido a ShareUtils.kt

@DrawableRes
private fun getWeatherIconResId(emoji: String?, isDay: Boolean): Int {
    if (emoji.isNullOrBlank()) return R.drawable.help_outline

    // Mapeo de emoji a código de Open-Meteo para usar WeatherRepository
    val weatherCode = when (emoji) {
        // ☀️ Cielo Despejado
        "☀️" -> 0
        "🌙" -> 0

        // ⛅ Nubes Parciales
        "🌤️", "🌥️" -> if (isDay) 1 else 2
        "☁️🌙" -> 2

        // ☁️ Nublado
        "☁️" -> 3

        // 🌫️ Niebla
        "🌫️" -> 45

        // 🌦️ Lluvia Ligera / Chubascos leves
        "🌦️" -> if (isDay) 61 else 61

        // 🌧️ Lluvia Fuerte / Densa
        "🌧️" -> 65

        // 🥶 Aguanieve / Hielo
        "🥶" -> 66

        // ❄️ Nieve
        "❄️" -> 71

        // ⛈️ Tormenta / Granizo / Rayo
        "⚡" -> 95
        "⛈️" -> 96

        // Default: código desconocido
        else -> -1
    }

    // Si no encontramos el código, usar icono por defecto
    if (weatherCode == -1) return R.drawable.help_outline

    // Usar WeatherRepository para obtener el icono correcto
    return WeatherRepository.getIconResIdForWeather(weatherCode, if (isDay) 1 else 0)
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

/**
 * Formatea la temperatura asegurándose de que 0 se muestre sin signo menos
 */
private fun formatTemperature(temperature: Double, decimals: Int = 1): String {
    // Si la temperatura es exactamente 0 o muy cercana a 0, mostrar sin signo menos
    val absTemp = kotlin.math.abs(temperature)
    val formatted = if (decimals == 0) {
        String.format("%.0f", absTemp)
    } else {
        String.format("%.${decimals}f", absTemp)
    }
    
    // Si la temperatura original es negativa (y no es 0), añadir el signo menos
    return if (temperature < 0 && absTemp > 0.001) {
        "-$formatted"
    } else {
        formatted
    }
}

private fun convertWindDirectionToText(degrees: Int?): String {
    if (degrees == null) return "-"
    val directions = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    val index = ((degrees.toFloat() + 22.5f) % 360 / 45.0f).toInt()
    return directions[index]
}