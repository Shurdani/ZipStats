package com.zipstats.app.repository

import android.content.SharedPreferences
import android.net.Uri
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

class EmailNotVerifiedException(message: String) : Exception(message)

class AccountMergeRequiredException(
    val email: String,
    message: String
) : Exception(message)

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
        private const val KEY_LOGOUT_TIMESTAMP = "logout_timestamp"
        private const val DAYS_UNTIL_SESSION_EXPIRY = 30L
        private const val LOGOUT_GRACE_PERIOD_MS = 5000L // 5 segundos después del logout, no hacer autologin
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
            
            // Guardar credenciales y timestamp
            sharedPreferences.edit().apply {
                putString(KEY_USER_EMAIL, email)
                putString(KEY_USER_PASSWORD, password)
                putBoolean(KEY_IS_LOGGED_IN, true)
                putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
                remove(KEY_LOGOUT_TIMESTAMP) // Limpiar timestamp de logout al hacer login
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
            
            // Guardar credenciales inmediatamente después del registro
            sharedPreferences.edit().apply {
                putString(KEY_USER_EMAIL, email)
                putString(KEY_USER_PASSWORD, password)
                putBoolean(KEY_IS_LOGGED_IN, true)
                putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
                remove(KEY_LOGOUT_TIMESTAMP) // Limpiar timestamp de logout al registrarse
                apply()
            }
            
            android.util.Log.d("AuthRepository", "Usuario creado exitosamente: $email")
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
        // Guardar timestamp del logout para prevenir autologin inmediato
        val logoutTimestamp = System.currentTimeMillis()
        
        auth.signOut()
        // Limpiar credenciales guardadas
        sharedPreferences.edit().apply {
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_PASSWORD)
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_LAST_LOGIN)
            putLong(KEY_LOGOUT_TIMESTAMP, logoutTimestamp)
            apply()
        }
    }

    suspend fun autoSignIn(): Result<FirebaseUser> {
        // Verificar si se hizo logout recientemente (dentro del período de gracia)
        val logoutTimestamp = sharedPreferences.getLong(KEY_LOGOUT_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()
        val timeSinceLogout = currentTime - logoutTimestamp
        
        if (logoutTimestamp > 0 && timeSinceLogout < LOGOUT_GRACE_PERIOD_MS) {
            // Se hizo logout recientemente, no intentar autologin
            android.util.Log.d("AuthRepository", "Logout reciente detectado (${timeSinceLogout}ms). Evitando autologin.")
            return Result.failure(Exception("Logout reciente"))
        }
        
        // Limpiar el timestamp de logout si ha pasado el período de gracia
        if (logoutTimestamp > 0 && timeSinceLogout >= LOGOUT_GRACE_PERIOD_MS) {
            sharedPreferences.edit().remove(KEY_LOGOUT_TIMESTAMP).apply()
        }
        
        // Si ya hay un usuario autenticado y está marcado como logged in, retornarlo
        val currentUser = auth.currentUser
        if (isLoggedIn && currentUser != null) {
            return Result.success(currentUser)
        }

        // Si hay un usuario autenticado pero no está marcado como logged in, verificar si es válido
        if (currentUser != null) {
            val lastLogin = sharedPreferences.getLong(KEY_LAST_LOGIN, 0)
            val currentTime = System.currentTimeMillis()
            val isSessionValid = (currentTime - lastLogin) < DAYS_UNTIL_SESSION_EXPIRY * 24 * 60 * 60 * 1000
            
            if (isSessionValid) {
                // Actualizar timestamp de último login
                sharedPreferences.edit()
                    .putLong(KEY_LAST_LOGIN, currentTime)
                    .putBoolean(KEY_IS_LOGGED_IN, true)
                    .remove(KEY_LOGOUT_TIMESTAMP) // Limpiar timestamp de logout
                    .apply()
                return Result.success(currentUser)
            } else {
                // Sesión expirada, hacer logout
                logout()
                return Result.failure(Exception("La sesión ha expirado"))
            }
        }

        // Intentar auto-login solo si hay email y password (método tradicional)
        val email = sharedPreferences.getString(KEY_USER_EMAIL, null)
        val password = sharedPreferences.getString(KEY_USER_PASSWORD, null)
        val lastLogin = sharedPreferences.getLong(KEY_LAST_LOGIN, 0)
        val isSessionValid = (currentTime - lastLogin) < DAYS_UNTIL_SESSION_EXPIRY * 24 * 60 * 60 * 1000

        return if (email != null && password != null && isSessionValid) {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                // Actualizar timestamp de último login
                sharedPreferences.edit()
                    .putLong(KEY_LAST_LOGIN, currentTime)
                    .putBoolean(KEY_IS_LOGGED_IN, true)
                    .remove(KEY_LOGOUT_TIMESTAMP) // Limpiar timestamp de logout
                    .apply()
                Result.success(result.user!!)
            } catch (e: Exception) {
                logout() // Limpiar credenciales en caso de error
                Result.failure(e)
            }
        } else {
            logout() // Limpiar credenciales si la sesión expiró o no hay credenciales
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

    suspend fun signInWithGoogle(idToken: String, email: String? = null): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            
            // Guardar estado de login (para Google no guardamos email/password)
            sharedPreferences.edit().apply {
                putBoolean(KEY_IS_LOGGED_IN, true)
                putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
                remove(KEY_LOGOUT_TIMESTAMP) // Limpiar timestamp de logout
                // Guardar el email si existe para futuras referencias
                result.user?.email?.let {
                    putString(KEY_USER_EMAIL, it)
                }
                // No guardamos password para Google Sign In
                remove(KEY_USER_PASSWORD)
                apply()
            }
            
            Result.success(Unit)
        } catch (e: FirebaseAuthException) {
            // Verificar si el error es "email already in use"
            val errorCode = e.errorCode
            if (errorCode == "ERROR_EMAIL_ALREADY_IN_USE" || errorCode == "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL") {
                // El email ya está registrado con otro método de autenticación
                // Necesitamos obtener el email del ID token de Google
                // El email debería venir en el mensaje de error o podemos decodificarlo del token
                try {
                    // Extraer el email del mensaje de error si es posible
                    val errorMessage = e.message ?: ""
                    val emailRegex = Regex("""[\w\.-]+@[\w\.-]+\.\w+""")
                    val emailMatch = emailRegex.find(errorMessage)
                    val extractedEmail = emailMatch?.value
                    
                    // Usar el email pasado como parámetro, el extraído del error, o el guardado
                    val finalEmail = email ?: extractedEmail ?: sharedPreferences.getString(KEY_USER_EMAIL, null)
                    
                    if (finalEmail != null) {
                        // Intentar hacer login con email/password si tenemos las credenciales guardadas
                        val savedPassword = sharedPreferences.getString(KEY_USER_PASSWORD, null)
                        
                        if (savedPassword != null) {
                            // Tenemos las credenciales guardadas, hacer login y vincular
                            try {
                                val loginResult = auth.signInWithEmailAndPassword(finalEmail, savedPassword).await()
                                // Ahora vincular la credencial de Google
                                val googleCredential = GoogleAuthProvider.getCredential(idToken, null)
                                loginResult.user?.linkWithCredential(googleCredential)?.await()
                                
                                // Guardar estado de login
                                sharedPreferences.edit().apply {
                                    putBoolean(KEY_IS_LOGGED_IN, true)
                                    putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
                                    remove(KEY_LOGOUT_TIMESTAMP) // Limpiar timestamp de logout
                                    putString(KEY_USER_EMAIL, finalEmail)
                                    // Mantener password guardada para futuros logins
                                    apply()
                                }
                                
                                Result.success(Unit)
                            } catch (linkError: Exception) {
                                logout()
                                Result.failure(Exception("Error al vincular cuenta de Google. Por favor, intenta iniciar sesión con email y contraseña primero."))
                            }
                        } else {
                            // No tenemos las credenciales guardadas, necesitamos que el usuario ingrese su contraseña
                            logout()
                            Result.failure(AccountMergeRequiredException(finalEmail, "Ya existe una cuenta con este email. Por favor, ingresa tu contraseña para vincular tu cuenta de Google."))
                        }
                    } else {
                        logout()
                        Result.failure(Exception("Ya existe una cuenta con este email usando otro método de autenticación."))
                    }
                } catch (mergeError: Exception) {
                    logout()
                    Result.failure(Exception("Error al fusionar cuentas: ${mergeError.message}"))
                }
            } else {
                // Otro error de Firebase Auth
                logout()
                Result.failure(Exception(e.message ?: "Error al iniciar sesión con Google"))
            }
        } catch (e: FirebaseNetworkException) {
            logout()
            Result.failure(Exception("Error de conexión. Por favor, verifica tu conexión a internet."))
        } catch (e: Exception) {
            logout()
            Result.failure(Exception(e.message ?: "Error al iniciar sesión con Google"))
        }
    }
    
    /**
     * Vincula una cuenta de Google a una cuenta existente de email/password
     * El usuario debe estar autenticado previamente con email/password
     */
    suspend fun linkGoogleAccount(idToken: String, password: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("No hay un usuario autenticado"))
            }
            
            val googleCredential = GoogleAuthProvider.getCredential(idToken, null)
            currentUser.linkWithCredential(googleCredential).await()
            
            // Actualizar estado
            val email = currentUser.email
            sharedPreferences.edit().apply {
                putBoolean(KEY_IS_LOGGED_IN, true)
                putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
                remove(KEY_LOGOUT_TIMESTAMP) // Limpiar timestamp de logout
                // Mantener email y password guardados para que puedan usar ambos métodos
                if (email != null) {
                    putString(KEY_USER_EMAIL, email)
                    putString(KEY_USER_PASSWORD, password)
                }
                apply()
            }
            
            Result.success(Unit)
        } catch (e: FirebaseNetworkException) {
            Result.failure(Exception("Error de conexión. Por favor, verifica tu conexión a internet."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Error al vincular cuenta de Google"))
        }
    }
} 