package com.zipstats.app

import android.app.Application
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mapbox.common.MapboxOptions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PatinetaApplication : Application() {
    
    private val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    
    override fun onCreate() {
        super.onCreate()
        
        // Inicializar Mapbox con el access token
        val mapboxToken = getString(R.string.mapbox_access_token)
        if (mapboxToken.isNotEmpty() && mapboxToken != "YOUR_MAPBOX_ACCESS_TOKEN") {
            MapboxOptions.accessToken = mapboxToken
            Log.d("PatinetaApplication", "✅ Mapbox token configurado correctamente")
        } else {
            Log.e("PatinetaApplication", "❌ Mapbox token no encontrado o inválido")
        }
        
        // Configurar handler global para excepciones no capturadas
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            // Verificar si es un error de PERMISSION_DENIED de Firestore durante el logout
            val isPermissionDenied = exception is FirebaseFirestoreException &&
                    exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                    exception.message?.contains("PERMISSION_DENIED") == true
            
            // También verificar si es causado por PERMISSION_DENIED en la causa
            val causedByPermissionDenied = exception.cause is FirebaseFirestoreException &&
                    (exception.cause as FirebaseFirestoreException).code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            
            if (isPermissionDenied || causedByPermissionDenied) {
                // Silenciar el error durante el logout - es esperado cuando se cierra sesión
                Log.w("PatinetaApplication", "Error de PERMISSION_DENIED ignorado durante logout (esperado): ${exception.message}")
                // NO llamar al handler por defecto para evitar que la app se cierre
                return@setDefaultUncaughtExceptionHandler
            }
            
            // Ignorar el error del diálogo de atribución de Mapbox (ResourcesNotFoundException)
            val isMapboxAttributionError = exception is android.content.res.Resources.NotFoundException &&
                    exception.message?.contains("Resource ID #0x0") == true &&
                    exception.stackTrace.any { it.className.contains("AttributionDialogManager") }
            
            if (isMapboxAttributionError) {
                // Silenciar el error del diálogo de atribución de Mapbox
                Log.w("PatinetaApplication", "Error del diálogo de atribución de Mapbox ignorado: ${exception.message}")
                return@setDefaultUncaughtExceptionHandler
            }
            
            // Para otros errores, usar el handler por defecto
            defaultUncaughtExceptionHandler?.uncaughtException(thread, exception)
        }
    }
}