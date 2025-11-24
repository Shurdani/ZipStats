package com.zipstats.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Row expandible con animación al hacer click
 * Para usar en listas de rutas, registros, etc.
 */
@Composable
fun ExpandableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundAlpha: Float = 0f,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isExpanding by remember { mutableStateOf(false) }
    
    // Animación de escala al presionar
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isExpanding) 0.99f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    
    // Animación de expansión al hacer click
    val expandScale by animateFloatAsState(
        targetValue = if (isExpanding) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expandScale",
        finishedListener = {
            if (isExpanding) {
                isExpanding = false
            }
        }
    )
    
    val combinedScale = pressScale * expandScale
    
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = combinedScale
                scaleY = combinedScale
            }
            .background(
                if (backgroundAlpha > 0f) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = backgroundAlpha)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    if (!isExpanding) {
                        isExpanding = true
                        onClick()
                    }
                }
            )
    ) {
        content()
    }
}

