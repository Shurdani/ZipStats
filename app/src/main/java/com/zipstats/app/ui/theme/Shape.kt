package com.zipstats.app.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp) // Más redondeado para diálogos y cards principales
)

// Formas adicionales para componentes específicos
val DialogShape = RoundedCornerShape(32.dp) // Diálogos frontales más redondeados
val CardShape = RoundedCornerShape(20.dp) // Cards con bordes más suaves
val SmallCardShape = RoundedCornerShape(12.dp) // Cards pequeñas 