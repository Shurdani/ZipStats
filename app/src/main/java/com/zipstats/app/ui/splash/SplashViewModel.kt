package com.zipstats.app.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.zipstats.app.repository.AppOverlayRepository
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.RouteRepository
import com.zipstats.app.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val routeRepository: RouteRepository,
    private val recordRepository: RecordRepository,
    private val auth: FirebaseAuth,
    private val appOverlayRepository: AppOverlayRepository
) : ViewModel() {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                if (auth.currentUser != null) {
                    withContext(Dispatchers.IO) {
                        supervisorScope {
                            // La app abre siempre en Rutas: solo eso bloquea el splash.
                            val vehicles = async { vehicleRepository.getUserVehicles() }
                            val firstRoutesPage = async {
                                routeRepository.preloadFirstPageRoutes(RouteRepository.DEFAULT_PAGE_SIZE)
                            }
                            launch {
                                recordRepository.preloadFirstPageRecords(
                                    RecordRepository.DEFAULT_PAGE_SIZE
                                )
                            }
                            vehicles.await()
                            firstRoutesPage.await()
                        }
                    }
                }
            } catch (_: Exception) {
                // Las pantallas cargan sus propios Flows si falla el prefetch
            } finally {
                appOverlayRepository.setVehiclesReady(true)
                _ready.value = true
            }
        }
    }
}
