package com.zipstats.app.ui.routes

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val message by viewModel.message.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
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
    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            title = { Text("Información") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearMessage() }) {
                    Text("Aceptar")
                }
            }
        )
    }
    
    // Diálogo de confirmación para borrar
    routeToDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { routeToDelete = null },
            title = { Text("Confirmar eliminación") },
            text = { Text("¿Estás seguro de que quieres eliminar esta ruta?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRoute(route.id)
                        // Limpiar el estado local de la ruta eliminada
                        routeAddedToRecords = routeAddedToRecords - route.id
                        routeToDelete = null
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { routeToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Diálogo de detalles de ruta
    routeToView?.let { route ->
        val isAddedToRecords = routeAddedToRecords[route.id] ?: false
        RouteDetailDialog(
            route = route,
            onDismiss = { routeToView = null },
            onDelete = { 
                routeToDelete = route
                routeToView = null
            },
            onAddToRecords = if (!isAddedToRecords) {
                {
                    viewModel.addRouteToRecords(route)
                    // Actualizar el estado local
                    routeAddedToRecords = routeAddedToRecords + (route.id to true)
                }
            } else null,
            onShare = {
                viewModel.shareRouteWithMap(route)
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
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("Aceptar")
                }
            }
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
                ScrollableTabRow(
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

            // Encabezados de la tabla
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Patinete",
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
                    text = "Distancia",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
                Text(
                    text = "Duración",
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
                    items(
                        items = filteredRoutes,
                        key = { it.id }
                    ) { route ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { routeToView = route }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = userScooters.find { it.id == route.scooterId }?.modelo ?: route.scooterName,
                                modifier = Modifier.weight(1.2f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                text = DateUtils.formatForDisplay(
                                    Instant.ofEpochMilli(route.startTime)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                ),
                                modifier = Modifier.weight(0.8f),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = String.format("%.1f km", route.totalDistance),
                                modifier = Modifier.weight(0.8f),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = route.durationFormatted,
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
}
