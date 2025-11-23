package com.zipstats.app.ui.repairs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.model.Repair
import com.zipstats.app.model.Scooter
import com.zipstats.app.ui.components.DialogDeleteButton
import com.zipstats.app.ui.components.DialogNeutralButton
import com.zipstats.app.ui.components.DialogSaveButton
import com.zipstats.app.ui.components.StandardDatePickerDialog
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.utils.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairsScreen(
    navController: NavController,
    scooterId: String,
    viewModel: RepairsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var scooter by remember { mutableStateOf<Scooter?>(null) }

    // Estados UI
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var selectedRepair by remember { mutableStateOf<Repair?>(null) }

    // Estado secundario para diálogos de edición/borrado derivados de la selección
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // Fecha actual por defecto
    val today = LocalDate.now()

    // Campos temporales para formularios
    var tempDate by remember { mutableStateOf(today) }
    var tempDesc by remember { mutableStateOf("") }
    var tempMileage by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    // Cargar datos
    LaunchedEffect(scooterId) {
        viewModel.loadScooterAndRepairs(scooterId)
    }

    val loadedScooter by viewModel.scooterState.collectAsState()
    LaunchedEffect(loadedScooter) {
        scooter = loadedScooter
    }

    // Efecto para abrir el diálogo de edición cuando se selecciona una reparación
    LaunchedEffect(selectedRepair) {
        if (selectedRepair != null) {
            tempDate = selectedRepair!!.date
            tempDesc = selectedRepair!!.description
            tempMileage = selectedRepair!!.mileage?.toString() ?: ""
            showEditDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mantenimiento", fontWeight = FontWeight.Bold)
                        scooter?.let {
                            Text(
                                text = "${it.marca} ${it.modelo}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Resetear campos para nueva entrada
                    tempDate = LocalDate.now()
                    tempDesc = ""
                    tempMileage = "" // Opcional: pre-cargar kilometraje actual del scooter
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir reparación")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (uiState) {
                is RepairsUiState.Loading -> {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is RepairsUiState.Success -> {
                    val repairs = (uiState as RepairsUiState.Success).repairs.sortedByDescending { it.date }

                    if (repairs.isEmpty()) {
                        EmptyRepairsState()
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(repairs, key = { it.id }) { repair ->
                                RepairItemCard(
                                    repair = repair,
                                    onClick = { selectedRepair = repair }
                                )
                            }
                            // Espacio extra para el FAB
                            item { Spacer(modifier = Modifier.height(72.dp)) }
                        }
                    }
                }

                is RepairsUiState.Error -> {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (uiState as RepairsUiState.Error).message,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // --- DIÁLOGOS ---

    // 1. DIÁLOGO AÑADIR / EDITAR (Reutilizable)
    if (showAddDialog || showEditDialog) {
        val isEditing = showEditDialog
        val titleText = if (isEditing) "Editar reparación" else "Nueva reparación"

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                showEditDialog = false
                if (isEditing) selectedRepair = null
            },
            title = { Text(titleText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Selector de Fecha
                    OutlinedTextField(
                        value = DateUtils.formatForDisplay(tempDate),
                        onValueChange = {},
                        label = { Text("Fecha") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarMonth, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Descripción
                    OutlinedTextField(
                        value = tempDesc,
                        onValueChange = { tempDesc = it },
                        label = { Text("Descripción") },
                        placeholder = { Text("Ej: Cambio de pastillas de freno") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        minLines = 2,
                        maxLines = 4
                    )

                    // Kilometraje
                    OutlinedTextField(
                        value = tempMileage,
                        onValueChange = {
                            // Solo permitir números y un punto decimal
                            if (it.all { char -> char.isDigit() || char == '.' || char == ',' }) {
                                tempMileage = it.replace(',', '.')
                            }
                        },
                        label = { Text("Kilometraje (Opcional)") },
                        placeholder = { Text("Km al momento de la reparación") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        trailingIcon = { Text("km ", style = MaterialTheme.typography.bodySmall) }
                    )
                }
            },
            confirmButton = {
                DialogSaveButton(
                    text = "Guardar",
                    enabled = tempDesc.isNotBlank(),
                    onClick = {
                        val mileage = tempMileage.toDoubleOrNull()

                        if (isEditing && selectedRepair != null) {
                            viewModel.updateRepair(
                                selectedRepair!!.copy(
                                    date = tempDate,
                                    description = tempDesc,
                                    mileage = mileage
                                )
                            )
                        } else {
                            scooter?.let { loaded ->
                                viewModel.addRepair(
                                    date = tempDate,
                                    description = tempDesc,
                                    mileage = mileage,
                                    scooterName = loaded.nombre,
                                    scooterId = loaded.id
                                )
                            }
                        }

                        // Cerrar y limpiar
                        showAddDialog = false
                        showEditDialog = false
                        selectedRepair = null
                        tempDesc = ""
                        tempMileage = ""
                    }
                )
            },
            dismissButton = {
                if (isEditing) {
                    Row {
                        // Botón especial para borrar dentro del diálogo de edición
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Eliminar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        DialogNeutralButton(
                            text = "Cancelar",
                            onClick = {
                                showEditDialog = false
                                selectedRepair = null
                            }
                        )
                    }
                } else {
                    DialogNeutralButton(
                        text = "Cancelar",
                        onClick = { showAddDialog = false }
                    )
                }
            },
            shape = DialogShape
        )
    }

    // 2. DATE PICKER
    if (showDatePicker) {
        StandardDatePickerDialog(
            selectedDate = tempDate,
            onDateSelected = {
                tempDate = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
            title = "Fecha de la reparación"
        )
    }

    // 3. CONFIRMACIÓN DE BORRADO
    if (showDeleteDialog && selectedRepair != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar registro") },
            text = { Text("¿Estás seguro de que quieres eliminar esta reparación del historial?") },
            confirmButton = {
                DialogDeleteButton(
                    text = "Eliminar",
                    onClick = {
                        viewModel.deleteRepair(selectedRepair!!.id)
                        selectedRepair = null
                        showEditDialog = false // También cerramos el de edición si estaba abierto
                        showDeleteDialog = false
                    }
                )
            },
            dismissButton = {
                DialogNeutralButton(
                    text = "Cancelar",
                    onClick = { showDeleteDialog = false }
                )
            },
            shape = DialogShape
        )
    }
}

// ============================================================
// COMPONENTES UI
// ============================================================

@Composable
fun RepairItemCard(
    repair: Repair,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icono con fondo circular
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Contenido
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateUtils.formatForDisplay(repair.date), // Usar helper de fecha
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    // Kilometraje si existe
                    repair.mileage?.let { km ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${String.format("%.0f", km)} km",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = repair.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Icono de edición sutil
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Editar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun EmptyRepairsState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sin historial de mantenimiento",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Añade reparaciones, cambios de piezas\no revisiones periódicas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}