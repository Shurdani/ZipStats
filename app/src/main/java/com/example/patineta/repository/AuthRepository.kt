package com.example.patineta.repository

import android.content.SharedPreferences
import android.net.Uri
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

class EmailNotVerifiedException(message: String) : Exception(message)

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PASSWORD = "user_password"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_LAST_LOGIN = "last_login"
        private const val DAYS_UNTIL_SESSION_EXPIRY = 30L
    }

    init {
        // No intentamos auto-login aquí, lo dejamos para autoSignIn
        // Solo limpiar credenciales si no hay usuario autenticado en Firebase
        if (auth.currentUser == null) {
            // Limpiar solo las credenciales guardadas, no llamar a logout completo
            sharedPreferences.edit().apply {
                putBoolean(KEY_IS_LOGGED_IN, false)
                apply()
            }
        }
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isLoggedIn: Boolean
        get() = auth.currentUser != null && sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            
            // Verificar si el email está verificado
            if (!result.user?.isEmailVerified!!) {
                logout() // Limpiar credenciales si no está verificado
                throw EmailNotVerifiedException("Por favor, verifica tu correo electrónico antes de iniciar sesión.")
            }
            
            // Guardar credenciales y timestamp
            sharedPreferences.edit().apply {
                putString(KEY_USER_EMAIL, email)
                putString(KEY_USER_PASSWORD, password)
                putBoolean(KEY_IS_LOGGED_IN, true)
                putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
                apply()
            }
            
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            logout()
            Result.failure(Exception("Correo electrónico o contraseña incorrectos."))
        } catch (e: FirebaseAuthInvalidUserException) {
            logout()
            Result.failure(Exception("No existe una cuenta con este correo electrónico."))
        } catch (e: FirebaseNetworkException) {
            logout()
            Result.failure(Exception("Error de conexión. Por favor, verifica tu conexión a internet."))
        } catch (e: Exception) {
            logout() // Limpiar credenciales en caso de error
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String): Result<Unit> {
        return try {
            android.util.Log.d("AuthRepository", "Iniciando registro para: $email")
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            
            // Enviar email de verificación (método simple que funciona)
            android.util.Log.d("AuthRepository", "Usuario creado, enviando email de verificación...")
            result.user?.sendEmailVerification()?.await()
            android.util.Log.d("AuthRepository", "Email de verificación enviado exitosamente a: $email")
            
            // NO guardar credenciales hasta que el email esté verificado
            // El usuario tendrá que iniciar sesión después de verificar su email
            
            Result.success(Unit)
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(Exception("Ya existe una cuenta con este correo electrónico."))
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(Exception("La contraseña es demasiado débil. Debe tener al menos 6 caracteres."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(Exception("Error de conexión. Por favor, verifica tu conexión a internet."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Error al crear la cuenta"))
        }
    }

    fun logout() {
        auth.signOut()
        // Limpiar credenciales guardadas
        sharedPreferences.edit().apply {
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_PASSWORD)
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_LAST_LOGIN)
            apply()
        }
    }

    suspend fun autoSignIn(): Result<FirebaseUser> {
        // Si ya hay un usuario autenticado y está marcado como logged in, retornarlo
        if (isLoggedIn) {
            auth.currentUser?.let {
                return Result.success(it)
            }
        }

        val email = sharedPreferences.getString(KEY_USER_EMAIL, null)
        val password = sharedPreferences.getString(KEY_USER_PASSWORD, null)
        val lastLogin = sharedPreferences.getLong(KEY_LAST_LOGIN, 0)
        val currentTime = System.currentTimeMillis()
        val isSessionValid = (currentTime - lastLogin) < DAYS_UNTIL_SESSION_EXPIRY * 24 * 60 * 60 * 1000

        return if (email != null && password != null && isSessionValid) {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                // Actualizar timestamp de último login
                sharedPreferences.edit()
                    .putLong(KEY_LAST_LOGIN, currentTime)
                    .putBoolean(KEY_IS_LOGGED_IN, true)
                    .apply()
                Result.success(result.user!!)
            } catch (e: Exception) {
                logout() // Limpiar credenciales en caso de error
                Result.failure(e)
            }
        } else {
            logout() // Limpiar credenciales si la sesión expiró
            Result.failure(Exception("No hay credenciales guardadas o la sesión ha expirado"))
        }
    }

    suspend fun resetPassword(email: String) {
        try {
            auth.sendPasswordResetEmail(email).await()
        } catch (e: FirebaseNetworkException) {
            throw Exception("Error de conexión. Por favor, verifica tu conexión a internet.")
        } catch (e: Exception) {
            throw Exception(e.message ?: "Error al restablecer la contraseña")
        }
    }

    fun updateProfile(photoUrl: String?) {
        auth.currentUser?.updateProfile(
            UserProfileChangeRequest.Builder()
                .setPhotoUri(photoUrl?.let { Uri.parse(it) })
                .build()
        )
    }

    suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            val user = auth.currentUser
            android.util.Log.d("AuthRepository", "sendEmailVerification - Usuario actual: ${user?.email}, Verificado: ${user?.isEmailVerified}")
            
            if (user != null && !user.isEmailVerified) {
                // Usar el método simple (igual que resetPassword que sí funciona)
                user.sendEmailVerification().await()
                android.util.Log.d("AuthRepository", "Email de verificación enviado exitosamente a: ${user.email}")
                Result.success(Unit)
            } else {
                android.util.Log.e("AuthRepository", "No se puede enviar email - Usuario ya verificado o no autenticado")
                Result.failure(Exception("El usuario ya está verificado o no hay usuario autenticado"))
            }
        } catch (e: FirebaseNetworkException) {
            android.util.Log.e("AuthRepository", "Error de red al enviar email de verificación", e)
            Result.failure(Exception("Error de conexión. Por favor, verifica tu conexión a internet."))
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Error al enviar email de verificación", e)
            Result.failure(Exception("Error al enviar el email de verificación: ${e.message}"))
        }
    }

    fun isEmailVerified(): Boolean {
        return auth.currentUser?.isEmailVerified ?: false
    }
    
    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }

    suspend fun reloadUser(): Result<Unit> {
        return try {
            auth.currentUser?.reload()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 