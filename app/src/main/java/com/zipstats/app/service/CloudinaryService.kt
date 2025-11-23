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
    private val cloudinaryApi: CloudinaryApi
) {

    companion object {
        // Tus credenciales desde BuildConfig (local.properties)
        private val CLOUD_NAME = BuildConfig.CLOUDINARY_CLOUD_NAME
        private val API_KEY = BuildConfig.CLOUDINARY_API_KEY
        // private val API_SECRET = BuildConfig.CLOUDINARY_API_SECRET // No necesario para unsigned upload

        // ¡IMPORTANTE! Asegúrate de que este preset existe en tu Cloudinary y es "Unsigned"
        private const val UPLOAD_PRESET = "ml_default"
    }

    init {
        Log.d("CloudinaryService", "✅ Cloudinary REST Service inicializado para nube: $CLOUD_NAME")
    }

    // En CloudinaryService.kt

    // Función renombrada para coincidir con tu ViewModel
// Y cambio de parámetro: Ahora recibe un File, no una Uri
    suspend fun uploadImageFile(file: File, publicId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("CloudinaryService", "=== SUBIENDO ARCHIVO: ${file.name} ===")

                // 1. Preparamos el cuerpo de la petición con el Archivo que nos llega del ViewModel
                val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull()) // O "image/*"
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                // 2. Preparamos los parámetros extra
                val preset = UPLOAD_PRESET.toRequestBody("text/plain".toMediaTypeOrNull())
                val publicIdBody = publicId.toRequestBody("text/plain".toMediaTypeOrNull())
                val folder = "zipstats/profiles".toRequestBody("text/plain".toMediaTypeOrNull())

                // 3. Llamamos a la API (que ahora devuelve CloudinaryResponse)
                val response = cloudinaryApi.uploadImage(
                    file = body,
                    preset = preset,
                    publicId = publicIdBody,
                    folder = folder
                )

                // 4. Devolvemos la URL limpia
                Log.d("CloudinaryService", "✅ Subida exitosa: ${response.secureUrl}")
                response.secureUrl

            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("CloudinaryService", "❌ Error HTTP: ${e.code()} - $errorBody")
                throw e
            } catch (e: Exception) {
                Log.e("CloudinaryService", "❌ Error genérico en subida", e)
                throw e
            }
        }
    }

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

    fun getOptimizedImageUrl(publicId: String, width: Int = 200, height: Int = 200): String {
        // Usamos la variable CLOUD_NAME que viene de BuildConfig
        return "https://res.cloudinary.com/$CLOUD_NAME/image/upload/w_$width,h_$height,c_fill,f_auto,q_auto/v1/zipstats/profiles/$publicId"
    }

    fun deleteImage(publicId: String) {
        Log.d("CloudinaryService", "Eliminación pendiente de migración a REST segura")
    }
}