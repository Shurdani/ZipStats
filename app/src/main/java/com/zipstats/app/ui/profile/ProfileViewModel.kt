package com.zipstats.app.ui.profile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zipstats.app.model.Achievement
import com.zipstats.app.model.AchievementLevel
import com.zipstats.app.model.AchievementRequirementType
import com.zipstats.app.model.Avatar
import com.zipstats.app.model.Scooter
import com.zipstats.app.model.User
import com.zipstats.app.model.Record
import com.zipstats.app.model.Repair
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.repository.UserRepository
import com.zipstats.app.repository.RepairRepository
import com.zipstats.app.utils.ExcelExporter
import com.zipstats.app.utils.DateUtils
import com.google.firebase.auth.EmailAuthProvider
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import com.zipstats.app.service.CloudinaryService
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
    private val vehicleRepository: VehicleRepository,
    private val onboardingManager: com.zipstats.app.utils.OnboardingManager,
    private val recordRepository: RecordRepository,
    private val repairRepository: RepairRepository,
    private val routeRepository: com.zipstats.app.repository.RouteRepository,
    private val achievementsService: com.zipstats.app.service.AchievementsService,
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
        observeVehicles()
    }
    
    // Observar cambios en los vehículos y registros para actualizar la UI automáticamente
    private fun observeVehicles() {
        viewModelScope.launch {
            // Combinar los Flows de vehículos y registros para actualizar automáticamente
            combine(
                vehicleRepository.getScooters(),
                recordRepository.getRecords()
            ) { scooters, records ->
                // Calcular kilometraje total para cada patinete
                val scootersWithKm = scooters.map { scooter ->
                    val scooterRecords = records.filter { it.patinete == scooter.nombre }
                    val totalKm = scooterRecords.sumOf { it.diferencia }
                    scooter.copy(kilometrajeActual = totalKm)
                }
                
                // Calcular logros desbloqueados
                val stats = recordRepository.getAchievementStats()
                val unlockedAchievements = calculateUnlockedAchievements(stats)
                
                // Actualizar el estado
                val currentUser = _user.value
                if (currentUser != null) {
                    val currentState = _uiState.value
                    if (currentState is ProfileUiState.Success) {
                        _uiState.value = currentState.copy(
                            scooters = scootersWithKm,
                            unlockedAchievements = unlockedAchievements
                        )
                    }
                }
            }.collect { }
        }
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
                val scooters = vehicleRepository.getScooters().first()
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
                
                // Eliminar rutas del usuario
                routeRepository.deleteAllUserRoutes()
                
                // Eliminar patinetes del usuario
                vehicleRepository.deleteAllUserScooters()
                
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
                // Usar el Flow para obtener la lista más actualizada
                val scooters = vehicleRepository.getScooters().first()
                val records = recordRepository.getRecords().first()
                
                // Calcular kilometraje total para cada patinete
                val scootersWithKm = scooters.map { scooter ->
                    val scooterRecords = records.filter { it.patinete == scooter.nombre }
                    val totalKm = scooterRecords.sumOf { it.diferencia }
                    scooter.copy(kilometrajeActual = totalKm)
                }
                
                _uiState.update { currentState ->
                    when (currentState) {
                        is ProfileUiState.Success -> {
                            val currentUser = _user.value ?: return@update currentState
                            currentState.copy(user = currentUser, scooters = scootersWithKm)
                        }
                        else -> currentState
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading scooters", e)
            }
        }
    }

    fun addScooter(nombre: String, marca: String, modelo: String, fechaCompra: String?, vehicleType: com.zipstats.app.model.VehicleType = com.zipstats.app.model.VehicleType.PATINETE) {
        viewModelScope.launch {
            try {
                // Convertir la fecha del formato de visualización (DD/MM/YYYY) al formato de API (YYYY-MM-DD)
                val fechaFormateada = fechaCompra?.let { DateUtils.formatForApi(DateUtils.parseDisplayDate(it)) }
                
                vehicleRepository.addVehicle(
                    nombre = nombre,
                    marca = marca,
                    modelo = modelo,
                    fechaCompra = fechaFormateada,
                    vehicleType = vehicleType
                )
                
                // No necesitamos llamar a loadUserScooters() porque el Flow
                // se actualizará automáticamente cuando Firestore detecte el cambio
                
                // No necesitamos marcar el onboarding como completado
                // El diálogo simplemente no aparecerá cuando haya vehículos registrados
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

    fun updateScooter(scooterId: String, nombre: String, marca: String, modelo: String, fechaCompra: String?) {
        viewModelScope.launch {
            try {
                // Obtener el scooter actual
                val currentScooter = vehicleRepository.getScooters().first().find { it.id == scooterId }
                    ?: throw Exception("Vehículo no encontrado")
                
                // Convertir la fecha del formato de visualización (DD/MM/YYYY) al formato de API (YYYY-MM-DD)
                val fechaFormateada = fechaCompra?.let { DateUtils.formatForApi(DateUtils.parseDisplayDate(it)) }
                
                // Crear el scooter actualizado manteniendo los campos que no se editan
                val updatedScooter = currentScooter.copy(
                    nombre = nombre,
                    marca = marca,
                    modelo = modelo,
                    fechaCompra = fechaFormateada ?: currentScooter.fechaCompra
                )
                
                vehicleRepository.updateScooter(updatedScooter)
                
                // No necesitamos llamar a loadUserScooters() o loadUserProfile() porque el Flow
                // se actualizará automáticamente cuando Firestore detecte el cambio
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating scooter", e)
                _uiState.update { currentState ->
                    when (currentState) {
                        is ProfileUiState.Success -> {
                            currentState.copy(message = "Error al actualizar el vehículo: ${e.message}")
                        }
                        else -> currentState
                    }
                }
            }
        }
    }

    suspend fun deleteScooter(scooterId: String) {
        try {
            Log.d("ProfileViewModel", "=== INICIO ELIMINACIÓN VEHÍCULO ===")
            Log.d("ProfileViewModel", "ID del vehículo a eliminar: $scooterId")
            
            // Obtener el nombre del vehículo antes de eliminarlo para los registros
            val scooters = vehicleRepository.getScooters().first()
            Log.d("ProfileViewModel", "Vehículos encontrados: ${scooters.size}")
            val scooter = scooters.find { it.id == scooterId }
            
            if (scooter == null) {
                Log.e("ProfileViewModel", "Vehículo no encontrado con ID: $scooterId")
                _uiState.update { currentState ->
                    when (currentState) {
                        is ProfileUiState.Success -> {
                            currentState.copy(message = "Vehículo no encontrado")
                        }
                        else -> currentState
                    }
                }
                return
            }
            
            val scooterName = scooter.nombre
            Log.d("ProfileViewModel", "Nombre del vehículo: $scooterName")
            
            // Eliminar registros del vehículo (continuar incluso si falla)
            try {
                if (scooterName.isNotEmpty()) {
                    Log.d("ProfileViewModel", "Eliminando registros del vehículo...")
                    recordRepository.deleteScooterRecords(scooterName)
                    Log.d("ProfileViewModel", "Registros eliminados correctamente")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error eliminando registros del vehículo", e)
                // Continuar con la eliminación del vehículo
            }
            
            // Eliminar rutas del vehículo (continuar incluso si falla)
            try {
                Log.d("ProfileViewModel", "Eliminando rutas del vehículo...")
                val routesResult = routeRepository.deleteScooterRoutes(scooterId)
                routesResult.fold(
                    onSuccess = { Log.d("ProfileViewModel", "Rutas eliminadas correctamente") },
                    onFailure = { e -> 
                        if (e !is kotlinx.coroutines.CancellationException) {
                            Log.e("ProfileViewModel", "Error eliminando rutas del vehículo", e)
                        }
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Re-lanzar cancelación
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error eliminando rutas del vehículo", e)
                // Continuar con la eliminación del vehículo
            }
            
            // Eliminar el vehículo (esta es la operación principal)
            Log.d("ProfileViewModel", "Eliminando vehículo de Firestore...")
            vehicleRepository.deleteScooter(scooterId)
            Log.d("ProfileViewModel", "Vehículo eliminado de Firestore correctamente")
            
            // Esperar un momento para que Firestore propague el cambio
            kotlinx.coroutines.delay(500)
            
            // Verificar que el vehículo ya no existe
            val remainingScooters = vehicleRepository.getScooters().first()
            val stillExists = remainingScooters.any { it.id == scooterId }
            if (stillExists) {
                Log.w("ProfileViewModel", "El vehículo todavía existe después de la eliminación. Reintentando...")
                // Reintentar la eliminación
                vehicleRepository.deleteScooter(scooterId)
                kotlinx.coroutines.delay(500)
            }
            
            Log.d("ProfileViewModel", "=== ELIMINACIÓN COMPLETADA ===")
            
            // No necesitamos esperar ni recargar manualmente porque el Flow
            // se actualizará automáticamente cuando Firestore detecte el cambio
            // El observador continuo en observeVehicles() actualizará la UI
            
            // Mostrar mensaje de éxito
            _uiState.update { currentState ->
                when (currentState) {
                    is ProfileUiState.Success -> {
                        currentState.copy(message = "Vehículo eliminado correctamente")
                    }
                    else -> currentState
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Si se cancela, simplemente loguear y no mostrar error al usuario
            Log.d("ProfileViewModel", "Eliminación de vehículo cancelada")
            throw e // Re-lanzar para que la cancelación se propague correctamente
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "=== ERROR EN ELIMINACIÓN ===")
            Log.e("ProfileViewModel", "Tipo de error: ${e.javaClass.simpleName}")
            Log.e("ProfileViewModel", "Mensaje: ${e.message}")
            Log.e("ProfileViewModel", "Stack trace:", e)
            _uiState.update { currentState ->
                when (currentState) {
                    is ProfileUiState.Success -> {
                        currentState.copy(message = "Error al eliminar el vehículo: ${e.message}")
                    }
                    else -> currentState
                }
            }
            throw e // Re-lanzar el error para que el caller sepa que falló
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

    fun checkStoragePermission(context: Context): Boolean {
        // Para Android 10+ (API 29+), no necesitamos permisos explícitos para escribir en MediaStore
        // El MediaStore API permite escribir archivos sin permisos especiales
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d("ProfileVM", "Android 10+: Usando MediaStore sin permisos explícitos")
            true
        } else {
            // Android 9 y anteriores - verificar permiso de almacenamiento
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            Log.d("ProfileVM", "Android 9-: WRITE_EXTERNAL_STORAGE = $hasPermission")
            hasPermission
        }
        Log.d("ProfileVM", "checkStoragePermission result: $result")
        return result
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
                routeRepository.deleteAllUserRoutes()
                vehicleRepository.deleteAllUserScooters()
                
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("ProfileVM", "=== INICIO EXPORTACIÓN ===")
                
                // Verificar permisos de almacenamiento usando la misma lógica
                val hasStoragePermission = checkStoragePermission(context)
                Log.d("ProfileVM", "exportAllRecords - hasStoragePermission: $hasStoragePermission")
                
                if (!hasStoragePermission) {
                    Log.d("ProfileVM", "Sin permisos - NO mostrando toast (debe manejarse desde UI)")
                    return@launch
                }
                
                Log.d("ProfileVM", "Obteniendo registros de la base de datos...")
                val records = try {
                    recordRepository.getAllRecords()
                } catch (e: Exception) {
                    Log.e("ProfileVM", "Error al obtener registros: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error al obtener registros: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                
                Log.d("ProfileVM", "Registros obtenidos: ${records.size}")
                
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
                
                Log.d("ProfileVM", "Creando archivo Excel...")
                // Usar el nuevo ExcelExporter para crear archivo completo con estadísticas
                val timestamp = System.currentTimeMillis()
                val fileName = "todos_los_registros_$timestamp.xlsx"
                val result = ExcelExporter.exportAllRecordsWithStats(context, records, fileName)
                Log.d("ProfileVM", "Resultado de creación Excel: ${if (result.isSuccess) "SUCCESS" else "FAILURE"}")
                
                if (result.isFailure) {
                    val error = result.exceptionOrNull() ?: Exception("Error al crear archivo Excel")
                    Log.e("ProfileVM", "Error en creación de Excel: ${error.message}", error)
                    throw error
                }
                
                val tempFile = result.getOrThrow()
                Log.d("ProfileVM", "Archivo temporal creado: ${tempFile.absolutePath}, tamaño: ${tempFile.length()} bytes")
                
                // Guardar en la carpeta de Descargas usando MainActivity
                try {
                    Log.d("ProfileVM", "Intentando obtener MainActivity...")
                    val activity = if (context is Activity) context else (context as? android.content.ContextWrapper)?.baseContext as? Activity
                    val mainActivity = activity as? com.zipstats.app.MainActivity
                    
                    Log.d("ProfileVM", "MainActivity obtenida: ${mainActivity != null}")
                    
                    if (mainActivity != null) {
                        // exportToDownloads debe ser llamado desde el Main thread porque usa lifecycleScope
                        Log.d("ProfileVM", "Llamando a exportToDownloads desde Main thread...")
                        withContext(Dispatchers.Main) {
                            mainActivity.exportToDownloads(tempFile)
                        }
                        Log.d("ProfileVM", "exportToDownloads completado")
                        // MainActivity mostrará las notificaciones de progreso y resultado
                    } else {
                        // Si no podemos acceder a MainActivity, intentar compartir como fallback
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Preparando archivo para compartir...", Toast.LENGTH_SHORT).show()
                            
                            val fileUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                tempFile
                            )
                            
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            
                            context.startActivity(Intent.createChooser(shareIntent, "Exportar registros").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProfileVM", "Error al acceder a MainActivity o compartir archivo: ${e.message}", e)
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileVM", "=== ERROR EN EXPORTACIÓN ===")
                Log.e("ProfileVM", "Tipo de error: ${e.javaClass.simpleName}")
                Log.e("ProfileVM", "Mensaje: ${e.message}")
                Log.e("ProfileVM", "Stack trace completo:", e)
                e.printStackTrace()
                
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
            }
            Log.d("ProfileVM", "=== FIN EXPORTACIÓN ===")
        }
    }

    /**
     * Obtiene la última reparación realizada para un vehículo específico
     */
    suspend fun getLastRepair(vehicleId: String): Repair? {
        return try {
            val repairs = repairRepository.getRepairsForVehicle(vehicleId).first()
            repairs.maxByOrNull { it.date }
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error al obtener última reparación", e)
            null
        }
    }

    /**
     * Obtiene el último registro para un vehículo específico
     */
    suspend fun getLastRecord(vehicleName: String): Record? {
        return try {
            val records = recordRepository.getRecords().first()
            records
                .filter { it.vehicleName == vehicleName }
                .maxByOrNull { it.fecha }
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error al obtener último registro", e)
            null
        }
    }

    /**
     * Calcula el porcentaje de uso de un vehículo respecto al total de la flota
     */
    suspend fun getVehicleUsagePercentage(vehicleName: String): Double {
        return try {
            val records = recordRepository.getRecords().first()
            val allVehicles = records.map { it.vehicleName }.distinct()
            
            if (allVehicles.isEmpty()) return 0.0
            
            val totalKmAllVehicles = records.sumOf { it.diferencia }
            val vehicleKm = records
                .filter { it.vehicleName == vehicleName }
                .sumOf { it.diferencia }
            
            if (totalKmAllVehicles == 0.0) return 0.0
            
            (vehicleKm / totalKmAllVehicles) * 100.0
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error al calcular porcentaje de uso", e)
            0.0
        }
    }

    /**
     * Obtiene estadísticas detalladas de un vehículo específico
     */
    suspend fun getVehicleDetailedStats(vehicleId: String, vehicleName: String): VehicleDetailedStats {
        return try {
            val lastRepair = getLastRepair(vehicleId)
            val lastRecord = getLastRecord(vehicleName)
            val usagePercentage = getVehicleUsagePercentage(vehicleName)
            
            VehicleDetailedStats(
                lastRepair = lastRepair,
                lastRecord = lastRecord,
                usagePercentage = usagePercentage
            )
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error al obtener estadísticas detalladas", e)
            VehicleDetailedStats()
        }
    }

    companion object {
        private const val PROFILE_PHOTOS_PATH = "profile_photos"
    }
}

/**
 * Datos detallados de un vehículo para mostrar en la pantalla de detalles
 */
data class VehicleDetailedStats(
    val lastRepair: Repair? = null,
    val lastRecord: Record? = null,
    val usagePercentage: Double = 0.0
)
