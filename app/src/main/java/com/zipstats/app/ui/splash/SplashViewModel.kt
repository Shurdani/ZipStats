package com.zipstats.app.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.RouteRepository
import com.zipstats.app.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val routeRepository: RouteRepository,
    private val recordRepository: RecordRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user != null) {
                // Cargar datos iniciales en segundo plano antes de mostrar la UI
                try {
                    // Cargar vehículos para que estén en memoria
                    vehicleRepository.getUserVehicles()
                    
                    // Cargar rutas para que estén en memoria (ignorar resultado, solo necesita cargar)
                    routeRepository.getUserRoutes()
                    
                    // Cargar registros para que estén en memoria
                    recordRepository.getAllRecords()
                    
                    // Todo cargado, marcar como ready
                    _ready.value = true
                } catch (e: Exception) {
                    // Si hay error, igual marcamos como ready para no bloquear la app
                    // Los Flows se suscribirán cuando se necesiten y manejarán el error
                    _ready.value = true
                }
            } else {
                // Si no hay usuario, no hay nada que cargar
                _ready.value = true
            }
        }
    }
}

