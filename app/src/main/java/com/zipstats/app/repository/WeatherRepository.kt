package com.zipstats.app.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import com.zipstats.app.BuildConfig
import com.zipstats.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Datos del clima (Refactorizado para Google Weather API, pero mantiene la estructura)
 */
data class WeatherData(
    val temperature: Double,      // Temperatura en °C
    val feelsLike: Double,         // Sensación térmica general
    val windChill: Double?,        // Wind Chill (solo relevante <15°C) - viene directamente de Google API
    val heatIndex: Double?,        // Índice de calor (Heat Index - solo relevante >26°C)
    val description: String,       // Descripción del clima
    val icon: String,              // Código (ahora será la condición de Google, ej: "RAIN")
    val humidity: Int,             // Humedad %
    val windSpeed: Double,         // Velocidad del viento m/s
    val weatherEmoji: String,      // Emoji representativo
    val weatherCode: Int,           // Código mapeado para compatibilidad (WMO)
    val isDay: Boolean,
    val uvIndex: Double?,
    val windDirection: Int?,
    val windGusts: Double?,
    val rainProbability: Int?,
    val precipitation: Double,     // mm reales
    val rain: Double?,             // mm
    val showers: Double?,          // mm
    val dewPoint: Double?,         // Punto de rocío en °C (para detectar condensación)
    val visibility: Double?,       // Visibilidad en metros (para detectar niebla/talaia en Barcelona)
    // Estados de advertencia (guardados junto con el clima para persistencia)
    val shouldShowRainWarning: Boolean = false,
    val isActiveRainWarning: Boolean = false,
    val shouldShowExtremeWarning: Boolean = false
)

