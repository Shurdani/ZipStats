package com.example.patineta.ui.profile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.patineta.model.Achievement
import com.example.patineta.model.AchievementLevel
import com.example.patineta.model.AchievementRequirementType
import com.example.patineta.model.Avatar
import com.example.patineta.model.Scooter
import com.example.patineta.model.User
import com.example.patineta.repository.RecordRepository
import com.example.patineta.repository.ScooterRepository
import com.example.patineta.repository.UserRepository
import com.example.patineta.utils.DateUtils
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import com.example.patineta.service.CloudinaryService
import javax.inject.Inject

data class UserProfile(
    val name: String,
    val email: String,
    val photoUrl: String? = null
)

data class ScooterUi(
    val name: String,
    val brand: String,
    val model: String,
    val purchaseDate: String
)

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(
        val user: User,
        val scooters: List<Scooter> = emptyList(),
        val biometricEnabled: Boolean = false,
        val message: String = "",
        val unlockedAchievements: Int = 0,
        val totalAchievements: Int = 30
    ) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

sealed class ProfileEvent {
    object LoadUserProfile : ProfileEvent()
    object Logout : ProfileEvent()
    data class ToggleBiometric(val enabled: Boolean) : ProfileEvent()
    data class UpdatePhoto(val uri: Uri) : ProfileEvent()
    data class SelectAvatar(val avatar: Avatar) : ProfileEvent()
    object RemovePhoto : ProfileEvent()
    object PrepareCamera : ProfileEvent()
    object UpdatePhotoFromCamera : ProfileEvent()
    data class ChangePassword(val currentPassword: String, val newPassword: String) : ProfileEvent()
    data class DeleteAccount(val password: String) : ProfileEvent()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val scooterRepository: ScooterRepository,
    private val recordRepository: RecordRepository,
    private val achievementsService: com.example.patineta.service.AchievementsService,
    private val cloudinaryService: CloudinaryService,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var tempPhotoUri: Uri? = null
    private var photoFile: File? = null
    
    // Estado para notificar cuando la cámara está lista
    private val _cameraReady = MutableStateFlow<Uri?>(null)
    val cameraReady: StateFlow<Uri?> = _cameraReady.asStateFlow()

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    // Total de logros en el nuevo sistema (30 logros)
    private val totalAchievementsCount = 30
    
    // Obtener la lista de logros del servicio centralizado
    private val allAchievements get() = achievementsService.allAchievements

    init {
        loadUserProfile()
    }
    
    // Eliminado: ahora se usa achievementsService.allAchievements
    
