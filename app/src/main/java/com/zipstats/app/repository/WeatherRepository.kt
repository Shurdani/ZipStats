package com.zipstats.app.repository

import android.util.Log
import androidx.annotation.DrawableRes
import com.zipstats.app.BuildConfig
import com.zipstats.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Datos del clima obtenidos de OpenWeather API
 */
data class WeatherData(
    val temperature: Double,      // Temperatura en °C
    val feelsLike: Double,         // Sensación térmica
    val description: String,       // Descripción del clima
    val icon: String,              // Código del icono (01d, 02n, etc.)
    val humidity: Int,             // Humedad %
    val windSpeed: Double,         // Velocidad del viento m/s
    val weatherEmoji: String       // Emoji representativo
)

@Singleton
class WeatherRepository @Inject constructor() {
    
    companion object {
        private const val TAG = "WeatherRepository"
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5"
        
        // Mapeo de códigos de icono de OpenWeather a emojis
        private val WEATHER_EMOJI_MAP = mapOf(
            "01d" to "☀️",  // Despejado día
            "01n" to "🌙",  // Despejado noche
            "02d" to "🌤️",  // Pocas nubes día
            "02n" to "☁️",  // Pocas nubes noche
            "03d" to "☁️",  // Nubes dispersas
            "03n" to "☁️",  // Nubes dispersas noche
            "04d" to "☁️",  // Nubes
            "04n" to "☁️",  // Nubes noche
            "09d" to "🌧️",  // Lluvia
            "09n" to "🌧️",  // Lluvia noche
            "10d" to "🌦️",  // Lluvia día
            "10n" to "🌧️",  // Lluvia noche
            "11d" to "⛈️",  // Tormenta
            "11n" to "⛈️",  // Tormenta noche
            "13d" to "❄️",  // Nieve
            "13n" to "❄️",  // Nieve noche
            "50d" to "🌫️",  // Niebla
            "50n" to "🌫️"   // Niebla noche
        )
        
        /**
         * Convierte un código de icono de OpenWeather (ej: "01d", "02n") en un ID de Recurso de Drawable.
         * @param iconCode Código de icono de OpenWeather (ej: "01d", "02n", "03d", etc.)
         * @return ID del recurso drawable correspondiente
         */
        @DrawableRes
        fun getIconResIdForWeather(iconCode: String): Int {
            return when (iconCode) {
                // Cielo Despejado
                "01d" -> R.drawable.wb_sunny
                "01n" -> R.drawable.nightlight

                // Pocas nubes / Parcialmente nublado
                "02d" -> R.drawable.partly_cloudy_day
                "02n" -> R.drawable.partly_cloudy_night

                // Nubes dispersas / Nublado
                "03d", "03n", "04d", "04n" -> R.drawable.cloud

                // Lluvia / Llovizna / Chubascos
                "09d", "09n", "10d", "10n" -> R.drawable.rainy

                // Tormenta
                "11d", "11n" -> R.drawable.thunderstorm

                // Nieve
                "13d", "13n" -> R.drawable.snowing

                // Niebla
                "50d", "50n" -> R.drawable.foggy

                // Icono por defecto
                else -> R.drawable.help_outline
            }
        }
    }
    
    /**
     * Obtiene el clima actual para una ubicación específica
     */
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): Result<WeatherData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== Iniciando llamada a OpenWeather API ===")
                val apiKey = BuildConfig.OPENWEATHER_API_KEY
                Log.d(TAG, "API Key length: ${apiKey.length} chars")
                
                // Validar que la API key esté configurada
                if (apiKey == "YOUR_OPENWEATHER_API_KEY" || apiKey.isBlank()) {
                    Log.e(TAG, "❌ API key de OpenWeather no configurada correctamente")
                    Log.e(TAG, "Por favor, añade tu API key en local.properties:")
                    Log.e(TAG, "openweather.api.key=TU_API_KEY_AQUI")
                    return@withContext Result.failure(
                        Exception("API key de OpenWeather no configurada en local.properties")
                    )
                }
                
                val urlString = "$BASE_URL/weather?lat=$latitude&lon=$longitude&appid=$apiKey&units=metric&lang=es"
                Log.d(TAG, "URL (sin API key): $BASE_URL/weather?lat=$latitude&lon=$longitude&units=metric&lang=es")
                
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
                        
                        val weatherData = parseWeatherResponse(response)
                        Log.d(TAG, "✅ Clima parseado correctamente: ${weatherData.temperature}°C, ${weatherData.description}")
                        
                        Result.success(weatherData)
                    } else {
                        val errorMessage = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Sin mensaje de error"
                        Log.e(TAG, "❌ Error HTTP $responseCode")
                        Log.e(TAG, "Mensaje de error: $errorMessage")
                        
                        when (responseCode) {
                            401 -> Log.e(TAG, "API key inválida. Verifica tu key en local.properties")
                            429 -> Log.e(TAG, "Límite de llamadas excedido (60/min o 1000/día)")
                            else -> Log.e(TAG, "Error desconocido del servidor")
                        }
                        
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
     * Obtiene el clima histórico para una fecha y ubicación específicas
     * Nota: Requiere suscripción de pago en OpenWeather para datos históricos
     * Por ahora, devolvemos el clima actual como aproximación
     */
    suspend fun getHistoricalWeather(
        latitude: Double,
        longitude: Double,
        timestamp: Long
    ): Result<WeatherData> {
        // Por ahora, usar el clima actual como aproximación
        // En el futuro, se puede implementar con la API histórica de OpenWeather
        Log.d(TAG, "Obteniendo clima histórico (usando actual como aproximación)")
        return getCurrentWeather(latitude, longitude)
    }
    
    /**
     * Parsea la respuesta JSON de OpenWeather
     */
    private fun parseWeatherResponse(jsonString: String): WeatherData {
        val json = JSONObject(jsonString)
        
        val main = json.getJSONObject("main")
        val weather = json.getJSONArray("weather").getJSONObject(0)
        val wind = json.getJSONObject("wind")
        
        val temperature = main.getDouble("temp")
        val feelsLike = main.getDouble("feels_like")
        val description = weather.getString("description")
        val icon = weather.getString("icon")
        val humidity = main.getInt("humidity")
        val windSpeed = wind.getDouble("speed")
        
        val emoji = WEATHER_EMOJI_MAP[icon] ?: "☁️"
        
        return WeatherData(
            temperature = temperature,
            feelsLike = feelsLike,
            description = description,
            icon = icon,
            humidity = humidity,
            windSpeed = windSpeed,
            weatherEmoji = emoji
        )
    }
}

