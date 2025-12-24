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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.graphicsLayer
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
    val scope = rememberCoroutineScope()

    var isPaletteExpanded by remember { mutableStateOf(false) }
    var isPermissionsExpanded by remember { mutableStateOf(false) }
    var permissionStates by remember { mutableStateOf(permissionManager.getPermissionStates()) }
    val allPermissions = remember { permissionManager.getAllPermissions() }

    LaunchedEffect(Unit) {
        permissionStates = permissionManager.getPermissionStates()
    }

    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }

    fun openSystemSettings() {
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
                            restartApp()
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
            // 1. SECCIÓN APARIENCIA
            SettingsGroup(title = "Apariencia") {
                // Selector de Tema (Row de 3)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionItem(
                        icon = Icons.Default.LightMode,
                        label = "Claro",
                        selected = currentThemeMode == com.zipstats.app.ui.theme.ThemeMode.LIGHT,
                        onClick = { onThemeModeChange(com.zipstats.app.ui.theme.ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionItem(
                        icon = Icons.Default.DarkMode,
                        label = "Oscuro",
                        selected = currentThemeMode == com.zipstats.app.ui.theme.ThemeMode.DARK,
                        onClick = { onThemeModeChange(com.zipstats.app.ui.theme.ThemeMode.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionItem(
                        icon = Icons.Default.Android,
                        label = "Sistema",
                        selected = currentThemeMode == com.zipstats.app.ui.theme.ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(com.zipstats.app.ui.theme.ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Switches
                SettingsSwitchRow(
                    title = "Colores dinámicos",
                    subtitle = "Adaptar al fondo de pantalla",
                    icon = Icons.Default.Palette,
                    checked = dynamicColorEnabled,
                    onCheckedChange = onDynamicColorChange,
                    enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                )

                // Paleta de Colores (Desplegable)
                if (!dynamicColorEnabled || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    ExpandableSettingRow(
                        title = "Paleta de colores",
                        subtitle = currentColorTheme.displayName,
                        icon = Icons.Default.Palette,
                        expanded = isPaletteExpanded,
                        onExpandChange = { isPaletteExpanded = it }
                    ) {
                        ColorPaletteGrid(
                            currentColorTheme = currentColorTheme,
                            onColorThemeChange = onColorThemeChange
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                SettingsSwitchRow(
                    title = "Negro puro",
                    subtitle = "Ahorro batería OLED",
                    icon = Icons.Default.DarkMode,
                    checked = pureBlackOledEnabled,
                    onCheckedChange = onPureBlackOledChange
                )
            }

            // 2. SECCIÓN COMPORTAMIENTO
            SettingsGroup(title = "Comportamiento") {
                SettingsSwitchRow(
                    title = "Pantalla encendida",
                    subtitle = "Evitar bloqueo al grabar ruta",
                    icon = Icons.Default.ScreenLockPortrait,
                    checked = keepScreenOnDuringTracking,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setKeepScreenOnDuringTracking(enabled) }
                    }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Permisos (Desplegable)
                ExpandableSettingRow(
                    title = "Permisos del sistema",
                    subtitle = "Gestionar accesos",
                    icon = Icons.Default.Security,
                    expanded = isPermissionsExpanded,
                    onExpandChange = { isPermissionsExpanded = it }
                ) {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        allPermissions.forEach { perm ->
                            val isGranted = permissionStates[perm.permission] == true
                            SettingsNavigationRow(
                                title = getPermissionDisplayName(perm.permission),
                                icon = getPermissionIcon(perm.permission),
                                showChevron = false,
                                trailingContent = {
                                    Switch(
                                        checked = isGranted,
                                        onCheckedChange = { openSystemSettings() },
                                        modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f)
                                    )
                                },
                                onClick = { openSystemSettings() }
                            )
                        }
                    }
                }
            }

            // 3. SECCIÓN CUENTA
            SettingsGroup(title = "Cuenta") {
                SettingsNavigationRow(
                    title = "Editar perfil",
                    icon = Icons.Outlined.AccountCircle,
                    onClick = { showEditNameDialog = true }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                SettingsNavigationRow(
                    title = "Cambiar contraseña",
                    icon = Icons.Default.Lock,
                    onClick = { showChangePasswordDialog = true }
                )
            }

            // 4. ZONA DE PELIGRO (Acciones destructivas separadas)
            SettingsGroup(
                title = "Zona de peligro",
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            ) {
                SettingsNavigationRow(
                    title = "Cerrar sesión",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconTint = MaterialTheme.colorScheme.error,
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = { showLogoutDialog = true }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                )
                
                SettingsNavigationRow(
                    title = "Eliminar cuenta",
                    icon = Icons.Outlined.DeleteOutline,
                    iconTint = MaterialTheme.colorScheme.error,
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteAccountDialog = true }
                )
            }
            
            // Versión de la app
            ZipStatsText(
                text = "Versión 4.6.5",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ----------------------------------------------------------------
// COMPONENTES DE AJUSTES REUTILIZABLES
// ----------------------------------------------------------------

@Composable
fun SettingsGroup(
    title: String,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        ZipStatsText(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun ThemeOptionItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor)
        Spacer(modifier = Modifier.height(4.dp))
        ZipStatsText(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { ZipStatsText(title, fontWeight = FontWeight.Medium) },
        supportingContent = subtitle?.let { { ZipStatsText(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = { 
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            ) 
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            )
    )
}

@Composable
fun SettingsNavigationRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headlineContent = { ZipStatsText(title, color = textColor, fontWeight = FontWeight.Medium) },
        leadingContent = { Icon(imageVector = icon, contentDescription = null, tint = iconTint) },
        trailingContent = trailingContent ?: if (showChevron) {
            { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    )
}

@Composable
fun ExpandableSettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        ListItem(
            headlineContent = { ZipStatsText(title, fontWeight = FontWeight.Medium) },
            supportingContent = { ZipStatsText(subtitle, style = MaterialTheme.typography.bodySmall) },
            leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { onExpandChange(!expanded) }
                )
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            content()
        }
    }
}

@Composable
fun ColorPaletteGrid(
    currentColorTheme: com.zipstats.app.ui.theme.ColorTheme,
    onColorThemeChange: (com.zipstats.app.ui.theme.ColorTheme) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        com.zipstats.app.ui.theme.ColorTheme.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { theme ->
                    val selected = currentColorTheme == theme
                    Card(
                        onClick = { onColorThemeChange(theme) },
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (selected) 1f else 0.3f)
                        ),
                        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(theme.primaryLight))
                            Spacer(modifier = Modifier.width(8.dp))
                            ZipStatsText(theme.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
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