package com.zipstats.app.ui.routes

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.model.Route
import com.zipstats.app.navigation.Screen
import com.zipstats.app.ui.components.AnimatedFloatingActionButton
import com.zipstats.app.ui.components.DialogCancelButton
import com.zipstats.app.ui.components.DialogConfirmButton
import com.zipstats.app.ui.components.EmptyStateRoutes
import com.zipstats.app.ui.components.ExpandableRow
import com.zipstats.app.di.AppOverlayRepositoryEntryPoint
import com.zipstats.app.repository.AppOverlayRepository
import com.zipstats.app.ui.onboarding.OnboardingDialog
import com.zipstats.app.ui.records.OnboardingViewModel
import com.zipstats.app.ui.theme.DialogShape
import com.zipstats.app.utils.DateUtils
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    navController: NavController,
    viewModel: RoutesViewModel = hiltViewModel(),
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
    
    val routes by viewModel.routes.collectAsState()
    val userScooters by viewModel.userScooters.collectAsState()
    val selectedScooter by viewModel.selectedScooter.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isLoading = uiState is RoutesUiState.Loading

    var routeToDelete by remember { mutableStateOf<Route?>(null) }
    var routeToView by remember { mutableStateOf<Route?>(null) }
    var routeAddedToRecords by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var showOnboardingDialog by remember { mutableStateOf(false) }

    // Estado de scroll único
    val listState = rememberLazyListState()

    // Variable para detectar nuevos registros y hacer scroll
    var previousRoutesSize by remember { mutableStateOf(routes.size) }
    var isFilterChanging by remember { mutableStateOf(false) }
    var isInitialLoad by remember { mutableStateOf(true) }

    // Filtrar rutas según el patinete seleccionado
    val filteredRoutes = when (selectedScooter) {
        null -> routes
        else -> routes.filter { route ->
            route.scooterId == selectedScooter
        }
    }

    // Detectar la primera carga completada
    LaunchedEffect(uiState) {
        if (uiState is RoutesUiState.Success && isInitialLoad) {
            kotlinx.coroutines.delay(100) // Pequeño delay para asegurar que los datos se rendericen
            isInitialLoad = false
        }
    }

    // Detectar cambio de filtro para evitar flash del EmptyStateView
    LaunchedEffect(selectedScooter) {
        isFilterChanging = true
        kotlinx.coroutines.delay(150) // Pequeño delay para evitar el flash
        isFilterChanging = false
        if (filteredRoutes.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Scroll automático al añadir
    LaunchedEffect(routes.size) {
        if (routes.size > previousRoutesSize && filteredRoutes.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
        previousRoutesSize = routes.size
    }

    // Verificar si las rutas ya fueron añadidas a registros
    LaunchedEffect(routes) {
        routes.forEach { route ->
            if (!routeAddedToRecords.containsKey(route.id)) {
                val isAdded = viewModel.isRouteAddedToRecords(route)
                routeAddedToRecords = routeAddedToRecords + (route.id to isAdded)
            }
        }
    }

    // Recargar rutas tras eliminar
    LaunchedEffect(uiState) {
        if (uiState is RoutesUiState.Success) {
            val existingRouteIds = routes.map { it.id }.toSet()
            routeAddedToRecords = routeAddedToRecords.filterKeys { routeId ->
                existingRouteIds.contains(routeId)
            }
        }
    }

    // Diálogos
    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Información") },
            text = { Text(msg) },
            confirmButton = {
                DialogConfirmButton(
                    text = "Aceptar",
                    onClick = { viewModel.clearError() }
                )
            },
            shape = DialogShape
        )
    }

    routeToDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { routeToDelete = null },
            title = { Text("Confirmar eliminación") },
            text = { Text("¿Estás seguro de que quieres eliminar esta ruta?") },
            confirmButton = {
                DialogConfirmButton(
                    text = "Eliminar",
                    onClick = {
                        viewModel.deleteRoute(route.id)
                        routeAddedToRecords = routeAddedToRecords - route.id
                        routeToDelete = null
                    }
                )
            },
            dismissButton = {
                DialogCancelButton(
                    text = "Cancelar",
                    onClick = { routeToDelete = null }
                )
            },
            shape = DialogShape
        )
    }

    routeToView?.let { clickedRoute ->
        // Buscar la ruta actual en filteredRoutes primero, luego en routes como fallback
        val sortedFilteredRoutes = filteredRoutes.sortedByDescending { it.startTime }
        val currentRoute = sortedFilteredRoutes.find { it.id == clickedRoute.id } 
            ?: routes.find { it.id == clickedRoute.id } 
            ?: clickedRoute
        val isAddedToRecords = routeAddedToRecords[currentRoute.id] ?: false
        RouteDetailDialog(
            route = currentRoute,
            allRoutes = sortedFilteredRoutes, // Ordenar por fecha descendente
            onRouteChange = { newRoute ->
                // Actualizar routeToView con la nueva ruta
                routeToView = newRoute
            },
            onDismiss = { routeToView = null },
            onDelete = {
                routeToDelete = currentRoute
                routeToView = null
            },
            onAddToRecords = if (!isAddedToRecords) {
                {
                    viewModel.addRouteToRecords(currentRoute)
                    routeAddedToRecords = routeAddedToRecords + (currentRoute.id to true)
                }
            } else null,
            onShare = {
                viewModel.shareRouteWithMap(currentRoute)
                routeToView = null
            }
        )
    }

    if (showOnboardingDialog) {
        OnboardingDialog(
            onDismiss = {
                showOnboardingDialog = false
            },
            onRegisterVehicle = {
                showOnboardingDialog = false
                navController.navigate("${Screen.Profile.route}?openAddVehicle=true")
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Historial de Rutas",
                        fontWeight = FontWeight.Bold
                    )
                },
                // Estilo moderno 'Surface' igual que Historial de Registros
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
                    // Verificar si hay vehículos antes de permitir iniciar ruta
                    if (userScooters.isEmpty()) {
                        showOnboardingDialog = true
                    } else {
                        navController.navigate(Screen.Tracking.route) {
                            // No necesitamos hacer popUpTo aquí porque queremos mantener RoutesScreen
                            // en la pila para poder volver si el usuario cancela
                            launchSingleTop = true
                        }
                    }
                },
                enabled = vehiclesReady, // Deshabilitar hasta que los vehículos estén cargados
                containerColor = MaterialTheme.colorScheme.tertiary
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "Iniciar seguimiento GPS",
                    modifier = Modifier.size(24.dp)
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
                    selectedTabIndex = when(selectedScooter) {
                        null -> 0
                        else -> userScooters.indexOfFirst { it.id == selectedScooter } + 1
                    },
                    edgePadding = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedScooter == null,
                        onClick = { viewModel.setSelectedScooter(null) }
                    ) {
                        Text(
                            text = "Todos",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                            fontWeight = if (selectedScooter == null) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    userScooters.forEach { scooter ->
                        Tab(
                            selected = selectedScooter == scooter.id,
                            onClick = { viewModel.setSelectedScooter(scooter.id) }
                        ) {
                            Text(
                                text = scooter.modelo,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                fontWeight = if (selectedScooter == scooter.id) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lista de rutas (Diseño moderno de 2 columnas)
            if (isLoading || isInitialLoad) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredRoutes.isEmpty() && (routes.isEmpty() || !isFilterChanging)) {
                // Estado vacío
                EmptyStateRoutes(
                    onStartRoute = {
                        // Verificar si hay vehículos antes de permitir iniciar ruta
                        if (!vehiclesReady) {
                            // Esperar a que los vehículos estén cargados
                            return@EmptyStateRoutes
                        }
                        if (userScooters.isEmpty()) {
                            showOnboardingDialog = true
                        } else {
                            navController.navigate(Screen.Tracking.route) {
                                launchSingleTop = true
                            }
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
                        items = filteredRoutes,
                        key = { _, route -> route.id }
                    ) { index, route ->
                        Column {
                            ExpandableRow(
                                onClick = { routeToView = route },
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
                                    // Nombre del vehículo (Grande)
                                    Text(
                                        text = userScooters.find { it.id == route.scooterId }?.modelo ?: route.scooterName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // Fecha y hora (Pequeña y gris)
                                    Text(
                                        text = DateUtils.formatForDisplayWithTime(route.startTime),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                // COLUMNA DERECHA: Distancia y Tiempo
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Distancia (El dato "Héroe")
                                    Text(
                                        text = String.format("%.1f km", route.totalDistance),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    // Duración (Dato secundario)
                                    Text(
                                        text = route.durationFormatted,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
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