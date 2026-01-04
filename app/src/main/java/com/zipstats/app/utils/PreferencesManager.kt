package com.zipstats.app.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "PatinetaPrefs",
        Context.MODE_PRIVATE
    )

    fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }
    
    /**
     * Guarda el ID del último vehículo usado
     */
    fun saveLastUsedScooterId(scooterId: String) {
        sharedPreferences.edit().putString(KEY_LAST_USED_SCOOTER, scooterId).apply()
    }
    
    /**
     * Obtiene el ID del último vehículo usado
     */
    fun getLastUsedScooterId(): String? {
        return sharedPreferences.getString(KEY_LAST_USED_SCOOTER, null)
    }

    companion object {
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_LAST_USED_SCOOTER = "last_used_scooter"
    }
} 