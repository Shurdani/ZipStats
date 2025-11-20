package com.zipstats.app.ui.routes

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.model.Route
import com.zipstats.app.navigation.Screen
import com.zipstats.app.utils.DateUtils
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    navController: NavController,
    viewModel: RoutesViewModel = hiltViewModel()
) {
    val routes by viewModel.routes.collectAsState()
    val userScooters by viewModel.userScooters.collectAsState()
    val selectedScooter by viewModel.selectedScooter.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isLoading = uiState is RoutesUiState.Loading

    var routeToDelete by remember { mutableStateOf<Route?>(null) }
    var routeToView by remember { mutableStateOf<Route?>(null) }
    var routeAddedToRecords by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    
    // Estado para controlar el scroll de la lista
    val listState = rememberLazyListState()
    
    // Filtrar rutas según el patinete seleccionado
    val filteredRoutes = when (selectedScooter) {
        null -> routes
        else -> routes.filter { route ->
            route.scooterId == selectedScooter
        }
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
    
    // Recargar rutas cuando el uiState cambie a Success (después de eliminar)
    LaunchedEffect(uiState) {
        if (uiState is RoutesUiState.Success) {
            // Limpiar el estado local de rutas añadidas a registros para rutas que ya no existen
            val existingRouteIds = routes.map { it.id }.toSet()
            routeAddedToRecords = routeAddedToRecords.filterKeys { routeId ->
                existingRouteIds.contains(routeId)
            }
        }
    }
    
    // Mostrar mensaje si existe
    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Información") },
            text = { Text(msg) },
            confirmButton = {
                com.zipstats.app.ui.components.DialogConfirmButton(
                    text = "Aceptar",
                    onClick = { viewModel.clearError() }
                )
            },
            shape = com.zipstats.app.ui.theme.DialogShape
        )
    }
    
    // Diálogo de confirmación para borrar
    routeToDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { routeToDelete = null },
            title = { Text("Confirmar eliminación") },
            text = { Text("¿Estás seguro de que quieres eliminar esta ruta?") },
            confirmButton = {
                com.zipstats.app.ui.components.DialogConfirmButton(
                    text = "Eliminar",
                    onClick = {
                        viewModel.deleteRoute(route.id)
                        // Limpiar el estado local de la ruta eliminada
                        routeAddedToRecords = routeAddedToRecords - route.id
                        routeToDelete = null
                    }
                )
            },
            dismissButton = {
                com.zipstats.app.ui.components.DialogCancelButton(
                    text = "Cancelar",
                    onClick = { routeToDelete = null }
                )
            },
            shape = com.zipstats.app.ui.theme.DialogShape
        )
    }
    
    // Diálogo de detalles de ruta
    routeToView?.let { clickedRoute ->
        // Siempre obtener la ruta más reciente de la lista para asegurar datos actualizados
        val currentRoute = routes.find { it.id == clickedRoute.id } ?: clickedRoute
        val isAddedToRecords = routeAddedToRecords[currentRoute.id] ?: false
        RouteDetailDialog(
            route = currentRoute,
            onDismiss = { routeToView = null },
            onDelete = { 
                routeToDelete = currentRoute
                routeToView = null
            },
            onAddToRecords = if (!isAddedToRecords) {
                {
                    viewModel.addRouteToRecords(currentRoute)
                    // Actualizar el estado local
                    routeAddedToRecords = routeAddedToRecords + (currentRoute.id to true)
                }
            } else null,
            onShare = {
                viewModel.shareRouteWithMap(currentRoute)
                routeToView = null
            }
        )
    }
    
    // Mostrar error si existe
    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                com.zipstats.app.ui.components.DialogConfirmButton(
                    text = "Aceptar",
                    onClick = { viewModel.clearError() }
                )
            },
            shape = com.zipstats.app.ui.theme.DialogShape
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Rutas") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            // FAB para seguimiento GPS
            FloatingActionButton(
                onClick = { navController.navigate(Screen.Tracking.route) },
                containerColor = MaterialTheme.colorScheme.tertiary
            ) {
                androidx.compose.material3.Icon(
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

            // --- ESTADO DE LA LISTA Y EFECTO DE SCROLL ---
            val listState = rememberLazyListState()

            // Cuando cambia el filtro (selectedScooter), volvemos arriba
            LaunchedEffect(selectedScooter) {
                listState.scrollToItem(0)
            }

            // Encabezados de la tabla - Adaptativo según el tamaño de pantalla
            val configuration = LocalConfiguration.current
            val screenWidthDp = configuration.screenWidthDp
            val isSmallScreen = screenWidthDp < 360 // Pantallas muy pequeñas (< 360dp)
            val isMediumScreen = screenWidthDp < 420 // Pantallas medianas (360-420dp)

            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isSmallScreen) 8.dp else 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vehículo",
                    modifier = Modifier.weight(1.4f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.87f),
                    
                )
                Text(
                    text = "Fecha",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.87f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Km",
                    modifier = Modifier.weight(1.0f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.87f),
                    textAlign = TextAlign.End,
                )
                Text(
                    text = "Lapso",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.87f),
                    textAlign = TextAlign.End,
                )
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            // Lista de rutas
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { routeToView = route }
                                    .background(
                                        color = if (index % 2 == 0) {
                                            Color.Transparent
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        }
                                    )
                                    .padding(
                                        horizontal = if (screenWidthDp < 360) 8.dp else 16.dp,
                                        vertical = 14.dp
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            Text(
                                text = userScooters.find { it.id == route.scooterId }?.modelo ?: route.scooterName,
                                modifier = Modifier.weight(1.4f),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = DateUtils.formatForDisplay(
                                    Instant.ofEpochMilli(route.startTime)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                ),
                                modifier = Modifier.weight(0.8f),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format("%.1f km", route.totalDistance),
                                modifier = Modifier.weight(1.0f),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp
                                ),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = route.durationFormatted,
                                modifier = Modifier.weight(0.8f),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp
                                ),
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
    }
}
