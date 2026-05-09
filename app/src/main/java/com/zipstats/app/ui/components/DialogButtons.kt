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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val DIALOG_BUTTON_MAX_LINES = 2

/**
 * Título de [AlertDialog]: permite varias líneas (p. ej. 2) sin truncar con "..." en anchos estrechos.
 */
@Composable
fun DialogTitleText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    fontWeight: FontWeight? = FontWeight.Bold,
    maxLines: Int = 2,
    color: Color = Color.Unspecified
) {
    ZipStatsText(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = style,
        fontWeight = fontWeight,
        maxLines = maxLines,
        color = color,
        textAlign = TextAlign.Start
    )
}

/**
 * Texto principal del cuerpo de [AlertDialog]; por defecto hasta 6 líneas.
 */
@Composable
fun DialogContentText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = 6,
    color: Color = Color.Unspecified
) {
    ZipStatsText(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = style,
        maxLines = maxLines,
        color = color,
        textAlign = TextAlign.Start
    )
}

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
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        ZipStatsText(
            text,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = DIALOG_BUTTON_MAX_LINES,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        ZipStatsText(
            text,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = DIALOG_BUTTON_MAX_LINES,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
        ZipStatsText(
            text,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onError,
            maxLines = DIALOG_BUTTON_MAX_LINES,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        ZipStatsText(
            text,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = DIALOG_BUTTON_MAX_LINES,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        ZipStatsText(
            text,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = DIALOG_BUTTON_MAX_LINES,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
                style = MaterialTheme.typography.bodyLarge,
                maxLines = DIALOG_BUTTON_MAX_LINES,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
        }
    }
}
