package com.zipstats.app.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle

/**
 * Utilidades para adaptación responsive de pantallas
 */
object ScreenUtils {
    
    @Composable
    fun getScreenWidthDp(): Int {
        val configuration = LocalConfiguration.current
        return configuration.screenWidthDp
    }
    
    @Composable
    fun isSmallScreen(): Boolean {
        return getScreenWidthDp() < 360
    }
    
    @Composable
    fun isMediumScreen(): Boolean {
        val width = getScreenWidthDp()
        return width >= 360 && width < 420
    }
    
    @Composable
    fun isLargeScreen(): Boolean {
        return getScreenWidthDp() >= 420
    }
    
    @Composable
    fun getResponsivePadding(): Dp {
        return if (isSmallScreen()) 8.dp else 16.dp
    }
    
    @Composable
    fun getResponsiveSpacing(): Dp {
        return if (isSmallScreen()) 8.dp else 16.dp
    }
    
    @Composable
    fun getSmallTextSize(baseStyle: TextStyle): Float {
        return if (isSmallScreen()) {
            baseStyle.fontSize.value * 0.85f
        } else {
            baseStyle.fontSize.value
        }
    }
    
    @Composable
    fun getResponsiveTextSize(baseSize: Float): Float {
        return if (isSmallScreen()) {
            baseSize * 0.85f
        } else {
            baseSize
        }
    }
}

