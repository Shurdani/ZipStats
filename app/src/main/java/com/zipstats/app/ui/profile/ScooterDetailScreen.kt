package com.zipstats.app.ui.profile

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.R
import com.zipstats.app.model.VehicleType
import com.zipstats.app.navigation.Screen
import com.zipstats.app.utils.DateUtils
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScooterDetailScreen(
    navController: NavController,
    scooterId: String,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var vehicleStats by remember { mutableStateOf<VehicleDetailedStats?>(null) }
    var isLoadingStats by remember { mutableStateOf(true) }
    var showEditSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val scooter = when (val state = uiState) {
        is ProfileUiState.Success -> {
            state.scooters.find { it.id == scooterId }
        }
        else -> null
    }

    // Cargar estadísticas detalladas del vehículo
    LaunchedEffect(scooter) {
        if (scooter != null) {
            isLoadingStats = true
            vehicleStats = viewModel.getVehicleDetailedStats(scooter.id, scooter.nombre)
            isLoadingStats = false
        }
    }

    if (showDeleteDialog && scooter != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar vehículo") },
            text = {
                Text(
                    "¿Estás seguro de que quieres eliminar este vehículo? " +
                            "Esta acción también eliminará todos los registros asociados."
                )
            },

            confirmButton = {
                DialogDeleteButton(
                    text = "Eliminar",
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            try {
                                // Ejecutar la eliminación y esperar a que termine
                                viewModel.deleteScooter(scooterId)

                                // Pequeño delay para garantizar sincronización
                                kotlinx.coroutines.delay(300)

                                // Navegar al terminar
                                navController.navigateUp()
                            } catch (e: Exception) {
                                android.util.Log.e("ScooterDetailScreen", "Error durante eliminación", e)
                            }
                        }
                    }
                )
            },

            dismissButton = {
                DialogCancelButton(
                    text = "Cancelar",
                    onClick = { showDeleteDialog = false }
                )
            },

            shape = DialogShape
        )
    }

    // Diálogo para editar el vehículo
    if (showEditSheet && scooter != null) {
        EditScooterDialog(
            scooter = scooter,
            onDismiss = { showEditSheet = false },
            onSave = { nombre, marca, modelo, fechaCompra ->
                viewModel.updateScooter(scooterId, nombre, marca, modelo, fechaCompra)
                showEditSheet = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Detalles del vehículo") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Más opciones"
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar vehículo") },
                            onClick = {
                                showMenu = false
                                showEditSheet = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editar"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    "Eliminar vehículo",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        when (uiState) {
            is ProfileUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            is ProfileUiState.Success -> {
                if (scooter != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                // Encabezado del vehículo
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Fila principal con nombre del vehículo e icono
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = scooter.nombre,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                
                                if (vehicleStats?.lastRecord != null && !isLoadingStats) {
                                    val lastRecord = vehicleStats?.lastRecord
                                    Row(
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text(
                                            text = "Último viaje: ${lastRecord?.fecha ?: ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                            
                            Image(
                                painter = getVehicleIcon(scooter.vehicleType),
                                contentDescription = "Icono del vehículo",
                                modifier = Modifier.size(56.dp),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
                            )
                        }
                        
                        // Separador visual
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                            thickness = 1.dp
                        )
                        
                        // Fila con kilometraje y porcentaje de uso
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "${String.format("%.1f", scooter.kilometrajeActual ?: 0.0)} km",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                
                                androidx.compose.material3.Text(
                                    text = "|",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Light
                                )
                                
                                Column {
                                    Text(
                                        text = "Ultimo viaje",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Light,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                    )
                                    if (vehicleStats?.lastRecord != null && !isLoadingStats) {
                                        val lastRecord = vehicleStats?.lastRecord
                                        Text(
                                            text = "${String.format("%.1f", lastRecord?.diferencia ?: 0.0)} km",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Círculo con porcentaje de uso
                            if (!isLoadingStats && vehicleStats != null) {
                                val percentage = vehicleStats?.usagePercentage ?: 0.0
                                if (percentage > 0) {
                                    Column(
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                                                    androidx.compose.foundation.shape.CircleShape
                                                ),
                                            contentAlignment = androidx.compose.ui.Alignment.Center
                                        ) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                progress = { (percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                                                modifier = Modifier.size(50.dp),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                                                strokeWidth = 3.dp,
                                                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                            )
                                            Text(
                                                text = "${String.format("%.0f", percentage)}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Uso sobre el total",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Light,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                                    androidx.compose.foundation.shape.CircleShape
                                                ),
                                            contentAlignment = androidx.compose.ui.Alignment.Center
                                        ) {
                                            Text(
                                                text = "0%",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Uso sobre el total",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Light,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            } else if (isLoadingStats) {
                                Column(
                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(40.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Uso sobre el total",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Light,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Detalles
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Información",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        InfoRow(label = "Marca", value = scooter.marca)
                        InfoRow(label = "Modelo", value = scooter.modelo)
                        InfoRow(
                            label = "Fecha de compra",
                            value = DateUtils.formatForDisplay(DateUtils.parseApiDate(scooter.fechaCompra))
                        )
                    }
                }
                
                // Mantenimiento
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        // Título de la sección
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    navController.navigate("${Screen.Repairs.route}/${scooter.id}")
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Build,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "Mantenimiento",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Ver todo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        // Última reparación
                        if (vehicleStats?.lastRepair != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Última reparación",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            val lastRepair = vehicleStats?.lastRepair
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lastRepair?.getFormattedDate() ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = lastRepair?.description ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Normal
                                )
                                lastRepair?.mileage?.let { mileage ->
                                    Text(
                                        text = "—",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                                    Text(
                                        text = "${String.format("%.0f", mileage)} km",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Vehículo no encontrado",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            is ProfileUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(
                        text = (uiState as ProfileUiState.Error).message,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditScooterDialog(
    scooter: com.zipstats.app.model.Scooter,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var nombre by remember { mutableStateOf(scooter.nombre) }
    var marca by remember { mutableStateOf(scooter.marca) }
    var modelo by remember { mutableStateOf(scooter.modelo) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { 
        mutableStateOf(
            if (scooter.fechaCompra.isNotEmpty()) {
                DateUtils.parseApiDate(scooter.fechaCompra)
            } else {
                LocalDate.now()
            }
        )
    }
    var fechaTexto by remember { mutableStateOf(DateUtils.formatForDisplay(selectedDate)) }
    var fechaError by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDate) {
        fechaTexto = DateUtils.formatForDisplay(selectedDate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar vehículo") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                            val parsedDate = DateUtils.parseDisplayDate(nuevoTexto)
                            if (parsedDate.isAfter(LocalDate.now())) {
                                fechaError = "La fecha no puede ser futura"
                            } else {
                                fechaError = null
                                selectedDate = parsedDate
                            }
                        } catch (e: Exception) {
                            fechaError = "Fecha inválida"
                        }
                    },
                    label = { Text("Fecha de compra") },
                    isError = fechaError != null,
                    supportingText = fechaError?.let { { Text(it) } },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showError) {
                    Text(
                        text = "Por favor, complete todos los campos",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },

        confirmButton = {
            DialogConfirmButton(
                text = "Guardar",
                onClick = {
                    if (nombre.isBlank() || marca.isBlank() || modelo.isBlank() || fechaError != null) {
                        showError = true
                    } else {
                        onSave(nombre, marca, modelo, fechaTexto)
                        showError = false
                    }
                },
                enabled = nombre.isNotBlank() &&
                        marca.isNotBlank() &&
                        modelo.isNotBlank() &&
                        fechaError == null
            )
        },

        dismissButton = {
            DialogCancelButton(
                text = "Cancelar",
                onClick = onDismiss
            )
        },

        shape = DialogShape
    )

    if (showDatePicker) {
        com.zipstats.app.ui.components.StandardDatePickerDialogWithValidation(
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
