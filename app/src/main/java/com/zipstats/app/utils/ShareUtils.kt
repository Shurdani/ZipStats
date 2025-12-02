package com.zipstats.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.FileProvider
import com.zipstats.app.R
import com.zipstats.app.model.Route
import com.zipstats.app.model.Vehicle
import com.zipstats.app.model.VehicleType
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.ui.components.MapSnapshotTrigger
import com.zipstats.app.utils.CityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Utilidades para compartir rutas como imágenes
 */
object ShareUtils {

    /**
     * Función principal para compartir una ruta con snapshot del mapa
     */
    fun shareRouteImage(
        context: Context,
        route: Route,
        snapshotTrigger: MapSnapshotTrigger,
        vehicleRepository: VehicleRepository? = null,
        onComplete: () -> Unit
    ) {
        try {
            android.util.Log.d("ShareUtils", "=== INICIO COMPARTIR ===")
            Toast.makeText(context, "Generando imagen...", Toast.LENGTH_SHORT).show()

            android.util.Log.d("ShareUtils", "Capturando snapshot del mapa con API de Mapbox...")

            // Usar la API de snapshot de Mapbox - esto captura solo el mapa
            snapshotTrigger { mapBitmap ->
                if (mapBitmap == null) {
                    android.util.Log.e("ShareUtils", "Snapshot es null")
                    Toast.makeText(context, "Error al capturar el mapa", Toast.LENGTH_SHORT).show()
                    onComplete()
                    return@snapshotTrigger
                }

                android.util.Log.d("ShareUtils", "Snapshot capturado correctamente: ${mapBitmap.width}x${mapBitmap.height}")
                android.util.Log.d("ShareUtils", "Bitmap config: ${mapBitmap.config}, isRecycled: ${mapBitmap.isRecycled}")
                
                // Verificar que el bitmap tenga un tamaño razonable (no toda la pantalla)
                val displayMetrics = context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                android.util.Log.d("ShareUtils", "Tamaño de pantalla: ${screenWidth}x${screenHeight}")
                
                if (mapBitmap.width > screenWidth * 1.5 || mapBitmap.height > screenHeight * 1.5) {
                    android.util.Log.w("ShareUtils", "El bitmap parece ser más grande que la pantalla, puede estar capturando toda la ventana")
                }

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        android.util.Log.d("ShareUtils", "Creando imagen final...")
                        val finalBitmap = withContext(Dispatchers.IO) {
                            createFinalRouteImageFromFullscreenCanvas(
                                context = context,
                                route = route,
                                mapBitmap = mapBitmap,
                                vehicleRepository = vehicleRepository
                            )
                        }
                        android.util.Log.d("ShareUtils", "Imagen final creada: width=${finalBitmap.width}, height=${finalBitmap.height}")

                        android.util.Log.d("ShareUtils", "Compartiendo imagen...")
                        shareBitmap(context, finalBitmap, route)
                        android.util.Log.d("ShareUtils", "=== FIN COMPARTIR ===")

                        onComplete()
                    } catch (e: Exception) {
                        android.util.Log.e("ShareUtils", "Error al procesar imagen: ${e.message}", e)
                        Toast.makeText(context, "Error al procesar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                        onComplete()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ShareUtils", "Error al compartir: ${e.message}", e)
            Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            onComplete()
        }
    }

    private suspend fun createFinalRouteImageFromFullscreenCanvas(
        context: Context,
        route: Route,
        mapBitmap: Bitmap,
        vehicleRepository: VehicleRepository?
    ): Bitmap {
        val width = mapBitmap.width
        val height = mapBitmap.height
        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)

        // 1. Dibujar mapa de fondo
        canvas.drawBitmap(mapBitmap, 0f, 0f, null)

        // 2. Inflar y configurar la tarjeta XML
        val inflater = android.view.LayoutInflater.from(context)
        val cardView = inflater.inflate(R.layout.share_route_stats_card, null) as androidx.cardview.widget.CardView

        // Configurar la tarjeta con los datos de la ruta
        configurarTarjetaCompartir(cardView, route, context, vehicleRepository)

        // Medir y renderizar la tarjeta
        val cardWidth = width - 64 // Márgenes de 32dp a cada lado
        val measureSpec = android.view.View.MeasureSpec.makeMeasureSpec(cardWidth, android.view.View.MeasureSpec.EXACTLY)
        cardView.measure(measureSpec, android.view.View.MeasureSpec.UNSPECIFIED)

        val cardHeight = cardView.measuredHeight
        val cardX = 32
        val cardY = height - cardHeight - 32 // Anclar al borde inferior

        cardView.layout(0, 0, cardView.measuredWidth, cardHeight)

        // Dibujar la tarjeta en el canvas
        canvas.save()
        canvas.translate(cardX.toFloat(), cardY.toFloat())
        cardView.draw(canvas)
        canvas.restore()

        return finalBitmap
    }

