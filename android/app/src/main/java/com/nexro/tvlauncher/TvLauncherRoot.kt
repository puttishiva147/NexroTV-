package com.nexro.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.launcherStore by preferencesDataStore(name = "vise_launcher_settings")
private val KEY_THEME = stringPreferencesKey("theme")
private val KEY_ICON_SIZE = stringPreferencesKey("icon_size")
private val KEY_CORNERS = stringPreferencesKey("corners")
private val KEY_LABELS = stringPreferencesKey("labels")
private val KEY_PARALLAX = booleanPreferencesKey("parallax")
private val KEY_REDUCE = booleanPreferencesKey("reduce_effects")
private val KEY_WALLPAPER = stringPreferencesKey("wallpaper")

private data class LauncherPrefs(
    val theme: String = "Dark",
    val iconSize: String = "M",
    val corners: String = "16",
    val labels: String = "Focus only",
    val parallax: Boolean = true,
    val reduceEffects: Boolean = false,
    val wallpaper: String = "Solid"
)

private data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap
)

@Composable
fun TvLauncherRoot(onOpenRemote: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by context.launcherStore.data.map { p ->
        LauncherPrefs(
            theme = p[KEY_THEME] ?: "Dark",
            iconSize = p[KEY_ICON_SIZE] ?: "M",
            corners = p[KEY_CORNERS] ?: "16",
            labels = p[KEY_LABELS] ?: "Focus only",
            parallax = p[KEY_PARALLAX] ?: true,
            reduceEffects = p[KEY_REDUCE] ?: false,
            wallpaper = p[KEY_WALLPAPER] ?: "Solid"
        )
    }.collectAsState(initial = LauncherPrefs())
    val theme = com.nexro.tvlauncher.ui.theme.LauncherThemes.all.firstOrNull { it.name == prefs.theme }
        ?: com.nexro.tvlauncher.ui.theme.LauncherThemes.Dark
    val apps = remember { loadInstalledApps(context) }
    var currentTime by remember { mutableStateOf(clockNow()) }
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsSection by remember { mutableStateOf<String?>(null) }
    val firstAppFocus = remember { FocusRequester() }
    val firstSettingFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = clockNow()
            delay(30_000L)
        }
    }
    LaunchedEffect(settingsOpen, settingsSection) {
        if (settingsOpen && settingsSection == null) {
            delay(80)
            runCatching { firstSettingFocus.requestFocus() }
        } else if (!settingsOpen && apps.isNotEmpty()) {
            delay(80)
            runCatching { firstAppFocus.requestFocus() }
        }
    }
    BackHandler(enabled = settingsOpen) {
        if (settingsSection != null) settingsSection = null else settingsOpen = false
    }

    Box(Modifier.fillMaxSize().background(theme.background)) {
        if (prefs.wallpaper == "Blurred") {
            Box(Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(theme.background, theme.panel, theme.background))
            ))
        }
        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 45.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(currentTime, color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text("●", color = theme.accent, fontSize = 13.sp)
                Spacer(Modifier.width(20.dp))
                Text("◉", color = theme.textSecondary, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("VISE", color = theme.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Text("Your apps", color = theme.textSecondary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(24.dp))
            if (apps.isEmpty()) {
                Text("No launchable apps found on this TV.", color = theme.textPrimary, fontSize = 18.sp)
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    contentPadding = PaddingValues(start = 8.dp, end = 48.dp, top = 24.dp, bottom = 40.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                        AppTile(
                            app = app, colors = theme, size = prefs.iconSize, corners = prefs.corners,
                            labels = prefs.labels, parallax = prefs.parallax && !prefs.reduceEffects,
                            reduceEffects = prefs.reduceEffects, first = index == 0,
                            requester = if (index == 0) firstAppFocus else null,
                            onClick = {
                                runCatching {
                                    context.packageManager.getLaunchIntentForPackage(app.packageName)?.let(context::startActivity)
                                }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Press OK to open an app", color = theme.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                LauncherButton("Settings", theme, Modifier.width(150.dp)) {
                    settingsSection = null
                    settingsOpen = true
                }
            }
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)).clickable {
                settingsOpen = false
                settingsSection = null
            })
        }
        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(220)) + fadeIn(tween(220)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220)) + fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SettingsPanel(
                colors = theme, prefs = prefs, section = settingsSection, firstRequester = firstSettingFocus,
                onBack = { if (settingsSection != null) settingsSection = null else settingsOpen = false },
                onOpenSection = { settingsSection = it },
                onTheme = { value -> scope.launch { context.launcherStore.edit { it[KEY_THEME] = value } } },
                onIconSize = { value -> scope.launch { context.launcherStore.edit { it[KEY_ICON_SIZE] = value } } },
                onCorners = { value -> scope.launch { context.launcherStore.edit { it[KEY_CORNERS] = value } } },
                onLabels = { value -> scope.launch { context.launcherStore.edit { it[KEY_LABELS] = value } } },
                onParallax = { value -> scope.launch { context.launcherStore.edit { it[KEY_PARALLAX] = value } } },
                onReduce = { value -> scope.launch { context.launcherStore.edit { it[KEY_REDUCE] = value } } },
                onWallpaper = { value -> scope.launch { context.launcherStore.edit { it[KEY_WALLPAPER] = value } } },
                onDefaultHelp = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                }
            )
        }
    }
}