    private fun calculateUnlockedAchievements(stats: RecordRepository.AchievementStats): Int {
        return allAchievements.count { achievement ->
            when (achievement.requirementType) {
                AchievementRequirementType.DISTANCE -> 
                    stats.totalDistance >= (achievement.requiredDistance ?: 0.0)
                AchievementRequirementType.TRIPS -> 
                    stats.totalTrips >= (achievement.requiredTrips ?: 0)
                AchievementRequirementType.CONSECUTIVE_DAYS -> 
                    stats.consecutiveDays >= (achievement.requiredConsecutiveDays ?: 0)
                AchievementRequirementType.UNIQUE_WEEKS -> 
                    stats.uniqueWeeks >= (achievement.requiredUniqueWeeks ?: 0)
                AchievementRequirementType.MAINTENANCE_COUNT -> 
                    stats.maintenanceCount >= (achievement.requiredMaintenanceCount ?: 0)
                AchievementRequirementType.CO2_SAVED -> 
                    stats.co2Saved >= (achievement.requiredCO2Saved ?: 0.0)
                AchievementRequirementType.UNIQUE_MONTHS -> 
                    stats.uniqueMonths >= (achievement.requiredUniqueMonths ?: 0)
                AchievementRequirementType.CONSECUTIVE_MONTHS -> 
                    stats.consecutiveMonths >= (achievement.requiredConsecutiveMonths ?: 0)
                AchievementRequirementType.ALL_OTHERS -> {
                    // Verificar si todos los demás logros están desbloqueados
                    val otherAchievements = allAchievements.filter { it.id != achievement.id }
                    otherAchievements.all { other ->
                        when (other.requirementType) {
                            AchievementRequirementType.DISTANCE -> 
                                stats.totalDistance >= (other.requiredDistance ?: 0.0)
                            AchievementRequirementType.TRIPS -> 
                                stats.totalTrips >= (other.requiredTrips ?: 0)
                            AchievementRequirementType.CONSECUTIVE_DAYS -> 
                                stats.consecutiveDays >= (other.requiredConsecutiveDays ?: 0)
                            AchievementRequirementType.UNIQUE_WEEKS -> 
                                stats.uniqueWeeks >= (other.requiredUniqueWeeks ?: 0)
                            AchievementRequirementType.MAINTENANCE_COUNT -> 
                                stats.maintenanceCount >= (other.requiredMaintenanceCount ?: 0)
                            AchievementRequirementType.CO2_SAVED -> 
                                stats.co2Saved >= (other.requiredCO2Saved ?: 0.0)
                            AchievementRequirementType.UNIQUE_MONTHS -> 
                                stats.uniqueMonths >= (other.requiredUniqueMonths ?: 0)
                            AchievementRequirementType.CONSECUTIVE_MONTHS -> 
                                stats.consecutiveMonths >= (other.requiredConsecutiveMonths ?: 0)
                            else -> false
                        }
                    }
                }
                else -> false
            }
        }
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            try {
                _uiState.value = ProfileUiState.Loading
                val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
                
                val user = userRepository.getUser(userId) ?: throw Exception("Usuario no encontrado")
                
                // Cargar scooters con sus kilometrajes totales
                val scooters = scooterRepository.getScooters().first()
                val records = recordRepository.getRecords().first()
                
                // Calcular kilometraje total para cada patinete
                val scootersWithKm = scooters.map { scooter ->
                    val scooterRecords = records.filter { it.patinete == scooter.nombre }
                    val totalKm = scooterRecords.sumOf { it.diferencia }
                    scooter.copy(kilometrajeActual = totalKm)
                }
                
                // Calcular logros desbloqueados usando el nuevo sistema
                val stats = recordRepository.getAchievementStats()
                val unlockedAchievements = calculateUnlockedAchievements(stats)
                
                val biometricEnabled = prefs.getBoolean("biometric_enabled", false)
                
                _user.value = user
                _uiState.value = ProfileUiState.Success(
                    user = user,
                    scooters = scootersWithKm,
                    biometricEnabled = biometricEnabled,
                    unlockedAchievements = unlockedAchievements,
                    totalAchievements = totalAchievementsCount
                )
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "Error al cargar el perfil")
            }
        }
    }

    // Eliminado: método obsoleto que usaba startActivityForResult deprecado

    fun uploadProfileImage(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = ProfileUiState.Loading
                val currentUser = auth.currentUser
                
                if (currentUser == null) {
                    throw Exception("Usuario no autenticado")
                }
                
                val userId = currentUser.uid
                Log.d("ProfileVM", "=== SUBIENDO IMAGEN A CLOUDINARY ===")
                Log.d("ProfileVM", "Usuario ID: $userId")
                Log.d("ProfileVM", "URI de imagen: $uri")
                
                // Crear ID único para la imagen
                val timestamp = System.currentTimeMillis()
                val publicId = "profile_${userId}_$timestamp"
                
                // Subir a Cloudinary
                val cloudinaryUrl = cloudinaryService.uploadImage(uri, publicId)
                
                Log.d("ProfileVM", "✅ Imagen subida a Cloudinary: $cloudinaryUrl")
                
                // Guardar también localmente como respaldo
                val localImagePath = saveImageLocally(uri, userId)
                Log.d("ProfileVM", "✅ Imagen guardada localmente: $localImagePath")
                
                // Actualizar en Firestore con la URL de Cloudinary
                Log.d("ProfileVM", "Actualizando perfil en Firestore...")
                userRepository.updateUserPhoto(cloudinaryUrl)
                
                Log.d("ProfileVM", "✅ Perfil actualizado en Firestore")
                
                // Limpiar archivos temporales después de la subida exitosa
                cleanupTempFiles()
                
                // Recargar el perfil
                loadUserProfile()
                
                Log.d("ProfileVM", "=== SUBIDA COMPLETADA EXITOSAMENTE ===")
                
            } catch (e: Exception) {
                Log.e("ProfileVM", "❌ ERROR AL SUBIR IMAGEN", e)
                Log.e("ProfileVM", "Tipo de error: ${e.javaClass.simpleName}")
                Log.e("ProfileVM", "Mensaje: ${e.message}")
                
                // Fallback: intentar guardar localmente si falla Cloudinary
                try {
                    Log.d("ProfileVM", "Intentando fallback a almacenamiento local...")
                    val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")
                    val localImagePath = saveImageLocally(uri, currentUser.uid)
                    userRepository.updateUserPhoto(localImagePath)
                    
                    // Limpiar archivos temporales después del fallback exitoso
                    cleanupTempFiles()
                    
                    loadUserProfile()
                    Log.d("ProfileVM", "✅ Fallback exitoso: imagen guardada localmente")
                } catch (fallbackError: Exception) {
                    Log.e("ProfileVM", "❌ Fallback también falló", fallbackError)
                    _uiState.value = ProfileUiState.Error("Error al guardar la imagen: ${e.message}")
                }
            }
        }
    }
    
    private suspend fun saveImageLocally(uri: Uri, userId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Crear directorio para imágenes de perfil
                val imagesDir = File(context.filesDir, "profile_images")
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                
                // Crear archivo con nombre único
                val timestamp = System.currentTimeMillis()
                val imageFile = File(imagesDir, "profile_${userId}_$timestamp.jpg")
                
                Log.d("ProfileVM", "Guardando imagen en: ${imageFile.absolutePath}")
                
                // Copiar la imagen al almacenamiento interno
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    imageFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                // Comprimir la imagen si es muy grande
                val compressedFile = compressImageIfNeeded(imageFile)
                
                Log.d("ProfileVM", "Imagen guardada: ${compressedFile.absolutePath}")
                Log.d("ProfileVM", "Tamaño: ${compressedFile.length()} bytes")
                
                // Retornar la ruta relativa para almacenar en Firestore
                "local://${compressedFile.name}"
                
            } catch (e: Exception) {
                Log.e("ProfileVM", "Error al guardar imagen localmente", e)
                throw Exception("Error al guardar la imagen: ${e.message}")
            }
        }
    }
    
    private suspend fun compressImageIfNeeded(imageFile: File): File {
        return withContext(Dispatchers.IO) {
            try {
                // Si la imagen es menor a 500KB, no comprimir
                if (imageFile.length() < 500 * 1024) {
                    return@withContext imageFile
                }
                
                Log.d("ProfileVM", "Comprimiendo imagen (${imageFile.length()} bytes)...")
                
                // Crear archivo comprimido
                val compressedFile = File(imageFile.parent, "compressed_${imageFile.name}")
                
                // Usar BitmapFactory para comprimir
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, options)
                
                // Calcular factor de escala
                val scale = when {
                    options.outWidth > 1024 || options.outHeight > 1024 -> 4
                    options.outWidth > 512 || options.outHeight > 512 -> 2
                    else -> 1
                }
                
                options.inJustDecodeBounds = false
                options.inSampleSize = scale
                
                val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, options)
                
                // Guardar comprimida
                compressedFile.outputStream().use { outputStream ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
                }
                
                bitmap.recycle()
                
                // Eliminar archivo original y renombrar el comprimido
                imageFile.delete()
                compressedFile.renameTo(imageFile)
                
                Log.d("ProfileVM", "Imagen comprimida: ${imageFile.length()} bytes")
                imageFile
                
            } catch (e: Exception) {
                Log.e("ProfileVM", "Error al comprimir imagen", e)
                // Si falla la compresión, retornar el archivo original
                imageFile
            }
        }
    }

    fun updateProfile(newName: String) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
                firestore.collection("users").document(userId)
                    .update("name", newName)
                    .await()
                loadUserProfile()
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "Error al actualizar el perfil")
            }
        }
    }

    fun updatePassword(
        currentPassword: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser ?: throw Exception("Usuario no autenticado")
                val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                
                user.reauthenticate(credential).await()
                user.updatePassword(newPassword).await()
                
                onSuccess()
            } catch (e: Exception) {
                onError(when (e) {
                    is FirebaseAuthInvalidCredentialsException -> "Contraseña actual incorrecta"
                    else -> e.message ?: "Error al cambiar la contraseña"
                })
            }
        }
    }

    fun deleteAccount(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")
                val userId = currentUser.uid
                
                // Eliminar registros del usuario
                recordRepository.deleteAllUserRecords()
                
                // Eliminar patinetes del usuario
                scooterRepository.deleteAllUserScooters()
                
                // Eliminar foto de perfil
                try {
                    storage.reference.child("profile_images/$userId.jpg").delete().await()
                } catch (e: Exception) {
                    // Ignorar error si no existe la imagen
                }
                
                // Eliminar documento del usuario
                userRepository.deleteUser(userId)
                
                // Eliminar cuenta de autenticación
                currentUser.delete().await()
                
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Error al eliminar la cuenta")
            }
        }
    }

    private fun loadUserScooters() {
        viewModelScope.launch {
            try {
                val scooters = scooterRepository.getUserScooters()
                _uiState.update { currentState ->
                    when (currentState) {
                        is ProfileUiState.Success -> {
                            val currentUser = _user.value ?: return@update currentState
                            currentState.copy(user = currentUser, scooters = scooters)
                        }
                        else -> currentState
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading scooters", e)
            }
        }
    }

    fun addScooter(nombre: String, marca: String, modelo: String, fechaCompra: String?, vehicleType: com.example.patineta.model.VehicleType = com.example.patineta.model.VehicleType.PATINETE) {
        viewModelScope.launch {
            try {
                // Convertir la fecha del formato de visualización (DD/MM/YYYY) al formato de API (YYYY-MM-DD)
                val fechaFormateada = fechaCompra?.let { DateUtils.formatForApi(DateUtils.parseDisplayDate(it)) }
                
                scooterRepository.addVehicle(
                    nombre = nombre,
                    marca = marca,
                    modelo = modelo,
                    fechaCompra = fechaFormateada,
                    vehicleType = vehicleType
                )
                loadUserProfile()
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error adding scooter", e)
                _uiState.update { currentState ->
                    when (currentState) {
                        is ProfileUiState.Success -> {
                            currentState.copy(message = "Error al guardar el vehículo: ${e.message}")
                        }
                        else -> currentState
                    }
                }
            }
        }
    }

    fun deleteScooter(scooterId: String) {
        viewModelScope.launch {
            try {
                scooterRepository.deleteScooter(scooterId)
                loadUserProfile()
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error deleting scooter", e)
                _uiState.update { currentState ->
                    when (currentState) {
                        is ProfileUiState.Success -> {
                            currentState.copy(message = "Error al eliminar el vehículo: ${e.message}")
                        }
                        else -> currentState
                    }
                }
            }
        }
    }

    fun setAvatar(avatar: Avatar) {
        viewModelScope.launch {
            try {
                _uiState.value = ProfileUiState.Loading
                val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")
                
                userRepository.updateAvatar(currentUser.uid, avatar)
                
                // Si hay una foto de perfil, la eliminamos
                if (currentUser.photoUrl != null) {
                    // Eliminar la foto de Firebase Storage
                    val photoRef = storage.reference.child("profile_photos/${currentUser.uid}")
                    photoRef.delete().await()
                    
                    // Actualizar el perfil del usuario
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setPhotoUri(null)
                        .build()
                    
                    currentUser.updateProfile(profileUpdates).await()
                }
                
                loadUserProfile()
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Error al actualizar el avatar: ${e.message}")
            }
        }
    }

    // Eliminado: métodos obsoletos que usaban startActivityForResult deprecado

    fun checkAndRequestCameraPermission(context: Context): Boolean {
        return when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                true
            }
            else -> false
        }
    }

    fun prepareCamera(context: Context) {
        viewModelScope.launch {
            try {
                auth.currentUser ?: throw Exception("Usuario no autenticado")
                
                // Limpiar estado anterior
                cleanupTempFiles()
                
                // Crear directorio si no existe
                val storageDir = File(context.cacheDir, "camera")
                if (!storageDir.exists()) {
                    storageDir.mkdirs()
                }
                
                // Crear archivo temporal con nombre único
                val timestamp = System.currentTimeMillis()
                photoFile = File.createTempFile(
                    "PROFILE_${timestamp}_",
                    ".jpg",
                    storageDir
                )
                
                Log.d("ProfileVM", "Archivo temporal creado: ${photoFile?.absolutePath}")
                
                // Obtener URI usando FileProvider
                tempPhotoUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    photoFile!!
                )
                
                Log.d("ProfileVM", "URI temporal creada: $tempPhotoUri")
                
                // Notificar que la cámara está lista con la URI
                _cameraReady.value = tempPhotoUri
                
            } catch (e: Exception) {
                Log.e("ProfileVM", "Error al preparar la cámara: ${e.message}", e)
                _uiState.value = ProfileUiState.Error("Error al preparar la cámara: ${e.message}")
            }
        }
    }
    
    fun clearCameraReady() {
        _cameraReady.value = null
    }
    
    private fun cleanupTempFiles() {
        try {
            tempPhotoUri = null
            photoFile?.delete()
            photoFile = null
            Log.d("ProfileVM", "✅ Archivos temporales limpiados")
        } catch (e: Exception) {
            Log.e("ProfileVM", "Error al limpiar archivos temporales", e)
        }
    }
    
    fun getImageUri(photoUrl: String?): Uri? {
        return when {
            // Imagen local
            photoUrl?.startsWith("local://") == true -> {
                val fileName = photoUrl.removePrefix("local://")
                val imageFile = File(context.filesDir, "profile_images/$fileName")
                if (imageFile.exists()) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        imageFile
                    )
                } else {
                    null
                }
            }
            // URL de Cloudinary o cualquier URL HTTP
            photoUrl?.startsWith("http") == true -> {
                Uri.parse(photoUrl)
            }
            // Otros casos
            else -> photoUrl?.let { Uri.parse(it) }
        }
    }

    fun handleEvent(event: ProfileEvent, context: Context? = null) {
        when (event) {
            is ProfileEvent.LoadUserProfile -> loadUserProfile()
            is ProfileEvent.Logout -> logout()
            is ProfileEvent.ToggleBiometric -> toggleBiometric(event.enabled)
            is ProfileEvent.UpdatePhoto -> uploadProfileImage(event.uri)
            is ProfileEvent.SelectAvatar -> updateAvatar(event.avatar.id, event.avatar.emoji)
            is ProfileEvent.RemovePhoto -> removeProfilePhoto()
            is ProfileEvent.PrepareCamera -> context?.let { prepareCamera(it) }
            is ProfileEvent.UpdatePhotoFromCamera -> updatePhotoFromCamera()
            is ProfileEvent.ChangePassword -> changePassword(event.currentPassword, event.newPassword)
            is ProfileEvent.DeleteAccount -> deleteAccount(event.password)
        }
    }

    private fun logout() {
        viewModelScope.launch {
            try {
                auth.signOut()
                _uiState.value = ProfileUiState.Success(
                    user = User(),
                    message = "Sesión cerrada correctamente"
                )
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Error al cerrar sesión: ${e.message}")
            }
        }
    }

    private fun updatePhoto(uri: Uri) {
        // Usar el mismo método mejorado para ambas fuentes (galería y cámara)
        uploadProfileImage(uri)
    }

    private fun updatePhotoFromCamera() {
        tempPhotoUri?.let { uri ->
            // NO eliminar el archivo temporal hasta después de la subida
            updatePhoto(uri)
            // Los archivos temporales se limpiarán automáticamente más tarde
        }
    }

    private fun removeProfilePhoto() {
        viewModelScope.launch {
            try {
                _uiState.value = ProfileUiState.Loading
                val user = auth.currentUser ?: throw Exception("Usuario no autenticado")
                
                // Intentar eliminar la foto de Firebase Storage si existe
                try {
                    val photoRef = storage.reference
                        .child(PROFILE_PHOTOS_PATH)
                        .child(user.uid)
                        .child("profile.jpg")
                    photoRef.delete().await()
                } catch (e: Exception) {
                    // Ignorar errores al eliminar la foto, ya que podría no existir
                    println("DEBUG: Error al eliminar foto (ignorado): ${e.message}")
                }
                
                // Actualizar el perfil del usuario
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setPhotoUri(null)
                    .build()
                
                user.updateProfile(profileUpdates).await()
                
                // Actualizar el documento del usuario en Firestore
                userRepository.updateUserPhoto("")
                
                loadUserProfile()
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Error al eliminar la foto: ${e.message}")
            }
        }
    }

    private fun selectAvatar(avatar: Avatar) {
        viewModelScope.launch {
            try {
                _uiState.value = ProfileUiState.Loading
                val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
                
                userRepository.updateAvatar(userId, avatar)
                
                // Si hay una foto de perfil, intentar eliminarla
                val user = auth.currentUser
                if (user?.photoUrl != null) {
                    try {
                        // Intentar eliminar la foto de Firebase Storage
                        val photoRef = storage.reference
                            .child(PROFILE_PHOTOS_PATH)
                            .child(user.uid)
                            .child("profile.jpg")
                        photoRef.delete().await()
                    } catch (e: Exception) {
                        // Ignorar errores al eliminar la foto, ya que podría no existir
                        println("DEBUG: Error al eliminar foto (ignorado): ${e.message}")
                    }
                    
                    // Actualizar el perfil del usuario
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setPhotoUri(null)
                        .build()
                    
                    user.updateProfile(profileUpdates).await()
                    
                    // Actualizar el documento del usuario en Firestore
                    userRepository.updateUserPhoto("")
                }
                
                loadUserProfile()
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Error al actualizar el avatar: ${e.message}")
            }
        }
    }

    private suspend fun compressImage(uri: Uri): Uri {
        // Implementar compresión de imagen aquí
        // Por ahora retornamos la URI original
        return uri
    }

    // Eliminado: ya no es necesario exponer la URI directamente

    fun updateAvatar(avatarId: Int, emoji: String) {
        viewModelScope.launch {
            try {
                _uiState.value = ProfileUiState.Loading
                
                // Actualizar solo los campos de avatar en Firestore
                userRepository.updateUserAvatar(avatarId, emoji)
                
                loadUserProfile()
                _uiState.value = ProfileUiState.Success(_user.value ?: throw Exception("Usuario no encontrado"))
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Error al actualizar el avatar: ${e.message}")
            }
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            try {
                prefs.edit().putBoolean("biometric_enabled", enabled).apply()
                
                when (val currentState = _uiState.value) {
                    is ProfileUiState.Success -> {
                        _uiState.value = currentState.copy(
                            biometricEnabled = enabled,
                            message = if (enabled) "Autenticación biométrica activada" else "Autenticación biométrica desactivada"
                        )
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Error al cambiar la configuración biométrica: ${e.message}")
            }
        }
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser ?: throw Exception("Usuario no autenticado")
                
                // Reautenticar al usuario
                val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                user.reauthenticate(credential).await()
                
                // Cambiar la contraseña
                user.updatePassword(newPassword).await()
                
                _uiState.update { currentState ->
                    when (currentState) {
                        is ProfileUiState.Success -> currentState.copy(
                            message = "Contraseña actualizada correctamente"
                        )
                        else -> currentState
                    }
                }
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _uiState.update { currentState ->
                    when (currentState) {
                        is ProfileUiState.Success -> currentState.copy(
                            message = "Contraseña actual incorrecta"
                        )
                        else -> ProfileUiState.Error("Contraseña actual incorrecta")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    when (currentState) {
                        is ProfileUiState.Success -> currentState.copy(
                            message = "Error al cambiar la contraseña: ${e.message}"
                        )
                        else -> ProfileUiState.Error("Error al cambiar la contraseña: ${e.message}")
                    }
                }
            }
        }
    }

    private fun deleteAccount(password: String) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser ?: throw Exception("Usuario no autenticado")
                
                // Reautenticar al usuario
                val credential = EmailAuthProvider.getCredential(user.email!!, password)
                user.reauthenticate(credential).await()
                
                // Eliminar todos los datos del usuario
                recordRepository.deleteAllUserRecords()
                scooterRepository.deleteAllUserScooters()
                
                // Eliminar el documento del usuario en Firestore
                val userId = user.uid
                firestore.collection("users").document(userId).delete().await()
                
                // Eliminar la cuenta de autenticación
                user.delete().await()
                
                _uiState.value = ProfileUiState.Success(
                    user = User(),
                    message = "Cuenta eliminada correctamente"
                )
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _uiState.update { currentState ->
                    when (currentState) {
                        is ProfileUiState.Success -> currentState.copy(
                            message = "Contraseña incorrecta"
                        )
                        else -> ProfileUiState.Error("Contraseña incorrecta")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    when (currentState) {
                        is ProfileUiState.Success -> currentState.copy(
                            message = "Error al eliminar la cuenta: ${e.message}"
                        )
                        else -> ProfileUiState.Error("Error al eliminar la cuenta: ${e.message}")
                    }
                }
            }
        }
    }

    fun exportAllRecords(context: Context) {
        viewModelScope.launch {
            try {
                val records = recordRepository.getAllRecords()
                
                if (records.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No hay registros para exportar", Toast.LENGTH_SHORT).show()
                    }
                    _uiState.update { currentState ->
                        when (currentState) {
                            is ProfileUiState.Success -> currentState.copy(
                                message = "No hay registros para exportar"
                            )
                            else -> currentState
                        }
                    }
                    return@launch
                }
                
                // Crear archivo Excel
                val timestamp = System.currentTimeMillis()
                val tempFile = File(context.cacheDir, "todos_los_registros_$timestamp.xls")
                
                val workbook = jxl.Workbook.createWorkbook(tempFile)
                val sheet = workbook.createSheet("Todos los Registros", 0)
                
                // Formato para encabezados
                val headerFont = jxl.write.WritableFont(jxl.write.WritableFont.ARIAL, 10, jxl.write.WritableFont.BOLD)
                val headerFormat = jxl.write.WritableCellFormat(headerFont)
                headerFormat.setAlignment(jxl.format.Alignment.CENTRE)
                
                // Formato para números
                val numberFormat = jxl.write.WritableCellFormat(jxl.write.NumberFormat("#,##0.00"))
                
                // Encabezados
                sheet.addCell(jxl.write.Label(0, 0, "Vehículo", headerFormat))
                sheet.addCell(jxl.write.Label(1, 0, "Fecha", headerFormat))
                sheet.addCell(jxl.write.Label(2, 0, "Kilometraje", headerFormat))
                sheet.addCell(jxl.write.Label(3, 0, "Diferencia", headerFormat))
                
                // Añadir registros
                records.sortedByDescending { it.fecha }.forEachIndexed { index, record ->
                    val rowNum = index + 1
                    sheet.addCell(jxl.write.Label(0, rowNum, record.patinete))
                    sheet.addCell(jxl.write.Label(1, rowNum, record.fecha))
                    sheet.addCell(jxl.write.Number(2, rowNum, record.kilometraje, numberFormat))
                    sheet.addCell(jxl.write.Number(3, rowNum, record.diferencia, numberFormat))
                }
                
                // Ajustar anchos de columna
                sheet.setColumnView(0, 20)
                sheet.setColumnView(1, 15)
                sheet.setColumnView(2, 12)
                sheet.setColumnView(3, 12)
                
                workbook.write()
                workbook.close()
                
                // Guardar en la carpeta de Descargas usando MainActivity
                val activity = if (context is Activity) context else (context as? android.content.ContextWrapper)?.baseContext as? Activity
                val mainActivity = activity as? com.example.patineta.MainActivity
                
                if (mainActivity != null) {
                    mainActivity.exportToDownloads(tempFile)
                    // MainActivity mostrará las notificaciones de progreso y resultado
                } else {
                    // Si no podemos acceder a MainActivity, intentar compartir como fallback
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Preparando archivo para compartir...", Toast.LENGTH_SHORT).show()
                    }
                    
                    val fileUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        tempFile
                    )
                    
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.ms-excel"
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    context.startActivity(Intent.createChooser(shareIntent, "Exportar registros").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
                }
                _uiState.update { currentState ->
                    when (currentState) {
                        is ProfileUiState.Success -> currentState.copy(
                            message = "Error al exportar: ${e.message}"
                        )
                        else -> ProfileUiState.Error("Error al exportar registros: ${e.message}")
                    }
                }
                Log.e("ProfileViewModel", "Error al exportar registros", e)
            }
        }
    }

    companion object {
        private const val PROFILE_PHOTOS_PATH = "profile_photos"
    }
}