    private suspend fun configurarTarjetaCompartir(
        cardView: androidx.cardview.widget.CardView,
        route: Route,
        context: Context,
        vehicleRepository: VehicleRepository?
    ) {
        // Configurar título de la ruta
        val routeTitle = CityUtils.getRouteTitleText(route)
        cardView.findViewById<android.widget.TextView>(R.id.routeTitle).text = routeTitle

        // Configurar métricas
        cardView.findViewById<android.widget.TextView>(R.id.distanceValue).text =
            String.format("%.1f km", route.totalDistance)
        cardView.findViewById<android.widget.TextView>(R.id.timeValue).text =
            formatDurationWithUnits(route.totalDuration)
        cardView.findViewById<android.widget.TextView>(R.id.speedValue).text =
            String.format("%.1f km/h", route.averageSpeed)

        // Configurar clima si está disponible
        if (route.weatherEmoji != null && route.weatherTemperature != null) {
            val weatherIconRes = getWeatherIconResId(route.weatherEmoji, route.weatherIsDay)
            cardView.findViewById<android.widget.ImageView>(R.id.weatherIcon).setImageResource(weatherIconRes)
            cardView.findViewById<android.widget.ImageView>(R.id.weatherIcon).setColorFilter(android.graphics.Color.WHITE)
            cardView.findViewById<android.widget.TextView>(R.id.weatherTemp).text =
                String.format("%.0f°C", route.weatherTemperature)
            cardView.findViewById<android.widget.LinearLayout>(R.id.weatherContainer).visibility = android.view.View.VISIBLE
        } else {
            cardView.findViewById<android.widget.LinearLayout>(R.id.weatherContainer).visibility = android.view.View.GONE
        }

        // Configurar icono del vehículo
        val vehicle = vehicleRepository?.let { repo ->
            try {
                repo.getUserVehicles().find { it.id == route.scooterId }
            } catch (e: Exception) {
                null
            }
        }
        val vehicleIconRes = getVehicleIconResource(vehicle?.vehicleType)
        cardView.findViewById<android.widget.ImageView>(R.id.vehicleIcon).setImageResource(vehicleIconRes)

        // Configurar información del vehículo y fecha
        val vehicleInfoText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val date = java.time.LocalDate.ofEpochDay(route.startTime / (1000 * 60 * 60 * 24))
            val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", java.util.Locale.forLanguageTag("es-ES"))
            "${route.scooterName} | ${date.format(dateFormatter)}"
        } else {
            val simpleDateFormat = java.text.SimpleDateFormat("d 'de' MMMM 'de' yyyy", java.util.Locale.forLanguageTag("es-ES"))
            "${route.scooterName} | ${simpleDateFormat.format(java.util.Date(route.startTime))}"
        }
        cardView.findViewById<android.widget.TextView>(R.id.vehicleInfo).text = vehicleInfoText

        // Eliminar el logo de ZipStats si existe
        try {
            cardView.findViewById<android.widget.TextView>(R.id.zipstatsBranding)?.setCompoundDrawables(null, null, null, null)
        } catch (e: Exception) {
            // Ignorar si no existe
        }
    }

    private fun shareBitmap(context: Context, bitmap: Bitmap, route: Route) {
        try {
            val cacheDir = context.cacheDir
            val shareDir = File(cacheDir, "shared_routes")
            if (!shareDir.exists()) shareDir.mkdirs()
            val imageFile = File(shareDir, "route_${route.id}.png")
            FileOutputStream(imageFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val imageUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Compartir ruta")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @DrawableRes
    private fun getWeatherIconResId(emoji: String?, isDay: Boolean): Int {
        if (emoji.isNullOrBlank()) return R.drawable.help_outline

        return when (emoji) {
            // ☀️ Cielo Despejado
            "☀️" -> R.drawable.wb_sunny
            "🌙" -> R.drawable.nightlight

            // ⛅ Nubes Parciales
            "🌤️", "🌥️", "☁️🌙" -> if (isDay) R.drawable.partly_cloudy_day else R.drawable.partly_cloudy_night

            // ☁️ Nublado (A veces la API manda esto de noche también)
            "☁️" -> R.drawable.cloud

            // 🌫️ Niebla
            "🌫️" -> R.drawable.foggy

            // 🌦️ Lluvia Ligera / Chubascos leves (Sol con lluvia) -> Icono Normal
            "🌦️" -> R.drawable.rainy

            // 🌧️ Lluvia Fuerte / Densa (Solo nube) -> Icono HEAVY (Nuevo)
            "🌧️" -> R.drawable.rainy_heavy

            // 🥶 Aguanieve / Hielo (Cara de frío) -> Icono SNOWY RAINY (Nuevo)
            "🥶" -> R.drawable.rainy_snow

            // ❄️ Nieve
            "❄️" -> R.drawable.snowing

            // ⛈️ Tormenta / Granizo / Rayo
            "⛈️", "⚡" -> R.drawable.thunderstorm

            // 🤷 Default
            else -> R.drawable.help_outline
        }
    }

    private fun getVehicleIconResource(vehicleType: VehicleType?): Int {
        return when (vehicleType) {
            VehicleType.PATINETE -> R.drawable.ic_electric_scooter_adaptive
            VehicleType.BICICLETA -> R.drawable.ic_ciclismo_adaptive
            VehicleType.E_BIKE -> R.drawable.ic_bicicleta_electrica_adaptive
            VehicleType.MONOCICLO -> R.drawable.ic_unicycle_adaptive
            null -> R.drawable.ic_electric_scooter_adaptive
        }
    }

    private fun formatDurationWithUnits(durationMs: Long): String {
        val minutes = durationMs / 1000 / 60
        val hours = minutes / 60
        return if (hours > 0) String.format("%d h %d min", hours, minutes % 60) else String.format("%d min", minutes)
    }
}

