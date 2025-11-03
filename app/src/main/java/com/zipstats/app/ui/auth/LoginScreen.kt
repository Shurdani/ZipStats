package com.zipstats.app.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlin.Suppress

// Web Client ID para Google Sign In (tipo 3 en google-services.json)
private const val DEFAULT_WEB_CLIENT_ID = "811393382396-fi0s13vdo86gabespr7dmb559f202l7d.apps.googleusercontent.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var resetError by remember { mutableStateOf("") }
    var showMergeDialog by remember { mutableStateOf(false) }
    var mergeEmail by remember { mutableStateOf("") }
    var mergePassword by remember { mutableStateOf("") }
    var mergePasswordVisible by remember { mutableStateOf(false) }
    var mergeError by remember { mutableStateOf("") }
    var pendingGoogleIdToken by remember { mutableStateOf<String?>(null) }
    
    // Estados de validación
    var emailError by remember { mutableStateOf("") }

    val authState by viewModel.authState.collectAsState()

    // Configurar Google Sign In
    // Nota: Las clases de Google Sign In están marcadas como deprecadas pero siguen siendo la API oficial
    // y recomendada para autenticación con Firebase. La alternativa One Tap Sign In es más compleja.
    @Suppress("DEPRECATION")
    val googleSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(DEFAULT_WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }

    @Suppress("DEPRECATION")
    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    // Launcher para Google Sign In
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        @Suppress("DEPRECATION")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleGoogleSignInResult(task, viewModel) { idToken ->
            pendingGoogleIdToken = idToken
        }
    }

    // Personalizar colores de error
    val customErrorColors = OutlinedTextFieldDefaults.colors(
        cursorColor = MaterialTheme.colorScheme.primary
    )

    // Función de validación de email
    fun validateEmail(email: String): String {
        return when {
            email.isEmpty() -> "El email es obligatorio"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Formato de email inválido"
            else -> ""
        }
    }

    // Validar en tiempo real
    LaunchedEffect(email) {
        emailError = validateEmail(email)
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                if (showMergeDialog) {
                    // Si el merge fue exitoso, cerrar el diálogo y continuar
                    showMergeDialog = false
                    mergePassword = ""
                    mergeError = ""
                    pendingGoogleIdToken = null
                }
                onLoginSuccess()
            }
            is AuthState.Error -> {
                if (showMergeDialog) {
                    // Si hay un error mientras el diálogo de merge está abierto, mostrar el error en el diálogo
                    mergeError = state.message
                } else {
                    showError = true
                    errorMessage = state.message
                }
            }
            is AuthState.AccountMergeRequired -> {
                mergeEmail = state.email
                showMergeDialog = true
                mergeError = ""
            }
            is AuthState.ResetEmailSent -> {
                showResetDialog = false
                showResetConfirmation = true
                resetEmail = ""
            }
            else -> {
                if (!showMergeDialog) {
                    showError = false
                }
            }
        }
    }

    if (showResetDialog) {
        Dialog(onDismissRequest = { showResetDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Recuperar Contraseña",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Introduce tu correo electrónico y te enviaremos un enlace para restablecer tu contraseña.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { 
                            resetEmail = it
                            resetError = ""
                        },
                        label = { Text("Correo electrónico") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = resetError.isNotEmpty()
                    )
                    
                    if (resetError.isNotEmpty()) {
                        Text(
                            text = resetError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showResetDialog = false }
                        ) {
                            Text("Cancelar")
                        }
                        
                        Button(
                            onClick = {
                                if (resetEmail.isEmpty()) {
                                    resetError = "Por favor, introduce tu correo electrónico"
                                } else {
                                    viewModel.resetPassword(resetEmail)
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Enviar")
                        }
                    }
                }
            }
        }
    }

    // Diálogo para fusionar cuentas
    if (showMergeDialog) {
        Dialog(onDismissRequest = { 
            showMergeDialog = false
            mergePassword = ""
            mergeError = ""
            pendingGoogleIdToken = null
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Vincular cuenta de Google",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Ya existe una cuenta con el email $mergeEmail. Ingresa tu contraseña para vincular tu cuenta de Google.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = mergePassword,
                        onValueChange = { 
                            mergePassword = it
                            mergeError = ""
                        },
                        label = { Text("Contraseña") },
                        visualTransformation = if (mergePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = mergeError.isNotEmpty(),
                        trailingIcon = {
                            IconButton(onClick = { mergePasswordVisible = !mergePasswordVisible }) {
                                Icon(
                                    imageVector = if (mergePasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (mergePasswordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                    
                    if (mergeError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = mergeError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { 
                                showMergeDialog = false
                                mergePassword = ""
                                mergeError = ""
                                pendingGoogleIdToken = null
                            }
                        ) {
                            Text("Cancelar")
                        }
                        
                        Button(
                            onClick = {
                                if (mergePassword.isEmpty()) {
                                    mergeError = "Por favor, ingresa tu contraseña"
                                } else if (pendingGoogleIdToken == null) {
                                    mergeError = "Error: No se pudo obtener el token de Google. Por favor, intenta de nuevo."
                                } else {
                                    viewModel.linkGoogleAccount(pendingGoogleIdToken!!, mergeEmail, mergePassword)
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp),
                            enabled = mergePassword.isNotEmpty() && authState !is AuthState.Loading
                        ) {
                            if (authState is AuthState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Vincular")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirmation) {
        Dialog(onDismissRequest = { showResetConfirmation = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Correo Enviado",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Hemos enviado un enlace a tu correo electrónico para restablecer tu contraseña.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { showResetConfirmation = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Entendido")
                    }
                }
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Iniciar Sesión",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo electrónico") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = emailError.isNotEmpty(),
                colors = customErrorColors
            )

            if (emailError.isNotEmpty()) {
                Text(
                    text = emailError,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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

            if (showError) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("¿Olvidaste tu contraseña?")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (emailError.isEmpty()) {
                        viewModel.login(email, password)
                    } else {
                        showError = true
                        errorMessage = "Por favor, corrige el formato del email"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotEmpty() && password.isNotEmpty() && emailError.isEmpty() && authState !is AuthState.Loading
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Iniciar Sesión")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Separador
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    HorizontalDivider()
                }
                Text(
                    text = "O",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    HorizontalDivider()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón de Google Sign In
            OutlinedButton(
                onClick = {
                    val signInIntent = googleSignInClient.signInIntent
                    googleSignInLauncher.launch(signInIntent)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = authState !is AuthState.Loading
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icono de Google (usando un emoji o un icono de Material)
                    Text(
                        text = "G",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Continuar con Google")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onRegisterClick
            ) {
                Text("¿No tienes cuenta? Regístrate")
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun handleGoogleSignInResult(
    completedTask: Task<GoogleSignInAccount>,
    viewModel: AuthViewModel,
    onIdTokenReady: (String) -> Unit
) {
    try {
        val account = completedTask.getResult(ApiException::class.java)
        val idToken = account?.idToken
        val email = account?.email
        if (idToken != null) {
            onIdTokenReady(idToken)
            viewModel.signInWithGoogle(idToken, email)
        }
    } catch (e: ApiException) {
        android.util.Log.e("LoginScreen", "Error en Google Sign In", e)
        // El error se manejará a través del AuthState en el ViewModel
    }
} 