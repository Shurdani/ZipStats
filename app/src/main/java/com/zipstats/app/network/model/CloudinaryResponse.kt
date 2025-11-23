package com.zipstats.app.network.model // ⚠️ Asegúrate de que el paquete coincida con tu carpeta

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

// @Keep es VITAL para dispositivos físicos (Release build). 
// Evita que R8/ProGuard le cambie el nombre a la clase y rompa el JSON.
@Keep
data class CloudinaryResponse(
    @SerializedName("public_id")
    val publicId: String,

    @SerializedName("version")
    val version: Int,

    @SerializedName("signature")
    val signature: String? = null,

    @SerializedName("width")
    val width: Int,

    @SerializedName("height")
    val height: Int,

    @SerializedName("format")
    val format: String,

    @SerializedName("resource_type")
    val resourceType: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("bytes")
    val bytes: Int,

    @SerializedName("url")
    val url: String,

    @SerializedName("secure_url") // <--- ¡ESTA ES LA QUE QUEREMOS!
    val secureUrl: String
)