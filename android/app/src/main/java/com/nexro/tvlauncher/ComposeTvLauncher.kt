package com.nexro.tvlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ComponentActivity.launcherStore by preferencesDataStore(name = "tvos_launcher_settings")
private data class TvApp(val label: String, val packageName: String, val icon: Bitmap)
private data class TvPalette(val name: String, val background: Color, val panel: Color, val focusFill: Color, val focusText: Color, val primary: Color, val secondary: Color, val accent: Color)

private val tvPalettes = listOf(
    TvPalette("Dark", Color(0xFF0B0B0F), Color(0xFF1C1C22), Color(0xFFF2F2F7), Color(0xFF111114), Color.White, Color(0xFFA1A1AA), Color(0xFF0A84FF)),
    TvPalette("Light", Color(0xFFF2F2F7), Color.White, Color(0xFF1C1C22), Color.White, Color(0xFF111114), Color(0xFF6B6B73), Color(0xFF007AFF)),
    TvPalette("Midnight", Color(0xFF05070F), Color(0xFF111A2E), Color(0xFFE6EEFF), Color(0xFF0B1224), Color.White, Color(0xFF8FA0C2), Color(0xFF5E8BFF)),
    TvPalette("Forest", Color(0xFF07100C), Color(0xFF13241B), Color(0xFFE8F5EC), Color(0xFF0B1A12), Color.White, Color(0xFF8DB39C), Color(0xFF30D158)),
    TvPalette("Sunset", Color(0xFF140A0A), Color(0xFF2A1616), Color(0xFFFFF1E8), Color(0xFF2A1010), Color.White, Color(0xFFC9A090), Color(0xFFFF6A3D)),
    TvPalette("Graphite", Color(0xFF0F0F10), Color(0xFF232325), Color(0xFFEDEDED), Color(0xFF121212), Color.White, Color(0xFF9A9A9F), Color(0xFFA0A0A8))
)

class ComposeTvLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894 or 1024 or 512
        setContent { TvLauncherRoot() }
    }
}

