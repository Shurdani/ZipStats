package com.zipstats.app.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.di.AppOverlayRepositoryEntryPoint
import com.zipstats.app.model.Record
import com.zipstats.app.navigation.Screen
import com.zipstats.app.repository.AppOverlayRepository
import com.zipstats.app.ui.components.AnimatedFloatingActionButton
import com.zipstats.app.ui.components.DialogCancelButton
import com.zipstats.app.ui.components.DialogDeleteButton
import com.zipstats.app.ui.components.DialogSaveButton
import com.zipstats.app.ui.components.EmptyStateRecords
import com.zipstats.app.ui.components.StandardDatePickerDialogWithValidation
import com.zipstats.app.ui.components.ZipStatsText
import com.zipstats.app.ui.onboarding.OnboardingDialog
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.utils.DateUtils
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
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
            title = { ZipStatsText("Confirmar eliminación") },
            text = { ZipStatsText("¿Estás seguro de que quieres eliminar este registro?") },
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
        NewRecordBottomSheet(
            userScooters = userScooters,
            records = records,
            defaultScooter = lastUsedScooterName,
            onDismiss = { showBottomSheet = false },
            onConfirm = { patinete, kilometraje, fecha ->
                viewModel.addRecord(patinete, kilometraje, fecha)
                showBottomSheet = false
            }
        )
    }

    val editRecordSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    
    if (recordToEdit != null) {
        ModalBottomSheet(
            onDismissRequest = { recordToEdit = null },
            sheetState = editRecordSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            EditRecordBottomSheet(
                record = recordToEdit!!,
                userScooters = userScooters,
                onDismiss = {
                    scope.launch {
                        editRecordSheetState.hide()
                        recordToEdit = null
                    }
                },
                onSave = { patinete, kilometraje, fecha ->
                    viewModel.updateRecord(recordToEdit!!.id, patinete, kilometraje, fecha)
                    scope.launch {
                        editRecordSheetState.hide()
                        recordToEdit = null
                    }
                },
                onDelete = {
                    scope.launch {
                        editRecordSheetState.hide()
                    }
                    recordToDelete = recordToEdit
                    recordToEdit = null
                }
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    ZipStatsText(
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
                        ZipStatsText(
                            text = "Todos",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                            fontWeight = if (selectedModel == null) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                    userScooters.distinctBy { it.modelo }.forEach { scooter ->
                        Tab(
                            selected = selectedModel == scooter.modelo,
                            onClick = { viewModel.setSelectedModel(scooter.modelo) }
                        ) {
                            ZipStatsText(
                                text = scooter.modelo,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                fontWeight = if (selectedModel == scooter.modelo) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
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
                    ) { _, record -> // Ya no necesitamos el index para el color
                        val interactionSource = remember { MutableInteractionSource() }
                        
                        // 1. Contenedor CLICKABLE limpio
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { recordToEdit = record }
                                )
                        ) {
                            ListItem(
                                // 2. HEADLINE: El dato principal (Nombre del vehículo)
                                headlineContent = {
                                    ZipStatsText(
                                        text = userScooters.find { it.nombre == record.patinete }?.modelo ?: record.patinete,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                // 3. SUPPORTING: Fecha (dato secundario)
                                supportingContent = {
                                    ZipStatsText(
                                        text = DateUtils.formatForDisplay(DateUtils.parseApiDate(record.fecha)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                // 4. TRAILING: Los datos numéricos a la derecha
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        // El dato "héroe": La distancia recorrida
                                        ZipStatsText(
                                            text = String.format("+%.1f km", record.diferencia),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        // El metadato: Total acumulado
                                        ZipStatsText(
                                            text = String.format("Total: %.1f km", record.kilometraje),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                },
                                // 5. COLORES: Fondo transparente para respetar el tema
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            )

                            // 6. DIVISOR: Sutil y elegante
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp), // Indentado para look moderno
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
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
fun NewRecordBottomSheet(
    userScooters: List<com.zipstats.app.model.Scooter>,
    records: List<Record>,
    defaultScooter: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    // Estado de la hoja inferior
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope() // Necesario para animar el cierre
    
    // Estados del formulario
    var selectedScooter by remember { mutableStateOf(defaultScooter ?: "") }
    var kilometraje by remember { mutableStateOf("") }
    var isVehicleDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fechas
    val millis = System.currentTimeMillis()
    val today = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    var selectedDate by remember { mutableStateOf(today) }

    // Lógica de autoselección
    LaunchedEffect(userScooters) {
        if (selectedScooter.isEmpty() && userScooters.isNotEmpty()) {
            selectedScooter = userScooters.first().nombre
        }
    }

    // Cálculo de kilometraje anterior (Helper visual)
    val previousMileage = remember(selectedScooter, records) {
        records
            .filter { it.patinete == selectedScooter }
            .maxByOrNull { it.fecha }?.kilometraje
    }

    // Date Picker (Se muestra por encima del BottomSheet)
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

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp) // Márgenes laterales estándar MD3
                .padding(bottom = 24.dp) // Margen inferior extra para seguridad
                .navigationBarsPadding(), // Respetar barra de navegación del sistema
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CABECERA
            Text(
                text = "Nuevo registro",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 1. SELECTOR DE VEHÍCULO
            ExposedDropdownMenuBox(
                expanded = isVehicleDropdownExpanded,
                onExpandedChange = { isVehicleDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = userScooters.find { it.nombre == selectedScooter }
                        ?.let { "${it.modelo} (${it.nombre})" } ?: "",
                    onValueChange = {},
                    label = { Text("Vehículo") },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isVehicleDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        ), // Importante para anclar el menú
                    shape = MaterialTheme.shapes.medium
                )
                ExposedDropdownMenu(
                    expanded = isVehicleDropdownExpanded,
                    onDismissRequest = { isVehicleDropdownExpanded = false }
                ) {
                    userScooters.forEach { scooter ->
                        DropdownMenuItem(
                            text = { Text("${scooter.modelo} (${scooter.nombre})") },
                            onClick = {
                                selectedScooter = scooter.nombre
                                isVehicleDropdownExpanded = false
                                errorMessage = null
                            }
                        )
                    }
                }
            }

            // 2. INPUT KILOMETRAJE
            Column {
                OutlinedTextField(
                    value = kilometraje,
                    onValueChange = {
                        kilometraje = it
                        errorMessage = null
                    },
                    label = { Text("Kilometraje actual") },
                    placeholder = { Text("Ej. 1250.5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    supportingText = {
                        // Aquí mostramos el kilometraje anterior de forma elegante
                        if (previousMileage != null) {
                            Text(
                                text = "Anterior: $previousMileage km",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    isError = errorMessage != null
                )
            }

            // 3. SELECTOR DE FECHA
            val dateInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = dateInteractionSource,
                        indication = null,
                        onClick = { showDatePicker = true }
                    )
            ) {
                OutlinedTextField(
                    value = DateUtils.formatForDisplay(selectedDate),
                    onValueChange = {},
                    label = { Text("Fecha") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Cambiar fecha")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false, // Deshabilitamos input manual para forzar click
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = MaterialTheme.shapes.medium
                )
            }

            // MENSAJE DE ERROR
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // BOTÓN DE ACCIÓN PRINCIPAL (Full Width)
            Button(
                onClick = {
                    if (selectedScooter.isEmpty() || kilometraje.isEmpty()) {
                        errorMessage = "Por favor, complete todos los campos"
                    } else {
                        // Animación de cierre segura
                        scope.launch {
                            sheetState.hide()
                            if (!sheetState.isVisible) {
                                onConfirm(
                                    selectedScooter,
                                    kilometraje,
                                    DateUtils.formatForApi(selectedDate)
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp), // Altura cómoda para el pulgar
                shape = MaterialTheme.shapes.large
            ) {
                Text("Guardar Registro", fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecordBottomSheet(
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ZipStatsText(
            text = "Editar registro",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Selector de vehículo
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = userScooters.find { it.nombre == selectedScooter }
                    ?.let { "${it.modelo} (${it.nombre})" } ?: "",
                onValueChange = {},
                label = { ZipStatsText("Vehículo") },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        type = MenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                userScooters.forEach { scooter ->
                    DropdownMenuItem(
                        text = { ZipStatsText("${scooter.modelo} (${scooter.nombre})") },
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
            label = { ZipStatsText("Kilometraje") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        // Selector de fecha
        OutlinedTextField(
            value = DateUtils.formatForDisplay(selectedDate),
            onValueChange = {},
            label = { ZipStatsText("Fecha") },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let { error ->
            ZipStatsText(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Botones
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                ZipStatsText("Eliminar")
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                ZipStatsText("Cancelar")
            }
            Button(
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
                },
                modifier = Modifier.weight(1f)
            ) {
                ZipStatsText("Guardar")
            }
        }
    }
}
