package com.zipstats.app.ui.tracking

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zipstats.app.R
import com.zipstats.app.di.AppOverlayRepositoryEntryPoint
import com.zipstats.app.model.Scooter
import com.zipstats.app.model.VehicleType
import com.zipstats.app.permission.PermissionManager
import com.zipstats.app.repository.AppOverlayRepository
import com.zipstats.app.repository.SettingsRepository
import com.zipstats.app.ui.components.DialogCancelButton
import com.zipstats.app.ui.components.DialogConfirmButton
import com.zipstats.app.ui.components.DialogDeleteButton
import com.zipstats.app.ui.components.ZipStatsText
import com.zipstats.app.ui.shared.AppOverlayState
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.utils.LocationUtils
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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

    // AppOverlayRepository para observar cuando se oculta el overlay
    val appOverlayRepository: AppOverlayRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppOverlayRepositoryEntryPoint::class.java
        ).appOverlayRepository()
    }
    val overlay by appOverlayRepository.overlay.collectAsState()

    val trackingState by viewModel.trackingState.collectAsState()
    val isTracking = trackingState is TrackingState.Tracking

    DisposableEffect(keepScreenOnEnabled, isTracking) {
        val window = (context as? Activity)?.window
        var flagWasSet = false
        if (keepScreenOnEnabled && isTracking && window != null) {
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                flagWasSet = true
            } catch (e: Exception) {
                android.util.Log.e("TrackingScreen", "Error setting FLAG_KEEP_SCREEN_ON", e)
            }
        }
        onDispose {
            if (flagWasSet && window != null) {
                try {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } catch (e: Exception) {
                    android.util.Log.e("TrackingScreen", "Error clearing FLAG_KEEP_SCREEN_ON", e)
                }
            }
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
    val hasValidGpsSignal = remember(gpsPreLocationState) { viewModel.hasValidGpsSignal() }
    val shouldShowRainWarning by viewModel.shouldShowRainWarning.collectAsState()
    val isActiveRainWarning by viewModel.isActiveRainWarning.collectAsState()
    val shouldShowExtremeWarning by viewModel.shouldShowExtremeWarning.collectAsState()

    var showScooterPicker by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    val hasLocationPermission = permissionManager.hasLocationPermissions()
    val hasNotificationPermission = permissionManager.hasNotificationPermission()
    val hasAllRequiredPermissions = hasLocationPermission && hasNotificationPermission

    fun openAppSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    var routeSaved by remember { mutableStateOf(false) }
    
    // Flag local de cierre: una vez se pulsa "Guardar", la pantalla NO debe renderizar nada más
    // Este flag manda más que cualquier estado del ViewModel
    var isClosing by rememberSaveable { mutableStateOf(false) }
    
    // Flag para garantizar que el pre-GPS solo se inicia una vez al entrar a la pantalla
    var hasStartedPreGps by rememberSaveable { mutableStateOf(false) }

    // Pre-GPS solo se inicia una vez al entrar a la pantalla
    // Patrón determinista: efecto de entrada, no de estado
    LaunchedEffect(hasAllRequiredPermissions) {
        if (hasAllRequiredPermissions && !hasStartedPreGps) {
            hasStartedPreGps = true
            viewModel.startPreLocationTracking()
        }
    }

    // Detener pre-GPS cuando empieza el tracking activo
    LaunchedEffect(trackingState) {
        if (trackingState is TrackingState.Tracking) {
            viewModel.stopPreLocationTracking()
        }
    }

    // Detener pre-GPS cuando se sale de la pantalla o cuando se guarda la ruta
    DisposableEffect(Unit) {
        onDispose {
            if (!routeSaved) {
                viewModel.stopPreLocationTracking()
            }
        }
    }

    // Navegar cuando el overlay se oculte después de guardar
    LaunchedEffect(overlay, message) {
        if (message?.contains("Ruta guardada exitosamente") == true && overlay is AppOverlayState.None && !routeSaved) {
            routeSaved = true
            viewModel.stopPreLocationTracking() // Detener GPS previo para evitar mostrar la pantalla de precarga
            // Usar onNavigateToRoutes para navegar correctamente a Routes
            // en lugar de onNavigateBack que puede mostrar la pantalla de pre-carga
            onNavigateToRoutes()
            viewModel.clearMessage()
        }
    }

    // SOLUCIÓN DEFINITIVA: Abortar composición completa antes del Scaffold
    // Esto evita el layout pass que causa el flash
    // Compose no puede dibujar lo que no existe en el árbol
    if (isClosing) {
        // Importante: no Scaffold, no Column, no Scroll
        // Solo un Box vacío que ocupa todo el espacio
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ZipStatsText(
                        "Seguimiento GPS",
                        fontWeight = FontWeight.Bold
                    )
                },
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
                // Estilo moderno 'Surface' unificado con el resto de la app
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()) // Scroll para pantallas pequeñas
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                !hasAllRequiredPermissions -> {
                    PermissionRequestCard(onRequestPermissions = { openAppSettings() })
                }
                // No mostrar IdleStateContent si ya guardamos la ruta (estamos navegando)
                routeSaved -> {
                    // Mostrar contenido vacío mientras navegamos
                    Box(modifier = Modifier.fillMaxSize())
                }
                trackingState is TrackingState.Idle && hasAllRequiredPermissions -> {
                    IdleStateContent(
                        selectedScooter = selectedScooter,
                        scooters = scooters,
                        gpsPreLocationState = gpsPreLocationState,
                        hasValidGpsSignal = hasValidGpsSignal,
                        weatherStatus = weatherStatus,
                        shouldShowRainWarning = shouldShowRainWarning,
                        isActiveRainWarning = isActiveRainWarning,
                        shouldShowExtremeWarning = shouldShowExtremeWarning,
                        isTracking = isTracking,
                        onScooterClick = { showScooterPicker = true },
                        onStartTracking = { viewModel.startTracking() },
                        onDismissRainWarning = { viewModel.dismissRainWarning() },
                        onDismissExtremeWarning = { viewModel.dismissExtremeWarning() }
                    )
                }
                else -> {
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

    val finishSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    
    if (showFinishDialog) {
        ModalBottomSheet(
            onDismissRequest = { showFinishDialog = false },
            sheetState = finishSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            FinishRouteBottomSheet(
                distance = currentDistance,
                duration = duration,
                onConfirm = { notes, addToRecords ->
                    // Activar flag de cierre ANTES de cualquier estado
                    // Esto congela la UI y evita cualquier flash
                    isClosing = true
                    viewModel.finishTracking(notes, addToRecords)
                    scope.launch {
                        finishSheetState.hide()
                        showFinishDialog = false
                    }
                },
                onDismiss = {
                    scope.launch {
                        finishSheetState.hide()
                        showFinishDialog = false
                    }
                }
            )
        }
    }

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

// Modelo de datos simple para un aviso
data class WarningItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val iconColor: Color // Color del icono
)

