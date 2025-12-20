package com.zipstats.app.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.navigation.Screen
import com.zipstats.app.permission.PermissionManager
import com.zipstats.app.repository.SettingsRepository
import com.zipstats.app.ui.components.DialogDeleteButton
import com.zipstats.app.ui.components.DialogNeutralButton
import com.zipstats.app.ui.components.DialogSaveButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AccountSettingsScreen(
    navController: NavController,
    currentThemeMode: com.zipstats.app.ui.theme.ThemeMode,
    onThemeModeChange: (com.zipstats.app.ui.theme.ThemeMode) -> Unit,
    currentColorTheme: com.zipstats.app.ui.theme.ColorTheme = com.zipstats.app.ui.theme.ColorTheme.RIDE_BLUE,
    onColorThemeChange: (com.zipstats.app.ui.theme.ColorTheme) -> Unit = {},
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    pureBlackOledEnabled: Boolean,
    onPureBlackOledChange: (Boolean) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: com.zipstats.app.ui.auth.AuthViewModel = hiltViewModel()
) {

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val settingsRepository = remember { SettingsRepository(context) }
    val permissionManager = remember { PermissionManager(context) }
    val keepScreenOnDuringTracking by settingsRepository.keepScreenOnDuringTrackingFlow.collectAsState(initial = false)
    val composeScope = rememberCoroutineScope()

    var isPaletteExpanded by remember { mutableStateOf(false) }
    var isPermissionsExpanded by remember { mutableStateOf(false) }
    var permissionStates by remember { mutableStateOf(permissionManager.getPermissionStates()) }
    val allPermissions = remember { permissionManager.getAllPermissions() }

    LaunchedEffect(Unit) {
        permissionStates = permissionManager.getPermissionStates()
    }

    // En ProfileScreen.kt o AccountSettingsScreen.kt

    fun restartApp(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)

        if (intent != null) {
            // Estos flags son la clave:
            // NEW_TASK: Empieza una tarea nueva.
            // CLEAR_TASK: Borra TODA la historia anterior (mata las activities viejas).
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

            context.startActivity(intent)

            // ❌ ELIMINADO: Runtime.getRuntime().exit(0)
            // No matamos el proceso a lo bruto, dejamos que el sistema limpie.
        }
    }
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()

    // --- DIÁLOGOS ---
    if (showEditNameDialog) {
        val currentState = uiState as? ProfileUiState.Success
        currentState?.let { state ->
            EditNameDialog(
                currentName = state.user.name,
                onDismiss = { showEditNameDialog = false },
                onSave = { newName ->
                    viewModel.updateProfile(newName)
                    showEditNameDialog = false
                }
            )
        }
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onSave = { currentPassword, newPassword, confirmPassword ->
                if (newPassword == confirmPassword) {
                    viewModel.updatePassword(
                        currentPassword = currentPassword,
                        newPassword = newPassword,
                        onSuccess = {
                            showChangePasswordDialog = false
                            Toast.makeText(context, "Contraseña actualizada", Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    Toast.makeText(context, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            onDismiss = { showDeleteAccountDialog = false },
            onConfirm = {
                viewModel.deleteAccount(
                    onSuccess = {
                        // ¡ESTO ES VITAL! Igual que en el logout
                        navController.navigate(Screen.Login.route) {
                            popUpTo(navController.graph.id)
                            { inclusive = true } // Limpieza nuclear
                            launchSingleTop = true
                        }
                    },
                    onError = { mensaje ->
                        // Mostrar error
                    }
                )
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { ZipStatsText("Cerrar sesión") },
            text = { ZipStatsText("¿Estás seguro de que quieres salir?") },
            confirmButton = {
                DialogDeleteButton(
                    text = "Cerrar sesión",
                    onClick = {
                        // Llamamos al ViewModel para que cierre en Firebase
                        viewModel.logout {
                            // En vez de navegar, REINICIAMOS LA APP
                            restartApp(context)
                        }
                    }
                )
            },
            dismissButton = {
                DialogNeutralButton(
                    text = "Cancelar",
                    onClick = { showLogoutDialog = false }
                )
            },
            shape = com.zipstats.app.ui.theme.DialogShape
        )
    }

    // --- UI PRINCIPAL ---
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background, // Fondo neutro
        topBar = {
            TopAppBar(
                title = {
                    ZipStatsText(
                        "Ajustes",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. SECCIÓN APARIENCIA (Tema)
            SettingsSection(title = "Apariencia") {
                ZipStatsText(
                    text = "Tema",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )

                // Selector de Tema (Botones Grandes)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemeOptionCard(
                        text = "Claro",
                        icon = Icons.Default.LightMode,
                        selected = currentThemeMode == com.zipstats.app.ui.theme.ThemeMode.LIGHT,
                        onClick = { onThemeModeChange(com.zipstats.app.ui.theme.ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionCard(
                        text = "Oscuro",
                        icon = Icons.Default.DarkMode,
                        selected = currentThemeMode == com.zipstats.app.ui.theme.ThemeMode.DARK,
                        onClick = { onThemeModeChange(com.zipstats.app.ui.theme.ThemeMode.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionCard(
                        text = "Sistema",
                        icon = Icons.Default.Android,
                        selected = currentThemeMode == com.zipstats.app.ui.theme.ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(com.zipstats.app.ui.theme.ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Opciones de Apariencia (Switches)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        SettingsSwitchItem(
                            title = "Colores dinámicos",
                            subtitle = "Tema adaptado a tu fondo de pantalla",
                            icon = Icons.Default.Palette,
                            checked = dynamicColorEnabled,
                            onCheckedChange = onDynamicColorChange,
                            enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        SettingsSwitchItem(
                            title = "Negro puro",
                            subtitle = "Ahorra batería en pantallas OLED",
                            icon = Icons.Default.DarkMode,
                            checked = pureBlackOledEnabled,
                            onCheckedChange = onPureBlackOledChange
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        SettingsSwitchItem(
                            title = "Mantener pantalla encendida",
                            subtitle = "Durante la grabación de rutas",
                            icon = Icons.Default.ScreenLockPortrait,
                            checked = keepScreenOnDuringTracking,
                            onCheckedChange = { enabled ->
                                composeScope.launch {
                                    settingsRepository.setKeepScreenOnDuringTracking(enabled)
                                }
                            }
                        )

                        // Paleta de Colores (Expandible)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        val paletteClickInteractionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = paletteClickInteractionSource,
                                    indication = null,
                                    onClick = { isPaletteExpanded = !isPaletteExpanded }
                                )
                        ) {
                            Column {
                                ListItem(
                                    headlineContent = { ZipStatsText("Paleta de colores") },
                                    supportingContent = {
                                        ZipStatsText(
                                            if (dynamicColorEnabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                                                "Dinámicos activados"
                                            else
                                                currentColorTheme.displayName
                                        )
                                    },
                                    leadingContent = {
                                        Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    trailingContent = {
                                        Icon(
                                            imageVector = if (isPaletteExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )

                                AnimatedVisibility(
                                    visible = isPaletteExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = slideOutVertically() + fadeOut()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        val palettes = com.zipstats.app.ui.theme.ColorTheme.entries.chunked(2)
                                        palettes.forEach { row ->
                                            Row(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                row.forEach { theme ->
                                                    ColorPaletteCard(
                                                        theme = theme,
                                                        selected = currentColorTheme == theme,
                                                        onClick = { onColorThemeChange(theme) },
                                                        enabled = !(dynamicColorEnabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                if (row.size < 2) Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. SECCIÓN PERMISOS
            SettingsSection(title = "Permisos") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    val permissionsClickInteractionSource = remember { MutableInteractionSource() }
                    Column {
                        ListItem(
                            headlineContent = { ZipStatsText("Permisos de la app") },
                            supportingContent = { ZipStatsText("Gestionar acceso a ubicación, cámara...") },
                            leadingContent = { Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                Icon(
                                    imageVector = if (isPermissionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable(
                                interactionSource = permissionsClickInteractionSource,
                                indication = null,
                                onClick = { isPermissionsExpanded = !isPermissionsExpanded }
                            ),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        AnimatedVisibility(
                            visible = isPermissionsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = slideOutVertically() + fadeOut()
                        ) {
                            Column {
                                allPermissions.forEach { permission ->
                                    val permissionItemClickInteractionSource = remember { MutableInteractionSource() }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    ListItem(
                                        headlineContent = { ZipStatsText(getPermissionDisplayName(permission.permission)) },
                                        supportingContent = {
                                            ZipStatsText(
                                                text = if (permissionStates[permission.permission] == true) "Permitido" else "Denegado",
                                                color = if (permissionStates[permission.permission] == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingContent = {
                                            Icon(
                                                getPermissionIcon(permission.permission),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailingContent = {
                                            Switch(
                                                checked = permissionStates[permission.permission] ?: false,
                                                onCheckedChange = { openAppSettings() }
                                            )
                                        },
                                        modifier = Modifier.clickable(
                                            interactionSource = permissionItemClickInteractionSource,
                                            indication = null,
                                            onClick = { openAppSettings() }
                                        ),
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. SECCIÓN CUENTA
            SettingsSection(title = "Cuenta") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        SettingsActionItem(
                            title = "Editar perfil",
                            icon = Icons.Outlined.AccountCircle,
                            onClick = { showEditNameDialog = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsActionItem(
                            title = "Cambiar contraseña",
                            icon = Icons.Default.Lock,
                            onClick = { showChangePasswordDialog = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsActionItem(
                            title = "Cerrar sesión",
                            icon = Icons.AutoMirrored.Filled.Logout,
                            iconTint = MaterialTheme.colorScheme.error,
                            textColor = MaterialTheme.colorScheme.error,
                            onClick = { showLogoutDialog = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsActionItem(
                            title = "Eliminar cuenta",
                            icon = Icons.Outlined.DeleteOutline,
                            iconTint = MaterialTheme.colorScheme.error,
                            textColor = MaterialTheme.colorScheme.error,
                            onClick = { showDeleteAccountDialog = true }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ----------------------------------------------------------------
// COMPONENTES DE AJUSTES REUTILIZABLES
// ----------------------------------------------------------------

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ZipStatsText(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )
        content()
    }
}

@Composable
fun ThemeOptionCard(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = if (!selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = modifier.height(80.dp), // Altura fija para uniformidad
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            ZipStatsText(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { ZipStatsText(title) },
        supportingContent = subtitle?.let { { ZipStatsText(it) } },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = { onCheckedChange(!checked) }
        )
    )
}

@Composable
fun SettingsActionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val actionClickInteractionSource = remember { MutableInteractionSource() }
    ListItem(
        headlineContent = {
            ZipStatsText(
                text = title,
                color = textColor,
                fontWeight = if (textColor != MaterialTheme.colorScheme.onSurface) FontWeight.Bold else FontWeight.Normal
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(
            interactionSource = actionClickInteractionSource,
            indication = null,
            onClick = onClick
        ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// --- DIÁLOGOS (Igual que antes) ---

@Composable
private fun EditNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { ZipStatsText("Editar nombre") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { ZipStatsText("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            DialogSaveButton(
                text = "Guardar",
                enabled = name.isNotEmpty(),
                onClick = { onSave(name) }
            )
        },
        dismissButton = {
            DialogNeutralButton(text = "Cancelar", onClick = onDismiss)
        },
        shape = com.zipstats.app.ui.theme.DialogShape
    )
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { ZipStatsText("Cambiar contraseña") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { ZipStatsText("Contraseña actual") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { ZipStatsText("Nueva contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { ZipStatsText("Confirmar contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            DialogSaveButton(
                text = "Guardar",
                enabled = currentPassword.isNotEmpty() && newPassword.isNotEmpty() && confirmPassword.isNotEmpty(),
                onClick = { onSave(currentPassword, newPassword, confirmPassword) }
            )
        },
        dismissButton = {
            DialogNeutralButton(text = "Cancelar", onClick = onDismiss)
        },
        shape = com.zipstats.app.ui.theme.DialogShape
    )
}

@Composable
fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { ZipStatsText("Eliminar cuenta") },
        text = { ZipStatsText("¿Estás seguro de que quieres eliminar tu cuenta? Esta acción no se puede deshacer.") },
        confirmButton = {
            DialogDeleteButton(text = "Eliminar", onClick = onConfirm)
        },
        dismissButton = {
            DialogNeutralButton(text = "Cancelar", onClick = onDismiss)
        },
        shape = com.zipstats.app.ui.theme.DialogShape
    )
}

@Composable
private fun ColorPaletteCard(
    theme: com.zipstats.app.ui.theme.ColorTheme,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val paletteCardClickInteractionSource = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .clickable(
                interactionSource = paletteCardClickInteractionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .then(if (selected && enabled) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ZipStatsText(
                text = theme.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected && enabled) FontWeight.Bold else FontWeight.Medium,
                color = if (enabled) (if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(theme.primaryLight))
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(theme.secondaryLight))
            }
        }
    }
}

@Composable
private fun getPermissionIcon(permission: String): ImageVector {
    return when {
        permission.contains("LOCATION") -> Icons.Default.LocationOn
        permission.contains("NOTIFICATION") -> Icons.Default.Notifications
        permission.contains("CAMERA") -> Icons.Default.Camera
        permission.contains("MEDIA_PROJECTION") -> Icons.Default.Videocam
        permission.contains("MEDIA") -> Icons.Default.Image
        else -> Icons.Default.Security
    }
}

private fun getPermissionDisplayName(permission: String): String {
    return when {
        permission.contains("FINE_LOCATION") -> "Ubicación (Precisa)"
        permission.contains("COARSE_LOCATION") -> "Ubicación (Aproximada)"
        permission.contains("NOTIFICATION") -> "Notificaciones"
        permission.contains("CAMERA") -> "Cámara"
        permission.contains("MEDIA_PROJECTION") -> "Grabación de pantalla"
        permission.contains("MEDIA") -> "Archivos"
        else -> "Permiso del sistema"
    }
}