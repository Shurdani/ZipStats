package com.zipstats.app.ui.routes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.zipstats.app.R
import com.zipstats.app.model.Route
import com.zipstats.app.model.VehicleType
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.repository.WeatherRepository
import com.zipstats.app.ui.components.CapturableMapView
import com.zipstats.app.utils.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
    var googleMapRef by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    var fullscreenMapRef by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    var showAdvancedDetails by remember { mutableStateOf(false) }
    var showFullscreenMap by remember { mutableStateOf(false) }
    var isCapturingForShare by remember { mutableStateOf(false) }
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

    // Obtener info del vehículo
    LaunchedEffect(route.scooterId) {
        try {
            val vehicle = getVehicleById(route.scooterId)
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
                                        onMapReady = { googleMap -> googleMapRef = googleMap },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(280.dp)
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
            },
            onMapReady = { googleMap ->
                fullscreenMapRef = googleMap
                if (isCapturingForShare && googleMap != null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        shareRouteWithRealMapAndClose(
                            route = route,
                            googleMap = googleMap,
                            context = context,
                            onComplete = {
                                showFullscreenMap = false
                                isCapturingForShare = false
                            }
                        )
                    }, 1000)
                }
            }
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
    onMapReady: ((com.google.android.gms.maps.GoogleMap?) -> Unit)? = null
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
                onMapReady = { googleMap ->
                    // PADDING PARA EL MAPA: Centra la ruta más arriba para dejar sitio a la tarjeta
                    googleMap.setPadding(0, 0, 0, 700)
                    onMapReady?.invoke(googleMap)
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
    val tituloRuta = remember(route) { getRouteTitleText(route) }

    val fechaFormateada = remember(route.startTime) {
        val date = java.time.LocalDate.ofEpochDay(route.startTime / (1000 * 60 * 60 * 24))
        val formatter = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", java.util.Locale("es", "ES"))
        date.format(formatter)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF1E1E1E).copy(alpha = 0.98f)
        ),
        elevation = CardDefaults.cardElevation(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = tituloRuta,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "ZipStats",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = "${route.scooterName} • $fechaFormateada",
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color.LightGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItemModern(Icons.Default.Straighten, String.format("%.1f km", route.totalDistance), "Distancia")
                StatItemModern(Icons.Default.Timer, formatDurationWithUnits(route.totalDuration), "Tiempo")
                StatItemModern(Icons.Default.Speed, String.format("%.1f km/h", route.averageSpeed), "Vel. Media")
            }

            if (route.weatherTemperature != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = getWeatherIconResId(route.weatherEmoji, route.weatherIsDay)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(androidx.compose.ui.graphics.Color.White)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "${String.format("%.0f", route.weatherTemperature)}°C",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = androidx.compose.ui.graphics.Color.White
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = (route.weatherDescription ?: "").substringBefore("(").trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItemModern(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color(0xFF90CAF9),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = androidx.compose.ui.graphics.Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)
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
    val title = getRouteTitleText(route)

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

// -------------------------------------------------------------------------
// LÓGICA DE GENERACIÓN DE IMAGEN (CANVAS MANUAL - ESTILO MODERNO v2.0)
// -------------------------------------------------------------------------

private fun shareRouteWithRealMapAndClose(
    route: Route,
    googleMap: com.google.android.gms.maps.GoogleMap,
    context: Context,
    onComplete: () -> Unit
) {
    try {
        android.util.Log.d("RouteDetailDialog", "=== INICIO COMPARTIR ===")
        Toast.makeText(context, "Generando imagen...", Toast.LENGTH_SHORT).show()
        
        android.util.Log.d("RouteDetailDialog", "Capturando snapshot del mapa...")
        googleMap.snapshot { snapshotBitmap ->
            android.util.Log.d("RouteDetailDialog", "Snapshot capturado: ${snapshotBitmap != null}, width=${snapshotBitmap?.width}, height=${snapshotBitmap?.height}")
            
            if (snapshotBitmap == null) {
                Toast.makeText(context, "Error al capturar el mapa", Toast.LENGTH_SHORT).show()
                onComplete()
                return@snapshot
            }

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    android.util.Log.d("RouteDetailDialog", "Creando imagen final...")
                    val finalBitmap = withContext(Dispatchers.IO) {
                        createFinalRouteImageFromFullscreenCanvas(context, route, snapshotBitmap)
                    }
                    android.util.Log.d("RouteDetailDialog", "Imagen final creada: width=${finalBitmap.width}, height=${finalBitmap.height}")
                    
                    android.util.Log.d("RouteDetailDialog", "Compartiendo imagen...")
                    shareBitmap(context, finalBitmap, route)
                    android.util.Log.d("RouteDetailDialog", "=== FIN COMPARTIR ===")
                    
                    onComplete()
                } catch (e: Exception) {
                    android.util.Log.e("RouteDetailDialog", "Error al procesar imagen: ${e.message}", e)
                    Toast.makeText(context, "Error al procesar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                    onComplete()
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("RouteDetailDialog", "Error al compartir: ${e.message}", e)
        Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
        onComplete()
    }
}

private suspend fun createFinalRouteImageFromFullscreenCanvas(
    context: Context,
    route: Route,
    mapBitmap: Bitmap
): Bitmap {
    val width = mapBitmap.width
    val height = mapBitmap.height
    val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(finalBitmap)

    // 1. Dibujar mapa de fondo
    canvas.drawBitmap(mapBitmap, 0f, 0f, null)

    // 2. Inflar y configurar la tarjeta XML
    val inflater = android.view.LayoutInflater.from(context)
    val cardView = inflater.inflate(R.layout.share_route_stats_card, null) as androidx.cardview.widget.CardView
    
    // Configurar la tarjeta con los datos de la ruta
    configurarTarjetaCompartir(cardView, route, context)
    
    // Medir y renderizar la tarjeta
    val cardWidth = width - 64 // Márgenes de 32dp a cada lado
    val measureSpec = android.view.View.MeasureSpec.makeMeasureSpec(cardWidth, android.view.View.MeasureSpec.EXACTLY)
    cardView.measure(measureSpec, android.view.View.MeasureSpec.UNSPECIFIED)
    
    val cardHeight = cardView.measuredHeight
    val cardX = 32
    val cardY = height - cardHeight - 32 // Anclar al borde inferior
    
    cardView.layout(0, 0, cardView.measuredWidth, cardHeight)
    
    // Dibujar la tarjeta en el canvas
    canvas.save()
    canvas.translate(cardX.toFloat(), cardY.toFloat())
    cardView.draw(canvas)
    canvas.restore()

    return finalBitmap
}

private suspend fun configurarTarjetaCompartir(
    cardView: androidx.cardview.widget.CardView,
    route: Route,
    context: Context
) {
    // Configurar título de la ruta
    val routeTitle = getRouteTitleText(route)
    cardView.findViewById<android.widget.TextView>(R.id.routeTitle).text = routeTitle
    
    // Configurar métricas
    cardView.findViewById<android.widget.TextView>(R.id.distanceValue).text = 
        String.format("%.1f km", route.totalDistance)
    cardView.findViewById<android.widget.TextView>(R.id.timeValue).text = 
        formatDurationWithUnits(route.totalDuration)
    cardView.findViewById<android.widget.TextView>(R.id.speedValue).text = 
        String.format("%.1f km/h", route.averageSpeed)
    
    // Configurar clima si está disponible
    if (route.weatherEmoji != null && route.weatherTemperature != null) {
        val weatherIconRes = getWeatherIconResId(route.weatherEmoji, route.weatherIsDay)
        cardView.findViewById<android.widget.ImageView>(R.id.weatherIcon).setImageResource(weatherIconRes)
        cardView.findViewById<android.widget.ImageView>(R.id.weatherIcon).setColorFilter(android.graphics.Color.WHITE)
        cardView.findViewById<android.widget.TextView>(R.id.weatherTemp).text = 
            String.format("%.0f°C", route.weatherTemperature)
        cardView.findViewById<android.widget.LinearLayout>(R.id.weatherContainer).visibility = android.view.View.VISIBLE
    } else {
        cardView.findViewById<android.widget.LinearLayout>(R.id.weatherContainer).visibility = android.view.View.GONE
    }
    
    // Configurar icono del vehículo
    val vehicle = getVehicleById(route.scooterId)
    val vehicleIconRes = getVehicleIconResource(vehicle?.vehicleType)
    cardView.findViewById<android.widget.ImageView>(R.id.vehicleIcon).setImageResource(vehicleIconRes)
    
    // Configurar información del vehículo y fecha
    val vehicleInfoText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val date = java.time.LocalDate.ofEpochDay(route.startTime / (1000 * 60 * 60 * 24))
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", java.util.Locale.forLanguageTag("es-ES"))
        "${route.scooterName} | ${date.format(dateFormatter)}"
    } else {
        val simpleDateFormat = java.text.SimpleDateFormat("d 'de' MMMM 'de' yyyy", java.util.Locale.forLanguageTag("es-ES"))
        "${route.scooterName} | ${simpleDateFormat.format(java.util.Date(route.startTime))}"
    }
    cardView.findViewById<android.widget.TextView>(R.id.vehicleInfo).text = vehicleInfoText
    
    // Eliminar el logo de ZipStats si existe
    try {
        cardView.findViewById<android.widget.TextView>(R.id.zipstatsBranding)?.setCompoundDrawables(null, null, null, null)
    } catch (e: Exception) {
        // Ignorar si no existe
    }
}

// Helper para título en canvas (ya no se usa pero lo mantengo por compatibilidad)
private fun getRouteTitleText(route: Route): String {
    if (route.notes.isNotBlank()) return route.notes
    if (route.points.isEmpty()) return "Mi paseo"

    val startCity = getCityName(route.points.first().latitude, route.points.first().longitude)
    val endCity = getCityName(route.points.last().latitude, route.points.last().longitude)

    return if (startCity != null && endCity != null && startCity != endCity) {
        "$startCity → $endCity"
    } else if (startCity != null) {
        "Paseo en $startCity"
    } else {
        "Mi paseo"
    }
}

// Función obsoleta eliminada - ahora usamos layout XML

// Helper vital para convertir Vectores (XML) a Bitmaps que el Canvas entienda
private fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap? {
    val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null

    // Si es VectorDrawable, necesitamos envolverlo
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth.takeIf { it > 0 } ?: 512,
        drawable.intrinsicHeight.takeIf { it > 0 } ?: 512,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}



private fun shareBitmap(context: Context, bitmap: Bitmap, route: Route) {
    try {
        val cacheDir = context.cacheDir
        val shareDir = File(cacheDir, "shared_routes")
        if (!shareDir.exists()) shareDir.mkdirs()
        val imageFile = File(shareDir, "route_${route.id}.png")
        FileOutputStream(imageFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val imageUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Compartir ruta")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun getVehicleById(vehicleId: String): com.zipstats.app.model.Vehicle? {
    // Simulación simple para no complicar dependencias aquí
    // En un caso real inyectarías el repositorio o lo pasarías
    return try {
        val repo = VehicleRepository(
            com.google.firebase.firestore.FirebaseFirestore.getInstance(),
            com.google.firebase.auth.FirebaseAuth.getInstance()
        )
        repo.getUserVehicles().find { it.id == vehicleId }
    } catch (e: Exception) { null }
}

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

// --- Lógica de Ciudades ---
data class CityArea(val name: String, val latMin: Double, val latMax: Double, val lonMin: Double, val lonMax: Double)

private val cityAreas = listOf(
    // CAPITALES DE PROVINCIA Y CIUDADES IMPORTANTES DE ESPAÑA
    CityArea("A Coruña", 43.35, 43.40, -8.45, -8.35),
    CityArea("Albacete", 38.95, 39.05, -1.90, -1.80),
    CityArea("Alcalá de Henares", 40.47, 40.49, -3.38, -3.32),
    CityArea("Alicante", 38.30, 38.40, -0.55, -0.45),
    CityArea("Almería", 36.80, 36.90, -2.50, -2.40),
    CityArea("Ávila", 40.63, 40.68, -4.75, -4.65),
    CityArea("Badajoz", 38.85, 38.90, -7.02, -6.92),
    CityArea("Barcelona", 41.35, 41.45, 2.10, 2.25),
    CityArea("Bilbao", 43.20, 43.30, -3.00, -2.90),
    CityArea("Burgos", 42.32, 42.37, -3.75, -3.65),
    CityArea("Cáceres", 39.45, 39.50, -6.42, -6.32),
    CityArea("Cádiz", 36.48, 36.54, -6.35, -6.20),
    CityArea("Castellón de la Plana", 39.95, 40.05, -0.10, 0.00),
    CityArea("Ceuta", 35.87, 35.91, -5.35, -5.28),
    CityArea("Ciudad Real", 38.96, 39.01, -3.97, -3.87),
    CityArea("Córdoba", 37.85, 37.90, -4.85, -4.75),
    CityArea("Cuenca", 40.05, 40.10, -2.18, -2.08),
    CityArea("Gijón", 43.50, 43.55, -5.70, -5.60),
    CityArea("Girona", 41.95, 42.00, 2.78, 2.88),
    CityArea("Granada", 37.15, 37.20, -3.65, -3.55),
    CityArea("Guadalajara", 40.61, 40.66, -3.21, -3.11),
    CityArea("Huelva", 37.23, 37.28, -7.00, -6.90),
    CityArea("Huesca", 42.11, 42.16, -0.45, -0.35),
    CityArea("Jaén", 37.75, 37.80, -3.83, -3.73),
    CityArea("León", 42.57, 42.62, -5.62, -5.52),
    CityArea("Lleida", 41.59, 41.64, 0.58, 0.68),
    CityArea("Logroño", 42.44, 42.49, -2.49, -2.39),
    CityArea("Lugo", 42.99, 43.04, -7.60, -7.50),
    CityArea("Madrid", 40.35, 40.50, -3.80, -3.60),
    CityArea("Málaga", 36.60, 36.80, -4.50, -4.30),
    CityArea("Melilla", 35.27, 35.31, -2.97, -2.91),
    CityArea("Murcia", 37.90, 38.00, -1.20, -1.10),
    CityArea("Ourense", 42.31, 42.36, -7.91, -7.81),
    CityArea("Oviedo", 43.35, 43.40, -5.90, -5.80),
    CityArea("Palencia", 41.98, 42.03, -4.57, -4.47),
    CityArea("Palma", 39.50, 39.60, 2.60, 2.70),
    CityArea("Pamplona", 42.80, 42.85, -1.70, -1.60),
    CityArea("Pontevedra", 42.41, 42.46, -8.69, -8.59),
    CityArea("Salamanca", 40.94, 40.99, -5.70, -5.60),
    CityArea("San Sebastián", 43.30, 43.35, -2.00, -1.90),
    CityArea("Santander", 43.45, 43.50, -3.85, -3.75),
    CityArea("Segovia", 40.92, 40.97, -4.16, -4.06),
    CityArea("Sevilla", 37.30, 37.45, -6.05, -5.85),
    CityArea("Soria", 41.74, 41.79, -2.51, -2.41),
    CityArea("Tarragona", 41.09, 41.14, 1.20, 1.30),
    CityArea("Teruel", 40.32, 40.37, -1.15, -1.05),
    CityArea("Toledo", 39.83, 39.88, -4.07, -3.97),
    CityArea("Valencia", 39.40, 39.50, -0.40, -0.30),
    CityArea("Valladolid", 41.62, 41.67, -4.77, -4.67),
    CityArea("Vigo", 42.20, 42.25, -8.75, -8.65),
    CityArea("Vitoria-Gasteiz", 42.82, 42.87, -2.72, -2.62),
    CityArea("Zamora", 41.48, 41.53, -5.80, -5.70),
    CityArea("Zaragoza", 41.60, 41.70, -0.95, -0.85),

    // MUNICIPIOS DE CATALUÑA (BARCELONA)
    CityArea("Badalona", 41.43, 41.46, 2.23, 2.26),
    CityArea("Berga", 42.09, 42.11, 1.83, 1.86),
    CityArea("Castelldefels", 41.27, 41.29, 1.96, 1.98),
    CityArea("Cerdanyola del Vallès", 41.48, 41.50, 2.13, 2.15),
    CityArea("Cornellà de Llobregat", 41.34, 41.36, 2.07, 2.09),
    CityArea("El Prat de Llobregat", 41.31, 41.33, 2.07, 2.09),
    CityArea("Esplugues de Llobregat", 41.36, 41.38, 2.07, 2.09),
    CityArea("Gavà", 41.28, 41.30, 2.00, 2.02),
    CityArea("Granollers", 41.59, 41.62, 2.27, 2.30),
    CityArea("L'Hospitalet de Llobregat", 41.35, 41.37, 2.09, 2.11),
    CityArea("Igualada", 41.57, 41.59, 1.60, 1.63),
    CityArea("Manresa", 41.71, 41.74, 1.81, 1.84),
    CityArea("Mataró", 41.53, 41.55, 2.43, 2.46),
    CityArea("Mollet del Vallès", 41.53, 41.55, 2.20, 2.23),
    CityArea("Montgat", 41.46, 41.48, 2.27, 2.29),
    CityArea("Premià de Mar", 41.49, 41.51, 2.35, 2.38),
    CityArea("Rubí", 41.48, 41.50, 2.03, 2.05),
    CityArea("Sabadell", 41.53, 41.56, 2.10, 2.13),
    CityArea("Sant Adrià de Besòs", 41.42, 41.44, 2.21, 2.23),
    CityArea("Sant Boi de Llobregat", 41.33, 41.35, 2.02, 2.04),
    CityArea("Sant Cugat del Vallès", 41.46, 41.48, 2.07, 2.09),
    CityArea("Santa Coloma de Gramenet", 41.44, 41.46, 2.20, 2.22),
    CityArea("Sitges", 41.22, 41.25, 1.78, 1.82),
    CityArea("Terrassa", 41.56, 41.59, 2.00, 2.04),
    CityArea("Vic", 41.92, 41.94, 2.24, 2.27),
    CityArea("Vilafranca del Penedès", 41.33, 41.36, 1.68, 1.71),
    CityArea("Vilanova i la Geltrú", 41.21, 41.24, 1.71, 1.74),
    CityArea("Viladecans", 41.30, 41.32, 2.00, 2.02),

    // MUNICIPIOS DE CATALUÑA (GIRONA)
    CityArea("Banyoles", 42.10, 42.13, 2.75, 2.78),
    CityArea("Blanes", 41.66, 41.69, 2.78, 2.81),
    CityArea("Figueres", 42.25, 42.28, 2.95, 2.98),
    CityArea("Lloret de Mar", 41.69, 41.71, 2.83, 2.86),
    CityArea("Olot", 42.17, 42.19, 2.47, 2.50),
    CityArea("Palafrugell", 41.90, 41.93, 3.15, 3.18),
    CityArea("Salt", 41.96, 41.98, 2.78, 2.81),
    CityArea("Sant Feliu de Guíxols", 41.77, 41.79, 3.01, 3.04),

    // MUNICIPIOS DE CATALUÑA (LLEIDA)
    CityArea("Balaguer", 41.78, 41.80, 0.80, 0.83),
    CityArea("La Seu d'Urgell", 42.35, 42.37, 1.44, 1.47),
    CityArea("Mollerussa", 41.62, 41.64, 0.88, 0.91),
    CityArea("Tàrrega", 41.63, 41.66, 1.13, 1.16),

    // MUNICIPIOS DE CATALUÑA (TARRAGONA)
    CityArea("Amposta", 40.70, 40.72, 0.57, 0.60),
    CityArea("Cambrils", 41.06, 41.08, 1.04, 1.07),
    CityArea("El Vendrell", 41.21, 41.23, 1.52, 1.55),
    CityArea("Reus", 41.14, 41.17, 1.09, 1.12),
    CityArea("Salou", 41.06, 41.09, 1.12, 1.15),
    CityArea("Tortosa", 40.80, 40.83, 0.51, 0.54),
    CityArea("Valls", 41.27, 41.30, 1.24, 1.27),

    // ÁREA METROPOLITANA DE MADRID
    CityArea("Alcorcón", 40.34, 40.36, -3.84, -3.82),
    CityArea("Alcobendas", 40.52, 40.54, -3.64, -3.62),
    CityArea("Aranjuez", 40.02, 40.04, -3.60, -3.58),
    CityArea("Coslada", 40.42, 40.44, -3.57, -3.55),
    CityArea("Fuenlabrada", 40.27, 40.29, -3.81, -3.79),
    CityArea("Getafe", 40.30, 40.32, -3.73, -3.67),
    CityArea("Las Rozas de Madrid", 40.48, 40.50, -3.89, -3.87),
    CityArea("Leganés", 40.31, 40.33, -3.76, -3.74),
    CityArea("Majadahonda", 40.46, 40.48, -3.87, -3.85),
    CityArea("Móstoles", 40.31, 40.33, -3.88, -3.86),
    CityArea("Parla", 40.23, 40.25, -3.78, -3.76),
    CityArea("Pinto", 40.24, 40.26, -3.71, -3.69),
    CityArea("Pozuelo de Alarcón", 40.42, 40.44, -3.82, -3.80),
    CityArea("San Sebastián de los Reyes", 40.54, 40.56, -3.63, -3.61),
    CityArea("Torrejón de Ardoz", 40.44, 40.46, -3.49, -3.47),
    CityArea("Valdemoro", 40.18, 40.20, -3.67, -3.65),

    // ÁREA METROPOLITANA DE VALENCIA
    CityArea("Alboraya", 39.50, 39.52, -0.35, -0.33),
    CityArea("Burjassot", 39.50, 39.52, -0.42, -0.40),
    CityArea("Manises", 39.49, 39.51, -0.46, -0.44),
    CityArea("Mislata", 39.47, 39.49, -0.42, -0.40),
    CityArea("Paiporta", 39.42, 39.44, -0.42, -0.40),
    CityArea("Paterna", 39.50, 39.52, -0.44, -0.42),
    CityArea("Silla", 39.36, 39.38, -0.42, -0.40),
    CityArea("Tavernes Blanques", 39.51, 39.53, -0.36, -0.34),
    CityArea("Torrent", 39.43, 39.45, -0.47, -0.45),
    CityArea("Xirivella", 39.46, 39.48, -0.42, -0.40),

    // ÁREA METROPOLITANA DE SEVILLA
    CityArea("Alcalá de Guadaíra", 37.32, 37.34, -5.86, -5.84),
    CityArea("Alcalá del Río", 37.52, 37.54, -5.97, -5.95),
    CityArea("Bormujos", 37.36, 37.38, -6.07, -6.05),
    CityArea("Coria del Río", 37.27, 37.29, -6.05, -6.03),
    CityArea("Dos Hermanas", 37.26, 37.28, -5.95, -5.93),
    CityArea("La Rinconada", 37.48, 37.50, -5.99, -5.97),
    CityArea("Los Palacios y Villafranca", 37.15, 37.17, -5.93, -5.91),
    CityArea("Mairena del Aljarafe", 37.33, 37.35, -6.08, -6.06),
    CityArea("San Juan de Aznalfarache", 37.35, 37.37, -6.03, -6.01),
    CityArea("Tomares", 37.37, 37.39, -6.04, -6.02),

    // ÁREA METROPOLITANA DE BILBAO
    CityArea("Barakaldo", 43.29, 43.31, -3.01, -2.99),
    CityArea("Basauri", 43.23, 43.25, -2.90, -2.88),
    CityArea("Getxo", 43.34, 43.36, -3.03, -3.01),
    CityArea("Portugalete", 43.31, 43.33, -3.03, -3.01),
    CityArea("Santurtzi", 43.32, 43.34, -3.04, -3.02),

    // ÁREA METROPOLITANA DE MÁLAGA (COSTA DEL SOL)
    CityArea("Benalmádena", 36.59, 36.61, -4.53, -4.51),
    CityArea("Estepona", 36.41, 36.44, -5.17, -5.13),
    CityArea("Fuengirola", 36.53, 36.55, -4.63, -4.61),
    CityArea("Marbella", 36.48, 36.52, -4.95, -4.85),
    CityArea("Mijas", 36.58, 36.60, -4.65, -4.63),
    CityArea("Rincón de la Victoria", 36.71, 36.73, -4.29, -4.27),
    CityArea("Torremolinos", 36.61, 36.63, -4.51, -4.49),
    CityArea("Vélez-Málaga", 36.77, 36.80, -4.12, -4.08),

    // ÁREA METROPOLITANA DE ZARAGOZA
    CityArea("Cuarte de Huerva", 41.59, 41.61, -0.94, -0.92),
    CityArea("La Puebla de Alfindén", 41.66, 41.68, -0.77, -0.75),
    CityArea("Utebo", 41.69, 41.71, -1.00, -0.98),

    // ÁREA METROPOLITANA DE ALICANTE-ELCHE
    CityArea("Elche", 38.25, 38.29, -0.73, -0.67),
    CityArea("Elda", 38.47, 38.49, -0.80, -0.78),
    CityArea("Petrer", 38.48, 38.50, -0.78, -0.76),
    CityArea("San Vicente del Raspeig", 38.39, 38.41, -0.53, -0.51),

    // ÁREA METROPOLITANA DE MURCIA
    CityArea("Alcantarilla", 37.93, 37.95, -1.22, -1.20),
    CityArea("Las Torres de Cotillas", 38.02, 38.04, -1.25, -1.23),
    CityArea("Molina de Segura", 38.04, 38.06, -1.22, -1.20),

    // ÁREA METROPOLITANA DE VIGO
    CityArea("Cangas de Morrazo", 42.25, 42.27, -8.80, -8.78),
    CityArea("Moaña", 42.27, 42.29, -8.76, -8.74),
    CityArea("Redondela", 42.27, 42.29, -8.62, -8.60),

    // ÁREA METROPOLITANA DE A CORUÑA
    CityArea("Arteixo", 43.29, 43.31, -8.52, -8.50),
    CityArea("Culleredo", 43.28, 43.30, -8.40, -8.38),
    CityArea("Oleiros", 43.33, 43.35, -8.32, -8.30),

    // BAHÍA DE CÁDIZ
    CityArea("Chiclana de la Frontera", 36.40, 36.44, -6.17, -6.13),
    CityArea("El Puerto de Santa María", 36.58, 36.62, -6.25, -6.21),
    CityArea("Jerez de la Frontera", 36.67, 36.71, -6.16, -6.10),
    CityArea("Puerto Real", 36.51, 36.54, -6.15, -6.11),
    CityArea("San Fernando", 36.45, 36.48, -6.22, -6.18),

    // ÁREA METROPOLITANA DE CASTELLÓN Y LEVANTE
    CityArea("Almassora", 39.93, 39.96, -0.08, -0.04),
    CityArea("Benicàssim", 40.04, 40.07, 0.05, 0.08),
    CityArea("Benidorm", 38.52, 38.55, -0.15, -0.10),
    CityArea("Gandia", 38.95, 38.98, -0.20, -0.15),
    CityArea("Torrevieja", 37.96, 38.00, -0.70, -0.65),
    CityArea("Vila-real", 39.92, 39.95, -0.12, -0.08),

    // ÁREA METROPOLITANA DE ASTURIAS (ZONA CENTRAL)
    CityArea("Avilés", 43.54, 43.57, -5.94, -5.90),
    CityArea("Langreo", 43.29, 43.32, -5.71, -5.67),
    CityArea("Siero", 43.38, 43.41, -5.68, -5.63),

    // ÁREA METROPOLITANA DE GRANADA
    CityArea("Albolote", 37.22, 37.24, -3.67, -3.65),
    CityArea("Armilla", 37.14, 37.16, -3.63, -3.61),
    CityArea("Las Gabias", 37.13, 37.15, -3.70, -3.68),
    CityArea("La Zubia", 37.12, 37.14, -3.60, -3.58),
    CityArea("Maracena", 37.20, 37.22, -3.64, -3.62),

    // ZONAS TURÍSTICAS (ISLAS)
    CityArea("Adeje", 28.09, 28.13, -16.75, -16.71),
    CityArea("Arona", 28.05, 28.11, -16.70, -16.66),
    CityArea("Calvià", 39.52, 39.58, 2.48, 2.54),
    CityArea("Ibiza", 38.90, 38.92, 1.41, 1.44),
    CityArea("Las Palmas de Gran Canaria", 28.10, 28.20, -15.50, -15.40),
    CityArea("San Bartolomé de Tirajana", 27.85, 27.95, -15.60, -15.54),
    CityArea("Santa Cruz de Tenerife", 28.45, 28.50, -16.30, -16.20),

    // CIUDADES IMPORTANTES DE EUROPA
    CityArea("Ámsterdam", 52.32, 52.42, 4.80, 5.00),
    CityArea("Atenas", 37.93, 38.03, 23.67, 23.87),
    CityArea("Berlín", 52.45, 52.55, 13.30, 13.50),
    CityArea("Bruselas", 50.80, 50.90, 4.30, 4.50),
    CityArea("Dublín", 53.30, 53.40, -6.35, -6.15),
    CityArea("Estocolmo", 59.28, 59.38, 17.95, 18.15),
    CityArea("Lisboa", 38.69, 38.79, -9.22, -9.02),
    CityArea("Londres", 51.45, 51.55, -0.20, 0.00),
    CityArea("París", 48.80, 48.90, 2.25, 2.45),
    CityArea("Praga", 50.03, 50.13, 14.35, 14.55),
    CityArea("Roma", 41.85, 41.95, 12.40, 12.60),
    CityArea("Viena", 48.15, 48.25, 16.30, 16.50),

    // CIUDADES IMPORTANTES DE AMÉRICA
    CityArea("Bogotá", 4.55, 4.75, -74.20, -74.00),
    CityArea("Buenos Aires", -34.65, -34.55, -58.50, -58.30),
    CityArea("Chicago", 41.80, 42.00, -87.80, -87.60),
    CityArea("Ciudad de México", 19.35, 19.55, -99.25, -99.05),
    CityArea("Lima", -12.15, -11.95, -77.15, -76.95),
    CityArea("Los Ángeles", 33.95, 34.15, -118.40, -118.20),
    CityArea("Nueva York", 40.65, 40.85, -74.10, -73.90),
    CityArea("Santiago de Chile", -33.50, -33.30, -70.75, -70.55),
    CityArea("São Paulo", -23.60, -23.50, -46.75, -46.55),
    CityArea("Toronto", 43.60, 43.80, -79.50, -79.30),

    // CIUDADES IMPORTANTES DE ASIA
    CityArea("Bangkok", 13.65, 13.85, 100.40, 100.60),
    CityArea("Dubái", 25.05, 25.25, 55.10, 55.40),
    CityArea("Estambul", 40.95, 41.15, 28.80, 29.20),
    CityArea("Hong Kong", 22.20, 22.40, 114.00, 114.30),
    CityArea("Mumbai", 18.90, 19.20, 72.75, 73.00),
    CityArea("Pekín", 39.80, 40.00, 116.20, 116.60),
    CityArea("Seúl", 37.45, 37.65, 126.80, 127.20),
    CityArea("Shanghái", 31.00, 31.40, 121.20, 121.80),
    CityArea("Singapur", 1.25, 1.45, 103.75, 104.00),
    CityArea("Tokio", 35.60, 35.80, 139.60, 139.90)
)
private fun getCityName(latitude: Double?, longitude: Double?): String? {
    if (latitude == null || longitude == null) return null
    val matchingCities = cityAreas.filter { city ->
        latitude >= city.latMin && latitude <= city.latMax &&
                longitude >= city.lonMin && longitude <= city.lonMax
    }
    if (matchingCities.isEmpty()) return null
    return matchingCities.minByOrNull { city ->
        (city.latMax - city.latMin) * (city.lonMax - city.lonMin)
    }?.name
}