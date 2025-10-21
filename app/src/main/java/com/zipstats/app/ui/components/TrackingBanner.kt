package com.zipstats.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zipstats.app.service.TrackingStateManager

/**
 * Banner que se muestra cuando hay una grabación GPS activa
 * Permite volver rápidamente a la pantalla de tracking
 */
@Composable
fun TrackingBanner(
    trackingStateManager: TrackingStateManager,
    onBannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTracking by trackingStateManager.isTracking.collectAsState()
    val isPaused by trackingStateManager.isPaused.collectAsState()
    val distance by trackingStateManager.distance.collectAsState()
    val duration by trackingStateManager.duration.collectAsState()
    
    AnimatedVisibility(
        visible = isTracking,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        TrackingBannerContent(
            isPaused = isPaused,
            distance = distance,
            duration = duration,
            onBannerClick = onBannerClick,
            modifier = modifier
        )
    }
}

@Composable
private fun TrackingBannerContent(
    isPaused: Boolean,
    distance: String,
    duration: String,
    onBannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animación del icono de GPS (pulsante cuando está grabando)
    val infiniteTransition = rememberInfiniteTransition(label = "gps_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPaused) 1f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val backgroundColor = if (isPaused) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    
    val contentColor = if (isPaused) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onBannerClick),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icono GPS con animación
            Icon(
                imageVector = Icons.Default.GpsFixed,
                contentDescription = null,
                tint = contentColor.copy(alpha = alpha),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Información de la grabación
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isPaused) "GPS Pausado" else "Grabando ruta GPS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = distance,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }
            
            // Indicador de "Toca para ver"
            Text(
                text = "Ver",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

