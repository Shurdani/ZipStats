package com.zipstats.app.ui.profile

import android.Manifest
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.zipstats.app.R
import com.zipstats.app.model.VehicleType
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zipstats.app.model.Avatar
import com.zipstats.app.model.Avatars
import com.zipstats.app.ui.profile.components.AvatarSelectionDialog
import com.zipstats.app.navigation.Screen
import com.zipstats.app.ui.components.StandardDatePickerDialogWithValidation
import com.zipstats.app.ui.theme.ThemeMode
import com.zipstats.app.utils.DateUtils
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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

    // Mostrar mensajes en el Snackbar
    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Success) {
            val message = (uiState as ProfileUiState.Success).message
            if (message.isNotEmpty()) {
                scope.launch {
                    snackbarHostState.showSnackbar(message)
                }
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
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.handleEvent(ProfileEvent.UpdatePhoto(it)) }
    }
    
    // PermissionManager para verificar permisos (solo verificación, no solicitud)
    val permissionManager = remember { com.zipstats.app.permission.PermissionManager(context) }
    
    // Función para abrir configuración de permisos
    fun openAppSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
    
    
    // Observar cuando la cámara está lista y lanzarla
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
    
    // Abrir diálogo de vehículo si viene el parámetro
    LaunchedEffect(openAddVehicleDialog) {
        if (openAddVehicleDialog) {
            showAddScooterDialog = true
        }
    }

    // Diálogo de opciones de foto
        if (showPhotoOptionsDialog) {
            val currentState = uiState as? ProfileUiState.Success
            AlertDialog(
                onDismissRequest = { showPhotoOptionsDialog = false },
                title = { Text("Cambiar foto de perfil") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                showPhotoOptionsDialog = false
                                // Verificar permiso de almacenamiento (solo verificación)
                                if (permissionManager.hasStoragePermission()) {
                                    galleryLauncher.launch("image/*")
                                } else {
                                    Toast.makeText(context, "Permiso de almacenamiento requerido. Ve a Configuración > Aplicaciones > ZipStats > Permisos.", Toast.LENGTH_LONG).show()
                                    openAppSettings()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Image, "Galería")
                                Spacer(Modifier.width(8.dp))
                                Text("Seleccionar de la galería")
                            }
                        }
                        
                        TextButton(
                            onClick = {
                                showPhotoOptionsDialog = false
                                // Verificar permiso de cámara (solo verificación)
                                if (permissionManager.hasCameraPermission()) {
                                    // Si ya tiene permiso, preparar la cámara (se lanzará automáticamente)
                                    viewModel.prepareCamera(context)
                                } else {
                                    Toast.makeText(context, "Permiso de cámara requerido. Ve a Configuración > Aplicaciones > ZipStats > Permisos.", Toast.LENGTH_LONG).show()
                                    openAppSettings()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PhotoCamera, "Cámara")
                                Spacer(Modifier.width(8.dp))
                                Text("Tomar foto con la cámara")
                            }
                        }
                        
                        TextButton(
                            onClick = {
                                showAvatarDialog = true
                                showPhotoOptionsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Face, "Avatar")
                                Spacer(Modifier.width(8.dp))
                                Text("Elegir avatar")
                            }
                        }

                        currentState?.let { state ->
                            if (state.user.photoUrl != null || state.user.avatar != null) {
                                TextButton(
                                    onClick = {
                                        viewModel.handleEvent(ProfileEvent.RemovePhoto)
                                        showPhotoOptionsDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Start,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Delete, "Eliminar foto")
                                        Spacer(Modifier.width(8.dp))
                                        Text("Eliminar foto/avatar actual")
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    com.zipstats.app.ui.components.DialogCancelButton(
                        text = "Cancelar",
                        onClick = { showPhotoOptionsDialog = false }
                    )
                },
                shape = com.zipstats.app.ui.theme.DialogShape
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

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permiso necesario") },
            text = { Text("Para tomar fotos, necesitamos acceso a la cámara. ¿Deseas conceder el permiso?") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        openAppSettings()
                    }
                ) {
                    Text("Abrir configuración")
                }
            },
            dismissButton = {
                com.zipstats.app.ui.components.DialogCancelButton(
                    text = "Cancelar",
                    onClick = { showPermissionDialog = false }
                )
            },
            shape = com.zipstats.app.ui.theme.DialogShape
        )
    }

    if (showAddScooterDialog) {
        AddScooterDialog(
            onDismiss = { showAddScooterDialog = false },
            onConfirm = { nombre, marca, modelo, fechaCompra, vehicleType ->
                viewModel.addScooter(nombre, marca, modelo, fechaCompra, vehicleType)
                showAddScooterDialog = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate(Screen.AccountSettings.route)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = { showAddScooterDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Añadir vehículo"
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val isSmallScreen = screenWidthDp < 360
        val cardPadding = if (isSmallScreen) 8.dp else 16.dp
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
            ) {
                when (val state = uiState) {
                    is ProfileUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is ProfileUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Header con foto de perfil
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(cardPadding),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), // Sombra más sutil pero presente
                            shape = com.zipstats.app.ui.theme.CardShape
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(cardPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Foto de perfil
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onChangePhotoClick() }
                                ) {
                                    if (state.user.photoUrl != null) {
                                        val imageUri = viewModel.getImageUri(state.user.photoUrl)
                                        if (imageUri != null) {
                                            AsyncImage(
                                                model = imageUri,
                                                contentDescription = "Foto de perfil",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            // Fallback si la imagen local no existe
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Foto de perfil no encontrada",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(cardPadding),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else if (state.user.avatar != null) {
                                        Text(
                                            text = state.user.avatar,
                                            style = MaterialTheme.typography.headlineLarge,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .wrapContentSize(Alignment.Center)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Foto de perfil por defecto",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(cardPadding),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    // Botón de cambiar foto
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clickable { onChangePhotoClick() }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Cambiar foto",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(14.dp)
                                        )
                                    }
                                }

                                // Información del usuario
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = state.user.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = state.user.email,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f) // Mejor contraste
                                    )
                                }
                                
                                // Botón Exportar
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    IconButton(onClick = {
                                        android.util.Log.d("ProfileScreen", "Botón Exportar presionado desde tarjeta de usuario")
                                        android.util.Log.d("ProfileScreen", "Android SDK: ${Build.VERSION.SDK_INT}")
                                        
                                        // Para Android 10+ no necesitamos permisos explícitos para MediaStore
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            android.util.Log.d("ProfileScreen", "Android 10+: Exportando directamente con MediaStore")
                                            viewModel.exportAllRecords(context)
                                        } else {
                                            // Android 9 y anteriores - verificar permiso de almacenamiento
                                            val hasPermission = viewModel.checkStoragePermission(context)
                                            android.util.Log.d("ProfileScreen", "Tiene permiso: $hasPermission")
                                            
                                            if (hasPermission) {
                                                android.util.Log.d("ProfileScreen", "Exportando directamente...")
                                                viewModel.exportAllRecords(context)
                                            } else {
                                                android.util.Log.d("ProfileScreen", "Verificando permiso de almacenamiento...")
                                                // Solo verificar, no solicitar
                                                if (permissionManager.hasStoragePermission()) {
                                                    android.util.Log.d("ProfileScreen", "Permiso concedido, exportando...")
                                                    viewModel.exportAllRecords(context)
                                                } else {
                                                    android.util.Log.d("ProfileScreen", "Permiso denegado")
                                                    Toast.makeText(context, "Permiso de almacenamiento requerido. Ve a Configuración > Aplicaciones > ZipStats > Permisos.", Toast.LENGTH_LONG).show()
                                                    openAppSettings()
                                                }
                                            }
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = "Exportar registros",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "Exportar",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f) // Mejor contraste
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Tarjeta de Resumen
                        SummaryCard(
                            totalKm = state.scooters.sumOf { it.kilometrajeActual ?: 0.0 },
                            unlockedAchievements = state.unlockedAchievements,
                            totalAchievements = state.totalAchievements
                        )

        Spacer(modifier = Modifier.height(16.dp))

                        // Sección: Gestión de Mis Vehículos
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "GESTIÓN DE MIS VEHÍCULOS",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (state.scooters.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    shape = com.zipstats.app.ui.theme.SmallCardShape
                            ) {
                                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                                            .padding(24.dp), // Reducir padding para mejor respiración
                                        horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No tienes vehículos registrados",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f) // Mejor contraste
                                        )
                                    }
                                }
                                    } else {
                                        Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            state.scooters.forEach { scooter ->
                                        ScooterCardItem(
                                                    scooter = scooter,
                                            onClick = {
                                                navController.navigate("${Screen.ScooterDetail.route}/${scooter.id}")
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    is ProfileUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
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

@Composable
fun AvatarSelectionDialog(
    onDismiss: () -> Unit,
    onSelectAvatar: (Avatar) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar avatar") },
        shape = com.zipstats.app.ui.theme.DialogShape,
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                items(Avatars.list) { avatar ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSelectAvatar(avatar) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = avatar.emoji,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            com.zipstats.app.ui.components.DialogCancelButton(
                text = "Cancelar",
                onClick = onDismiss
            )
        }
    )
}

@Composable
fun SummaryCard(
    totalKm: Double,
    unlockedAchievements: Int,
    totalAchievements: Int
) {
        Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), // Sombra más sutil pero presente
        shape = com.zipstats.app.ui.theme.CardShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Resumen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryMetric(
                    value = String.format("%.1f", totalKm),
                    label = "KM Totales",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    value = "$unlockedAchievements/$totalAchievements",
                    label = "Logros",
                    icon = Icons.Default.EmojiEvents,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SummaryMetric(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
        ) {
            Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(48.dp)
                )
                Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
                Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) // Mejor contraste
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
fun ScooterCardItem(
    scooter: com.zipstats.app.model.Scooter,
    onClick: () -> Unit
) {
        Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = com.zipstats.app.ui.theme.SmallCardShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = getVehicleIcon(scooter.vehicleType),
                        contentDescription = "Icono del vehículo",
                        modifier = Modifier.size(24.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
                    )
                }
                
                    Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = scooter.nombre,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                        text = scooter.modelo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f) // Mejor contraste
                        )
                        Text(
                        text = "${String.format("%.1f", scooter.kilometrajeActual ?: 0.0)} km",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Ver detalles",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScooterDialog(
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
    
    // Validar que la fecha no sea futura
    LaunchedEffect(selectedDate) {
        fechaTexto = DateUtils.formatForDisplay(selectedDate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir vehículo") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selector de tipo de vehículo
                ExposedDropdownMenuBox(
                    expanded = vehicleTypeExpanded,
                    onExpandedChange = { vehicleTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedVehicleType.displayName,
                        onValueChange = { },
                        label = { Text("Tipo de vehículo") },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
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
                                        Text(type.displayName)
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
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = marca,
                    onValueChange = { marca = it },
                    label = { Text("Marca") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelo,
                    onValueChange = { modelo = it },
                    label = { Text("Modelo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fechaTexto,
                    onValueChange = { nuevoTexto ->
                        fechaTexto = nuevoTexto
                        try {
                            val fecha = DateUtils.parseDisplayDate(nuevoTexto)
                            if (fecha.isAfter(LocalDate.now())) {
                                selectedDate = LocalDate.now()
                                fechaTexto = DateUtils.formatForDisplay(selectedDate)
                            } else {
                                selectedDate = fecha
                                fechaError = null
                            }
                        } catch (e: Exception) {
                            fechaError = "Formato inválido. Use dd/MM/yyyy"
                        }
                    },
                    label = { Text("Fecha de compra") },
                    supportingText = fechaError?.let { { Text(it) } },
                    isError = fechaError != null,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            com.zipstats.app.ui.components.DialogConfirmButton(
                text = "Guardar",
                onClick = {
                    if (nombre.isNotBlank() && marca.isNotBlank() && modelo.isNotBlank() && fechaError == null) {
                        onConfirm(
                            nombre,
                            marca,
                            modelo,
                            fechaTexto,
                            selectedVehicleType
                        )
                    }
                },
                enabled = nombre.isNotBlank() && marca.isNotBlank() && modelo.isNotBlank() && fechaError == null
            )
        },
        dismissButton = {
            com.zipstats.app.ui.components.DialogCancelButton(
                text = "Cancelar",
                onClick = onDismiss
            )
        },
        shape = com.zipstats.app.ui.theme.DialogShape
    )

    if (showDatePicker) {
        StandardDatePickerDialogWithValidation(
            selectedDate = selectedDate,
            onDateSelected = { 
                selectedDate = it
                fechaError = null
            },
            onDismiss = { showDatePicker = false },
            title = "Seleccionar fecha de compra",
            maxDate = LocalDate.now(),
            validateDate = { date -> !date.isAfter(LocalDate.now()) }
        )
    }
}
