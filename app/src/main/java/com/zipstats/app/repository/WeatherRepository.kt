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
    val feelsLike: Double,         // Sensación térmica
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
    val showers: Double?           // mm
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
         * para mantener compatibilidad con el código existente
         */
        private fun mapConditionToOldCode(condition: String): Int {
            return when (condition.uppercase()) {
                "CLEAR", "SUNNY" -> 0
                "MOSTLY_CLEAR" -> 1
                "PARTLY_CLOUDY" -> 2
                "CLOUDY", "MOSTLY_CLOUDY" -> 3
                "FOG", "HAZE" -> 45
                "DRIZZLE", "LIGHT_RAIN" -> 61
                "RAIN" -> 63
                "HEAVY_RAIN" -> 65
                "THUNDERSTORM" -> 95
                "THUNDERSTORM_WITH_HAIL" -> 96
                "SNOW" -> 71
                "HEAVY_SNOW" -> 75
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
                "CLEAR", "SUNNY" -> if (isDayTime) "☀️" else "🌙"
                "MOSTLY_CLEAR", "PARTLY_CLOUDY" -> if (isDayTime) "🌤️" else "☁️🌙"
                "CLOUDY", "MOSTLY_CLOUDY" -> "☁️"
                "FOG", "HAZE" -> "🌫️"
                "DRIZZLE", "LIGHT_RAIN" -> if (isDayTime) "🌦️" else "🌧️"
                "RAIN" -> "🌧️"
                "HEAVY_RAIN" -> "🌧️"
                "THUNDERSTORM" -> "⚡"
                "THUNDERSTORM_WITH_HAIL" -> "⛈️"
                "SNOW" -> "❄️"
                "HEAVY_SNOW" -> "❄️"
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
                "CLEAR" -> "Despejado"
                "SUNNY" -> "Soleado"
                "MOSTLY_CLEAR" -> "Mayormente despejado"
                "PARTLY_CLOUDY" -> "Parcialmente nublado"
                "CLOUDY" -> "Nublado"
                "MOSTLY_CLOUDY" -> "Muy nublado"
                "FOG" -> "Niebla"
                "HAZE" -> "Calima"
                "DRIZZLE" -> "Llovizna"
                "LIGHT_RAIN" -> "Lluvia ligera"
                "RAIN" -> "Lluvia"
                "HEAVY_RAIN" -> "Lluvia fuerte"
                "THUNDERSTORM" -> "Tormenta eléctrica"
                "THUNDERSTORM_WITH_HAIL" -> "Tormenta y granizo"
                "SNOW" -> "Nieve"
                "HEAVY_SNOW" -> "Nieve fuerte"
                "ICE" -> "Hielo"
                "FREEZING_RAIN" -> "Lluvia helada"
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
                0 -> if (isDayTime) R.drawable.wb_sunny else R.drawable.nightlight
                1, 2 -> if (isDayTime) R.drawable.partly_cloudy_day else R.drawable.partly_cloudy_night
                3 -> R.drawable.cloud
                45, 48 -> R.drawable.foggy
                51, 53, 61, 80 -> R.drawable.rainy
                55, 63, 65, 81, 82 -> R.drawable.rainy_heavy
                56, 57, 66, 67 -> R.drawable.rainy_snow
                71, 73, 75, 77, 85, 86 -> R.drawable.snowing
                95 -> R.drawable.thunderstorm
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
        val description = descriptionObj?.optString("text", null) 
            ?: getDescriptionForCondition(condition)
        
        // temperature.degrees contiene la temperatura
        val temperatureObj = json.optJSONObject("temperature")
        val temperature = temperatureObj?.optDouble("degrees", 20.0) ?: 20.0
        
        // feelsLikeTemperature.degrees contiene la sensación térmica
        val feelsLikeObj = json.optJSONObject("feelsLikeTemperature")
        val feelsLike = feelsLikeObj?.optDouble("degrees", temperature) ?: temperature
        
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
        
        // Mapear condición de Google a código WMO antiguo para compatibilidad
        val weatherCode = mapConditionToOldCode(condition)
        
        // Obtener emoji desde la condición de Google
        val emoji = getEmojiForCondition(condition, isDay)
        
        // Para compatibilidad, usamos la misma precipitación para rain y showers
        val rain = if (precipitation > 0) precipitation else null
        val showers = null // Google no diferencia entre rain y showers

        return WeatherData(
            temperature = temperature,
            feelsLike = feelsLike,
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
            showers = showers
        )
    }
}
