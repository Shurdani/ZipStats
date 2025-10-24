package com.zipstats.app.ui.records

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.model.Record
import com.zipstats.app.navigation.Screen
import com.zipstats.app.ui.components.StandardDatePickerDialogWithValidation
import com.zipstats.app.utils.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsHistoryScreen(
    navController: NavController,
    viewModel: RecordsViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsState()
    val userScooters by viewModel.userScooters.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    var recordToDelete by remember { mutableStateOf<Record?>(null) }
    var recordToEdit by remember { mutableStateOf<Record?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    
    // Estado para controlar el scroll de la lista
    val listState = rememberLazyListState()
    
    // Variable para detectar cuando se añade un registro
    var previousRecordsSize by remember { mutableStateOf(records.size) }

    // Filtrar registros según el patinete seleccionado
    val filteredRecords = remember(records, selectedModel, userScooters) {
        when (selectedModel) {
            null -> records
            else -> records.filter { record ->
                val matchingScooter = userScooters.find { it.nombre == record.patinete }
                matchingScooter?.modelo == selectedModel
            }
        }
    }
    
    // Scroll automático al principio cuando se añade un nuevo registro
    LaunchedEffect(records.size) {
        if (records.size > previousRecordsSize && filteredRecords.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
        previousRecordsSize = records.size
    }

    // Diálogo de confirmación para borrar
    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Confirmar eliminación") },
            text = { Text("¿Estás seguro de que quieres eliminar este registro?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRecord(record.id)
                        recordToDelete = null
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo con formulario de nuevo registro
    if (showBottomSheet) {
        NewRecordDialog(
            userScooters = userScooters,
            onDismiss = { showBottomSheet = false },
            onConfirm = { patinete, kilometraje, fecha ->
                viewModel.addRecord(patinete, kilometraje, fecha)
                showBottomSheet = false
            }
        )
    }

    // Diálogo de edición de registro
    if (recordToEdit != null) {
        EditRecordDialog(
            record = recordToEdit!!,
            userScooters = userScooters,
            onDismiss = { recordToEdit = null },
            onSave = { patinete, kilometraje, fecha ->
                viewModel.updateRecord(recordToEdit!!.id, patinete, kilometraje, fecha)
                recordToEdit = null
            },
            onDelete = { 
                recordToDelete = recordToEdit
                recordToEdit = null
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Historial de Viajes") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            // FAB para agregar registro manual
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nuevo registro"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pestañas de filtrado
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = when(selectedModel) {
                        null -> 0
                        else -> userScooters.indexOfFirst { it.modelo == selectedModel } + 1
                    },
                    edgePadding = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedModel == null,
                        onClick = { viewModel.setSelectedModel(null) }
                    ) {
                        Text(
                            text = "Todos",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                            fontWeight = if (selectedModel == null) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    userScooters.distinctBy { it.modelo }.forEach { scooter ->
                        Tab(
                            selected = selectedModel == scooter.modelo,
                            onClick = { viewModel.setSelectedModel(scooter.modelo) }
                        ) {
                            Text(
                                text = scooter.modelo,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                fontWeight = if (selectedModel == scooter.modelo) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Encabezados de la tabla
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vehículo",
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Fecha",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Total KM",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
                Text(
                    text = "Viaje KM",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            // Lista de registros
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = filteredRecords,
                    key = { it.id }
                ) { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { recordToEdit = record }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = userScooters.find { it.nombre == record.patinete }?.modelo ?: record.patinete,
                            modifier = Modifier.weight(1.2f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                        Text(
                            text = DateUtils.formatForDisplay(DateUtils.parseApiDate(record.fecha)),
                            modifier = Modifier.weight(0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = String.format("%.1f", record.kilometraje),
                            modifier = Modifier.weight(0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = String.format("+%.1f", record.diferencia),
                            modifier = Modifier.weight(0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRecordDialog(
    userScooters: List<com.zipstats.app.model.Scooter>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var selectedScooter by remember { mutableStateOf("") }
    var kilometraje by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Inicializar la fecha seleccionada con la fecha real del sistema
    val millis = System.currentTimeMillis()
    val today = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    var selectedDate by remember { mutableStateOf(today) }

    // Establecer patinete predeterminado
    LaunchedEffect(userScooters) {
        if (selectedScooter.isEmpty() && userScooters.isNotEmpty()) {
            selectedScooter = userScooters.first().nombre
        }
    }

    if (showDatePicker) {
        StandardDatePickerDialogWithValidation(
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it },
            onDismiss = { showDatePicker = false },
            title = "Seleccionar fecha",
            maxDate = today,
            validateDate = { date -> !date.isAfter(today) }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo registro") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selector de vehículo
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = userScooters.find { it.nombre == selectedScooter }?.let { "${it.modelo} (${it.nombre})" } ?: "",
                        onValueChange = { },
                        label = { Text("Vehículo") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        userScooters.forEach { scooter ->
                            DropdownMenuItem(
                                text = { 
                                    Text("${scooter.modelo} (${scooter.nombre})")
                                },
                                onClick = { 
                                    selectedScooter = scooter.nombre
                                    expanded = false
                                    errorMessage = null
                                }
                            )
                        }
                    }
                }

                // Campo de kilometraje
                OutlinedTextField(
                    value = kilometraje,
                    onValueChange = { 
                        kilometraje = it
                        errorMessage = null
                    },
                    label = { Text("Kilometraje") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de fecha
                OutlinedTextField(
                    value = DateUtils.formatForDisplay(selectedDate),
                    onValueChange = { },
                    label = { Text("Fecha") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Mostrar error si existe
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedScooter.isEmpty() || kilometraje.isEmpty()) {
                        errorMessage = "Por favor, complete todos los campos"
                    } else {
                        onConfirm(
                            selectedScooter,
                            kilometraje,
                            DateUtils.formatForApi(selectedDate)
                        )
                    }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecordDialog(
    record: Record,
    userScooters: List<com.zipstats.app.model.Scooter>,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    var selectedScooter by remember { mutableStateOf(record.patinete) }
    var kilometraje by remember { mutableStateOf(record.kilometraje.toString()) }
    var expanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Inicializar la fecha seleccionada con la fecha del registro
    val recordDate = DateUtils.parseApiDate(record.fecha)
    var selectedDate by remember { mutableStateOf(recordDate) }
    val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()

    if (showDatePicker) {
        StandardDatePickerDialogWithValidation(
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it },
            onDismiss = { showDatePicker = false },
            title = "Seleccionar fecha",
            maxDate = today,
            validateDate = { date -> !date.isAfter(today) }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar registro") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selector de vehículo
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = userScooters.find { it.nombre == selectedScooter }?.let { "${it.modelo} (${it.nombre})" } ?: "",
                        onValueChange = { },
                        label = { Text("Vehículo") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        userScooters.forEach { scooter ->
                            DropdownMenuItem(
                                text = { 
                                    Text("${scooter.modelo} (${scooter.nombre})")
                                },
                                onClick = { 
                                    selectedScooter = scooter.nombre
                                    expanded = false
                                    errorMessage = null
                                }
                            )
                        }
                    }
                }

                // Campo de kilometraje
                OutlinedTextField(
                    value = kilometraje,
                    onValueChange = { 
                        kilometraje = it
                        errorMessage = null
                    },
                    label = { Text("Kilometraje") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de fecha
                OutlinedTextField(
                    value = DateUtils.formatForDisplay(selectedDate),
                    onValueChange = { },
                    label = { Text("Fecha") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Mostrar error si existe
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedScooter.isEmpty() || kilometraje.isEmpty()) {
                        errorMessage = "Por favor, complete todos los campos"
                    } else {
                        onSave(
                            selectedScooter,
                            kilometraje,
                            DateUtils.formatForApi(selectedDate)
                        )
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}

