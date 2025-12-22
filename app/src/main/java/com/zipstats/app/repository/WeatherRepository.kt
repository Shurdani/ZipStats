package com.zipstats.app.repository

import android.util.Log
import androidx.annotation.DrawableRes
import com.zipstats.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Datos del clima (Refactorizado para Open-Meteo, pero mantiene la estructura)
 */
data class WeatherData(
    val temperature: Double,      // Temperatura en °C
    val feelsLike: Double,         // Sensación térmica
    val description: String,       // Descripción del clima
    val icon: String,              // Código (ahora será el numérico, ej: "3")
    val humidity: Int,             // Humedad %
    val windSpeed: Double,         // Velocidad del viento m/s
    val weatherEmoji: String,      // Emoji representativo (¡ARREGLADO!)
    val weatherCode: Int,
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
class WeatherRepository @Inject constructor() {

    companion object {
        private const val TAG = "WeatherRepository"
        // 1. CAMBIO: URL de Open-Meteo
        private const val BASE_URL = "https://api.open-meteo.com/v1"

        // 2. ELIMINADO: El WEATHER_EMOJI_MAP antiguo

        /**
         * 3. CORREGIDO: Función de Emojis para Open-Meteo (con "☁️🌙")
         * Esto arregla tu problema de "siempre nublado" por la noche.
         */
        fun getEmojiForWeather(weatherCode: Int, isDay: Int): String {
            val isDayTime = (isDay == 1)

            return when (weatherCode) {
                // ☀️ Despejado
                0 -> if (isDayTime) "☀️" else "🌙"

                // ⛅ Nubes
                1, 2 -> if (isDayTime) "🌤️" else "☁️🌙"
                3 -> "☁️"

                // 🌫️ Niebla
                45, 48 -> "🌫️"

                // 🌦️ Llovizna / Lluvia Ligera (Algo suave)
                // 51, 53: Llovizna
                // 61: Lluvia ligera
                // 80: Chubasco leve
                51, 53, 61, 80 -> if (isDayTime) "🌦️" else "🌧️"

                // 🌧️ Lluvia Fuerte / Moderada (¡Mójate!)
                // 55: Llovizna densa
                // 63, 65: Lluvia fuerte
                // 81, 82: Chubascos fuertes
                55, 63, 65, 81, 82 -> "🌧️"

                // 🥶 AGUANIEVE / HIELO (Los códigos nuevos)
                // Usamos la cara azul de frío porque es una advertencia clara de "Hielo"
                // O puedes usar 🌨️ si prefieres solo clima.
                56, 57, 66, 67 -> "🥶"

                // ❄️ Nieve
                71, 73, 75, 77, 85, 86 -> "❄️"

                // ⛈️ Tormenta
                95 -> "⚡" // Rayo o Nube con rayo

                // 🧊 Granizo (Hail)
                // Como duele si te da, el hielo es muy representativo
                96, 99 -> "⛈️" // Tormenta fuerte (O puedes usar 🧊 si quieres ser muy literal)

                else -> "🤷" // Desconocido
            }
        }

        /**
         * 4. AÑADIDO: Descripción local (Open-Meteo no la da traducida)
         */
        fun getDescriptionForWeather(weatherCode: Int, isDay: Int): String {
            // El texto suele ser el mismo de día o de noche, pero mantenemos el check por si acaso
            // (Ej: Podrías poner "Noche clara" en el 0 si quisieras)

            return when (weatherCode) {
                // ☀️ Despejado / Nubes
                0 -> "Despejado"
                1 -> "Mayormente despejado"
                2 -> "Parcialmente nublado"
                3 -> "Nublado"

                // 🌫️ Niebla
                45 -> "Niebla"
                48 -> "Niebla con escarcha" // Ojo: pavimento resbaladizo

                // 💧 Llovizna (Drizzle)
                51 -> "Llovizna ligera"
                53 -> "Llovizna moderada"
                55 -> "Llovizna densa" // Aquí ya moja bastante

                // ❄️💧 Llovizna Helada (Faltaba en tu lista)
                56, 57 -> "Llovizna helada" // ¡Peligro de hielo!

                // 🌧️ Lluvia (Rain)
                61 -> "Lluvia ligera"
                63 -> "Lluvia moderada"
                65 -> "Lluvia fuerte" // Coincide con tu nuevo icono rainy_heavy

                // ❄️💧 Lluvia Helada (Faltaba en tu lista)
                66, 67 -> "Lluvia helada" // ¡Peligro máximo!

                // ❄️ Nieve (Snow)
                71 -> "Nevada ligera"
                73 -> "Nevada moderada"
                75 -> "Nevada fuerte"
                77 -> "Granos de nieve"

                // 🌧️ Chubascos (Showers - Lluvia repentina)
                80 -> "Chubascos leves"
                81 -> "Chubascos moderados"
                82 -> "Chubascos violentos" // Coincide con rainy_heavy

                // ❄️ Chubascos de nieve
                85 -> "Chubascos de nieve"
                86 -> "Chubascos de nieve fuertes"

                // ⛈️ Tormenta
                95 -> "Tormenta eléctrica"

                // 🧊 Granizo (Hail) - Importante diferenciarlo
                96 -> "Tormenta y granizo"
                99 -> "Tormenta y granizo fuerte"

                else -> "Desconocido"
            }
        }

        /**
         * 5. CAMBIADO: Esta función ahora usa los códigos de Open-Meteo (Int, Int)
         * Mantenemos el nombre para que tu UI (si la llama) se actualice fácil.
         */
        @DrawableRes
        fun getIconResIdForWeather(weatherCode: Int, isDay: Int): Int {
            val isDayTime = (isDay == 1)

            return when (weatherCode) {
                // ☀️ Cielo Despejado
                0 -> if (isDayTime) R.drawable.wb_sunny else R.drawable.nightlight

                // ⛅ Nubes
                1, 2 -> if (isDayTime) R.drawable.partly_cloudy_day else R.drawable.partly_cloudy_night
                3 -> R.drawable.cloud

                // 🌫️ Niebla (Peligroso por visibilidad)
                45, 48 -> R.drawable.foggy

                // 💧 LLOVIZNA / LLUVIA LIGERA (Precaución)
                // 51, 53: Llovizna ligera/moderada
                // 61: Lluvia ligera
                // 80: Chubascos leves
                51, 53, 61, 80 -> R.drawable.rainy

                // 🌧️ LLUVIA FUERTE / AGUACERO (¡Peligro Aquaplaning!)
                // 55: Llovizna densa
                // 63, 65: Lluvia moderada/fuerte
                // 81, 82: Chubascos violentos
                55, 63, 65, 81, 82 -> R.drawable.rainy_heavy // Nuevo icono sugerido

                // ❄️💧 AGUANIEVE / HIELO (Peligro Máximo)
                // 56, 57, 66, 67
                56, 57, 66, 67 -> R.drawable.rainy_snow

                // ❄️ NIEVE
                // En patinete, poca o mucha nieve es igual de malo (resbala).
                // Podrías dejarlo todo junto, o separar 75 y 86 como "snowing_heavy" si quisieras.
                71, 73, 75, 77, 85, 86 -> R.drawable.snowing

                // ⛈️ TORMENTA ELÉCTRICA
                95 -> R.drawable.thunderstorm

                // 🧊 TORMENTA CON GRANIZO (Dolor físico al conducir)
                // 96, 99: Tormenta con granizo leve/fuerte
                // Si no tienes icono de granizo, usa thunderstorm
                96, 99 -> R.drawable.hail // O usa R.drawable.thunderstorm si no tienes este

                // ❓ Default
                else -> R.drawable.help_outline
            }
        }
    }
    /**
     * CAMBIADO: Obtiene el clima actual de Open-Meteo
     */
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): Result<WeatherData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== Iniciando llamada a Open-Meteo API ===")

                // ¡Ya no necesitamos API Key!

                val params = "current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day," +
                    "weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
                    "uv_index,precipitation,rain,showers,precipitation_probability"
                // 🔥 CORRECCIÓN: Pedir viento en m/s (ms) para mantener consistencia con el código
                // El código espera m/s y luego convierte a km/h cuando es necesario
                val units = "temperature_unit=celsius&wind_speed_unit=ms&timezone=auto&precipitation_unit=mm"
                val urlString = "$BASE_URL/forecast?latitude=$latitude&longitude=$longitude&$params&$units"

                Log.d(TAG, "URL: $urlString")

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    Log.d(TAG, "Realizando petición HTTP...")
                    val responseCode = connection.responseCode
                    Log.d(TAG, "Código de respuesta: $responseCode")

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        Log.d(TAG, "Respuesta recibida: ${response.take(200)}...")

                        val weatherData = parseOpenMeteoResponse(response) // Llamamos al nuevo parser
                        Log.d(TAG, "✅ Clima parseado correctamente (Open-Meteo): ${weatherData.temperature}°C, ${weatherData.description}")

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
        return getCurrentWeather(latitude, longitude) // Ahora llama a la nueva función
    }

    /**
     * CAMBIADO: Parsea la respuesta JSON de Open-Meteo
     */
    private fun parseOpenMeteoResponse(jsonString: String): WeatherData {
        val json = JSONObject(jsonString)
        val current = json.getJSONObject("current")

        val temperature = current.getDouble("temperature_2m")
        val feelsLike = current.getDouble("apparent_temperature")
        val humidity = current.getInt("relative_humidity_2m")
        val windSpeed = current.getDouble("wind_speed_10m")
        val weatherCode = current.getInt("weather_code")
        val isDay = current.getInt("is_day")
        val uvIndex = current.getDouble("uv_index")
        val windDirection = current.getInt("wind_direction_10m")
        val windGusts = current.getDouble("wind_gusts_10m")
        val rainProbability = current.getInt("precipitation_probability")
        val precipitation = current.getDouble("precipitation")
        val rain = current.optDouble("rain", 0.0)
        val showers = current.optDouble("showers", 0.0)

        // Usamos nuestras nuevas funciones del companion object
        val description = getDescriptionForWeather(weatherCode, isDay)
        val emoji = getEmojiForWeather(weatherCode, isDay) // <-- Aquí se usa la función corregida

        return WeatherData(
            temperature = temperature,
            feelsLike = feelsLike,
            description = description,
            icon = weatherCode.toString(),
            humidity = humidity,
            windSpeed = windSpeed,
            weatherEmoji = emoji,
            weatherCode = weatherCode,
            isDay = isDay == 1,
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