@Composable
private fun AppTile(
    app: InstalledApp,
    colors: com.nexro.tvlauncher.ui.theme.LauncherColors,
    size: String,
    corners: String,
    labels: String,
    parallax: Boolean,
    reduceEffects: Boolean,
    first: Boolean,
    requester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "appTileScale"
    )
    val shineAlpha by animateFloatAsState(
        targetValue = if (focused && !reduceEffects) 0.18f else 0f,
        animationSpec = tween(220),
        label = "tileShine"
    )
    val width = when (size) { "S" -> 200.dp; "L" -> 280.dp; else -> 240.dp }
    val height = when (size) { "S" -> 120.dp; "L" -> 168.dp; else -> 144.dp }
    val radius = when (corners) { "8" -> 8.dp; "24" -> 24.dp; else -> 16.dp }
    val tileShape = RoundedCornerShape(radius)
    val focusModifier = if (requester != null) Modifier.focusRequester(requester) else Modifier
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.width(width).graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = if (focused && parallax) (-6).dp.toPx() else 0f
        }.then(focusModifier)
            .onFocusChanged { focused = it.isFocused }
            .shadow(
                elevation = if (focused) 24.dp else 0.dp,
                shape = tileShape,
                ambientColor = Color.Black.copy(alpha = if (focused) 0.35f else 0f),
                spotColor = Color.Black.copy(alpha = if (focused) 0.35f else 0f)
            )
            .clip(tileShape)
            .background(Brush.linearGradient(listOf(colors.panel, colors.background)))
            .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) colors.focusFill.copy(alpha = 0.85f) else colors.textSecondary.copy(alpha = 0.18f)), tileShape)
            .clickable(onClick = onClick)
            .focusable()
            .semantics { contentDescription = "${app.name}, button" }
    ) {
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(20.dp), contentScale = ContentScale.Fit)
            if (shineAlpha > 0f) {
                Box(Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = shineAlpha), Color.White.copy(alpha = 0f)))
                ))
            }
        }
        if (labels == "Always" || (labels == "Focus only" && focused)) {
            Text(app.name, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp))
        } else if (labels == "Never") {
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SettingsPanel(
    colors: com.nexro.tvlauncher.ui.theme.LauncherColors,
    prefs: LauncherPrefs,
    section: String?,
    firstRequester: FocusRequester,
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit,
    onTheme: (String) -> Unit,
    onIconSize: (String) -> Unit,
    onCorners: (String) -> Unit,
    onLabels: (String) -> Unit,
    onParallax: (Boolean) -> Unit,
    onReduce: (Boolean) -> Unit,
    onWallpaper: (String) -> Unit,
    onDefaultHelp: () -> Unit
) {
    Column(
        Modifier.width(420.dp).fillMaxHeight()
            .background(colors.panel.copy(alpha = 0.92f), RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(if (section == null) "Settings" else section, color = colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            SettingsRow("‹", "Back", "", colors, compact = true, onClick = onBack)
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (section == null) {
                SettingsRow("◉", "Theme", prefs.theme, colors, firstRequester) { onOpenSection("Theme") }
                SettingsRow("▣", "Icon size", prefs.iconSize, colors) { onOpenSection("Icon size") }
                SettingsRow("◩", "Corner style", prefs.corners + " dp", colors) { onOpenSection("Corner style") }
                SettingsRow("Aa", "Show labels", prefs.labels, colors) { onOpenSection("Show labels") }
                SettingsRow("↗", "Parallax", if (prefs.parallax) "On" else "Off", colors) { onParallax(!prefs.parallax) }
                SettingsRow("◐", "Reduce effects", if (prefs.reduceEffects) "On" else "Off", colors) { onReduce(!prefs.reduceEffects) }
                SettingsRow("▧", "Wallpaper", prefs.wallpaper, colors) { onOpenSection("Wallpaper") }
                SettingsRow("⌂", "Default launcher help", "", colors, onClick = onDefaultHelp)
                SettingsRow("ⓘ", "About", "Version 1.2.0", colors) { onOpenSection("About") }
            } else {
                when (section) {
                    "Theme" -> com.nexro.tvlauncher.ui.theme.LauncherThemes.all.forEach { t ->
                        SettingsRow(if (prefs.theme == t.name) "✓" else "●", t.name, if (prefs.theme == t.name) "Selected" else "", t, onClick = { onTheme(t.name) })
                    }
                    "Icon size" -> listOf("S", "M", "L").forEach { v ->
                        SettingsRow(if (prefs.iconSize == v) "✓" else "▣", v, when(v) { "S" -> "200 × 120 dp"; "L" -> "280 × 168 dp"; else -> "240 × 144 dp" }, colors, onClick = { onIconSize(v) })
                    }
                    "Corner style" -> listOf("8", "16", "24").forEach { v ->
                        SettingsRow(if (prefs.corners == v) "✓" else "◩", v + " dp", if (prefs.corners == v) "Selected" else "", colors, onClick = { onCorners(v) })
                    }
                    "Show labels" -> listOf("Focus only", "Always", "Never").forEach { v ->
                        SettingsRow(if (prefs.labels == v) "✓" else "Aa", v, if (prefs.labels == v) "Selected" else "", colors, onClick = { onLabels(v) })
                    }
                    "Wallpaper" -> listOf("Solid", "Blurred").forEach { v ->
                        SettingsRow(if (prefs.wallpaper == v) "✓" else "▧", v, if (prefs.wallpaper == v) "Selected" else "", colors, onClick = { onWallpaper(v) })
                    }
                    "About" -> Text("VISE TV Launcher\nVersion 1.2.0\nOriginal design for Android TV.", color = colors.textSecondary, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    glyph: String,
    title: String,
    value: String,
    colors: com.nexro.tvlauncher.ui.theme.LauncherColors,
    requester: FocusRequester? = null,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = if (focused) colors.focusFill else Color.Transparent,
        animationSpec = tween(120),
        label = "settingsRowColor"
    )
    val fg = if (focused) colors.focusText else colors.textPrimary
    val mod = if (requester != null) Modifier.focusRequester(requester) else Modifier
    Row(
        Modifier.fillMaxWidth().height(if (compact) 44.dp else 60.dp)
            .then(mod)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { val s = if (focused) 1.03f else 1f; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = if (compact) 8.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(glyph, color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(16.dp))
        Text(title, color = fg, fontSize = if (compact) 13.sp else 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = if (focused) fg else colors.textSecondary, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
private fun LauncherButton(
    label: String,
    colors: com.nexro.tvlauncher.ui.theme.LauncherColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.onFocusChanged { focused = it.isFocused }
            .graphicsLayer { val s = if (focused) 1.03f else 1f; scaleX = s; scaleY = s }
            .clip(CircleShape)
            .background(if (focused) colors.focusFill else colors.panel)
            .border(1.dp, colors.textSecondary.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (focused) colors.focusText else colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

private fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = try { pm.queryIntentActivities(leanback, PackageManager.MATCH_ALL) } catch (_: Exception) { emptyList() }
    val fallback = try { pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL) } catch (_: Exception) { emptyList() }
    val seen = mutableSetOf<String>()
    return (resolved + fallback).mapNotNull { info ->
        val pkg = info.activityInfo.packageName
        if (pkg == context.packageName || !seen.add(pkg)) return@mapNotNull null
        runCatching {
            val drawable = try { info.activityInfo.loadBanner(pm) ?: info.loadIcon(pm) } catch (_: Exception) { info.loadIcon(pm) }
            InstalledApp(info.loadLabel(pm).toString(), pkg, drawableToBitmap(drawable).asImageBitmap())
        }.getOrNull()
    }.sortedBy { it.name.lowercase(Locale.getDefault()) }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    val w = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(512) ?: 128
    val h = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(512) ?: 128
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return bitmap
}

private fun clockNow(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
