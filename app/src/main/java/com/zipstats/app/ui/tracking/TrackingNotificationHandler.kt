package com.zipstats.app.ui.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat

class TrackingNotificationHandler(private val context: Context) {

    private val TAG = "TrackingNotification"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        // Centralizamos los IDs aquí para no tener errores de "Unresolved reference"
        const val WEATHER_CHANNEL_ID = "weather_change_channel"
        const val WEATHER_NOTIF_ID = 2000
    }

    init {
        createWeatherChangeNotificationChannel()
    }

    /**
     * Crea el canal de notificaciones para cambios de clima
     */
    private fun createWeatherChangeNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WEATHER_CHANNEL_ID,
                "Avisos de Seguridad",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones cuando cambia el clima durante una ruta activa"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "📢 Canal de notificaciones de cambio de clima creado")
        }
    }

    /**
     * Muestra una notificación de cambio de clima con vibración
     */
    fun showWeatherChangeNotification(badgeText: String, iconResId: Int) {
        try {
            // Patrón de vibración de doble pulso
            val vibrationPattern = longArrayOf(0, 200, 100, 200)

            // Intent para abrir la app
            val intent = Intent(context, com.zipstats.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            // Construir la notificación usando el CHANNEL_ID correcto
            val notification = NotificationCompat.Builder(context, WEATHER_CHANNEL_ID)
                .setSmallIcon(iconResId)
                .setContentTitle("Aviso de Seguridad")
                .setContentText(badgeText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setVibrate(vibrationPattern)
                .setAutoCancel(true) // Cambiado a true para que se limpie al pulsarla
                .setContentIntent(pendingIntent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(badgeText))
                .build()

            // Vibración profesional
            triggerProfessionalVibration(vibrationPattern)

            // Mostrarla usando el NOTIF_ID del companion
            notificationManager.notify(WEATHER_NOTIF_ID, notification)
            Log.d(TAG, "📢 Notificación enviada: $badgeText")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al mostrar notificación: ${e.message}")
        }
    }

    private fun triggerProfessionalVibration(pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ No se pudo vibrar")
        }
    }


/**
 * Elimina la notificación de clima
 */
fun dismissWeatherNotification() {
    try {
        notificationManager.cancel(WEATHER_NOTIF_ID)
        Log.d(TAG, "🧹 Notificación de clima eliminada")
    } catch (e: Exception) {
        Log.e(TAG, "Error al cancelar notificación: ${e.message}")
    }
}

}