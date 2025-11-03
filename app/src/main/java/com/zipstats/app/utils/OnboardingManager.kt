package com.zipstats.app.utils

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingManager @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_FIRST_LOGIN_COMPLETED = "first_login_completed"
    }

    /**
     * Marca el onboarding como completado
     */
    fun markOnboardingCompleted() {
        sharedPreferences.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .apply()
    }

    /**
     * Verifica si el onboarding ya fue completado
     */
    fun isOnboardingCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    /**
     * Marca el primer login como completado
     */
    fun markFirstLoginCompleted() {
        sharedPreferences.edit()
            .putBoolean(KEY_FIRST_LOGIN_COMPLETED, true)
            .apply()
    }

    /**
     * Verifica si es el primer login del usuario
     */
    fun isFirstLogin(): Boolean {
        return !sharedPreferences.getBoolean(KEY_FIRST_LOGIN_COMPLETED, false)
    }

    /**
     * Resetea el estado de onboarding (útil para testing o si se quiere mostrar de nuevo)
     */
    fun resetOnboarding() {
        sharedPreferences.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, false)
            .putBoolean(KEY_FIRST_LOGIN_COMPLETED, false)
            .apply()
    }
}

