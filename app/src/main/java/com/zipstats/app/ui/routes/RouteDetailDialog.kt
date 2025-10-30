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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.zipstats.app.R
import com.zipstats.app.model.Route
import com.zipstats.app.repository.WeatherRepository
import com.zipstats.app.ui.components.CapturableMapView
import com.zipstats.app.utils.DateUtils
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
    var fullscreenMapRef by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    var showAdvancedDetails by remember { mutableStateOf(false) }
    var showFullscreenMap by remember { mutableStateOf(false) }
    var isCapturingForShare by remember { mutableStateOf(false) }
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
                            // Abrir mapa fullscreen para captura
                            isCapturingForShare = true
                            showFullscreenMap = true
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
            onDismiss = { 
                showFullscreenMap = false
                isCapturingForShare = false
            },
            onMapReady = { googleMap ->
                fullscreenMapRef = googleMap
                // Si estamos capturando para compartir, hacerlo automáticamente
                if (isCapturingForShare && googleMap != null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        // Llamar a compartir pero con callback para cerrar después
                        shareRouteWithRealMapAndClose(
                            route = route,
                            googleMap = googleMap,
                            context = context,
                            onComplete = {
                                showFullscreenMap = false
                                isCapturingForShare = false
                            }
                        )
                    }, 500) // Esperar 500ms para que el mapa se renderice completamente
                }
            }
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
    // IMPORTANTE: usar remember(route.id) para reinicializar el estado cuando cambia la ruta
    var weatherEmoji by remember(route.id) { 
        mutableStateOf(route.weatherEmoji ?: "☁️") 
    }
    var weatherTemp by remember(route.id) { 
        mutableStateOf(
            if (route.weatherTemperature != null) {
                String.format("%.0f°C", route.weatherTemperature)
            } else {
                "--°C"
            }
        )
    }
    var isLoadingWeather by remember(route.id) { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Actualizar valores cuando cambia la ruta y tiene clima guardado
    // Solo cargar clima si NO está guardado (para rutas antiguas)
    LaunchedEffect(route.id, route.weatherTemperature, route.weatherEmoji) {
        // Primero, siempre inicializar con los valores guardados de la ruta actual
        if (route.weatherTemperature != null) {
            weatherEmoji = route.weatherEmoji ?: "☁️"
            weatherTemp = String.format("%.0f°C", route.weatherTemperature)
            isLoadingWeather = false
            android.util.Log.d("StatsChips", "Usando clima guardado para ruta ${route.id}: ${route.weatherTemperature}°C, ${route.weatherEmoji}")
            return@LaunchedEffect
        }
        
        // Si no hay clima guardado, intentar obtenerlo (solo para rutas antiguas)
        if (route.points.isNotEmpty()) {
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
                    android.util.Log.d("StatsChips", "Clima obtenido para ruta ${route.id}: ${weather.temperature}°C, emoji=${weather.weatherEmoji}")
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
    onDismiss: () -> Unit,
    onMapReady: ((com.google.android.gms.maps.GoogleMap?) -> Unit)? = null
) {
    val context = LocalContext.current
    var vehicleIconRes by remember { mutableStateOf(R.drawable.ic_electric_scooter_adaptive) }
    
    // Obtener el icono del vehículo
    LaunchedEffect(route.scooterId) {
        try {
            val vehicle = getVehicleById(route.scooterId)
            vehicleIconRes = getVehicleIconResource(vehicle?.vehicleType)
        } catch (e: Exception) {
            vehicleIconRes = R.drawable.ic_electric_scooter_adaptive
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        // Box principal que capturaremos
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Mapa en pantalla completa usando CapturableMapView (misma polyline y marcadores)
            CapturableMapView(
                route = route,
                modifier = Modifier.fillMaxSize(),
                onMapReady = onMapReady
            )
            
            // Botón de cerrar flotante
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .zIndex(10f),
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
            
            // Usar la misma tarjeta que se usa para compartir (usando AndroidView para inflar el layout XML)
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .fillMaxWidth(),
                factory = { ctx ->
                    val inflater = android.view.LayoutInflater.from(ctx)
                    val cardView = inflater.inflate(R.layout.share_route_stats_card, null) as androidx.cardview.widget.CardView
                    
                    // Configurar título de la ruta
                    val routeTitle = if (route.notes.isNotBlank()) {
                        route.notes
                    } else {
                        if (route.points.isNotEmpty()) {
                            val startCity = getCityName(route.points.firstOrNull()?.latitude, route.points.firstOrNull()?.longitude)
                            val endCity = getCityName(route.points.lastOrNull()?.latitude, route.points.lastOrNull()?.longitude)
                            
                            when {
                                startCity != null && endCity != null && startCity != endCity -> "$startCity → $endCity"
                                startCity != null -> "Ruta por $startCity"
                                else -> "Mi ruta"
                            }
                        } else {
                            "Mi ruta"
                        }
                    }
                    cardView.findViewById<android.widget.TextView>(R.id.routeTitle).text = routeTitle
                    
                    // Configurar métricas
                    cardView.findViewById<android.widget.TextView>(R.id.distanceValue).text = 
                        String.format("%.1f km", route.totalDistance)
                    cardView.findViewById<android.widget.TextView>(R.id.timeValue).text = 
                        formatDurationWithUnits(route.totalDuration)
                    cardView.findViewById<android.widget.TextView>(R.id.speedValue).text = 
                        String.format("%.1f km/h", route.averageMovingSpeed)
                    
                    // Configurar clima si está disponible
                    if (route.weatherEmoji != null && route.weatherTemperature != null) {
                        cardView.findViewById<android.widget.TextView>(R.id.weatherEmoji).text = route.weatherEmoji
                        cardView.findViewById<android.widget.TextView>(R.id.weatherTemp).text = 
                            String.format("%.0f°C", route.weatherTemperature)
                        cardView.findViewById<android.widget.LinearLayout>(R.id.weatherContainer).visibility = android.view.View.VISIBLE
                    } else {
                        cardView.findViewById<android.widget.LinearLayout>(R.id.weatherContainer).visibility = android.view.View.GONE
                    }
                    
                    // Configurar icono del vehículo
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
                    
                    // Eliminar el logo/icono de ZipStats (dejar solo el texto)
                    cardView.findViewById<android.widget.TextView>(R.id.zipstatsBranding).setCompoundDrawables(null, null, null, null)
                    
                    cardView
                }
            )
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
 * Comparte una ruta usando el mapa capturado y cierra el dialog al completar
 */
private fun shareRouteWithRealMapAndClose(
    route: Route,
    googleMap: com.google.android.gms.maps.GoogleMap,
    context: android.content.Context,
    onComplete: () -> Unit
) {
    try {
        android.util.Log.d("RouteDetailDialog", "=== INICIO COMPARTIR ===")
        // Mostrar indicador de carga
        android.widget.Toast.makeText(context, "Generando imagen...", android.widget.Toast.LENGTH_SHORT).show()
        
        // Tomar snapshot del mapa fullscreen
        android.util.Log.d("RouteDetailDialog", "Capturando snapshot del mapa...")
        googleMap.snapshot(com.google.android.gms.maps.GoogleMap.SnapshotReadyCallback { snapshotBitmap ->
            android.util.Log.d("RouteDetailDialog", "Snapshot capturado: ${snapshotBitmap != null}, width=${snapshotBitmap?.width}, height=${snapshotBitmap?.height}")
            
            if (snapshotBitmap == null) {
                android.widget.Toast.makeText(context, "Error al capturar el mapa", android.widget.Toast.LENGTH_SHORT).show()
                onComplete()
                return@SnapshotReadyCallback
            }

            // Usar corrutina para combinar mapa + tarjeta
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    android.util.Log.d("RouteDetailDialog", "Creando imagen final...")
                    // Crear la imagen final combinando mapa + tarjeta
                    val finalBitmap = createFinalRouteImageFromFullscreen(route, snapshotBitmap, context)
                    android.util.Log.d("RouteDetailDialog", "Imagen final creada: width=${finalBitmap.width}, height=${finalBitmap.height}")
                    
                    // Compartir la imagen
                    android.util.Log.d("RouteDetailDialog", "Compartiendo imagen...")
                    shareBitmap(context, finalBitmap, route)
                    android.util.Log.d("RouteDetailDialog", "=== FIN COMPARTIR ===")
                    
                    // Cerrar el dialog después de compartir
                    onComplete()
                    
                } catch (e: Exception) {
                    android.util.Log.e("RouteDetailDialog", "Error al procesar imagen: ${e.message}", e)
                    android.widget.Toast.makeText(context, "Error al procesar imagen: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                    onComplete()
                }
            }
        })
        
    } catch (e: Exception) {
        android.util.Log.e("RouteDetailDialog", "Error al compartir: ${e.message}", e)
        android.widget.Toast.makeText(context, "Error al compartir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        e.printStackTrace()
        onComplete()
    }
}

/**
 * Crea la imagen final del mapa fullscreen con la tarjeta flotante encima
 */
private suspend fun createFinalRouteImageFromFullscreen(
    route: Route,
    mapBitmap: android.graphics.Bitmap,
    context: android.content.Context
): android.graphics.Bitmap {
    // El mapa ya viene en pantalla completa, usamos sus dimensiones
    val width = mapBitmap.width
    val height = mapBitmap.height
    
    val finalBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(finalBitmap)
    
    // Dibujar el mapa como fondo
    canvas.drawBitmap(mapBitmap, 0f, 0f, null)
    
    // Inflar la tarjeta
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

/**
 * Configura la tarjeta con los datos de la ruta (función auxiliar reutilizable)
 */
private suspend fun configurarTarjetaCompartir(
    cardView: androidx.cardview.widget.CardView,
    route: Route,
    context: android.content.Context
) {
    // Configurar título de la ruta
    val routeTitle = if (route.notes.isNotBlank()) {
        route.notes
    } else {
        if (route.points.isNotEmpty()) {
            val startCity = getCityName(route.points.firstOrNull()?.latitude, route.points.firstOrNull()?.longitude)
            val endCity = getCityName(route.points.lastOrNull()?.latitude, route.points.lastOrNull()?.longitude)
            
            when {
                startCity != null && endCity != null && startCity != endCity -> "$startCity → $endCity"
                startCity != null -> "Ruta por $startCity"
                else -> "Mi ruta"
            }
        } else {
            "Mi ruta"
        }
    }
    cardView.findViewById<android.widget.TextView>(R.id.routeTitle).text = routeTitle
    
    // Configurar métricas
    cardView.findViewById<android.widget.TextView>(R.id.distanceValue).text = 
        String.format("%.1f km", route.totalDistance)
    cardView.findViewById<android.widget.TextView>(R.id.timeValue).text = 
        formatDurationWithUnits(route.totalDuration)
    cardView.findViewById<android.widget.TextView>(R.id.speedValue).text = 
        String.format("%.1f km/h", route.averageMovingSpeed)
    
    // Configurar clima si está disponible
    if (route.weatherEmoji != null && route.weatherTemperature != null) {
        cardView.findViewById<android.widget.TextView>(R.id.weatherEmoji).text = route.weatherEmoji
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
    
    // Eliminar el logo de ZipStats
    cardView.findViewById<android.widget.TextView>(R.id.zipstatsBranding).setCompoundDrawables(null, null, null, null)
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

    val finalBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(finalBitmap)
    
    // El mapa ocupa TODA la imagen (100% de altura y ancho)
    // Dibujar el mapa escalado para llenar toda la imagen
    val mapWidth = mapBitmap.width
    val mapHeightActual = mapBitmap.height
    val scaleX = width.toFloat() / mapWidth
    val scaleY = height.toFloat() / mapHeightActual
    val scale = kotlin.math.max(scaleX, scaleY) // Usar max para llenar completamente
    
    val scaledWidth = (mapWidth * scale).toInt()
    val scaledHeight = (mapHeightActual * scale).toInt()
    val offsetX = (width - scaledWidth) / 2
    val offsetY = (height - scaledHeight) / 2
    
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
    
    
    // Medir y renderizar la tarjeta (más compacta)
    val cardWidth = width - 64 // Márgenes de 32dp a cada lado
    val measureSpec = android.view.View.MeasureSpec.makeMeasureSpec(cardWidth, android.view.View.MeasureSpec.EXACTLY)
    cardView.measure(measureSpec, android.view.View.MeasureSpec.UNSPECIFIED)
    
    val cardHeight = cardView.measuredHeight
    val cardX = 32
    val cardY = height - cardHeight - 32 // Anclar al borde inferior con margen de 32dp
    
    cardView.layout(0, 0, cardView.measuredWidth, cardHeight)
    
    // Dibujar la tarjeta en el canvas (sin sombra oscura)
    canvas.save()
    canvas.translate(cardX.toFloat(), cardY.toFloat())
    cardView.draw(canvas)
    canvas.restore()
    
    return finalBitmap
}

/**
 * Representa un área de ciudad con sus coordenadas de límites
 */
data class CityArea(val name: String, val latMin: Double, val latMax: Double, val lonMin: Double, val lonMax: Double)

/**
 * Lista completa de ciudades y áreas metropolitanas para la aplicación de VMP.
 * Incluye una cobertura exhaustiva de España (con especial detalle en Cataluña),
 * además de ciudades importantes de Europa, América y Asia.
 */
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
/**
 * Obtiene el nombre de la ciudad basado en coordenadas GPS
 * Usa mapeo manual de ciudades españolas conocidas
 */
private fun getCityName(latitude: Double?, longitude: Double?): String? {
    if (latitude == null || longitude == null) return null
    
    return cityAreas.firstOrNull { city ->
        latitude >= city.latMin && latitude <= city.latMax &&
        longitude >= city.lonMin && longitude <= city.lonMax
    }?.name
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
        
        // Crear intent para compartir solo la imagen
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, imageUri)
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