@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Constructor interno para uso sin Hilt (llamado desde la función factory)
    private constructor(context: android.content.Context, @Suppress("UNUSED_PARAMETER") dummy: Boolean) : 
        this(context.applicationContext as Context)

    companion object {
        private const val TAG = "WeatherRepository"
        // Cambio a Google Weather API
        private const val BASE_URL = "https://weather.googleapis.com/v1"

        /**
         * Crea una instancia de WeatherRepository sin Hilt (útil para Composables)
         */
        fun create(context: android.content.Context): WeatherRepository {
            return WeatherRepository(context, true)
        }

        /**
         * Mapea condiciones de Google Weather API a códigos WMO antiguos
         * SOLO para compatibilidad con código legacy existente.
         * 
         * Confiamos 100% en Google: no modificamos, filtramos ni interpretamos
         * las condiciones que devuelve la API. Este mapeo es puramente traducir
         * el formato de Google al formato antiguo para mantener compatibilidad.
         */
        private fun mapConditionToOldCode(condition: String): Int {
            return when (condition.uppercase()) {
                "TYPE_UNSPECIFIED" -> 0
                "CLEAR", "SUNNY" -> 0
                "MOSTLY_CLEAR" -> 1
                "PARTLY_CLOUDY" -> 2
                "MOSTLY_CLOUDY", "CLOUDY" -> 3
                "FOG", "HAZE" -> 45
                "WINDY" -> 4 // Viento fuerte (código personalizado para usar drawable windy)
                "WIND_AND_RAIN" -> 63 // Lluvia con viento
                // Lluvias intermitentes (showers)
                "LIGHT_RAIN_SHOWERS", "CHANCE_OF_SHOWERS", "SCATTERED_SHOWERS" -> 80 // Chubascos leves - siempre usar icono de lluvia
                "RAIN_SHOWERS" -> 81 // Chubascos moderados
                "HEAVY_RAIN_SHOWERS" -> 82 // Chubascos violentos
                // Lluvias continuas
                "LIGHT_TO_MODERATE_RAIN", "LIGHT_RAIN" -> 61 // Lluvia ligera - siempre usar icono de lluvia
                "MODERATE_TO_HEAVY_RAIN", "RAIN" -> 63 // Lluvia moderada
                "HEAVY_RAIN" -> 65 // Lluvia fuerte
                "RAIN_PERIODICALLY_HEAVY" -> 65 // Lluvia fuerte intermitente
                // Nieve intermitente (showers)
                "LIGHT_SNOW_SHOWERS", "CHANCE_OF_SNOW_SHOWERS", "SCATTERED_SNOW_SHOWERS" -> 85 // Chubascos de nieve
                "SNOW_SHOWERS" -> 85
                "HEAVY_SNOW_SHOWERS" -> 86 // Chubascos de nieve fuertes
                // Nieve continua
                "LIGHT_TO_MODERATE_SNOW", "LIGHT_SNOW" -> 71 // Nevada ligera
                "MODERATE_TO_HEAVY_SNOW", "SNOW" -> 73 // Nevada moderada
                "HEAVY_SNOW" -> 75 // Nevada fuerte
                "SNOWSTORM" -> 75 // Tormenta de nieve - usar código de nieve fuerte
                "SNOW_PERIODICALLY_HEAVY" -> 75 // Nevada fuerte intermitente
                "HEAVY_SNOW_STORM" -> 86 // Tormenta de nieve intensa - usar código de chubascos de nieve fuertes
                "BLOWING_SNOW" -> 75 // Nieve con viento
                "RAIN_AND_SNOW" -> 66 // Mezcla de lluvia y nieve (lluvia helada)
                // Granizo
                "HAIL" -> 96 // Granizo (similar a tormenta con granizo)
                "HAIL_SHOWERS" -> 96
                // Tormentas eléctricas
                "THUNDERSTORM" -> 95
                "THUNDERSHOWER" -> 95
                "LIGHT_THUNDERSTORM_RAIN" -> 95
                "SCATTERED_THUNDERSTORMS" -> 95
                "HEAVY_THUNDERSTORM" -> 96
                // Condiciones legacy (compatibilidad)
                "DRIZZLE" -> 51 // Llovizna - siempre usar icono de lluvia, aunque sea ligera
                "THUNDERSTORM_WITH_HAIL" -> 96
                "ICE", "FREEZING_RAIN" -> 66
                else -> 0 // Default a despejado
            }
        }

        /**
         * Obtiene emoji desde condición de Google Weather API
         */
        fun getEmojiForCondition(condition: String, isDay: Boolean): String {
            val isDayTime = isDay
            return when (condition.uppercase()) {
                "TYPE_UNSPECIFIED" -> "🌡️"
                "CLEAR", "SUNNY" -> if (isDayTime) "☀️" else "🌙"
                "MOSTLY_CLEAR" -> if (isDayTime) "🌤️" else "☁️🌙"
                "PARTLY_CLOUDY" -> if (isDayTime) "🌤️" else "☁️🌙"
                "MOSTLY_CLOUDY", "CLOUDY" -> "☁️"
                "FOG", "HAZE" -> "🌫️"
                "WINDY" -> "💨"
                "WIND_AND_RAIN" -> "🌧️💨"
                // Lluvias intermitentes
                "LIGHT_RAIN_SHOWERS", "CHANCE_OF_SHOWERS", "SCATTERED_SHOWERS" -> if (isDayTime) "🌦️" else "🌧️"
                "RAIN_SHOWERS" -> "🌧️"
                "HEAVY_RAIN_SHOWERS" -> "🌧️"
                // Lluvias continuas
                "LIGHT_TO_MODERATE_RAIN", "LIGHT_RAIN" -> if (isDayTime) "🌦️" else "🌧️"
                "MODERATE_TO_HEAVY_RAIN", "RAIN" -> "🌧️"
                "HEAVY_RAIN" -> "🌧️"
                "RAIN_PERIODICALLY_HEAVY" -> "🌧️"
                // Nieve intermitente
                "LIGHT_SNOW_SHOWERS", "CHANCE_OF_SNOW_SHOWERS", "SCATTERED_SNOW_SHOWERS" -> "❄️"
                "SNOW_SHOWERS" -> "❄️"
                "HEAVY_SNOW_SHOWERS" -> "❄️"
                // Nieve continua
                "LIGHT_TO_MODERATE_SNOW", "LIGHT_SNOW" -> "❄️"
                "MODERATE_TO_HEAVY_SNOW", "SNOW" -> "❄️"
                "HEAVY_SNOW" -> "❄️"
                "SNOWSTORM" -> "⛈️❄️"
                "SNOW_PERIODICALLY_HEAVY" -> "❄️"
                "HEAVY_SNOW_STORM" -> "⛈️❄️"
                "BLOWING_SNOW" -> "❄️💨"
                "RAIN_AND_SNOW" -> "🌨️"
                // Granizo
                "HAIL" -> "🧊"
                "HAIL_SHOWERS" -> "🧊"
                // Tormentas eléctricas
                "THUNDERSTORM" -> "⚡"
                "THUNDERSHOWER" -> "⚡"
                "LIGHT_THUNDERSTORM_RAIN" -> "⚡"
                "SCATTERED_THUNDERSTORMS" -> "⚡"
                "HEAVY_THUNDERSTORM" -> "⛈️"
                // Condiciones legacy (compatibilidad)
                "DRIZZLE" -> if (isDayTime) "🌦️" else "🌧️"
                "THUNDERSTORM_WITH_HAIL" -> "⛈️"
                "ICE", "FREEZING_RAIN" -> "🥶"
                else -> "🌡️"
            }
        }

        /**
         * Obtiene descripción en español desde condición de Google Weather API
         * Google ya devuelve descripciones en español si usas languageCode=es,
         * pero mantenemos esta función como fallback
         */
        fun getDescriptionForCondition(condition: String): String {
            return when (condition.uppercase()) {
                "TYPE_UNSPECIFIED" -> "Condición no especificada"
                "CLEAR" -> "Despejado"
                "SUNNY" -> "Soleado"
                "MOSTLY_CLEAR" -> "Mayormente despejado"
                "PARTLY_CLOUDY" -> "Parcialmente nublado"
                "MOSTLY_CLOUDY" -> "Mayormente nublado"
                "CLOUDY" -> "Nublado"
                "FOG" -> "Niebla"
                "HAZE" -> "Calima"
                "WINDY" -> "Viento fuerte"
                "WIND_AND_RAIN" -> "Viento fuerte con precipitaciones"
                // Lluvias intermitentes
                "LIGHT_RAIN_SHOWERS" -> "Lluvia ligera intermitente"
                "CHANCE_OF_SHOWERS" -> "Probabilidad de lluvias intermitentes"
                "SCATTERED_SHOWERS" -> "Lluvias intermitentes"
                "RAIN_SHOWERS" -> "Chubascos"
                "HEAVY_RAIN_SHOWERS" -> "Chubascos intensos"
                // Lluvias continuas
                "LIGHT_TO_MODERATE_RAIN" -> "Lluvia de leve a moderada"
                "LIGHT_RAIN" -> "Lluvia ligera"
                "MODERATE_TO_HEAVY_RAIN" -> "Lluvia de moderada a intensa"
                "RAIN" -> "Lluvia"
                "HEAVY_RAIN" -> "Lluvia intensa"
                "RAIN_PERIODICALLY_HEAVY" -> "Lluvias, por momentos intensas"
                // Nieve intermitente
                "LIGHT_SNOW_SHOWERS" -> "Nevadas leves intermitentes"
                "CHANCE_OF_SNOW_SHOWERS" -> "Probabilidad de nevadas"
                "SCATTERED_SNOW_SHOWERS" -> "Nevadas intermitentes"
                "SNOW_SHOWERS" -> "Chubascos de nieve"
                "HEAVY_SNOW_SHOWERS" -> "Chubascos de nieve intensos"
                // Nieve continua
                "LIGHT_TO_MODERATE_SNOW" -> "Nevadas leves a moderadas"
                "LIGHT_SNOW" -> "Nevadas ligeras"
                "MODERATE_TO_HEAVY_SNOW" -> "Nevadas moderadas a intensas"
                "SNOW" -> "Nieve"
                "HEAVY_SNOW" -> "Nevadas intensas"
                "SNOWSTORM" -> "Nieve con posibles truenos y relámpagos"
                "SNOW_PERIODICALLY_HEAVY" -> "Nevadas, por momentos intensas"
                "HEAVY_SNOW_STORM" -> "Nevadas intensas con posibles truenos y relámpagos"
                "BLOWING_SNOW" -> "Nieve con viento intenso"
                "RAIN_AND_SNOW" -> "Mezcla de lluvia y nieve"
                // Granizo
                "HAIL" -> "Granizo"
                "HAIL_SHOWERS" -> "Granizo intermitente"
                // Tormentas eléctricas
                "THUNDERSTORM" -> "Tormenta eléctrica"
                "THUNDERSHOWER" -> "Lluvia con truenos y relámpagos"
                "LIGHT_THUNDERSTORM_RAIN" -> "Tormentas eléctricas con lluvia de poca intensidad"
                "SCATTERED_THUNDERSTORMS" -> "Tormentas eléctricas intermitentes"
                "HEAVY_THUNDERSTORM" -> "Tormenta eléctrica intensa"
                // Condiciones legacy (compatibilidad)
                "DRIZZLE" -> "Llovizna"
                "THUNDERSTORM_WITH_HAIL" -> "Tormenta y granizo"
                "ICE", "FREEZING_RAIN" -> "Lluvia helada"
                else -> "Desconocido"
            }
        }

        /**
         * Función de compatibilidad: Obtiene emoji desde código WMO (para código legacy)
         * Mantenemos esta función para compatibilidad con código que aún use códigos numéricos
         */
        fun getEmojiForWeather(weatherCode: Int, isDay: Int): String {
            val isDayTime = (isDay == 1)

            return when (weatherCode) {
                0 -> if (isDayTime) "☀️" else "🌙"
                1, 2 -> if (isDayTime) "🌤️" else "☁️🌙"
                3 -> "☁️"
                45, 48 -> "🌫️"
                51, 53, 61, 80 -> if (isDayTime) "🌦️" else "🌧️"
                55, 63, 65, 81, 82 -> "🌧️"
                56, 57, 66, 67 -> "🥶"
                71, 73, 75, 77, 85, 86 -> "❄️"
                95 -> "⚡"
                96, 99 -> "⛈️"
                else -> "🤷"
            }
        }

        /**
         * Función de compatibilidad: Obtiene descripción desde código WMO (para código legacy)
         */
        fun getDescriptionForWeather(weatherCode: Int, isDay: Int): String {
            return when (weatherCode) {
                0 -> "Despejado"
                1 -> "Mayormente despejado"
                2 -> "Parcialmente nublado"
                3 -> "Nublado"
                45 -> "Niebla"
                48 -> "Niebla con escarcha"
                51 -> "Llovizna ligera"
                53 -> "Llovizna moderada"
                55 -> "Llovizna densa"
                56, 57 -> "Llovizna helada"
                61 -> "Lluvia ligera"
                63 -> "Lluvia moderada"
                65 -> "Lluvia fuerte"
                66, 67 -> "Lluvia helada"
                71 -> "Nevada ligera"
                73 -> "Nevada moderada"
                75 -> "Nevada fuerte"
                77 -> "Granos de nieve"
                80 -> "Chubascos leves"
                81 -> "Chubascos moderados"
                82 -> "Chubascos violentos"
                85 -> "Chubascos de nieve"
                86 -> "Chubascos de nieve fuertes"
                95 -> "Tormenta eléctrica"
                96 -> "Tormenta y granizo"
                99 -> "Tormenta y granizo fuerte"
                else -> "Desconocido"
            }
        }

        /**
         * Función de compatibilidad: Obtiene icono drawable desde código WMO (para código legacy)
         */
        @DrawableRes
        fun getIconResIdForWeather(weatherCode: Int, isDay: Int): Int {
            val isDayTime = (isDay == 1)

            return when (weatherCode) {
                0 -> if (isDayTime) R.drawable.wb_sunny else R.drawable.achievement_explorador_1
                1, 2 -> if (isDayTime) R.drawable.partly_cloudy_day else R.drawable.partly_cloudy_night
                3 -> R.drawable.cloud
                4 -> R.drawable.windy // Viento fuerte
                45, 48 -> R.drawable.foggy
                51, 53 -> R.drawable.rainy_light // Llovizna (ligera y moderada) - usar icono de lluvia ligera
                55 -> R.drawable.rainy_light // Llovizna densa - sigue siendo llovizna, no lluvia fuerte
                61 -> R.drawable.rainy // Lluvia ligera
                80 -> R.drawable.rainy_light // Chubascos leves (LIGHT_RAIN_SHOWERS, CHANCE_OF_SHOWERS, SCATTERED_SHOWERS)
                63, 65, 81, 82 -> R.drawable.rainy_heavy // Lluvias moderadas a fuertes y chubascos intensos
                56, 57 -> R.drawable.rainy_snow // Llovizna helada
                66 -> R.drawable.weather_mix // Mezcla de lluvia y nieve
                67 -> R.drawable.rainy_snow // Lluvia helada - similar a llovizna helada, lluvia que se congela
                71, 73, 75, 77 -> R.drawable.snowing // Nieve normal y con viento
                85 -> R.drawable.snowing // Chubascos de nieve (LIGHT_SNOW_SHOWERS, CHANCE_OF_SNOW_SHOWERS, SCATTERED_SNOW_SHOWERS, SNOW_SHOWERS) - usar icono de nieve consistente
                86 -> R.drawable.snowing_heavy // Chubascos de nieve intensos (HEAVY_SNOW_SHOWERS)
                95 -> R.drawable.thunderstorm // Tormentas eléctricas
                96, 99 -> R.drawable.hail
                else -> R.drawable.help_outline
            }
        }
    }

    /**
     * Obtiene el SHA-1 de la firma con la que se instaló la app
     * (ya sea la de debug de cualquier PC o la de producción)
     */
    private fun getSigningCertificateSHA1(): String? {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners // Usamos ?. para evitar el error de nulabilidad
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            signatures?.let {
                for (signature in it) {
                    val md = MessageDigest.getInstance("SHA-1")
                    val digest = md.digest(signature.toByteArray())
                    return digest.joinToString(":") { String.format("%02X", it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo SHA-1 dinámico", e)
        }
        return null
    }

    /**
     * Obtiene el clima actual de Google Weather API
     */
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): Result<WeatherData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== Iniciando llamada a Google Weather API ===")

                val apiKey = BuildConfig.GOOGLE_WEATHER_API_KEY
                if (apiKey == "YOUR_GOOGLE_WEATHER_API_KEY" || apiKey.isEmpty()) {
                    Log.e(TAG, "❌ Google Weather API Key no configurada")
                    return@withContext Result.failure(Exception("Google Weather API Key no configurada. Configúrala en local.properties"))
                }

                // Endpoint de Google Weather API (formato correcto según documentación)
                val urlString = "$BASE_URL/currentConditions:lookup?key=$apiKey&location.latitude=$latitude&location.longitude=$longitude&languageCode=es"

                Log.d(TAG, "URL: ${urlString.replace(apiKey, "***")}")

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    
                    // Obtener SHA-1 dinámicamente de la firma de la app
                    val sha1 = getSigningCertificateSHA1()
                    Log.e("PRUEBA_SHA1", "El SHA1 que detecta la app es: $sha1")
                    val sha1Value = sha1 ?: ""
                    connection.setRequestProperty("X-Android-Package", context.packageName)
                    connection.setRequestProperty("X-Android-Cert", sha1Value)
                    
                    Log.d(TAG, "Cabeceras HTTP: Package=${context.packageName}, SHA-1=${sha1Value.take(20)}...")

                    Log.d(TAG, "Realizando petición HTTP...")
                    val responseCode = connection.responseCode
                    Log.d(TAG, "Código de respuesta: $responseCode")

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        Log.d(TAG, "Respuesta completa recibida: $response")

                        val weatherData = parseGoogleResponse(response)
                        Log.d(TAG, "✅ Clima parseado correctamente (Google): ${weatherData.temperature}°C, ${weatherData.description}")

                        Result.success(weatherData)
                    } else {
                        val errorMessage = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Sin mensaje de error"
                        Log.e(TAG, "❌ Error HTTP $responseCode")
                        Log.e(TAG, "Mensaje de error: $errorMessage")
                        Result.failure(Exception("Error al obtener clima: HTTP $responseCode - $errorMessage"))
                    }
                } finally {
                    connection.disconnect()
                    Log.d(TAG, "Conexión cerrada")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción al obtener clima: ${e.javaClass.simpleName} - ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Obtiene precipitación acumulada reciente basada en histórico por horas de Google Weather.
     *
     * Devuelve la suma de `precipitation.qpf.quantity` de las últimas `hours` horas.
     * (Según documentación de Google, `qpf` es acumulación por hora; aquí la agregamos.)
     */
    suspend fun getRecentPrecipitationHours(
        latitude: Double,
        longitude: Double,
        hours: Int = 3
    ): Result<Double> {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.GOOGLE_WEATHER_API_KEY
                if (apiKey == "YOUR_GOOGLE_WEATHER_API_KEY" || apiKey.isEmpty()) {
                    return@withContext Result.failure(Exception("Google Weather API Key no configurada. Configúrala en local.properties"))
                }

                val safeHours = hours.coerceIn(1, 24)
                val urlString =
                    "$BASE_URL/history/hours:lookup?key=$apiKey&location.latitude=$latitude&location.longitude=$longitude&hours=$safeHours&languageCode=es&unitsSystem=METRIC"

                Log.d(TAG, "URL history.hours: ${urlString.replace(apiKey, "***")}")

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    val sha1 = getSigningCertificateSHA1()
                    val sha1Value = sha1 ?: ""
                    connection.setRequestProperty("X-Android-Package", context.packageName)
                    connection.setRequestProperty("X-Android-Cert", sha1Value)

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val total = parseRecentPrecipitationHoursResponse(response)
                        Result.success(total)
                    } else {
                        val errorMessage =
                            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Sin mensaje de error"
                        Result.failure(Exception("Error al obtener histórico por horas: HTTP $responseCode - $errorMessage"))
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Obtiene el clima histórico (sigue usando el actual como aproximación)
     */
    suspend fun getHistoricalWeather(
        latitude: Double,
        longitude: Double,
        timestamp: Long
    ): Result<WeatherData> {
        Log.d(TAG, "Obteniendo clima histórico (usando actual como aproximación)")
        return getCurrentWeather(latitude, longitude)
    }

    /**
     * Parsea la respuesta JSON de Google Weather API
     * Estructura según documentación oficial: https://developers.google.com/maps/documentation/weather/current-conditions
     */
    private fun parseGoogleResponse(jsonString: String): WeatherData {
        val json = JSONObject(jsonString)
        Log.d(TAG, "Estructura JSON recibida: ${json.keys().asSequence().joinToString()}")
        
        // La respuesta está en el nivel raíz, no hay objeto anidado
        // weatherCondition.type contiene la condición (ej: "CLEAR", "RAIN")
        val weatherConditionObj = json.optJSONObject("weatherCondition")
        val condition = weatherConditionObj?.optString("type", "CLEAR") ?: "CLEAR"
        
        // weatherCondition.description.text contiene la descripción
        val descriptionObj = weatherConditionObj?.optJSONObject("description")
        val descriptionFromApi = descriptionObj?.optString("text", "")
        val description = if (!descriptionFromApi.isNullOrBlank()) {
            descriptionFromApi
        } else {
            getDescriptionForCondition(condition)
        }
        
        // temperature.degrees contiene la temperatura
        val temperatureObj = json.optJSONObject("temperature")
        val temperature = temperatureObj?.optDouble("degrees", 20.0) ?: 20.0
        
        // feelsLikeTemperature.degrees contiene la sensación térmica general de Google
        val feelsLikeObj = json.optJSONObject("feelsLikeTemperature")
        val feelsLikeFromApi = feelsLikeObj?.optDouble("degrees", temperature) ?: temperature
        
        // heatIndex.degrees contiene el índice de calor (Heat Index - solo relevante >26°C)
        val heatIndexObj = json.optJSONObject("heatIndex")
        val heatIndex = heatIndexObj?.optDouble("degrees")?.takeIf { !it.isNaN() && !it.isInfinite() }
        
        // relativeHumidity contiene la humedad (número entero)
        val humidity = json.optInt("relativeHumidity", 50)
        
        // isDaytime indica si es de día
        val isDay = json.optBoolean("isDaytime", true)
        
        // uvIndex contiene el índice UV
        val uvIndex = json.optInt("uvIndex", 0).takeIf { it > 0 }?.toDouble()
        
        // precipitation.qpf.quantity contiene la cantidad de precipitación
        val precipitationObj = json.optJSONObject("precipitation")
        val qpfObj = precipitationObj?.optJSONObject("qpf")
        val precipitation = qpfObj?.optDouble("quantity", 0.0) ?: 0.0
        
        // precipitation.probability.percent contiene la probabilidad de lluvia
        val probabilityObj = precipitationObj?.optJSONObject("probability")
        val rainProbability = probabilityObj?.optInt("percent", 0)?.takeIf { it > 0 }
        
        // wind.speed.value contiene la velocidad del viento (en km/h por defecto)
        val windObj = json.optJSONObject("wind")
        val windSpeedObj = windObj?.optJSONObject("speed")
        val windSpeedValue = windSpeedObj?.optDouble("value", 0.0) ?: 0.0
        val windSpeedUnit = windSpeedObj?.optString("unit", "KILOMETERS_PER_HOUR")
        // Convertir a m/s (si está en km/h, dividir por 3.6; si está en m/s, usar directamente)
        val windSpeed = when (windSpeedUnit) {
            "KILOMETERS_PER_HOUR" -> windSpeedValue / 3.6
            "METERS_PER_SECOND" -> windSpeedValue
            else -> windSpeedValue / 3.6 // Default asumir km/h
        }
        
        // wind.gust.value contiene las ráfagas
        val windGustObj = windObj?.optJSONObject("gust")
        val windGustValue = windGustObj?.optDouble("value", 0.0) ?: 0.0
        val windGustUnit = windGustObj?.optString("unit", "KILOMETERS_PER_HOUR")
        val windGustsKph = when (windGustUnit) {
            "KILOMETERS_PER_HOUR" -> windGustValue
            "METERS_PER_SECOND" -> windGustValue * 3.6
            else -> windGustValue
        }
        val windGusts = if (windGustsKph > 0) windGustsKph / 3.6 else null
        
        // wind.direction.degrees contiene la dirección del viento
        val windDirectionObj = windObj?.optJSONObject("direction")
        val windDirection = windDirectionObj?.optInt("degrees", 0)?.takeIf { it > 0 }
        
        // windChill.degrees contiene el Wind Chill directamente de Google API
        // Solo está disponible cuando la temperatura es baja (<15°C)
        val windChillObj = json.optJSONObject("windChill")
        val windChill = windChillObj?.optDouble("degrees")?.takeIf { !it.isNaN() && !it.isInfinite() }
        
        // dewPoint.degrees contiene el punto de rocío (para detectar condensación)
        val dewPointObj = json.optJSONObject("dewPoint")
        val dewPointFromApi = dewPointObj?.optDouble("degrees")?.takeIf { !it.isNaN() }
        val dewPoint = dewPointFromApi 
            ?: (temperature - 2.0).takeIf { humidity > 85 } // Estimar si hay alta humedad
        
        // visibility.distance contiene la visibilidad (crítico para Barcelona - niebla/talaia)
        // Según la documentación oficial de Google Weather API:
        // - Campo: "distance" (no "value")
        // - Unidad: "KILOMETERS" (sistema métrico) o "MILES" (sistema imperial)
        // - Siempre convertir a METROS para consistencia interna
        val visibilityObj = json.optJSONObject("visibility")
        val visibility: Double? = if (visibilityObj != null) {
            // La API usa "distance" no "value"
            val visibilityDistance = visibilityObj.optDouble("distance").takeIf { !it.isNaN() && it > 0 }
            val visibilityUnit = visibilityObj.optString("unit", "KILOMETERS")
            
            if (visibilityDistance == null) {
                Log.d(TAG, "🌫️ Visibilidad no disponible (distance null)")
                null
            } else {
                // Convertir siempre a METROS para consistencia interna
                val resultInMeters = when (visibilityUnit) {
                    "KILOMETERS", "KILOMETER" -> {
                        // Visibilidad en kilómetros, convertir a metros
                        val meters = visibilityDistance * 1000.0
                        Log.d(TAG, "🌫️ Visibilidad en KILOMETERS: ${visibilityDistance}km -> ${meters}m")
                        meters
                    }
                    "MILES", "MILE" -> {
                        // Visibilidad en millas (sistema imperial), convertir a metros
                        // 1 milla = 1609.344 metros
                        val meters = visibilityDistance * 1609.344
                        Log.d(TAG, "🌫️ Visibilidad en MILES: ${visibilityDistance}mi -> ${meters}m")
                        meters
                    }
                    else -> {
                        // Unidad desconocida, asumir kilómetros por defecto
                        val meters = visibilityDistance * 1000.0
                        Log.w(TAG, "🌫️ ⚠️ Unidad de visibilidad desconocida: '$visibilityUnit'. Asumiendo KILOMETERS: ${visibilityDistance}km -> ${meters}m")
                        meters
                    }
                }
                
                // Validación adicional: visibilidad máxima razonable es ~50km = 50000m
                // Si supera 100000m (100km), algo está mal
                if (resultInMeters > 100000) {
                    Log.w(TAG, "🌫️ ⚠️ Visibilidad anormalmente alta detectada: ${resultInMeters}m. Capando a 50000m")
                    50000.0 // Capar a 50km máximo
                } else {
                    resultInMeters
                }
            }
        } else {
            // No hay objeto visibility en la respuesta
            Log.d(TAG, "🌫️ Visibilidad no disponible en la respuesta de Google")
            null
        }
        
        // Mapear condición de Google a código WMO antiguo SOLO para compatibilidad legacy
        // Confiamos 100% en Google, no modificamos sus condiciones
        val weatherCode = mapConditionToOldCode(condition)
        
        // Obtener emoji desde la condición de Google
        val emoji = getEmojiForCondition(condition, isDay)
        
        // Para compatibilidad, usamos la misma precipitación para rain y showers
        val rain = if (precipitation > 0) precipitation else null
        val showers = null // Google no diferencia entre rain y showers

        return WeatherData(
            temperature = temperature,
            feelsLike = feelsLikeFromApi,
            windChill = windChill,
            heatIndex = heatIndex,
            description = description,
            icon = condition, // Guardamos la condición de Google (ej: "RAIN")
            humidity = humidity,
            windSpeed = windSpeed,
            weatherEmoji = emoji,
            weatherCode = weatherCode, // Código mapeado para compatibilidad
            isDay = isDay,
            uvIndex = uvIndex,
            windDirection = windDirection,
            windGusts = windGusts,
            rainProbability = rainProbability,
            precipitation = precipitation,
            rain = rain,
            showers = showers,
            dewPoint = dewPoint,
            visibility = visibility
        )
    }

    /**
     * Parse robusto para `history.hours:lookup`.
     * Suma `precipitation.qpf.quantity` por hora y lo devuelve en milímetros.
     */
    private fun parseRecentPrecipitationHoursResponse(jsonString: String): Double {
        val json = JSONObject(jsonString)

        val hoursArray =
            json.optJSONArray("historyHours")
                ?: json.optJSONArray("hours")
                ?: json.optJSONArray("hourlyHistory")
                ?: json.optJSONArray("hourly")
                ?: return 0.0

        var totalMm = 0.0
        for (i in 0 until hoursArray.length()) {
            val hourObj = hoursArray.optJSONObject(i) ?: continue
            val precipObj = hourObj.optJSONObject("precipitation") ?: continue
            val qpfObj = precipObj.optJSONObject("qpf") ?: continue

            val quantity = qpfObj.optDouble("quantity", 0.0).takeIf { !it.isNaN() && it.isFinite() } ?: 0.0
            val unit = qpfObj.optString("unit", "MILLIMETERS").uppercase()

            val quantityMm = when (unit) {
                "INCHES", "INCH" -> quantity * 25.4
                else -> quantity // MILLIMETERS (o desconocido → asumimos mm en unitsSystem=METRIC)
            }

            totalMm += quantityMm
        }

        return totalMm
    }
}
