@file:Suppress("DEPRECATION")

package com.zipstats.app.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.zipstats.app.R
import com.zipstats.app.ui.components.ZipStatsText

// Web Client ID para Google Sign In
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

    // Estados para diálogos
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

    // Validación
    var emailError by remember { mutableStateOf("") }

    val authState by viewModel.authState.collectAsState()

    // Google Sign In
    val googleSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(DEFAULT_WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }

    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleGoogleSignInResult(task, viewModel) { idToken ->
            pendingGoogleIdToken = idToken
        }
    }

    fun validateEmail(email: String): String {
        return when {
            email.isEmpty() -> ""
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Formato de email inválido"
            else -> ""
        }
    }

    LaunchedEffect(email) {
        if (email.isNotEmpty()) emailError = validateEmail(email)
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                if (showMergeDialog) {
                    showMergeDialog = false
                    mergePassword = ""
                    mergeError = ""
                    pendingGoogleIdToken = null
                }
                onLoginSuccess()
            }
            is AuthState.Error -> {
                if (showMergeDialog) {
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
                if (!showMergeDialog) showError = false
            }
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Fondo decorativo superior (Gradiente sutil)
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

                // 1. LOGO PERSONALIZADO
                Surface(
                    shape = CircleShape,
                    // CAMBIO CLAVE: Usamos Color.White para que se fusione con el fondo blanco de tu logo
                    color = Color.White,
                    modifier = Modifier.size(130.dp), // Un poco más grande
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_app),
                            contentDescription = "Logo ZipStats",
                            modifier = Modifier.size(110.dp), // Logo ajustado
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                ZipStatsText(
                    text = "ZipStats",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                ZipStatsText(
                    text = "Inicia sesión para continuar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 2. FORMULARIO
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
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,        // Usa el gris que definimos
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, // Usa el gris plata legible
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                if (emailError.isNotEmpty() && email.isNotEmpty()) {
                    ZipStatsText(
                        text = emailError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp).align(Alignment.Start)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { ZipStatsText("Contraseña") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Ocultar" else "Mostrar"
                            )
                        }
                    }
                )

                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ZipStatsText(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = { showResetDialog = true }) {
                        ZipStatsText("¿Olvidaste tu contraseña?")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. BOTONES
                Button(
                    onClick = {
                        if (email.isNotEmpty() && emailError.isEmpty()) {
                            viewModel.login(email, password)
                        } else {
                            showError = true
                            errorMessage = "Por favor, verifica los datos"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = email.isNotEmpty() && password.isNotEmpty() && authState !is AuthState.Loading
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ZipStatsText("Iniciar Sesión", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    ZipStatsText(
                        text = "O continúa con",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Botón Google
                OutlinedButton(
                    onClick = {
                        val signInIntent = googleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = authState !is AuthState.Loading
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Icono "G" estilizado
                        ZipStatsText(
                            text = "G",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        ZipStatsText("Google", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    ZipStatsText(
                        text = "¿No tienes cuenta?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRegisterClick) {
                        ZipStatsText(
                            text = "Regístrate",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // --- DIÁLOGOS ---

    if (showResetDialog) {
        Dialog(onDismissRequest = { showResetDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ZipStatsText("Recuperar Contraseña", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    ZipStatsText(
                        "Introduce tu email para recibir un enlace de recuperación.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it; resetError = "" },
                        label = { ZipStatsText("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = resetError.isNotEmpty()
                    )
                    if (resetError.isNotEmpty()) {
                        ZipStatsText(resetError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showResetDialog = false }) { ZipStatsText("Cancelar") }
                        Button(
                            onClick = {
                                if (resetEmail.isEmpty()) resetError = "Campo obligatorio"
                                else viewModel.resetPassword(resetEmail)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) { ZipStatsText("Enviar", color = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            icon = { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
            title = { ZipStatsText("Correo Enviado") },
            text = { ZipStatsText("Revisa tu bandeja de entrada para restablecer tu contraseña.") },
            confirmButton = {
                Button(
                    onClick = { showResetConfirmation = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { ZipStatsText("Entendido", color = MaterialTheme.colorScheme.onPrimary) }
            }
        )
    }

    // Diálogo de Fusión de Cuentas
    if (showMergeDialog) {
        Dialog(onDismissRequest = { showMergeDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ZipStatsText("Vincular cuenta", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    ZipStatsText(
                        "El email $mergeEmail ya está registrado. Ingresa tu contraseña para vincular con Google.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = mergePassword,
                        onValueChange = { mergePassword = it; mergeError = "" },
                        label = { ZipStatsText("Contraseña") },
                        visualTransformation = if (mergePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { mergePasswordVisible = !mergePasswordVisible }) {
                                Icon(if (mergePasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = mergeError.isNotEmpty()
                    )
                    if (mergeError.isNotEmpty()) {
                        ZipStatsText(mergeError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showMergeDialog = false }) { ZipStatsText("Cancelar") }
                        Button(
                            onClick = {
                                if (mergePassword.isEmpty()) mergeError = "Ingresa tu contraseña"
                                else if (pendingGoogleIdToken != null) viewModel.linkGoogleAccount(pendingGoogleIdToken!!, mergeEmail, mergePassword)
                            },
                            enabled = authState !is AuthState.Loading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (authState is AuthState.Loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            else ZipStatsText("Vincular", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

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
    }
}