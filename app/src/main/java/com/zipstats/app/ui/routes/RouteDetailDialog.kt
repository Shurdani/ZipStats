package com.zipstats.app.ui.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.zipstats.app.model.Route
import com.zipstats.app.repository.WeatherRepository
import com.zipstats.app.ui.components.BasicMapView
import com.zipstats.app.ui.components.CapturableMapView
import com.zipstats.app.utils.DateUtils
import com.zipstats.app.R
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun RouteDetailDialog(
    route: Route,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onAddToRecords: (() -> Unit)? = null,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    var googleMapRef by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    var showAdvancedDetails by remember { mutableStateOf(false) }
    var showFullscreenMap by remember { mutableStateOf(false) }
    var vehicleIconRes by remember { mutableStateOf(R.drawable.ic_electric_scooter_adaptive) }
    var vehicleModel by remember { mutableStateOf(route.scooterName) }
    
    // Obtener el icono y modelo del vehículo
    LaunchedEffect(route.scooterId) {
        try {
            val vehicle = getVehicleById(route.scooterId)
            vehicleIconRes = getVehicleIconResource(vehicle?.vehicleType)
            
            // Obtener el modelo del vehículo
            if (vehicle != null && vehicle.modelo.isNotBlank()) {
                vehicleModel = vehicle.modelo
                android.util.Log.d("RouteDialog", "Usando modelo: '${vehicle.modelo}'")
            } else {
                vehicleModel = route.scooterName
                android.util.Log.d("RouteDialog", "Usando nombre: '${route.scooterName}'")
            }
        } catch (e: Exception) {
            vehicleIconRes = R.drawable.ic_electric_scooter_adaptive // Fallback en caso de error
            vehicleModel = route.scooterName
            android.util.Log.e("RouteDialog", "Error obteniendo vehículo: ${e.message}", e)
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                    .padding(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                        .padding(12.dp)
            ) {
                    // Header minimalista con botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón de añadir a registros (si está disponible)
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
                        // Botón de cerrar
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
                        // Encabezado compacto: Icono + Vehículo + Fecha + Hora
                        CompactHeader(route = route, vehicleIconRes = vehicleIconRes, vehicleName = vehicleModel)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Título de la ruta (notas o automático)
                        RouteTitle(route = route)
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Mapa compacto (formato panorámico) con botón de expandir
                        Box(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                                    .clickable { showFullscreenMap = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                    ) {
                        CapturableMapView(
                            route = route,
                            onMapReady = { googleMap ->
                                googleMapRef = googleMap
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                        .height(280.dp)
                                )
                            }
                            
                            // Icono de pantalla completa
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
                        
                        // Estadísticas en chips: Distancia, Duración, Clima
                        StatsChips(route = route)
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Detalles avanzados colapsables
                    if (route.movingTime > 0 || route.pauseCount > 0) {
                            CollapsibleAdvancedDetails(
                                route = route,
                                expanded = showAdvancedDetails,
                                onToggle = { showAdvancedDetails = !showAdvancedDetails }
                            )
                        }
                        
                        // Espaciado para los botones flotantes
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
            
            // Botones flotantes en esquina inferior derecha
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Botón de eliminar
                SmallFloatingActionButton(
                    onClick = onDelete,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar"
                        )
                    }
                    
                    // Botón de compartir
                FloatingActionButton(
                        onClick = {
                            if (googleMapRef != null) {
                                shareRouteWithRealMap(route, googleMapRef!!, context)
                            } else {
                                onShare()
                            }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartir"
                    )
                }
            }
        }
    }
    
    // Modal de mapa en pantalla completa
    if (showFullscreenMap) {
        FullscreenMapDialog(
            route = route,
            onDismiss = { showFullscreenMap = false }
        )
    }
}

/**
 * Encabezado compacto con icono vehículo, nombre, fecha y hora
 */
@Composable
private fun CompactHeader(route: Route, vehicleIconRes: Int, vehicleName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono del vehículo
        Icon(
            painter = painterResource(id = vehicleIconRes),
            contentDescription = "Icono del vehículo",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(28.dp)
                .padding(end = 6.dp)
        )
        
        // Nombre del vehículo (modelo preferentemente)
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
        
        // Fecha
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

/**
 * Título de la ruta (notas del usuario o título automático)
 */
@Composable
private fun RouteTitle(route: Route) {
    val title = if (route.notes.isNotBlank()) {
        route.notes
    } else {
        // Generar título automático basado en puntos
        if (route.points.isNotEmpty()) {
            val startCity = getCityName(
                route.points.firstOrNull()?.latitude, 
                route.points.firstOrNull()?.longitude
            )
            val endCity = getCityName(
                route.points.lastOrNull()?.latitude, 
                route.points.lastOrNull()?.longitude
            )
            
            when {
                startCity != null && endCity != null && startCity != endCity -> 
                    "$startCity → $endCity"
                startCity != null -> 
                    "Ruta por $startCity"
                else -> 
                    "Mi ruta"
            }
        } else {
            "Mi ruta"
        }
    }
    
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

/**
 * Estadísticas en chips: Distancia, Duración y Clima
 */
@Composable
private fun StatsChips(route: Route) {
    // Usar el clima guardado si existe, sino valores por defecto
    var weatherEmoji by remember { mutableStateOf(route.weatherEmoji ?: "☁️") }
    var weatherTemp by remember { mutableStateOf(
        if (route.weatherTemperature != null) {
            String.format("%.0f°C", route.weatherTemperature)
        } else {
            "--°C"
        }
    ) }
    var isLoadingWeather by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Solo cargar clima si NO está guardado (para rutas antiguas)
    LaunchedEffect(route.id) {
        if (route.weatherTemperature == null && route.points.isNotEmpty()) {
            isLoadingWeather = true
            android.util.Log.d("StatsChips", "Clima no guardado, obteniendo clima actual para ruta ${route.id}")
            
            try {
                val weatherRepository = WeatherRepository()
                val firstPoint = route.points.first()
                
                android.util.Log.d("StatsChips", "Obteniendo clima para lat=${firstPoint.latitude}, lon=${firstPoint.longitude}")
                
                val result = weatherRepository.getCurrentWeather(
                    latitude = firstPoint.latitude,
                    longitude = firstPoint.longitude
                )
                
                result.onSuccess { weather ->
                    android.util.Log.d("StatsChips", "Clima obtenido: ${weather.temperature}°C, emoji=${weather.weatherEmoji}")
                    weatherEmoji = weather.weatherEmoji
                    weatherTemp = String.format("%.0f°C", weather.temperature)
                }.onFailure { error ->
                    // Mantener valores por defecto en caso de error
                    android.util.Log.e("StatsChips", "Error obteniendo clima: ${error.message}", error)
                }
            } catch (e: Exception) {
                android.util.Log.e("StatsChips", "Excepción al cargar clima: ${e.message}", e)
            } finally {
                isLoadingWeather = false
                android.util.Log.d("StatsChips", "Carga de clima finalizada")
            }
        } else if (route.weatherTemperature != null) {
            android.util.Log.d("StatsChips", "Usando clima guardado: ${route.weatherTemperature}°C, ${route.weatherEmoji}")
        }
    }
    
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
        // Distancia
        StatChip(
            value = String.format("%.1f km", route.totalDistance),
                    label = "Distancia",
            icon = null
                )
        
        // Duración con unidades
        StatChip(
            value = formatDurationWithUnits(route.totalDuration),
                    label = "Duración",
            icon = null
        )
        
        // Clima
        StatChip(
            value = if (isLoadingWeather) "..." else weatherTemp,
            label = "Clima",
            icon = weatherEmoji
        )
    }
}

/**
 * Chip individual de estadística con icono opcional
 */
@Composable
private fun StatChip(value: String, label: String, icon: String? = null) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Text(
                        text = icon,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Sección colapsable de detalles avanzados
 */
@Composable
private fun CollapsibleAdvancedDetails(
    route: Route,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Botón de expansión
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = "Ver más detalles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Contraer" else "Expandir"
                )
            }
            
            // Contenido expandible
            if (expanded) {
                HorizontalDivider()
                
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Velocidad Real (destacada)
                    AdvancedStatRow(
                        label = "Velocidad Real",
                        value = String.format("%.1f km/h", route.averageMovingSpeed),
                        highlight = true
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Velocidad Máxima
                    AdvancedStatRow(
                        label = "Velocidad Máxima",
                        value = String.format("%.1f km/h", route.maxSpeed),
                        highlight = false
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Velocidad Media (movida desde chips)
                    AdvancedStatRow(
                        label = "Velocidad Media",
                        value = String.format("%.1f km/h", route.averageSpeed),
                        highlight = false
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Tiempo en movimiento con porcentaje
                    AdvancedStatRow(
                        label = "Tiempo en Movimiento (${String.format("%.0f%%", route.movingPercentage)})",
                        value = formatDuration(route.movingTime),
                        highlight = false
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Hora de inicio
                    AdvancedStatRow(
                        label = "Hora de Inicio",
                        value = formatTime(route.startTime),
                        highlight = false
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Hora de fin
                    AdvancedStatRow(
                        label = "Hora de Fin",
                        value = if (route.endTime != null) formatTime(route.endTime) else "--:--",
                        highlight = false
                    )
                }
            }
        }
    }
}

/**
 * Fila de estadística avanzada
 */
@Composable
private fun AdvancedStatRow(label: String, value: String, highlight: Boolean) {
            Row(
                modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}


/**
 * Modal de mapa en pantalla completa
 */
@Composable
private fun FullscreenMapDialog(
    route: Route,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Mapa en pantalla completa usando CapturableMapView (misma polyline y marcadores)
            CapturableMapView(
                route = route,
                modifier = Modifier.fillMaxSize()
            )
            
            // Botón de cerrar flotante
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 8.dp
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            // Información compacta en la parte inferior
                Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = route.scooterName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format("%.1f km", route.totalDistance),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Distancia",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                text = route.durationFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Duración",
                                style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                text = String.format("%.1f km/h", route.averageMovingSpeed),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Velocidad Real",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

/**
 * Formatea un timestamp a hora (HH:mm)
 */
private fun formatTime(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return formatter.format(date)
}

/**
 * Obtiene el recurso del icono del vehículo según su tipo
 */
private fun getVehicleIconResource(vehicleType: com.zipstats.app.model.VehicleType?): Int {
    return when (vehicleType) {
        com.zipstats.app.model.VehicleType.PATINETE -> R.drawable.ic_electric_scooter_adaptive
        com.zipstats.app.model.VehicleType.BICICLETA -> R.drawable.ic_ciclismo_adaptive
        com.zipstats.app.model.VehicleType.E_BIKE -> R.drawable.ic_bicicleta_electrica_adaptive
        com.zipstats.app.model.VehicleType.MONOCICLO -> R.drawable.ic_unicycle_adaptive
        null -> R.drawable.ic_electric_scooter_adaptive // Fallback
    }
}

/**
 * Comparte una ruta usando el mapa real capturado
 */
private fun shareRouteWithRealMap(
    route: Route,
    googleMap: com.google.android.gms.maps.GoogleMap,
    context: android.content.Context
) {
    try {
        // Mostrar indicador de carga
        android.widget.Toast.makeText(context, "Generando imagen...", android.widget.Toast.LENGTH_SHORT).show()
        
        // Tomar snapshot del mapa real
        googleMap.snapshot(com.google.android.gms.maps.GoogleMap.SnapshotReadyCallback { snapshotBitmap ->
            if (snapshotBitmap == null) {
                android.widget.Toast.makeText(context, "Error al capturar el mapa", android.widget.Toast.LENGTH_SHORT).show()
                return@SnapshotReadyCallback
            }

            // Usar corrutina para manejar la función asíncrona
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // Redimensionar el bitmap para evitar problemas de memoria
                    val resizedBitmap = redimensionarBitmap(snapshotBitmap, 1080)
                    
                    // Crear imagen final con estadísticas
                    val finalBitmap = createFinalRouteImage(route, resizedBitmap, context)
                    
                    // Compartir la imagen
                    shareBitmap(context, finalBitmap, route)
                    
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Error al procesar imagen: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        })
        
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error al compartir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}

/**
 * Redimensiona un Bitmap para evitar problemas de memoria
 */
private fun redimensionarBitmap(bitmapOriginal: android.graphics.Bitmap, tamanoMaximo: Int): android.graphics.Bitmap {
    val anchoOriginal = bitmapOriginal.width
    val altoOriginal = bitmapOriginal.height

    if (anchoOriginal <= tamanoMaximo && altoOriginal <= tamanoMaximo) {
        return bitmapOriginal
    }

    val ratio = minOf(
        tamanoMaximo.toFloat() / anchoOriginal,
        tamanoMaximo.toFloat() / altoOriginal
    )

    val nuevoAncho = (anchoOriginal * ratio).toInt()
    val nuevoAlto = (altoOriginal * ratio).toInt()

    return android.graphics.Bitmap.createScaledBitmap(bitmapOriginal, nuevoAncho, nuevoAlto, true)
}

/**
 * Crea la imagen final combinando el mapa con las estadísticas
 */
private suspend fun createFinalRouteImage(route: Route, mapBitmap: android.graphics.Bitmap, context: android.content.Context): android.graphics.Bitmap {
    val width = 1080
    val height = 1920
    val mapHeight = (height * 0.85f).toInt() // 85% para el mapa

    val finalBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(finalBitmap)
    
    // Fondo limpio
    canvas.drawColor(android.graphics.Color.rgb(245, 245, 245))
    
    // Dibujar el mapa escalado
    val mapWidth = mapBitmap.width
    val mapHeightActual = mapBitmap.height
    val scaleX = width.toFloat() / mapWidth
    val scaleY = mapHeight.toFloat() / mapHeightActual
    val scale = kotlin.math.min(scaleX, scaleY)
    
    val scaledWidth = (mapWidth * scale).toInt()
    val scaledHeight = (mapHeightActual * scale).toInt()
    val offsetX = (width - scaledWidth) / 2
    val offsetY = (mapHeight - scaledHeight) / 2
    
    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(mapBitmap, scaledWidth, scaledHeight, true)
    canvas.drawBitmap(scaledBitmap, offsetX.toFloat(), offsetY.toFloat(), null)
    
    // Inflar el layout de la tarjeta flotante
    val inflater = android.view.LayoutInflater.from(context)
    val cardView = inflater.inflate(R.layout.share_route_stats_card, null) as androidx.cardview.widget.CardView
    
    // Configurar título de la ruta (igual que en RouteTitle)
    // Para la imagen compartida usamos el nombre completo del vehículo (hay espacio suficiente)
    val vehicleModel = route.scooterName
    
    val routeTitle = if (route.notes.isNotBlank()) {
        route.notes
    } else {
        // Generar título automático basado en las ciudades (misma lógica que RouteTitle)
        if (route.points.isNotEmpty()) {
        val startCity = getCityName(route.points.firstOrNull()?.latitude, route.points.firstOrNull()?.longitude)
        val endCity = getCityName(route.points.lastOrNull()?.latitude, route.points.lastOrNull()?.longitude)
        
        when {
            startCity != null && endCity != null && startCity != endCity -> 
                    "$startCity → $endCity"
            startCity != null -> 
                    "Ruta por $startCity"
            else -> 
                    "Mi ruta en $vehicleModel"
            }
        } else {
            "Mi ruta en $vehicleModel"
        }
    }
    cardView.findViewById<android.widget.TextView>(R.id.routeTitle).text = routeTitle
    
    
    // Configurar métricas con formato mejorado
    // 1. Distancia
    cardView.findViewById<android.widget.TextView>(R.id.distanceValue).text = 
        String.format("%.1f km", route.totalDistance)
    
    // 2. Duración total (no tiempo en movimiento)
    cardView.findViewById<android.widget.TextView>(R.id.timeValue).text = 
        formatDurationWithUnits(route.totalDuration)
    
    // 3. Velocidad real (en movimiento)
    cardView.findViewById<android.widget.TextView>(R.id.speedValue).text = 
        String.format("%.1f km/h", route.averageMovingSpeed)
    
    // 4. Clima (si está disponible)
    if (route.weatherEmoji != null && route.weatherTemperature != null) {
        cardView.findViewById<android.widget.TextView>(R.id.weatherEmoji).text = route.weatherEmoji
        cardView.findViewById<android.widget.TextView>(R.id.weatherTemp).text = 
            String.format("%.0f°C", route.weatherTemperature)
        cardView.findViewById<android.widget.LinearLayout>(R.id.weatherContainer).visibility = android.view.View.VISIBLE
    } else {
        // Ocultar el contenedor de clima si no hay datos
        cardView.findViewById<android.widget.LinearLayout>(R.id.weatherContainer).visibility = android.view.View.GONE
    }
    
    // Configurar icono del vehículo según el tipo
    val vehicleIconRes = getVehicleIconResource(route.scooterId)
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
    
    
    // Medir y renderizar la tarjeta
    val cardWidth = width - 64 // Márgenes de 32dp a cada lado
    val measureSpec = android.view.View.MeasureSpec.makeMeasureSpec(cardWidth, android.view.View.MeasureSpec.EXACTLY)
    cardView.measure(measureSpec, android.view.View.MeasureSpec.UNSPECIFIED)
    
    val cardHeight = cardView.measuredHeight
    val cardX = 32
    val cardY = height - cardHeight - 32 // Anclar al borde inferior con margen de 32dp
    
    cardView.layout(0, 0, cardView.measuredWidth, cardHeight)
    
    // Dibujar la tarjeta en el canvas
    canvas.save()
    canvas.translate(cardX.toFloat(), cardY.toFloat())
    cardView.draw(canvas)
    canvas.restore()
    
    // Watermark eliminado - el branding ya está integrado en la tarjeta
    
    return finalBitmap
}

/**
 * Obtiene el nombre de la ciudad basado en coordenadas GPS
 * Por simplicidad, devuelve nombres de ciudades españolas conocidas
 */
private fun getCityName(latitude: Double?, longitude: Double?): String? {
    if (latitude == null || longitude == null) return null
    
    // Mapeo básico de coordenadas a ciudades españolas
    // Barcelona
    if (latitude in 41.35..41.45 && longitude in 2.10..2.25) return "Barcelona"
    // Madrid
    if (latitude in 40.35..40.50 && longitude in -3.80..-3.60) return "Madrid"
    // Valencia
    if (latitude in 39.40..39.50 && longitude in -0.40..-0.30) return "Valencia"
    // Sevilla
    if (latitude in 37.30..37.45 && longitude in -6.05..-5.85) return "Sevilla"
    // Bilbao
    if (latitude in 43.20..43.30 && longitude in -3.00..-2.90) return "Bilbao"
    // Zaragoza
    if (latitude in 41.60..41.70 && longitude in -0.95..-0.85) return "Zaragoza"
    // Málaga
    if (latitude in 36.60..36.80 && longitude in -4.50..-4.30) return "Málaga"
    // Murcia
    if (latitude in 37.90..38.00 && longitude in -1.20..-1.10) return "Murcia"
    // Palma
    if (latitude in 39.50..39.60 && longitude in 2.60..2.70) return "Palma"
    // Las Palmas
    if (latitude in 28.10..28.20 && longitude in -15.50..-15.40) return "Las Palmas"
    
    return null
}

/**
 * Obtiene el nombre del tipo de vehículo basado en el ID del vehículo
 */
private suspend fun getVehicleTypeName(scooterId: String): String {
    return try {
        val vehicle = getVehicleById(scooterId)
        when (vehicle?.vehicleType) {
            com.zipstats.app.model.VehicleType.PATINETE -> "paseo en patinete"
            com.zipstats.app.model.VehicleType.BICICLETA -> "paseo en bicicleta"
            com.zipstats.app.model.VehicleType.E_BIKE -> "paseo en e-bike"
            com.zipstats.app.model.VehicleType.MONOCICLO -> "paseo en monociclo"
            else -> "paseo"
        }
    } catch (e: Exception) {
        "paseo"
    }
}

/**
 * Obtiene el recurso del icono del vehículo basado en el ID del vehículo
 */
private suspend fun getVehicleIconResource(scooterId: String): Int {
    return try {
        val vehicle = getVehicleById(scooterId)
        when (vehicle?.vehicleType) {
            com.zipstats.app.model.VehicleType.PATINETE -> com.zipstats.app.R.drawable.electric_scooter
            com.zipstats.app.model.VehicleType.BICICLETA -> com.zipstats.app.R.drawable.ciclismo
            com.zipstats.app.model.VehicleType.E_BIKE -> com.zipstats.app.R.drawable.bicicleta_electrica
            com.zipstats.app.model.VehicleType.MONOCICLO -> com.zipstats.app.R.drawable.unicycle
            else -> com.zipstats.app.R.drawable.electric_scooter
        }
    } catch (e: Exception) {
        com.zipstats.app.R.drawable.electric_scooter
    }
}

/**
 * Obtiene un vehículo por su ID
 */
private suspend fun getVehicleById(vehicleId: String): com.zipstats.app.model.Vehicle? {
    return try {
        // Obtener el repositorio de vehículos
        val vehicleRepository = com.zipstats.app.repository.VehicleRepository(
            com.google.firebase.firestore.FirebaseFirestore.getInstance(),
            com.google.firebase.auth.FirebaseAuth.getInstance()
        )
        
        // Buscar el vehículo en la lista de vehículos del usuario
        val vehicles = vehicleRepository.getUserVehicles()
        vehicles.find { it.id == vehicleId }
    } catch (e: Exception) {
        android.util.Log.e("RouteDialog", "Error obteniendo vehículo: ${e.message}", e)
        null
    }
}

/**
 * Dibuja la información de la ruta en la parte inferior
 */
@Suppress("UNUSED_PARAMETER")
private fun drawRouteInfo(canvas: android.graphics.Canvas, route: Route, width: Int, mapHeight: Int, infoHeight: Int) {
    val titlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(33, 33, 33)
        textSize = 72f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val subtitlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(100, 100, 100)
        textSize = 42f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val statPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(33, 150, 243)
        textSize = 64f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(120, 120, 120)
        textSize = 36f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    // Calcular posición inicial basada en el área de información
    val startY = mapHeight + (infoHeight * 0.15f)
    var yPos = startY
    
    // Título del patinete
    canvas.drawText("🛴 ${route.scooterName}", width / 2f, yPos, titlePaint)
    yPos += 60f
    
    // Fecha
    val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    val date = dateFormat.format(java.util.Date(route.startTime))
    canvas.drawText(date, width / 2f, yPos, subtitlePaint)
    yPos += 80f

    // Estadísticas principales
    val col1X = width * 0.25f
    val col2X = width * 0.5f
    val col3X = width * 0.75f
    canvas.drawText(String.format("%.2f", route.totalDistance), col1X, yPos, statPaint)
    canvas.drawText("km", col1X, yPos + 50f, labelPaint)
    canvas.drawText(formatDurationShort(route.totalDuration), col2X, yPos, statPaint)
    canvas.drawText("tiempo", col2X, yPos + 50f, labelPaint)
    canvas.drawText(String.format("%.1f", route.averageSpeed), col3X, yPos, statPaint)
    canvas.drawText("km/h media", col3X, yPos + 50f, labelPaint)

    // Watermark centrado en la parte inferior
    yPos = mapHeight + infoHeight - 40f
    val watermarkPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(180, 180, 180)
        textSize = 32f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("ZipStats", width / 2f, yPos, watermarkPaint)
}

/**
 * Formatea la duración en formato corto
 */
private fun formatDurationShort(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> String.format("%d:%02d", hours, minutes % 60)
        else -> String.format("%d min", minutes)
    }
}

/**
 * Formatea la duración en formato legible con unidades (para desplegable)
 */
private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    val s = seconds % 60
    val m = minutes % 60
    val h = hours
    
    return when {
        h > 0 -> String.format("%d h %d min %d s", h, m, s)
        m > 0 -> String.format("%d min %d s", m, s)
        else -> String.format("%d s", s)
    }
}

/**
 * Formatea la duración en formato compacto con unidades (para chips)
 */
private fun formatDurationWithUnits(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    val s = seconds % 60
    val m = minutes % 60
    val h = hours
    
    return when {
        h > 0 -> String.format("%d h %d min", h, m)
        m > 0 -> String.format("%d min", m)
        else -> String.format("%d s", s)
    }
}

/**
 * Comparte un Bitmap usando el sistema de compartir de Android
 */
private fun shareBitmap(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    route: Route
) {
    try {
        // Crear archivo temporal
        val cacheDir = context.cacheDir
        val shareDir = java.io.File(cacheDir, "shared_routes")
        if (!shareDir.exists()) {
            shareDir.mkdirs()
        }
        
        val imageFile = java.io.File(shareDir, "route_${route.id}.png")
        
        // Guardar bitmap como PNG
        java.io.FileOutputStream(imageFile).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        
        // Crear URI con FileProvider
        val imageUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )
        
        // Crear mensaje para compartir
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        val date = dateFormat.format(java.util.Date(route.startTime))
        
        // Para el mensaje compartido usamos el nombre completo del vehículo
        val vehicleModel = route.scooterName
        
        // Usar el mismo título que en la imagen
        val messageTitle = if (route.notes.isNotBlank()) {
            route.notes
        } else {
            if (route.points.isNotEmpty()) {
                val startCity = getCityName(route.points.firstOrNull()?.latitude, route.points.firstOrNull()?.longitude)
                val endCity = getCityName(route.points.lastOrNull()?.latitude, route.points.lastOrNull()?.longitude)
                
                when {
                    startCity != null && endCity != null && startCity != endCity -> 
                        "$startCity → $endCity"
                    startCity != null -> 
                        "Ruta por $startCity"
                    else -> 
                        "Mi ruta en $vehicleModel"
                }
            } else {
                "Mi ruta en $vehicleModel"
            }
        }
        
        val shareMessage = """
            🛴 $messageTitle
            
            📅 Fecha: $date
            📍 Distancia: ${String.format("%.1f", route.totalDistance)} km
            ⏱️ Duración: ${formatDurationWithUnits(route.totalDuration)}
            ⚡ Velocidad: ${String.format("%.1f", route.averageMovingSpeed)} km/h
            🚀 Velocidad máxima: ${String.format("%.1f", route.maxSpeed)} km/h
            
            #ZipStats
        """.trimIndent()
        
        // Crear intent para compartir
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, imageUri)
            putExtra(android.content.Intent.EXTRA_TEXT, shareMessage)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(
                context.contentResolver,
                "shared_route",
                imageUri
            )
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Conceder permisos de lectura a todas las apps objetivo
        val resInfoList = context.packageManager.queryIntentActivities(intent, 0)
        if (resInfoList.isEmpty()) {
            android.widget.Toast.makeText(context, "No hay apps para compartir la imagen", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            context.grantUriPermission(
                packageName,
                imageUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        // Abrir chooser
        val chooser = android.content.Intent.createChooser(intent, "Compartir ruta").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error al compartir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}


/**
 * Dibuja un marco negro con esquinas redondeadas alrededor del mapa
 */
private fun drawMapFrame(
    canvas: android.graphics.Canvas, 
    width: Int, 
    mapHeight: Int, 
    offsetX: Int, 
    offsetY: Int, 
    scaledWidth: Int, 
    scaledHeight: Int
) {
    val framePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 8f
        style = android.graphics.Paint.Style.STROKE
        isAntiAlias = true
    }
    
    val cornerRadius = 32f // Radio de las esquinas redondeadas
    val frameRect = android.graphics.RectF(
        offsetX.toFloat() - 4f,
        offsetY.toFloat() - 4f,
        (offsetX + scaledWidth).toFloat() + 4f,
        (offsetY + scaledHeight).toFloat() + 4f
    )
    
    canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, framePaint)
}



