package com.zipstats.app.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.zipstats.app.R
import com.zipstats.app.navigation.Screen
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {
    // Diseño: Fondo blanco con el logo de la app en el centro (igual que la splash nativa)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White), // Fondo blanco para que coincida con la splash nativa
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_app), // Logo de la app
            contentDescription = "Logo ZipStats",
            modifier = Modifier.size(250.dp), // Tamaño del logo
            contentScale = ContentScale.Fit
        )
    }

    // La Lógica de Decisión
    LaunchedEffect(Unit) {
        // Opcional: Pequeño delay para que se vea el logo y no sea un flashazo
        // Si la carga es muy rápida, a veces es mejor esperar 500ms por estética.
        // Si prefieres velocidad pura, quita esta línea.
        delay(500) 

        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            // USUARIO LOGUEADO -> Vamos a Home/Routes
            navController.navigate(Screen.Routes.route) {
                popUpTo(Screen.Splash.route) { inclusive = true } // Borramos Splash del historial
            }
        } else {
            // NO LOGUEADO -> Vamos a Login
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        }
    }
}

