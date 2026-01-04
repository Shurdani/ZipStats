package com.zipstats.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Efecto que oculta las barras del sistema (barra de estado y barra de navegación)
 * para crear una experiencia de pantalla completa.
 * 
 * Detecta automáticamente si está dentro de un Dialog y oculta las barras de esa ventana,
 * o si está en una Activity normal, oculta las barras de la Activity.
 * 
 * Útil para grabaciones de pantalla o visualizaciones inmersivas.
 * Las barras se restauran automáticamente cuando el composable se desmonta.
 * 
 * ⚠️ IMPORTANTE: Debe llamarse DENTRO del contenido del Dialog, no fuera.
 */
@Composable
fun HideSystemBarsEffect() {
    val view = LocalView.current
    
    DisposableEffect(Unit) {
        // 1. Truco de Magia: Intentamos obtener la ventana del Diálogo.
        // Si estamos dentro de un Dialog, esto funcionará. Si no, cogemos la de la Activity.
        val window = (view.parent as? DialogWindowProvider)?.window 
                     ?: view.context.findActivity()?.window

        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            
            // 2. Ocultar agresivamente TODO (Barras + Iconos)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            // 3. Al salir, restaurar todo
            val windowToRestore = (view.parent as? DialogWindowProvider)?.window 
                                  ?: view.context.findActivity()?.window
                                  
            if (windowToRestore != null) {
                WindowCompat.getInsetsController(windowToRestore, windowToRestore.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
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

