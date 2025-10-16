package com.example.patineta.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.patineta.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CloudinaryService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        // Credenciales cargadas desde local.properties (BuildConfig)
        // ⚠️ IMPORTANTE: Configura tus credenciales en local.properties
        // Puedes obtener las credenciales en: https://cloudinary.com/console
        private val CLOUD_NAME = BuildConfig.CLOUDINARY_CLOUD_NAME
        private val API_KEY = BuildConfig.CLOUDINARY_API_KEY
        private val API_SECRET = BuildConfig.CLOUDINARY_API_SECRET
    }
    
    init {
        // Configurar Cloudinary solo una vez
        try {
            val config = mapOf(
                "cloud_name" to CLOUD_NAME,
                "api_key" to API_KEY,
                "api_secret" to API_SECRET
            )
            MediaManager.init(context, config)
            Log.d("CloudinaryService", "✅ Cloudinary configurado correctamente")
        } catch (e: Exception) {
            Log.e("CloudinaryService", "❌ Error al configurar Cloudinary: ${e.message}")
        }
    }
    
    suspend fun uploadImage(uri: Uri, publicId: String): String {
        return suspendCancellableCoroutine { continuation ->
            try {
                Log.d("CloudinaryService", "=== INICIANDO SUBIDA A CLOUDINARY ===")
                Log.d("CloudinaryService", "URI: $uri")
                Log.d("CloudinaryService", "Public ID: $publicId")
                
                MediaManager.get().upload(uri)
                    .option("public_id", publicId)
                    .option("resource_type", "image")
                    .option("folder", "zipstats/profiles") // Organizar en carpetas
                    .option("transformation", "f_auto,q_auto,w_500,h_500,c_fill") // Optimización automática
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d("CloudinaryService", "Subida iniciada: $requestId")
                        }
                        
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                            val progress = (bytes * 100 / totalBytes).toInt()
                            Log.d("CloudinaryService", "Progreso: $progress%")
                        }
                        
                        override fun onSuccess(requestId: String, resultData: MutableMap<Any?, Any?>) {
                            val secureUrl = resultData["secure_url"] as? String
                            if (secureUrl != null) {
                                Log.d("CloudinaryService", "✅ Subida exitosa: $secureUrl")
                                continuation.resume(secureUrl)
                            } else {
                                Log.e("CloudinaryService", "❌ URL no encontrada en respuesta")
                                continuation.resumeWithException(Exception("URL no encontrada en respuesta de Cloudinary"))
                            }
                        }
                        
                        override fun onError(requestId: String, error: ErrorInfo) {
                            Log.e("CloudinaryService", "❌ Error en subida: ${error.description}")
                            continuation.resumeWithException(Exception("Error en Cloudinary: ${error.description}"))
                        }
                        
                        override fun onReschedule(requestId: String, error: ErrorInfo) {
                            Log.w("CloudinaryService", "Reagendando subida: ${error.description}")
                        }
                    })
                    .dispatch()
                    
            } catch (e: Exception) {
                Log.e("CloudinaryService", "❌ Error al iniciar subida", e)
                continuation.resumeWithException(e)
            }
        }
    }
    
    fun getOptimizedImageUrl(publicId: String, width: Int = 200, height: Int = 200): String {
        return "https://res.cloudinary.com/$CLOUD_NAME/image/upload/w_$width,h_$height,c_fill,f_auto,q_auto/v1/zipstats/profiles/$publicId"
    }
    
    fun deleteImage(publicId: String) {
        try {
            // En la versión 3.x, la eliminación se hace de forma diferente
            Log.d("CloudinaryService", "Eliminación de imagen: $publicId (implementar según API v3)")
        } catch (e: Exception) {
            Log.e("CloudinaryService", "❌ Error al eliminar imagen", e)
        }
    }
}