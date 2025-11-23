package com.zipstats.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * Card animada con efecto de elevación y rebote mejorado
 * Ideal para listas y navegación a detalles
 */
@Composable
fun ClickableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    elevation: androidx.compose.ui.unit.Dp = 2.dp,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var clicked by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = when {
            clicked -> 0.96f // Rebote al hacer click
            isPressed && enabled -> 0.98f // Sutil al presionar
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "clickableCardScale",
        finishedListener = {
            clicked = false
        }
    )
    
    val animatedElevation by animateDpAsState(
        targetValue = when {
            clicked -> elevation.times(0.5f) // Elevación menor al hacer click
            isPressed && enabled -> elevation.times(1.5f) // Mayor elevación al presionar
            else -> elevation
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "clickableCardElevation"
    )
    
    Card(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = animatedElevation,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    clicked = true
                    onClick()
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { content() }
    )
}

