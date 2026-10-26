package com.nexro.tvlauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.Collections
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 10, 15)
    private val panel = Color.rgb(18, 22, 31)
    private val tile = Color.rgb(26, 32, 44)
    private val mint = Color.rgb(117, 229, 209)
    private val white = Color.rgb(245, 247, 251)
    private val muted = Color.rgb(145, 155, 173)
    private lateinit var status: TextView
    private lateinit var pinView: TextView
    private lateinit var ipView: TextView
    private lateinit var bridge: TvBridge
    private val apps = mutableListOf<Pair<String, Intent>>()
    private val tiles = mutableListOf<View>()
    private var selected = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        bridge = TvBridge(this) { msg -> runOnUiThread { if (::status.isInitialized) status.text = msg } }
        render()
        bridge.start()
    }

    private fun render() {
        // tvOS-inspired experience: cinematic hero, top navigation, oversized app artwork.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(58, 26, 58, 22)
            setBackgroundColor(Color.rgb(5, 7, 12))
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "nexro"
            textSize = 30f
            letterSpacing = -.04f
            setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        top.addView(TextView(this).apply {
            text = "  TV"
            textSize = 19f
            setTextColor(Color.rgb(170, 178, 195))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val nav = listOf("Watch Now", "Movies", "TV Shows", "Sports", "Library")
        nav.forEachIndexed { i, label ->
            top.addView(TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(if (i == 0) white else Color.rgb(157, 165, 181))
                typeface = Typeface.create("sans-serif", if (i == 0) Typeface.BOLD else Typeface.NORMAL)
                setPadding(12, 10, 12, 10)
                if (i == 0) background = rounded(Color.rgb(36, 42, 55), 20)
                isFocusable = true
                setOnFocusChangeListener { v, focus -> v.background = if (focus) rounded(Color.rgb(45, 53, 69), 20) else if (i == 0) rounded(Color.rgb(36, 42, 55), 20) else null }
            })
        }
        val remoteButton = TextView(this).apply {
            text = "  ⚙  Remote"
            textSize = 13f
            setTextColor(Color.rgb(208, 214, 225))
            setPadding(16, 10, 0, 10)
            isFocusable = true
            setOnClickListener {
                remotePanel.visibility = if (remotePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                requestFocus()
            }
        }
        top.addView(remoteButton)
        root.addView(top, LinearLayout.LayoutParams(-1, 52))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(34, 22, 30, 22)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(29, 43, 66), Color.rgb(19, 26, 42), Color.rgb(12, 15, 24))
            ).apply { cornerRadius = 26f }
        }
        hero.addView(TextView(this).apply {
            text = "N E X R O   O R I G I N A L S"
            textSize = 10f
            letterSpacing = .20f
            setTextColor(Color.rgb(183, 201, 255))
        })
        hero.addView(TextView(this).apply {
            text = "Your next\nfavourite thing."
            textSize = 39f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setLineSpacing(-2f, 1f)
            setPadding(0, 8, 0, 4)
        })
        hero.addView(TextView(this).apply {
            text = "Movies, shows and the apps you love — all in one place."
            textSize = 14f
            setTextColor(Color.rgb(190, 198, 213))
            setPadding(0, 0, 0, 12)
        })
        val heroActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val explore = TextView(this).apply {
            text = "  ▶   Explore your apps   "
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(16, 19, 26))
            setPadding(12, 11, 12, 11)
            background = rounded(Color.WHITE, 24)
            isFocusable = true
            setOnClickListener { tiles.firstOrNull()?.requestFocus() }
        }
        heroActions.addView(explore)
        heroActions.addView(TextView(this).apply {
            text = "     Built for the big screen"
            textSize = 12f
            setTextColor(Color.rgb(169, 181, 203))
        })
        hero.addView(heroActions)
        root.addView(hero, LinearLayout.LayoutParams(-1, 232).apply { topMargin = 16 })

        val appsTitle = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        appsTitle.addView(TextView(this).apply {
            text = "Your Apps"
            textSize = 22f
            setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        appsTitle.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        appsTitle.addView(TextView(this).apply {
            text = "INSTALLED ON THIS TV  ›"
            textSize = 10f
            letterSpacing = .09f
            setTextColor(Color.rgb(139, 150, 173))
        })
        root.addView(appsTitle, LinearLayout.LayoutParams(-1, 42).apply { topMargin = 13 })

        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false }
        val appRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        apps.clear()
        tiles.clear()
        val pm = packageManager
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )
        val seen = mutableSetOf<String>()
        intents.flatMap { intent ->
            try { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) } catch (_: Exception) { emptyList() }
        }.forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg != packageName && seen.add(pkg)) {
                val category = if (info.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0)
                    Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
                apps.add(info.loadLabel(pm).toString() to Intent(Intent.ACTION_MAIN).addCategory(category)
                    .setClassName(pkg, info.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        apps.sortBy { it.first.lowercase() }
        if (apps.isEmpty()) {
            appRow.addView(TextView(this).apply {
                text = "No apps found yet. Install streaming apps from your TV's app store and they'll appear here."
                textSize = 14f
                setTextColor(Color.rgb(170, 178, 195))
                setPadding(8, 20, 8, 20)
            })
        } else {
            apps.forEachIndexed { idx, app ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(10, 12, 10, 10)
                    background = rounded(Color.rgb(23, 27, 37), 20, Color.rgb(42, 48, 62))
                    isFocusable = true
                    isFocusableInTouchMode = true
                    contentDescription = "Open ${app.first}"
                    val iconBox = LinearLayout(this@MainActivity).apply {
                        gravity = Gravity.CENTER
                        background = rounded(Color.rgb(34 + (idx * 17 % 25), 39 + (idx * 11 % 20), 53 + (idx * 13 % 28)), 18)
                        val image = ImageView(this@MainActivity).apply {
                            try { setImageDrawable(packageManager.getApplicationIcon(app.second.component?.packageName ?: "")) } catch (_: Exception) {}
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                        addView(image, LinearLayout.LayoutParams(58, 58))
                    }
                    addView(iconBox, LinearLayout.LayoutParams(94, 88))
                    addView(TextView(this@MainActivity).apply {
                        text = app.first
                        textSize = 12f
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextColor(white)
                        setPadding(0, 9, 0, 0)
                    }, LinearLayout.LayoutParams(-1, -2))
                    setOnFocusChangeListener { v, focus ->
                        v.background = rounded(if (focus) Color.rgb(44, 54, 72) else Color.rgb(23, 27, 37), 20, if (focus) Color.WHITE else Color.rgb(42, 48, 62))
                        if (focus) v.scaleX = 1.04f else v.scaleX = 1f
                        if (focus) v.scaleY = 1.04f else v.scaleY = 1f
                    }
                    setOnClickListener { launchApp(idx) }
                }
                appRow.addView(card, LinearLayout.LayoutParams(128, 130).apply { marginEnd = 13 })
                tiles.add(card)
            }
        }
        scroll.addView(appRow)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 144))

        remotePanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 12, 18, 12)
            background = rounded(Color.rgb(19, 23, 32), 18, Color.rgb(49, 57, 74))
            visibility = View.GONE
        }
        val remoteInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        remoteInfo.addView(TextView(this).apply { text = "PHONE REMOTE  ·  OPTIONAL"; textSize = 10f; letterSpacing = .1f; setTextColor(Color.rgb(177, 193, 225)) })
        remoteInfo.addView(TextView(this).apply { text = "Pair a phone on the same Wi-Fi to control this TV."; textSize = 12f; setTextColor(Color.rgb(177, 184, 199)); setPadding(0, 5, 0, 0) })
        remoteInfo.addView(TextView(this).apply { text = "PIN  ${bridge.pin}     ADDRESS  ${localIp()}:8765"; textSize = 14f; setTextColor(white); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 8, 0, 0) })
        pinView = TextView(this).apply { text = bridge.pin; visibility = View.GONE }
        ipView = TextView(this).apply { text = "${localIp()}:8765"; visibility = View.GONE }
        status = TextView(this).apply { text = "Local remote bridge starting…"; textSize = 10f; setTextColor(Color.rgb(140, 151, 170)); setPadding(0, 5, 0, 0) }
        remoteInfo.addView(status)
        remotePanel.addView(remoteInfo, LinearLayout.LayoutParams(0, -2, 1f))
        val pinButton = TextView(this).apply {
            text = "Generate new PIN"
            textSize = 12f
            setTextColor(white)
            setPadding(14, 10, 14, 10)
            background = rounded(Color.rgb(40, 47, 61), 20)
            isFocusable = true
            setOnClickListener { bridge.rotatePin(); pinView.text = bridge.pin; remoteInfo.getChildAt(2).let { (it as TextView).text = "PIN  ${bridge.pin}     ADDRESS  ${localIp()}:8765" } }
        }
        remotePanel.addView(pinButton)
        root.addView(remotePanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
        root.addView(TextView(this).apply {
            text = "NEXRO  ·  MADE FOR YOUR LIVING ROOM"
            textSize = 9f
            letterSpacing = .12f
            setTextColor(Color.rgb(91, 101, 119))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }, LinearLayout.LayoutParams(-1, 22))
        setContentView(root)
        root.requestFocus()
    }

    private lateinit var remotePanel: LinearLayout

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color); cornerRadius = radius.toFloat()
            if (stroke != null) setStroke(1, stroke)
        }

    private fun localIp(): String = try {
        Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress ?: "TV-IP"
    } catch (_: Exception) { "TV-IP" }

    private fun launchApp(index: Int) {
        if (index !in apps.indices) return
        selected = index
        try { startActivity(apps[index].second) } catch (_: Exception) { status.text = "This app could not be opened." }
    }

    private fun move(action: String) {
        if (apps.isEmpty()) return
        selected = when (action) {
            "left" -> (selected - 1 + apps.size) % apps.size
            "right" -> (selected + 1) % apps.size
            "up" -> (selected - 4 + apps.size) % apps.size
            "down" -> (selected + 4) % apps.size
            else -> selected
        }
        tiles.getOrNull(selected)?.requestFocus()
    }

    fun execute(action: String, value: String?): JSONObject {
        var ok = true
        val message: String
        when (action) {
            "volume_up" -> { (getSystemService(AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); message = "Volume up requested." }
            "volume_down" -> { (getSystemService(AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); message = "Volume down requested." }
            "mute", "unmute" -> { val a = getSystemService(AUDIO_SERVICE) as AudioManager; a.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (action == "mute") AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI); message = if (action == "mute") "Mute requested." else "Sound enabled." }
            "up", "down", "left", "right" -> { runOnUiThread { move(action) }; message = "Launcher focus moved $action." }
            "select" -> { runOnUiThread { launchApp(selected) }; message = "Opening selected app." }
            "home" -> { runOnUiThread { move("home"); if (apps.isNotEmpty()) { selected = 0; tiles.firstOrNull()?.requestFocus() } }; message = "Returned to Nexro home." }
            "open_app" -> {
                val name = value?.lowercase()?.trim().orEmpty()
                val i = apps.indexOfFirst { it.first.lowercase().contains(name) || name.contains(it.first.lowercase()) }
                if (i >= 0) { runOnUiThread { launchApp(i) }; message = "Opening ${apps[i].first}." } else { ok = false; message = "App not found: $name" }
            }
            "play", "pause" -> { val a = getSystemService(AUDIO_SERVICE) as AudioManager; a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)); a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)); message = "Media key sent; the active app decides whether to respond." }
            "back" -> { runOnUiThread { onBackPressed() }; message = "Back requested." }
            "settings" -> { runOnUiThread { try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }; message = "Opening Android settings." }
            "power_off" -> { ok = false; message = "Android does not allow ordinary launcher apps to power off every TV." }
            "channel_up", "channel_down", "number", "input" -> { ok = false; message = "This Android TV does not expose this control to the launcher." }
            else -> { ok = false; message = "Unsupported command: $action" }
        }
        return JSONObject().put("ok", ok).put("executed", ok).put("simulated", false).put("action", action).put("message", message)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { move("left"); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { move("right"); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { move("up"); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { move("down"); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { launchApp(selected); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { bridge.stop(); super.onDestroy() }
}

private class TvBridge(private val activity: MainActivity, private val report: (String) -> Unit) {
    @Volatile var pin: String = newPin()
        private set
    @Volatile private var token: String? = null
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var running = false
    private fun newPin() = (100000 + SecureRandom().nextInt(900000)).toString()

    fun rotatePin() { pin = newPin(); token = null; report("New PIN generated; previous phone sessions revoked.") }
    fun start() {
        if (running) return
        running = true
        thread(name = "NexroTVBridge") {
            try {
                socket = ServerSocket(8765)
                report("Local bridge ready on port 8765.")
                while (running) {
                    val client = socket!!.accept()
                    thread(name = "NexroTVClient") { handle(client) }
                }
            } catch (e: Exception) { if (running) report("Bridge error: ${e.message}") }
        }
    }
    fun stop() { running = false; try { socket?.close() } catch (_: Exception) {} }

    private fun handle(client: java.net.Socket) {
        client.use { s ->
            try {
                s.soTimeout = 4000
                val input = BufferedInputStream(s.getInputStream())
                val headerBytes = ByteArrayOutputStream()
                var state = 0
                while (headerBytes.size() < 8192) {
                    val b = input.read()
                    if (b < 0) return
                    headerBytes.write(b)
                    state = when {
                        state == 0 && b == 13 -> 1
                        state == 1 && b == 10 -> 2
                        state == 2 && b == 13 -> 3
                        state == 3 && b == 10 -> 4
                        b == 13 -> 1
                        else -> 0
                    }
                    if (state == 4) break
                }
                val lines = headerBytes.toString("UTF-8").split("\r\n")
                val parts = lines.firstOrNull()?.split(" ") ?: return
                val method = parts.getOrNull(0) ?: return
                val path = parts.getOrNull(1) ?: return
                val headers = lines.drop(1).mapNotNull { line ->
                    val i = line.indexOf(':')
                    if (i > 0) line.substring(0, i).lowercase() to line.substring(i + 1).trim() else null
                }.toMap()
                val length = (headers["content-length"] ?: "0").toIntOrNull()?.coerceIn(0, 16384) ?: 0
                val bytes = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val n = input.read(bytes, offset, length - offset)
                    if (n < 0) break
                    offset += n
                }
                val body = JSONObject(String(bytes, 0, offset, Charsets.UTF_8).ifBlank { "{}" })
                if (method == "OPTIONS") { respond(s, 200, JSONObject().put("ok", true)); return }
                if (method == "GET" && (path == "/" || path == "/index.html")) { respondHtml(s); return }
                val auth = headers["authorization"]?.removePrefix("Bearer ")
                val result = when {
                    method == "GET" && path == "/api/health" -> JSONObject().put("ok", true).put("service", "Nexro TV Launcher Bridge")
                    method == "POST" && path == "/api/pair" -> if (body.optString("pin") == pin) {
                        token = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
                        JSONObject().put("ok", true).put("token", token).put("capabilities", JSONObject().put("volume", true).put("navigation", true).put("apps", true).put("media", true).put("power", false))
                    } else {
                        respond(s, 401, JSONObject().put("error", "Incorrect TV pairing PIN.")); return
                    }
                    token != null && auth == token && method == "POST" && path == "/api/command" ->
                        activity.execute(body.optString("action"), body.opt("value")?.takeUnless { it == JSONObject.NULL }?.toString())
                    else -> { respond(s, 401, JSONObject().put("error", "Pairing token missing or endpoint not found.")); return }
                }
                respond(s, if (result.optBoolean("ok", true)) 200 else 422, result)
            } catch (_: Exception) {
                try { respond(s, 400, JSONObject().put("error", "Malformed request.")) } catch (_: Exception) {}
            }
        }
    }

    private fun respondHtml(s: java.net.Socket) {
        val data = activity.assets.open("index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: ${data.size}\r\n\r\n"
        s.getOutputStream().apply { write(header.toByteArray(Charsets.UTF_8)); write(data); flush() }
    }

    private fun respond(s: java.net.Socket, code: Int, json: JSONObject) {
        val data = json.toString().toByteArray(Charsets.UTF_8)
        val reason = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 422 -> "Unprocessable Entity"; else -> "Error" }
        val header = "HTTP/1.1 $code $reason\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: Content-Type, Authorization\r\nAccess-Control-Allow-Private-Network: true\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nConnection: close\r\nContent-Length: ${data.size}\r\n\r\n"
        s.getOutputStream().apply { write(header.toByteArray(Charsets.UTF_8)); write(data); flush() }
    }
}