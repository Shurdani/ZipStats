package com.zipstats.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Botón principal (Guardar / Aceptar / Confirmar).
 */
@Composable
fun DialogSaveButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
    ) {
        ZipStatsText(text)
    }
}

/**
 * Botón de cancelar. (Neutral en Material Design)
 */
@Composable
fun DialogNeutralButton(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp)
    ) {
        ZipStatsText(text)
    }
}

/**
 * Botón rojo para acciones destructivas (Eliminar)
 */
@Composable
fun DialogDeleteButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        ZipStatsText(text)
    }
}

/**
 * Botón principal ancho completo
 * (algunas pantallas de onboarding lo usan).
 */
@Composable
fun DialogFullWidthButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        ZipStatsText(text)
    }
}

/**
 * Botón de confirmar simple (alias de Save).
 * Algunas pantallas lo usan con este nombre.
 */
@Composable
fun DialogConfirmButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    DialogSaveButton(
        text = text,
        onClick = onClick,
        enabled = enabled
    )
}

/**
 * Botón de cancelar simple (alias de Neutral).
 */
@Composable
fun DialogCancelButton(
    text: String,
    onClick: () -> Unit
) {
    DialogNeutralButton(
        text = text,
        onClick = onClick
    )
}
@Composable
fun DialogApplyButton(
    text: String = "Aplicar",
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        ZipStatsText(text)
    }
}

@Composable
fun DialogOptionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            ZipStatsText(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
