package com.zipstats.app.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

/**
 * Componente de texto reutilizable con comportamiento inteligente.
 * 
 * **USAR SIEMPRE en lugar de Text() para texto visible al usuario.**
 * 
 * Comportamiento:
 * - **autoResize = false** (por defecto): Si el texto no cabe, muestra "..." (ellipsis)
 * - **autoResize = true**: Si el texto no cabe, reduce el tamaño de fuente hasta que quepa
 * 
 * El escalado adaptativo del fontScale del sistema se aplica automáticamente
 * limitando entre 0.8x y 1.15x para mantener legibilidad.
 * 
 * @param text Texto a mostrar
 * @param modifier Modifier para el componente
 * @param style Estilo de texto de MaterialTheme (por defecto bodyMedium)
 * @param color Color del texto (por defecto usa el color del estilo)
 * @param fontSize Tamaño de fuente específico (opcional, sobrescribe el del estilo)
 * @param fontWeight Peso de fuente (opcional, sobrescribe el del estilo)
 * @param textAlign Alineación del texto (opcional)
 * @param maxLines Número máximo de líneas (por defecto 1)
 * @param autoResize Si es true, reduce el tamaño de fuente si no cabe. Si es false, usa ellipsis.
 * 
 * @example
 * ```
 * // Texto normal: corta con "..."
 * ZipStatsText(
 *     text = "Calle del Doctor Trueta, Barcelona",
 *     style = MaterialTheme.typography.bodyLarge
 * )
 * 
 * // Métrica numérica: reduce tamaño para que quepa
 * ZipStatsText(
 *     text = "1.245 km",
 *     style = MaterialTheme.typography.displayLarge,
 *     autoResize = true
 * )
 * ```
 */
@Composable
fun ZipStatsText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    // 🔥 NUEVO PARÁMETRO: Si es true, reduce el tamaño en vez de cortar
    autoResize: Boolean = false
) {
    val textColor = color.takeOrElse {
        style.color.takeOrElse { MaterialTheme.colorScheme.onSurface }
    }
    
    // Combinamos el estilo base con los overrides usando copy()
    val mergedStyle = style.copy(
        color = textColor,
        fontSize = if (fontSize.isUnspecified) style.fontSize else fontSize,
        fontWeight = fontWeight ?: style.fontWeight,
        textAlign = textAlign ?: style.textAlign
    )

    if (autoResize) {
        // --- LÓGICA PARA MÉTRICAS (REDUCIR TAMAÑO) ---
        var resizedTextStyle by remember(style, text, fontSize, fontWeight) { 
            mutableStateOf(mergedStyle) 
        }
        
        Text(
            text = text,
            modifier = modifier,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            style = resizedTextStyle,
            onTextLayout = { result ->
                // Si el texto se desborda horizontalmente, reducimos la fuente un 10% y reintentamos
                if (result.didOverflowWidth) {
                    val currentSize = resizedTextStyle.fontSize
                    // Evitamos bucles infinitos o textos microscópicos (mínimo 8sp)
                    if (!currentSize.isUnspecified && currentSize.value > 8f) {
                        resizedTextStyle = resizedTextStyle.copy(
                            fontSize = currentSize * 0.9f
                        )
                    }
                }
            }
        )
    } else {
        // --- LÓGICA PARA TEXTO NORMAL (PUNTOS SUSPENSIVOS) ---
        
        // Aplicamos el limitador de escalado (adaptiveSp original) aquí
        val density = LocalDensity.current
        // Limitamos que el usuario no ponga la letra gigante en ajustes (max 1.15x, min 0.8x)
        val fontScale = density.fontScale.coerceIn(0.8f, 1.15f)
        
        val scaledStyle = if (mergedStyle.fontSize.isSp) {
            // Aplicamos el escalado limitado
            val baseSize = mergedStyle.fontSize.value
            val scaledSize = (baseSize * fontScale).sp
            mergedStyle.copy(fontSize = scaledSize)
        } else {
            mergedStyle
        }

        Text(
            text = text,
            modifier = modifier,
            color = textColor,
            style = scaledStyle,
            textAlign = textAlign ?: TextAlign.Start,
            // Aquí está la clave para que salgan los puntos suspensivos (...)
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines
        )
    }
}

