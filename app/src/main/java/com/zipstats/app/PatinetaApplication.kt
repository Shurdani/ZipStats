package com.zipstats.app

import android.app.Application
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PatinetaApplication : Application() {
    
    private val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    
    override fun onCreate() {
        super.onCreate()
        
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
            
            // Para otros errores, usar el handler por defecto
            defaultUncaughtExceptionHandler?.uncaughtException(thread, exception)
        }
    }
}