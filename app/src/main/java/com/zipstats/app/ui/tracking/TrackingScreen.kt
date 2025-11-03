package com.zipstats.app.ui.tracking

import android.Manifest
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import com.zipstats.app.model.Scooter
import com.zipstats.app.model.VehicleType
import com.zipstats.app.permission.PermissionManager
import com.zipstats.app.service.TrackingStateManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.zipstats.app.R
import com.zipstats.app.utils.LocationUtils
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import com.zipstats.app.repository.SettingsRepository
import androidx.compose.foundation.rememberScrollState

/**
 * Pantalla principal para el seguimiento de rutas GPS
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    viewModel: TrackingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToRoutes: () -> Unit = onNavigateBack
) {
    val context = LocalContext.current
    val permissionManager = remember { PermissionManager(context) }
    val settingsRepository = remember { SettingsRepository(context) }
    val keepScreenOnEnabled by settingsRepository.keepScreenOnDuringTrackingFlow.collectAsState(initial = false)
    
    // Manager de estado global
    val trackingStateManager = TrackingStateManager
    
    // Estados del ViewModel
    val trackingState by viewModel.trackingState.collectAsState()
    val isTracking = trackingState is TrackingState.Tracking
    
    // Mantener la pantalla encendida durante el tracking si está habilitado
    DisposableEffect(keepScreenOnEnabled, isTracking) {
        val window = (context as? Activity)?.window
        if (keepScreenOnEnabled && isTracking && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } else {
            onDispose { }
        }
    }
    val selectedScooter by viewModel.selectedScooter.collectAsState()
    val scooters by viewModel.scooters.collectAsState()
    val currentDistance by viewModel.currentDistance.collectAsState()
    val currentSpeed by viewModel.currentSpeed.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val message by viewModel.message.collectAsState()
    val gpsSignalStrength by viewModel.gpsSignalStrength.collectAsState()
    val weatherStatus by viewModel.weatherStatus.collectAsState()
    val gpsPreLocationState by viewModel.gpsPreLocationState.collectAsState()
    val hasValidGpsSignal = remember(gpsPreLocationState) { 
        viewModel.hasValidGpsSignal() 
    }
    
    // Estado local
    var showScooterPicker by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { 
        mutableStateOf(permissionManager.hasLocationPermissions()) 
    }
    
    // Launcher para permisos de ubicación y notificaciones
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
                                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!hasLocationPermission) {
            // Mostrar mensaje de error
            viewModel.clearMessage()
        }
    }
    
    // Variable para rastrear si acabamos de guardar una ruta
    var routeSaved by remember { mutableStateOf(false) }
    
    // Iniciar posicionamiento GPS previo cuando se entra en estado Idle y hay permisos
    LaunchedEffect(hasLocationPermission, trackingState) {
        if (hasLocationPermission && trackingState is TrackingState.Idle) {
            // Iniciar posicionamiento previo después de un pequeño delay
            kotlinx.coroutines.delay(300)
            viewModel.startPreLocationTracking()
        } else if (trackingState !is TrackingState.Idle) {
            // Si ya no estamos en Idle, detener el posicionamiento previo
            viewModel.stopPreLocationTracking()
        }
    }
    
    // Detener posicionamiento previo cuando se sale de la pantalla
    DisposableEffect(Unit) {
        onDispose {
            if (trackingState is TrackingState.Idle) {
                viewModel.stopPreLocationTracking()
            }
        }
    }
    
    // Navegar de vuelta a rutas cuando se finaliza una ruta
    LaunchedEffect(message) {
        if (message?.contains("Ruta guardada exitosamente") == true) {
            routeSaved = true
            // Navegar directamente sin delay
            onNavigateBack()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seguimiento GPS") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (trackingState is TrackingState.Tracking || trackingState is TrackingState.Paused) {
                            showCancelDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                !hasLocationPermission -> {
                    // Solicitar permisos
                    PermissionRequestCard(
                        onRequestPermissions = {
                            // Solicitar permisos de ubicación y notificaciones
                            val permissions = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            
                            // Agregar permiso de notificaciones en Android 13+
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            
                            locationPermissionLauncher.launch(permissions.toTypedArray())
                        }
                    )
                }
                trackingState is TrackingState.Idle -> {
                    // Selección de patinete e inicio
                    IdleStateContent(
                        selectedScooter = selectedScooter,
                        scooters = scooters,
                        gpsPreLocationState = gpsPreLocationState,
                        hasValidGpsSignal = hasValidGpsSignal,
                        onScooterClick = { showScooterPicker = true },
                        onStartTracking = { viewModel.startTracking() }
                    )
                }
                else -> {
                    // Seguimiento activo
                    TrackingActiveContent(
                        trackingState = trackingState,
                        currentDistance = currentDistance,
                        currentSpeed = currentSpeed,
                        duration = duration,
                        pointsCount = routePoints.size,
                        gpsSignalStrength = gpsSignalStrength,
                        weatherStatus = weatherStatus,
                        onPause = { viewModel.pauseTracking() },
                        onResume = { viewModel.resumeTracking() },
                        onFinish = { showFinishDialog = true },
                        onCancel = { showCancelDialog = true },
                        onFetchWeather = { viewModel.fetchWeatherManually() }
                    )
                }
            }
        }
    }
    
    // Diálogo de selección de patinete
    if (showScooterPicker) {
        ScooterPickerDialog(
            scooters = scooters,
            selectedScooter = selectedScooter,
            onScooterSelected = { 
                viewModel.selectScooter(it)
                showScooterPicker = false
            },
            onDismiss = { showScooterPicker = false }
        )
    }
    
    // Diálogo de finalización
    if (showFinishDialog) {
        FinishRouteDialog(
            distance = currentDistance,
            duration = duration,
            onConfirm = { notes, addToRecords ->
                viewModel.finishTracking(notes, addToRecords)
                showFinishDialog = false
            },
            onDismiss = { showFinishDialog = false }
        )
    }
    
    // Diálogo de cancelación
    if (showCancelDialog) {
        CancelRouteDialog(
            onConfirm = {
                viewModel.cancelTracking()
                showCancelDialog = false
                onNavigateBack()
            },
            onDismiss = { showCancelDialog = false }
        )
    }
}

/**
 * Obtiene el icono del vehículo según su tipo
 */
