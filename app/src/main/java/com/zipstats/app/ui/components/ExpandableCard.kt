package com.zipstats.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Card
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Card con animación de expansión al hacer click
 * Simula que la tarjeta se expande antes de navegar, preparando para Shared Element Transition
 */
@Composable
fun ExpandableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isExpanding by remember { mutableStateOf(false) }
    
    // Animación de escala normal (al presionar)
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isExpanding) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    
    // Animación de expansión (al hacer click, antes de navegar)
    val expandScale by animateFloatAsState(
        targetValue = if (isExpanding) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expandScale",
        finishedListener = {
            // Reset después de que la animación termine
            if (isExpanding) {
                isExpanding = false
            }
        }
    )
    
    // Combinar ambas escalas
    val combinedScale = pressScale * expandScale
    
    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = combinedScale
                scaleY = combinedScale
                // Añadir un poco de elevación al expandir
                shadowElevation = if (isExpanding) 12f else 4f
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    if (!isExpanding) {
                        isExpanding = true
                        // Ejecutar onClick inmediatamente para que la navegación sea rápida
                        // La animación de expansión seguirá visualmente
                        onClick()
                    }
                }
            ),
        content = { content() }
    )
}

