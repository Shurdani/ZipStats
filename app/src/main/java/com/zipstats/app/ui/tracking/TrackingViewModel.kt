package com.zipstats.app.ui.tracking

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zipstats.app.model.RouteWeatherDecision
import com.zipstats.app.model.Scooter
import com.zipstats.app.model.SurfaceConditionType
import com.zipstats.app.repository.AppOverlayRepository
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.service.TrackingStateManager
import com.zipstats.app.tracking.GpsPreLocationState
import com.zipstats.app.tracking.RouteSaveUseCase
import com.zipstats.app.tracking.TrackingServiceController
import com.zipstats.app.tracking.TrackingState
import com.zipstats.app.tracking.WeatherMonitoringUseCase
import com.zipstats.app.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel del tracking: orquesta GPS ([TrackingServiceController]),
 * clima ([WeatherMonitoringUseCase]) y guardado ([RouteSaveUseCase]).
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    application: Application,
    private val scooterRepository: VehicleRepository,
    private val preferencesManager: PreferencesManager,
    private val appOverlayRepository: AppOverlayRepository,
    private val weatherMonitoring: WeatherMonitoringUseCase,
    private val routeSaveUseCase: RouteSaveUseCase,
    private val serviceController: TrackingServiceController,
) : AndroidViewModel(application) {

    private val trackingStateManager = TrackingStateManager

    // --- Clima ---
    val weatherStatus = weatherMonitoring.weatherStatus
    val shouldShowRainWarning = weatherMonitoring.shouldShowRainWarning
    val isActiveRainWarning = weatherMonitoring.isActiveRainWarning
    val shouldShowExtremeWarning = weatherMonitoring.shouldShowExtremeWarning

    // --- GPS / servicio (delegado al controller) ---
    val routePoints = serviceController.routePoints
    val currentDistance = serviceController.currentDistance
    val currentSpeed = serviceController.currentSpeed
    val averageMovingSpeed = serviceController.averageMovingSpeed
    val timeInMotion = serviceController.timeInMotion
    val startTime = serviceController.startTime
    val duration = serviceController.duration
    val gpsSignalStrength = serviceController.gpsSignalStrength
    val gpsPreLocationState = serviceController.gpsPreLocationState
    val preLocation = serviceController.preLocation

    private val _trackingState = MutableStateFlow<TrackingState>(TrackingState.Idle)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    private val _selectedScooter = MutableStateFlow<Scooter?>(null)
    val selectedScooter: StateFlow<Scooter?> = _selectedScooter.asStateFlow()

    private val _scooters = MutableStateFlow<List<Scooter>>(emptyList())
    val scooters: StateFlow<List<Scooter>> = _scooters.asStateFlow()
    val vehiclesLoaded: StateFlow<Boolean> = appOverlayRepository.vehiclesLoaded

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var globalStateJob: kotlinx.coroutines.Job? = null

    init {
        serviceController.attach(
            scope = viewModelScope,
            onServicePauseChanged = { isPaused ->
                if (_trackingState.value is TrackingState.Tracking || _trackingState.value is TrackingState.Paused) {
                    _trackingState.value = if (isPaused) TrackingState.Paused else TrackingState.Tracking
                }
            },
        )

        loadScooters()
        syncWithGlobalState()
        weatherMonitoring.restoreFromSavedWeather(trackingStateManager.isTracking.value)
    }

    private fun isWeatherSessionActive(): Boolean =
        _trackingState.value is TrackingState.Tracking || _trackingState.value is TrackingState.Paused

    private fun syncWithGlobalState() {
        globalStateJob?.cancel()

        globalStateJob = viewModelScope.launch {
            trackingStateManager.isTracking.collect { isTracking ->
                if (isTracking) {
                    if (!serviceController.isServiceBound) {
                        serviceController.connectToExistingService()
                    }
                    _trackingState.value = if (trackingStateManager.isPaused.value) {
                        TrackingState.Paused
                    } else {
                        TrackingState.Tracking
                    }
                    weatherMonitoring.ensureContinuousMonitoringRunning(
                        scope = viewModelScope,
                        routePoints = routePoints,
                        startTime = startTime,
                        isSessionActive = ::isWeatherSessionActive,
                    )
                } else {
                    _trackingState.value = TrackingState.Idle
                    serviceController.resetSessionMetrics()
                    weatherMonitoring.onSessionEnded()
                }
            }
        }
    }

    private fun loadScooters() {
        viewModelScope.launch {
            scooterRepository.getScooters().collect { scootersList ->
                _scooters.value = scootersList
                appOverlayRepository.setVehiclesLoaded()

                val currentSelectedId = _selectedScooter.value?.id
                val selectedStillExists = currentSelectedId != null &&
                    scootersList.any { it.id == currentSelectedId }

                if (!selectedStillExists) {
                    val lastUsedScooterId = loadLastUsedScooter()
                    val scooterToSelect = if (lastUsedScooterId != null) {
                        scootersList.find { it.id == lastUsedScooterId }
                    } else {
                        null
                    }
                    _selectedScooter.value = scooterToSelect ?: scootersList.firstOrNull()
                }
            }
        }
    }

    private suspend fun loadLastUsedScooter(): String? {
        return try {
            preferencesManager.getLastUsedScooterId()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar último vehículo usado", e)
            null
        }
    }

    private suspend fun saveLastUsedScooter(scooterId: String) {
        try {
            preferencesManager.saveLastUsedScooterId(scooterId)
            Log.d(TAG, "Último vehículo guardado: $scooterId")
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar último vehículo", e)
        }
    }

    fun selectScooter(scooter: Scooter) {
        _selectedScooter.value = scooter
        viewModelScope.launch {
            saveLastUsedScooter(scooter.id)
            tryCaptureInitialWeather()
        }
    }

    fun dismissRainWarning() = weatherMonitoring.dismissRainWarning()

    fun dismissExtremeWarning() = weatherMonitoring.dismissExtremeWarning()

    fun startPreLocationTracking() {
        serviceController.startPreLocationTracking { _, state ->
            if (state is GpsPreLocationState.Ready || (state is GpsPreLocationState.Found && state.accuracy <= 10f)) {
                tryCaptureInitialWeather()
            }
        }
    }

    private fun tryCaptureInitialWeather() {
        val location = preLocation.value ?: return
        if (_selectedScooter.value != null && !weatherMonitoring.isInitialWeatherCaptured) {
            weatherMonitoring.captureInitialWeather(viewModelScope, location)
        }
    }

    fun stopPreLocationTracking() = serviceController.stopPreLocationTracking()

    fun restartPreLocationTracking() {
        serviceController.restartPreLocationTracking { _, state ->
            if (state is GpsPreLocationState.Ready || (state is GpsPreLocationState.Found && state.accuracy <= 10f)) {
                tryCaptureInitialWeather()
            }
        }
    }

    fun hasValidGpsSignal(): Boolean = serviceController.hasValidGpsSignal()

    fun startTracking() {
        weatherMonitoring.resetBadgesOnNewRoute()

        val scooter = _selectedScooter.value
        if (scooter == null) {
            _message.value = "Por favor, selecciona un vehículo primero"
            return
        }

        if (!hasValidGpsSignal()) {
            _message.value = "Esperando señal GPS... Por favor espera unos segundos"
            return
        }

        serviceController.stopPreLocationTracking()

        viewModelScope.launch {
            try {
                serviceController.startTracking(scooter)
                _trackingState.value = TrackingState.Tracking

                weatherMonitoring.captureStartWeather(
                    scope = viewModelScope,
                    routePoints = routePoints,
                    preLocation = preLocation.value,
                )
                weatherMonitoring.startContinuousMonitoring(
                    scope = viewModelScope,
                    routePoints = routePoints,
                    startTime = startTime,
                    isSessionActive = ::isWeatherSessionActive,
                )

                _message.value = "¡Tracking iniciado al 100%!"
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar seguimiento", e)
                _trackingState.value = TrackingState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun fetchWeatherManually() {
        viewModelScope.launch {
            weatherMonitoring.fetchWeatherManually(
                routePoints = routePoints.value,
                startTimeMs = startTime.value,
                onMessage = { _message.value = it },
            )
        }
    }

    fun pauseTracking() {
        serviceController.pauseTracking()
        _trackingState.value = TrackingState.Paused
    }

    fun resumeTracking() {
        serviceController.resumeTracking()
        _trackingState.value = TrackingState.Tracking
    }

    fun getSurfaceConditionTypeForConfirmation(): SurfaceConditionType =
        weatherMonitoring.getSurfaceConditionTypeForConfirmation()

    fun shouldAskSurfaceConditionQuestionsOnFinish(): Boolean =
        weatherMonitoring.shouldAskSurfaceConditionQuestionsOnFinish()

    fun finishTracking(
        notes: String = "",
        addToRecords: Boolean = false,
        surfaceConditionType: SurfaceConditionType = SurfaceConditionType.NONE,
        isSurfaceConditionConfirmed: Boolean = true,
        userAnsweredSurfaceQuestions: Boolean = false,
    ) {
        viewModelScope.launch {
            try {
                weatherMonitoring.dismissNotification()
                weatherMonitoring.cancelContinuousMonitoring()
                globalStateJob?.cancel()
                globalStateJob = null

                _trackingState.value = TrackingState.Saving
                appOverlayRepository.showSplashOverlay("Guardando ruta…")

                serviceController.stopTracking()
                trackingStateManager.resetState()

                val points = routePoints.value
                val scooter = _selectedScooter.value ?: throw Exception("No hay vehículo seleccionado")
                val capturedStartTime = startTime.value
                val capturedTimeInMotion = timeInMotion.value
                val endTime = System.currentTimeMillis()

                serviceController.resetTrackingUI()

                val result = routeSaveUseCase.saveCompletedRoute(
                    points = points,
                    scooter = scooter,
                    startTime = capturedStartTime,
                    endTime = endTime,
                    notes = notes,
                    timeInMotion = capturedTimeInMotion,
                    weatherDecision = RouteWeatherDecision(
                        surfaceConditionType = surfaceConditionType,
                        isSurfaceConditionConfirmed = isSurfaceConditionConfirmed,
                        userAnsweredSurfaceQuestions = userAnsweredSurfaceQuestions,
                    ),
                    addToRecords = addToRecords,
                )

                result.onSuccess { saveResult ->
                    _message.value = saveResult.message
                }.onFailure { error ->
                    throw error
                }
            } catch (e: Exception) {
                handleTrackingError(e)
            } finally {
                delay(600)
                appOverlayRepository.hideOverlay()
                _trackingState.value = TrackingState.Idle
            }
        }
    }

    fun cancelTracking() {
        weatherMonitoring.cancelWeatherJob()
        weatherMonitoring.cancelContinuousMonitoring()
        weatherMonitoring.resetOnCancel()

        serviceController.stopTracking()
        _trackingState.value = TrackingState.Idle
        trackingStateManager.resetState()
        serviceController.resetSessionMetrics()

        _message.value = "Ruta cancelada"
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun handleTrackingError(e: Exception) {
        Log.e(TAG, "❌ Error al finalizar tracking: ${e.message}", e)
        _message.value = "Error al guardar: ${e.message}"
        _trackingState.value = TrackingState.Error(e.message ?: "Error desconocido")
    }

    override fun onCleared() {
        super.onCleared()
        weatherMonitoring.dismissNotification()
        weatherMonitoring.cancelWeatherJob()
        weatherMonitoring.cancelContinuousMonitoring()
        serviceController.stopPreLocationTracking()
        serviceController.unbindIfBound()
    }

    companion object {
        private const val TAG = "TrackingViewModel"
    }
}
