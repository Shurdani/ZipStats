package com.zipstats.app.ui.shared

/**
 * Estado del overlay global de la aplicación
 */
sealed class AppOverlayState {
    object None : AppOverlayState()
    data class Splash(val message: String) : AppOverlayState()
}

