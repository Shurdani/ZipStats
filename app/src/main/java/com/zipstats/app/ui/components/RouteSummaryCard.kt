package com.zipstats.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zipstats.app.utils.LocationUtils

/**
 * Tarjeta reutilizable que muestra un resumen de la ruta con:
 * - Título y subtítulo
 * - Estadísticas (distancia, tiempo, velocidad media)
 * - Información del clima (opcional)
 * 
 * Diseñada para usarse en pantallas de mapa completo y animación de ruta.
 */
@Composable
fun RouteSummaryCard(
    title: String,
    subtitle: String,
    distanceKm: Float,
    duration: String,
    avgSpeed: Float,
    temperature: Int? = null,
    weatherText: String? = null,
    @DrawableRes weatherIconRes: Int? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                ZipStatsText(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    maxLines = 1
                )

                ZipStatsText(
                    text = "ZipStats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1
                )
            }

            ZipStatsText(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color.LightGray,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    StatItemModern(
                        icon = Icons.Default.Straighten,
                        value = "${LocationUtils.formatNumberSpanish(distanceKm.toDouble())} km",
                        label = "Distancia"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatItemModern(
                        icon = Icons.Default.Timer,
                        value = duration,
                        label = "Tiempo"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatItemModern(
                        icon = Icons.Default.Speed,
                        value = "${LocationUtils.formatNumberSpanish(avgSpeed.toDouble())} km/h",
                        label = "Vel. Media"
                    )
                }
            }

            if (temperature != null && weatherText != null && weatherIconRes != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = weatherIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(androidx.compose.ui.graphics.Color.White)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        ZipStatsText(
                            text = "${formatTemperature(temperature.toDouble(), decimals = 0)}°C",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        ZipStatsText(
                            text = weatherText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItemModern(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color(0xFF90CAF9),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        ZipStatsText(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = androidx.compose.ui.graphics.Color.White,
            maxLines = 1
        )
        ZipStatsText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
            maxLines = 1
        )
    }
}

/**
 * Formatea la temperatura asegurándose de que 0 se muestre sin signo menos
 */
private fun formatTemperature(temperature: Double, decimals: Int = 1): String {
    // Si la temperatura es exactamente 0 o muy cercana a 0, mostrar sin signo menos
    val absTemp = kotlin.math.abs(temperature)
    val formatted = LocationUtils.formatNumberSpanish(absTemp, decimals)
    
    // Si la temperatura original es negativa (y no es 0), añadir el signo menos
    return if (temperature < 0 && absTemp > 0.001) {
        "-$formatted"
    } else {
        formatted
    }
}

