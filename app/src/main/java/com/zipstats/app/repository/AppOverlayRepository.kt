package com.zipstats.app.repository

import com.zipstats.app.ui.shared.AppOverlayState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio singleton para manejar el estado del overlay global de la aplicación
 */
@Singleton
class AppOverlayRepository @Inject constructor() {

    private val _overlay = MutableStateFlow<AppOverlayState>(AppOverlayState.None)
    val overlay: StateFlow<AppOverlayState> = _overlay.asStateFlow()

    private val _vehiclesReady = MutableStateFlow<Boolean>(false)
    val vehiclesReady: StateFlow<Boolean> = _vehiclesReady.asStateFlow()

    private val _recordsLoaded = MutableStateFlow(false)
    val recordsLoaded: StateFlow<Boolean> = _recordsLoaded.asStateFlow()

    private val _vehiclesLoaded = MutableStateFlow(false)
    val vehiclesLoaded: StateFlow<Boolean> = _vehiclesLoaded.asStateFlow()

    private val _onboardingDismissedInSession = MutableStateFlow(false)
    val onboardingDismissedInSession: StateFlow<Boolean> = _onboardingDismissedInSession.asStateFlow()

    fun dismissOnboarding() {
        _onboardingDismissedInSession.value = true
    }

    fun setVehiclesLoaded() {
        _vehiclesLoaded.value = true
    }

    fun setRecordsLoaded() {
        _recordsLoaded.value = true
    }

    fun showSplashOverlay(message: String) {
        _overlay.value = AppOverlayState.Splash(message)
    }

    fun hideOverlay() {
        _overlay.value = AppOverlayState.None
    }

    fun setVehiclesReady(ready: Boolean) {
        _vehiclesReady.value = ready
    }

    fun resetSessionState() {
        _overlay.value = AppOverlayState.None
        _vehiclesReady.value = false
        _recordsLoaded.value = false
        _vehiclesLoaded.value = false
        _onboardingDismissedInSession.value = false
    }
}

