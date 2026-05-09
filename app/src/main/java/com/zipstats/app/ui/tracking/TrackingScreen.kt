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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zipstats.app.R
import com.zipstats.app.di.AppOverlayRepositoryEntryPoint
import com.zipstats.app.model.Scooter
import com.zipstats.app.model.SurfaceConditionType
import com.zipstats.app.model.VehicleType
import com.zipstats.app.permission.PermissionManager
import com.zipstats.app.repository.AppOverlayRepository
import com.zipstats.app.repository.SettingsRepository
import com.zipstats.app.ui.components.AnimatedFloatingActionButton
import com.zipstats.app.ui.components.DialogCancelButton
import com.zipstats.app.ui.components.DialogContentText
import com.zipstats.app.ui.components.DialogDeleteButton
import com.zipstats.app.ui.components.DialogTitleText
import com.zipstats.app.ui.components.ZipStatsText
import com.zipstats.app.ui.shared.AppOverlayState
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.utils.LocationUtils
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    val vehiclesLoaded by viewModel.vehiclesLoaded.collectAsState()
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
    var surfaceConditionType by remember { mutableStateOf(SurfaceConditionType.NONE) }
    var shouldAskSurfaceQuestions by remember { mutableStateOf(false) }

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
    
    // Mantener pre-GPS idempotente; evitamos reiniciar hardware GPS innecesariamente.
    LaunchedEffect(hasAllRequiredPermissions, trackingState) {
        if (hasAllRequiredPermissions && trackingState is TrackingState.Idle) {
            viewModel.startPreLocationTracking()
        }
    }

    // Al volver a la pantalla, mostramos unos instantes el estado de "búsqueda"
    // para que el texto no reaparezca directamente en "GPS listo".
    val lifecycleOwner = LocalLifecycleOwner.current
    var showPreGpsIntroText by rememberSaveable { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner, trackingState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && trackingState is TrackingState.Idle) {
                showPreGpsIntroText = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(showPreGpsIntroText, trackingState) {
        if (showPreGpsIntroText && trackingState is TrackingState.Idle) {
            delay(1200)
            showPreGpsIntroText = false
        }
    }

    val effectiveGpsPreLocationState =
        if (showPreGpsIntroText && trackingState is TrackingState.Idle) {
            TrackingViewModel.GpsPreLocationState.Searching
        } else {
            gpsPreLocationState
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
        // 1. Capturamos el valor actual en una constante local
        val currentMessage = message

        // 2. Ahora sí podemos hacer el smart cast con la constante
        if (currentMessage != null && currentMessage.contains("Ruta guardada") &&
            overlay is AppOverlayState.None && !routeSaved) {

            routeSaved = true
            viewModel.stopPreLocationTracking()
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
                        text = "Seguimiento GPS",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
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
                trackingState is TrackingState.Idle && !vehiclesLoaded -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                trackingState is TrackingState.Idle && hasAllRequiredPermissions -> {
                    IdleStateContent(
                        selectedScooter = selectedScooter,
                        scooters = scooters,
                        gpsPreLocationState = effectiveGpsPreLocationState,
                        hasValidGpsSignal = hasValidGpsSignal,
                        weatherStatus = weatherStatus,
                        shouldShowRainWarning = shouldShowRainWarning,
                        isActiveRainWarning = isActiveRainWarning,
                        shouldShowExtremeWarning = shouldShowExtremeWarning,
                        isTracking = isTracking,
                        onScooterClick = {
                            if (vehiclesLoaded && scooters.isNotEmpty()) {
                                showScooterPicker = true
                            }
                        },
                        onStartTracking = { viewModel.startTracking() },
                        onDismissRainWarning = { viewModel.dismissRainWarning() },
                        onDismissExtremeWarning = { viewModel.dismissExtremeWarning() },
                        onFetchWeather = { viewModel.fetchWeatherManually() }
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
                        shouldShowRainWarning = shouldShowRainWarning,
                        isActiveRainWarning = isActiveRainWarning,
                        shouldShowExtremeWarning = shouldShowExtremeWarning,
                        onPause = { viewModel.pauseTracking() },
                        onResume = { viewModel.resumeTracking() },
                        onFinish = {
                            surfaceConditionType = viewModel.getSurfaceConditionTypeForConfirmation()
                            shouldAskSurfaceQuestions = viewModel.shouldAskSurfaceConditionQuestionsOnFinish()
                            showFinishDialog = true
                        },
                        onCancel = { showCancelDialog = true },
                        onFetchWeather = { viewModel.fetchWeatherManually() }
                    )
                }
            }
        }
    }

    if (showScooterPicker && scooters.isNotEmpty()) {
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
                surfaceConditionType = surfaceConditionType,
                shouldAskSurfaceQuestions = shouldAskSurfaceQuestions,
                onConfirm = { notes, addToRecords, selectedSurfaceConditionType ->
                    // Activar flag de cierre ANTES de cualquier estado
                    // Esto congela la UI y evita cualquier flash
                    isClosing = true
                    viewModel.finishTracking(
                        notes = notes,
                        addToRecords = addToRecords,
                        surfaceConditionType = selectedSurfaceConditionType,
                        isSurfaceConditionConfirmed = selectedSurfaceConditionType != SurfaceConditionType.NONE
                    )
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

/**
 * Centro de Notificaciones Unificado - Estilo simple como RouteDetailDialog
 * Prioridad: Lluvia/Calzada humeda > Condiciones extremas
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
    
    // Detectar factores extremos (igual que RouteDetailDialog)
    val extremeFactors = remember(shouldShowExtremeWarning, weatherStatus) {
        if (!shouldShowExtremeWarning || weatherStatus !is WeatherStatus.Success) {
            emptyList()
        } else {
            val factors = mutableListOf<String>()
            val windSpeedKmh = weatherStatus.windSpeed ?: 0.0
            val windGustsKmh = weatherStatus.windGusts ?: 0.0
            val temperature = weatherStatus.temperature
            val uvIndex = weatherStatus.uvIndex
            val isDay = weatherStatus.isDay
            
            if (windSpeedKmh > 40) factors.add("Viento intenso")
            if (windGustsKmh > 60) factors.add("Ráfagas")
            if (temperature < 0) factors.add("Helada")
            if (temperature > 35) factors.add("Calor intenso")
            if (isDay && uvIndex != null && uvIndex >= 8) factors.add("Radiación UV alta")
            
            val isStorm = weatherStatus.weatherEmoji?.let { emoji ->
                emoji.contains("⛈") || emoji.contains("⚡")
            } ?: false
            val isStormByDescription = weatherStatus.description?.let { desc ->
                desc.contains("Tormenta", ignoreCase = true) ||
                desc.contains("granizo", ignoreCase = true) ||
                desc.contains("rayo", ignoreCase = true)
            } ?: false
            if (isStorm || isStormByDescription) factors.add("Tormenta")
            
            val isSnow = weatherStatus.weatherEmoji?.let { emoji ->
                emoji.contains("❄️")
            } ?: false
            val isSnowByDescription = weatherStatus.description?.let { desc ->
                desc.contains("Nieve", ignoreCase = true) ||
                desc.contains("nevada", ignoreCase = true) ||
                desc.contains("snow", ignoreCase = true)
            } ?: false
            val isSnowByCode = weatherStatus.weatherCode?.let { code ->
                code in listOf(71, 73, 75, 77, 85, 86)
            } ?: false
            if (isSnow || isSnowByDescription || isSnowByCode) factors.add("Nieve")
            
            factors
        }
    }
    
    // Texto del badge de clima extremo según número de factores (igual que RouteDetailDialog)
    val extremeBadgeText = remember(extremeFactors) {
        when {
            extremeFactors.isEmpty() -> null
            extremeFactors.size == 1 -> "⚠️ ${extremeFactors.first()}"
            else -> "⚠️ Clima extremo"
        }
    }
    
    // Construir la lista de badges (estilo simple como RouteDetailDialog)
    val badges = remember(shouldShowRainWarning, isActiveRainWarning, extremeBadgeText) {
        mutableListOf<String>().apply {
            if (shouldShowRainWarning) {
                if (isActiveRainWarning) {
                    add("🔵 Lluvia")
                } else {
                    add("🟡 Calzada húmeda")
                }
            }
            if (extremeBadgeText != null) {
                add(extremeBadgeText)
            }
        }
    }
    
    val badgeCount = badges.size
    
    if (badgeCount == 0 || !shouldShow) return

    // Renderizado - Estilo simple como RouteDetailDialog
    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)) {
            if (badgeCount >= 2) {
                // Si hay 2 o más badges, agruparlos en una sola tarjeta
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        badges.forEachIndexed { index, badgeText ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                    thickness = 0.5.dp
                                )
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                ZipStatsText(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        lineHeight = 18.sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            } else {
                // Si hay solo 1 badge, mostrar tarjeta individual
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    ZipStatsText(
                        text = badges.first(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            lineHeight = 18.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            ZipStatsText(
                text = "Permisos requeridos",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            ZipStatsText(
                text = "Para grabar rutas GPS, necesitamos:\n• Acceso a tu ubicación\n• Mostrar notificación persistente\n\nVe a Configuración > Aplicaciones > ZipStats > Permisos para activarlos.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermissions,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                ZipStatsText("Abrir configuración", color = MaterialTheme.colorScheme.onPrimary)
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
    onDismissExtremeWarning: () -> Unit,
    onFetchWeather: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icono GPS con pulso sutil si está listo
        GpsPreLocationIcon(
            gpsPreLocationState = gpsPreLocationState,
            modifier = Modifier.size(140.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Texto condicional según el estado del GPS y tipo de vehículo
        val vehicleTypeName = selectedScooter?.vehicleType?.name ?: "PATINETE"
        val titleText = remember(gpsPreLocationState, vehicleTypeName) {
            getHumorousGpsTitle(gpsPreLocationState, vehicleTypeName)
        }
        
        ZipStatsText(
            text = titleText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = Int.MAX_VALUE
        )

        Spacer(modifier = Modifier.height(8.dp))

        GpsPreLocationStatusText(gpsPreLocationState = gpsPreLocationState)

        Spacer(modifier = Modifier.height(32.dp))

        // Selector de vehículo estilo tarjeta limpia
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onScooterClick,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface // Color más visible cuando está deshabilitado
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (selectedScooter != null && hasValidGpsSignal) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            ZipStatsText(
                text = if (hasValidGpsSignal) "Iniciar seguimiento" else "Buscando señal GPS...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selectedScooter != null && hasValidGpsSignal) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
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
    shouldShowRainWarning: Boolean,
    isActiveRainWarning: Boolean,
    shouldShowExtremeWarning: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onFetchWeather: () -> Unit = {}
) {
    val isPaused = trackingState is TrackingState.Paused
    val isSaving = trackingState is TrackingState.Saving
    val isTrackingActive = trackingState is TrackingState.Tracking || trackingState is TrackingState.Paused || trackingState is TrackingState.Saving

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
            shouldShowRainWarning = shouldShowRainWarning,
            isActiveRainWarning = isActiveRainWarning,
            shouldShowExtremeWarning = shouldShowExtremeWarning,
            isTracking = isTrackingActive,
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
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                ZipStatsText(
                    "Guardando ruta...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            AnimatedContent(
                targetState = isPaused,
                label = "controls"
            ) { paused ->
                if (paused) {
                    // --- MODO PAUSADO (Gestión) ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
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
                        AnimatedFloatingActionButton(
                            onClick = onResume,
                            containerColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(88.dp)
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
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

// --- WEATHER CARD LOCAL (Para Tracking) ---
@Composable
fun TrackingWeatherCard(
    weatherStatus: WeatherStatus,
    shouldShowRainWarning: Boolean = false,
    isActiveRainWarning: Boolean = false,
    shouldShowExtremeWarning: Boolean = false,
    isTracking: Boolean = false,
    onFetchWeatherClick: () -> Unit
) {
    val weatherClickInteractionSource = remember { MutableInteractionSource() }
    // Los iconos de preaviso solo se muestran DURANTE el tracking activo
    // El estado se guarda en el ViewModel (como el clima), así que se restaura automáticamente
    val preWarningEmojiText = remember(shouldShowRainWarning, isActiveRainWarning, shouldShowExtremeWarning, isTracking) {
        if (!isTracking) {
            null
        } else {
            buildString {
                // IMPORTANTE: Si hay 2 badges, el de condiciones extremas debe ir primero (izquierda).
                // Badge complementario: condiciones extremas (puede coexistir)
                if (shouldShowExtremeWarning) {
                    append("⚠️")
                }
                // Badge principal (lluvia o calzada humeda). Son excluyentes por lógica de ViewModel.
                if (shouldShowRainWarning) {
                    append(if (isActiveRainWarning) "🔵" else "🟡")
                }
            }.ifBlank { null }
        }
    }
    AnimatedVisibility(
        visible = weatherStatus !is WeatherStatus.Idle,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = weatherClickInteractionSource,
                    indication = null,
                    onClick = onFetchWeatherClick
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
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
                        if (preWarningEmojiText != null) {
                            // Importante: usar un único "slot" para evitar que el Row se quede sin espacio
                            // y termine recortando uno de los iconos cuando hay 2 badges activos.
                            ZipStatsText(
                                text = preWarningEmojiText,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        // Icono basado estrictamente en el `condition` de Google (sin inferencias).
                        val iconResId = remember(weatherStatus.icon, weatherStatus.isDay) {
                            com.zipstats.app.repository.WeatherRepository.getIconResIdForCondition(
                                weatherStatus.icon,
                                weatherStatus.isDay
                            )
                        }
                        Image(
                            painter = painterResource(id = iconResId),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        ZipStatsText(
                            text = "${formatTemperature(weatherStatus.temperature, decimals = 0)}°C",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        // --- CORRECCIÓN: Mostrar dirección del viento ---
                        Icon(Icons.Default.Air, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))

                        val direction = convertWindDirectionToText(weatherStatus.windDirection)
                        // WeatherRepository ya entrega el viento en km/h
                        val windSpeedKmh = weatherStatus.windSpeed ?: 0.0

                        ZipStatsText(
                            text = "${com.zipstats.app.utils.LocationUtils.formatNumberSpanish(windSpeedKmh, 0)} km/h ($direction)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
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
        title = {
            DialogTitleText(
                text = "Seleccionar vehículo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                items(scooters) { scooter ->
                    val isSelected = scooter.id == selectedScooter?.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { onScooterSelected(scooter) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(14.dp)
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
                            Column(modifier = Modifier.weight(1f)) {
                                ZipStatsText(
                                    text = scooter.nombre,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                ZipStatsText(
                                    text = scooter.modelo,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
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
    surfaceConditionType: SurfaceConditionType,
    shouldAskSurfaceQuestions: Boolean,
    onConfirm: (String, Boolean, SurfaceConditionType) -> Unit,
    onDismiss: () -> Unit
) {
    var notes by remember { mutableStateOf("") }
    var addToRecords by remember { mutableStateOf(false) }
    var didRain by remember(surfaceConditionType, shouldAskSurfaceQuestions) {
        mutableStateOf(surfaceConditionType == SurfaceConditionType.RAIN)
    }
    var wasWetRoad by remember(surfaceConditionType, shouldAskSurfaceQuestions) {
        mutableStateOf(surfaceConditionType == SurfaceConditionType.WET_ROAD)
    }

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
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        ZipStatsText(
            text = "¿Deseas guardar esta ruta?",
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        ZipStatsText(
            text = "Distancia: ${LocationUtils.formatDistance(distance)}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        ZipStatsText(
            text = "Duración: ${formatDuration(duration)}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        if (shouldAskSurfaceQuestions) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZipStatsText(
                        text = "Condiciones del asfalto",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ZipStatsText(
                        text = "¿Llovió durante la ruta?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                didRain = true
                                wasWetRoad = false
                            }
                        ) {
                            RadioButton(
                                selected = didRain,
                                onClick = {
                                    didRain = true
                                    wasWetRoad = false
                                }
                            )
                            ZipStatsText("Sí")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { didRain = false }
                        ) {
                            RadioButton(
                                selected = !didRain,
                                onClick = { didRain = false }
                            )
                            ZipStatsText("No")
                        }
                    }

                    if (!didRain) {
                        ZipStatsText(
                            text = "¿Estaba húmedo el suelo?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { wasWetRoad = true }
                            ) {
                                RadioButton(
                                    selected = wasWetRoad,
                                    onClick = { wasWetRoad = true }
                                )
                                ZipStatsText("Sí")
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { wasWetRoad = false }
                            ) {
                                RadioButton(
                                    selected = !wasWetRoad,
                                    onClick = { wasWetRoad = false }
                                )
                                ZipStatsText("No")
                            }
                        }
                    }
                }
            }
        }

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
                text = "Añadir ${com.zipstats.app.utils.LocationUtils.formatNumberSpanish(distance)} km a registros",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { ZipStatsText("Título (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        // Botones de acción con jerarquía M3: secundario tonal + primario
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                ZipStatsText(
                    text = "Cancelar",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Button(
                onClick = {
                    val selectedSurfaceConditionType = when {
                        !shouldAskSurfaceQuestions -> surfaceConditionType
                        didRain -> SurfaceConditionType.RAIN
                        wasWetRoad -> SurfaceConditionType.WET_ROAD
                        else -> SurfaceConditionType.NONE
                    }
                    onConfirm(notes, addToRecords, selectedSurfaceConditionType)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                ZipStatsText(
                    text = "Guardar",
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
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
        title = { DialogTitleText("Cancelar seguimiento") },
        text = { DialogContentText("¿Estás seguro? Se perderán todos los datos de la ruta.", maxLines = 4) },
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
/**
 * Formatea la temperatura y evita el "-0" o "-0.0"
 */
private fun formatTemperature(temperature: Double, decimals: Int = 1): String {
    // 1. Obtenemos el valor absoluto para formatear el número "limpio"
    val absTemp = kotlin.math.abs(temperature)
    
    // 2. Usamos tu utilidad para formatear (ej: "0,0" o "17,5")
    val formatted = com.zipstats.app.utils.LocationUtils.formatNumberSpanish(absTemp, decimals)

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
        ZipStatsText(
            text = "VELOCIDAD",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            // El número gigante - usa autoResize para que se ajuste si no cabe
            ZipStatsText(
                text = LocationUtils.formatSpeed(speed).replace(" km/h", ""),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 96.sp, // Tamaño heroico
                    fontWeight = FontWeight.Black,
                    // "tnum" asegura que los números tengan el mismo ancho y no "bailen" al cambiar
                    fontFeatureSettings = "tnum",
                    lineHeight = 90.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                autoResize = true // 🔥 Reduce tamaño si no cabe
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

/**
 * Genera un título humorístico según el estado del GPS y el tipo de vehículo
 * Trata bicicleta y e-bike como si fueran el mismo
 */
private fun getHumorousGpsTitle(
    state: TrackingViewModel.GpsPreLocationState,
    vehicleType: String? // Pasa aquí el tipo: "PATINETE", "E_BIKE", "BICICLETA", "MONOCICLO"
): String {
    // Normalizamos para evitar problemas de mayúsculas/minúsculas
    val type = vehicleType?.uppercase() ?: ""

    // Tratamos bicicleta y e-bike como si fueran el mismo
    val isBike = type.contains("BIKE") || type.contains("BICICLETA") || type == "E_BIKE"
    val isScooter = type.contains("PATINETE") || type.contains("SCOOTER")
    val isUnicycle = type.contains("MONOCICLO") || type.contains("UNICYCLE")

    return when (state) {
        is TrackingViewModel.GpsPreLocationState.Searching -> {
            // Frases base para todos
            val phrases = mutableListOf(
                "Sobornando a los satélites...",
                "Triangulando tu posición...",
                "Preguntando a la NASA...",
                "Calibrando sensores..."
            )

            // Frases específicas según vehículo
            when {
                isBike -> phrases.addAll(listOf(
                    "Engrasando la cadena digital...",
                    "Inflando los píxeles de las ruedas...",
                    "Buscando el maillot amarillo...",
                    "Ajustando el sillín virtual...",
                    "Calculando ruta sin cuestas..."
                ))
                isScooter -> phrases.addAll(listOf(
                    "Cargando iones de litio...",
                    "Desplegando el mástil...",
                    "Buscando carril bici...",
                    "Activando modo Sport...",
                    "Revisando presión de neumáticos..."
                ))
                isUnicycle -> phrases.addAll(listOf(
                    "Calibrando giroscopios...",
                    "Buscando el equilibrio perfecto...",
                    "Una rueda para dominarlos a todos...",
                    "Activando auto-balanceo...",
                    "Cargando software de circo..." // Un toque de humor
                ))
            }

            phrases.random()
        }

        is TrackingViewModel.GpsPreLocationState.Found -> {
            // Estado intermedio: hay señal, pero aún no está fina o no se ha iniciado.
            when {
                isBike -> "Señal detectada. Ajustando ruta para tu bici..."
                isScooter -> "Señal detectada. Preparando despegue..."
                isUnicycle -> "Señal detectada. Afinando el equilibrio..."
                else -> "Señal detectada. Ajustando precisión..."
            }
        }

        is TrackingViewModel.GpsPreLocationState.Ready -> {
            // Importante: no mostrar mensaje de éxito final aquí; aún no se ha pulsado iniciar.
            when {
                isBike -> "GPS listo. Pulsa \"Iniciar seguimiento\" para pedalear"
                isScooter -> "GPS listo. Pulsa \"Iniciar seguimiento\" para arrancar"
                isUnicycle -> "GPS listo. Pulsa \"Iniciar seguimiento\" para rodar"
                else -> "GPS listo. Pulsa \"Iniciar seguimiento\""
            }
        }
    }
}