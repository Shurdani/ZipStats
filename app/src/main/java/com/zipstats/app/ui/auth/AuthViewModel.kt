package com.zipstats.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zipstats.app.repository.AuthRepository
import com.zipstats.app.repository.EmailNotVerifiedException
import com.zipstats.app.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Verificar el estado de autenticación al iniciar
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                
                // Verificar si ya hay un usuario autenticado
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    // Usuario ya autenticado
                    _authState.value = AuthState.Success
                } else {
                    // Intentar auto-login con credenciales guardadas
                    try {
                        val result = authRepository.autoSignIn()
                        if (result.isSuccess) {
                            _authState.value = AuthState.Success
                        } else {
                            _authState.value = AuthState.Initial
                        }
                    } catch (e: Exception) {
                        _authState.value = AuthState.Initial
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Initial
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val result = authRepository.login(email, password)
                if (result.isSuccess) {
                    _authState.value = AuthState.Success
                } else {
                    val exception = result.exceptionOrNull()
                    when (exception) {
                        is EmailNotVerifiedException -> {
                            _authState.value = AuthState.EmailNotVerified
                        }
                        else -> {
                            _authState.value = AuthState.Error(exception?.message ?: "Error al iniciar sesión")
                        }
                    }
                }
            } catch (e: EmailNotVerifiedException) {
                _authState.value = AuthState.EmailNotVerified
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error al iniciar sesión")
            }
        }
    }

    fun register(email: String, password: String, name: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                // Crear usuario en Authentication
                val result = authRepository.register(email, password)
                if (result.isSuccess) {
                    // Crear documento de usuario en Firestore
                    userRepository.createUser(email, name)
                    _authState.value = AuthState.EmailVerificationSent
                } else {
                    _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Error al registrar usuario")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error al registrar usuario")
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                authRepository.resetPassword(email)
                _authState.value = AuthState.ResetEmailSent
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error al restablecer la contraseña")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                authRepository.logout()
                _authState.value = AuthState.Initial
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error al cerrar sesión")
            }
        }
    }

    fun sendEmailVerification() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val result = authRepository.sendEmailVerification()
                if (result.isSuccess) {
                    _authState.value = AuthState.EmailVerificationSent
                } else {
                    _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Error al enviar verificación")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error al enviar verificación")
            }
        }
    }

    fun checkEmailVerification() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                authRepository.reloadUser()
                if (authRepository.isEmailVerified()) {
                    _authState.value = AuthState.Success
                } else {
                    _authState.value = AuthState.EmailNotVerified
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error al verificar email")
            }
        }
    }
    
    fun getCurrentUserEmail(): String? {
        return authRepository.getCurrentUserEmail()
    }
}

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    object ResetEmailSent : AuthState()
    object EmailVerificationSent : AuthState()
    object EmailNotVerified : AuthState()
    data class Error(val message: String) : AuthState()
} 