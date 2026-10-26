package com.nexro.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Nexro Liquid Glass: minimal, remote-first Android TV launcher.
 * Uses real installed apps, local preferences and a custom-drawn offline wallpaper.
 */
class NexroTvOsExperience(private val a: Activity, private val remoteSettings: () -> Unit) {
    private val prefs = a.getSharedPreferences("nexro_tv_preferences", Activity.MODE_PRIVATE)
    private val bg = Color.rgb(5, 7, 13)
    private val white = Color.rgb(247, 249, 255)
    private val muted = Color.rgb(168, 179, 200)
    private val blue = Color.rgb(145, 191, 255)
    private val apps = mutableListOf<Item>()
    private var page = "Home"
    private var selectedIndex = 0
    private var searchQuery = ""
    private lateinit var root: FrameLayout
    private lateinit var dockRow: LinearLayout
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            if (::clockText.isInitialized && page == "Home") {
                clockText.text = currentTime()
                clockText.invalidate()
                dateText.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
            }
            clockHandler.postDelayed(this, 30_000L)
        }
    }

    private data class Item(val name: String, val pkg: String, val icon: Drawable, val system: Boolean)

    fun dispose() { clockHandler.removeCallbacks(clockTick) }

    fun render() {
        loadApps()
        show("Home")
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
    }

    private fun loadApps() {
        apps.clear()
        val pm = a.packageManager
        val seen = mutableSetOf<String>()
        val queries = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )
        queries.flatMap { intent ->
            try { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) } catch (_: Exception) { emptyList() }
        }.forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg != a.packageName && seen.add(pkg) && pm.getLaunchIntentForPackage(pkg) != null) {
                val appInfo = info.activityInfo.applicationInfo
                // Preserve the real activity icon; do not tint or substitute a generic package icon.
                apps.add(Item(info.loadLabel(pm).toString(), pkg,
                    try { info.loadIcon(pm) } catch (_: Exception) {
                        try { pm.getApplicationIcon(pkg) } catch (_: Exception) { a.applicationInfo.loadIcon(pm) }
                    },
                    (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0))
            }
        }
        apps.sortBy { it.name.lowercase(Locale.getDefault()) }
    }

    private fun show(destination: String, query: String = "") {
        page = destination
        searchQuery = query
        root = FrameLayout(a).apply {
            setBackgroundColor(bg)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(WallpaperView(a), FrameLayout.LayoutParams(-1, -1))
        when (destination) {
            "Home" -> buildHome()
            "Settings" -> buildSettings()
            "IconStyle" -> buildIconStyle()
            "QuickSettings" -> buildQuickSettings()
            "Apps" -> buildApps()
            "Search" -> buildSearch(query)
            else -> buildHome()
        }
        a.setContentView(root)
        root.post {
            if (page == "Home" && ::dockRow.isInitialized && dockRow.childCount > 0) {
                dockRow.getChildAt(selectedIndex.coerceIn(0, dockRow.childCount - 1)).requestFocus()
            } else {
                val firstControl = root.focusables(View.FOCUS_FORWARD)
                    .firstOrNull { it !== root && it.isFocusable && it.visibility == View.VISIBLE }
                if (firstControl != null) firstControl.requestFocus() else root.requestFocus()
            }
        }
    }

    private fun buildHome() {
        val content = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(42), dp(20), dp(42), dp(18))
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        val top = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        brand.addView(text("nexro", 30f, white, true))
        brand.addView(text(" TV", 19f, muted))
        top.addView(brand, LinearLayout.LayoutParams(0, dp(56), 1f))

        val controls = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = glassPill()
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }
        addControl(controls, "⌕", "Search") { show("Search") }
        addControl(controls, "▦", "All Apps") { show("Apps") }
        addControl(controls, "☷", "Quick Settings") { show("QuickSettings") }
        addControl(controls, "⚙", "Nexro TV Settings") { show("Settings") }
        addControl(controls, "⌁", "Phone remote") { remoteSettings() }
        val time = TextView(a).apply {
            textSize = 14f; setTextColor(white); gravity = Gravity.CENTER
            text = currentTime()
            setPadding(dp(7), 0, dp(7), 0)
        }
        controls.addView(time, LinearLayout.LayoutParams(-2, dp(38)))
        addControl(controls, "●", "Profile") {
            AlertDialog.Builder(a).setTitle("Nexro TV").setMessage("Premium launcher for the apps installed on this TV.")
                .setPositiveButton("OK", null).show()
        }
        top.addView(controls)
        content.addView(top, LinearLayout.LayoutParams(-1, dp(60)))

        val spacer = View(a)
        content.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        val clockBlock = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        dateText = text(SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date()), 19f, muted)
        clockText = text(currentTime(), 64f, white)
        clockText.typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        clockBlock.addView(dateText)
        clockBlock.addView(clockText, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        content.addView(clockBlock, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(12); bottomMargin = dp(22)
        })

        val dockGlass = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glassDock()
            setPadding(dp(14), dp(12), dp(14), dp(9))
        }
        val scroll = HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        dockRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        scroll.addView(dockRow, ViewGroup.LayoutParams(-2, -1))
        dockGlass.addView(scroll, LinearLayout.LayoutParams(-1, dp(122)))
        content.addView(dockGlass, LinearLayout.LayoutParams(-1, dp(150)))
        populateDock()
        if (apps.isEmpty()) {
            val empty = text("No launchable apps found", 16f, muted)
            empty.gravity = Gravity.CENTER
            dockRow.addView(empty, LinearLayout.LayoutParams(dp(260), dp(110)))
        }
    }

    private fun populateDock() {
        dockRow.removeAllViews()
        val favorites = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        val pinned = apps.filter { favorites.contains(it.pkg) }
        val displayed = if (pinned.isNotEmpty()) (pinned + apps.filterNot { favorites.contains(it.pkg) }) else apps
        displayed.forEachIndexed { index, item ->
            val tile = dockItem(item, index)
            dockRow.addView(tile, LinearLayout.LayoutParams(dp(iconTileWidth()), dp(118)).apply {
                marginStart = dp(4); marginEnd = dp(4)
            })
        }
        if (displayed.isNotEmpty()) {
            val target = selectedIndex.coerceIn(0, displayed.lastIndex)
            dockRow.getChildAt(target)?.requestFocus()
        }
    }

    private fun dockItem(item: Item, index: Int): View {
        val style = prefs.getString("icon_style", "Squircle") ?: "Squircle"
        val size = when (prefs.getString("icon_size", "Medium")) {
            "Small" -> 52
            "Large" -> 76
            else -> 64
        }
        val tile = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            clipChildren = false
            clipToPadding = false
            setPadding(dp(7), dp(7), dp(7), dp(8))
            background = tileBackground(false)
            elevation = dp(6).toFloat()
            translationZ = dp(1).toFloat()
        }
        val iconFrame = FrameLayout(a).apply {
            background = when (style) {
                "Rounded" -> GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(250, 251, 255), Color.rgb(218, 226, 240))).apply {
                    cornerRadius = dp(17).toFloat()
                    setStroke(dp(1), Color.argb(90, 255, 255, 255))
                }
                "Glass" -> GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.argb(205, 190, 211, 245), Color.argb(105, 62, 81, 112))).apply {
                    cornerRadius = dp(21).toFloat()
                    setStroke(dp(1), Color.argb(150, 238, 246, 255))
                }
                "Minimal" -> GradientDrawable().apply {
                    cornerRadius = dp(17).toFloat()
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), Color.argb(150, 220, 232, 255))
                }
                else -> GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(245, 247, 252), Color.rgb(213, 222, 237))).apply {
                    cornerRadius = dp(19).toFloat()
                    setStroke(dp(1), Color.argb(105, 255, 255, 255))
                }
            }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            elevation = dp(3).toFloat()
        }
        val icon = ImageView(a).apply {
            setImageDrawable(item.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = item.name
            val inset = if (style == "Minimal") dp(1) else dp(5)
            setPadding(inset, inset, inset, inset)
        }
        iconFrame.addView(icon, FrameLayout.LayoutParams(-1, -1))
        tile.addView(iconFrame, LinearLayout.LayoutParams(dp(size), dp(size)))
        tile.addView(text(item.name, 13f, white, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(2), dp(8), dp(2), dp(1))
        }, LinearLayout.LayoutParams(-1, -2))
        tile.setOnFocusChangeListener { view, focused ->
            view.background = tileBackground(focused)
            view.elevation = dp(if (focused) 18 else 6).toFloat()
            view.translationZ = dp(if (focused) 10 else 1).toFloat()
            iconFrame.elevation = dp(if (focused) 10 else 3).toFloat()
            val scale = if (focused && motion()) 1.075f else 1f
            view.animate().cancel()
            view.animate().scaleX(scale).scaleY(scale)
                .setDuration(if (motion()) 180L else 0L).start()
            if (focused) selectedIndex = index
        }
        tile.setOnClickListener { launch(item) }
        tile.setOnLongClickListener { appActions(item); true }
        return tile
    }

    /** Main launcher settings. Icon styling is a separate destination, never the Settings home. */
    private fun buildSettings() {
        val outer = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(42), dp(22), dp(42), dp(24))
        }
        root.addView(outer, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text("Nexro TV Settings", 30f, white, true))
        titles.addView(text("Personalize your launcher and control your TV", 14f, muted))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(focusButton("‹  Home") { show("Home") })
        outer.addView(header, LinearLayout.LayoutParams(-1, dp(66)))
        outer.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(14) })

        val sc = ScrollView(a).apply { isVerticalScrollBarEnabled = false }
        val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        list.addView(actionRow("▣", "Appearance & App Icons", "Icon shape, size and icon display options") {
            show("IconStyle")
        }, LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(8) })
        list.addView(actionRow("☷", "Quick Settings", "Network, display, sound and system shortcuts") {
            show("QuickSettings")
        }, LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(8) })
        list.addView(actionRow("⌁", "Phone Remote Connection", "Pair a phone using the TV address and PIN") {
            remoteSettings()
        }, LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(8) })
        list.addView(actionRow("⚙", "Android System Settings", "Open the actual settings app provided by your TV") {
            openAndroidSettings()
        }, LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(8) })
        list.addView(actionRow("↻", "Refresh Installed Apps", "Reload apps and their original Android icons") {
            loadApps(); show("Settings")
            Toast.makeText(a, "Installed apps refreshed", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, dp(70)))
        sc.addView(list)
        outer.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(4) })
    }

    /** Dedicated quick-settings hub. Shortcuts launch Android's real settings panels. */
    private fun buildQuickSettings() {
        val outer = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(42), dp(22), dp(42), dp(24))
        }
        root.addView(outer, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text("Quick Settings", 30f, white, true))
        titles.addView(text("Shortcuts open Android's system controls", 14f, muted))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(focusButton("‹  Back") { show("Settings") })
        outer.addView(header, LinearLayout.LayoutParams(-1, dp(66)))
        outer.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(14) })

        val grid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        fun quickTile(glyph: String, title: String, subtitle: String, action: String): View =
            LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                setPadding(dp(18), dp(14), dp(18), dp(14))
                background = glassPanel()
                elevation = dp(4).toFloat()
                contentDescription = "$title. Opens Android system settings"
                addView(text(glyph, 27f, blue, true), LinearLayout.LayoutParams(-1, dp(34)))
                addView(text(title, 17f, white, true).apply { setPadding(0, dp(7), 0, dp(4)) },
                    LinearLayout.LayoutParams(-1, -2))
                addView(text(subtitle, 12f, muted).apply { maxLines = 2 },
                    LinearLayout.LayoutParams(-1, -2))
                setOnFocusChangeListener { v, focused ->
                    v.background = if (focused) tileBackground(true) else glassPanel()
                    val scale = if (focused && motion()) 1.025f else 1f
                    v.animate().scaleX(scale).scaleY(scale).setDuration(if (motion()) 140L else 0L).start()
                }
                setOnClickListener {
                    isPressed = true
                    postDelayed({ isPressed = false }, 90L)
                    openSystemPage(action)
                }
            }
        val settingsTiles = listOf(
            listOf("⌁", "Network", "Wi-Fi & internet", android.provider.Settings.ACTION_WIFI_SETTINGS),
            listOf("ᛒ", "Bluetooth", "Pair devices", android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
            listOf("☼", "Display", "Screen preferences", android.provider.Settings.ACTION_DISPLAY_SETTINGS),
            listOf("♫", "Sound", "Audio & volume", android.provider.Settings.ACTION_SOUND_SETTINGS),
            listOf("⚙", "System Settings", "All TV settings", android.provider.Settings.ACTION_SETTINGS)
        )
        settingsTiles.chunked(2).forEach { pair ->
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { item ->
                row.addView(quickTile(item[0], item[1], item[2], item[3]),
                    LinearLayout.LayoutParams(0, dp(116), 1f).apply {
                        marginEnd = dp(10); bottomMargin = dp(10)
                    })
            }
            if (pair.size == 1) row.addView(View(a), LinearLayout.LayoutParams(0, dp(116), 1f))
            grid.addView(row, LinearLayout.LayoutParams(-1, dp(126)))
        }
        outer.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun openAndroidSettings() {
        openSystemPage(android.provider.Settings.ACTION_SETTINGS)
    }

    private fun openSystemPage(action: String) {
        val candidates = when (action) {
            android.provider.Settings.ACTION_SETTINGS -> listOf(
                android.provider.Settings.ACTION_SETTINGS,
                "android.settings.DEVICE_INFO_SETTINGS",
                android.provider.Settings.ACTION_WIFI_SETTINGS
            )
            else -> listOf(action, android.provider.Settings.ACTION_SETTINGS)
        }
        for (candidate in candidates) {
            try {
                val intent = Intent(candidate).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                a.startActivity(intent)
                return
            } catch (_: Exception) { }
        }
        Toast.makeText(a, "This TV firmware does not expose that Android settings page to apps.", Toast.LENGTH_LONG).show()
    }

    private fun buildIconStyle() {
        val outer = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(42), dp(20), dp(42), dp(24))
        }
        root.addView(outer, FrameLayout.LayoutParams(-1, -1))
        val top = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text("nexro", 28f, white, true))
        top.addView(text(" TV", 18f, muted))
        top.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(focusButton("‹  Settings") { show("Settings") })
        outer.addView(top, LinearLayout.LayoutParams(-1, dp(54)))
        val panel = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = glassPanel()
            setPadding(dp(24), dp(18), dp(24), dp(20))
        }
        val heading = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(text("Icon Style", 22f, white, true))
        heading.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        heading.addView(text("NEXRO LIQUID GLASS", 10f, muted, true))
        panel.addView(heading, LinearLayout.LayoutParams(-1, dp(44)))
        panel.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(8); bottomMargin = dp(14) })

        val styles = listOf("Rounded", "Squircle", "Glass", "Minimal")
        val styleRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        styles.forEach { style ->
            val selected = prefs.getString("icon_style", "Squircle") == style
            val option = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                isFocusable = true; isFocusableInTouchMode = true
                background = tileBackground(selected)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val preview = FrameLayout(a).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(if (style == "Squircle") Color.rgb(58, 118, 207) else Color.rgb(75, 87, 111),
                        Color.rgb(18, 27, 44))).apply {
                    cornerRadius = dp(if (style == "Rounded") 15 else 19).toFloat()
                    setStroke(dp(if (selected) 2 else 1), if (selected) Color.WHITE else Color.argb(100, 190, 208, 239))
                }
                if (style == "Minimal") background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat(); setColor(Color.TRANSPARENT); setStroke(dp(2), white)
                }
                if (style == "Glass") foreground = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat(); setColor(Color.argb(40, 255, 255, 255)); setStroke(dp(1), Color.argb(120,255,255,255))
                }
            }
            // Preview a real installed app icon instead of an empty sample tile.
            val sampleIcon = apps.firstOrNull()?.icon
            if (sampleIcon != null) {
                val sample = ImageView(a).apply {
                    setImageDrawable(sampleIcon.constantState?.newDrawable(a.resources)?.mutate() ?: sampleIcon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                preview.addView(sample, FrameLayout.LayoutParams(-1, -1))
            } else {
                preview.addView(text("N", 25f, white, true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(-1, -1))
            }
            option.addView(preview, LinearLayout.LayoutParams(dp(54), dp(54)))
            option.addView(text(style, 13f, if (selected) white else muted, selected).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
            })
            option.setOnFocusChangeListener { v, focus -> v.background = tileBackground(focus || prefs.getString("icon_style", "Squircle") == style) }
            option.setOnClickListener { prefs.edit().putString("icon_style", style).apply(); show("IconStyle") }
            styleRow.addView(option, LinearLayout.LayoutParams(0, dp(104), 1f).apply { marginEnd = dp(8) })
        }
        panel.addView(styleRow, LinearLayout.LayoutParams(-1, dp(108)))

        panel.addView(text("Icon Size", 16f, muted, true).apply { setPadding(0, dp(16), 0, dp(8)) })
        val sizes = listOf("Small", "Medium", "Large")
        val sizeRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        sizes.forEach { size ->
            val chosen = prefs.getString("icon_size", "Medium") == size
            val btn = focusButton(size, chosen) {
                prefs.edit().putString("icon_size", size).apply()
                show("IconStyle")
            }
            sizeRow.addView(btn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        }
        panel.addView(sizeRow, LinearLayout.LayoutParams(-1, dp(46)))

        panel.addView(text("Icon Pack", 16f, muted, true).apply { setPadding(0, dp(18), 0, dp(8)) })
        panel.addView(actionRow("▱", "System (Default)", "Use authentic installed application icons") {
            AlertDialog.Builder(a).setTitle("Icon Pack")
                .setMessage("Nexro currently uses authentic icons supplied by Android for installed apps. External icon-pack mapping is not configured yet.")
                .setPositiveButton("OK", null).show()
        }, LinearLayout.LayoutParams(-1, dp(58)))
        panel.addView(actionRow("↻", "Restore Default Icons", "Reset style to Squircle and size to Medium") {
            AlertDialog.Builder(a).setTitle("Restore default icons?")
                .setMessage("Reset the icon style and size? Your apps and favorites will remain unchanged.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore") { _, _ ->
                    prefs.edit().putString("icon_style", "Squircle").putString("icon_size", "Medium").remove("icon_pack").apply()
                    show("IconStyle")
                }.show()
        }, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(8) })
        val scroll = ScrollView(a).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        scroll.addView(panel)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12); bottomMargin = dp(12) })
        outer.addView(focusButton("Done") { show("Home") }, LinearLayout.LayoutParams(dp(150), dp(44)))
    }

    private fun buildApps() {
        val outer = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(42), dp(20), dp(42), dp(20)) }
        root.addView(outer, FrameLayout.LayoutParams(-1, -1))
        val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("Apps", 30f, white, true))
        head.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        head.addView(focusButton("‹  Home") { show("Home") })
        outer.addView(head, LinearLayout.LayoutParams(-1, dp(58)))
        if (apps.isEmpty()) {
            outer.addView(text("No launchable apps found", 20f, muted))
            return
        }
        val scroll = ScrollView(a).apply { isVerticalScrollBarEnabled = false }
        val rows = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        apps.chunked(6).forEach { group ->
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            group.forEach { item -> row.addView(dockItem(item, apps.indexOf(item)), LinearLayout.LayoutParams(0, dp(125), 1f).apply { marginEnd = dp(8) }) }
            repeat(6 - group.size) { row.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f)) }
            rows.addView(row, LinearLayout.LayoutParams(-1, dp(135)))
        }
        scroll.addView(rows)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun buildSearch(initial: String) {
        val outer = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(42), dp(20), dp(42), dp(20)) }
        root.addView(outer, FrameLayout.LayoutParams(-1, -1))
        val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("Search", 30f, white, true))
        head.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        head.addView(focusButton("‹  Home") { show("Home") })
        outer.addView(head, LinearLayout.LayoutParams(-1, dp(58)))
        val field = EditText(a).apply {
            setSingleLine(true); textSize = 19f; setTextColor(white); setHintTextColor(muted)
            hint = "Search installed apps"; setText(initial)
            setPadding(dp(16), dp(10), dp(16), dp(10)); background = glassPanel()
            isFocusable = true
        }
        outer.addView(field, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(16); bottomMargin = dp(12) })
        val resultList = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val sc = ScrollView(a).apply { isVerticalScrollBarEnabled = false }; sc.addView(resultList)
        outer.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f))
        fun update(q: String) {
            resultList.removeAllViews()
            val found = apps.filter { it.name.contains(q, true) || it.pkg.contains(q, true) }
            if (found.isEmpty()) resultList.addView(text(if (q.isBlank()) "Type an app name to search." else "No matching apps", 16f, muted))
            found.chunked(6).forEach { group ->
                val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                group.forEach { item -> row.addView(dockItem(item, apps.indexOf(item)), LinearLayout.LayoutParams(0, dp(125), 1f).apply { marginEnd = dp(8) }) }
                resultList.addView(row, LinearLayout.LayoutParams(-1, dp(135)))
            }
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        update(initial)
    }

    private fun appActions(item: Item) {
        val favorite = prefs.getStringSet("favorites", emptySet())?.contains(item.pkg) == true
        AlertDialog.Builder(a).setTitle(item.name)
            .setItems(arrayOf(if (favorite) "Remove from dock priority" else "Prioritize in dock", "Open app", "Cancel")) { dialog, which ->
                when (which) {
                    0 -> {
                        val set = (prefs.getStringSet("favorites", emptySet()) ?: emptySet()).toMutableSet()
                        if (favorite) set.remove(item.pkg) else set.add(item.pkg)
                        prefs.edit().putStringSet("favorites", set).apply()
                        show(page)
                    }
                    1 -> launch(item)
                    else -> dialog.dismiss()
                }
            }.show()
    }

    private fun addControl(parent: LinearLayout, glyph: String, label: String, click: () -> Unit) {
        val control = TextView(a).apply {
            text = glyph; textSize = 22f; setTextColor(white); gravity = Gravity.CENTER
            contentDescription = label; isFocusable = true; isClickable = true
            background = tileBackground(false)
            setOnFocusChangeListener { v, focused -> v.background = tileBackground(focused) }
            setOnClickListener { click() }
        }
        parent.addView(control, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(2); marginEnd = dp(2) })
    }

    private fun actionRow(glyph: String, title: String, subtitle: String, click: () -> Unit): View =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isFocusableInTouchMode = true; isClickable = true
            elevation = dp(3).toFloat()
            background = tileBackground(false); setPadding(dp(12), dp(5), dp(12), dp(5))
            addView(text(glyph, 22f, muted), LinearLayout.LayoutParams(dp(42), -2))
            val copy = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            copy.addView(text(title, 14f, white, true))
            copy.addView(text(subtitle, 11f, muted))
            addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text("›", 24f, muted))
            setOnFocusChangeListener { v, f ->
                v.background = tileBackground(f)
                v.elevation = dp(if (f) 12 else 3).toFloat()
                v.translationZ = dp(if (f) 6 else 0).toFloat()
                v.animate().cancel()
                v.animate().scaleX(if (f) 1.025f else 1f).scaleY(if (f) 1.025f else 1f)
                    .setDuration(if (motion()) 150L else 0L).start()
            }
            setOnClickListener { click() }
        }

    private fun focusButton(label: String, selected: Boolean = false, click: () -> Unit): TextView =
        text(label, 14f, if (selected) bg else white, true).apply {
            gravity = Gravity.CENTER; isFocusable = true; isClickable = true
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = if (selected) pill(Color.rgb(225, 235, 255)) else glassPill()
            setOnFocusChangeListener { v, f -> v.background = if (f) pill(Color.rgb(225, 235, 255)) else if (selected) pill(Color.rgb(225, 235, 255)) else glassPill(); setTextColor(if (f || selected) bg else white) }
            setOnClickListener { click() }
        }

    private fun launch(item: Item) {
        try {
            val intent = a.packageManager.getLaunchIntentForPackage(item.pkg)
            if (intent != null) a.startActivity(intent)
            else AlertDialog.Builder(a).setMessage("This app no longer has a launchable activity.").setPositiveButton("OK", null).show()
        } catch (_: Exception) {
            AlertDialog.Builder(a).setTitle("Couldn't open app").setMessage("Android couldn't open " + item.name + ".").setPositiveButton("OK", null).show()
        }
    }

    fun executeRemote(action: String, value: String?): JSONObject {
        var ok = true
        val message: String
        when (action) {
            "volume_up", "volume_down", "mute", "unmute" -> {
                val audio = a.getSystemService(Activity.AUDIO_SERVICE) as AudioManager
                val direction = when (action) { "volume_up" -> AudioManager.ADJUST_RAISE; "volume_down" -> AudioManager.ADJUST_LOWER; "mute" -> AudioManager.ADJUST_MUTE; else -> AudioManager.ADJUST_UNMUTE }
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                message = "Volume command sent."
            }
            "home" -> { a.runOnUiThread { show("Home") }; message = "Returned to Nexro home." }
            "settings" -> { a.runOnUiThread { show("Settings") }; message = "Opened Nexro TV settings." }
            "back" -> { a.runOnUiThread { if (page != "Home") show("Home") else a.onBackPressed() }; message = "Back requested." }
            "open_app" -> {
                val term = value.orEmpty().trim()
                val item = apps.firstOrNull { it.name.contains(term, true) || it.pkg.contains(term, true) }
                if (item == null) { ok = false; message = "App not found: $term" } else { a.runOnUiThread { launch(item) }; message = "Opening ${item.name}." }
            }
            "select" -> { if (apps.isEmpty()) { ok = false; message = "No launchable apps found." } else { a.runOnUiThread { launch(apps[selectedIndex.coerceIn(0, apps.lastIndex)]) }; message = "Opening selected app." } }
            "play", "pause" -> {
                val audio = a.getSystemService(Activity.AUDIO_SERVICE) as AudioManager
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                message = "Media key sent."
            }
            "up", "down", "left", "right" -> { a.runOnUiThread { if (page == "Home") moveFocus(action) else show("Apps") }; message = "Focus moved $action." }
            else -> { ok = false; message = "Unsupported command: $action" }
        }
        return JSONObject().put("ok", ok).put("executed", ok).put("simulated", false).put("action", action).put("message", message)
    }

    fun handleKeyCode(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (page == "Home" && isFocusInsideDock()) { moveFocus("left"); return true }
                return false
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (page == "Home" && isFocusInsideDock()) { moveFocus("right"); return true }
                return false
            }
            // Let Android's native focus engine move between the home controls and dock.
            // DPAD up/down must never unexpectedly navigate into Settings or Apps.
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return false
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                root.findFocus()?.performClick()
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (page == "IconStyle" || page == "QuickSettings") { show("Settings"); return true }
                if (page != "Home") { show("Home"); return true }
                return false
            }
        }
        return false
    }


    private fun isFocusInsideDock(): Boolean {
        if (!::dockRow.isInitialized) return false
        var current = root.findFocus() ?: return false
        while (current.parent is View) {
            if (current.parent === dockRow) return true
            current = current.parent as View
        }
        return current === dockRow
    }

    private fun moveFocus(action: String) {
        if (!::dockRow.isInitialized || dockRow.childCount == 0) return
        val n = dockRow.childCount
        selectedIndex = when (action) {
            "left" -> (selectedIndex - 1 + n) % n
            "right" -> (selectedIndex + 1) % n
            else -> selectedIndex
        }.coerceIn(0, n - 1)
        dockRow.getChildAt(selectedIndex)?.requestFocus()
    }

    private fun iconTileWidth(): Int = when (prefs.getString("icon_size", "Medium")) { "Small" -> 102; "Large" -> 126; else -> 114 }
    private fun motion() = prefs.getBoolean("motion", true)
    private fun dp(value: Int): Int = (value * a.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private fun currentTime(): String {
        val is24 = android.text.format.DateFormat.is24HourFormat(a)
        return SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date())
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(a).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        typeface = android.graphics.Typeface.create(if (size >= 50) "sans-serif-light" else "sans-serif",
            if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }
    private fun divider() = View(a).apply { setBackgroundColor(Color.argb(45, 255, 255, 255)) }
    private fun pill(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(40).toFloat() }
    private fun glassPill() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(150, 83, 104, 140), Color.argb(90, 16, 23, 38))).apply {
        cornerRadius = dp(40).toFloat(); setStroke(dp(1), Color.argb(85, 221, 233, 255))
    }
    private fun glassDock() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(112, 122, 142, 174), Color.argb(72, 28, 37, 55), Color.argb(102, 16, 24, 39))).apply {
        cornerRadius = dp(32).toFloat(); setStroke(dp(1), Color.argb(90, 220, 232, 255))
    }
    private fun glassPanel() = GradientDrawable(GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.argb(222, 12, 18, 31), Color.argb(228, 7, 10, 18))).apply {
        cornerRadius = dp(28).toFloat(); setStroke(dp(1), Color.argb(105, 190, 211, 245))
    }
    private fun tileBackground(focused: Boolean) = GradientDrawable(GradientDrawable.Orientation.TL_BR,
        if (focused) intArrayOf(Color.argb(220, 62, 98, 153), Color.argb(180, 22, 37, 62))
        else intArrayOf(Color.argb(95, 46, 59, 82), Color.argb(75, 13, 19, 32))).apply {
        cornerRadius = dp(22).toFloat()
        setStroke(dp(if (focused) 2 else 1), if (focused) Color.rgb(225, 238, 255) else Color.argb(45, 255, 255, 255))
    }

    private fun iconOutline(): ViewOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            val w = view.width.toFloat(); val h = view.height.toFloat()
            val r = min(w, h) * 0.24f
            val path = Path().apply {
                moveTo(w * 0.5f, 0f)
                cubicTo(w * 0.78f, 0f, w, h * 0.22f, w, h * 0.5f)
                cubicTo(w, h * 0.78f, w * 0.78f, h, w * 0.5f, h)
                cubicTo(w * 0.22f, h, 0f, h * 0.78f, 0f, h * 0.5f)
                cubicTo(0f, h * 0.22f, w * 0.22f, 0f, w * 0.5f, 0f)
                close()
            }
            try { outline.setConvexPath(path) } catch (_: Exception) { outline.setRoundRect(0, 0, view.width, view.height, r) }
        }
    }

    private fun buildUnused() = Unit

    private inner class WallpaperView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0 || h <= 0) return
            canvas.drawColor(bg)
            paint.shader = RadialGradient(w * 0.79f, h * 0.12f, w * 0.48f,
                intArrayOf(Color.rgb(45, 91, 157), Color.rgb(14, 24, 45), Color.TRANSPARENT),
                floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null

            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(w * 0.12f, h * 0.12f, w * 0.84f, h * 0.73f,
                    intArrayOf(Color.TRANSPARENT, Color.rgb(180, 100, 57), Color.rgb(245, 180, 112), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.43f, 0.55f, 1f), Shader.TileMode.CLAMP)
                maskFilter = BlurMaskFilter(min(w, h) * 0.085f, BlurMaskFilter.Blur.NORMAL)
            }
            val warmPath = Path().apply {
                moveTo(-w * .1f, h * .25f); cubicTo(w*.18f,h*.39f,w*.34f,h*.10f,w*.52f,h*.32f)
                cubicTo(w*.72f,h*.52f,w*.83f,h*.65f,w*1.1f,h*.55f); lineTo(w*1.1f,h*.69f)
                cubicTo(w*.78f,h*.80f,w*.61f,h*.52f,w*.45f,h*.42f); cubicTo(w*.24f,h*.24f,w*.12f,h*.50f,-w*.1f,h*.38f); close()
            }
            canvas.drawPath(warmPath, glow); glow.shader = null; glow.maskFilter = null
            paint.shader = LinearGradient(0f, h*.34f, w, h*.9f,
                intArrayOf(Color.rgb(13, 23, 43), Color.rgb(4, 7, 14), Color.rgb(11, 24, 43)),
                null, Shader.TileMode.CLAMP)
            val darkCurve = Path().apply {
                moveTo(-w*.08f,h*.72f); cubicTo(w*.16f,h*.65f,w*.28f,h*.45f,w*.49f,h*.48f)
                cubicTo(w*.68f,h*.50f,w*.82f,h*.75f,w*1.08f,h*.63f); lineTo(w*1.08f,h*1.1f)
                lineTo(-w*.08f,h*1.1f); close()
            }
            canvas.drawPath(darkCurve, paint); paint.shader = null
            paint.style = Paint.Style.STROKE; paint.strokeWidth = min(w,h)*.0035f
            paint.shader = LinearGradient(0f,h*.50f,w,h*.82f,
                intArrayOf(Color.TRANSPARENT,Color.rgb(98,153,226),Color.rgb(255,191,133),Color.TRANSPARENT),
                null,Shader.TileMode.CLAMP)
            val rim = Path().apply {
                moveTo(-w*.03f,h*.84f); cubicTo(w*.19f,h*.70f,w*.36f,h*.49f,w*.52f,h*.57f)
                cubicTo(w*.73f,h*.64f,w*.81f,h*.81f,w*1.04f,h*.69f)
            }
            canvas.drawPath(rim, paint); paint.shader = null; paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(w*.35f,h*.92f,w*.4f,
                intArrayOf(Color.argb(70,42,102,174),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            canvas.drawRect(0f,h*.6f,w,h,paint); paint.shader = null
        }
    }
}
