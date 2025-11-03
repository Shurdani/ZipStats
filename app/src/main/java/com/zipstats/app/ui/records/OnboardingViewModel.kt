package com.zipstats.app.ui.records

import androidx.lifecycle.ViewModel
import com.zipstats.app.utils.OnboardingManager
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val onboardingManager: OnboardingManager
) : ViewModel()

