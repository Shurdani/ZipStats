package com.zipstats.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager global del estado de tracking GPS
 * Permite que otras pantallas sepan si hay una grabación activa
 */
object TrackingStateManager {
    
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    
    private val _distance = MutableStateFlow("0 km")
    val distance: StateFlow<String> = _distance.asStateFlow()
    
    private val _duration = MutableStateFlow("00:00")
    val duration: StateFlow<String> = _duration.asStateFlow()
    
    fun updateTrackingState(isTracking: Boolean) {
        _isTracking.value = isTracking
    }
    
    fun updatePausedState(isPaused: Boolean) {
        _isPaused.value = isPaused
    }
    
    fun updateDistance(distance: String) {
        _distance.value = distance
    }
    
    fun updateDuration(duration: String) {
        _duration.value = duration
    }
    
    fun resetState() {
        _isTracking.value = false
        _isPaused.value = false
        _distance.value = "0 km"
        _duration.value = "00:00"
    }
}
