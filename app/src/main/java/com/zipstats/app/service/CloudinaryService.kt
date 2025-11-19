package com.zipstats.app.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.zipstats.app.BuildConfig
import com.zipstats.app.network.CloudinaryApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudinaryService @Inject constructor(
    @ApplicationContext private val context: Context,
    // Necesitarás proveer esta interfaz con Hilt (ver paso 2)
    private val cloudinaryApi: CloudinaryApi
) {

    companion object {
        private val CLOUD_NAME = BuildConfig.CLOUDINARY_CLOUD_NAME
        // API Key y Secret ya no son necesarios aquí para subidas unsigned,
        // pero los mantenemos si los usas para otra cosa.
        // private val API_KEY = BuildConfig.CLOUDINARY_API_KEY

        // ¡IMPORTANTE! Crea un preset "unsigned" en tu dashboard de Cloudinary
        // y pon su nombre aquí.
        private const val UPLOAD_PRESET = "ml_default"
    }

    init {
        Log.d("CloudinaryService", "✅ Cloudinary REST Service inicializado")
    }

    suspend fun uploadImage(uri: Uri, publicId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("CloudinaryService", "=== INICIANDO SUBIDA REST ===")

                // 1. Convertir Uri a File temporal (necesario para Retrofit)
                val file = uriToFile(uri) ?: throw Exception("No se pudo crear archivo temporal")

                // 2. Preparar RequestBody
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val preset = UPLOAD_PRESET.toRequestBody("text/plain".toMediaTypeOrNull())
                val publicIdBody = publicId.toRequestBody("text/plain".toMediaTypeOrNull())
                val folder = "zipstats/profiles".toRequestBody("text/plain".toMediaTypeOrNull())

                // 3. Llamada a la API
                val response = cloudinaryApi.uploadImage(
                    file = body,
                    preset = preset,
                    publicId = publicIdBody,
                    folder = folder
                )

                // 4. Limpiar archivo temporal
                file.delete()

                Log.d("CloudinaryService", "✅ Subida exitosa: ${response.secure_url}")
                response.secure_url

            } catch (e: Exception) {
                Log.e("CloudinaryService", "❌ Error en subida REST", e)
                throw e
            }
        }
    }

    // Helper para convertir Uri a File
    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("upload", ".jpg", context.cacheDir)
            val outputStream = FileOutputStream(tempFile)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("CloudinaryService", "Error convirtiendo Uri a File", e)
            null
        }
    }

    // Esta función no cambia, es solo manipulación de Strings
    fun getOptimizedImageUrl(publicId: String, width: Int = 200, height: Int = 200): String {
        return "https://res.cloudinary.com/$CLOUD_NAME/image/upload/w_$width,h_$height,c_fill,f_auto,q_auto/v1/zipstats/profiles/$publicId"
    }

    fun deleteImage(publicId: String) {
        // La eliminación requiere API Key/Secret y firma, o usar la Admin API.
        // Por seguridad, es mejor hacerlo desde un backend, pero si quieres hacerlo aquí,
        // necesitarás implementar la lógica de firma SHA-1 manual.
        // Por ahora lo dejamos igual.
        Log.d("CloudinaryService", "Eliminación pendiente de migración a REST segura")
    }
}