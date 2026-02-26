package com.zipstats.app.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.model.Record
import com.zipstats.app.model.Repair
import com.zipstats.app.model.Scooter
import com.zipstats.app.ui.components.DialogCancelButton
import com.zipstats.app.ui.components.DialogDeleteButton
import com.zipstats.app.ui.components.StandardDatePickerDialogWithValidation
import com.zipstats.app.ui.components.ZipStatsText
import com.zipstats.app.utils.DateUtils
import com.zipstats.app.utils.LocationUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScooterDetailScreen(
    navController: NavController,
    scooterId: String,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    // Estados para datos asíncronos
    var lastRepair by remember { mutableStateOf<Repair?>(null) }
    var lastRecord by remember { mutableStateOf<Record?>(null) }
    var usagePercentage by remember { mutableDoubleStateOf(0.0) } // Estado para el porcentaje
    var isLoadingDetails by remember { mutableStateOf(true) }

    val uiState by viewModel.uiState.collectAsState()

    val scope = rememberCoroutineScope() // Para lanzar la corrutina
    val scooter = (uiState as? ProfileUiState.Success)?.scooters?.find { it.id == scooterId }

    var isDeleting by remember { mutableStateOf(false) } // Para bloquear la UI

    // Cargar datos adicionales
    LaunchedEffect(scooterId, scooter?.nombre) {
        if (scooter != null) {
            // 1. Asegurar que tenemos los detalles
            viewModel.loadScooterDetails(scooterId)

            // 2. Cargar datos calculados usando el scooterId (permanente)
            lastRepair = viewModel.getLastRepair(scooterId)
            lastRecord = viewModel.getLastRecord(scooterId = scooterId, vehicleName = scooter.nombre)
            usagePercentage = viewModel.getVehicleUsagePercentage(scooterId = scooterId, vehicleName = scooter.nombre)

            isLoadingDetails = false
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) } // Estado para el menú de 3 puntos
    var showUsageExplanationDialog by remember { mutableStateOf(false) } // Estado para el diálogo de uso

    // 🚩 AQUÍ COLOCAS EL NUEVO CÓDIGO:
    var vehiclesForRanking by remember { mutableStateOf<List<Scooter>>(emptyList()) }

    LaunchedEffect(Unit) {
        // Esto se ejecuta una sola vez cuando se abre la pantalla
        vehiclesForRanking = viewModel.getAllVehiclesWithTotals()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    ZipStatsText(
                        text = "Detalles del Vehículo",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Menú de 3 puntos
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { ZipStatsText("Editar vehículo") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                showMenu = false
                                showEditDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { ZipStatsText("Eliminar vehículo", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
        // FAB ELIMINADO (Ya no es necesario)
    ) { padding ->
        if (scooter != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. TARJETA HERO DEL VEHÍCULO
                val userName = (uiState as? ProfileUiState.Success)?.user?.name ?: "tu"
                VehicleHeroCard(
                    scooter = scooter,
                    lastRecordDate = lastRecord?.fecha,
                    usagePercentage = usagePercentage,
                    onUsageClick = { showUsageExplanationDialog = true }
                )

                // 2. ESPECIFICACIONES (Grid de datos)
                VehicleSpecsSection(scooter = scooter)

                // 3. MANTENIMIENTO (Tarjeta inteligente clicable)
                MaintenanceSection(
                    lastRepair = lastRepair,
                    onHistoryClick = {
                        navController.navigate("repairs/$scooterId")
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    // Bottom Sheet de EDICIÓN
    val editSheetState = rememberModalBottomSheetState()
    if (showEditDialog && scooter != null) {
        ModalBottomSheet(
            onDismissRequest = { showEditDialog = false },
            sheetState = editSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            EditScooterBottomSheet(
                scooter = scooter,
                onDismiss = {
                    scope.launch {
                        editSheetState.hide()
                        showEditDialog = false
                    }
                },
                onConfirm = { nombre, marca, modelo, fechaCompra, matricula ->
                    scope.launch {
                        viewModel.updateScooter(
                            scooter.id,
                            nombre,
                            marca,
                            modelo,
                            fechaCompra,
                            matricula
                        )
                        // Esperar un momento para que se actualice el estado
                        kotlinx.coroutines.delay(300)
                        // Recargar datos si es necesario
                        lastRepair = viewModel.getLastRepair(scooterId)
                        lastRecord = viewModel.getLastRecord(scooterId = scooterId, vehicleName = nombre)
                        usagePercentage = viewModel.getVehicleUsagePercentage(scooterId = scooterId, vehicleName = nombre)
                        editSheetState.hide()
                        showEditDialog = false
                    }
                }
            )
        }
    }

    // Diálogo de USO RELATIVO (fuera de la estructura visual)
    if (showUsageExplanationDialog && scooter != null) {
        val userName = (uiState as? ProfileUiState.Success)?.user?.name ?: "tu"
        val allScooters = (uiState as? ProfileUiState.Success)?.scooters ?: emptyList()
        UsageExplanationDialog(
            vehicleName = scooter.nombre,
            vehicleKm = scooter.kilometrajeActual ?: 0.0,
            usagePercentage = usagePercentage.toInt(),
            allVehicles = vehiclesForRanking,
            onDismiss = { showUsageExplanationDialog = false }
        )
    }

    // Diálogo de ELIMINACIÓN
// Diálogo de ELIMINACIÓN (Versión Corregida)
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false }, // Bloquear cierre si borra
            title = { ZipStatsText("Eliminar vehículo") },
            text = { ZipStatsText("¿Estás seguro? Se eliminará el vehículo y todo su historial de mantenimiento. Esta acción no se puede deshacer.") },
            confirmButton = {
                DialogDeleteButton(
                    text = if (isDeleting) "Borrando..." else "Eliminar",
                    // Si tu botón no soporta 'enabled', quita la siguiente línea
                    // enabled = !isDeleting,
                    onClick = {
                        if (isDeleting) return@DialogDeleteButton

                        isDeleting = true // 1. Bloquear UI

                        // 2. LANZAR CORRUTINA (Esto soluciona el error del delay)
                        scope.launch {
                            try {
                                viewModel.deleteScooter(scooterId)

                                delay(500) // Ahora sí funciona porque está dentro de launch {}

                                showDeleteDialog = false
                                navController.navigateUp()
                            } catch (e: Exception) {
                                isDeleting = false
                                e.printStackTrace()
                            }
                        }
                    }
                )
            },
            dismissButton = {
                if (!isDeleting) {
                    DialogCancelButton(text = "Cancelar", onClick = { showDeleteDialog = false })
                }
            }
        )
    }
}

// ----------------------------------------------------------------
// COMPONENTES DE DISEÑO
// ----------------------------------------------------------------

@Composable
fun VehicleHeroCard(
    scooter: Scooter,
    lastRecordDate: String?,
    usagePercentage: Double,
    onUsageClick: () -> Unit
) {
    // Degradado sutil y elegante
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(24.dp)
        ) {
            Column {
                // Cabecera - Nombre del vehículo con más espacio
                Column(modifier = Modifier.fillMaxWidth()) {
                    ZipStatsText(
                        text = scooter.nombre,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    // Mostrar fecha del último viaje si existe
                    val lastTripText = if (lastRecordDate != null) {
                        "Último viaje: ${DateUtils.formatForDisplay(DateUtils.parseApiDate(lastRecordDate))}"
                    } else {
                        "Sin viajes registrados"
                    }

                    ZipStatsText(
                        text = lastTripText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Estadísticas Principales
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        ZipStatsText(
                            text = "Kilometraje Total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        ZipStatsText(
                            text = "${com.zipstats.app.utils.LocationUtils.formatNumberSpanish(scooter.kilometrajeActual ?: 0.0, 1)} km",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Icono con círculo de porcentaje alrededor (clickeable)
                    val progress = (usagePercentage / 100.0).toFloat().coerceIn(0f, 1f)
                    UsageCircularIndicatorWithIcon(
                        percentage = progress,
                        displayValue = usagePercentage.toInt(),
                        vehicleIcon = getVehicleIcon(scooter.vehicleType),
                        onClick = onUsageClick
                    )
                }
            }
        }
    }
}

@Composable
fun UsageCircularIndicatorWithIcon(
    percentage: Float,
    displayValue: Int,
    vehicleIcon: Painter,
    onClick: () -> Unit
) {
    val clickInteractionSource = remember { MutableInteractionSource() }
    
    // Contenedor principal con tamaño fijo para evitar problemas de layout
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(100.dp, 120.dp) // Ancho fijo, altura suficiente para incluir el texto
            .clickable(
                interactionSource = clickInteractionSource,
                indication = null, // Sin animación de ripple
                onClick = onClick
            )
    ) {
        // Círculo de fondo (track)
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(80.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
            strokeWidth = 8.dp,
            trackColor = Color.Transparent,
        )
        // Círculo de progreso
        CircularProgressIndicator(
            progress = { percentage },
            modifier = Modifier.size(80.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            strokeWidth = 8.dp,
            strokeCap = StrokeCap.Round
        )
        // Icono del vehículo en el centro
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = vehicleIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        // Etiqueta de porcentaje debajo
        ZipStatsText(
            text = "$displayValue%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
    }
}

@Composable
fun UsageExplanationDialog(
    vehicleName: String,
    vehicleKm: Double,
    usagePercentage: Int,
    allVehicles: List<Scooter>,
    onDismiss: () -> Unit
) {
    // 1. Ranking (ya con kms hidratados)
    val rankedVehicles = remember(allVehicles) {
        allVehicles.sortedByDescending { it.kilometrajeActual ?: 0.0 }
    }

    val vehiclePosition = remember(rankedVehicles, vehicleName) {
        val index = rankedVehicles.indexOfFirst { it.nombre.trim().equals(vehicleName.trim(), true) }
        if (index != -1) index + 1 else 1
    }

    val explanationText = remember(vehiclePosition, vehicleName, vehicleKm, usagePercentage) {
        val kmFormatted = LocationUtils.formatNumberSpanish(vehicleKm, 1)

        // Solo definimos la medalla y la frase introductoria
        val (medal, intro) = when (vehiclePosition) {
            1 -> "🥇" to "¡El rey de la ruta! Tu $vehicleName lidera el historial"
            2 -> "🥈" to "¡Muy cerca! Tu $vehicleName es tu segunda opción favorita"
            3 -> "🥉" to "En el podio: Tu $vehicleName ocupa el tercer lugar"
            else -> "🚲" to "Tu $vehicleName ocupa la posición $vehiclePosition"
        }

        buildAnnotatedString {
            // 1. Medalla y frase de posición en negrita
            append("$medal ")
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(intro)
            }

            append(" con ")

            // 2. Kilómetros en negrita
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append("$kmFormatted km")
            }

            append(". Representa un ")

            // 3. Porcentaje en negrita
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append("$usagePercentage%")
            }

            append(" de tu actividad total.")
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ZipStatsText(
                    text = "Cuota de uso",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = explanationText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = Int.MAX_VALUE,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            DialogCancelButton(
                text = "Entendido",
                onClick = onDismiss
            )
        }
    )
}

/**
 * Convierte un número a su forma ordinal en español (1 -> "primer", 2 -> "segundo", etc.)
 */
private fun getOrdinalNumber(position: Int): String {
    return when (position) {
        1 -> "primer"
        2 -> "segundo"
        3 -> "tercer"
        4 -> "cuarto"
        5 -> "quinto"
        6 -> "sexto"
        7 -> "séptimo"
        8 -> "octavo"
        9 -> "noveno"
        10 -> "décimo"
        else -> "${position}º"
    }
}

@Composable
fun VehicleSpecsSection(scooter: Scooter) {
    Column {
        ZipStatsText(
            text = "Información Técnica",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp), // Esquinas ligeramente más orgánicas
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                // Fila Superior: Los 3 datos fijos
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpecItem(Icons.AutoMirrored.Filled.Label, "Marca", scooter.marca, Modifier.weight(1f))
                    VerticalDivider(modifier = Modifier.height(30.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SpecItem(Icons.Default.Info, "Modelo", scooter.modelo, Modifier.weight(1f))
                    VerticalDivider(modifier = Modifier.height(30.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SpecItem(Icons.Default.CalendarMonth, "Año", DateUtils.formatYear(scooter.fechaCompra), Modifier.weight(1f))
                }

                // Sección Matrícula: Solo si existe
                if (!scooter.matricula.isNullOrBlank()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp).padding(horizontal = 24.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Aquí le damos un diseño más de "Identificación"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "MATRÍCULA: ",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = scooter.matricula!!.uppercase(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VerticalDivider() {
    Box(
        modifier = Modifier
            .height(40.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
fun SpecItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier // <--- Añadimos esto
) {
    Column(
        modifier = modifier, // <--- Lo aplicamos aquí
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ZipStatsText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        ZipStatsText(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun MaintenanceSection(
    lastRepair: Repair?,
    onHistoryClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZipStatsText(
                text = "Mantenimiento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        val historyClickInteractionSource = remember { MutableInteractionSource() }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = historyClickInteractionSource,
                    indication = null,
                    onClick = onHistoryClick
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (lastRepair != null) {
                        ZipStatsText(
                            text = "Última reparación",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        ZipStatsText(
                            text = lastRepair.description,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        ZipStatsText(
                            text = "${DateUtils.formatForDisplay(lastRepair.date)} • ${LocationUtils.formatNumberSpanish(lastRepair.mileage ?: 0.0, 0)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        ZipStatsText(
                            text = "Sin registros",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ZipStatsText(
                            text = "Toca para registrar mantenimiento",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Ver más",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScooterBottomSheet(
    scooter: Scooter,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var nombre by remember { mutableStateOf(scooter.nombre) }
    var marca by remember { mutableStateOf(scooter.marca) }
    var modelo by remember { mutableStateOf(scooter.modelo) }
    var matricula by remember { mutableStateOf(scooter.matricula ?: "") }

    // 1. Mejoramos el parseo inicial
    var selectedDate by remember {
        mutableStateOf(
            try {
                if (!scooter.fechaCompra.isNullOrBlank()) {
                    // Intenta parsear el formato que viene de la base de datos (API)
                    DateUtils.parseApiDate(scooter.fechaCompra)
                } else {
                    LocalDate.now()
                }
            } catch (e: Exception) {
                // Si falla, intenta el de display por si acaso
                try { DateUtils.parseFullDisplayDate(scooter.fechaCompra!!) }
                catch (e2: Exception) { LocalDate.now() }
            }
        )
    }

    var fechaTexto by remember {
        mutableStateOf(DateUtils.formatFullDisplayDate(scooter.fechaCompra))
    }
    var showDatePicker by remember { mutableStateOf(false) }


    if (showDatePicker) {
        StandardDatePickerDialogWithValidation(
            selectedDate = selectedDate,
            onDateSelected = { date ->
                selectedDate = date
                // ACTUALIZACIÓN MANUAL: Solo ocurre cuando el usuario toca el calendario
                fechaTexto = DateUtils.formatFullDisplayDate(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
            title = "Fecha de compra",
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
            text = "Editar vehículo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

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
            value = matricula,
            onValueChange = { matricula = it },
            label = { ZipStatsText("Matrícula") },
            placeholder = { ZipStatsText(text = "Opcional")},
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
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                ZipStatsText(
                    text = "Cancelar",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            val isEnabled = nombre.isNotBlank() && marca.isNotBlank() && modelo.isNotBlank()
            Button(
                onClick = {
                    onConfirm(nombre, marca, modelo, fechaTexto, matricula)

                },
                modifier = Modifier.weight(1f),
                enabled = isEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                ZipStatsText(
                    "Guardar",
                    color = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}