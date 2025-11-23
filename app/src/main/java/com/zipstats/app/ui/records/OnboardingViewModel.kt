package com.zipstats.app.ui.records

import androidx.lifecycle.ViewModel
import com.zipstats.app.utils.OnboardingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val onboardingManager: OnboardingManager
) : ViewModel()

