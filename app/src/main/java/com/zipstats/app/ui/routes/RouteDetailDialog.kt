package com.zipstats.app.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zipstats.app.model.Route
import com.zipstats.app.ui.components.BasicMapView
import com.zipstats.app.ui.components.CapturableMapView
import com.zipstats.app.utils.DateUtils
import com.zipstats.app.R
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun RouteDetailDialog(
    route: Route,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onAddToRecords: (() -> Unit)? = null,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    var googleMapRef by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header con título y botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detalles de la Ruta",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Botón de añadir a registros (si está disponible)
                        onAddToRecords?.let { addToRecords ->
                            IconButton(onClick = addToRecords) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = "Añadir a registros",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        // Botón de cerrar
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar"
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Contenido scrolleable
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Información básica de la ruta
                    RouteInfoCard(route = route)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Mapa capturable para compartir con marco
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    ) {
                        CapturableMapView(
                            route = route,
                            onMapReady = { googleMap ->
                                googleMapRef = googleMap
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(450.dp) // Aumentar altura para orientación vertical
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Estadísticas detalladas
                    RouteStatsCard(route = route)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // Botón de eliminar
                    TextButton(
                        onClick = onDelete
                    ) {
                        Text(
                            "Eliminar",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // Botón de compartir
                    TextButton(
                        onClick = {
                            if (googleMapRef != null) {
                                // Usar el mapa real para compartir
                                shareRouteWithRealMap(route, googleMapRef!!, context)
                            } else {
                                // Fallback al método original
                                onShare()
                            }
                        }
                    ) {
                        Text("Compartir")
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteInfoCard(route: Route) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Información de la Ruta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Patinete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = route.scooterName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Column {
                    Text(
                        text = "Fecha",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = DateUtils.formatForDisplay(
                            Instant.ofEpochMilli(route.startTime)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
        }
    }
}

@Composable
private fun MapPlaceholder(route: Route) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        if (route.points.isNotEmpty()) {
            // Usar mapa básico sin callback automático
            BasicMapView(
                route = route,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Mapa",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sin puntos GPS",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "No se registraron puntos de ubicación",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteStatsCard(route: Route) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Estadísticas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Estadísticas en una sola línea
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Distancia",
                    value = String.format("%.1f km", route.totalDistance)
                )
                StatItem(
                    label = "Duración",
                    value = route.durationFormatted
                )
                StatItem(
                    label = "Velocidad Media",
                    value = String.format("%.1f km/h", route.averageSpeed)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Comparte una ruta usando el mapa real capturado
 */
private fun shareRouteWithRealMap(
    route: Route,
    googleMap: com.google.android.gms.maps.GoogleMap,
    context: android.content.Context
) {
    try {
        // Mostrar indicador de carga
        android.widget.Toast.makeText(context, "Generando imagen...", android.widget.Toast.LENGTH_SHORT).show()
        
        // Tomar snapshot del mapa real
        googleMap.snapshot(com.google.android.gms.maps.GoogleMap.SnapshotReadyCallback { snapshotBitmap ->
            if (snapshotBitmap == null) {
                android.widget.Toast.makeText(context, "Error al capturar el mapa", android.widget.Toast.LENGTH_SHORT).show()
                return@SnapshotReadyCallback
            }

            // Usar corrutina para manejar la función asíncrona
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // Redimensionar el bitmap para evitar problemas de memoria
                    val resizedBitmap = redimensionarBitmap(snapshotBitmap, 1080)
                    
                    // Crear imagen final con estadísticas
                    val finalBitmap = createFinalRouteImage(route, resizedBitmap, context)
                    
                    // Compartir la imagen
                    shareBitmap(context, finalBitmap, route)
                    
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Error al procesar imagen: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        })
        
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error al compartir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}

/**
 * Redimensiona un Bitmap para evitar problemas de memoria
 */
private fun redimensionarBitmap(bitmapOriginal: android.graphics.Bitmap, tamanoMaximo: Int): android.graphics.Bitmap {
    val anchoOriginal = bitmapOriginal.width
    val altoOriginal = bitmapOriginal.height

    if (anchoOriginal <= tamanoMaximo && altoOriginal <= tamanoMaximo) {
        return bitmapOriginal
    }

    val ratio = minOf(
        tamanoMaximo.toFloat() / anchoOriginal,
        tamanoMaximo.toFloat() / altoOriginal
    )

    val nuevoAncho = (anchoOriginal * ratio).toInt()
    val nuevoAlto = (altoOriginal * ratio).toInt()

    return android.graphics.Bitmap.createScaledBitmap(bitmapOriginal, nuevoAncho, nuevoAlto, true)
}

/**
 * Crea la imagen final combinando el mapa con las estadísticas
 */
private suspend fun createFinalRouteImage(route: Route, mapBitmap: android.graphics.Bitmap, context: android.content.Context): android.graphics.Bitmap {
    val width = 1080
    val height = 1920
    val mapHeight = (height * 0.85f).toInt() // 85% para el mapa

    val finalBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(finalBitmap)
    
    // Fondo limpio
    canvas.drawColor(android.graphics.Color.rgb(245, 245, 245))
    
    // Dibujar el mapa escalado
    val mapWidth = mapBitmap.width
    val mapHeightActual = mapBitmap.height
    val scaleX = width.toFloat() / mapWidth
    val scaleY = mapHeight.toFloat() / mapHeightActual
    val scale = kotlin.math.min(scaleX, scaleY)
    
    val scaledWidth = (mapWidth * scale).toInt()
    val scaledHeight = (mapHeightActual * scale).toInt()
    val offsetX = (width - scaledWidth) / 2
    val offsetY = (mapHeight - scaledHeight) / 2
    
    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(mapBitmap, scaledWidth, scaledHeight, true)
    canvas.drawBitmap(scaledBitmap, offsetX.toFloat(), offsetY.toFloat(), null)
    
    // Inflar el layout de la tarjeta flotante
    val inflater = android.view.LayoutInflater.from(context)
    val cardView = inflater.inflate(R.layout.share_route_stats_card, null) as androidx.cardview.widget.CardView
    
    // Configurar título de la ruta
    val routeTitle = if (route.notes.isNotBlank()) {
        route.notes
    } else {
        // Generar título automático basado en las ciudades de inicio y fin
        val startCity = getCityName(route.points.firstOrNull()?.latitude, route.points.firstOrNull()?.longitude)
        val endCity = getCityName(route.points.lastOrNull()?.latitude, route.points.lastOrNull()?.longitude)
        val vehicleType = getVehicleTypeName(route.scooterId)
        
        when {
            startCity != null && endCity != null && startCity != endCity -> 
                "Mi $vehicleType por $startCity - $endCity"
            startCity != null -> 
                "Mi $vehicleType por $startCity"
            else -> 
                "Mi $vehicleType"
        }
    }
    cardView.findViewById<android.widget.TextView>(R.id.routeTitle).text = routeTitle
    
    
    // Configurar métricas con formato mejorado
    cardView.findViewById<android.widget.TextView>(R.id.distanceValue).text = 
        String.format("%.1f km", route.totalDistance)
    
    val durationMinutes = route.totalDuration / 60000
    cardView.findViewById<android.widget.TextView>(R.id.timeValue).text = 
        String.format("%d min", durationMinutes)
    
    // Configurar velocidad media con dato numérico
    cardView.findViewById<android.widget.TextView>(R.id.speedValue).text = 
        String.format("%.1f km/h media", route.averageSpeed)
    
    // Configurar icono del vehículo según el tipo
    val vehicleIconRes = getVehicleIconResource(route.scooterId)
    cardView.findViewById<android.widget.ImageView>(R.id.vehicleIcon).setImageResource(vehicleIconRes)
    
    // Configurar información del vehículo y fecha
    val vehicleInfoText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val instant = java.time.Instant.ofEpochMilli(route.startTime)
        val date = java.time.LocalDate.ofInstant(instant, java.time.ZoneId.systemDefault())
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", java.util.Locale.forLanguageTag("es-ES"))
        "${route.scooterName} | ${date.format(dateFormatter)}"
    } else {
        val simpleDateFormat = java.text.SimpleDateFormat("d 'de' MMMM 'de' yyyy", java.util.Locale.forLanguageTag("es-ES"))
        "${route.scooterName} | ${simpleDateFormat.format(java.util.Date(route.startTime))}"
    }
    cardView.findViewById<android.widget.TextView>(R.id.vehicleInfo).text = vehicleInfoText
    
    
    // Medir y renderizar la tarjeta
    val cardWidth = width - 64 // Márgenes de 32dp a cada lado
    val measureSpec = android.view.View.MeasureSpec.makeMeasureSpec(cardWidth, android.view.View.MeasureSpec.EXACTLY)
    cardView.measure(measureSpec, android.view.View.MeasureSpec.UNSPECIFIED)
    
    val cardHeight = cardView.measuredHeight
    val cardX = 32
    val cardY = height - cardHeight - 32 // Anclar al borde inferior con margen de 32dp
    
    cardView.layout(0, 0, cardView.measuredWidth, cardHeight)
    
    // Dibujar la tarjeta en el canvas
    canvas.save()
    canvas.translate(cardX.toFloat(), cardY.toFloat())
    cardView.draw(canvas)
    canvas.restore()
    
    // Watermark eliminado - el branding ya está integrado en la tarjeta
    
    return finalBitmap
}

/**
 * Obtiene el nombre de la ciudad basado en coordenadas GPS
 * Por simplicidad, devuelve nombres de ciudades españolas conocidas
 */
private fun getCityName(latitude: Double?, longitude: Double?): String? {
    if (latitude == null || longitude == null) return null
    
    // Mapeo básico de coordenadas a ciudades españolas
    // Barcelona
    if (latitude in 41.35..41.45 && longitude in 2.10..2.25) return "Barcelona"
    // Madrid
    if (latitude in 40.35..40.50 && longitude in -3.80..-3.60) return "Madrid"
    // Valencia
    if (latitude in 39.40..39.50 && longitude in -0.40..-0.30) return "Valencia"
    // Sevilla
    if (latitude in 37.30..37.45 && longitude in -6.05..-5.85) return "Sevilla"
    // Bilbao
    if (latitude in 43.20..43.30 && longitude in -3.00..-2.90) return "Bilbao"
    // Zaragoza
    if (latitude in 41.60..41.70 && longitude in -0.95..-0.85) return "Zaragoza"
    // Málaga
    if (latitude in 36.60..36.80 && longitude in -4.50..-4.30) return "Málaga"
    // Murcia
    if (latitude in 37.90..38.00 && longitude in -1.20..-1.10) return "Murcia"
    // Palma
    if (latitude in 39.50..39.60 && longitude in 2.60..2.70) return "Palma"
    // Las Palmas
    if (latitude in 28.10..28.20 && longitude in -15.50..-15.40) return "Las Palmas"
    
    return null
}

/**
 * Obtiene el nombre del tipo de vehículo basado en el ID del vehículo
 */
private suspend fun getVehicleTypeName(scooterId: String): String {
    return try {
        val vehicle = getVehicleById(scooterId)
        when (vehicle?.vehicleType) {
            com.zipstats.app.model.VehicleType.PATINETE -> "paseo en patinete"
            com.zipstats.app.model.VehicleType.BICICLETA -> "paseo en bicicleta"
            com.zipstats.app.model.VehicleType.E_BIKE -> "paseo en e-bike"
            com.zipstats.app.model.VehicleType.MONOCICLO -> "paseo en monociclo"
            else -> "paseo"
        }
    } catch (e: Exception) {
        "paseo"
    }
}

/**
 * Obtiene el recurso del icono del vehículo basado en el ID del vehículo
 */
private suspend fun getVehicleIconResource(scooterId: String): Int {
    return try {
        val vehicle = getVehicleById(scooterId)
        when (vehicle?.vehicleType) {
            com.zipstats.app.model.VehicleType.PATINETE -> com.zipstats.app.R.drawable.electric_scooter
            com.zipstats.app.model.VehicleType.BICICLETA -> com.zipstats.app.R.drawable.ciclismo
            com.zipstats.app.model.VehicleType.E_BIKE -> com.zipstats.app.R.drawable.bicicleta_electrica
            com.zipstats.app.model.VehicleType.MONOCICLO -> com.zipstats.app.R.drawable.unicycle
            else -> com.zipstats.app.R.drawable.electric_scooter
        }
    } catch (e: Exception) {
        com.zipstats.app.R.drawable.electric_scooter
    }
}

/**
 * Obtiene un vehículo por su ID
 */
private suspend fun getVehicleById(vehicleId: String): com.zipstats.app.model.Vehicle? {
    return try {
        // Obtener el repositorio de vehículos
        val vehicleRepository = com.zipstats.app.repository.VehicleRepository(
            com.google.firebase.firestore.FirebaseFirestore.getInstance(),
            com.google.firebase.auth.FirebaseAuth.getInstance()
        )
        
        // Buscar el vehículo en la lista de vehículos del usuario
        val vehicles = vehicleRepository.getUserVehicles()
        vehicles.find { it.id == vehicleId }
    } catch (e: Exception) {
        null
    }
}

/**
 * Dibuja la información de la ruta en la parte inferior
 */
@Suppress("UNUSED_PARAMETER")
private fun drawRouteInfo(canvas: android.graphics.Canvas, route: Route, width: Int, mapHeight: Int, infoHeight: Int) {
    val titlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(33, 33, 33)
        textSize = 72f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val subtitlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(100, 100, 100)
        textSize = 42f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val statPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(33, 150, 243)
        textSize = 64f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(120, 120, 120)
        textSize = 36f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    // Calcular posición inicial basada en el área de información
    val startY = mapHeight + (infoHeight * 0.15f)
    var yPos = startY
    
    // Título del patinete
    canvas.drawText("🛴 ${route.scooterName}", width / 2f, yPos, titlePaint)
    yPos += 60f
    
    // Fecha
    val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    val date = dateFormat.format(java.util.Date(route.startTime))
    canvas.drawText(date, width / 2f, yPos, subtitlePaint)
    yPos += 80f

    // Estadísticas principales
    val col1X = width * 0.25f
    val col2X = width * 0.5f
    val col3X = width * 0.75f
    canvas.drawText(String.format("%.2f", route.totalDistance), col1X, yPos, statPaint)
    canvas.drawText("km", col1X, yPos + 50f, labelPaint)
    canvas.drawText(formatDurationShort(route.totalDuration), col2X, yPos, statPaint)
    canvas.drawText("tiempo", col2X, yPos + 50f, labelPaint)
    canvas.drawText(String.format("%.1f", route.averageSpeed), col3X, yPos, statPaint)
    canvas.drawText("km/h media", col3X, yPos + 50f, labelPaint)

    // Watermark centrado en la parte inferior
    yPos = mapHeight + infoHeight - 40f
    val watermarkPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(180, 180, 180)
        textSize = 32f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("ZipStats", width / 2f, yPos, watermarkPaint)
}

/**
 * Formatea la duración en formato corto
 */
private fun formatDurationShort(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> String.format("%d:%02d", hours, minutes % 60)
        else -> String.format("%d min", minutes)
    }
}

/**
 * Comparte un Bitmap usando el sistema de compartir de Android
 */
private fun shareBitmap(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    route: Route
) {
    try {
        // Crear archivo temporal
        val cacheDir = context.cacheDir
        val shareDir = java.io.File(cacheDir, "shared_routes")
        if (!shareDir.exists()) {
            shareDir.mkdirs()
        }
        
        val imageFile = java.io.File(shareDir, "route_${route.id}.png")
        
        // Guardar bitmap como PNG
        java.io.FileOutputStream(imageFile).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        
        // Crear URI con FileProvider
        val imageUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )
        
        // Crear mensaje para compartir
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        val date = dateFormat.format(java.util.Date(route.startTime))
        val shareMessage = """
            🛴 Mi ruta en ${route.scooterName}
            
            📅 Fecha: $date
            📍 Distancia: ${String.format("%.1f", route.totalDistance)} km
            ⏱️ Duración: ${route.durationFormatted}
            ⚡ Velocidad media: ${String.format("%.1f", route.averageSpeed)} km/h
            🚀 Velocidad máxima: ${String.format("%.1f", route.maxSpeed)} km/h
            
            #ZipStats
        """.trimIndent()
        
        // Crear intent para compartir
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, imageUri)
            putExtra(android.content.Intent.EXTRA_TEXT, shareMessage)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(
                context.contentResolver,
                "shared_route",
                imageUri
            )
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Conceder permisos de lectura a todas las apps objetivo
        val resInfoList = context.packageManager.queryIntentActivities(intent, 0)
        if (resInfoList.isEmpty()) {
            android.widget.Toast.makeText(context, "No hay apps para compartir la imagen", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            context.grantUriPermission(
                packageName,
                imageUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        // Abrir chooser
        val chooser = android.content.Intent.createChooser(intent, "Compartir ruta").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error al compartir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}


/**
 * Dibuja un marco negro con esquinas redondeadas alrededor del mapa
 */
private fun drawMapFrame(
    canvas: android.graphics.Canvas, 
    width: Int, 
    mapHeight: Int, 
    offsetX: Int, 
    offsetY: Int, 
    scaledWidth: Int, 
    scaledHeight: Int
) {
    val framePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 8f
        style = android.graphics.Paint.Style.STROKE
        isAntiAlias = true
    }
    
    val cornerRadius = 32f // Radio de las esquinas redondeadas
    val frameRect = android.graphics.RectF(
        offsetX.toFloat() - 4f,
        offsetY.toFloat() - 4f,
        (offsetX + scaledWidth).toFloat() + 4f,
        (offsetY + scaledHeight).toFloat() + 4f
    )
    
    canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, framePaint)
}



