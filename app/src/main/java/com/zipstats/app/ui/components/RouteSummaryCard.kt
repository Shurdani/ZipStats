package com.zipstats.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zipstats.app.model.Route
import com.zipstats.app.repository.WeatherRepository
import com.zipstats.app.utils.LocationUtils

@Composable
fun RouteSummaryCard(
    title: String,
    subtitle: String,
    distanceKm: Float,
    duration: String,
    avgSpeed: Float,
    temperature: Double? = null,
    weatherText: String? = null,
    @DrawableRes weatherIconRes: Int? = null,
    badgeEmojiText: String? = null, // Emojis de badges (⚠️, 🔵, 🟡) para mostrar antes del icono del clima
    modifier: Modifier = Modifier
) {
    val overlayContainer = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
    val overlayOnContainer = MaterialTheme.colorScheme.onSurface
    val overlayBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val weatherChipContainer = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    val weatherChipContent = MaterialTheme.colorScheme.onSecondaryContainer

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp), // Más redondeado, estilo iOS/Material 3 moderno
        colors = CardDefaults.cardColors(
            containerColor = overlayContainer
        ),
        border = BorderStroke(1.dp, overlayBorder),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp) // Un poco más de aire interno
        ) {
            // CABECERA: Título y Logo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    ZipStatsText(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = overlayOnContainer,
                        maxLines = Int.MAX_VALUE

                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ZipStatsText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = overlayOnContainer.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }

                // Marca de agua "ZipStats"
                ZipStatsText(

                    text = "ZipStats",

                    style = MaterialTheme.typography.bodySmall,

                    fontWeight = FontWeight.Bold,

                    color = overlayOnContainer.copy(alpha = 0.75f),

                    maxLines = 1

                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // MÉTRICAS (Row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween // SpaceBetween llena mejor el ancho
            ) {
                StatItemModern(
                    icon = Icons.Default.Straighten,
                    value = "${LocationUtils.formatNumberSpanish(distanceKm.toDouble())} km",
                    label = "Distancia"
                )

                // Separador vertical sutil (opcional, pero queda elegante)
                VerticalDivider()

                StatItemModern(
                    icon = Icons.Default.Timer,
                    value = duration,
                    label = "Tiempo"
                )

                VerticalDivider()

                StatItemModern(
                    icon = Icons.Default.Speed,
                    value = "${LocationUtils.formatNumberSpanish(avgSpeed.toDouble())} km/h",
                    label = "Vel. Media"
                )
            }

            // CLIMA (Barra inferior)
            if (temperature != null && weatherText != null && weatherIconRes != null) {
                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    // Fondo un poco más claro que la tarjeta para crear capas
                    color = weatherChipContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Badges de seguridad (lluvia, calzada húmeda, condiciones extremas)
                        if (badgeEmojiText != null) {
                            ZipStatsText(
                                text = badgeEmojiText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = weatherChipContent,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Image(
                            painter = painterResource(id = weatherIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(weatherChipContent)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        ZipStatsText(
                            text = "${formatTemperature(temperature, decimals = 0)}°C",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = weatherChipContent,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Descripción del clima: permitir hasta 2 líneas y ajustar tamaño si es muy larga
                        val baseStyle = MaterialTheme.typography.titleMedium
                        // Si el texto es largo, reducir ligeramente el tamaño de fuente para que quepa mejor
                        val weatherTextStyle = if (weatherText.length > 26) {
                            baseStyle.copy(fontSize = baseStyle.fontSize * 0.9f)
                        } else {
                            baseStyle
                        }
                        ZipStatsText(
                            text = weatherText,
                            style = weatherTextStyle,
                            fontWeight = FontWeight.Bold,
                            color = weatherChipContent.copy(alpha = 0.9f),
                            modifier = Modifier,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wrapper "tonto" para pantallas: recibe una `Route` y usa la misma `RouteSummaryCard`.
 * La idea es que las pantallas NO tengan que calcular nada de clima (texto/icono).
 */
@Composable
fun RouteSummaryCardFromRoute(
    route: Route,
    title: String,
    subtitle: String,
    duration: String,
    modifier: Modifier = Modifier
) {
    val weatherText = route.weatherTemperature?.let {
        // En la tarjeta resumen mostramos siempre la DESCRIPCIÓN, no los mm de lluvia.
        route.weatherDescription
            ?.substringBefore("(")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: route.weatherCondition
                ?.takeIf { it.isNotBlank() }
                ?.let { WeatherRepository.getDescriptionForCondition(it) }
            ?: "Clima"
    }

    val weatherIconRes = route.weatherTemperature?.let {
        when {
            !route.weatherCondition.isNullOrBlank() ->
                WeatherRepository.getIconResIdForCondition(route.weatherCondition, route.weatherIsDay)
            route.weatherCode != null ->
                WeatherRepository.getIconResIdForWeather(route.weatherCode, if (route.weatherIsDay) 1 else 0)
            !route.weatherEmoji.isNullOrBlank() -> {
                val inferredCode = inferWeatherCodeFromEmoji(route.weatherEmoji)
                if (inferredCode != null) {
                    WeatherRepository.getIconResIdForWeather(inferredCode, if (route.weatherIsDay) 1 else 0)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    // Leer badges guardados de la ruta (ya calculados al finalizar)
    val hadRain = route.weatherHadRain == true
    val hasWetRoad = route.weatherHadWetRoad == true && !hadRain // Calzada mojada solo si NO hay lluvia
    val hasExtremeConditions = route.weatherHadExtremeConditions == true
    
    // Construir emojis de badges (igual que en TrackingScreen.kt)
    val badgeEmojiText = remember(hadRain, hasWetRoad, hasExtremeConditions) {
        buildString {
            // IMPORTANTE: Si hay 2 badges, el de condiciones extremas debe ir primero (izquierda).
            if (hasExtremeConditions) {
                append("⚠️")
            }
            // Badge principal (lluvia o calzada húmeda). Son excluyentes por lógica.
            if (hadRain) {
                append("🔵")
            } else if (hasWetRoad) {
                append("🟡")
            }
        }.takeIf { it.isNotBlank() }
    }

    RouteSummaryCard(
        title = title,
        subtitle = subtitle,
        distanceKm = route.totalDistance.toFloat(),
        duration = duration,
        avgSpeed = route.averageSpeed.toFloat(),
        temperature = route.weatherTemperature,
        weatherText = weatherText,
        weatherIconRes = weatherIconRes,
        badgeEmojiText = badgeEmojiText,
        modifier = modifier
    )
}

private fun inferWeatherCodeFromEmoji(emoji: String?): Int? {
    if (emoji.isNullOrBlank()) return null
    return when (emoji) {
        "☀️", "🌙" -> 0
        "🌤️", "🌥️", "☁️🌙" -> 1
        "☁️" -> 3
        "🌫️" -> 45
        "🌦️" -> 61
        "🌧️" -> 65
        "🥶" -> 66
        "❄️" -> 71
        "⛈️", "⚡" -> 95
        else -> null
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
            // Usa el color primario del tema para adaptarse a modo claro/oscuro
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        ZipStatsText(
            text = value,
            // 🔥 Aumentado de bodyLarge a titleMedium/Large para legibilidad
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        ZipStatsText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .height(30.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

/**
 * Formatea la temperatura y evita el "-0" o "-0.0"
 */
private fun formatTemperature(temperature: Double, decimals: Int = 1): String {
    // 1. Obtenemos el valor absoluto para formatear el número "limpio"
    val absTemp = kotlin.math.abs(temperature)
    
    // 2. Usamos tu utilidad para formatear (ej: "0,0" o "17,5")
    val formatted = LocationUtils.formatNumberSpanish(absTemp, decimals)

    // 3. TRUCO DE MAGIA 🪄
    // Comprobamos si el número que vamos a mostrar es realmente un cero.
    // Reemplazamos la coma por punto para asegurar que toDouble() funcione.
    val isEffectiveZero = try {
        formatted.replace(",", ".").toDouble() == 0.0
    } catch (e: Exception) {
        false
    }

    // 4. Lógica de signo:
    // Solo ponemos el "-" si la temperatura original es negativa Y NO es un cero efectivo.
    return if (temperature < 0 && !isEffectiveZero) {
        "-$formatted"
    } else {
        formatted
    }
}