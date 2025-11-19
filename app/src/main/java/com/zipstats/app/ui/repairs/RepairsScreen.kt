package com.zipstats.app.ui.repairs

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
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
import java.time.Instant
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
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var selectedRepair by remember { mutableStateOf<Repair?>(null) }

// Fecha actual
    val today = Instant.ofEpochMilli(System.currentTimeMillis())
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

// Campos para añadir
    var newDate by remember { mutableStateOf(today) }
    var newDesc by remember { mutableStateOf("") }
    var newMileage by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

// Campos para editar
    var editDate by remember { mutableStateOf(today) }
    var editDesc by remember { mutableStateOf("") }
    var editMileage by remember { mutableStateOf("") }
    var showEditDatePicker by remember { mutableStateOf(false) }

// Cargar datos
    LaunchedEffect(scooterId) {
        viewModel.loadScooterAndRepairs(scooterId)
    }

    val loadedScooter by viewModel.scooterState.collectAsState()
    LaunchedEffect(loadedScooter) {
        scooter = loadedScooter
    }

// ============================================================
// DIÁLOGOS
// ============================================================

// --- Añadir reparación
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Añadir reparación") },
            text = {
                Column {
                    Text("Fecha: $newDate")

                    Button(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Seleccionar fecha") }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Descripción") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        minLines = 3
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newMileage,
                        onValueChange = {
                            newMileage = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                                .replace(',', '.')
                        },
                        label = { Text("Kilometraje (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            },
            confirmButton = {
                DialogSaveButton(
                    text = "Guardar",
                    enabled = newDesc.isNotBlank(),
                    onClick = {
                        val mileage = newMileage.toDoubleOrNull()
                        scooter?.let { loaded ->
                            viewModel.addRepair(
                                newDate,
                                newDesc,
                                mileage,
                                loaded.nombre,
                                loaded.id
                            )
                        }
                        showAddDialog = false
                        newDesc = ""
                        newMileage = ""
                        newDate = today
                    }
                )
            },
            dismissButton = {
                DialogNeutralButton(
                    text = "Cancelar",
                    onClick = {
                        showAddDialog = false
                        newDesc = ""
                        newMileage = ""
                        newDate = today
                    }
                )
            },
            shape = DialogShape
        )
    }

// DatePicker nuevo
    if (showDatePicker) {
        StandardDatePickerDialog(
            selectedDate = newDate,
            onDateSelected = {
                newDate = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
            title = "Seleccionar fecha"
        )
    }

// --- Editar reparación
    if (selectedRepair != null) {

        AlertDialog(
            onDismissRequest = { selectedRepair = null },
            title = { Text("Editar reparación") },
            text = {
                Column {

                    Text("Fecha: $editDate")
                    Button(
                        onClick = { showEditDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Seleccionar fecha")
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Descripción") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editMileage,
                        onValueChange = {
                            editMileage = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                                .replace(',', '.')
                        },
                        label = { Text("Kilometraje (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            },
            confirmButton = {
                DialogSaveButton(
                    text = "Guardar",
                    enabled = editDesc.isNotBlank(),
                    onClick = {
                        val mileage = editMileage.toDoubleOrNull()
                        viewModel.updateRepair(
                            selectedRepair!!.copy(
                                date = editDate,
                                description = editDesc,
                                mileage = mileage
                            )
                        )
                        selectedRepair = null
                    }
                )
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogDeleteButton(
                        text = "Eliminar",
                        onClick = { showDeleteDialog = true }
                    )
                    DialogNeutralButton(
                        text = "Cancelar",
                        onClick = { selectedRepair = null }
                    )
                }
            },
            shape = DialogShape
        )
    }

// Edit DatePicker
    if (showEditDatePicker) {
        StandardDatePickerDialog(
            selectedDate = editDate,
            onDateSelected = {
                editDate = it
                showEditDatePicker = false
            },
            onDismiss = { showEditDatePicker = false },
            title = "Seleccionar fecha"
        )
    }

// Eliminar
    if (showDeleteDialog && selectedRepair != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar reparación") },
            text = { Text("¿Confirmas eliminar esta reparación?") },
            confirmButton = {
                DialogDeleteButton(
                    text = "Eliminar",
                    onClick = {
                        viewModel.deleteRepair(selectedRepair!!.id)
                        selectedRepair = null
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

// ============================================================
// SCAFFOLD – SIEMPRE SE PINTA
// ============================================================

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reparaciones - ${scooter?.modelo ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir")
            }
        }
    ) { padding ->

        when (uiState) {

            is RepairsUiState.Loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is RepairsUiState.Success -> {
                val repairs = (uiState as RepairsUiState.Success).repairs
                if (repairs.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No hay reparaciones registradas")
                    }
                } else {
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        items(repairs, key = { it.id }) { repair ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable {
                                        selectedRepair = repair
                                        editDate = repair.date
                                        editDesc = repair.description
                                        editMileage = repair.mileage?.toString() ?: ""
                                    }
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(repair.getFormattedDate())
                                    Text(repair.description)
                                    repair.mileage?.let {
                                        Text("${"%.0f".format(it)} km")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is RepairsUiState.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (uiState as RepairsUiState.Error).message,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
