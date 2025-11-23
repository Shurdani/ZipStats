package com.zipstats.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * Button con animación de rebote mejorada al presionar y click
 */
@Composable
fun AnimatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var clicked by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = when {
            clicked -> 0.9f // Rebote más visible al hacer click
            isPressed && enabled -> 0.95f // Sutil al presionar
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale",
        finishedListener = {
            clicked = false
        }
    )
    
    Button(
        onClick = {
            clicked = true
            onClick()
        },
        modifier = modifier.scale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        content = { content() }
    )
}

/**
 * IconButton con animación de rebote mejorada al presionar y click
 */
@Composable
fun AnimatedIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var clicked by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = when {
            clicked -> 0.8f // Rebote más visible al hacer click
            isPressed && enabled -> 0.9f // Sutil al presionar
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "iconButtonScale",
        finishedListener = {
            clicked = false
        }
    )
    
    IconButton(
        onClick = {
            clicked = true
            onClick()
        },
        modifier = modifier.scale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        content = { content() }
    )
}

/**
 * FloatingActionButton con animación de rebote mejorada al presionar y click
 */
@Composable
fun AnimatedFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var clicked by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = when {
            clicked -> 0.85f // Rebote más visible al hacer click
            isPressed && enabled -> 0.92f // Sutil al presionar
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fabScale",
        finishedListener = {
            clicked = false
        }
    )
    
    androidx.compose.material3.FloatingActionButton(
        onClick = {
            clicked = true
            onClick()
        },
        modifier = modifier.scale(scale),
        containerColor = containerColor
    ) {
        content()
    }
}

