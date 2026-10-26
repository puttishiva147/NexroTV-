package com.nexro.tvlauncher.ui.theme

import androidx.compose.ui.graphics.Color

data class LauncherColors(
    val name: String,
    val background: Color,
    val panel: Color,
    val focusFill: Color,
    val focusText: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val shadow: Color = Color(0x59000000)
)

object LauncherThemes {
    val Dark = LauncherColors("Dark", Color(0xFF0B0B0F), Color(0xFF1C1C22), Color(0xFFF2F2F7), Color(0xFF111114), Color(0xFFFFFFFF), Color(0xFFA1A1AA), Color(0xFF0A84FF))
    val Light = LauncherColors("Light", Color(0xFFF2F2F7), Color(0xFFFFFFFF), Color(0xFF1C1C22), Color(0xFFFFFFFF), Color(0xFF111114), Color(0xFF6B6B73), Color(0xFF007AFF))
    val Midnight = LauncherColors("Midnight", Color(0xFF05070F), Color(0xFF111A2E), Color(0xFFE6EEFF), Color(0xFF0B1224), Color(0xFFFFFFFF), Color(0xFF8FA0C2), Color(0xFF5E8BFF))
    val Forest = LauncherColors("Forest", Color(0xFF07100C), Color(0xFF13241B), Color(0xFFE8F5EC), Color(0xFF0B1A12), Color(0xFFFFFFFF), Color(0xFF8DB39C), Color(0xFF30D158))
    val Sunset = LauncherColors("Sunset", Color(0xFF140A0A), Color(0xFF2A1616), Color(0xFFFFF1E8), Color(0xFF2A1010), Color(0xFFFFFFFF), Color(0xFFC9A090), Color(0xFFFF6A3D))
    val Graphite = LauncherColors("Graphite", Color(0xFF0F0F10), Color(0xFF232325), Color(0xFFEDEDED), Color(0xFF121212), Color(0xFFFFFFFF), Color(0xFF9A9A9F), Color(0xFFA0A0A8))
    val all = listOf(Dark, Light, Midnight, Forest, Sunset, Graphite)
}
