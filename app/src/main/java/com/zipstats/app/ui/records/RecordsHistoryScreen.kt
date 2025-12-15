package com.zipstats.app.ui.records

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.model.Record
import com.zipstats.app.navigation.Screen
import com.zipstats.app.ui.components.AnimatedFloatingActionButton
import com.zipstats.app.ui.components.DialogCancelButton
import com.zipstats.app.ui.components.DialogDeleteButton
import com.zipstats.app.ui.components.DialogSaveButton
import com.zipstats.app.ui.components.EmptyStateRecords
import com.zipstats.app.ui.components.ExpandableRow
import com.zipstats.app.ui.components.StandardDatePickerDialogWithValidation
import com.zipstats.app.di.AppOverlayRepositoryEntryPoint
import com.zipstats.app.repository.AppOverlayRepository
import com.zipstats.app.ui.onboarding.OnboardingDialog
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.utils.DateUtils
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsHistoryScreen(
    navController: NavController,
    viewModel: RecordsViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val appOverlayRepository: AppOverlayRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppOverlayRepositoryEntryPoint::class.java
        ).appOverlayRepository()
    }
    val vehiclesReady by appOverlayRepository.vehiclesReady.collectAsState()
    
    val onboardingManager = onboardingViewModel.onboardingManager
    val records by viewModel.records.collectAsState()
    val userScooters by viewModel.userScooters.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isLoading = uiState is RecordsUiState.Loading

    var recordToDelete by remember { mutableStateOf<Record?>(null) }
    var recordToEdit by remember { mutableStateOf<Record?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showOnboardingDialog by remember { mutableStateOf(false) }
    var hasCheckedVehicles by remember { mutableStateOf(false) }

    val onboardingDismissedInSession by viewModel.onboardingDismissedInSession.collectAsState()

    // Estado para controlar el scroll de la lista (Definido UNA sola vez)
    val listState = rememberLazyListState()

    // Variable para detectar cuando se añade un registro
    var previousRecordsSize by remember { mutableStateOf(records.size) }
    var isFilterChanging by remember { mutableStateOf(false) }

    // Verificar si se debe mostrar el onboarding
    LaunchedEffect(userScooters.size, uiState, onboardingDismissedInSession) {
        val isLoading = uiState is RecordsUiState.Loading

        if (!isLoading && !hasCheckedVehicles && !onboardingDismissedInSession) {
            hasCheckedVehicles = true
            if (userScooters.isEmpty()) {
                showOnboardingDialog = true
            } else {
                showOnboardingDialog = false
            }
        } else {
            showOnboardingDialog = false
        }
    }

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

    // Detectar cambio de filtro para evitar flash del EmptyStateView
    LaunchedEffect(selectedModel) {
        isFilterChanging = true
        kotlinx.coroutines.delay(150) // Pequeño delay para evitar el flash
        isFilterChanging = false
        if (filteredRecords.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Scroll automático al principio cuando se añade un nuevo registro
    LaunchedEffect(records.size) {
        if (records.size > previousRecordsSize && filteredRecords.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
        previousRecordsSize = records.size
    }

    // Lógica para determinar el patinete por defecto (el último usado)
    val lastUsedScooterName = remember(records) {
        records.maxByOrNull { it.fecha }?.patinete
    }

    // Diálogos
    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Confirmar eliminación") },
            text = { Text("¿Estás seguro de que quieres eliminar este registro?") },
            confirmButton = {
                DialogDeleteButton(
                    text = "Eliminar",
                    onClick = {
                        viewModel.deleteRecord(record.id)
                        recordToDelete = null
                    }
                )
            },
            dismissButton = {
                DialogCancelButton(
                    text = "Cancelar",
                    onClick = { recordToDelete = null }
                )
            },
            shape = DialogShape
        )
    }

    if (showOnboardingDialog) {
        OnboardingDialog(
            onDismiss = {
                showOnboardingDialog = false
                viewModel.markOnboardingDismissed()
            },
            onRegisterVehicle = {
                showOnboardingDialog = false
                viewModel.markOnboardingDismissed()
                navController.navigate("${Screen.Profile.route}?openAddVehicle=true")
            }
        )
    }

    if (showBottomSheet) {
        NewRecordDialog(
            userScooters = userScooters,
            records = records, // Pasamos los registros para calcular el kilometraje anterior
            defaultScooter = lastUsedScooterName, // Pasamos el último usado
            onDismiss = { showBottomSheet = false },
            onConfirm = { patinete, kilometraje, fecha ->
                viewModel.addRecord(patinete, kilometraje, fecha)
                showBottomSheet = false
            }
        )
    }

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
                title = {
                    Text(
                        "Historial de Viajes",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            AnimatedFloatingActionButton(
                onClick = {
                    // Verificar si hay vehículos antes de permitir añadir registro
                    if (userScooters.isEmpty()) {
                        showOnboardingDialog = true
                    } else {
                        showBottomSheet = true
                    }
                },
                enabled = vehiclesReady, // Deshabilitar hasta que los vehículos estén cargados
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

            // Lista de registros con nuevo diseño de tarjeta
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredRecords.isEmpty() && (records.isEmpty() || !isFilterChanging)) {
                // Estado vacío
                EmptyStateRecords(
                    onAddRecord = {
                        if (userScooters.isEmpty()) {
                            showOnboardingDialog = true
                        } else {
                            showBottomSheet = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(
                    items = filteredRecords,
                    key = { _, record -> record.id }
                ) { index, record ->
                    Column {
                        ExpandableRow(
                            onClick = { recordToEdit = record },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            backgroundAlpha = if (index % 2 == 0) 0f else 0.3f
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // COLUMNA IZQUIERDA: Vehículo y Fecha
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = userScooters.find { it.nombre == record.patinete }?.modelo ?: record.patinete,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = DateUtils.formatForDisplay(DateUtils.parseApiDate(record.fecha)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            // COLUMNA DERECHA: Diferencia y Total
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = String.format("+%.1f km", record.diferencia),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Text(
                                    text = String.format("%.1f total", record.kilometraje),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRecordDialog(
    userScooters: List<com.zipstats.app.model.Scooter>,
    records: List<Record>, // Nuevo parámetro para buscar historial
    defaultScooter: String?, // Nuevo parámetro para selección por defecto
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var selectedScooter by remember { mutableStateOf(defaultScooter ?: "") }
    var kilometraje by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val millis = System.currentTimeMillis()
    val today = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    var selectedDate by remember { mutableStateOf(today) }

    // Fallback: Si no hay default, seleccionamos el primer vehículo disponible
    LaunchedEffect(userScooters) {
        if (selectedScooter.isEmpty() && userScooters.isNotEmpty()) {
            selectedScooter = userScooters.first().nombre
        }
    }

    // UX: Calcular kilometraje anterior para mostrar como ayuda
    val previousMileage = remember(selectedScooter, records) {
        records
            .filter { it.patinete == selectedScooter }
            .maxByOrNull { it.fecha }?.kilometraje
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
                        value = userScooters.find { it.nombre == selectedScooter }
                            ?.let { "${it.modelo} (${it.nombre})" } ?: "",
                        onValueChange = {},
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
                                text = { Text("${scooter.modelo} (${scooter.nombre})") },
                                onClick = {
                                    selectedScooter = scooter.nombre
                                    expanded = false
                                    errorMessage = null
                                }
                            )
                        }
                    }
                }

                // Campo de kilometraje con ayuda visual
                Column {
                    OutlinedTextField(
                        value = kilometraje,
                        onValueChange = {
                            kilometraje = it
                            errorMessage = null
                        },
                        label = { Text("Kilometraje") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Chivato de kilometraje anterior
                    if (previousMileage != null) {
                        Text(
                            text = "Anterior: $previousMileage km",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }

                // Selector de fecha
                OutlinedTextField(
                    value = DateUtils.formatForDisplay(selectedDate),
                    onValueChange = {},
                    label = { Text("Fecha") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

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
            DialogSaveButton(
                text = "Guardar",
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
                        value = userScooters.find { it.nombre == selectedScooter }
                            ?.let { "${it.modelo} (${it.nombre})" } ?: "",
                        onValueChange = {},
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
                                text = { Text("${scooter.modelo} (${scooter.nombre})") },
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de fecha
                OutlinedTextField(
                    value = DateUtils.formatForDisplay(selectedDate),
                    onValueChange = {},
                    label = { Text("Fecha") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

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
            DialogSaveButton(
                text = "Guardar",
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
            )
        },

        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogDeleteButton(
                    text = "Eliminar",
                    onClick = onDelete
                )
                DialogCancelButton(
                    text = "Cancelar",
                    onClick = onDismiss
                )
            }
        },

        shape = DialogShape
    )
}