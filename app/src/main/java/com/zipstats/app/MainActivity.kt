package com.zipstats.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.FirebaseApp
import com.zipstats.app.di.AppOverlayRepositoryEntryPoint
import com.zipstats.app.navigation.NavGraph
import com.zipstats.app.navigation.Screen
import com.zipstats.app.permission.PermissionManager
import com.zipstats.app.repository.AppOverlayRepository
import com.zipstats.app.repository.SettingsRepository
import com.zipstats.app.service.LocationTrackingService
import com.zipstats.app.ui.components.BottomNavigation
import com.zipstats.app.ui.components.DialogNeutralButton
import com.zipstats.app.ui.components.ZipStatsText
import com.zipstats.app.ui.permissions.PermissionsDialog
import com.zipstats.app.ui.shared.AppOverlayState
import com.zipstats.app.ui.shared.SplashOverlay
import com.zipstats.app.ui.theme.ColorTheme
import com.zipstats.app.ui.theme.PatinetatrackTheme
import com.zipstats.app.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var notificationManager: NotificationManager
    private val CHANNEL_ID = "export_channel"
    private val NOTIFICATION_ID = 1
    

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Algunos permisos fueron denegados", Toast.LENGTH_SHORT).show()
        }
    }

    private val exportExcelLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data?.data != null) {
            val uri = result.data?.data!!
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val tempFile = File(cacheDir, "registros_vehiculos_${System.currentTimeMillis()}.xls")
                    if (tempFile.exists() && tempFile.length() > 0) {
                        tempFile.inputStream().use { input ->
                            input.copyTo(outputStream)
                        }
                        tempFile.delete()
                        Toast.makeText(this, "Archivo exportado correctamente", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Error: No se pudo encontrar el archivo temporal", Toast.LENGTH_LONG).show()
                    }
                } ?: run {
                    Toast.makeText(this, "Error: No se pudo abrir el archivo de destino", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Las notificaciones son necesarias para mostrar el progreso de la exportación",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Permiso concedido. Intenta exportar nuevamente.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permiso denegado. No se puede exportar sin acceso al almacenamiento.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        FirebaseApp.initializeApp(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        // No pedir permisos al inicio - solo cuando se necesiten (al exportar)

        val showErrorDialog = intent.getBooleanExtra("SHOW_ERROR_DIALOG", false)
        val errorMessage = intent.getStringExtra("ERROR_MESSAGE")
        val shouldOpenTracking = intent.action == LocationTrackingService.ACTION_OPEN_TRACKING
        val navigateToRoute = intent.getStringExtra("navigate_to")

        setContent {
            MainContent(
                showErrorDialog = showErrorDialog,
                errorMessage = errorMessage,
                shouldOpenTracking = shouldOpenTracking,
                navigateToRoute = navigateToRoute
            )
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Si llega un nuevo intent mientras la app está abierta
        if (intent.action == LocationTrackingService.ACTION_OPEN_TRACKING) {
            // Usar un enfoque más robusto: agregar flag para forzar navegación
            intent.putExtra("FORCE_NAVIGATE_TO_TRACKING", true)
            // Re-crear el contenido para manejar la navegación
            setContent {
                MainContent(
                    showErrorDialog = false,
                    errorMessage = null,
                    shouldOpenTracking = true,
                    navigateToRoute = null
                )
            }
        } else {
            val navigateToRoute = intent.getStringExtra("navigate_to")
            if (navigateToRoute != null) {
                setContent {
                    MainContent(
                        showErrorDialog = false,
                        errorMessage = null,
                        shouldOpenTracking = false,
                        navigateToRoute = navigateToRoute
                    )
                }
            }
        }
    }

    @Composable
    private fun MainContent(
        showErrorDialog: Boolean,
        errorMessage: String?,
        shouldOpenTracking: Boolean = false,
        navigateToRoute: String? = null
    ) {
        val navController = rememberNavController()
        
        // Navegar a tracking si viene desde la notificación
        LaunchedEffect(shouldOpenTracking) {
            if (shouldOpenTracking) {
                // Esperar un momento para asegurar que el NavController esté listo
                kotlinx.coroutines.delay(100)
                
                // Obtener la ruta de inicio del grafo de navegación
                val startRoute = navController.graph.startDestinationRoute ?: Screen.Records.route
                
                // Limpiar el back stack hasta la ruta inicial y navegar directamente a Tracking
                navController.navigate(Screen.Tracking.route) {
                    // Pop hasta la ruta inicial pero NO inclusive (para mantener la base)
                    popUpTo(startRoute) {
                        inclusive = false
                        saveState = false
                    }
                    // Forzar navegación única top - si Tracking ya está en el stack, reutilizarlo
                    launchSingleTop = true
                    restoreState = false
                }
            }
        }
        
        // Navegar a una ruta específica si viene desde una notificación
        LaunchedEffect(navigateToRoute) {
            navigateToRoute?.let { route ->
                kotlinx.coroutines.delay(100)
                navController.navigate(route) {
                    launchSingleTop = true
                }
            }
        }
        var currentThemeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
        var currentColorTheme by remember { mutableStateOf(ColorTheme.RIDE_BLUE) }
        var dynamicColorEnabled by remember { mutableStateOf(true) }
        var pureBlackOledEnabled by remember { mutableStateOf(false) }
        val composeScope = rememberCoroutineScope()

        // AppOverlayRepository para el overlay global
        val appOverlayRepository: AppOverlayRepository = remember { 
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                applicationContext,
                AppOverlayRepositoryEntryPoint::class.java
            ).appOverlayRepository()
        }
        val overlay by appOverlayRepository.overlay.collectAsState()

        // Repository for settings
        val settingsRepository = remember { SettingsRepository(applicationContext) }

        // Collect settings from DataStore
        androidx.compose.runtime.LaunchedEffect(Unit) {
            settingsRepository.themeModeFlow.collect { mode ->
                currentThemeMode = mode
            }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            settingsRepository.colorThemeFlow.collect { theme ->
                currentColorTheme = theme
            }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            settingsRepository.dynamicColorFlow.collect { enabled ->
                dynamicColorEnabled = enabled
            }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            settingsRepository.pureBlackOledFlow.collect { enabled ->
                pureBlackOledEnabled = enabled
            }
        }

        // Ensure dynamic color is disabled on devices below Android 12
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && dynamicColorEnabled) {
                dynamicColorEnabled = false
                settingsRepository.setDynamicColor(false)
            }
        }
        var showDialog by remember { mutableStateOf(showErrorDialog) }
        
        // Sistema de permisos centralizado
        val permissionManager = remember { PermissionManager(applicationContext) }
        var showPermissionsDialog by remember { mutableStateOf(false) }
        val allPermissions = remember { permissionManager.getAllPermissions() }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Los permisos se han solicitado, el diálogo se cerrará automáticamente
            showPermissionsDialog = false
        }
        
        // Mostrar diálogo de permisos al inicio si no están todos concedidos
        LaunchedEffect(Unit) {
            if (!permissionManager.hasAllRequiredPermissions()) {
                showPermissionsDialog = true
            }
        }
        
        PatinetatrackTheme(
            darkTheme = when (currentThemeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            },
            colorTheme = currentColorTheme,
            dynamicColor = dynamicColorEnabled,
            pureBlackOled = pureBlackOledEnabled
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            // Diálogo de permisos al inicio
            if (showPermissionsDialog) {
                PermissionsDialog(
                    permissions = allPermissions,
                    onConfirm = {
                        val permissionsToRequest = permissionManager.getRequiredStartupPermissions()
                        if (permissionsToRequest.isNotEmpty()) {
                            permissionLauncher.launch(permissionsToRequest)
                        } else {
                            showPermissionsDialog = false
                        }
                    },
                    onDismiss = {
                        showPermissionsDialog = false
                    }
                )
            }
            
            if (showDialog && errorMessage != null) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { ZipStatsText("Error en la importación") },
                    text = { ZipStatsText(errorMessage) },
                    confirmButton = {
                        DialogNeutralButton(
                            text = "Aceptar",
                            onClick = { showDialog = false }
                        )
                    }
                )

            }

            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (currentRoute !in listOf(Screen.Splash.route, Screen.Login.route, Screen.Register.route)) {
                            BottomNavigation(navController = navController)
                        }
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NavGraph(
                            navController = navController,
                            currentThemeMode = currentThemeMode,
                            onThemeModeChange = { newMode ->
                                currentThemeMode = newMode
                                composeScope.launch {
                                    settingsRepository.setThemeMode(newMode)
                                }
                            },
                            currentColorTheme = currentColorTheme,
                            onColorThemeChange = { newTheme ->
                                currentColorTheme = newTheme
                                composeScope.launch {
                                    settingsRepository.setColorTheme(newTheme)
                                }
                            },
                            dynamicColorEnabled = dynamicColorEnabled,
                            onDynamicColorChange = { enabled ->
                                dynamicColorEnabled = enabled
                                composeScope.launch {
                                    settingsRepository.setDynamicColor(enabled)
                                }
                            },
                            pureBlackOledEnabled = pureBlackOledEnabled,
                            onPureBlackOledChange = { enabled ->
                                pureBlackOledEnabled = enabled
                                composeScope.launch {
                                    settingsRepository.setPureBlackOled(enabled)
                                }
                            }
                        )
                    }
                }
                
                // Mostrar overlay si está activo
                when (val overlayState = overlay) {
                    is AppOverlayState.Splash -> {
                        SplashOverlay(message = overlayState.message)
                    }
                    is AppOverlayState.None -> {
                        // No mostrar nada
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Exportación de registros"
            val descriptionText = "Canal para notificaciones de exportación de registros"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Los permisos ya están concedidos
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Mostrar explicación de por qué se necesitan los permisos
                    Toast.makeText(
                        this,
                        "Las notificaciones son necesarias para mostrar el progreso de la exportación",
                        Toast.LENGTH_LONG
                    ).show()
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Solicitar permisos directamente
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    fun exportToDownloads(file: File) {
        Log.d("MainActivity", "Iniciando exportación a Downloads")
        
        // Verificar si tenemos permiso de notificaciones
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // En versiones anteriores a Android 13, no se necesita permiso
        }
        
        Log.d("MainActivity", "Permiso de notificaciones: $hasNotificationPermission")
        
        // Si no tiene permiso, pedirlo pero continuar con la exportación de todos modos
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                this,
                "Exportando archivo. Para ver el progreso, concede permisos de notificación.",
                Toast.LENGTH_LONG
            ).show()
            requestNotificationPermissionIfNeeded()
        }

        if (!file.exists() || file.length() == 0L) {
            Log.e("MainActivity", "Archivo temporal no válido: exists=${file.exists()}, length=${file.length()}")
            Toast.makeText(this, "Error: El archivo temporal no es válido", Toast.LENGTH_LONG).show()
            return
        }

        Log.d("MainActivity", "Archivo temporal válido: ${file.absolutePath}, tamaño: ${file.length()} bytes")

        // Ejecutar operaciones de IO en un hilo de fondo
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val fileName = "registros_vehiculos_$timestamp.xlsx"
                
                // Verificar permisos de almacenamiento antes de proceder
                val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ - MediaStore no requiere permisos explícitos para escribir
                    true
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Android 6-9 - verificar permiso de escritura externa
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    // Android 5 y anteriores - no se necesita permiso explícito
                    true
                }
                
                Log.d("MainActivity", "Permiso de almacenamiento: $hasStoragePermission")
                
                if (!hasStoragePermission) {
                    Log.e("MainActivity", "No se tiene permiso de almacenamiento")
                    
                    withContext(Dispatchers.Main) {
                        // Solicitar permiso de almacenamiento solo para Android 6-9
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            Toast.makeText(this@MainActivity, "Se necesita permiso de almacenamiento para exportar", Toast.LENGTH_LONG).show()
                        }
                    }
                    return@launch
                }

                // Mostrar notificación de progreso solo si tenemos permiso
                if (hasNotificationPermission) {
                    withContext(Dispatchers.Main) {
                        val builder = NotificationCompat.Builder(this@MainActivity, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_download)
                            .setContentTitle("Exportando registros")
                            .setProgress(0, 0, true)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        Log.d("MainActivity", "Usando MediaStore para Android Q+")
                        
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.ms-excel")
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }

                        val resolver = contentResolver
                        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                        Log.d("MainActivity", "Insertando en MediaStore: $collection")
                        
                        val itemUri = resolver.insert(collection, contentValues)
                        Log.d("MainActivity", "URI creada: $itemUri")

                        if (itemUri != null) {
                            Log.d("MainActivity", "Copiando archivo a MediaStore")
                            resolver.openOutputStream(itemUri)?.use { outputStream ->
                                file.inputStream().use { input ->
                                    input.copyTo(outputStream)
                                }
                            }

                            contentValues.clear()
                            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                            resolver.update(itemUri, contentValues, null, null)
                            Log.d("MainActivity", "Archivo copiado exitosamente")

                            withContext(Dispatchers.Main) {
                                if (hasNotificationPermission) {
                                    showCompletionNotification(itemUri)
                                } else {
                                    Toast.makeText(this@MainActivity, "Archivo exportado exitosamente a Descargas", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Log.e("MainActivity", "No se pudo crear URI en MediaStore")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Error: No se pudo crear el archivo en Descargas", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error en MediaStore", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val destinationFile = File(downloadsDir, fileName)

                    file.inputStream().use { input ->
                        destinationFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.provider",
                        destinationFile
                    )
                    withContext(Dispatchers.Main) {
                        if (hasNotificationPermission) {
                            showCompletionNotification(uri)
                        } else {
                            Toast.makeText(this@MainActivity, "Archivo exportado exitosamente a Descargas", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // Eliminar el archivo temporal
                file.delete()
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    if (hasNotificationPermission) {
                        notificationManager.cancel(NOTIFICATION_ID)
                    }
                    Toast.makeText(this@MainActivity, "Error al guardar el archivo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showCompletionNotification(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.ms-excel")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_done)
            .setContentTitle("Exportación completada")
            .setContentText("Toca para abrir el archivo")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
    
    @Deprecated("Deprecated in Java. Use Activity Result API instead.")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Este método ya no se usa, se migró a Activity Result API
        // Se mantiene por compatibilidad pero no realiza ninguna acción
    }
}