@file:Suppress("DEPRECATION")

package com.zipstats.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zipstats.app.R
import com.zipstats.app.ui.components.ZipStatsText

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

    // Personalizar colores de campos
    val customColors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,        // Usa el gris que definimos
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, // Usa el gris plata legible
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        focusedLabelColor = MaterialTheme.colorScheme.primary
    )

    // Funciones de validación (Mantenemos tu lógica)
    fun validateEmail(email: String): String {
        return when {
            email.isEmpty() -> "" // No error si vacío al principio
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Formato de email inválido"
            email.length > 254 -> "El email es demasiado largo"
            else -> ""
        }
    }

    fun validatePassword(password: String): String {
        return when {
            password.isEmpty() -> ""
            password.length < 8 -> "La contraseña debe tener al menos 8 caracteres"
            else -> ""
        }
    }

    fun validateName(name: String): String {
        return when {
            name.isEmpty() -> ""
            name.length < 2 -> "El nombre es muy corto"
            else -> ""
        }
    }

    // Validar en tiempo real
    LaunchedEffect(email) { if (email.isNotEmpty()) emailError = validateEmail(email) }
    LaunchedEffect(password) { if (password.isNotEmpty()) passwordError = validatePassword(password) }
    LaunchedEffect(name) { if (name.isNotEmpty()) nameError = validateName(name) }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> onRegisterSuccess()
            is AuthState.Error -> {
                showError = true
                errorMessage = (authState as AuthState.Error).message
            }
            else -> showError = false
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Fondo decorativo (igual que en Login)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 1. LOGO / BRANDING
                Surface(
                    shape = CircleShape,
                    color = Color.White, // Fondo blanco para que el logo PNG no se corte feo
                    modifier = Modifier.size(100.dp),
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_app),
                            contentDescription = "Logo ZipStats",
                            modifier = Modifier.size(90.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                ZipStatsText(
                    text = "Crear Cuenta",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                ZipStatsText(
                    text = "Únete a la comunidad ZipStats",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 2. FORMULARIO

                // Nombre
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { ZipStatsText("Nombre") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError.isNotEmpty() && name.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = customColors
                )
                if (nameError.isNotEmpty() && name.isNotEmpty()) {
                    ZipStatsText(nameError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { ZipStatsText("Correo electrónico") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = emailError.isNotEmpty() && email.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    colors = customColors
                )
                if (emailError.isNotEmpty() && email.isNotEmpty()) {
                    ZipStatsText(emailError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Contraseña
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { ZipStatsText("Contraseña") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = passwordError.isNotEmpty() && password.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    colors = customColors
                )

                // Indicador de requisitos de contraseña (Mejorado visualmente)
                if (password.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            PasswordRequirement(text = "Mínimo 8 caracteres", isValid = password.length >= 8)
                            PasswordRequirement(text = "Al menos una mayúscula", isValid = password.any { it.isUpperCase() })
                            PasswordRequirement(text = "Al menos una minúscula", isValid = password.any { it.isLowerCase() })
                            PasswordRequirement(text = "Al menos un número", isValid = password.any { it.isDigit() })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Confirmar Contraseña
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { ZipStatsText("Confirmar contraseña") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    colors = customColors
                )

                if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                    ZipStatsText("Las contraseñas no coinciden", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp))
                }

                if (showError) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ZipStatsText(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 3. BOTÓN REGISTRO
                Button(
                    onClick = {
                        // Validar todo antes de proceder (Validación estricta final)
                        val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                        val isPasswordValid = password.length >= 8 &&
                                password.any { it.isUpperCase() } &&
                                password.any { it.isLowerCase() } &&
                                password.any { it.isDigit() }
                        val isNameValid = name.length >= 2

                        if (isEmailValid && isPasswordValid && isNameValid && password == confirmPassword) {
                            viewModel.register(email, password, name)
                        } else {
                            showError = true
                            errorMessage = "Por favor, cumple todos los requisitos de seguridad"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = name.isNotEmpty() &&
                            email.isNotEmpty() &&
                            password.isNotEmpty() &&
                            confirmPassword.isNotEmpty() &&
                            password == confirmPassword &&
                            authState !is AuthState.Loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ZipStatsText("Registrarse", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    ZipStatsText(
                        text = "¿Ya tienes cuenta?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { navController.navigateUp() }) {
                        ZipStatsText(
                            text = "Inicia sesión",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isValid) Icons.Default.Check else Icons.Default.Info,
            contentDescription = null,
            // Color verde éxito si válido, gris si no
            tint = if (isValid) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        ZipStatsText(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isValid) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = if (isValid) FontWeight.Bold else FontWeight.Normal
        )
    }
}