package com.zipstats.app.ui.profile

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import com.zipstats.app.ui.components.ZipStatsText
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zipstats.app.R
import com.zipstats.app.model.Avatar
import com.zipstats.app.model.Avatars
import com.zipstats.app.model.VehicleType
import com.zipstats.app.navigation.Screen
import com.zipstats.app.ui.components.AnimatedFloatingActionButton
import com.zipstats.app.ui.components.AnimatedIconButton
import com.zipstats.app.ui.components.DialogCancelButton
import com.zipstats.app.ui.components.DialogConfirmButton
import com.zipstats.app.ui.components.DialogOptionButton
import com.zipstats.app.ui.components.ExpandableCard
import com.zipstats.app.ui.components.StandardDatePickerDialogWithValidation
import com.zipstats.app.ui.profile.components.AvatarSelectionDialog
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.ui.theme.ThemeMode
import com.zipstats.app.utils.DateUtils
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    openAddVehicleDialog: Boolean = false
) {
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showAddScooterDialog by remember { mutableStateOf(openAddVehicleDialog) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Success) {
            val message = (uiState as ProfileUiState.Success).message
            if (message.isNotEmpty()) {
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.handleEvent(ProfileEvent.UpdatePhotoFromCamera)
        }
        viewModel.clearCameraReady()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.handleEvent(ProfileEvent.UpdatePhoto(it)) }
    }

    val permissionManager = remember { com.zipstats.app.permission.PermissionManager(context) }

    fun openAppSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    val cameraReady by viewModel.cameraReady.collectAsState()
    LaunchedEffect(cameraReady) {
        cameraReady?.let { uri ->
            try {
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Error al lanzar la cámara: ${e.message}")
            }
        }
    }

    fun onChangePhotoClick() {
        showPhotoOptionsDialog = true
    }

    LaunchedEffect(Unit) {
        viewModel.handleEvent(ProfileEvent.LoadUserProfile)
    }

    LaunchedEffect(openAddVehicleDialog) {
        if (openAddVehicleDialog) {
            showAddScooterDialog = true
        }
    }

    // --- DIÁLOGOS ---
    if (showPhotoOptionsDialog) {
        val currentState = uiState as? ProfileUiState.Success
        AlertDialog(
            onDismissRequest = { showPhotoOptionsDialog = false },
            title = { ZipStatsText("Cambiar foto de perfil") },
            shape = DialogShape,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DialogOptionButton(
                        text = "Seleccionar de la galería",
                        icon = Icons.Default.Image,
                        onClick = {
                            showPhotoOptionsDialog = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    DialogOptionButton(
                        text = "Tomar foto con la cámara",
                        icon = Icons.Default.PhotoCamera,
                        onClick = {
                            showPhotoOptionsDialog = false
                            if (permissionManager.hasCameraPermission()) {
                                viewModel.prepareCamera(context)
                            } else {
                                Toast.makeText(context, "Permiso de cámara requerido.", Toast.LENGTH_LONG).show()
                                openAppSettings()
                            }
                        }
                    )
                    DialogOptionButton(
                        text = "Elegir avatar",
                        icon = Icons.Default.Face,
                        onClick = {
                            showAvatarDialog = true
                            showPhotoOptionsDialog = false
                        }
                    )
                    currentState?.let { state ->
                        if (state.user.photoUrl != null || state.user.avatar != null) {
                            DialogOptionButton(
                                text = "Eliminar foto/avatar actual",
                                icon = Icons.Default.Delete,
                                onClick = {
                                    viewModel.handleEvent(ProfileEvent.RemovePhoto)
                                    showPhotoOptionsDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                DialogCancelButton(text = "Cancelar", onClick = { showPhotoOptionsDialog = false })
            }
        )
    }

    if (showAvatarDialog && uiState is ProfileUiState.Success) {
        AvatarSelectionDialog(
            onDismiss = { showAvatarDialog = false },
            onAvatarSelected = { avatar ->
                viewModel.handleEvent(ProfileEvent.SelectAvatar(avatar))
                showAvatarDialog = false
            },
            currentAvatar = (uiState as ProfileUiState.Success).user.avatar?.let { avatarEmoji ->
                Avatars.list.find { it.emoji == avatarEmoji }
            }
        )
    }

    val addScooterSheetState = rememberModalBottomSheetState()

    if (showAddScooterDialog) {
        ModalBottomSheet(
            onDismissRequest = { showAddScooterDialog = false },
            sheetState = addScooterSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            AddScooterBottomSheet(
                onDismiss = {
                    scope.launch {
                        addScooterSheetState.hide()
                        showAddScooterDialog = false
                    }
                },
                onConfirm = { nombre, marca, modelo, fechaCompra, vehicleType ->
                    viewModel.addScooter(nombre, marca, modelo, fechaCompra, vehicleType)
                    scope.launch {
                        addScooterSheetState.hide()
                        showAddScooterDialog = false
                    }
                }
            )
        }
    }

    // --- CONTENIDO PRINCIPAL ---
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    ZipStatsText(
                        text = "Perfil",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    ) 
                },
                actions = {
                    AnimatedIconButton(onClick = { navController.navigate(Screen.AccountSettings.route) }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            AnimatedFloatingActionButton(
                onClick = { showAddScooterDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Añadir vehículo")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is ProfileUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ProfileUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // 1. SECCIÓN DE USUARIO (Perfil Rediseñado)
                        UserProfileSection(
                            user = state.user,
                            onChangePhotoClick = { onChangePhotoClick() },
                            onExportClick = { viewModel.exportAllRecords(context) }
                        )

                        // 2. TARJETAS DE RESUMEN (KMs y Logros)
                        // Usamos Row para ponerlas lado a lado si hay espacio, o una debajo de otra
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Tarjeta KM (clickable - lleva a Estadísticas)
                            StatSummaryCard(
                                title = "Distancia Total",
                                value = formatNumberWithCommas(state.scooters.sumOf { it.kilometrajeActual ?: 0.0 }),
                                unit = "en KM",
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = { navController.navigate(Screen.Statistics.route) },
                                modifier = Modifier.weight(1f)
                            )

                            // Tarjeta Logros (clickable - lleva a Logros)
                            StatSummaryCard(
                                title = "Logros",
                                value = "${state.unlockedAchievements}/${state.totalAchievements}",
                                unit = "desbloqueados",
                                icon = Icons.Default.EmojiEvents,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                onClick = { navController.navigate(Screen.Achievements.route) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 3. SECCIÓN VEHÍCULOS
                        Column {
                            ZipStatsText(
                                text = "Mis Vehículos",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (state.scooters.isEmpty()) {
                                EmptyStateVehicleCard()
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    state.scooters
                                        .sortedByDescending { it.kilometrajeActual ?: 0.0 }
                                        .forEach { scooter ->
                                            ScooterCardItem(
                                                scooter = scooter,
                                                onClick = { navController.navigate("${Screen.ScooterDetail.route}/${scooter.id}") }
                                            )
                                        }
                                }
                            }
                        }

                        // Espacio extra para el FAB
                        Spacer(modifier = Modifier.height(64.dp))
                    }
                }
                is ProfileUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ZipStatsText(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------
// COMPONENTES UI REDISEÑADOS
// ----------------------------------------------------------------

@Composable
fun UserProfileSection(
    user: com.zipstats.app.model.User,
    onChangePhotoClick: () -> Unit,
    onExportClick: () -> Unit
) {
    val photoClickInteractionSource = remember { MutableInteractionSource() }
    val cameraClickInteractionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Foto con borde y botón de edición
        Box {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(
                        interactionSource = photoClickInteractionSource,
                        indication = null,
                        onClick = { onChangePhotoClick() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (user.photoUrl != null) {
                    AsyncImage(
                        model = user.photoUrl, // Coil se encarga de cargar URIs locales o remotas
                        contentDescription = "Foto de perfil",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (user.avatar != null) {
                    ZipStatsText(
                        text = user.avatar,
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.wrapContentSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Botón mini de cámara
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = cameraClickInteractionSource,
                        indication = null,
                        onClick = { onChangePhotoClick() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Editar foto",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ZipStatsText(
            text = user.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        ZipStatsText(
            text = user.email,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Botón de Exportar Datos (Estilo Chip/Botón pequeño)
        OutlinedButton(
            onClick = onExportClick,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            ZipStatsText("Exportar Datos")
        }
    }
}

@Composable
fun StatSummaryCard(
    title: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // AUTO-FORMATO DE NÚMEROS (formato español)
                val formattedValue = try {
                    // Intenta parsear si es un número simple para poner formato español
                    val number = value.toDoubleOrNull()
                    if (number != null) {
                        // Formatear con Locale español: punto para miles, coma para decimales
                        val formatter = java.text.DecimalFormat("#,##0.0", java.text.DecimalFormatSymbols(java.util.Locale("es", "ES")))
                        val formatted = formatter.format(number)
                        formatted.removeSuffix(",0") // Quitar decimal si es ,0
                    } else value
                } catch (e: Exception) { value }

                ZipStatsText(
                    text = formattedValue, // Usamos el valor formateado
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    autoResize = true // Ajusta el tamaño automáticamente para que no se corte
                )
                ZipStatsText(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 1
                )
                ZipStatsText(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ScooterCardItem(
    scooter: com.zipstats.app.model.Scooter,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface // Fondo blanco
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) // Borde sutil
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), // Sombra suave
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono del vehículo en contenedor de color
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = getVehicleIcon(scooter.vehicleType),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                ZipStatsText(
                    text = scooter.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                ZipStatsText(
                    text = scooter.modelo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                ZipStatsText(
                    text = "${formatNumberWithCommas(scooter.kilometrajeActual ?: 0.0)} km",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun EmptyStateVehicleCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ZipStatsText(
                text = "No tienes vehículos registrados",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- COMPONENTES AUXILIARES (DIÁLOGOS) ---

@Composable
fun AvatarSelectionDialog(
    onDismiss: () -> Unit,
    onSelectAvatar: (Avatar) -> Unit,
    currentAvatar: Avatar? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { ZipStatsText("Seleccionar avatar") },
        shape = DialogShape,
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(300.dp)
            ) {
                items(Avatars.list) { avatar ->
                    val isSelected = avatar.emoji == currentAvatar?.emoji
                    val avatarClickInteractionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable(
                                interactionSource = avatarClickInteractionSource,
                                indication = null,
                                onClick = { onSelectAvatar(avatar) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        ZipStatsText(
                            text = avatar.emoji,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogCancelButton(text = "Cancelar", onClick = onDismiss)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScooterBottomSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, VehicleType) -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var marca by remember { mutableStateOf("") }
    var modelo by remember { mutableStateOf("") }
    var selectedVehicleType by remember { mutableStateOf(VehicleType.PATINETE) }
    var vehicleTypeExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var fechaTexto by remember { mutableStateOf(DateUtils.formatForDisplay(selectedDate)) }
    var fechaError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedDate) {
        fechaTexto = DateUtils.formatForDisplay(selectedDate)
    }

    if (showDatePicker) {
        StandardDatePickerDialogWithValidation(
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it },
            onDismiss = { showDatePicker = false },
            title = "Seleccionar fecha",
            maxDate = LocalDate.now(),
            validateDate = { !it.isAfter(LocalDate.now()) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ZipStatsText(
            text = "Añadir vehículo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        ExposedDropdownMenuBox(
            expanded = vehicleTypeExpanded,
            onExpandedChange = { vehicleTypeExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedVehicleType.displayName,
                onValueChange = { },
                label = { ZipStatsText("Tipo de vehículo") },
                readOnly = true,
                leadingIcon = {
                    Image(
                        painter = getVehicleIcon(selectedVehicleType),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleTypeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(
                    type = MenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
            )
            ExposedDropdownMenu(
                expanded = vehicleTypeExpanded,
                onDismissRequest = { vehicleTypeExpanded = false }
            ) {
                VehicleType.values().forEach { type ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Image(
                                    painter = getVehicleIcon(type),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                                )
                                ZipStatsText(type.displayName)
                            }
                        },
                        onClick = {
                            selectedVehicleType = type
                            vehicleTypeExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { ZipStatsText("Nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = marca,
            onValueChange = { marca = it },
            label = { ZipStatsText("Marca") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = modelo,
            onValueChange = { modelo = it },
            label = { ZipStatsText("Modelo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = fechaTexto,
            onValueChange = { },
            label = { ZipStatsText("Fecha de compra") },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth()
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
                onClick = {
                    onConfirm(nombre, marca, modelo, fechaTexto, selectedVehicleType)
                },
                modifier = Modifier.weight(1f),
                enabled = nombre.isNotBlank() && marca.isNotBlank() && modelo.isNotBlank()
            ) {
                ZipStatsText("Guardar")
            }
        }
    }
}

/**
 * Helper para formatear números con separadores de miles en formato español
 * Formato español: punto (.) para miles, coma (,) para decimales
 * Ejemplo: 23.525,25
 */
private fun formatNumberWithCommas(value: Double): String {
    return try {
        // Formatear con Locale español para obtener el formato correcto
        val formatter = java.text.DecimalFormat("#,##0.0", java.text.DecimalFormatSymbols(java.util.Locale("es", "ES")))
        val formatted = formatter.format(value)
        formatted.removeSuffix(",0") // Quitar decimal si es ,0
    } catch (e: Exception) {
        // Fallback: formatear manualmente
        val parts = String.format("%.1f", value).split(".")
        if (parts.size == 2) {
            val integerPart = parts[0].reversed().chunked(3).joinToString(".").reversed()
            "$integerPart,${parts[1]}"
        } else {
            parts[0].reversed().chunked(3).joinToString(".").reversed()
        }
    }
}

/**
 * Helper para obtener icono de vehículo (reutilizado)
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