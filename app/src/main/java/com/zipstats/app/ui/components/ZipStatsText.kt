package com.zipstats.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.zipstats.app.ui.theme.adaptiveSp

/**
 * Componente de texto reutilizable que aplica automáticamente escalado adaptativo
 * basado en el fontScale del sistema, respetando MaterialTheme y manteniendo
 * coherencia en toda la aplicación.
 * 
 * Incluye auto-shrink para evitar cortes de texto en dispositivos con fontScale alto.
 * 
 * **USAR SIEMPRE en lugar de Text() para texto visible al usuario.**
 * 
 * @param text Texto a mostrar
 * @param modifier Modifier para el componente
 * @param style Estilo de texto de MaterialTheme (por defecto bodyMedium)
 * @param color Color del texto (por defecto usa el color del estilo)
 * @param maxLines Número máximo de líneas (por defecto 1 para evitar wrapping)
 * @param overflow Comportamiento de overflow (por defecto Ellipsis)
 * @param textAlign Alineación del texto (por defecto Start)
 * @param fontWeight Peso de fuente (opcional, sobrescribe el del estilo)
 * @param softWrap Si es false, evita el wrapping automático
 * 
 * @example
 * ```
 * ZipStatsText(
 *     text = "34390.2 km",
 *     style = MaterialTheme.typography.headlineSmall,
 *     maxLines = 1
 * )
 * ```
 */
@Composable
fun ZipStatsText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
    softWrap: Boolean = false
) {
    // Reiniciar el estilo cuando cambie el texto o el estilo base
    var resizedTextStyle by remember(style, text) { mutableStateOf(style) }
    
    val finalStyle = resizedTextStyle.copy(
        fontSize = adaptiveSp(resizedTextStyle.fontSize.value),
        fontWeight = fontWeight ?: resizedTextStyle.fontWeight
    )
    
    Text(
        text = text,
        modifier = modifier,
        style = finalStyle,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign ?: TextAlign.Start,
        softWrap = softWrap,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && maxLines == 1) {
                val currentSize = resizedTextStyle.fontSize
                val newSize = currentSize * 0.92f
                // Solo reducir si el nuevo tamaño es válido y menor que el actual
                if (newSize > 8.sp && newSize < currentSize) {
                    resizedTextStyle = resizedTextStyle.copy(fontSize = newSize)
                }
            }
        }
    )
}

