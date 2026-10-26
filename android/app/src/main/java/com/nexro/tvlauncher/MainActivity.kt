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
    private val tiles = mutableListOf<TextView>()
    private var selected = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        bridge = TvBridge(this) { msg -> runOnUiThread { if (::status.isInitialized) status.text = msg } }
        render()
        bridge.start()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 28, 44, 28)
            setBackgroundColor(bg)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "NEXRO"
            textSize = 27f
            letterSpacing = .12f
            setTextColor(mint)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "  TV"
            textSize = 27f
            setTextColor(white)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(TextView(this).apply {
            text = "●  ANDROID TV"
            textSize = 11f
            letterSpacing = .08f
            setTextColor(mint)
            setPadding(15, 9, 15, 9)
            background = rounded(Color.rgb(18, 38, 39), 24)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, 48))
        root.addView(TextView(this).apply {
            text = "Your TV. Your apps."
            textSize = 34f
            setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, 18, 0, 4)
        })
        root.addView(TextView(this).apply {
            text = "A cleaner home screen, built around what is actually installed."
            textSize = 14f
            setTextColor(muted)
            setPadding(0, 0, 0, 20)
        })

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val appPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
            background = rounded(panel, 22)
        }
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = "YOUR INSTALLED APPS"
            textSize = 11f
            letterSpacing = .12f
            setTextColor(mint)
            typeface = Typeface.DEFAULT_BOLD
        })
        titleRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        titleRow.addView(TextView(this).apply { text = "LIVE"; textSize = 10f; setTextColor(muted) })
        appPanel.addView(titleRow, LinearLayout.LayoutParams(-1, 28))

        val scroll = ScrollView(this).apply { clipToPadding = false; isFillViewport = false }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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
            grid.addView(TextView(this).apply {
                text = "No launchable apps found"
                textSize = 21f
                setTextColor(white)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(8, 28, 8, 10)
            })
            grid.addView(TextView(this).apply {
                text = "Apps installed on this TV will appear here. Open the TV app store to install more."
                textSize = 13f
                setTextColor(muted)
                setPadding(8, 0, 8, 20)
            })
        } else {
            apps.chunked(4).forEach { rowApps ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                rowApps.forEach { app ->
                    val idx = apps.indexOf(app)
                    val label = app.first
                    val view = TextView(this).apply {
                        text = "◈\n\n$label"
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setTextColor(white)
                        setPadding(8, 12, 8, 12)
                        background = rounded(tile, 17, Color.rgb(45, 52, 66))
                        isFocusable = true
                        isFocusableInTouchMode = true
                        contentDescription = "Open $label"
                        setOnFocusChangeListener { v, focus ->
                            v.background = rounded(if (focus) Color.rgb(26, 57, 55) else tile, 17, if (focus) mint else Color.rgb(45, 52, 66))
                            setTextColor(if (focus) mint else white)
                        }
                        setOnClickListener { launchApp(idx) }
                    }
                    row.addView(view, LinearLayout.LayoutParams(0, 112, 1f).apply { marginEnd = 10 })
                    tiles.add(view)
                }
                repeat(4 - rowApps.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 112, 1f).apply { marginEnd = 10 }) }
                grid.addView(row, LinearLayout.LayoutParams(-1, 112).apply { bottomMargin = 10 })
            }
        }
        scroll.addView(grid)
        appPanel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(appPanel, LinearLayout.LayoutParams(0, 0, 1.7f).apply { marginEnd = 18 })

        val pair = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 22)
            background = rounded(Color.rgb(16, 27, 32), 22, Color.rgb(40, 74, 73))
        }
        pair.addView(TextView(this).apply {
            text = "PHONE REMOTE"
            textSize = 11f
            letterSpacing = .13f
            setTextColor(mint)
            typeface = Typeface.DEFAULT_BOLD
        })
        pair.addView(TextView(this).apply {
            text = "Pair your phone"
            textSize = 23f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 16, 0, 6)
        })
        pair.addView(TextView(this).apply {
            text = "Connect Nexro Remote AI from a phone on the same Wi-Fi network."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, 0, 0, 14)
        })
        pair.addView(TextView(this).apply {
            text = "PAIRING PIN"
            textSize = 10f
            letterSpacing = .15f
            setTextColor(muted)
        })
        pinView = TextView(this).apply {
            text = bridge.pin
            textSize = 35f
            letterSpacing = .12f
            setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, 3, 0, 4)
        }
        pair.addView(pinView)
        pair.addView(TextView(this).apply {
            text = "TV BRIDGE ADDRESS"
            textSize = 10f
            letterSpacing = .13f
            setTextColor(muted)
            setPadding(0, 12, 0, 5)
        })
        ipView = TextView(this).apply {
            text = "${localIp()}:8765"
            textSize = 16f
            setTextColor(mint)
            typeface = Typeface.DEFAULT_BOLD
        }
        pair.addView(ipView)
        val rotate = TextView(this).apply {
            text = "↻  Generate new PIN"
            textSize = 13f
            setTextColor(white)
            setPadding(0, 18, 0, 14)
            isFocusable = true
            setOnClickListener { bridge.rotatePin(); pinView.text = bridge.pin }
        }
        pair.addView(rotate)
        status = TextView(this).apply {
            text = "Starting local bridge…"
            textSize = 12f
            setTextColor(muted)
            setPadding(0, 10, 0, 0)
        }
        pair.addView(status)
        body.addView(pair, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "NEXRO  ·  PRIVATE LOCAL PAIRING  ·  NO FAKE CONTENT"
            textSize = 10f
            letterSpacing = .1f
            setTextColor(Color.rgb(91, 101, 119))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }, LinearLayout.LayoutParams(-1, 30))
        setContentView(root)
        root.requestFocus()
    }

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

    private fun respond(s: java.net.Socket, code: Int, json: JSONObject) {
        val data = json.toString().toByteArray(Charsets.UTF_8)
        val reason = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 422 -> "Unprocessable Entity"; else -> "Error" }
        val header = "HTTP/1.1 $code $reason\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: Content-Type, Authorization\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nConnection: close\r\nContent-Length: ${data.size}\r\n\r\n"
        s.getOutputStream().apply { write(header.toByteArray(Charsets.UTF_8)); write(data); flush() }
    }
}