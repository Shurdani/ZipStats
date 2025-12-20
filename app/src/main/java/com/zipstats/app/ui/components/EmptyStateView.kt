package com.zipstats.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Componente reutilizable para estados vacíos
 * Diseñado para ser modular y flexible
 */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 96.dp,
    actionButton: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icono con contenedor circular y color de fondo
        Box(
            modifier = Modifier.size(iconSize * 1.2f),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {}
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Título
        ZipStatsText(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Descripción
        ZipStatsText(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp)
        )

        // Botón de acción opcional
        actionButton?.let {
            Spacer(modifier = Modifier.height(32.dp))
            it()
        }
    }
}

/**
 * Variantes predefinidas para casos comunes
 */
@Composable
fun EmptyStateRecords(
    onAddRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyStateView(
        icon = Icons.Default.History,
        title = "Sin historial de viajes",
        description = "Añade tu primer registro de kilometraje para comenzar a trackear tus viajes.",
        modifier = modifier,
        actionButton = {
            Button(
                onClick = onAddRecord,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ZipStatsText("Añadir registro")
            }
        }
    )
}

@Composable
fun EmptyStateRoutes(
    onStartRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyStateView(
        icon = Icons.Default.GpsFixed,
        title = "Sin rutas guardadas",
        description = "Inicia tu primera ruta GPS para comenzar a registrar tus viajes con mapa.",
        modifier = modifier,
        actionButton = {
            Button(
                onClick = onStartRoute,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ZipStatsText("Iniciar ruta")
            }
        }
    )
}

@Composable
fun EmptyStateRepairs(
    onAddRepair: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyStateView(
        icon = Icons.Default.Build,
        title = "Sin historial de mantenimiento",
        description = "Añade reparaciones, cambios de piezas o revisiones periódicas para mantener un registro completo.",
        modifier = modifier,
        actionButton = {
            Button(
                onClick = onAddRepair,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ZipStatsText("Añadir mantenimiento")
            }
        }
    )
}

@Composable
fun EmptyStateVehicles(
    onAddVehicle: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyStateView(
        icon = Icons.Default.Speed,
        title = "No tienes vehículos registrados",
        description = "Añade tu primer vehículo para comenzar a trackear tus viajes y mantenimientos.",
        modifier = modifier,
        actionButton = {
            Button(
                onClick = onAddVehicle,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ZipStatsText("Añadir vehículo")
            }
        }
    )
}