@Composable
fun getVehicleIcon(vehicleType: VehicleType): Painter {
    return when (vehicleType) {
        VehicleType.PATINETE -> painterResource(id = R.drawable.ic_electric_scooter_adaptive)
        VehicleType.BICICLETA -> painterResource(id = R.drawable.ic_ciclismo_adaptive)
        VehicleType.E_BIKE -> painterResource(id = R.drawable.ic_bicicleta_electrica_adaptive)
        VehicleType.MONOCICLO -> painterResource(id = R.drawable.ic_unicycle_adaptive)
    }
}

@Composable
fun PermissionRequestCard(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Permisos de ubicación requeridos",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Para grabar rutas GPS, necesitamos:\n• Acceso a tu ubicación\n• Mostrar notificación persistente",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Otorgar permisos")
            }
        }
    }
}

@Composable
fun IdleStateContent(
    selectedScooter: Scooter?,
    scooters: List<Scooter>,
    gpsPreLocationState: TrackingViewModel.GpsPreLocationState,
    hasValidGpsSignal: Boolean,
    onScooterClick: () -> Unit,
    onStartTracking: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icono GPS con estado dinámico
        GpsPreLocationIcon(
            gpsPreLocationState = gpsPreLocationState,
            modifier = Modifier.size(120.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Listo para grabar tu ruta",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Mensaje de estado GPS
        GpsPreLocationStatusText(
            gpsPreLocationState = gpsPreLocationState
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Selecciona tu vehículo e inicia el seguimiento",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Selector de vehículo
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onScooterClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedScooter != null) {
                    Image(
                        painter = getVehicleIcon(selectedScooter.vehicleType),
                        contentDescription = "Icono del vehículo",
                        modifier = Modifier.size(40.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.TwoWheeler,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedScooter?.nombre ?: "Seleccionar vehículo",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (selectedScooter != null) {
                        Text(
                            text = selectedScooter.modelo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Botón de inicio (solo habilitado si hay vehículo Y señal GPS válida)
        Button(
            onClick = onStartTracking,
            enabled = selectedScooter != null && hasValidGpsSignal,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasValidGpsSignal) "Iniciar seguimiento" else "Esperando señal GPS...",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * Icono GPS con estado dinámico según la calidad de señal
 */
@Composable
fun GpsPreLocationIcon(
    gpsPreLocationState: TrackingViewModel.GpsPreLocationState,
    modifier: Modifier = Modifier
) {
    val (iconColor, iconVector) = when (gpsPreLocationState) {
        is TrackingViewModel.GpsPreLocationState.Ready -> {
            Color(0xFF4CAF50) to Icons.Default.Place // Verde - Listo
        }
        is TrackingViewModel.GpsPreLocationState.Found -> {
            when {
                gpsPreLocationState.accuracy <= 10f -> {
                    Color(0xFFFFEB3B) to Icons.Default.Place // Amarillo - Buena señal
                }
                else -> {
                    Color(0xFFFF9800) to Icons.Default.Place // Naranja - Señal regular
                }
            }
        }
        is TrackingViewModel.GpsPreLocationState.Searching -> {
            Color(0xFFF44336) to Icons.Default.LocationOn // Rojo - Buscando
        }
    }
    
    Icon(
        imageVector = iconVector,
        contentDescription = null,
        modifier = modifier,
        tint = iconColor
    )
}

/**
 * Texto de estado GPS
 */
@Composable
fun GpsPreLocationStatusText(
    gpsPreLocationState: TrackingViewModel.GpsPreLocationState
) {
    val statusText = when (gpsPreLocationState) {
        is TrackingViewModel.GpsPreLocationState.Ready -> {
            "Listo para iniciar"
        }
        is TrackingViewModel.GpsPreLocationState.Found -> {
            when {
                gpsPreLocationState.accuracy <= 10f -> {
                    "Señal encontrada: Precisión ${gpsPreLocationState.accuracy.roundToInt()} m"
                }
                else -> {
                    "Buscando señal GPS... (Precisión ${gpsPreLocationState.accuracy.roundToInt()} m)"
                }
            }
        }
        is TrackingViewModel.GpsPreLocationState.Searching -> {
            "Buscando señal GPS..."
        }
    }
    
    val statusColor = when (gpsPreLocationState) {
        is TrackingViewModel.GpsPreLocationState.Ready -> {
            Color(0xFF4CAF50) // Verde
        }
        is TrackingViewModel.GpsPreLocationState.Found -> {
            when {
                gpsPreLocationState.accuracy <= 10f -> Color(0xFFFFEB3B) // Amarillo
                else -> Color(0xFFFF9800) // Naranja
            }
        }
        is TrackingViewModel.GpsPreLocationState.Searching -> {
            Color(0xFFF44336) // Rojo
        }
    }
    
    Text(
        text = statusText,
        style = MaterialTheme.typography.bodyMedium,
        color = statusColor,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun TrackingActiveContent(
    trackingState: TrackingState,
    currentDistance: Double,
    currentSpeed: Double,
    duration: Long,
    pointsCount: Int,
    gpsSignalStrength: Float,
    weatherStatus: WeatherStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onFetchWeather: () -> Unit = {}
) {
    val isPaused = trackingState is TrackingState.Paused
    val isSaving = trackingState is TrackingState.Saving
    
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isSmallScreen = screenWidthDp < 360
    val verticalSpacing = if (isSmallScreen) 16.dp else 24.dp
    val mediumSpacing = if (isSmallScreen) 12.dp else 16.dp
    val largeSpacing = if (isSmallScreen) 24.dp else 32.dp
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Indicador animado
        AnimatedTrackingIndicator(isPaused = isPaused, signalStrength = gpsSignalStrength)
        
        Spacer(modifier = Modifier.height(verticalSpacing))
        
        // Indicador de clima
        WeatherStatusIndicator(
            weatherStatus = weatherStatus,
            onFetchWeatherClick = onFetchWeather
        )
        
        Spacer(modifier = Modifier.height(mediumSpacing))
        
        // Estadísticas principales
        StatsGrid(
            distance = currentDistance,
            speed = currentSpeed,
            duration = duration,
            pointsCount = pointsCount
        )
        
        Spacer(modifier = Modifier.height(largeSpacing))
        
        // Controles
        if (!isSaving) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Botón pausar/reanudar
                FloatingActionButton(
                    onClick = { if (isPaused) onResume() else onPause() },
                    containerColor = if (isPaused) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(if (isSmallScreen) 48.dp else 56.dp)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Reanudar" else "Pausar",
                        modifier = Modifier.size(if (isSmallScreen) 24.dp else 32.dp)
                    )
                }
                
                // Botón finalizar
                FloatingActionButton(
                    onClick = onFinish,
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (isSmallScreen) 48.dp else 56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Finalizar",
                        modifier = Modifier.size(if (isSmallScreen) 24.dp else 32.dp)
                    )
                }
                
                // Botón cancelar
                FloatingActionButton(
                    onClick = onCancel,
                    containerColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(if (isSmallScreen) 48.dp else 56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancelar",
                        modifier = Modifier.size(if (isSmallScreen) 24.dp else 32.dp)
                    )
                }
            }
        } else {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(mediumSpacing))
            Text(
                text = "Guardando ruta...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (isSmallScreen) 12.sp else MaterialTheme.typography.bodyMedium.fontSize
                )
            )
        }
    }
}

@Composable
fun AnimatedTrackingIndicator(isPaused: Boolean, signalStrength: Float) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isSmallScreen = screenWidthDp < 360
    val indicatorSize = if (isSmallScreen) 80.dp else 100.dp
    val iconSize = if (isSmallScreen) 96.dp else 120.dp
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(indicatorSize)
    ) {
        if (!isPaused) {
            Box(
                modifier = Modifier
                    .size(indicatorSize * scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            )
        }
        GpsIconWithSignalRing(
            signalStrength = signalStrength,
            isPaused = isPaused,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun StatsGrid(
    distance: Double,
    speed: Double,
    duration: Long,
    pointsCount: Int
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isSmallScreen = screenWidthDp < 360
    val spacing = if (isSmallScreen) 8.dp else 16.dp
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            StatCard(
                title = "Distancia",
                value = LocationUtils.formatDistance(distance),
                icon = Icons.Default.Route,
                modifier = Modifier.weight(1f),
                isSmallScreen = isSmallScreen
            )
            StatCard(
                title = "Velocidad",
                value = LocationUtils.formatSpeed(speed),
                icon = Icons.Default.Speed,
                modifier = Modifier.weight(1f),
                isSmallScreen = isSmallScreen
            )
        }
        Spacer(modifier = Modifier.height(spacing))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            StatCard(
                title = "Duración",
                value = formatDuration(duration),
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f),
                isSmallScreen = isSmallScreen
            )
            StatCard(
                title = "Puntos GPS",
                value = pointsCount.toString(),
                icon = Icons.Default.Place,
                modifier = Modifier.weight(1f),
                isSmallScreen = isSmallScreen
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    isSmallScreen: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isSmallScreen) 8.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (isSmallScreen) 20.dp else 24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(if (isSmallScreen) 4.dp else 8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (isSmallScreen) 14.sp else MaterialTheme.typography.titleMedium.fontSize
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = if (isSmallScreen) 10.sp else MaterialTheme.typography.bodySmall.fontSize
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ScooterPickerDialog(
    scooters: List<Scooter>,
    selectedScooter: Scooter?,
    onScooterSelected: (Scooter) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar vehículo") },
        text = {
            LazyColumn {
                items(scooters) { scooter ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { onScooterSelected(scooter) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (scooter.id == selectedScooter?.id) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = getVehicleIcon(scooter.vehicleType),
                                contentDescription = "Icono del vehículo",
                                modifier = Modifier.size(24.dp),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = scooter.nombre,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = scooter.modelo,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun FinishRouteDialog(
    distance: Double,
    duration: Long,
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var notes by remember { mutableStateOf("") }
    var addToRecords by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finalizar ruta") },
        text = {
            Column {
                Text("¿Deseas guardar esta ruta?")
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Distancia: ${LocationUtils.formatDistance(distance)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Duración: ${formatDuration(duration)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Checkbox para añadir a registros
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = addToRecords,
                        onCheckedChange = { addToRecords = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Añadir ${String.format("%.1f", distance)} km a registros",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Título (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(notes, addToRecords) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun CancelRouteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancelar seguimiento") },
        text = { Text("¿Estás seguro? Se perderán todos los datos de la ruta.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Cancelar ruta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Volver")
            }
        }
    )
}

fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

/**
 * Componente personalizado del icono GPS con aro dinámico según la señal
 */
@Composable
fun GpsIconWithSignalRing(
    signalStrength: Float,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    // Determinar color del aro según la fuerza de señal
    val ringColor = when {
        signalStrength >= 80f -> Color(0xFF4CAF50) // Verde - Excelente
        signalStrength >= 50f -> Color(0xFFFFEB3B) // Amarillo - Buena/Regular
        signalStrength >= 20f -> Color(0xFFFF9800) // Naranja - Débil
        else -> Color(0xFFF44336) // Rojo - Muy débil/Sin señal
    }
    
    // Opacidad del aro (más opaco = mejor señal)
    val ringAlpha = (signalStrength / 100f).coerceIn(0.3f, 1f)
    
    Box(
        modifier = modifier
            .size(120.dp)
            .drawBehind {
                // Dibujar aro de señal GPS
                drawCircle(
                    color = ringColor.copy(alpha = ringAlpha),
                    radius = size.minDimension / 2 - 8.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 8.dp.toPx()
                    )
                )
                
                // Dibujar aro interno más sutil
                drawCircle(
                    color = ringColor.copy(alpha = ringAlpha * 0.3f),
                    radius = size.minDimension / 2 - 4.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx()
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Icono GPS central
        Icon(
            imageVector = if (isPaused) Icons.Default.Pause else Icons.Default.GpsFixed,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = Color.White
        )
    }
}

/**
 * Función para obtener el color de la sombra según la fuerza de señal GPS
 */
private fun getSignalColor(signalStrength: Float): Color {
    return when {
        signalStrength >= 80f -> Color(0xFF4CAF50) // Verde - Excelente
        signalStrength >= 50f -> Color(0xFFFFEB3B) // Amarillo - Buena/Regular
        signalStrength >= 20f -> Color(0xFFFF9800) // Naranja - Débil
        else -> Color(0xFFF44336) // Rojo - Muy débil/Sin señal
    }
}

/**
 * Indicador del estado de captura del clima
 */
@Composable
fun WeatherStatusIndicator(
    weatherStatus: WeatherStatus,
    onFetchWeatherClick: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = weatherStatus !is WeatherStatus.Idle,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
    ) {
        val isClickable = weatherStatus is WeatherStatus.NotAvailable || 
                          weatherStatus is WeatherStatus.Error
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .then(
                    if (isClickable) {
                        Modifier.clickable(onClick = onFetchWeatherClick)
                    } else Modifier
                ),
            colors = CardDefaults.cardColors(
                containerColor = when (weatherStatus) {
                    is WeatherStatus.Loading -> MaterialTheme.colorScheme.surfaceVariant
                    is WeatherStatus.Success -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    is WeatherStatus.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    is WeatherStatus.NotAvailable -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                when (weatherStatus) {
                    is WeatherStatus.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Obteniendo clima...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is WeatherStatus.Success -> {
                        Text(
                            text = weatherStatus.emoji,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${String.format("%.0f", weatherStatus.temperature)}°C",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF4CAF50)
                        )
                    }
                    is WeatherStatus.Error -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clima no disponible",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (isClickable) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "- Toca para intentar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                    is WeatherStatus.NotAvailable -> {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sin datos de clima",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        if (isClickable) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "- Toca para obtener",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