@Composable
private fun TvLauncherRoot() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scope = rememberCoroutineScope()
    val prefs by activity.launcherStore.data.collectAsState(initial = androidx.datastore.preferences.core.emptyPreferences())
    val themeName = prefs[stringPreferencesKey("theme")] ?: "Dark"
    val palette = tvPalettes.firstOrNull { it.name == themeName } ?: tvPalettes.first()
    val iconSize = prefs[stringPreferencesKey("icon_size")] ?: "M"
    val labels = prefs[stringPreferencesKey("labels")] ?: "Focus only"
    val parallax = prefs[booleanPreferencesKey("parallax")] ?: true
    val reduceEffects = prefs[booleanPreferencesKey("reduce_effects")] ?: false
    var settingsOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(clockText()) }
    val apps = remember { mutableStateListOf<TvApp>() }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = (pm.queryIntentActivities(leanback, PackageManager.MATCH_ALL) + pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL))
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase(Locale.getDefault()) }
        apps.clear()
        infos.forEach { info -> try { apps.add(TvApp(info.loadLabel(pm).toString(), info.activityInfo.packageName, info.loadIcon(pm).toBitmapSafe())) } catch (_: Exception) { } }
        while (true) { clock = clockText(); delay(30_000) }
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 44.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(clock, color = palette.primary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text("NEXRO", color = palette.secondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(20.dp))
                FocusChip("Settings", palette) { settingsOpen = true }
            }
            Spacer(Modifier.height(38.dp))
            Text("Your apps", color = palette.primary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(24.dp))
            if (apps.isEmpty()) Text("No launchable apps found on this device.", color = palette.secondary, fontSize = 16.sp)
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(32.dp), contentPadding = PaddingValues(vertical = 28.dp, horizontal = 4.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    TvAppTile(app, palette, iconSize, labels, parallax, reduceEffects) {
                        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let(context::startActivity)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Installed apps · " + apps.size, color = palette.secondary, fontSize = 14.sp)
        }
        if (settingsOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f))) {
                Column(
                    Modifier.align(Alignment.CenterEnd).width(420.dp).fillMaxHeight()
                        .shadow(28.dp, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                        .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                        .background(palette.panel.copy(alpha = 0.96f)).padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Settings", color = palette.primary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("Personalize your launcher", color = palette.secondary, fontSize = 14.sp)
                        }
                        FocusChip("Close", palette) { settingsOpen = false }
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingSection("COLOR THEME", palette)
                    tvPalettes.forEach { item ->
                        SettingRow(item.name, if (themeName == item.name) "Selected" else "", palette, item.accent, themeName == item.name) {
                            scope.launch { activity.launcherStore.edit { it[stringPreferencesKey("theme")] = item.name } }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingSection("APP ICONS", palette)
                    SettingRow("Icon size", iconSize, palette) {
                        val next = when (iconSize) { "S" -> "M"; "M" -> "L"; else -> "S" }
                        scope.launch { activity.launcherStore.edit { it[stringPreferencesKey("icon_size")] = next } }
                    }
                    SettingRow("Labels", labels, palette) {
                        val next = when (labels) { "Focus only" -> "Always"; "Always" -> "Never"; else -> "Focus only" }
                        scope.launch { activity.launcherStore.edit { it[stringPreferencesKey("labels")] = next } }
                    }
                    SettingRow("Parallax", if (parallax) "On" else "Off", palette) {
                        scope.launch { activity.launcherStore.edit { it[booleanPreferencesKey("parallax")] = !parallax } }
                    }
                    SettingRow("Reduce effects", if (reduceEffects) "On" else "Off", palette) {
                        scope.launch { activity.launcherStore.edit { it[booleanPreferencesKey("reduce_effects")] = !reduceEffects } }
                    }
                    SettingRow("Default launcher help", "Instructions", palette) { helpOpen = !helpOpen }
                    SettingRow("About", "Version 1.2.0", palette) { helpOpen = !helpOpen }
                    if (helpOpen) Text("Press Home, choose Nexro TV Launcher, then select Always. Some Google TV builds restrict changing the default Home app.", color = palette.secondary, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text("Changes save automatically", color = palette.secondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun TvAppTile(app: TvApp, palette: TvPalette, size: String, labels: String, parallax: Boolean, reduceEffects: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused && !reduceEffects) 1.12f else 1f, animationSpec = if (reduceEffects) tween(0) else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), label = "tileScale")
    val width = when (size) { "S" -> 200.dp; "L" -> 280.dp; else -> 240.dp }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(width).scale(scale).onFocusChanged { focused = it.isFocused }
            .then(if (focused && !reduceEffects) Modifier.shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f)) else Modifier)
            .clip(shape).background(palette.panel).border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) palette.focusFill else palette.secondary.copy(alpha = 0.20f)), shape)
            .clickable(onClick = onClick).focusable()
    ) {
        Box(Modifier.fillMaxWidth().height(width * 0.6f).background(palette.panel), contentAlignment = Alignment.Center) {
            Image(app.icon.asImageBitmap(), app.label, Modifier.fillMaxSize().padding(20.dp).then(if (focused && parallax && !reduceEffects) Modifier.offset(x = 3.dp, y = (-2).dp) else Modifier), contentScale = ContentScale.Fit)
            if (focused && !reduceEffects) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)))
        }
        if (labels == "Always" || (labels == "Focus only" && focused)) {
            Text(app.label, color = palette.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp))
        }
    }
}

@Composable
private fun SettingSection(title: String, palette: TvPalette) {
    Text(title, color = palette.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
private fun SettingRow(title: String, value: String, palette: TvPalette, swatch: Color? = null, selected: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
            .background(if (focused) palette.focusFill else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (swatch != null) Box(Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(swatch))
        Text(title, color = if (focused) palette.focusText else palette.primary, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = if (swatch != null) 10.dp else 0.dp))
        Text(if (selected) "✓" else value, color = if (focused) palette.focusText else palette.secondary, fontSize = 13.sp)
    }
}

@Composable
private fun FocusChip(label: String, palette: TvPalette, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (focused) palette.focusFill else palette.panel)
            .border(1.dp, if (focused) palette.focusFill else palette.secondary.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { Text(label, color = if (focused) palette.focusText else palette.primary, fontSize = 14.sp) }
}

private fun Drawable.toBitmapSafe(): Bitmap {
    val w = intrinsicWidth.coerceAtLeast(1).coerceAtMost(256)
    val h = intrinsicHeight.coerceAtLeast(1).coerceAtMost(256)
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        setBounds(0, 0, w, h)
        draw(canvas)
    }
}
private fun clockText(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
