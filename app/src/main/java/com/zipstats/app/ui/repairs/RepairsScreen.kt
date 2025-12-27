package com.zipstats.app.ui.repairs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.model.Repair
import com.zipstats.app.model.Scooter
import com.zipstats.app.ui.components.AnimatedFloatingActionButton
import com.zipstats.app.ui.components.DialogDeleteButton
import com.zipstats.app.ui.components.DialogNeutralButton
import com.zipstats.app.ui.components.DialogSaveButton
import com.zipstats.app.ui.components.EmptyStateRepairs
import com.zipstats.app.ui.components.StandardDatePickerDialog
import com.zipstats.app.ui.components.ZipStatsText
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.utils.DateUtils
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairsScreen(
    navController: NavController,
    scooterId: String,
    viewModel: RepairsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var scooter by remember { mutableStateOf<Scooter?>(null) }
    val scope = rememberCoroutineScope()

    // Estados UI
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var selectedRepair by remember { mutableStateOf<Repair?>(null) }

    // Estado secundario para bottom sheet de edición/borrado derivados de la selección
    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    
    // Estado del bottom sheet
    val addSheetState = rememberModalBottomSheetState()
    val editSheetState = rememberModalBottomSheetState()

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

    // Efecto para abrir el bottom sheet de edición cuando se selecciona una reparación
    LaunchedEffect(selectedRepair) {
        if (selectedRepair != null) {
            tempDate = selectedRepair!!.date
            tempDesc = selectedRepair!!.description
            tempMileage = selectedRepair!!.mileage?.toString() ?: ""
            showEditSheet = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        ZipStatsText(
                            text = "Mantenimiento",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        scooter?.let {
                            ZipStatsText(
                                text = "${it.marca} ${it.modelo}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                maxLines = 1
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
            AnimatedFloatingActionButton(
                onClick = {
                    // Resetear campos para nueva entrada
                    tempDate = LocalDate.now()
                    tempDesc = ""
                    tempMileage = "" // Opcional: pre-cargar kilometraje actual del scooter
                    showAddSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary
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
                        EmptyStateRepairs(
                            onAddRepair = {
                                // Resetear campos para nueva entrada
                                tempDate = LocalDate.now()
                                tempDesc = ""
                                tempMileage = ""
                                showAddSheet = true
                            }
                        )
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
                        ZipStatsText(
                            text = (uiState as RepairsUiState.Error).message,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // --- BOTTOM SHEETS ---

    // 1. BOTTOM SHEET AÑADIR REPARACIÓN
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = addSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            AddRepairBottomSheet(
                tempDate = tempDate,
                tempDesc = tempDesc,
                tempMileage = tempMileage,
                onDateChange = { tempDate = it },
                onDescChange = { tempDesc = it },
                onMileageChange = { tempMileage = it },
                onSave = {
                    val mileage = tempMileage.toDoubleOrNull()
                    scooter?.let { loaded ->
                        viewModel.addRepair(
                            date = tempDate,
                            description = tempDesc,
                            mileage = mileage,
                            scooterName = loaded.nombre,
                            scooterId = loaded.id
                        )
                    }
                    scope.launch {
                        addSheetState.hide()
                        showAddSheet = false
                    }
                    // Limpiar campos
                    tempDesc = ""
                    tempMileage = ""
                },
                onCancel = {
                    scope.launch {
                        addSheetState.hide()
                        showAddSheet = false
                    }
                },
                onDatePickerClick = { showDatePicker = true }
            )
        }
    }

    // 2. BOTTOM SHEET EDITAR REPARACIÓN
    if (showEditSheet && selectedRepair != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showEditSheet = false
                selectedRepair = null
            },
            sheetState = editSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            EditRepairBottomSheet(
                tempDate = tempDate,
                tempDesc = tempDesc,
                tempMileage = tempMileage,
                onDateChange = { tempDate = it },
                onDescChange = { tempDesc = it },
                onMileageChange = { tempMileage = it },
                onSave = {
                    val mileage = tempMileage.toDoubleOrNull()
                    viewModel.updateRepair(
                        selectedRepair!!.copy(
                            date = tempDate,
                            description = tempDesc,
                            mileage = mileage
                        )
                    )
                    scope.launch {
                        editSheetState.hide()
                        showEditSheet = false
                        selectedRepair = null
                    }
                    // Limpiar campos
                    tempDesc = ""
                    tempMileage = ""
                },
                onCancel = {
                    scope.launch {
                        editSheetState.hide()
                        showEditSheet = false
                        selectedRepair = null
                    }
                },
                onDelete = {
                    scope.launch {
                        editSheetState.hide()
                        showEditSheet = false
                    }
                    showDeleteDialog = true
                },
                onDatePickerClick = { showDatePicker = true }
            )
        }
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
            title = { ZipStatsText("Eliminar registro") },
            text = { ZipStatsText("¿Estás seguro de que quieres eliminar esta reparación del historial?") },
            confirmButton = {
                DialogDeleteButton(
                    text = "Eliminar",
                    onClick = {
                        viewModel.deleteRepair(selectedRepair!!.id)
                        selectedRepair = null
                        showEditSheet = false // También cerramos el bottom sheet de edición si estaba abierto
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
fun AddRepairBottomSheet(
    tempDate: LocalDate,
    tempDesc: String,
    tempMileage: String,
    onDateChange: (LocalDate) -> Unit,
    onDescChange: (String) -> Unit,
    onMileageChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDatePickerClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ZipStatsText(
            text = "Nueva reparación",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Selector de Fecha
        OutlinedTextField(
            value = DateUtils.formatForDisplay(tempDate),
            onValueChange = {},
            label = { ZipStatsText("Fecha") },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = onDatePickerClick) {
                    Icon(Icons.Default.CalendarMonth, null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Descripción
        OutlinedTextField(
            value = tempDesc,
            onValueChange = onDescChange,
            label = { ZipStatsText("Descripción") },
            placeholder = { ZipStatsText("Ej: Cambio de pastillas de freno") },
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
                    onMileageChange(it.replace(',', '.'))
                }
            },
            label = { ZipStatsText("Kilometraje (Opcional)") },
            placeholder = { ZipStatsText("Km al momento de la reparación") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            trailingIcon = { ZipStatsText("km ", style = MaterialTheme.typography.bodySmall) }
        )

        // Botones
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                ZipStatsText("Cancelar")
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                enabled = tempDesc.isNotBlank()
            ) {
                ZipStatsText("Guardar")
            }
        }
    }
}

@Composable
fun EditRepairBottomSheet(
    tempDate: LocalDate,
    tempDesc: String,
    tempMileage: String,
    onDateChange: (LocalDate) -> Unit,
    onDescChange: (String) -> Unit,
    onMileageChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onDatePickerClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ZipStatsText(
            text = "Editar reparación",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Selector de Fecha
        OutlinedTextField(
            value = DateUtils.formatForDisplay(tempDate),
            onValueChange = {},
            label = { ZipStatsText("Fecha") },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = onDatePickerClick) {
                    Icon(Icons.Default.CalendarMonth, null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Descripción
        OutlinedTextField(
            value = tempDesc,
            onValueChange = onDescChange,
            label = { ZipStatsText("Descripción") },
            placeholder = { ZipStatsText("Ej: Cambio de pastillas de freno") },
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
                    onMileageChange(it.replace(',', '.'))
                }
            },
            label = { ZipStatsText("Kilometraje (Opcional)") },
            placeholder = { ZipStatsText("Km al momento de la reparación") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            trailingIcon = { ZipStatsText("km ", style = MaterialTheme.typography.bodySmall) }
        )

        // Botones
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                ZipStatsText("Eliminar")
            }
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                ZipStatsText("Cancelar")
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                enabled = tempDesc.isNotBlank()
            ) {
                ZipStatsText("Guardar")
            }
        }
    }
}

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
                    ZipStatsText(
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
                                ZipStatsText(
                                    text = "${com.zipstats.app.utils.LocationUtils.formatNumberSpanish(km, 0)} km",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ZipStatsText(
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
