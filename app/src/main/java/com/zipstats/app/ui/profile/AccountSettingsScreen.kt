package com.zipstats.app.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenLockPortrait
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.navigation.Screen
import com.zipstats.app.repository.SettingsRepository
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
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val keepScreenOnDuringTracking by settingsRepository.keepScreenOnDuringTrackingFlow.collectAsState(initial = false)
    val composeScope = rememberCoroutineScope()
    
    var isPaletteExpanded by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    // Observar el estado de autenticación
    val authState by authViewModel.authState.collectAsState()
    
    // Navegar automáticamente al login cuando se detecte el logout
    LaunchedEffect(authState) {
        if (authState is com.zipstats.app.ui.auth.AuthState.Initial) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Diálogos
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
                            Toast.makeText(context, "Contraseña actualizada correctamente", Toast.LENGTH_SHORT).show()
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
                        authViewModel.logout()
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Estás seguro de que quieres cerrar sesión?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        authViewModel.logout()
                        showLogoutDialog = false
                    }
                ) {
                    Text("Cerrar sesión")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Sección de Apariencia
            Text(
                text = "Apariencia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Tema",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOption(
                            text = "Claro",
                            icon = Icons.Default.LightMode,
                            selected = currentThemeMode == com.zipstats.app.ui.theme.ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(com.zipstats.app.ui.theme.ThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOption(
                            text = "Oscuro",
                            icon = Icons.Default.DarkMode,
                            selected = currentThemeMode == com.zipstats.app.ui.theme.ThemeMode.DARK,
                            onClick = { onThemeModeChange(com.zipstats.app.ui.theme.ThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOption(
                            text = "Sistema",
                            icon = Icons.Default.Android,
                            selected = currentThemeMode == com.zipstats.app.ui.theme.ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(com.zipstats.app.ui.theme.ThemeMode.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Colores dinámicos, OLED y Paleta de colores
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Colores dinámicos") },
                        supportingContent = { Text("Tema adaptado a tu fondo de pantalla") },
                        leadingContent = { 
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = dynamicColorEnabled,
                                onCheckedChange = onDynamicColorChange,
                                enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                            )
                        }
                    )
                    
                    HorizontalDivider()
                    
                    ListItem(
                        headlineContent = { Text("Negro puro") },
                        supportingContent = { Text("Ahorra batería en pantallas OLED") },
                        leadingContent = { 
                            Icon(
                                Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = pureBlackOledEnabled,
                                onCheckedChange = onPureBlackOledChange,
                                enabled = true
                            )
                        }
                    )
                    
                    HorizontalDivider()
                    
                    ListItem(
                        headlineContent = { Text("Mantener pantalla encendida") },
                        supportingContent = { Text("Durante la grabación de rutas") },
                        leadingContent = { 
                            Icon(
                                Icons.Default.ScreenLockPortrait,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = keepScreenOnDuringTracking,
                                onCheckedChange = { enabled ->
                                    composeScope.launch {
                                        settingsRepository.setKeepScreenOnDuringTracking(enabled)
                                    }
                                },
                                enabled = true
                            )
                        }
                    )
                    
                    HorizontalDivider()
                    
                    // Desplegable de Paleta de Colores
                    Column {
                        ListItem(
                            headlineContent = { Text("Paleta de colores") },
                            supportingContent = { 
                                Text(
                                    if (dynamicColorEnabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                        "Los colores dinámicos reemplazan la paleta personalizada"
                                    } else {
                                        "Elige el estilo visual de la app"
                                    }
                                )
                            },
                            leadingContent = { 
                                Icon(
                                    Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { isPaletteExpanded = !isPaletteExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (isPaletteExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isPaletteExpanded) "Contraer" else "Expandir"
                                    )
                                }
                            },
                            modifier = Modifier.clickable { isPaletteExpanded = !isPaletteExpanded }
                        )
                        
                        AnimatedVisibility(
                            visible = isPaletteExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = slideOutVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                // Grid de paletas (2 columnas)
                                val palettes = com.zipstats.app.ui.theme.ColorTheme.entries.chunked(2)
                                palettes.forEach { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
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
                                        // Si es impar, añadir espaciador
                                        if (row.size < 2) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sección de Cuenta
            Text(
                text = "Cuenta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    // Editar perfil
                    ListItem(
                        headlineContent = { Text("Editar perfil") },
                        supportingContent = { Text("Cambiar el nombre de tu perfil") },
                        leadingContent = { 
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable { showEditNameDialog = true }
                    )
                    
                    HorizontalDivider()
                    
                    // Cambiar contraseña
                    ListItem(
                        headlineContent = { Text("Cambiar contraseña") },
                        supportingContent = { Text("Actualizar tu contraseña de acceso") },
                        leadingContent = { 
                            Icon(
                                imageVector = Icons.Default.Lock, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable { showChangePasswordDialog = true }
                    )
                    
                    HorizontalDivider()
                    
                    // Cerrar sesión
                    ListItem(
                        headlineContent = { 
                            Text(
                                "Cerrar sesión",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        supportingContent = { Text("Salir de tu cuenta") },
                        leadingContent = { 
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.clickable { showLogoutDialog = true }
                    )
                    
                    HorizontalDivider()
                    
                    // Eliminar cuenta
                    ListItem(
                        headlineContent = { 
                            Text(
                                "Eliminar cuenta",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        supportingContent = { Text("Eliminar permanentemente tu cuenta y todos tus datos") },
                        leadingContent = { 
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.clickable { showDeleteAccountDialog = true }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EditNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar nombre") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotEmpty()
            ) {
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
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar contraseña") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Contraseña actual") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nueva contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirmar contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(currentPassword, newPassword, confirmPassword) },
                enabled = currentPassword.isNotEmpty() && 
                         newPassword.isNotEmpty() && 
                         confirmPassword.isNotEmpty()
            ) {
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
fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar cuenta") },
        text = { Text("¿Estás seguro de que quieres eliminar tu cuenta? Esta acción no se puede deshacer y se eliminarán todos tus datos permanentemente.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
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
fun ThemeOption(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ColorPaletteCard(
    theme: com.zipstats.app.ui.theme.ColorTheme,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick, enabled = enabled)
            .then(
                if (selected && enabled) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant 
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Nombre de la paleta
            Text(
                text = theme.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected && enabled) FontWeight.Bold else FontWeight.Medium,
                color = if (enabled) {
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Círculos de colores
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Color primario
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(theme.primaryLight)
                        .border(
                            width = if (selected && enabled) 2.dp else 1.dp,
                            color = if (enabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Color secundario
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(theme.secondaryLight)
                        .border(
                            width = if (selected && enabled) 2.dp else 1.dp,
                            color = if (enabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Blanco (representa el fondo claro)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(
                            width = if (selected && enabled) 2.dp else 1.dp,
                            color = if (enabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                )
            }
            
            if (!enabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Desactivado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}


