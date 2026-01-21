package com.zipstats.app.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.FileProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import com.zipstats.app.R
import com.zipstats.app.model.Route
import com.zipstats.app.model.Vehicle
import com.zipstats.app.model.VehicleType
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.ui.components.RouteSummaryCardFromRoute
import com.zipstats.app.ui.theme.ZipStatsTypography
import com.zipstats.app.ui.components.MapSnapshotTrigger
import com.zipstats.app.utils.CityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

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
        primaryColorArgb: Int? = null,
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
                                vehicleRepository = vehicleRepository,
                                primaryColorArgb = primaryColorArgb
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
        vehicleRepository: VehicleRepository?,
        primaryColorArgb: Int?
    ): Bitmap {
        val width = mapBitmap.width
        val height = mapBitmap.height
        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)

        // 1. Dibujar mapa de fondo
        canvas.drawBitmap(mapBitmap, 0f, 0f, null)

        // 2. Renderizar la MISMA tarjeta Compose que se muestra en pantalla (RouteSummaryCard)
        val vehicle: Vehicle? = vehicleRepository?.let { repo ->
            try {
                repo.getUserVehicles().find { it.id == route.scooterId }
            } catch (_: Exception) {
                null
            }
        }

        val tituloRuta = CityUtils.getRouteTitleText(route, vehicle?.vehicleType)
        val fechaFormateada = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val date = java.time.LocalDate.ofEpochDay(route.startTime / (1000 * 60 * 60 * 24))
            val formatter = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", java.util.Locale("es", "ES"))
            date.format(formatter)
        } else {
            val sdf = java.text.SimpleDateFormat("d 'de' MMMM, yyyy", java.util.Locale("es", "ES"))
            sdf.format(java.util.Date(route.startTime))
        }
        val subtitle = "${route.scooterName} • $fechaFormateada"
        val duration = formatDurationWithUnits(route.totalDuration)

        // ComposeView debe medirse/dibujarse en Main.
        val cardBitmap = withContext(Dispatchers.Main) {
            renderRouteSummaryCardToBitmap(
                context = context,
                widthPx = width,
                route = route,
                title = tituloRuta,
                subtitle = subtitle,
                duration = duration,
                primaryColorArgb = primaryColorArgb
            )
        }

        val marginPx = dpToPx(context, 24f)
        val cardY = (height - cardBitmap.height - marginPx).coerceAtLeast(0)

        canvas.save()
        canvas.translate(0f, cardY.toFloat())
        canvas.drawBitmap(cardBitmap, 0f, 0f, null)
        canvas.restore()

        return finalBitmap
    }

    private fun renderRouteSummaryCardToBitmap(
        context: Context,
        widthPx: Int,
        route: Route,
        title: String,
        subtitle: String,
        duration: String,
        primaryColorArgb: Int?
    ): Bitmap {
        val activity = context.findActivity()
        val root = activity?.window?.decorView as? ViewGroup

        val composeView = ComposeView(context).apply {
            // Fondo transparente: el mapa se verá debajo y la tarjeta tendrá su propio fondo.
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setContent {
                ShareCardTheme(primaryColorArgb = primaryColorArgb) {
                    RouteSummaryCardFromRoute(
                        route = route,
                        title = title,
                        subtitle = subtitle,
                        duration = duration
                    )
                }
            }
        }

        // Para asegurar que ComposeView compone/dibuja correctamente, lo adjuntamos temporalmente al root.
        // Se coloca fuera de pantalla para que no “parpadee” en UI.
        val hostContainer = if (root != null) {
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(1, 1)
                translationX = -10_000f
                translationY = -10_000f
                addView(
                    composeView,
                    FrameLayout.LayoutParams(
                        widthPx,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }.also { root.addView(it) }
        } else {
            null
        }

        val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY)
        val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        composeView.measure(widthSpec, heightSpec)
        composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

        val bitmap = Bitmap.createBitmap(composeView.measuredWidth, composeView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        composeView.draw(canvas)

        // Limpieza para evitar fugas.
        hostContainer?.let { root?.removeView(it) }

        return bitmap
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    @Composable
    private fun ShareCardTheme(
        primaryColorArgb: Int?,
        content: @Composable () -> Unit
    ) {
        val primary = primaryColorArgb?.let { Color(it) } ?: darkColorScheme().primary
        MaterialTheme(
            colorScheme = darkColorScheme(primary = primary),
            typography = ZipStatsTypography,
            content = content
        )
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).roundToInt()
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
    private fun getWeatherIconResId(
        condition: String?,
        weatherCode: Int?,
        emoji: String?,
        isDay: Boolean
    ): Int {
        // Prioridad: `weatherCondition` de Google (fuente de verdad).
        if (!condition.isNullOrBlank()) {
            return com.zipstats.app.repository.WeatherRepository.getIconResIdForCondition(condition, isDay)
        }

        // Fallback: rutas antiguas sin condition (usar WMO guardado si existe).
        if (weatherCode != null) {
            return com.zipstats.app.repository.WeatherRepository.getIconResIdForWeather(weatherCode, if (isDay) 1 else 0)
        }

        // Último fallback: rutas muy antiguas con solo emoji.
        if (emoji.isNullOrBlank()) return R.drawable.help_outline

        return when (emoji) {
            "☀️", "🌙" -> com.zipstats.app.repository.WeatherRepository.getIconResIdForWeather(0, if (isDay) 1 else 0)
            "🌤️", "🌥️", "☁️🌙" -> com.zipstats.app.repository.WeatherRepository.getIconResIdForWeather(1, if (isDay) 1 else 0)
            "☁️" -> com.zipstats.app.repository.WeatherRepository.getIconResIdForWeather(3, if (isDay) 1 else 0)
            "🌫️" -> com.zipstats.app.repository.WeatherRepository.getIconResIdForWeather(45, if (isDay) 1 else 0)
            "🌦️" -> com.zipstats.app.repository.WeatherRepository.getIconResIdForWeather(61, if (isDay) 1 else 0)
            "🌧️" -> com.zipstats.app.repository.WeatherRepository.getIconResIdForWeather(65, if (isDay) 1 else 0)
            "🥶" -> com.zipstats.app.repository.WeatherRepository.getIconResIdForWeather(66, if (isDay) 1 else 0)
            "❄️" -> com.zipstats.app.repository.WeatherRepository.getIconResIdForWeather(71, if (isDay) 1 else 0)
            "⛈️", "⚡" -> com.zipstats.app.repository.WeatherRepository.getIconResIdForWeather(95, if (isDay) 1 else 0)
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

    /**
     * Formatea la temperatura asegurándose de que 0 se muestre sin signo menos
     */
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
}