/**
 * Centro de Notificaciones Unificado - Una sola tarjeta inteligente que agrupa todos los avisos
 * Prioridad: Lluvia/Calzada mojada > Condiciones extremas
 */
@Composable
fun PreRideSmartWarning(
    shouldShowRainWarning: Boolean,
    isActiveRainWarning: Boolean,
    shouldShowExtremeWarning: Boolean,
    isTracking: Boolean,
    weatherStatus: WeatherStatus,
    onDismissRainWarning: () -> Unit,
    onDismissExtremeWarning: () -> Unit
) {
    // Solo mostrar en pantalla de precarga GPS (antes de iniciar), no durante el tracking
    val shouldShow = (shouldShowRainWarning || shouldShowExtremeWarning) && !isTracking
    
    // Construir la lista de avisos activos
    // 🔒 REGLAS: 
    // 1. Lluvia y Calzada Mojada son EXCLUYENTES (nunca aparecen juntos)
    // 2. Condiciones Extremas es COMPLEMENTARIO (puede aparecer solo o con lluvia/calzada)
    val activeWarnings = remember(shouldShowRainWarning, isActiveRainWarning, shouldShowExtremeWarning, weatherStatus) {
        val list = mutableListOf<WarningItem>()

        // Prioridad 1: Lluvia o Calzada Mojada (Mutuamente EXCLUYENTES)
        if (shouldShowRainWarning) {
            if (isActiveRainWarning) {
                // Lluvia activa
                list.add(
                    WarningItem(
                        icon = Icons.Default.WaterDrop,
                        title = "Lluvia detectada",
                        subtitle = "El pavimento está resbaladizo.",
                        iconColor = Color(0xFF0D47A1) // Azul oscuro
                    )
                )
            } else {
                // Calzada mojada (sin lluvia activa) - Usar icono de lluvia en lugar de Warning
                list.add(
                    WarningItem(
                        icon = Icons.Default.WaterDrop,
                        title = "Calzada mojada",
                        subtitle = "El pavimento puede estar resbaladizo.",
                        iconColor = Color(0xFFF57C00) // Naranja fuerte para precaución, no error
                    )
                )
            }
        }

        // Prioridad 2: Condiciones Extremas (COMPLEMENTARIO - puede aparecer solo o con lluvia/calzada)
        // 🔥 Mensaje dinámico según la condición específica que activa la alerta
        if (shouldShowExtremeWarning) {
            val extremeSubtitle = when (weatherStatus) {
                is WeatherStatus.Success -> {
                    // Convertir viento de m/s a km/h
                    val windSpeedKmh = (weatherStatus.windSpeed ?: 0.0) * 3.6
                    val windGustsKmh = (weatherStatus.windGusts ?: 0.0) * 3.6
                    val temperature = weatherStatus.temperature // Double, no nullable
                    val uvIndex = weatherStatus.uvIndex
                    val isDay = weatherStatus.isDay
                    
                    // Detectar qué condiciones específicas están activas
                    val isExtremeWind = windSpeedKmh > 40
                    val isExtremeGusts = windGustsKmh > 60
                    val isExtremeTemp = temperature < 0 || temperature > 35
                    val isExtremeUv = isDay && uvIndex != null && uvIndex > 8
                    val isStorm = weatherStatus.weatherEmoji?.let { emoji ->
                        emoji.contains("⛈") || emoji.contains("⚡")
                    } ?: false
                    val isStormByDescription = weatherStatus.description?.let { desc ->
                        desc.contains("Tormenta", ignoreCase = true) ||
                        desc.contains("granizo", ignoreCase = true) ||
                        desc.contains("rayo", ignoreCase = true)
                    } ?: false
                    
                    // Generar mensaje específico según las condiciones detectadas
                    when {
                        isStorm || isStormByDescription -> "Tormenta detectada"
                        isExtremeGusts && isExtremeTemp -> "Ráfagas muy fuertes (${windGustsKmh.toInt()} km/h) y temp. extrema (${temperature.toInt()}°C)"
                        isExtremeWind && isExtremeTemp -> "Viento fuerte (${windSpeedKmh.toInt()} km/h) y temp. extrema (${temperature.toInt()}°C)"
                        isExtremeGusts -> "Ráfagas de viento muy fuertes (${windGustsKmh.toInt()} km/h)"
                        isExtremeWind -> "Viento fuerte (${windSpeedKmh.toInt()} km/h)"
                        isExtremeTemp -> {
                            if (temperature > 35) {
                                "Calor intenso (${temperature.toInt()}°C)"
                            } else {
                                "Temperaturas bajo cero (${temperature.toInt()}°C)"
                            }
                        }
                        isExtremeUv -> "Índice UV muy alto (${uvIndex?.toInt() ?: 0})"
                        else -> "Condiciones meteorológicas adversas"
                    }
                }
                else -> "Ten precaución durante el trayecto."
            }
            
            list.add(
                WarningItem(
                    icon = Icons.Default.Warning,
                    title = "Condiciones extremas",
                    subtitle = extremeSubtitle,
                    iconColor = Color(0xFFB71C1C) // Rojo oscuro
                )
            )
        }
        
        list
    }

    // Si no hay avisos, no mostramos nada
    if (activeWarnings.isEmpty() || !shouldShow) return

    // Determinar el color de fondo de la tarjeta según la prioridad
    // Si hay lluvia -> Fondo azul suave (no errorContainer)
    // Si hay calzada mojada -> Fondo naranja/ámbar suave
    // Si hay condiciones extremas -> Fondo rojo suave (no errorContainer puro para evitar confusión)
    val containerColor = if (shouldShowExtremeWarning) {
        Color(0xFFFFEBEE) // Rojo claro suave (no errorContainer puro)
    } else if (isActiveRainWarning) {
        Color(0xFFE3F2FD) // Azul claro suave (no tertiaryContainer puro)
    } else {
        // Calzada mojada: usar un color naranja/ámbar suave
        Color(0xFFFFF8E1) // Ámbar claro suave
    }
    
    val contentColor = if (shouldShowExtremeWarning) {
        Color(0xFFB71C1C) // Rojo oscuro para buen contraste
    } else if (isActiveRainWarning) {
        Color(0xFF0D47A1) // Azul oscuro para buen contraste
    } else {
        // Calzada mojada: texto naranja oscuro para buen contraste
        Color(0xFFE65100) // Naranja oscuro
    }

    // Renderizado
    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                // Agregar borde sutil para todos los tipos de avisos
                .border(
                    width = 1.dp,
                    color = when {
                        shouldShowExtremeWarning -> Color(0xFFEF5350).copy(alpha = 0.5f) // Rojo suave
                        isActiveRainWarning -> Color(0xFF42A5F5).copy(alpha = 0.5f) // Azul suave
                        else -> Color(0xFFFFB74D).copy(alpha = 0.5f) // Naranja suave (calzada mojada)
                    },
                    shape = RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Botón de cerrar (Uno solo para toda la tarjeta)
                // Si hay múltiples avisos, cerramos todos a la vez
                IconButton(
                    onClick = {
                        onDismissRainWarning()
                        onDismissExtremeWarning()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar avisos",
                        tint = contentColor.copy(alpha = 0.7f)
                    )
                }

                // Lista de avisos
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    activeWarnings.forEachIndexed { index, warning ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icono con fondo circular para todos los tipos de avisos (diseño consistente)
                            val iconBackgroundColor = when {
                                shouldShowExtremeWarning && warning.icon == Icons.Default.Warning -> 
                                    Color(0xFFEF5350).copy(alpha = 0.2f) // Rojo suave para condiciones extremas
                                isActiveRainWarning && warning.icon == Icons.Default.WaterDrop -> 
                                    Color(0xFF42A5F5).copy(alpha = 0.2f) // Azul suave para lluvia
                                !shouldShowExtremeWarning && !isActiveRainWarning && warning.icon == Icons.Default.WaterDrop -> 
                                    Color(0xFFFFB74D).copy(alpha = 0.2f) // Naranja suave para calzada mojada
                                else -> null // Sin fondo para otros casos
                            }
                            
                            if (iconBackgroundColor != null) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            iconBackgroundColor,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = warning.icon,
                                        contentDescription = null,
                                        tint = warning.iconColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = warning.icon,
                                    contentDescription = null,
                                    tint = warning.iconColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                ZipStatsText(
                                    text = warning.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                                ZipStatsText(
                                    text = warning.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor.copy(alpha = 0.9f)
                                )
                            }
                            // Espacio para que el texto no se monte sobre la X en el primer elemento
                            if (index == 0) {
                                Spacer(modifier = Modifier.width(24.dp)) 
                            }
                        }
                        
                        // Si no es el último elemento, ponemos una línea divisoria sutil
                        if (index < activeWarnings.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 12.dp),
                                color = contentColor.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

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
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
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
            ZipStatsText(
                text = "Permisos requeridos",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            ZipStatsText(
                text = "Para grabar rutas GPS, necesitamos:\n• Acceso a tu ubicación\n• Mostrar notificación persistente\n\nVe a Configuración > Aplicaciones > ZipStats > Permisos para activarlos.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                ZipStatsText("Abrir configuración")
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
    weatherStatus: WeatherStatus,
    shouldShowRainWarning: Boolean,
    isActiveRainWarning: Boolean,
    shouldShowExtremeWarning: Boolean,
    isTracking: Boolean,
    onScooterClick: () -> Unit,
    onStartTracking: () -> Unit,
    onDismissRainWarning: () -> Unit,
    onDismissExtremeWarning: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icono GPS con pulso sutil si está listo
        GpsPreLocationIcon(
            gpsPreLocationState = gpsPreLocationState,
            modifier = Modifier.size(140.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Texto condicional según el estado del GPS
        val titleText = when (gpsPreLocationState) {
            is TrackingViewModel.GpsPreLocationState.Searching -> "Preparando tu ruta"
            is TrackingViewModel.GpsPreLocationState.Found -> "Listo para grabar tu ruta"
            is TrackingViewModel.GpsPreLocationState.Ready -> "Listo para grabar tu ruta"
        }
        
        ZipStatsText(
            text = titleText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        GpsPreLocationStatusText(gpsPreLocationState = gpsPreLocationState)

        Spacer(modifier = Modifier.height(32.dp))

        // Selector de vehículo estilo tarjeta limpia
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onScooterClick,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) // Más sutil
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selectedScooter != null) {
                            Image(
                                painter = getVehicleIcon(selectedScooter.vehicleType),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.TwoWheeler,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    ZipStatsText(
                        text = "Vehículo seleccionado",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ZipStatsText(
                        text = selectedScooter?.nombre ?: "Seleccionar...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedScooter != null) {
                        ZipStatsText(
                            text = selectedScooter.modelo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // Centro de Notificaciones Unificado - Una sola tarjeta inteligente que agrupa todos los avisos
        PreRideSmartWarning(
            shouldShowRainWarning = shouldShowRainWarning,
            isActiveRainWarning = isActiveRainWarning,
            shouldShowExtremeWarning = shouldShowExtremeWarning,
            isTracking = isTracking,
            weatherStatus = weatherStatus,
            onDismissRainWarning = onDismissRainWarning,
            onDismissExtremeWarning = onDismissExtremeWarning
        )

        Button(
            onClick = onStartTracking,
            enabled = selectedScooter != null && hasValidGpsSignal,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp), // Botón más grande y fácil de pulsar
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            ZipStatsText(
                text = if (hasValidGpsSignal) "Iniciar seguimiento" else "Buscando señal GPS...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GpsPreLocationIcon(
    gpsPreLocationState: TrackingViewModel.GpsPreLocationState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gps_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (gpsPreLocationState is TrackingViewModel.GpsPreLocationState.Searching) 1.3f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val (iconColor, iconVector) = when (gpsPreLocationState) {
        is TrackingViewModel.GpsPreLocationState.Ready -> Color(0xFF4CAF50) to Icons.Default.Place
        is TrackingViewModel.GpsPreLocationState.Found -> {
            if (gpsPreLocationState.accuracy <= 10f) Color(0xFFFFEB3B) to Icons.Default.Place
            else Color(0xFFFF9800) to Icons.Default.Place
        }
        is TrackingViewModel.GpsPreLocationState.Searching -> Color(0xFFF44336) to Icons.Default.LocationOn
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        // Aro pulsante con animación más visible
        Box(
            modifier = Modifier
                .matchParentSize()
                .scale(scale)
                .drawBehind {
                    drawCircle(
                        color = iconColor.copy(alpha = alpha),
                        radius = size.minDimension / 2
                    )
                }
        )

        Icon(
            imageVector = iconVector,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = iconColor
        )
    }
}

@Composable
fun GpsPreLocationStatusText(gpsPreLocationState: TrackingViewModel.GpsPreLocationState) {
    val (text, containerColor, textColor) = when (gpsPreLocationState) {
        is TrackingViewModel.GpsPreLocationState.Ready -> {
            Triple(
                "Señal GPS Excelente",
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        is TrackingViewModel.GpsPreLocationState.Found -> {
            val acc = gpsPreLocationState.accuracy.roundToInt()
            if (acc <= 10) {
                Triple(
                    "Señal Buena (±${acc}m)",
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer
                )
            } else {
                Triple(
                    "Señal Débil (±${acc}m)",
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        is TrackingViewModel.GpsPreLocationState.Searching -> {
            Triple(
                "Buscando satélites...",
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        ZipStatsText(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        // 1. ZONA HERO: VELOCIDAD (Limpia, sin nada encima)
        // Damos buen margen superior para que respire
        HeroSpeedometer(
            speed = currentSpeed, 
            modifier = Modifier.padding(top = 40.dp) 
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Tarjeta Clima
        TrackingWeatherCard(
            weatherStatus = weatherStatus,
            onFetchWeatherClick = onFetchWeather
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Grid Datos
        StatsGridCompact(
            distance = currentDistance,
            duration = duration,
            gpsSignalStrength = gpsSignalStrength // Lo pasamos aunque no se use visualmente aquí
        )

        // 🛑 CAMBIO: En lugar de weight(1f) que puede fallar en pantallas pequeñas,
        // usamos un Spacer fijo para garantizar separación mínima.
        Spacer(modifier = Modifier.height(48.dp))

        // 4. CONTROLES INTEGRADOS
        if (isSaving) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                ZipStatsText("Guardando ruta...", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            AnimatedContent(
                targetState = isPaused,
                label = "controls"
            ) { paused ->
                if (paused) {
                    // --- MODO PAUSADO (Gestión) ---
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlActionButton(
                            icon = Icons.Default.Close,
                            label = "Cancelar",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                            onClick = onCancel
                        )
                        
                        // Botón Reanudar (Grande)
                        FloatingActionButton(
                            onClick = onResume,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(88.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.PlayArrow, "Reanudar", modifier = Modifier.size(40.dp))
                        }

                        ControlActionButton(
                            icon = Icons.Default.Check,
                            label = "Guardar",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = onFinish
                        )
                    }
                } else {
                    // --- MODO GRABANDO (Botón "Halo" Vivo) ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Usamos el nuevo componente aquí
                        PulsingGpsPauseButton(
                            signalStrength = gpsSignalStrength,
                            onClick = onPause,
                            modifier = Modifier.size(96.dp) // Tamaño generoso
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedTrackingIndicator(
    isPaused: Boolean,
    signalStrength: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    // OPTIMIZACIÓN: Si está pausado, animamos hacia el estado de reposo (1f)
    // en lugar de cortar el componente del árbol UI.
    val targetScale = if (isPaused) 1f else 1.2f
    val targetAlpha = if (isPaused) 0f else 0.1f // 0f para que desaparezca suavemente al pausar

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = targetScale,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = targetAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Usamos Box siempre, pero controlamos visibilidad con alpha
    Box(contentAlignment = Alignment.Center) {
        // Solo dibujamos si hay algo de opacidad visible
        if (alpha > 0f) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    // OPTIMIZACIÓN: graphicsLayer evita recomposiciones de layout
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha // Aplicamos alpha al layer completo o en el color
                    }
                    .drawBehind {
                        drawCircle(
                            color = getSignalColor(signalStrength), // El alpha ya lo maneja graphicsLayer
                            radius = size.minDimension / 2
                        )
                    }
            )
        }
		GpsIconWithSignalRing(
            signalStrength = signalStrength,
            isPaused = isPaused,
            modifier = Modifier.size(80.dp)
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                title = "Distancia",
                value = LocationUtils.formatDistance(distance),
                icon = Icons.Default.Route,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Velocidad",
                value = LocationUtils.formatSpeed(speed),
                icon = Icons.Default.Speed,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                title = "Duración",
                value = formatDuration(duration),
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Puntos GPS",
                value = pointsCount.toString(),
                icon = Icons.Default.Place,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // --- AQUÍ ESTÁ EL CAMBIO ---
            ZipStatsText(
                text = value,
                // Usamos el estilo del tema (que ya debería tener Montserrat)
                // Y le inyectamos la configuración "tnum" (Tabular Numbers)
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFeatureSettings = "tnum"
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            // ---------------------------

            ZipStatsText(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// --- WEATHER CARD LOCAL (Para Tracking) ---
@Composable
fun TrackingWeatherCard(
    weatherStatus: WeatherStatus,
    onFetchWeatherClick: () -> Unit
) {
    val weatherClickInteractionSource = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = weatherStatus !is WeatherStatus.Idle,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(
                interactionSource = weatherClickInteractionSource,
                indication = null,
                onClick = onFetchWeatherClick
            ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (weatherStatus) {
                    is WeatherStatus.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        ZipStatsText("Cargando clima...", style = MaterialTheme.typography.bodyMedium)
                    }
                    is WeatherStatus.Success -> {
                        // Nota: usamos el helper que acepta nulo para seguridad
                        Image(
                            painter = painterResource(id = getWeatherIconResIdFromEmoji(weatherStatus.weatherEmoji, weatherStatus.isDay)),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp) ,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        ZipStatsText(
                            text = "${formatTemperature(weatherStatus.temperature, decimals = 0)}°C",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface // <--- Blanco fuerte
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        // --- CORRECCIÓN: Mostrar dirección del viento ---
                        Icon(Icons.Default.Air, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))

                        val direction = convertWindDirectionToText(weatherStatus.windDirection)
                        // Convertir viento de m/s a km/h (weatherStatus.windSpeed está en m/s)
                        val windSpeedKmh = (weatherStatus.windSpeed ?: 0.0) * 3.6

                        ZipStatsText(
                            text = "${String.format("%.0f", windSpeedKmh)} km/h ($direction)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is WeatherStatus.Error -> {
                        Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        ZipStatsText("Sin datos de clima (Toca para reintentar)", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {}
                }
            }
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
        title = { ZipStatsText("Seleccionar vehículo") },
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
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                ZipStatsText(
                                    text = scooter.nombre,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                ZipStatsText(
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
            DialogCancelButton(
                text = "Cerrar",
                onClick = onDismiss
            )
        },
        shape = DialogShape
    )
}

@Composable
fun FinishRouteBottomSheet(
    distance: Double,
    duration: Long,
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var notes by remember { mutableStateOf("") }
    var addToRecords by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ZipStatsText(
            text = "Finalizar ruta",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        ZipStatsText("¿Deseas guardar esta ruta?")

        ZipStatsText(
            text = "Distancia: ${LocationUtils.formatDistance(distance)}",
            style = MaterialTheme.typography.bodyMedium
        )
        ZipStatsText(
            text = "Duración: ${formatDuration(duration)}",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = addToRecords,
                onCheckedChange = { addToRecords = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            ZipStatsText(
                text = "Añadir ${String.format("%.1f", distance)} km a registros",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { ZipStatsText("Título (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        // Botones
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                ZipStatsText("Cancelar")
            }
            Button(
                onClick = { onConfirm(notes, addToRecords) },
                modifier = Modifier.weight(1f)
            ) {
                ZipStatsText("Guardar")
            }
        }
    }
}

@Composable
fun CancelRouteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { ZipStatsText("Cancelar seguimiento") },
        text = { ZipStatsText("¿Estás seguro? Se perderán todos los datos de la ruta.") },
        confirmButton = {
            DialogDeleteButton(
                text = "Cancelar ruta",
                onClick = onConfirm
            )
        },
        dismissButton = {
            DialogCancelButton(
                text = "Volver",
                onClick = onDismiss
            )
        },
        shape = DialogShape
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

@Composable
fun GpsIconWithSignalRing(
    signalStrength: Float,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val ringColor = getSignalColor(signalStrength)
    val ringAlpha = (signalStrength / 100f).coerceIn(0.3f, 1f)

    // Animación pulsante cuando está grabando (no pausado)
    val infiniteTransition = rememberInfiniteTransition(label = "gps_signal_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPaused) 1f else 1.15f, // Solo anima si no está pausado
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = if (isPaused) 0.6f else 0.3f, // Solo anima si no está pausado
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Anillo pulsante animado (solo cuando está grabando)
        if (!isPaused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .drawBehind {
                        // Anillo principal pulsante que sale directamente del borde del botón
                        // El botón tiene 96dp, así que su radio es 48dp desde el centro del Box
                        // El anillo debe empezar exactamente en el borde del botón
                        val botonRadius = 48.dp.toPx() // Radio del botón (96dp / 2)
                        // El radio del anillo es el borde del botón (el stroke se dibuja hacia afuera)
                        val radiusMain = botonRadius

                        drawCircle(
                            color = ringColor.copy(alpha = ringAlpha),
                            radius = radiusMain,
                            style = Stroke(width = 10.dp.toPx()) // El stroke se dibuja hacia afuera desde el radio
                        )
                    }
            )
        } else {
            // Cuando está pausado, mostrar anillo estático sin animación
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        // Anillo estático cuando está pausado - sale del borde del botón
                        val botonRadius = 48.dp.toPx() // Radio del botón (96dp / 2)
                        // El radio del anillo es el borde del botón (el stroke se dibuja hacia afuera)
                        val radiusMain = botonRadius

                        drawCircle(
                            color = ringColor.copy(alpha = ringAlpha),
                            radius = radiusMain,
                            style = Stroke(width = 10.dp.toPx()) // El stroke se dibuja hacia afuera desde el radio
                        )
                    }
            )
        }

        Icon(
            imageVector = if (isPaused) Icons.Default.Pause else Icons.Default.GpsFixed,
            contentDescription = "GPS Status",
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun HeroSpeedometer(
    speed: Double,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = "VELOCIDAD",
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            // El número gigante
            Text(
                text = LocationUtils.formatSpeed(speed).replace(" km/h", ""),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 96.sp, // Tamaño heroico
                    fontWeight = FontWeight.Black,
                    // "tnum" asegura que los números tengan el mismo ancho y no "bailen" al cambiar
                    fontFeatureSettings = "tnum",
                    lineHeight = 90.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            // La unidad más pequeña al lado
            ZipStatsText(
                text = "km/h",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 14.dp, start = 4.dp)
            )
        }
    }
}

@Composable
fun StatsGridCompact(
    distance: Double,
    duration: Long,
    gpsSignalStrength: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tarjeta Distancia
        StatCard(
            title = "Distancia",
            value = LocationUtils.formatDistance(distance),
            icon = Icons.Default.Route,
            modifier = Modifier.weight(1f)
        )
        
        // Tarjeta Tiempo
        StatCard(
            title = "Tiempo",
            value = formatDuration(duration),
            icon = Icons.Default.Timer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ControlActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            modifier = Modifier.size(64.dp)
        ) {
            Icon(icon, label, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        ZipStatsText(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PulsingGpsPauseButton(
    signalStrength: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Determinar el color del "Estado GPS" usando la misma función que los otros iconos GPS
    // Esto mantiene coherencia visual con GpsPreLocationIcon y GpsIconWithSignalRing
    val signalColor = getSignalColor(signalStrength)

    // 2. Animación del Halo (Onda expansiva)
    val infiniteTransition = rememberInfiniteTransition(label = "gps_pulse")
    
    // Escala: Crece hacia afuera
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, // Empieza pegado al borde del botón
        targetValue = 1.5f, // Se expande un 50% extra
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    
    // Opacidad: Se desvanece mientras crece
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    // Usamos Box para apilar el halo detrás del botón
    Box(
        contentAlignment = Alignment.Center, 
        modifier = modifier
    ) {
        // CAPA 1: EL HALO (Detrás)
        // Es un círculo del mismo color que el borde, que se expande
        Box(
            modifier = Modifier
                .matchParentSize()
                .scale(scale)
                .background(signalColor.copy(alpha = alpha), CircleShape)
        )

        // CAPA 2: EL BOTÓN DE PAUSA (Delante)
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface, // Fondo blanco/oscuro
                contentColor = signalColor // Icono y texto del color de la señal
            ),
            // 🔥 El borde sólido conecta visualmente con el halo (más ancho para mejor visibilidad)
            border = BorderStroke(8.dp, signalColor), 
            contentPadding = PaddingValues(0.dp) // Sin padding extra
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Pausar",
                    modifier = Modifier.size(32.dp),
                    tint = signalColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                ZipStatsText(
                    text = "PAUSAR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = signalColor
                )
            }
        }
    }
}

private fun getSignalColor(signalStrength: Float): Color {
    return when {
        signalStrength >= 80f -> Color(0xFF4CAF50)
        signalStrength >= 50f -> Color(0xFFFFEB3B)
        signalStrength >= 20f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}

@androidx.annotation.DrawableRes
private fun getWeatherIconResIdFromEmoji(emoji: String?, isDay: Boolean): Int {
    if (emoji.isNullOrBlank()) return R.drawable.help_outline
    return when (emoji) {
        // ☀️ Cielo Despejado
        "☀️" -> R.drawable.wb_sunny
        "🌙" -> R.drawable.nightlight

        // ⛅ Nubes Parciales
        "🌤️", "🌥️", "☁️🌙" -> if (isDay) R.drawable.partly_cloudy_day else R.drawable.partly_cloudy_night

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

/**
 * Helper para convertir grados a dirección (N, S, E, O...)
 */
@Composable
private fun convertWindDirectionToText(degrees: Int?): String {
    if (degrees == null) return "-"
    val directions = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    // Corrección para que 360/0 sea "N"
    val index = ((degrees.toFloat() + 22.5f) % 360 / 45.0f).toInt()
    return directions[index % 8]
}