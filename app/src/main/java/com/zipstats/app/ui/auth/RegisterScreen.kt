@file:Suppress("DEPRECATION")

package com.zipstats.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    navController: NavController,
    onRegisterSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Estados de validación
    var emailError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf("") }
    
    // Estados de visibilidad de contraseñas
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val authState by viewModel.authState.collectAsState()

    // Personalizar colores de error
    val customErrorColors = OutlinedTextFieldDefaults.colors(
        cursorColor = MaterialTheme.colorScheme.primary
    )

    // Función de validación de email
    fun validateEmail(email: String): String {
        return when {
            email.isEmpty() -> "El email es obligatorio"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Formato de email inválido"
            email.length > 254 -> "El email es demasiado largo"
            !email.contains("@") -> "El email debe contener @"
            !email.contains(".") -> "El email debe contener un dominio válido"
            email.startsWith("@") || email.endsWith("@") -> "El email no puede empezar o terminar con @"
            email.startsWith(".") || email.endsWith(".") -> "El email no puede empezar o terminar con ."
            email.contains("..") -> "El email no puede contener puntos consecutivos"
            email.contains("@.") || email.contains(".@") -> "El email no puede contener @. o .@"
            else -> ""
        }
    }

    // Función de validación de contraseña
    fun validatePassword(password: String): String {
        return when {
            password.isEmpty() -> "La contraseña es obligatoria"
            password.length < 8 -> "La contraseña debe tener al menos 8 caracteres"
            password.length > 128 -> "La contraseña es demasiado larga"
            !password.any { it.isUpperCase() } -> "La contraseña debe contener al menos una MAYÚSCULA"
            !password.any { it.isLowerCase() } -> "La contraseña debe contener al menos una minúscula"
            !password.any { it.isDigit() } -> "La contraseña debe contener al menos un número"
            else -> ""
        }
    }

    // Función de validación de nombre
    fun validateName(name: String): String {
        return when {
            name.isEmpty() -> "El nombre es obligatorio"
            name.length < 2 -> "El nombre debe tener al menos 2 caracteres"
            name.length > 50 -> "El nombre es demasiado largo"
            name.any { it.isDigit() } -> "El nombre no puede contener números"
            name.any { !it.isLetterOrDigit() && it != ' ' && it != '-' && it != '\'' } -> "El nombre contiene caracteres no válidos"
            else -> ""
        }
    }

    // Validar en tiempo real
    LaunchedEffect(email) {
        emailError = validateEmail(email)
    }

    LaunchedEffect(password) {
        passwordError = validatePassword(password)
    }

    LaunchedEffect(name) {
        nameError = validateName(name)
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> {
                onRegisterSuccess()
            }
            is AuthState.Error -> {
                showError = true
                errorMessage = (authState as AuthState.Error).message
            }
            else -> {
                showError = false
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Crear Cuenta",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = nameError.isNotEmpty(),
                supportingText = if (nameError.isNotEmpty()) { { Text(nameError) } } else null,
                colors = customErrorColors
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo electrónico") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = emailError.isNotEmpty(),
                supportingText = if (emailError.isNotEmpty()) { { Text(emailError) } } else null,
                colors = customErrorColors
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = passwordError.isNotEmpty(),
                supportingText = if (passwordError.isNotEmpty()) { { Text(passwordError) } } else null,
                colors = customErrorColors,
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            // Indicador de requisitos de contraseña
            if (password.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    PasswordRequirement(
                        text = "Al menos 8 caracteres",
                        isValid = password.length >= 8
                    )
                    PasswordRequirement(
                        text = "Al menos una MAYÚSCULA",
                        isValid = password.any { it.isUpperCase() }
                    )
                    PasswordRequirement(
                        text = "Al menos una minúscula",
                        isValid = password.any { it.isLowerCase() }
                    )
                    PasswordRequirement(
                        text = "Al menos un número",
                        isValid = password.any { it.isDigit() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirmar contraseña") },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                supportingText = if (confirmPassword.isNotEmpty() && password != confirmPassword) { 
                    { Text("Las contraseñas no coinciden") } 
                } else null,
                colors = customErrorColors,
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (confirmPasswordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            if (showError) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    // Validar todo antes de proceder
                    val finalEmailError = validateEmail(email)
                    val finalPasswordError = validatePassword(password)
                    val finalNameError = validateName(name)
                    
                    if (finalEmailError.isEmpty() && finalPasswordError.isEmpty() && 
                        finalNameError.isEmpty() && password == confirmPassword) {
                        viewModel.register(email, password, name)
                    } else {
                        showError = true
                        errorMessage = "Por favor, corrige los errores en el formulario"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotEmpty() && 
                         email.isNotEmpty() && 
                         password.isNotEmpty() && 
                         confirmPassword.isNotEmpty() && 
                         emailError.isEmpty() &&
                         passwordError.isEmpty() &&
                         nameError.isEmpty() &&
                         password == confirmPassword &&
                         authState !is AuthState.Loading
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Registrarse")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = { navController.navigateUp() }
            ) {
                Text("¿Ya tienes cuenta? Inicia sesión")
            }
        }
    }
}

@Composable
private fun PasswordRequirement(
    text: String,
    isValid: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isValid) {
                Icons.Default.Check
            } else {
                Icons.Default.Info
            },
            contentDescription = null,
            tint = if (isValid) {
                androidx.compose.ui.graphics.Color(0xFF4CAF50) // Verde más suave
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            },
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isValid) {
                androidx.compose.ui.graphics.Color(0xFF4CAF50) // Verde más suave
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }
        )
    }
} 