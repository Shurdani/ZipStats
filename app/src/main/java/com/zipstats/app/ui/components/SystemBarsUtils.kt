package com.zipstats.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Efecto que oculta las barras del sistema (barra de estado y barra de navegación)
 * para crear una experiencia de pantalla completa.
 * 
 * Útil para grabaciones de pantalla o visualizaciones inmersivas.
 * Las barras se restauran automáticamente cuando el composable se desmonta.
 */
@Composable
fun HideSystemBarsEffect() {
    val context = LocalContext.current
    
    DisposableEffect(Unit) {
        val activity = context.findActivity() ?: return@DisposableEffect onDispose {}
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        // 1. Ocultar tanto la barra de estado (arriba) como la de navegación (abajo)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        
        // 2. Comportamiento: Que reaparezcan si el usuario desliza desde el borde
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            // 3. Al cerrar el diálogo o salir de la pantalla, MOSTRAR todo de nuevo
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * Función de extensión necesaria para encontrar la Activity desde el contexto de Compose
 */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

