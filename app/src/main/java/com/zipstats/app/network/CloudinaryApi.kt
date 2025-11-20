package com.zipstats.app.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface CloudinaryApi {
    // Reemplaza TU_CLOUD_NAME con tu cloud name real de BuildConfig o hardcodeado
    // Si usas BuildConfig, mejor inyectar la URL base en el módulo de Hilt.
    // Por simplicidad aquí ponemos la URL relativa asumiendo BaseURL correcta.
    
    @Multipart
    @POST("image/upload") 
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Part("upload_preset") preset: RequestBody,
        @Part("public_id") publicId: RequestBody,
        @Part("folder") folder: RequestBody
    ): CloudinaryResponse
}

data class CloudinaryResponse(
    val public_id: String,
    val secure_url: String,
    val url: String
)