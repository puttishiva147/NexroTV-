package com.nexro.tvlauncher.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object Motion {
    val focusScale = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
    fun <T> quick() = tween<T>(120, easing = FastOutSlowInEasing)
    fun <T> standard() = tween<T>(220, easing = FastOutSlowInEasing)
    fun <T> slow() = tween<T>(300, easing = FastOutSlowInEasing)
    const val STAGGER_MS = 25
    const val TILE_FOCUS_SCALE = 1.12f
    const val ROW_FOCUS_SCALE = 1.03f
}
