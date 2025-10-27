package com.zipstats.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavController
import android.Manifest
import com.zipstats.app.ui.profile.ProfileViewModel
import com.zipstats.app.ui.theme.ThemeMode
import android.os.Build

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    pureBlackOledEnabled: Boolean = false,
    onPureBlackOledChange: (Boolean) -> Unit = {},
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    var showExportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("SettingsScreen", "Resultado del permiso: $isGranted")
        if (isGranted) {
            // Si se concede el permiso, proceder con la exportación
            android.util.Log.d("SettingsScreen", "Permiso concedido, exportando...")
            profileViewModel.exportAllRecords(context)
            showExportDialog = false
        } else {
            // Si se deniega, mostrar mensaje
            android.util.Log.d("SettingsScreen", "Permiso denegado")
            Toast.makeText(context, "Permiso denegado. No se puede exportar sin acceso al almacenamiento.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
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
                .verticalScroll(scrollState)
        ) {
            // Sección de Apariencia
            Text(
                text = "Apariencia",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOption(
                            text = "Claro",
                            icon = Icons.Filled.LightMode,
                            selected = currentThemeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOption(
                            text = "Oscuro",
                            icon = Icons.Filled.DarkMode,
                            selected = currentThemeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOption(
                            text = "Sistema",
                            icon = Icons.Filled.Android,
                            selected = currentThemeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Colores dinámicos")
                                Text(
                                    text = "No compatible con Negro Oled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = dynamicColorEnabled,
                                onCheckedChange = { onDynamicColorChange(it) }
                            )
                        }
                    }
                    
                    // Negro OLED - siempre visible
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Negro para OLED",
                                color = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.38f else 1f
                                )
                            )
                            Text(
                                text = "Ahorra batería en pantallas OLED",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.38f else 0.7f
                                )
                            )
                        }
                        Switch(
                            checked = pureBlackOledEnabled,
                            onCheckedChange = onPureBlackOledChange,
                            enabled = !(dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sección de Datos
            Text(
                text = "Datos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                // Exportar registros
                ListItem(
                    headlineContent = { Text("Exportar todos los registros") },
                    supportingContent = { Text("Descarga un archivo Excel con todos tus registros") },
                    leadingContent = {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        showExportDialog = true
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Diálogo de exportación
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exportar registros") },
            text = {
                Column {
                    Text("Se exportarán todos tus registros en un archivo Excel.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "El archivo se guardará en la carpeta de Descargas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        android.util.Log.d("SettingsScreen", "=== INICIO BOTÓN EXPORTAR ===")
                        android.util.Log.d("SettingsScreen", "Botón Exportar presionado")
                        android.util.Log.d("SettingsScreen", "Android SDK: ${Build.VERSION.SDK_INT}")
                        
                        try {
                            // Verificar permisos antes de exportar
                            android.util.Log.d("SettingsScreen", "Llamando a checkStoragePermission...")
                            val hasPermission = profileViewModel.checkStoragePermission(context)
                            android.util.Log.d("SettingsScreen", "Tiene permiso: $hasPermission")
                            
                            if (hasPermission) {
                                android.util.Log.d("SettingsScreen", "Exportando directamente...")
                                profileViewModel.exportAllRecords(context)
                                showExportDialog = false
                            } else {
                                android.util.Log.d("SettingsScreen", "Solicitando permiso...")
                                // Solicitar permiso automáticamente
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    // Android 14+ - pedir permiso para documentos
                                    android.util.Log.d("SettingsScreen", "Solicitando READ_MEDIA_DOCUMENTS")
                                    storagePermissionLauncher.launch("android.permission.READ_MEDIA_DOCUMENTS")
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    // Android 13 - pedir permiso para imágenes (fallback)
                                    android.util.Log.d("SettingsScreen", "Solicitando READ_MEDIA_IMAGES")
                                    storagePermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                                } else {
                                    android.util.Log.d("SettingsScreen", "Mostrando toast para Android 12-")
                                    Toast.makeText(context, "Se necesita permiso de almacenamiento. Ve a Configuración > Permisos de la app.", Toast.LENGTH_LONG).show()
                                }
                                // NO cerrar el diálogo hasta que se conceda el permiso
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SettingsScreen", "Error en botón exportar: ${e.message}")
                        }
                        android.util.Log.d("SettingsScreen", "=== FIN BOTÓN EXPORTAR ===")
                    }
                ) {
                    Text("Exportar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ThemeOption(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

