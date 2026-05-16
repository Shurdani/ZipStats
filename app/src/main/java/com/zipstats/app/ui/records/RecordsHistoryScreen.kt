package com.zipstats.app.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.di.AppOverlayRepositoryEntryPoint
import com.zipstats.app.R
import com.zipstats.app.model.Record
import com.zipstats.app.model.VehicleType
import com.zipstats.app.navigation.Screen
import com.zipstats.app.ui.components.AnimatedFloatingActionButton
import com.zipstats.app.ui.components.DialogCancelButton
import com.zipstats.app.ui.components.DialogContentText
import com.zipstats.app.ui.components.DialogTitleText
import com.zipstats.app.ui.components.DialogDeleteButton
import com.zipstats.app.ui.components.EmptyStateRecords
import com.zipstats.app.ui.components.StandardDatePickerDialogWithValidation
import com.zipstats.app.ui.components.ZipStatsText
import com.zipstats.app.ui.onboarding.OnboardingDialog
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.utils.DateUtils
import com.zipstats.app.utils.LocationUtils
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun RecordsHistoryScreen(
    navController: NavController,
    viewModel: RecordsViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppOverlayRepositoryEntryPoint::class.java
        ).appOverlayRepository()
    }
    val vehiclesLoaded by viewModel.vehiclesLoaded.collectAsState()
    val initialDataResolved by viewModel.initialDataResolved.collectAsState()
    onboardingViewModel.onboardingManager
    val records by viewModel.records.collectAsState()
    val userScooters by viewModel.userScooters.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isLoading = uiState is RecordsUiState.Loading
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMorePages by viewModel.hasMorePages.collectAsState()

    var recordToEdit by remember { mutableStateOf<Record?>(null) }
    var recordIdPendingDelete by remember { mutableStateOf<String?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showOnboardingDialog by remember { mutableStateOf(false) }

    val onboardingDismissedInSession by viewModel.onboardingDismissedInSession.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.runScooterIdBackfillIfNeeded()
    }

    // Estado para controlar el scroll de la lista (Definido UNA sola vez)
    val listState = rememberLazyListState()

    // Guardamos el primer elemento visible para distinguir "nuevo arriba" vs "paginación abajo"
    var previousTopRecordId by remember { mutableStateOf(records.firstOrNull()?.id) }


    // Al cambiar de filtro, volvemos al inicio si hay resultados visibles
    LaunchedEffect(selectedModel) {
        if (records.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Scroll automático solo cuando aparece un nuevo registro en la parte superior
    LaunchedEffect(records) {
        val currentTopRecordId = records.firstOrNull()?.id
        if (
            previousTopRecordId != null &&
            currentTopRecordId != null &&
            currentTopRecordId != previousTopRecordId &&
            records.isNotEmpty()
        ) {
            listState.animateScrollToItem(0)
        }
        previousTopRecordId = currentTopRecordId
    }

    // Infinite scroll: cargar más al acercarse al final
    LaunchedEffect(listState, records.size, hasMorePages, isLoadingMore) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && hasMorePages && !isLoadingMore && lastVisible >= total - 3
        }
            .distinctUntilChanged()
            .debounce(250)
            .filter { it }
            .collect { viewModel.loadNextPage() }
    }

      // Con filtro activo, seguir cargando para completar resultados visibles
    LaunchedEffect(selectedModel, records.size, hasMorePages, isLoadingMore) {
        if (selectedModel != null && records.size < 3 && hasMorePages && !isLoadingMore) {
            viewModel.loadNextPage()
        }
    }

    // Lógica para determinar el patinete por defecto (el último usado)
    val lastUsedScooterName = remember(records) {
        records.maxWithOrNull(DateUtils.recordComparatorNewestFirst())?.patinete
    }

    val isWaitingInitialPage =
        !initialDataResolved
    val isWaitingFilteredResults =
        selectedModel != null &&
            records.isEmpty() &&
            hasMorePages
    val shouldShowOnboarding =
        initialDataResolved &&
            !isWaitingInitialPage &&
            !onboardingDismissedInSession &&
            records.isEmpty() &&
            userScooters.isEmpty()

    if (shouldShowOnboarding) {
        OnboardingDialog(
            onDismiss = { viewModel.markOnboardingDismissed() },
            onRegisterVehicle = {
                viewModel.markOnboardingDismissed()
                navController.navigate("${Screen.Profile.route}?openAddVehicle=true")
            }
        )
    }

    if (showOnboardingDialog) {
        OnboardingDialog(
            onDismiss = { showOnboardingDialog = false },
            onRegisterVehicle = {
                showOnboardingDialog = false
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
            onConfirm = { patinete, scooterId, kilometraje, fecha ->
                viewModel.addRecord(patinete, kilometraje, fecha, scooterId)
                showBottomSheet = false
            }
        )
    }

    if (recordIdPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { recordIdPendingDelete = null },
            title = { DialogTitleText("Confirmar eliminación") },
            text = { DialogContentText("¿Estás seguro de que quieres eliminar este registro?") },
            confirmButton = {
                DialogDeleteButton(
                    text = "Eliminar",
                    onClick = {
                        recordIdPendingDelete?.let { viewModel.deleteRecord(it) }
                        recordIdPendingDelete = null
                    }
                )
            },
            dismissButton = {
                DialogCancelButton(
                    text = "Cancelar",
                    onClick = { recordIdPendingDelete = null }
                )
            },
            shape = DialogShape
        )
    }

    val editRecordSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    if (recordToEdit != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    editRecordSheetState.hide()
                    recordToEdit = null
                }
            },
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
                onSave = { patinete, scooterId, kilometraje, fecha ->
                    viewModel.updateRecord(recordToEdit!!.id, patinete, kilometraje, fecha, scooterId)
                    scope.launch {
                        editRecordSheetState.hide()
                        recordToEdit = null
                    }
                },
                onDelete = {
                    val recordId = recordToEdit?.id
                    scope.launch {
                        editRecordSheetState.hide()
                        recordIdPendingDelete = recordId
                        recordToEdit = null
                    }
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
                        text = "Historial de Registros",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
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
                    if (userScooters.isEmpty()) {
                        showOnboardingDialog = true
                    } else {
                        showBottomSheet = true
                    }
                },
                enabled = vehiclesLoaded && initialDataResolved, // Igualar feedback de carga: semitransparente hasta tener datos iniciales
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
            if (isLoading || isWaitingInitialPage) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (records.isEmpty() && !isWaitingFilteredResults && !isWaitingInitialPage) {
                if (initialDataResolved && !shouldShowOnboarding) {
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
                    Box(modifier = Modifier.fillMaxWidth().weight(1f))
                }

            } else {

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(
                        items = records,
                        key = { _, record -> record.id }
                    ) { _, record ->
                        val interactionSource = remember(record.id) { MutableInteractionSource() }
                        val scooter = userScooters.find { it.nombre == record.patinete }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { recordToEdit = record }
                                )
                        ) {
                            ListItem(
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = getVehicleIcon(scooter?.vehicleType ?: VehicleType.PATINETE),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                },
                                // 2. HEADLINE: El dato principal (Nombre del vehículo)
                                headlineContent = {
                                    ZipStatsText(
                                        text = scooter?.modelo ?: record.patinete,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                // 3. SUPPORTING: Fecha y hora (dato secundario) - Formato humano
                                supportingContent = {
                                    ZipStatsText(
                                        text = DateUtils.formatHumanDateWithTime(record.fecha),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                // 4. TRAILING: Los datos numéricos a la derecha
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        // El dato "héroe": La distancia recorrida
                                        ZipStatsText(
                                            text = "+${LocationUtils.formatNumberSpanish(record.diferencia)} km",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        // El metadato: Total acumulado
                                        ZipStatsText(
                                            text = "Total: ${LocationUtils.formatNumberSpanish(record.kilometraje)} km",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                // 5. COLORES: Fondo transparente para respetar el tema
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            )
                        }
                    }

                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getVehicleIcon(vehicleType: VehicleType): Painter {
    return when (vehicleType) {
        VehicleType.PATINETE -> painterResource(id = R.drawable.ic_electric_scooter_adaptive)
        VehicleType.BICICLETA -> painterResource(id = R.drawable.ic_ciclismo_adaptive)
        VehicleType.E_BIKE -> painterResource(id = R.drawable.ic_bicicleta_electrica_adaptive)
        VehicleType.MONOCICLO -> painterResource(id = R.drawable.ic_unicycle_adaptive)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRecordBottomSheet(
    userScooters: List<com.zipstats.app.model.Scooter>,
    records: List<Record>,
    defaultScooter: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String, String) -> Unit
) {
    // Estado de la hoja inferior
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope() // Necesario para animar el cierre
    
    // Estados del formulario
    var selectedScooter by remember { mutableStateOf(defaultScooter ?: "") }
    var selectedScooterId by remember { mutableStateOf<String?>(null) }
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
            val first = userScooters.first()
            selectedScooter = first.nombre
            selectedScooterId = first.id.takeIf { it.isNotEmpty() }
        }
    }

    LaunchedEffect(selectedScooter, userScooters) {
        selectedScooterId = userScooters.find { it.nombre == selectedScooter }
            ?.id
            ?.takeIf { it.isNotEmpty() }
    }

    // Cálculo de kilometraje anterior (Helper visual)
    val previousMileage = remember(selectedScooter, selectedScooterId, records) {
        records
            .filter { record ->
                when {
                    !selectedScooterId.isNullOrEmpty() && record.scooterId == selectedScooterId -> true
                    else -> record.patinete == selectedScooter || record.vehiculo == selectedScooter
                }
            }
            .maxWithOrNull(DateUtils.recordComparatorNewestFirst())?.kilometraje
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
            ZipStatsText(
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
                    label = { ZipStatsText("Vehículo") },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isVehicleDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
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
                            text = {
                                ZipStatsText(
                                    "${scooter.modelo} (${scooter.nombre})",
                                    maxLines = 2
                                )
                            },
                            onClick = {
                                selectedScooter = scooter.nombre
                                selectedScooterId = scooter.id.takeIf { it.isNotEmpty() }
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
                    label = { ZipStatsText("Kilometraje actual") },
                    placeholder = { ZipStatsText("Ej. 1250.5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    supportingText = {
                        // Aquí mostramos el kilometraje anterior de forma elegante
                        if (previousMileage != null) {
                            ZipStatsText(
                                text = "Anterior: ${LocationUtils.formatNumberSpanish(previousMileage, 1)} km",
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
                    label = { ZipStatsText("Fecha") },
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
                ZipStatsText(
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
                                    selectedScooterId,
                                    kilometraje,
                                    DateUtils.formatForApiOnDayWithCurrentTime(selectedDate)
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp), // Altura cómoda para el pulgar
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                ZipStatsText(
                    text = "Guardar Registro",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
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
    onSave: (String, String?, String, String) -> Unit,
    onDelete: () -> Unit
) {
    var selectedScooter by remember { mutableStateOf(record.patinete) }
    var selectedScooterId by remember {
        mutableStateOf(
            record.scooterId.takeIf { it.isNotEmpty() }
                ?: userScooters.find { it.nombre == record.patinete }?.id?.takeIf { it.isNotEmpty() }
        )
    }
    var kilometraje by remember { mutableStateOf(LocationUtils.formatNumberSpanish(record.kilometraje, 1)) }
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
            style = MaterialTheme.typography.headlineSmall,
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
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                userScooters.forEach { scooter ->
                    DropdownMenuItem(
                        text = {
                            ZipStatsText(
                                "${scooter.modelo} (${scooter.nombre})",
                                maxLines = 2
                            )
                        },
                        onClick = {
                            selectedScooter = scooter.nombre
                            selectedScooterId = scooter.id.takeIf { it.isNotEmpty() }
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
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                ZipStatsText(
                    text = "Cancelar",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Button(
                onClick = {
                    if (selectedScooter.isEmpty() || kilometraje.isEmpty()) {
                        errorMessage = "Por favor, complete todos los campos"
                    } else {
                        onSave(
                            selectedScooter,
                            selectedScooterId,
                            kilometraje,
                            DateUtils.mergeApiDateWithRecordTime(selectedDate, record.fecha)
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                ZipStatsText(
                    "Guardar",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
