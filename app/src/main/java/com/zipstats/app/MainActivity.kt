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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zipstats.app.navigation.NavGraph
import com.zipstats.app.navigation.Screen
import com.zipstats.app.repository.SettingsRepository
import com.zipstats.app.service.LocationTrackingService
import com.zipstats.app.ui.achievements.AchievementsViewModel
import com.zipstats.app.ui.components.BottomNavigation
import com.zipstats.app.ui.theme.PatinetatrackTheme
import com.zipstats.app.ui.theme.ThemeMode
import com.google.firebase.FirebaseApp
import com.zipstats.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        FirebaseApp.initializeApp(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        // No pedir permisos al inicio - solo cuando se necesiten (al exportar)

        val showErrorDialog = intent.getBooleanExtra("SHOW_ERROR_DIALOG", false)
        val errorMessage = intent.getStringExtra("ERROR_MESSAGE")
        val shouldOpenTracking = intent.action == LocationTrackingService.ACTION_OPEN_TRACKING

        setContent {
            MainContent(
                showErrorDialog = showErrorDialog,
                errorMessage = errorMessage,
                shouldOpenTracking = shouldOpenTracking
            )
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Si llega un nuevo intent mientras la app está abierta
        if (intent.action == LocationTrackingService.ACTION_OPEN_TRACKING) {
            // Re-crear el contenido para manejar la navegación
            setContent {
                MainContent(
                    showErrorDialog = false,
                    errorMessage = null,
                    shouldOpenTracking = true
                )
            }
        }
    }

    @Composable
    private fun MainContent(
        showErrorDialog: Boolean,
        errorMessage: String?,
        shouldOpenTracking: Boolean = false
    ) {
        val navController = rememberNavController()
        
        // Navegar a tracking si viene desde la notificación
        LaunchedEffect(shouldOpenTracking) {
            if (shouldOpenTracking) {
                navController.navigate(Screen.Tracking.route) {
                    launchSingleTop = true
                }
            }
        }
        var currentThemeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
        var dynamicColorEnabled by remember { mutableStateOf(true) }
        var pureBlackOledEnabled by remember { mutableStateOf(false) }
        val composeScope = rememberCoroutineScope()

        // Repository for settings
        val settingsRepository = remember { SettingsRepository(applicationContext) }

        // Collect settings from DataStore
        androidx.compose.runtime.LaunchedEffect(Unit) {
            settingsRepository.themeModeFlow.collect { mode ->
                currentThemeMode = mode
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
        
        // Obtener el AuthViewModel aquí
        val authViewModel: com.zipstats.app.ui.auth.AuthViewModel = hiltViewModel()
        
        PatinetatrackTheme(
            darkTheme = when (currentThemeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            },
            dynamicColor = dynamicColorEnabled,
            pureBlackOled = pureBlackOledEnabled
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            if (showDialog && errorMessage != null) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Error en la importación") },
                    text = { Text(errorMessage) },
                    confirmButton = {
                        TextButton(onClick = { showDialog = false }) {
                            Text("Aceptar")
                        }
                    }
                )
            }

            // ViewModels globales
            val achievementsViewModel: AchievementsViewModel = hiltViewModel()
            val snackbarHostState = androidx.compose.material3.SnackbarHostState()
            val snackbarMessage by achievementsViewModel.newAchievementMessage.collectAsState()
            
            // Mostrar Snackbar cuando hay un logro nuevo
            LaunchedEffect(snackbarMessage) {
                snackbarMessage?.let { message ->
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = "Ver",
                        duration = androidx.compose.material3.SnackbarDuration.Long,
                        withDismissAction = true
                    )
                    
                    // Si el usuario pulsa "Ver", navegar a la pantalla de logros
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        navController.navigate(Screen.Achievements.route) {
                            launchSingleTop = true
                        }
                    }
                    
                    achievementsViewModel.clearSnackbarMessage()
                }
            }
            
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { 
                    androidx.compose.material3.SnackbarHost(
                        hostState = snackbarHostState,
                        snackbar = { data ->
                            androidx.compose.material3.Snackbar(
                                snackbarData = data,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                actionColor = MaterialTheme.colorScheme.primary,
                                dismissActionContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                        }
                    )
                },
                bottomBar = {
                    if (currentRoute !in listOf(Screen.Login.route, Screen.Register.route)) {
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
                        },
                        authViewModel = authViewModel
                    )
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
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                // Android 5 y anteriores - no se necesita permiso explícito
                true
            }
            
            Log.d("MainActivity", "Permiso de almacenamiento: $hasStoragePermission")
            
            if (!hasStoragePermission) {
                Log.e("MainActivity", "No se tiene permiso de almacenamiento")
                
                // Solicitar permiso de almacenamiento solo para Android 6-9
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        STORAGE_PERMISSION_REQUEST_CODE
                    )
                } else {
                    Toast.makeText(this, "Se necesita permiso de almacenamiento para exportar", Toast.LENGTH_LONG).show()
                }
                return
            }

            // Mostrar notificación de progreso solo si tenemos permiso
            if (hasNotificationPermission) {
                val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle("Exportando registros")
                    .setProgress(0, 0, true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                notificationManager.notify(NOTIFICATION_ID, builder.build())
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

                        if (hasNotificationPermission) {
                            showCompletionNotification(itemUri)
                        } else {
                            Toast.makeText(this, "Archivo exportado exitosamente a Descargas", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.e("MainActivity", "No se pudo crear URI en MediaStore")
                        Toast.makeText(this, "Error: No se pudo crear el archivo en Descargas", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error en MediaStore", e)
                    Toast.makeText(this, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
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
                    this,
                    "${packageName}.provider",
                    destinationFile
                )
                if (hasNotificationPermission) {
                    showCompletionNotification(uri)
                } else {
                    Toast.makeText(this, "Archivo exportado exitosamente a Descargas", Toast.LENGTH_LONG).show()
                }
            }

            // Eliminar el archivo temporal
            file.delete()
        } catch (e: IOException) {
            if (hasNotificationPermission) {
                notificationManager.cancel(NOTIFICATION_ID)
            }
            Toast.makeText(this, "Error al guardar el archivo: ${e.message}", Toast.LENGTH_LONG).show()
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
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permiso concedido. Intenta exportar nuevamente.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Permiso denegado. No se puede exportar sin acceso al almacenamiento.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1002
    }
}