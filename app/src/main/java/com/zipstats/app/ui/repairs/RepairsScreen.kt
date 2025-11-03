package com.zipstats.app.ui.repairs

import android.app.Activity
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.model.Repair
import com.zipstats.app.model.Scooter
import com.zipstats.app.ui.components.StandardDatePickerDialog
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
    var showAddDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedRepair by remember { mutableStateOf<Repair?>(null) }
    
    // Inicializar la fecha seleccionada con la fecha real del sistema
    val millis = System.currentTimeMillis()
    val today = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    var newRepairDate by remember { mutableStateOf(today) }
    
    var newRepairDescription by remember { mutableStateOf("") }
    var newRepairMileage by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf(today) }
    var editDescription by remember { mutableStateOf("") }
    var editMileage by remember { mutableStateOf("") }
    var showEditDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Obtener el vehículo completo y cargar reparaciones
    LaunchedEffect(scooterId) {
        viewModel.loadScooterAndRepairs(scooterId)
    }
    
    // Observar el scooter cargado
    val loadedScooter by viewModel.scooterState.collectAsState()
    LaunchedEffect(loadedScooter) {
        loadedScooter?.let {
            scooter = it
        }
    }

    // Diálogo de agregar reparación
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Agregar reparación") },
            text = {
                Column {
                    Text(
                        text = "Fecha: ${newRepairDate.toString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Seleccionar fecha")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newRepairDescription,
                        onValueChange = { newRepairDescription = it },
                        label = { Text("Descripción de la reparación") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        minLines = 3
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newRepairMileage,
                        onValueChange = { newRepairMileage = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.replace(',', '.') },
                        label = { Text("Kilometraje (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newRepairDescription.isNotBlank()) {
                            val mileage = newRepairMileage.toDoubleOrNull()
                            scooter?.let { loadedScooter ->
                                viewModel.addRepair(newRepairDate, newRepairDescription, mileage, loadedScooter.nombre, loadedScooter.id)
                            }
                            newRepairDescription = ""
                            newRepairDate = today
                            newRepairMileage = ""
                            showAddDialog = false
                        }
                    },
                    enabled = newRepairDescription.isNotBlank()
                ) {
                    Text("Agregar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        newRepairDescription = ""
                        newRepairDate = LocalDate.now()
                        newRepairMileage = ""
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // DatePicker
    if (showDatePicker) {
        StandardDatePickerDialog(
            selectedDate = newRepairDate,
            onDateSelected = { newRepairDate = it },
            onDismiss = { showDatePicker = false },
            title = "Seleccionar fecha de reparación"
        )
    }

    // DatePicker para edición
    if (showEditDatePicker) {
        StandardDatePickerDialog(
            selectedDate = editDate,
            onDateSelected = { editDate = it },
            onDismiss = { showEditDatePicker = false },
            title = "Seleccionar fecha de reparación"
        )
    }
    
    // Diálogo de editar reparación
    if (selectedRepair != null) {
        LaunchedEffect(selectedRepair!!.id) {
            editDate = selectedRepair!!.date
            editDescription = selectedRepair!!.description
            editMileage = selectedRepair!!.mileage?.toString() ?: ""
        }
        AlertDialog(
            onDismissRequest = { selectedRepair = null },
            title = { Text("Editar reparación") },
            text = {
                Column {
                    Text(
                        text = "Fecha: ${editDate.toString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(
                        onClick = { showEditDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Seleccionar fecha")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Descripción de la reparación") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        minLines = 3
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = editMileage,
                        onValueChange = { editMileage = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.replace(',', '.') },
                        label = { Text("Kilometraje (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mileage = editMileage.toDoubleOrNull()
                        viewModel.updateRepair(
                            selectedRepair!!.copy(
                                date = editDate,
                                description = editDescription,
                                mileage = mileage
                            )
                        )
                        selectedRepair = null
                    },
                    enabled = editDescription.isNotBlank()
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = true
                        }
                    ) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = {
                            selectedRepair = null
                        }
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
    
    // Diálogo de confirmación de eliminación
    if (showDeleteConfirmDialog && selectedRepair != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Eliminar reparación") },
            text = { Text("¿Estás seguro de que deseas eliminar esta reparación?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRepair(selectedRepair!!.id)
                        showDeleteConfirmDialog = false
                        selectedRepair = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Reparaciones - ${scooter?.modelo ?: "Cargando..."}") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agregar reparación"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState) {
                is RepairsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is RepairsUiState.Success -> {
                    val repairs = (uiState as RepairsUiState.Success).repairs
                    
                    if (repairs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No hay reparaciones registradas",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Encabezados removidos a petición

                        // Lista de reparaciones
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(
                                items = repairs,
                                key = { it.id }
                            ) { repair ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .clickable { selectedRepair = repair },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = repair.getFormattedDate(),
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = buildString {
                                                append(repair.description)
                                                repair.mileage?.let { km ->
                                                    append("\n")
                                                    append("${String.format("%.0f", km)} km")
                                                }
                                            },
                                            modifier = Modifier.weight(2f).padding(start = 12.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is RepairsUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (uiState as RepairsUiState.Error).message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
} 