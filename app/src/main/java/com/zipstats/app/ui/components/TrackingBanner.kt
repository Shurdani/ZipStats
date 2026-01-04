package com.zipstats.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val bannerClickInteractionSource = remember { MutableInteractionSource() }
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
            .clickable(
                interactionSource = bannerClickInteractionSource,
                indication = null,
                onClick = onBannerClick
            ),
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
                ZipStatsText(
                    text = if (isPaused) "GPS Pausado" else "Grabando ruta GPS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZipStatsText(
                        text = distance,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                    ZipStatsText(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    ZipStatsText(
                        text = duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }
            
            // Indicador de "Toca para ver"
            ZipStatsText(
                text = "Ver",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

