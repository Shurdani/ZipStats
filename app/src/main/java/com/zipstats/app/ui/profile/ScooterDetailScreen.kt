package com.zipstats.app.ui.profile

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.R
import com.zipstats.app.model.Record
import com.zipstats.app.model.Repair
import com.zipstats.app.model.Scooter
import com.zipstats.app.ui.components.DialogCancelButton
import com.zipstats.app.ui.components.DialogConfirmButton
import com.zipstats.app.ui.components.DialogDeleteButton
import com.zipstats.app.ui.components.StandardDatePickerDialogWithValidation
import com.zipstats.app.utils.DateUtils
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
    LaunchedEffect(scooterId, scooter) {
        if (scooter != null) {
            // 1. Asegurar que tenemos los detalles
            viewModel.loadScooterDetails(scooterId)

            // 2. Cargar datos calculados
            lastRepair = viewModel.getLastRepair(scooterId)
            lastRecord = viewModel.getLastRecord(scooter.nombre)
            usagePercentage = viewModel.getVehicleUsagePercentage(scooter.nombre)

            isLoadingDetails = false
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) } // Estado para el menú de 3 puntos

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalles del Vehículo", fontWeight = FontWeight.Bold) },
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
                            text = { Text("Editar vehículo") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                showMenu = false
                                showEditDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar vehículo", color = MaterialTheme.colorScheme.error) },
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
                VehicleHeroCard(
                    scooter = scooter,
                    lastRecordDate = lastRecord?.fecha,
                    usagePercentage = usagePercentage
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

    // Diálogo de EDICIÓN
    if (showEditDialog && scooter != null) {
        EditScooterDialog(
            scooter = scooter,
            onDismiss = { showEditDialog = false },
            onConfirm = { nombre, marca, modelo, fecha ->
                viewModel.updateScooter(scooter.id, nombre, marca, modelo, fecha)
                showEditDialog = false
            }
        )
    }

    // Diálogo de ELIMINACIÓN
// Diálogo de ELIMINACIÓN (Versión Corregida)
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false }, // Bloquear cierre si borra
            title = { Text("Eliminar vehículo") },
            text = { Text("¿Estás seguro? Se eliminará el vehículo y todo su historial de mantenimiento. Esta acción no se puede deshacer.") },
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
    usagePercentage: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.padding(24.dp)) {
            // Fondo decorativo
            Icon(
                imageVector = Icons.Default.ElectricScooter,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(140.dp)
                    .offset(x = 30.dp, y = (-30).dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)
            )

            Column {
                // Cabecera
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_electric_scooter_adaptive),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = scooter.nombre,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        // Mostrar fecha del último viaje si existe
                        val lastTripText = if (lastRecordDate != null) {
                            "Último viaje: ${DateUtils.formatForDisplay(DateUtils.parseApiDate(lastRecordDate))}"
                        } else {
                            "Sin viajes registrados"
                        }

                        Text(
                            text = lastTripText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Estadísticas Principales
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Kilometraje Total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "${String.format("%.1f", scooter.kilometrajeActual ?: 0.0)} km",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            letterSpacing = (-1).sp
                        )
                    }

                    // Indicador visual con porcentaje REAL (convertido a 0.0 - 1.0)
                    // Si usagePercentage es > 100 (posible si es relativo a otros), lo capeamos visualmente
                    val progress = (usagePercentage / 100.0).toFloat().coerceIn(0f, 1f)
                    UsageCircularIndicator(percentage = progress, displayValue = usagePercentage.toInt())
                }
            }
        }
    }
}

@Composable
fun UsageCircularIndicator(percentage: Float, displayValue: Int) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(72.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
            strokeWidth = 8.dp,
            trackColor = Color.Transparent,
        )
        CircularProgressIndicator(
            progress = { percentage },
            modifier = Modifier.size(72.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            strokeWidth = 8.dp,
            strokeCap = StrokeCap.Round
        )
        Text(
            text = "$displayValue%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun VehicleSpecsSection(scooter: Scooter) {
    Column {
        Text(
            text = "Información Técnica",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SpecItem(Icons.AutoMirrored.Filled.Label, "Marca", scooter.marca)
                VerticalDivider()
                SpecItem(Icons.Default.Info, "Modelo", scooter.modelo)
                VerticalDivider()
                SpecItem(Icons.Default.CalendarMonth, "Adquirido", scooter.fechaCompra ?: "-")
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
fun SpecItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
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
            Text(
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
                        Text(
                            text = "Última reparación",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = lastRepair.description,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${DateUtils.formatForDisplay(lastRepair.date)} • ${String.format("%.0f", lastRepair.mileage ?: 0.0)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Sin registros",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
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
fun EditScooterDialog(
    scooter: Scooter,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var nombre by remember { mutableStateOf(scooter.nombre) }
    var marca by remember { mutableStateOf(scooter.marca) }
    var modelo by remember { mutableStateOf(scooter.modelo) }

    // Parsear fecha para el picker
    var selectedDate by remember {
        mutableStateOf(
            try {
                if (!scooter.fechaCompra.isNullOrBlank()) DateUtils.parseDisplayDate(scooter.fechaCompra)
                else LocalDate.now()
            } catch (e: Exception) {
                LocalDate.now()
            }
        )
    }

    var fechaTexto by remember { mutableStateOf(scooter.fechaCompra ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDate) {
        fechaTexto = DateUtils.formatForDisplay(selectedDate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar vehículo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    onValueChange = { },
                    label = { Text("Fecha de compra") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = "Guardar",
                enabled = nombre.isNotBlank() && marca.isNotBlank() && modelo.isNotBlank(),
                onClick = {
                    onConfirm(nombre, marca, modelo, fechaTexto)
                }
            )
        },
        dismissButton = {
            DialogCancelButton(text = "Cancelar", onClick = onDismiss)
        }
    )

    if (showDatePicker) {
        StandardDatePickerDialogWithValidation(
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it },
            onDismiss = { showDatePicker = false },
            title = "Fecha de compra",
            maxDate = LocalDate.now(),
            validateDate = { !it.isAfter(LocalDate.now()) }
        )
    }
}